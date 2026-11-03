package gr.radiorise.player

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.IntentCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** A GitHub release that carries an installable APK. */
data class ReleaseInfo(val tag:String,val version:String,val title:String,val notes:String,val apkUrl:String,val apkName:String,val size:Long,val sha256:String?,val pageUrl:String)

sealed interface UpdateState {
 data object Idle:UpdateState
 data object Checking:UpdateState
 data class UpToDate(val latest:String):UpdateState
 data class Available(val release:ReleaseInfo):UpdateState
 data class Downloading(val release:ReleaseInfo,val progress:Float):UpdateState
 /** Downloaded and verified; waiting for the user (or for the "install unknown apps" permission). */
 data class Ready(val release:ReleaseInfo,val file:File,val needsPermission:Boolean):UpdateState
 data class Installing(val release:ReleaseInfo):UpdateState
 data class Error(val message:String,val release:ReleaseInfo?=null):UpdateState
}

/**
 * In-app updates from GitHub Releases:
 * check (api.github.com/…/releases/latest) → download the .apk asset (progress + SHA-256 check) → install with PackageInstaller.
 * Android always shows its own confirmation screen; the APK must be signed with the same key as the installed app.
 */
object Updater {
 val state=MutableStateFlow<UpdateState>(UpdateState.Idle)
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var job:Job?=null
 private const val ACTION_STATUS="gr.radiorise.player.UPDATE_STATUS"
 private const val AUTO_INTERVAL=6*60*60*1000L

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

