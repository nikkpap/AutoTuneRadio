package gr.radiorise.player

import android.Manifest
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class RadioApplication:Application() {
 override fun onCreate() {
  super.onCreate();Lang.init(this);DebugLog.init(this);CalendarEngine.init(this)
 }
}

object DebugLog {
 val output=MutableStateFlow("Debug Off")
 @Volatile private var enabled=false
 private lateinit var context:Context
 private val worker=Executors.newSingleThreadScheduledExecutor()
 private var local:File?=null
 private var target:Uri?=null
 private var legacy:File?=null
 private var dirty=false
 private var publishing:java.util.concurrent.ScheduledFuture<*>?=null
 private var sequence=0
 private var fileName=""
 private const val LIMIT=2_000_000L
 fun init(c:Context) {
  context=c.applicationContext
  val previous=Thread.getDefaultUncaughtExceptionHandler()
  Thread.setDefaultUncaughtExceptionHandler {thread,error ->
   if(enabled) runCatching {
    worker.submit {append("CRASH","thread=${thread.name} ${safe(error.stackTraceToString())}");publish()}.get(1500,TimeUnit.MILLISECONDS)
   }
   previous?.uncaughtException(thread,error)
  }
  if(context.getSharedPreferences("radio",0).getBoolean("debugOn",false)) setEnabled(true)
 }
 fun setEnabled(value:Boolean) {
  if(value && Build.VERSION.SDK_INT<=28 && ContextCompat.checkSelfPermission(context,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) {
   output.value=tr("Χρειάζεται άδεια αποθήκευσης για τα Downloads.","Storage permission is required for Downloads.");return
  }
  context.getSharedPreferences("radio",0).edit().putBoolean("debugOn",value).apply()
  enabled=value
  worker.execute {
   try {
    if(value) {
     if(local==null || (target==null && legacy==null)) newFile()
     if(publishing==null) publishing=worker.scheduleWithFixedDelay({publish()},2,2,TimeUnit.SECONDS)
     append("DEBUG","ON version=${BuildConfig.VERSION_NAME} sdk=${Build.VERSION.SDK_INT} device=${Build.MANUFACTURER} ${Build.MODEL}")
     val store=Store(context)
     append("CONFIG","stations=${store.stations().size} lang=${if(Lang.english) "en" else "el"} theme=${store.theme} duck=${store.duckNav} boot=${store.boot} auto=${store.appAuto} delay=${store.delay} buffer=${store.buffer} eq=${store.eqOn}/${store.eqPreset} saver=${store.saverOn}/${store.saverPhotos} folder=${store.photoFolder.isNotBlank()}")
     publish()
    } else {append("DEBUG","OFF");publish();publishing?.cancel(false);publishing=null;if(!dirty) {local=null;target=null;legacy=null}}
   } catch(e:Exception) {output.value=tr("Αποτυχία log: ","Log failure: ")+e.javaClass.simpleName}
  }
 }
 fun event(area:String,details:String) {if(enabled) worker.execute {runCatching {append(area,safe(details))}.onFailure {output.value=tr("Αποτυχία log: ","Log failure: ")+it.javaClass.simpleName}}}
 fun error(area:String,error:Throwable) = event(area,"${error.javaClass.simpleName}: ${error.stackTraceToString()}")
 fun flush() {worker.execute {publish()}}
 private fun safe(text:String):String = LogRedactor.sanitize(text)
 private fun newFile() {
  val stamp=SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(Date())
  fileName="AutoTuneRadio_${stamp}_${sequence++}.log"
  var newTarget:Uri?=null
  var newLegacy:File?=null
  if(Build.VERSION.SDK_INT>=29) {
   val values=ContentValues().apply {
    put(MediaStore.Downloads.DISPLAY_NAME,fileName);put(MediaStore.Downloads.MIME_TYPE,"text/plain")
    put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/AutoTuneRadio/Logs")
   }
   newTarget=context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values)
    ?: throw IllegalStateException("Downloads unavailable")
  } else {
   @Suppress("DEPRECATION") val dir=File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"AutoTuneRadio/Logs")
   check(dir.exists() || dir.mkdirs());newLegacy=File(dir,fileName)
  }
  target=newTarget;legacy=newLegacy
  local=File(context.filesDir,"debug-current.log").also {it.writeText("")}
  dirty=true
 }
 private fun append(area:String,details:String) {
  if(local==null) return
  if(local!!.length()>=LIMIT) {publish();if(dirty) throw java.io.IOException("Cannot publish full debug file");newFile()}
  val time=SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",Locale.US).format(Date())
  local!!.appendText("$time [$area] ${safe(details)}\n");dirty=true
 }
 private fun publish() {
  if(!dirty || local==null) return
  try {
   if(target!=null) context.contentResolver.openOutputStream(target!!,"wt")?.use {out ->local!!.inputStream().use {it.copyTo(out)}} ?: throw IllegalStateException("Cannot write Downloads")
   else if(legacy!=null) local!!.copyTo(legacy!!,overwrite=true) else throw IllegalStateException("No Downloads destination")
   dirty=false
   output.value="Download/AutoTuneRadio/Logs/$fileName"
  } catch(e:Exception) {output.value=tr("Αποτυχία εγγραφής Downloads: ","Downloads write failed: ")+e.javaClass.simpleName}
 }
}
