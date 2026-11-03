package gr.radiorise.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class ShortcutApp(val component:String,val name:String,val action:String,val category:String?)
private fun prefs(c:Context)=c.getSharedPreferences("radio",0)
private fun key(slot:Int)="appShortcut$slot"
@Composable private fun shortcutRevision():Int {
 val c=LocalContext.current
 var revision by remember {mutableIntStateOf(0)}
 DisposableEffect(c) {
  val p=prefs(c)
  val listener=SharedPreferences.OnSharedPreferenceChangeListener {_,k -> if(k?.startsWith("appShortcut")==true) revision++}
  p.registerOnSharedPreferenceChangeListener(listener)
  onDispose {p.unregisterOnSharedPreferenceChangeListener(listener)}
 }
 return revision
}
private fun selected(c:Context,slot:Int)=prefs(c).getString(key(slot),"").orEmpty()
private fun label(c:Context,slot:Int)=prefs(c).getString(key(slot)+"Name","").orEmpty().ifBlank {if(slot==0) tr("Προεπιλεγμένο τηλέφωνο","Default phone") else tr("Χωρίς εφαρμογή","No app")}
private fun save(c:Context,slot:Int,app:ShortcutApp?) {
 prefs(c).edit().putString(key(slot),app?.component.orEmpty()).putString(key(slot)+"Name",app?.name.orEmpty()).putString(key(slot)+"Action",app?.action).putString(key(slot)+"Category",app?.category).apply()
}
private fun openShortcut(c:Context,slot:Int) {
 try {
  val saved=selected(c,slot)
  val intent=if(saved.isBlank() && slot==0) Intent(Intent.ACTION_DIAL) else {
   val component=ComponentName.unflattenFromString(saved) ?: throw IllegalArgumentException("Missing shortcut")
   Intent(prefs(c).getString(key(slot)+"Action",Intent.ACTION_MAIN)).setComponent(component).also { intent ->
    prefs(c).getString(key(slot)+"Category",null)?.let {intent.addCategory(it)}
   }
  }
  c.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
 } catch(e:Exception) {
  DebugLog.error("APP_SHORTCUT",e)
  Toast.makeText(c,tr("Η εφαρμογή δεν ανοίγει. Επίλεξέ την ξανά στις Ρυθμίσεις → Οθόνη.","The app does not open. Choose it again in Settings → Display."),Toast.LENGTH_LONG).show()
 }
}
@Composable private fun ShortcutIcon(component:String,name:String) {
 val c=LocalContext.current
 var bitmap by remember(component) {mutableStateOf<ImageBitmap?>(null)}
 LaunchedEffect(component) {
  bitmap=withContext(Dispatchers.IO) {runCatching {c.packageManager.getActivityIcon(ComponentName.unflattenFromString(component)!!).toBitmap(64,64).asImageBitmap()}.getOrNull()}
 }
 bitmap?.let {Image(it,name,Modifier.size(28.dp))} ?: Icon(Icons.Default.Call,name)
}
@Composable fun PlayerAppShortcuts() {
 val c=LocalContext.current
 val revision=shortcutRevision()
 key(revision) {
  for(slot in 0..2) {
   val component=selected(c,slot)
   if(slot==0 || component.isNotBlank()) FilledTonalIconButton(onClick={openShortcut(c,slot)},modifier=Modifier.size(48.dp)) {
    if(component.isBlank()) Icon(Icons.Default.Call,tr("Άνοιγμα τηλεφώνου","Open phone")) else ShortcutIcon(component,label(c,slot))
   }
  }
 }
}
@Suppress("DEPRECATION")
private fun availableApps(c:Context):List<ShortcutApp> {
 val pm=c.packageManager
 val intents=listOf(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_INFO),Intent(Intent.ACTION_DIAL))
 return intents.flatMap { intent -> pm.queryIntentActivities(intent,0).filter {it.activityInfo.exported && it.activityInfo.enabled && it.activityInfo.applicationInfo.enabled && (it.activityInfo.permission==null || c.checkSelfPermission(it.activityInfo.permission)==android.content.pm.PackageManager.PERMISSION_GRANTED)}.map {
  ShortcutApp(ComponentName(it.activityInfo.packageName,it.activityInfo.name).flattenToString(),it.loadLabel(pm).toString(),intent.action!!,intent.categories?.firstOrNull())
 } }.distinctBy {it.component}.sortedBy {it.name.lowercase()}
}
@Composable fun AppShortcutSettings() {
 val c=LocalContext.current
 val revision=shortcutRevision()
 var picker by remember {mutableStateOf<Int?>(null)}
 Text(tr("Κουμπιά εφαρμογών","App buttons"),style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.Bold)
 Text(tr("Διάλεξε τι ανοίγει το Τηλέφωνο και πρόσθεσε έως δύο συντομεύσεις πάνω από το λογότυπο του player.","Choose what the Phone button opens and add up to two shortcuts above the player logo."))
 key(revision) {
  for(slot in 0..2) {
   Text(if(slot==0) tr("Τηλέφωνο","Phone") else tr("Πρόσθετο κουμπί $slot","Extra button $slot"),style=MaterialTheme.typography.titleMedium)
   OutlinedButton(onClick={picker=slot},modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
    Text(label(c,slot)+" · "+tr("Επιλογή εφαρμογής","Choose app"),maxLines=2,overflow=TextOverflow.Ellipsis)
   }
   if(selected(c,slot).isNotBlank()) TextButton(onClick={save(c,slot,null)}) {Text(if(slot==0) tr("Επαναφορά προεπιλεγμένου τηλεφώνου","Restore default phone") else tr("Αφαίρεση κουμπιού","Remove button"))}
  }
 }
 Text(tr("Περιλαμβάνονται εφαρμογές συστήματος που μπορούν να ανοίξουν, όπως BT Phone. Οι συντομεύσεις ανοίγουν την εφαρμογή κανονικά.","Includes system apps that can be opened, such as BT Phone. Shortcuts open the app normally."),style=MaterialTheme.typography.bodySmall)
 picker?.let {slot ->
  var query by remember(slot) {mutableStateOf("")}
  var failed by remember(slot) {mutableStateOf(false)}
  var apps by remember(slot) {mutableStateOf<List<ShortcutApp>?>(null)}
  LaunchedEffect(slot) {
   val result=withContext(Dispatchers.IO) {runCatching {availableApps(c)}}
   failed=result.isFailure
   result.exceptionOrNull()?.let {DebugLog.error("APP_PICKER",it)}
   apps=result.getOrDefault(emptyList())
  }
  AlertDialog(onDismissRequest={picker=null},title={Text(tr("Επιλογή εφαρμογής","Choose app"))},text={
   Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    OutlinedTextField(query,{query=it},label={Text(tr("Αναζήτηση εφαρμογής","Search app"))},singleLine=true,modifier=Modifier.fillMaxWidth())
    val matches=apps?.filter {it.name.contains(query,true) || it.component.contains(query,true)}
    if(apps==null) CircularProgressIndicator()
    else if(matches.isNullOrEmpty()) Text(if(failed) tr("Δεν ήταν δυνατή η φόρτωση εφαρμογών.","Could not load apps.") else tr("Δεν βρέθηκε εφαρμογή που μπορεί να ανοίξει.","No app that can be opened was found."))
    LazyColumn(Modifier.fillMaxWidth().heightIn(max=340.dp)) {
     items(matches.orEmpty(),key={it.component}) {app ->
      Row(Modifier.fillMaxWidth().clickable {save(c,slot,app);picker=null}.padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       ShortcutIcon(app.component,app.name)
       Column(Modifier.weight(1f)) {Text(app.name);Text(app.component.substringBefore('/'),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
      }
     }
    }
   }
  },confirmButton={TextButton(onClick={picker=null}) {Text(tr("Κλείσιμο","Close"))}})
 }
}
