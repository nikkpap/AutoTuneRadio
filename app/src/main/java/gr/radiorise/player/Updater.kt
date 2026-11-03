package gr.radiorise.player

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** A GitHub release that carries an installable APK. */
data class ReleaseInfo(val tag:String,val version:String,val title:String,val notes:String,val apkUrl:String,val apkName:String,val size:Long,val sha256:String?,val pageUrl:String)

sealed interface UpdateState {
 data object Idle:UpdateState
 data object Checking:UpdateState
 data class UpToDate(val latest:String):UpdateState
 data class Available(val release:ReleaseInfo):UpdateState
 data class Downloading(val release:ReleaseInfo,val progress:Float):UpdateState
 /** Downloaded and verified. auto=true → installs by itself as soon as the radio is not playing. */
 data class Ready(val release:ReleaseInfo,val file:File,val needsPermission:Boolean,val auto:Boolean=false):UpdateState
 data class Installing(val release:ReleaseInfo):UpdateState
 data class Error(val message:String,val release:ReleaseInfo?=null):UpdateState
}

/**
 * Automatic updates from GitHub Releases (every push to main publishes one):
 *  • checks when the app opens (at most every 30 min) and every 6 hours in the background,
 *  • downloads the APK by itself (SHA-256 verified),
 *  • installs it by itself when the radio is not playing.
 * Android 12+ installs silently once the app has updated itself one time; older Android shows its confirmation
 * screen (or a notification when the app is in the background). The APK must be signed with the same key.
 */
object Updater {
 val state=MutableStateFlow<UpdateState>(UpdateState.Idle)
 /** Set by MainActivity: an install confirmation may open directly only while the app is on screen. */
 @Volatile var foreground=false
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private val downloadLock=Mutex()
 private var job:Job?=null
 private const val ACTION_STATUS="gr.radiorise.player.UPDATE_STATUS"
 private const val OPEN_INTERVAL=30*60*1000L
 private const val CHANNEL="updates"
 private const val NOTIFICATION_ID=300

 /** Compares dotted versions numerically: 0.23.10 > 0.23.9. */
 fun compare(a:String,b:String):Int {
  val x=a.trim().removePrefix("v").removePrefix("V").split('.','-','+').map {it.toIntOrNull() ?: 0}
  val y=b.trim().removePrefix("v").removePrefix("V").split('.','-','+').map {it.toIntOrNull() ?: 0}
  for(i in 0 until maxOf(x.size,y.size)) {val d=x.getOrElse(i){0}.compareTo(y.getOrElse(i){0});if(d!=0) return d}
  return 0
 }

