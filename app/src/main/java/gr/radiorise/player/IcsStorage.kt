package gr.radiorise.player

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import java.io.File
import java.time.ZoneId

object IcsStorage {
 const val ID=-2L
 private fun file(c:Context)=AtomicFile(File(c.filesDir,"calendar.ics"))
 fun exists(c:Context)=file(c).baseFile.exists()
 fun importFile(c:Context,uri:Uri):Int {
  val bytes=c.contentResolver.openInputStream(uri)?.use {it.readLimited()} ?: error(tr("Δεν ήταν δυνατό να διαβαστεί το ICS.","Could not read the ICS file."))
  val text=bytes.toString(Charsets.UTF_8)
  val calendar=IcsCalendar.parse(text,ZoneId.systemDefault())
  require(calendar.eventCount>0) {tr("Δεν βρέθηκαν συμβάντα VEVENT στο αρχείο.","No VEVENT events found in the file.")}
  val now=System.currentTimeMillis()
  calendar.expand(now,now+7*24*60*60*1000L) // Validate bounded expansion before replacing a working import.
  var name="calendar.ics"
  c.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {if(it.moveToFirst()) name=it.getString(0).orEmpty().ifBlank {name}}
  val atomic=file(c);val stream=atomic.startWrite()
  try {stream.write(bytes);atomic.finishWrite(stream)} catch(e:Exception) {atomic.failWrite(stream);throw e}
  Store(c).icsName=name
  DebugLog.event("ICS","import events=${calendar.eventCount} bytes=${bytes.size}")
  return calendar.eventCount
 }
 fun meetings(c:Context,now:Long):List<Meeting> {
  val text=file(c).openRead().use {it.readLimited().toString(Charsets.UTF_8)}
  return IcsCalendar.parse(text,ZoneId.systemDefault()).expand(now,now+7*24*60*60*1000L).map {
   Meeting(ID,it.eventId,it.begin,it.end,it.title,it.location)
  }.filter {it.end>now}.distinctBy {it.key}
 }
}
