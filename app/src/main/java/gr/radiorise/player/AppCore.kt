package gr.radiorise.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** group: preset key (news, sports, rock …, see Groups) or a custom name; "" = no group. */
data class Station(val id: String = UUID.randomUUID().toString(), val name: String, val url: String, val logo: String = "", val group: String = "")
class Store(context: Context) {
 private val p = context.getSharedPreferences("radio", Context.MODE_PRIVATE)
 var reverseWheel: Boolean get() = p.getBoolean("reverseWheel",false); set(v) { p.edit().putBoolean("reverseWheel",v).apply() }
 var playerRight: Boolean get() = p.getBoolean("playerRight",false); set(v) { p.edit().putBoolean("playerRight",v).apply() }
 var buffer: Int get() = p.getInt("buffer",5).coerceIn(1,30); set(v) { p.edit().putInt("buffer",v.coerceIn(1,30)).apply() }
 var appAuto: Boolean get() = p.getBoolean("appAuto",true); set(v) { p.edit().putBoolean("appAuto",v).apply() }
 var boot: Boolean get() = p.getBoolean("boot", false); set(v) { p.edit().putBoolean("boot",v).apply() }
 var delay: Int get() = p.getInt("delay",10).coerceIn(0,300); set(v) { p.edit().putInt("delay",v.coerceIn(0,300)).apply() }
 var chosen: String get() = p.getString("chosen","")!!; set(v) { p.edit().putString("chosen",v).apply() }
 var last: String get() = p.getString("last","")!!; set(v) { p.edit().putString("last",v).apply() }
 var resume: Boolean get() = p.getBoolean("resume",true); set(v) { p.edit().putBoolean("resume",v).apply() }
 var saverOn: Boolean get() = p.getBoolean("saverOn",false); set(v) { p.edit().putBoolean("saverOn",v).apply() }
 var saverPhotos: Boolean get() = p.getBoolean("saverPhotos",false); set(v) { p.edit().putBoolean("saverPhotos",v).apply() }
 var saverIdle: Int get() = p.getInt("saverIdle",60).coerceIn(15,600); set(v) { p.edit().putInt("saverIdle",v.coerceIn(15,600)).apply() }
 var photoInterval: Int get() = p.getInt("photoInterval",15).coerceIn(5,120); set(v) { p.edit().putInt("photoInterval",v.coerceIn(5,120)).apply() }
 var photoFolder: String get() = p.getString("photoFolder","").orEmpty(); set(v) { p.edit().putString("photoFolder",v).apply() }
 var icsName: String get() = p.getString("icsName","").orEmpty(); set(v) {p.edit().putString("icsName",v).apply()}
 var calendarId: Long get() = p.getLong("calendarId",-1L); set(v) {p.edit().putLong("calendarId",v).apply()}
 var calendarSaver: Boolean get() = p.getBoolean("calendarSaver",false); set(v) {p.edit().putBoolean("calendarSaver",v).apply()}
 var calendarNotifications: Boolean get() = p.getBoolean("calendarNotifications",false); set(v) {p.edit().putBoolean("calendarNotifications",v).apply()}
 var calendarSnooze: Int get() = p.getInt("calendarSnooze",5).coerceIn(1,60); set(v) {p.edit().putInt("calendarSnooze",v.coerceIn(1,60)).apply()}
 var calendarReminders: Set<Int>
  get() = p.getStringSet("calendarReminders",setOf("30","5")).orEmpty().mapNotNull {it.toIntOrNull()}.filter {it in 0..1440}.toSet()
  set(v) {p.edit().putStringSet("calendarReminders",v.filter {it in 0..1440}.map {it.toString()}.toSet()).apply()}
 var theme: String get() = p.getString("theme","ocean").orEmpty().ifBlank {"ocean"}; set(v) { p.edit().putString("theme",v).apply() }
 var themeSeed: String get() = p.getString("themeSeed","").orEmpty(); set(v) { p.edit().putString("themeSeed",v).apply() }
 /** Lower the radio volume (instead of pausing) while navigation / short prompts speak. */
 var duckNav: Boolean get() = p.getBoolean("duckNav",true); set(v) { p.edit().putBoolean("duckNav",v).apply() }
 var showBars: Boolean get() = p.getBoolean("showBars",true); set(v) { p.edit().putBoolean("showBars",v).apply() }
 var keepScreenOn: Boolean get() = p.getBoolean("keepScreenOn",false); set(v) { p.edit().putBoolean("keepScreenOn",v).apply() }
 var sleepMinutes: Int get() = p.getInt("sleepMinutes",30).coerceIn(5,180); set(v) { p.edit().putInt("sleepMinutes",v.coerceIn(5,180)).apply() }
 /** Check GitHub for a new version automatically (at most every 6 hours). */
 var autoUpdate: Boolean get() = p.getBoolean("autoUpdate",true); set(v) { p.edit().putBoolean("autoUpdate",v).apply() }
 var lastUpdateCheck: Long get() = p.getLong("lastUpdateCheck",0L); set(v) { p.edit().putLong("lastUpdateCheck",v).apply() }
 var eqOn: Boolean get() = p.getBoolean("eqOn",false); set(v) { p.edit().putBoolean("eqOn",v).apply() }
 var eqPreset: String get() = p.getString("eqPreset","Flat").orEmpty(); set(v) {
  val edit=p.edit().putString("eqPreset",v)
  if(v in listOf("Flat","Bass","Voice","Rock","Pop")) edit.putString("eqBasePreset",v)
  edit.apply()
 }
 // Keep the selected factory preset when slider edits switch the active curve to Custom.
 val eqBasePreset: String get() = (if(eqPreset!="Custom") eqPreset else p.getString("eqBasePreset","Flat"))
  ?.takeIf {it in listOf("Flat","Bass","Voice","Rock","Pop")} ?: "Flat"
 fun eqLevel(frequency:Int):Int = p.getInt("eqBand$frequency",0)
 fun setEqLevel(frequency:Int,level:Int) { p.edit().putInt("eqBand$frequency",level).putString("eqBasePreset",eqBasePreset).putString("eqPreset","Custom").apply() }
 fun eqCurve():List<Int> = when(eqPreset) {
  "Bass" -> listOf(5,4,2,0,0)
  "Voice" -> listOf(-2,0,3,4,1)
  "Rock" -> listOf(4,2,-1,2,4)
  "Pop" -> listOf(-1,2,4,2,-1)
  else -> listOf(0,0,0,0,0)
 }
 fun stations(): List<Station> = runCatching {
  val a=JSONArray(p.getString("stations","[]")); (0 until a.length()).map { i -> val o=a.getJSONObject(i); Station(o.getString("id"),o.getString("name"),o.getString("url"),o.optString("logo",""),o.optString("group","")) }
 }.getOrDefault(emptyList())
 fun save(s: List<Station>) { DebugLog.event("STATIONS","save total=${s.size}");val a=JSONArray(); s.forEach { a.put(JSONObject().put("id",it.id).put("name",it.name).put("url",it.url).put("logo",it.logo).put("group",it.group)) }; p.edit().putString("stations",a.toString()).apply() }
 /** Adds new stations (by URL). Existing stations without a group take the group from the imported list. */
 fun merge(s: List<Station>): Int {
  val old=stations(); val incoming=s.associateBy { it.url }
  val updated=old.map { o -> if(o.group.isBlank()) incoming[o.url]?.group?.takeIf { it.isNotBlank() }?.let { o.copy(group=it) } ?: o else o }
  val all=(updated+s).distinctBy { it.url }; save(all); return all.size-old.size
 }
 /** "" = all stations, GROUP_NONE = stations without a group, otherwise a group key. */
 var activeGroup: String get() = p.getString("activeGroup","").orEmpty(); set(v) { p.edit().putString("activeGroup",v).apply() }
 /** Steering-wheel next/previous stays inside the selected group. */
 var skipInGroup: Boolean get() = p.getBoolean("skipInGroup",true); set(v) { p.edit().putBoolean("skipInGroup",v).apply() }
 fun startup(): Station? {val all=stations();return all.find {it.id==chosen} ?: all.find {it.id==last} ?: all.firstOrNull()}
}
object Playlist {
 fun valid(u: String): Boolean = runCatching { val x=URI(u); x.scheme.lowercase() in listOf("http","https") && !x.host.isNullOrBlank() }.getOrDefault(false)
 fun parse(text: String, base: String? = null): List<Station> {
  require(!isHls(text)) { tr("Αυτό είναι HLS stream. Πρόσθεσέ το ως URL σταθμού, όχι ως λίστα.","This is an HLS stream. Add it as a station URL, not as a list.") }
  fun resolve(u: String) = runCatching { if(base == null) u.trim() else URI(base).resolve(u.trim()).toString() }.getOrDefault("")
  val lines=text.removePrefix("\uFEFF").lineSequence().map { it.trim() }.toList()
  val files=sortedMapOf<Int,String>(); val names=mutableMapOf<Int,String>()
  val result=mutableListOf<Station>(); var title=""; var extGroup=""; var extLogo=""
  for(line in lines) {
   if(line.isBlank() || line.startsWith("####")) continue
   // CustomRadioPlayer TXT: name,stream URL,logo URL (CSV quoting supported).
   if(!line.startsWith("#") && !line.startsWith("[")) {
    val fields=csvFields(line)
    if(fields.size>=2) {
     val stream=resolve(fields[1].replace("&amp;","&"))
     if(valid(stream)) {
      val logo=fields.getOrNull(2)?.let { resolve(it.replace("&amp;","&")) }.orEmpty()
      result.add(Station(name=fields[0].ifBlank { URI(stream).host },url=stream,logo=logo.takeIf(::valid).orEmpty(),group=Groups.normalize(fields.getOrNull(3).orEmpty())))
      title=""; continue
     }
    }
   }
   val m=Regex("(?i)^(File|Title)(\\d+)=(.*)$").matchEntire(line)
   if(m!=null) { val i=m.groupValues[2].toInt(); if(m.groupValues[1].equals("file",true)) files[i]=resolve(m.groupValues[3]) else names[i]=m.groupValues[3]; continue }
   if(line.startsWith("#EXTINF",true)) {
    // #EXTINF:-1 tvg-logo="…" group-title="Rock",Station name  (the name follows the first comma outside quotes)
    var quoted=false; var comma=-1
    for(i in line.indices) { val ch=line[i]; if(ch=='"') quoted=!quoted else if(ch==',' && !quoted) { comma=i; break } }
    title=if(comma>=0) line.substring(comma+1).trim() else ""
    val head=if(comma>=0) line.substring(0,comma) else line
    extGroup=Regex("(?i)group-title=\"([^\"]*)\"").find(head)?.groupValues?.get(1).orEmpty().ifBlank { extGroup }
    extLogo=Regex("(?i)tvg-logo=\"([^\"]*)\"").find(head)?.groupValues?.get(1).orEmpty()
    continue
   }
   if(line.startsWith("#EXTGRP:",true)) { extGroup=line.substringAfter(':').trim(); continue }
   if(line.isBlank() || line.startsWith("#") || line.startsWith("[") || line.contains('=' ) && !line.startsWith("http",true)) continue
   val u=resolve(line); if(valid(u)) { result.add(Station(name=title.ifBlank { URI(u).host },url=u,logo=extLogo.takeIf(::valid).orEmpty(),group=Groups.normalize(extGroup))); title=""; extGroup=""; extLogo="" }
  }
  files.forEach { (i,u) -> if(valid(u)) result.add(Station(name=names[i].orEmpty().ifBlank { URI(u).host },url=u)) }
  return result.distinctBy { it.url }
 }
 fun isHls(text: String) = text.contains("#EXT-X-",true)
 /** Station URLs that point to a .pls/.m3u playlist (not HLS .m3u8) must be resolved to the real stream first. */
 fun isPlaylistUrl(u: String): Boolean = runCatching { val path=URI(u).path.orEmpty().lowercase(); path.endsWith(".pls") || path.endsWith(".m3u") }.getOrDefault(false)
 /** Returns the real stream URL behind a playlist link, and whether it is HLS. */
 fun resolveStream(url: String): Pair<String,Boolean> {
  val (text,base)=download(url)
  if(isHls(text)) return base to true
  val first=parse(text,base).firstOrNull() ?: throw IllegalStateException(tr("Η λίστα του σταθμού δεν περιέχει stream.","The station playlist contains no stream."))
  return first.url to first.url.substringBefore('?').lowercase().endsWith(".m3u8")
 }
 /** Downloads a list (max 2 MB). Follows up to 5 redirects, including http ↔ https which HttpURLConnection refuses on its own. */
 fun download(url: String): Pair<String,String> {
  require(valid(url)) { tr("Μη έγκυρο HTTP/HTTPS URL","Invalid HTTP/HTTPS URL") }
  var current=url
  repeat(6) {
   val c=URL(current).openConnection() as HttpURLConnection
   c.instanceFollowRedirects=false; c.connectTimeout=10000; c.readTimeout=15000; c.setRequestProperty("User-Agent","AutoTuneRadio/${BuildConfig.VERSION_NAME}")
   try {
    val code=c.responseCode
    if(code in 300..399) {
     val location=c.getHeaderField("Location") ?: throw IllegalStateException("HTTP $code")
     current=URI(current).resolve(location.trim()).toString()
     require(valid(current)) { tr("Μη έγκυρη ανακατεύθυνση","Invalid redirect") }
     DebugLog.event("IMPORT","redirect code=$code")
     return@repeat
    }
    require(code in 200..299) { "HTTP $code" }
    val bytes=c.inputStream.use { it.readLimited() }
    DebugLog.event("IMPORT","HTTP success bytes=${bytes.size}")
    return bytes.toString(Charsets.UTF_8) to current
   } finally { c.disconnect() }
  }
  throw IllegalStateException(tr("Πάρα πολλές ανακατευθύνσεις","Too many redirects"))
 }
 fun csvFields(line: String): List<String> {
  val fields=mutableListOf<String>(); val value=StringBuilder(); var quoted=false; var i=0
  while(i<line.length) {
   val c=line[i]
   when {
    c=='"' && quoted && i+1<line.length && line[i+1]=='"' -> { value.append('"'); i++ }
    c=='"' -> quoted=!quoted
    c==',' && !quoted -> { fields.add(value.toString().trim()); value.setLength(0) }
    else -> value.append(c)
   }; i++
  }
  fields.add(value.toString().trim()); return fields
 }
 fun exportTxt(s: List<Station>): String {
  fun quote(v:String):String = "\""+v.replace("\"","\"\"").replace('\n',' ')+"\""
  return "####Stations####\n"+s.joinToString("\n") { quote(it.name)+","+quote(it.url)+","+quote(it.logo)+(if(it.group.isNotBlank()) ","+quote(it.group) else "") }+"\n"
 }
 fun export(s: List<Station>) = "#EXTM3U\n"+s.joinToString("\n") {
  fun attr(v:String)=v.replace('"','\'').replace('\n',' ')
  val logo=if(it.logo.isNotBlank()) " tvg-logo=\"${attr(it.logo)}\"" else ""
  val group=if(it.group.isNotBlank()) " group-title=\"${attr(it.group)}\"" else ""
  "#EXTINF:-1$logo$group,${it.name.replace('\n',' ')}\n${it.url}"
 }+"\n"
}

fun java.io.InputStream.readLimited(): ByteArray {
 val out=java.io.ByteArrayOutputStream(); val buffer=ByteArray(8192)
 while(true) { val n=read(buffer); if(n<0) break; require(out.size()+n<=2_000_000) { tr("Το αρχείο ξεπερνά τα 2 MB","The file is larger than 2 MB") }; out.write(buffer,0,n) }
 return out.toByteArray()
}