 /** Reads the latest published release. Runs on the IO dispatcher. */
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
   return ReleaseInfo(tag,tag.removePrefix("v").removePrefix("V"),json.optString("name").ifBlank {tag},json.optString("body").trim().take(4000),
    apk.getString("browser_download_url"),apk.getString("name"),apk.optLong("size",-1),digest,json.optString("html_url"))
  } finally {c.disconnect()}
 }

 fun check(c:Context,silent:Boolean=false) {
  if(job?.isActive==true) return
  val app=c.applicationContext
  job=scope.launch {
   if(!silent) state.value=UpdateState.Checking
   try {
    val release=withContext(Dispatchers.IO) {fetchLatest()}
    Store(app).lastUpdateCheck=System.currentTimeMillis()
    val newer=compare(release.version,BuildConfig.VERSION_NAME)>0
    DebugLog.event("UPDATE","latest=${release.version} current=${BuildConfig.VERSION_NAME} newer=$newer")
    state.value=if(newer) UpdateState.Available(release) else if(silent) UpdateState.Idle else UpdateState.UpToDate(release.version)
   } catch(e:CancellationException) {throw e} catch(e:Exception) {
    DebugLog.error("UPDATE_CHECK",e)
    state.value=if(silent) UpdateState.Idle else UpdateState.Error(e.message ?: tr("Αποτυχία ελέγχου ενημερώσεων.","Update check failed."))
   }
  }
 }

 /** Silent check at most every 6 hours, only when enabled in settings. */
 fun autoCheck(c:Context) {
  val store=Store(c)
  if(!store.autoUpdate || state.value !is UpdateState.Idle) return
  if(System.currentTimeMillis()-store.lastUpdateCheck<AUTO_INTERVAL) return
  check(c,silent=true)
 }

 fun download(c:Context,release:ReleaseInfo) {
  if(job?.isActive==true) return
  val app=c.applicationContext
  job=scope.launch {
   state.value=UpdateState.Downloading(release,0f)
   try {
    val file=withContext(Dispatchers.IO) {
     val dir=File(app.cacheDir,"updates").apply {deleteRecursively();mkdirs()}
     val target=File(dir,release.apkName.replace(Regex("[^A-Za-z0-9._-]"),"_"))
     val conn=open(release.apkUrl).apply {setRequestProperty("Accept","application/octet-stream")}
     try {
      if(conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
      val total=conn.contentLengthLong.takeIf {it>0} ?: release.size
      val digest=MessageDigest.getInstance("SHA-256")
      var done=0L;var lastPublished=0f
      conn.inputStream.use {input -> target.outputStream().use {out ->
       val buffer=ByteArray(64*1024)
       while(true) {
        val n=input.read(buffer);if(n<0) break
        out.write(buffer,0,n);digest.update(buffer,0,n);done+=n
        if(done>200_000_000L) throw IllegalStateException(tr("Το αρχείο είναι υπερβολικά μεγάλο.","The file is too large."))
        if(total>0) {val p=(done.toFloat()/total).coerceIn(0f,1f);if(p-lastPublished>=0.01f) {lastPublished=p;state.value=UpdateState.Downloading(release,p)}}
       }
      }}
      if(release.size>0 && done!=release.size) throw IllegalStateException(tr("Η λήψη έμεινε ατελής. Δοκίμασε ξανά.","The download is incomplete. Try again."))
      val hex=digest.digest().joinToString("") {"%02x".format(it)}
      if(release.sha256!=null && !hex.equals(release.sha256,true)) throw IllegalStateException(tr("Ο έλεγχος SHA-256 απέτυχε — το αρχείο δεν εγκαταστάθηκε.","SHA-256 check failed — the file was not installed."))
      DebugLog.event("UPDATE","downloaded bytes=$done verified=${release.sha256!=null}")
      target
     } finally {conn.disconnect()}
    }
    state.value=UpdateState.Ready(release,file,!canInstall(app))
    install(app)
   } catch(e:CancellationException) {throw e} catch(e:Exception) {
    DebugLog.error("UPDATE_DOWNLOAD",e)
    state.value=UpdateState.Error(e.message ?: tr("Αποτυχία λήψης.","Download failed."),release)
   }
  }
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
     val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {setAppPackageName(app.packageName)}
     val id=installer.createSession(params)
     installer.openSession(id).use {session ->
      session.openWrite("base.apk",0,ready.file.length()).use {out -> ready.file.inputStream().use {it.copyTo(out)};session.fsync(out)}
      val intent=Intent(app,UpdateReceiver::class.java).setAction(ACTION_STATUS).setPackage(app.packageName)
      // MUTABLE: the system adds the install status extras to this intent.
      val flags=PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
      session.commit(PendingIntent.getBroadcast(app,id,intent,flags).intentSender)
     }
    }
    DebugLog.event("UPDATE","session committed")
   } catch(e:CancellationException) {throw e} catch(e:Exception) {
    DebugLog.error("UPDATE_INSTALL",e)
    state.value=UpdateState.Error(e.message ?: tr("Αποτυχία εγκατάστασης.","Installation failed."),ready.release)
   }
  }
 }

 fun dismiss() {
  when(state.value) {is UpdateState.Downloading,is UpdateState.Installing,UpdateState.Checking -> {};else -> state.value=UpdateState.Idle}
 }

 internal fun onStatus(c:Context,intent:Intent) {
  val status=intent.getIntExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
  val message=intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
  DebugLog.event("UPDATE","status=$status message=$message")
  val release=(state.value as? UpdateState.Installing)?.release
  when(status) {
   PackageInstaller.STATUS_PENDING_USER_ACTION -> {
    val confirm=IntentCompat.getParcelableExtra(intent,Intent.EXTRA_INTENT,Intent::class.java)
    if(confirm==null) state.value=UpdateState.Error(tr("Το Android δεν άνοιξε την οθόνη εγκατάστασης.","Android did not open the install screen."),release)
    else runCatching {c.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}.onFailure {DebugLog.error("UPDATE_CONFIRM",it);state.value=UpdateState.Error(tr("Δεν άνοιξε η οθόνη εγκατάστασης.","The install screen could not open."),release)}
   }
   PackageInstaller.STATUS_SUCCESS -> state.value=UpdateState.Idle
   PackageInstaller.STATUS_FAILURE_ABORTED -> state.value=release?.let {r -> File(c.cacheDir,"updates").listFiles()?.firstOrNull()?.let {UpdateState.Ready(r,it,false)}} ?: UpdateState.Idle
   PackageInstaller.STATUS_FAILURE_CONFLICT,PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> state.value=UpdateState.Error(
    tr("Η νέα έκδοση είναι υπογεγραμμένη με διαφορετικό κλειδί από την εγκατεστημένη. Απεγκατάστησε την παλιά (χάνονται οι ρυθμίσεις) ή φτιάξε το κλειδί στο GitHub.","The new version is signed with a different key than the installed one. Uninstall the old app (settings are lost) or fix the key on GitHub.")+if(message.isNotBlank()) "\n($message)" else "",release)
   PackageInstaller.STATUS_FAILURE_STORAGE -> state.value=UpdateState.Error(tr("Δεν υπάρχει αρκετός χώρος στη συσκευή.","Not enough storage on the device."),release)
   else -> state.value=UpdateState.Error(tr("Η εγκατάσταση απέτυχε","Installation failed")+if(message.isNotBlank()) ": $message" else ".",release)
  }
 }
}

class UpdateReceiver:BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {Updater.onStatus(c,i)}
}
