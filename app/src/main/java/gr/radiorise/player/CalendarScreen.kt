@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package gr.radiorise.player

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable fun CalendarSettings(store:Store) {
 val context=LocalContext.current
 val calendars by CalendarEngine.calendars.collectAsState()
 val meetings by CalendarEngine.meetings.collectAsState()
 val status by CalendarEngine.status.collectAsState()
 var saver by remember {mutableStateOf(store.calendarSaver)}
 var notifications by remember {mutableStateOf(store.calendarNotifications)}
 var reminders by remember {mutableStateOf(store.calendarReminders)}
 var snooze by remember {mutableIntStateOf(store.calendarSnooze)}
 var selected by remember {mutableLongStateOf(store.calendarId)}
 val scope=rememberCoroutineScope()
 var importing by remember {mutableStateOf(false)}
 var icsName by remember {mutableStateOf(store.icsName)}
 var pick by remember {mutableStateOf(false)}
 var permissionTarget by remember {mutableStateOf("")}
 var feedback by remember {mutableStateOf("")}
 var importedOk by remember {mutableStateOf(false)}
 val permissions=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
  if(if(permissionTarget=="pick") CalendarEngine.permitted(context) else CalendarEngine.sourceAvailable(context)) {
   when(permissionTarget) {
    "pick" -> pick=true
    "saver" -> {saver=true;store.calendarSaver=true}
    "notifications" -> {
     val allowed=Build.VERSION.SDK_INT<33 || androidx.core.content.ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED
     if(allowed) {notifications=true;store.calendarNotifications=true} else feedback=tr("Ενεργοποίησε τις ειδοποιήσεις από τις ρυθμίσεις Android.","Turn on notifications in Android settings.")
    }
   }
   CalendarEngine.refreshAsync(context)
  } else feedback=tr("Δεν δόθηκε άδεια ημερολογίου. Μπορείς να την επιτρέψεις από τις ρυθμίσεις Android.","Calendar permission was not granted. You can allow it in Android settings.")
 }
 fun request(target:String) {
  permissionTarget=target;feedback="";importedOk=false
  val requested=mutableListOf<String>()
  if(target=="pick" || store.calendarId!=IcsStorage.ID) requested.add(Manifest.permission.READ_CALENDAR)
  if(target=="notifications" && Build.VERSION.SDK_INT>=33) requested.add(Manifest.permission.POST_NOTIFICATIONS)
  permissions.launch(requested.toTypedArray())
 }
 val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {uri ->
  if(uri!=null) scope.launch {
   importing=true;feedback="";importedOk=false
   try {
    val count=withContext(Dispatchers.IO) {CalendarEngine.importIcs(context.applicationContext,uri)}
    selected=IcsStorage.ID;icsName=store.icsName
    feedback=tr("Εισήχθησαν $count συμβάντα από το ICS.","Imported $count events from the ICS file.");importedOk=true
   } catch(e:Exception) {DebugLog.error("ICS_IMPORT",e);feedback=e.message ?: tr("Αποτυχία εισαγωγής ICS.","ICS import failed.")}
   finally {importing=false}
  }
 }
 fun importIcs() {runCatching {importer.launch(arrayOf("*/*"))}.onFailure {feedback=tr("Δεν υπάρχει επιλογέας αρχείων στη συσκευή.","No file picker is available on this device.")}}
 fun chooseDevice() {if(CalendarEngine.permitted(context)) {pick=true;CalendarEngine.refreshAsync(context)} else request("pick")}
 LaunchedEffect(Unit) {CalendarEngine.refreshAsync(context)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Text(tr("Συναντήσεις","Meetings"),fontSize=24.sp,fontWeight=FontWeight.Bold)
  Text(tr("Επίλεξε ημερολόγιο της συσκευής ή εισήγαγε ένα αρχείο ICS για τις συναντήσεις σου.","Choose a device calendar or import an ICS file for your meetings."),fontSize=14.sp)
  Text(tr("Πηγή ημερολογίου","Calendar source"),fontSize=18.sp,fontWeight=FontWeight.Bold)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   OutlinedButton({chooseDevice()},Modifier.weight(1f).heightIn(min=56.dp),enabled=!importing) {Text(if(selected>=0) "✓ "+tr("Συσκευή","Device") else tr("Συσκευή","Device"))}
   OutlinedButton({importIcs()},Modifier.weight(1f).heightIn(min=56.dp),enabled=!importing) {Text(if(importing) tr("Εισαγωγή…","Importing…") else if(selected==IcsStorage.ID) "✓ "+tr("Αρχείο ICS","ICS file") else tr("Αρχείο ICS","ICS file"))}
  }
  if(selected==IcsStorage.ID) Text(tr("Αρχείο: ","File: ")+icsName,fontWeight=FontWeight.Bold)
  if(selected==IcsStorage.ID) Text(tr("Το ICS είναι τοπικό αντίγραφο. Για αλλαγές στο πρόγραμμα, εισήγαγε ξανά το ενημερωμένο αρχείο. Η «Ανανέωση τώρα» διαβάζει το αποθηκευμένο αντίγραφο.","The ICS is a local copy. For schedule changes, import the updated file again. “Refresh now” reads the stored copy."),fontSize=13.sp)
  Text(status,color=MaterialTheme.colorScheme.primary,fontSize=14.sp)
  if(feedback.isNotBlank()) Text(feedback,color=if(importedOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text(tr("Συναντήσεις στον screensaver","Meetings on the screensaver"),Modifier.weight(1f),fontSize=18.sp)
   Switch(saver,{if(it && !CalendarEngine.sourceAvailable(context)) request("saver") else {saver=it;store.calendarSaver=it}})
  }
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text(tr("Ειδοποιήσεις συναντήσεων","Meeting notifications"),Modifier.weight(1f),fontSize=18.sp)
   Switch(notifications,{if(it) request("notifications") else {notifications=false;store.calendarNotifications=false}})
  }
  Text(tr("Υπενθύμιση πριν τη συνάντηση","Reminder before the meeting"),fontSize=18.sp,fontWeight=FontWeight.Bold)
  Text(tr("Επίλεξε πολλούς χρόνους μαζί, π.χ. 30 και 5 λεπτά.","Pick several times together, e.g. 30 and 5 minutes."),fontSize=13.sp)
  listOf(listOf(0,5,10),listOf(15,30,60)).forEach {row ->
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    row.forEach {minutes ->
     val checked=minutes in reminders
     OutlinedButton({reminders=if(checked) reminders-minutes else reminders+minutes;store.calendarReminders=reminders},Modifier.weight(1f).heightIn(min=56.dp),contentPadding=PaddingValues(4.dp)) {
      Text((if(checked) "✓ " else "")+(if(minutes==0) tr("Στην έναρξη","At start") else tr("$minutes λεπτά","$minutes min")),fontSize=13.sp)
     }
    }
   }
  }
  Text(if(reminders.isEmpty()) tr("Δεν έχει επιλεγεί υπενθύμιση.","No reminder selected.") else tr("Επιλεγμένα: ","Selected: ")+reminders.sortedDescending().joinToString(" + ") {if(it==0) tr("στην έναρξη","at start") else "$it′"},color=MaterialTheme.colorScheme.primary)
  if(Build.VERSION.SDK_INT>=31) {
   OutlinedButton({runCatching {context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${context.packageName}")))}.onFailure {feedback=tr("Η συσκευή δεν προσφέρει ρύθμιση ακριβών ειδοποιήσεων.","This device offers no exact-alarm setting.")}},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(tr("Άδεια για ακριβή ώρα ειδοποίησης","Allow exact reminder time"))}
   Text(tr("Χωρίς αυτή την άδεια, το Android μπορεί να καθυστερεί τις υπενθυμίσεις.","Without this permission Android may delay reminders."),fontSize=13.sp)
  }
  Text(tr("Snooze · αναβολή για $snooze λεπτά","Snooze · postpone for $snooze min"),fontSize=18.sp,fontWeight=FontWeight.Bold)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   listOf(5,10,15,30).forEach {minutes ->OutlinedButton({snooze=minutes;store.calendarSnooze=minutes},Modifier.weight(1f).heightIn(min=56.dp),contentPadding=PaddingValues(0.dp)) {Text(if(snooze==minutes) "✓ $minutes′" else "$minutes′")}}
  }
  Text(tr("Snooze αντικαθιστά τις υπόλοιπες υπενθυμίσεις της συνάντησης με μία στον επιλεγμένο χρόνο. Cancel ακυρώνει τις υπενθυμίσεις μόνο αυτής της συνάντησης. Δεν διαγράφει το συμβάν.","Snooze replaces the meeting’s remaining reminders with one at the chosen time. Cancel stops the reminders of this meeting only. It does not delete the event."),fontSize=13.sp)
  Button({CalendarEngine.refreshAsync(context)},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(tr("Ανανέωση τώρα","Refresh now"))}
  OutlinedButton({runCatching {context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}"))) }},Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(tr("Άδειες και ειδοποιήσεις Android","Android permissions and notifications"))}
  Text(tr("Επόμενες συναντήσεις","Upcoming meetings"),fontSize=18.sp,fontWeight=FontWeight.Bold)
  if(meetings.isEmpty()) Text(tr("Δεν υπάρχουν διαθέσιμες συναντήσεις τις επόμενες 7 ημέρες.","No meetings in the next 7 days."),fontSize=14.sp)
  meetings.take(5).forEach {m ->MeetingCard(m)}
  Text(tr("Οι ολοήμερες εγγραφές δεν περιλαμβάνονται στις συναντήσεις.","All-day entries are not included in meetings."),fontSize=13.sp)
 }
 if(pick) AlertDialog(onDismissRequest={pick=false},title={Text(tr("Επιλογή ημερολογίου","Choose calendar"))},text={LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  if(calendars.isEmpty()) item {Text(tr("Δεν βρέθηκαν ημερολόγια. Πρόσθεσε ή συγχρόνισε ένα ημερολόγιο στη συσκευή.","No calendars found. Add or sync a calendar on the device."))}
  item {OutlinedButton({selected=-1;store.calendarId=-1;pick=false},Modifier.fillMaxWidth()) {Text(tr("Κανένα ημερολόγιο","No calendar"))}}
  items(calendars,key={it.id}) {calendar ->Card(onClick={selected=calendar.id;store.calendarId=calendar.id;pick=false}) {
   Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
    RadioButton(selected==calendar.id,null)
    Column(Modifier.weight(1f)) {Text(calendar.name,fontWeight=FontWeight.Bold);Text(calendar.account,fontSize=12.sp)}
   }
  }}
 }},confirmButton={TextButton({pick=false}) {Text(tr("Κλείσιμο","Close"))}})
}

@Composable fun MeetingCard(meeting:Meeting) {
 Card {Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
  Text(meeting.title,fontWeight=FontWeight.Bold,maxLines=2)
  Text(meeting.timeLabel(),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
  if(meeting.location.isNotBlank()) Text(meeting.location,fontSize=12.sp,maxLines=1)
 }}
}