 private fun open(url:String):HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
  connectTimeout=12000;readTimeout=30000;instanceFollowRedirects=true
  setRequestProperty("User-Agent","AutoTuneRadio/${BuildConfig.VERSION_NAME}")
  setRequestProperty("Accept","application/vnd.github+json")
 }

 /** Reads the latest published release (blocking — call on IO). */
 fun fetchLatest():ReleaseInfo {
  val c=open("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
  try {
   when(c.responseCode) {
    200 -> {}
    404 -> throw IllegalStateException(tr("Δεν υπάρχει ακόμα δημοσιευμένη έκδοση στο GitHub.","No release has been published on GitHub yet."))
    403,429 -> throw IllegalStateException(tr("Το GitHub περιόρισε προσωρινά τους ελέγχους. Δοκίμασε σε λίγο.","GitHub temporarily limited checks. Try again shortly."))
    else -> throw IllegalStateException("GitHub HTTP ${c.responseCode}")
   }
   val json=JSONObject(c.inputStream.use {it.readLimited().toString(Charsets.UTF_8)})
   val tag=json.getString("tag_name")
   val assets=json.optJSONArray("assets")
   var apk:JSONObject?=null
   if(assets!=null) for(i in 0 until assets.length()) {val a=assets.getJSONObject(i);if(a.optString("name").endsWith(".apk",true)) {apk=a;break}}
   apk ?: throw IllegalStateException(tr("Η έκδοση $tag δεν έχει αρχείο APK.","Release $tag has no APK file."))
   val digest=apk.optString("digest").takeIf {it.startsWith("sha256:")}?.substringAfter("sha256:")
   return ReleaseInfo(tag,tag.removePrefix("v").removePrefix("V"),json.optString("name").ifBlank {tag},cleanNotes(json.optString("body")),
    apk.getString("browser_download_url"),apk.getString("name"),apk.optLong("size",-1),digest,json.optString("html_url"))
  } finally {c.disconnect()}
 }
 /** Release notes = the commit message; the install hint added by CI is not useful inside the app. */
 private fun cleanNotes(body:String)=body.substringBefore("\n---").trim().take(4000)

 fun isNewer(release:ReleaseInfo)=compare(release.version,BuildConfig.VERSION_NAME)>0

 /** Downloads + verifies the APK (suspending, IO). Reuses an already downloaded identical file. */
 private suspend fun downloadFile(app:Context,release:ReleaseInfo,onProgress:(Float)->Unit):File = downloadLock.withLock {
  withContext(Dispatchers.IO) {
   val dir=File(app.cacheDir,"updates")
   val target=File(dir,release.apkName.replace(Regex("[^A-Za-z0-9._-]"),"_"))
   if(target.exists() && release.size>0 && target.length()==release.size && (release.sha256==null || sha256(target).equals(release.sha256,true))) return@withContext target
   dir.deleteRecursively();dir.mkdirs()
   val conn=open(release.apkUrl).apply {setRequestProperty("Accept","application/octet-stream")}
   try {
    if(conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
    val total=conn.contentLengthLong.takeIf {it>0} ?: release.size
    val digest=MessageDigest.getInstance("SHA-256")
    var done=0L;var last=0f
    conn.inputStream.use {input -> target.outputStream().use {out ->
     val buffer=ByteArray(64*1024)
     while(true) {
      val n=input.read(buffer);if(n<0) break
      out.write(buffer,0,n);digest.update(buffer,0,n);done+=n
      if(done>200_000_000L) throw IllegalStateException(tr("Το αρχείο είναι υπερβολικά μεγάλο.","The file is too large."))
      if(total>0) {val p=(done.toFloat()/total).coerceIn(0f,1f);if(p-last>=0.01f) {last=p;onProgress(p)}}
     }
    }}
    if(release.size>0 && done!=release.size) {target.delete();throw IllegalStateException(tr("Η λήψη έμεινε ατελής. Δοκίμασε ξανά.","The download is incomplete. Try again."))}
    val hex=digest.digest().joinToString("") {"%02x".format(it)}
    if(release.sha256!=null && !hex.equals(release.sha256,true)) {target.delete();throw IllegalStateException(tr("Ο έλεγχος SHA-256 απέτυχε — το αρχείο δεν εγκαταστάθηκε.","SHA-256 check failed — the file was not installed."))}
    DebugLog.event("UPDATE","downloaded ${release.version} bytes=$done verified=${release.sha256!=null}")
    target
   } finally {conn.disconnect()}
  }
 }
 private fun sha256(file:File):String {
  val d=MessageDigest.getInstance("SHA-256")
  file.inputStream().use {input -> val b=ByteArray(64*1024);while(true) {val n=input.read(b);if(n<0) break;d.update(b,0,n)}}
  return d.digest().joinToString("") {"%02x".format(it)}
 }

 /** Manual or automatic check. With automatic updates on, a newer version is downloaded and installed by itself. */
 fun check(c:Context,silent:Boolean=false) {
  if(job?.isActive==true) return
  val app=c.applicationContext
  job=scope.launch {
   if(!silent) state.value=UpdateState.Checking
   try {
    val release=withContext(Dispatchers.IO) {fetchLatest()}
    Store(app).lastUpdateCheck=System.currentTimeMillis()
    val newer=isNewer(release)
    DebugLog.event("UPDATE","latest=${release.version} current=${BuildConfig.VERSION_NAME} newer=$newer")
    when {
     !newer -> state.value=if(silent) UpdateState.Idle else UpdateState.UpToDate(release.version)
     Store(app).autoUpdate -> runDownload(app,release,auto=true)
     else -> state.value=UpdateState.Available(release)
    }
   } catch(e:CancellationException) {throw e} catch(e:Exception) {
    DebugLog.error("UPDATE_CHECK",e)
    state.value=if(silent) UpdateState.Idle else UpdateState.Error(e.message ?: tr("Αποτυχία ελέγχου ενημερώσεων.","Update check failed."))
   }
  }
 }

 /** Called when the app opens / returns to the screen. */
 fun autoCheck(c:Context) {
  val store=Store(c)
  if(!store.autoUpdate) return
  when(val s=state.value) {
   is UpdateState.Ready -> {if(s.auto) installWhenIdle(c);return}
   UpdateState.Idle,is UpdateState.UpToDate -> {}
   else -> return
  }
  if(System.currentTimeMillis()-store.lastUpdateCheck<OPEN_INTERVAL) return
  check(c,silent=true)
 }

 /** User pressed "Download & install". */
 fun download(c:Context,release:ReleaseInfo) {
  if(job?.isActive==true) return
  val app=c.applicationContext
  job=scope.launch {runDownload(app,release,auto=false)}
 }

 private suspend fun runDownload(app:Context,release:ReleaseInfo,auto:Boolean) {
  state.value=UpdateState.Downloading(release,0f)
  try {
   val file=downloadFile(app,release) {p -> state.value=UpdateState.Downloading(release,p)}
   state.value=UpdateState.Ready(release,file,!canInstall(app),auto)
   if(auto) installWhenIdle(app) else install(app)
  } catch(e:CancellationException) {throw e} catch(e:Exception) {
   DebugLog.error("UPDATE_DOWNLOAD",e)
   state.value=if(auto) UpdateState.Available(release) else UpdateState.Error(e.message ?: tr("Αποτυχία λήψης.","Download failed."),release)
  }
 }

 /** Installing replaces the running app, so it never happens in the middle of a song. */
 fun installWhenIdle(c:Context) {
  val ready=state.value as? UpdateState.Ready ?: return
  if(RadioService.active.value) {DebugLog.event("UPDATE","deferred: radio is playing");return}
  if(ready.needsPermission && !canInstall(c)) {notifyReady(c.applicationContext,ready.release);return}
  install(c)
 }

 fun canInstall(c:Context)=Build.VERSION.SDK_INT<26 || c.packageManager.canRequestPackageInstalls()

 /** Opens Android's "Install unknown apps" page for this app. */
 fun openInstallPermission(c:Context) {
  runCatching {c.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${c.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
   .onFailure {DebugLog.error("UPDATE_PERMISSION",it);state.value=UpdateState.Error(tr("Άνοιξε Ρυθμίσεις Android → Εφαρμογές → AutoTune Radio → «Εγκατάσταση άγνωστων εφαρμογών».","Open Android Settings → Apps → AutoTune Radio → “Install unknown apps”."))}
 }

 /** Installs the downloaded APK (state must be Ready). */
 fun install(c:Context) {
  val ready=state.value as? UpdateState.Ready ?: return
  val app=c.applicationContext
  if(!canInstall(app)) {state.value=ready.copy(needsPermission=true);return}
  if(!ready.file.exists()) {state.value=UpdateState.Available(ready.release);return}
  state.value=UpdateState.Installing(ready.release)
  scope.launch {
   try {
    withContext(Dispatchers.IO) {
     val installer=app.packageManager.packageInstaller
     val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
      setAppPackageName(app.packageName)
      // Android 12+: no confirmation screen when this app installed the previous version itself.
      if(Build.VERSION.SDK_INT>=31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
     }
     val id=installer.createSession(params)
     installer.openSession(id).use {session ->
      session.openWrite("base.apk",0,ready.file.length()).use {out -> ready.file.inputStream().use {it.copyTo(out)};session.fsync(out)}
      val intent=Intent(app,UpdateReceiver::class.java).setAction(ACTION_STATUS).setPackage(app.packageName)
      // MUTABLE: the system adds the install status extras to this intent.
      val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      session.commit(PendingIntent.getBroadcast(app,id,intent,flags).intentSender)
     }
    }
    DebugLog.event("UPDATE","session committed ${ready.release.version}")
   } catch(e:CancellationException) {throw e} catch(e:Exception) {
    DebugLog.error("UPDATE_INSTALL",e)
    state.value=UpdateState.Error(e.message ?: tr("Αποτυχία εγκατάστασης.","Installation failed."),ready.release)
   }
  }
 }

 fun dismiss() {
  when(state.value) {is UpdateState.Downloading,is UpdateState.Installing,is UpdateState.Ready,UpdateState.Checking -> {};else -> state.value=UpdateState.Idle}
 }

 private fun channel(c:Context) {
  c.getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,tr("Ενημερώσεις εφαρμογής","App updates"),NotificationManager.IMPORTANCE_DEFAULT))
 }
 private fun canNotify(c:Context)=(Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(c,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) && NotificationManagerCompat.from(c).areNotificationsEnabled()

 /** Background: "update ready — tap to install". The tap opens the app, which continues the installation. */
 @android.annotation.SuppressLint("MissingPermission")
 private fun notifyReady(c:Context,release:ReleaseInfo,confirm:Intent?=null) {
  if(!canNotify(c)) return
  channel(c)
  val target=confirm ?: Intent(c,MainActivity::class.java).setAction("OPEN_UPDATE").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
  val pi=PendingIntent.getActivity(c,NOTIFICATION_ID,target,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val n=NotificationCompat.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download_done)
   .setContentTitle(tr("AutoTune Radio ${release.version} έτοιμο","AutoTune Radio ${release.version} ready"))
   .setContentText(tr("Πάτα για εγκατάσταση της ενημέρωσης.","Tap to install the update."))
   .setStyle(NotificationCompat.BigTextStyle().bigText(release.notes.ifBlank {tr("Πάτα για εγκατάσταση της ενημέρωσης.","Tap to install the update.")}))
   .setContentIntent(pi).setAutoCancel(true).build()
  NotificationManagerCompat.from(c).notify(NOTIFICATION_ID,n)
 }

 internal fun onStatus(c:Context,intent:Intent) {
  val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
  val message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
  DebugLog.event("UPDATE","status=$status message=$message foreground=$foreground")
  val release=(state.value as? UpdateState.Installing)?.release
  fun readyAgain(auto:Boolean)=release?.let {r -> File(c.cacheDir,"updates").listFiles()?.firstOrNull()?.let {UpdateState.Ready(r,it,false,auto)}}
  when(status) {
   PackageInstaller.STATUS_PENDING_USER_ACTION -> {
    val confirm=IntentCompat.getParcelableExtra(intent,Intent.EXTRA_INTENT,Intent::class.java)
    when {
     confirm==null -> state.value=UpdateState.Error(tr("Το Android δεν άνοιξε την οθόνη εγκατάστασης.","Android did not open the install screen."),release)
     foreground -> runCatching {c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {DebugLog.error("UPDATE_CONFIRM",it);release?.let {r -> notifyReady(c,r,confirm)}}
     else -> {release?.let {r -> notifyReady(c,r,confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))};state.value=readyAgain(false) ?: UpdateState.Idle}
    }
   }
   PackageInstaller.STATUS_SUCCESS -> state.value=UpdateState.Idle
   // The user pressed "Cancel": keep the file, do not ask again automatically.
   PackageInstaller.STATUS_FAILURE_ABORTED -> state.value=readyAgain(false) ?: UpdateState.Idle
   PackageInstaller.STATUS_FAILURE_CONFLICT,PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> state.value=UpdateState.Error(
    tr("Η νέα έκδοση είναι υπογεγραμμένη με διαφορετικό κλειδί από την εγκατεστημένη. Απεγκατάστησε την παλιά (χάνονται οι ρυθμίσεις) ή φτιάξε το κλειδί στο GitHub.","The new version is signed with a different key than the installed one. Uninstall the old app (settings are lost) or fix the key on GitHub.")+if(message.isNotBlank()) "\n($message)" else "",release)
   PackageInstaller.STATUS_FAILURE_STORAGE -> state.value=UpdateState.Error(tr("Δεν υπάρχει αρκετός χώρος στη συσκευή.","Not enough storage on the device."),release)
   else -> state.value=UpdateState.Error(tr("Η εγκατάσταση απέτυχε","Installation failed")+if(message.isNotBlank()) ": $message" else ".",release)
  }
 }

 /** Background check every 6 hours (needs network). */
 fun schedule(c:Context) {
  val work=PeriodicWorkRequestBuilder<UpdateWorker>(6,TimeUnit.HOURS)
   .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
  WorkManager.getInstance(c).enqueueUniquePeriodicWork("app-update",ExistingPeriodicWorkPolicy.KEEP,work)
 }

 /** Worker body: check → download → install when the radio is idle. */
 internal suspend fun background(app:Context) {
  if(!Store(app).autoUpdate) return
  when(state.value) {is UpdateState.Downloading,is UpdateState.Installing,UpdateState.Checking -> return;else -> {}}
  val release=withContext(Dispatchers.IO) {fetchLatest()}
  Store(app).lastUpdateCheck=System.currentTimeMillis()
  if(!isNewer(release)) return
  DebugLog.event("UPDATE","background found ${release.version}")
  withContext(Dispatchers.Main) {runDownload(app,release,auto=true)}
 }
}

class UpdateReceiver:BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {Updater.onStatus(c,i)}
}

class UpdateWorker(c:Context,p:WorkerParameters):CoroutineWorker(c,p) {
 override suspend fun doWork():Result = try {Updater.background(applicationContext);Result.success()}
  catch(e:CancellationException) {throw e} catch(e:Exception) {DebugLog.error("UPDATE_WORKER",e);Result.success()}
}
