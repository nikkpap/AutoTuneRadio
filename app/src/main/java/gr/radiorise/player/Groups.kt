package gr.radiorise.player

/**
 * Station groups (genres). Presets are stored by key and shown in the active language;
 * any other text is a custom group and is shown as typed.
 */
object Groups {
 const val NONE="~none"
 data class Preset(val key:String,val gr:String,val en:String,val words:List<String>)
 val presets=listOf(
  Preset("news","Ειδήσεις","News",listOf("news","ειδήσ","ειδησ","ενημέρ","ενημερ","info","talk","λόγος")),
  Preset("sports","Αθλητικά","Sports",listOf("sport","σπορ","σπόρ","αθλητ","football","ποδόσφ","ποδοσφ")),
  Preset("rock","Rock","Rock",listOf("rock","ροκ","metal","alternative")),
  Preset("pop","Pop / Hits","Pop / Hits",listOf("pop","hits","hit radio","top 40","chart")),
  Preset("dance","Dance","Dance",listOf("dance","club","edm","house","techno","trance")),
  Preset("rnb","R&B / Hip-Hop","R&B / Hip-Hop",listOf("rnb","r&b","hip hop","hip-hop","hiphop","rap","urban")),
  Preset("chill","Chill / Lounge","Chill / Lounge",listOf("chill","lounge","smooth","deep","relax","ambient","easy")),
  Preset("greek","Ελληνικά","Greek",listOf("ελληνικ","λαϊκ","λαικ","greek","laika","rebetiko","ρεμπέτ")),
  Preset("jazz","Jazz / Blues","Jazz / Blues",listOf("jazz","blues","soul","funk")),
  Preset("classical","Κλασική","Classical",listOf("classic","κλασικ","opera","όπερα","symphon")),
  Preset("kids","Παιδικά","Kids",listOf("kids","παιδ","children")),
 )
 fun label(group:String):String = when(group) {
  "" -> tr("Όλοι","All")
  NONE -> tr("Χωρίς ομάδα","No group")
  else -> presets.find {it.key==group}?.let {tr(it.gr,it.en)} ?: group
 }
 /** Best preset for a station name / genre text, or "" when nothing matches. */
 fun guess(text:String):String = presets.firstOrNull {p -> p.words.any {text.contains(it,true)}}?.key.orEmpty()
 /** Imported/typed group text → preset key when it is a known genre, otherwise the text itself. */
 fun normalize(raw:String):String {
  val t=raw.trim(); if(t.isBlank()) return ""
  presets.find {it.key.equals(t,true) || it.gr.equals(t,true) || it.en.equals(t,true)}?.let {return it.key}
  return guess(t).ifBlank {t.take(40)}
 }
 /** Groups in use, presets first (in preset order), then custom groups alphabetically. */
 fun used(stations:List<Station>):List<String> {
  val keys=stations.map {it.group}.filter {it.isNotBlank()}.toSet()
  return presets.map {it.key}.filter {it in keys}+(keys-presets.map {it.key}.toSet()).sortedBy {it.lowercase()}
 }
 fun matches(s:Station,group:String)=when(group) {"" -> true; NONE -> s.group.isBlank(); else -> s.group==group}
 /** Gives a guessed group to every station without one. Returns how many changed. */
 fun autoAssign(stations:List<Station>):Pair<List<Station>,Int> {
  var n=0
  val out=stations.map {s -> if(s.group.isNotBlank()) s else guess(s.name).takeIf {it.isNotBlank()}?.let {n++;s.copy(group=it)} ?: s}
  return out to n
 }
}
