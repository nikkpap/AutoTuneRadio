package gr.radiorise.player

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class DeviceCalendar(val id:Long,val name:String,val account:String)
data class Meeting(val calendarId:Long,val eventId:Long,val begin:Long,val end:Long,val title:String,val location:String) {
 val key:String get()=ReminderTiming.occurrence(calendarId,eventId,begin)
 fun timeLabel():String {
  val fmt=SimpleDateFormat("EEE d/M · HH:mm",Lang.locale)
  return fmt.format(Date(begin))+"–"+SimpleDateFormat("HH:mm",Lang.locale).format(Date(end))
 }
 fun json():JSONObject=JSONObject().put("calendar",calendarId).put("event",eventId).put("begin",begin).put("end",end).put("title",title).put("location",location)
 companion object {fun from(o:JSONObject)=Meeting(o.getLong("calendar"),o.getLong("event"),o.getLong("begin"),o.getLong("end"),o.getString("title"),o.optString("location"))}
}

object CalendarEngine {
 val calendars=MutableStateFlow<List<DeviceCalendar>>(emptyList())
 val meetings=MutableStateFlow<List<Meeting>>(emptyList())
 val status=MutableStateFlow(tr("Επίλεξε ημερολόγιο συσκευής.","Choose a device calendar."))
 private val lock=Any()
 private lateinit var context:Context
 private val executor=Executors.newSingleThreadExecutor()
 private var listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener?=null
 private var observer:ContentObserver?=null
 private var observed=false
 fun permitted(c:Context)=ContextCompat.checkSelfPermission(c,Manifest.permission.READ_CALENDAR)==PackageManager.PERMISSION_GRANTED
 fun sourceAvailable(c:Context)=if(Store(c).calendarId==IcsStorage.ID) IcsStorage.exists(c) else permitted(c)
 fun importIcs(c:Context,uri:Uri):Int=synchronized(lock) {val count=IcsStorage.importFile(c,uri);Store(c).calendarId=IcsStorage.ID;refresh(c);count}
 fun notificationsAllowed(c:Context):Boolean {
  if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) return false
  if(!NotificationManagerCompat.from(c).areNotificationsEnabled()) return false
  return c.getSystemService(NotificationManager::class.java).getNotificationChannel("meetings")?.importance!=NotificationManager.IMPORTANCE_NONE
 }
 fun exact(c:Context)=Build.VERSION.SDK_INT<31 || c.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
 fun init(c:Context) {
  context=c.applicationContext
  listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key ->
   if(key in setOf("icsName","calendarId","calendarSaver","calendarNotifications","calendarReminders","calendarSnooze","lang")) requestRefresh(context)
  }
  context.getSharedPreferences("radio",0).registerOnSharedPreferenceChangeListener(listener)
  observer=object:ContentObserver(Handler(Looper.getMainLooper())) {
   override fun onChange(selfChange:Boolean) {requestRefresh(context)}
  }
  requestRefresh(context)
 }
 fun requestRefresh(c:Context) {
  val work=OneTimeWorkRequestBuilder<CalendarRefreshWorker>().build()
  WorkManager.getInstance(c).enqueueUniqueWork("calendar-refresh",ExistingWorkPolicy.APPEND_OR_REPLACE,work)
 }
 fun refreshAsync(c:Context) {executor.execute {refresh(c.applicationContext)}}
 fun withReceiver(c:Context,block:()->Unit,finish:()->Unit) {
  executor.execute {try {block()} catch(e:Exception) {DebugLog.error("CALENDAR_RECEIVER",e)} finally {finish()}}
 }
 private fun readCalendars(c:Context):List<DeviceCalendar> {
  val result=mutableListOf<DeviceCalendar>()
  c.contentResolver.query(CalendarContract.Calendars.CONTENT_URI,arrayOf("_id","calendar_displayName","account_name"),null,null,"calendar_displayName ASC")?.use {q ->
   while(q.moveToNext()) result.add(DeviceCalendar(q.getLong(0),q.getString(1).orEmpty().ifBlank {tr("Ημερολόγιο","Calendar")},q.getString(2).orEmpty()))
  }
  return result
 }
 private fun readMeetings(c:Context,id:Long,now:Long):List<Meeting> {
  if(id==IcsStorage.ID) return IcsStorage.meetings(c,now)
  val uri=CalendarContract.Instances.CONTENT_URI.buildUpon().also {
   ContentUris.appendId(it,now-24*60*60*1000L);ContentUris.appendId(it,now+7*24*60*60*1000L)
  }.build()
  val result=mutableListOf<Meeting>()
  c.contentResolver.query(uri,arrayOf("event_id","begin","end","title","eventLocation"),"calendar_id=? AND allDay=0 AND (eventStatus IS NULL OR eventStatus!=?) AND (selfAttendeeStatus IS NULL OR selfAttendeeStatus!=?)",arrayOf(id.toString(),CalendarContract.Events.STATUS_CANCELED.toString(),CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED.toString()),"begin ASC")?.use {q ->
   while(q.moveToNext()) if(q.getLong(2)>now) result.add(Meeting(id,q.getLong(0),q.getLong(1),q.getLong(2),q.getString(3).orEmpty().ifBlank {tr("Συνάντηση","Meeting")},q.getString(4).orEmpty()))
  }
  return result.distinctBy {it.key}
 }
 fun refresh(c:Context):Boolean=synchronized(lock) {
  val store=Store(c);val prefs=c.getSharedPreferences("calendar-runtime",0)
  try {
   val active=store.calendarSaver || store.calendarNotifications
   val usingIcs=store.calendarId==IcsStorage.ID
   if(!usingIcs && !permitted(c)) {
    calendars.value=emptyList();meetings.value=emptyList();cancelAll(c,prefs)
    WorkManager.getInstance(c).cancelUniqueWork("calendar-periodic")
    if(observed) {observer?.let {c.contentResolver.unregisterContentObserver(it)};observed=false}
    status.value=tr("Χρειάζεται άδεια ανάγνωσης ημερολογίου.","Calendar read permission is required.");return@synchronized true
   }
   if(active && !usingIcs && !observed && observer!=null) {
    c.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI,true,observer!!);observed=true
   } else if((!active || usingIcs) && observed) {observer?.let {c.contentResolver.unregisterContentObserver(it)};observed=false}
   calendars.value=if(permitted(c)) runCatching {readCalendars(c)}.onFailure {DebugLog.error("CALENDAR_LIST",it)}.getOrDefault(emptyList()) else emptyList()
   val selected=if(usingIcs && IcsStorage.exists(c)) DeviceCalendar(IcsStorage.ID,"ICS · ${store.icsName}","") else calendars.value.find {it.id==store.calendarId}
   if(!active || selected==null) {
    meetings.value=emptyList();cancelAll(c,prefs)
    WorkManager.getInstance(c).cancelUniqueWork("calendar-periodic")
    status.value=if(selected==null) tr("Επίλεξε διαθέσιμο ημερολόγιο.","Choose an available calendar.") else tr("Ο συγχρονισμός είναι ανενεργός.","Sync is off.")
    return@synchronized true
   }
   WorkManager.getInstance(c).enqueueUniquePeriodicWork("calendar-periodic",ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<CalendarRefreshWorker>(15,TimeUnit.MINUTES).build())
   val now=System.currentTimeMillis();val events=readMeetings(c,selected.id,now)
   meetings.value=events
   val keys=events.map {it.key}.toSet()
   val dismissed=prefs.getStringSet("dismissed",emptySet()).orEmpty().intersect(keys)
   val delivered=prefs.getStringSet("delivered",emptySet()).orEmpty().filter {token ->keys.any {token.startsWith("$it|")}}.toSet()
   val snoozes=runCatching {JSONObject(prefs.getString("snoozes","{}"))}.getOrDefault(JSONObject())
   snoozes.keys().asSequence().toList().filter {it !in keys}.forEach {snoozes.remove(it)}
   prefs.edit().putStringSet("dismissed",dismissed).putStringSet("delivered",delivered).putString("snoozes",snoozes.toString()).apply()
   cancelAlarms(c,prefs)
   val pending=JSONArray()
   val candidates=mutableListOf<ReminderTiming.Alarm>()
   val eventMap=events.associateBy {it.key}
   if(store.calendarNotifications) events.filter {it.key !in dismissed}.forEach {event ->
    val times=if(snoozes.has(event.key)) listOf("s${snoozes.getLong(event.key)}" to snoozes.getLong(event.key)) else store.calendarReminders.sortedDescending().map {it.toString() to ReminderTiming.trigger(event.begin,it)}
    times.forEach { (suffix,time) ->
     val token="${event.key}|$suffix"
     candidates.add(ReminderTiming.Alarm(event.key,token,time,event.end))
    }
   }
   ReminderTiming.plan(candidates,now,delivered,dismissed).forEach {alarm ->
    pending.put(JSONObject().put("token",alarm.token).put("at",alarm.at).put("meeting",eventMap.getValue(alarm.occurrence).json()))
   }
   prefs.edit().putString("pending",pending.toString()).commit()
   for(i in 0 until pending.length()) schedule(c,pending.getJSONObject(i),now)
   // Remove stale notifications after calendar edits/deletions or feature Off.
   val visible=prefs.getStringSet("visible",emptySet()).orEmpty()
   val retain=if(store.calendarNotifications) visible.intersect(keys) else emptySet()
   (visible-retain).forEach {NotificationManagerCompat.from(c).cancel(it,200)}
   val shown=runCatching {JSONObject(prefs.getString("shownMeetings","{}"))}.getOrDefault(JSONObject())
   shown.keys().asSequence().toList().filter {it !in retain}.forEach {shown.remove(it)}
   prefs.edit().putStringSet("visible",retain).putString("shownMeetings",shown.toString()).apply()
   status.value="${selected.name} · "+tr("${events.size} συναντήσεις","${events.size} meetings")+(if(store.calendarNotifications && !notificationsAllowed(c)) tr(" · Ειδοποιήσεις Android απενεργοποιημένες"," · Android notifications off") else "")+(if(store.calendarNotifications && !exact(c)) tr(" · Χρόνος ειδοποίησης κατά προσέγγιση"," · Approximate reminder time") else "")
   DebugLog.event("CALENDAR","refresh calendar=${selected.id} events=${events.size} alarms=${pending.length()} reminders=${store.calendarReminders.sorted()} notifications=${store.calendarNotifications} saver=${store.calendarSaver} exact=${exact(c)}")
   true
  } catch(e:SecurityException) {calendars.value=emptyList();meetings.value=emptyList();cancelAll(c,prefs);status.value=tr("Η άδεια ημερολογίου ανακλήθηκε.","Calendar permission was revoked.");DebugLog.error("CALENDAR",e);true}
  catch(e:IllegalArgumentException) {calendars.value=emptyList();meetings.value=emptyList();cancelAll(c,prefs);WorkManager.getInstance(c).cancelUniqueWork("calendar-periodic");status.value=if(store.calendarId==IcsStorage.ID) tr("Σφάλμα ICS: ","ICS error: ")+e.message else tr("Δεν υπάρχει διαθέσιμο ημερολόγιο στη συσκευή.","No calendar is available on the device.");DebugLog.error("CALENDAR_PROVIDER",e);true}
  catch(e:Exception) {status.value=tr("Αποτυχία ανάγνωσης ημερολογίου. Δοκίμασε ανανέωση.","Could not read the calendar. Try refreshing.");DebugLog.error("CALENDAR",e);false}
 }
 private fun pendingIntent(c:Context,token:String,action:String="FIRE"):PendingIntent = PendingIntent.getBroadcast(c,0,Intent(c,MeetingReceiver::class.java).setAction(action).setData(Uri.parse("autotune://meeting/"+Uri.encode(token))).putExtra("token",token),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun records(p:android.content.SharedPreferences)=runCatching {JSONArray(p.getString("pending","[]"))}.getOrDefault(JSONArray())
 private fun cancelAlarms(c:Context,p:android.content.SharedPreferences) {
  val a=records(p);val alarm=c.getSystemService(AlarmManager::class.java)
  for(i in 0 until a.length()) {val pi=pendingIntent(c,a.getJSONObject(i).getString("token"));alarm.cancel(pi);pi.cancel()}
 }
 private fun cancelAll(c:Context,p:android.content.SharedPreferences) {
  cancelAlarms(c,p);p.getStringSet("visible",emptySet()).orEmpty().forEach {NotificationManagerCompat.from(c).cancel(it,200)}
  p.edit().putString("pending","[]").putStringSet("visible",emptySet()).putString("shownMeetings","{}").commit()
 }
 private fun schedule(c:Context,o:JSONObject,now:Long) {
  val am=c.getSystemService(AlarmManager::class.java);val pi=pendingIntent(c,o.getString("token"));val at=maxOf(now+1000,o.getLong("at"))
  try {if(Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi) else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
  catch(e:SecurityException) {DebugLog.error("CALENDAR_ALARM",e);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,at,pi)}
 }
 // notificationsAllowed performs the version, runtime and channel permission checks.
 @android.annotation.SuppressLint("MissingPermission")
 fun receive(c:Context,action:String,token:String)=synchronized(lock) {
  DebugLog.event("CALENDAR_RECEIVE","action=$action token=$token")
  val p=c.getSharedPreferences("calendar-runtime",0);val store=Store(c)
  val list=records(p);val record=(0 until list.length()).map {list.getJSONObject(it)}.find {it.getString("token")==token}
  val key=token.substringBefore('|')
  if(action=="CANCEL" || action=="SNOOZE") {
   NotificationManagerCompat.from(c).cancel(key,200)
   val event=record?.let {Meeting.from(it.getJSONObject("meeting"))} ?: runCatching {Meeting.from(JSONObject(p.getString("shownMeetings","{}")).getJSONObject(key))}.getOrNull() ?: meetings.value.find {it.key==key}
   if(action=="CANCEL") p.edit().putStringSet("dismissed",p.getStringSet("dismissed",emptySet()).orEmpty()+key).commit()
   else if(event!=null && event.end>System.currentTimeMillis()) {
    val s=runCatching {JSONObject(p.getString("snoozes","{}"))}.getOrDefault(JSONObject());s.put(key,System.currentTimeMillis()+store.calendarSnooze*60_000L);p.edit().putString("snoozes",s.toString()).commit()
   }
   DebugLog.event("CALENDAR_ACTION",action);refresh(c);return@synchronized
  }
  if(record==null || !store.calendarNotifications || !sourceAvailable(c)) {refresh(c);return@synchronized}
  val event=Meeting.from(record.getJSONObject("meeting"));val now=System.currentTimeMillis()
  if(event.calendarId!=store.calendarId || event.end<=now || key in p.getStringSet("dismissed",emptySet()).orEmpty() || token in p.getStringSet("delivered",emptySet()).orEmpty()) return@synchronized
  // Validate occurrence against the provider to avoid notifying deleted/changed meetings.
  val actual=readMeetings(c,store.calendarId,now).find {it.key==event.key} ?: run {refresh(c);return@synchronized}
  if(!notificationsAllowed(c)) {DebugLog.event("CALENDAR","notification permission/channel denied");return@synchronized}
  val nm=c.getSystemService(NotificationManager::class.java)
  nm.createNotificationChannel(NotificationChannel("meetings",tr("Συναντήσεις","Meetings"),NotificationManager.IMPORTANCE_HIGH))
  val open=PendingIntent.getActivity(c,0,Intent(c,MainActivity::class.java).setAction("OPEN_CALENDAR").addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val notification=NotificationCompat.Builder(c,"meetings").setSmallIcon(android.R.drawable.ic_menu_my_calendar).setContentTitle(actual.title).setContentText(actual.timeLabel()).setStyle(NotificationCompat.BigTextStyle().bigText(actual.timeLabel()+if(actual.location.isNotBlank()) "\n${actual.location}" else "")).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setContentIntent(open).setAutoCancel(true).setCategory(NotificationCompat.CATEGORY_EVENT).addAction(0,tr("Αναβολή ${store.calendarSnooze}′","Snooze ${store.calendarSnooze}′"),pendingIntent(c,token,"SNOOZE")).addAction(0,tr("Ακύρωση","Cancel"),pendingIntent(c,token,"CANCEL")).build()
  NotificationManagerCompat.from(c).notify(key,200,notification)
  val shown=runCatching {JSONObject(p.getString("shownMeetings","{}"))}.getOrDefault(JSONObject());shown.put(key,actual.json())
  p.edit().putString("shownMeetings",shown.toString()).putStringSet("delivered",p.getStringSet("delivered",emptySet()).orEmpty()+token).putStringSet("visible",p.getStringSet("visible",emptySet()).orEmpty()+key).commit()
  DebugLog.event("CALENDAR","notification delivered token=$token latenessMs=${now-record.getLong("at")}")
 }
}

class CalendarRefreshWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
 override suspend fun doWork():Result=withContext(Dispatchers.IO) {if(CalendarEngine.refresh(applicationContext)) Result.success() else Result.retry()}
}
class MeetingReceiver:BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {
  val pending=goAsync()
  CalendarEngine.withReceiver(c,{CalendarEngine.receive(c.applicationContext,i.action.orEmpty(),i.getStringExtra("token").orEmpty())},{pending.finish()})
 }
}
class CalendarRestoreReceiver:BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {DebugLog.event("CALENDAR","restore=${i.action}");CalendarEngine.requestRefresh(c)}
}
