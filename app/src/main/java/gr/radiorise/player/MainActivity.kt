@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package gr.radiorise.player

import androidx.activity.compose.BackHandler
import android.os.SystemClock
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.delay
import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity: ComponentActivity() {
 private var openCalendar by mutableStateOf(false)
 private var openUpdate by mutableStateOf(false)
 override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);setIntent(intent);openCalendar=intent.action=="OPEN_CALENDAR";openUpdate=intent.action=="OPEN_UPDATE"}
 private var lastResume by mutableLongStateOf(0L)
 private var foreground by mutableStateOf(false)
 override fun onResume() {
  super.onResume();DebugLog.event("APP","onResume");CalendarEngine.requestRefresh(this);lastResume=SystemClock.elapsedRealtime();foreground=true
  Updater.foreground=true
  // Back from "Install unknown apps": continue the update automatically once the permission was given.
  (Updater.state.value as? UpdateState.Ready)?.let {if(it.needsPermission && Updater.canInstall(this)) Updater.install(this)}
  Updater.autoCheck(this)
 }
 override fun onPause() {DebugLog.event("APP","onPause");DebugLog.flush();foreground=false;Updater.foreground=false;super.onPause()}
 private var freshLaunch=false
 override fun onCreate(state:Bundle?) {
  super.onCreate(state)
  DebugLog.event("APP","onCreate restored=${state!=null}")
  openCalendar=intent.action=="OPEN_CALENDAR"
  openUpdate=intent.action=="OPEN_UPDATE"
  freshLaunch=state==null
  setContent {
   val store=remember {Store(applicationContext)}
   var revision by remember {mutableIntStateOf(0)}
   DisposableEffect(store) {
    val prefs=getSharedPreferences("radio",0)
    val listener=android.content.SharedPreferences.OnSharedPreferenceChangeListener {_,key->revision++;DebugLog.event("SETTINGS","changed=$key")}
    prefs.registerOnSharedPreferenceChangeListener(listener)
    onDispose {prefs.unregisterOnSharedPreferenceChangeListener(listener)}
   }
   val look=rememberLook(store,revision)
   AppTheme(look) {
    val background=MaterialTheme.colorScheme.background
    SideEffect {
     // Status / navigation bars follow the theme (light icons on dark themes and vice-versa).
     @Suppress("DEPRECATION") run {window.statusBarColor=background.toArgb();window.navigationBarColor=background.toArgb()}
     WindowCompat.getInsetsController(window,window.decorView).apply {isAppearanceLightStatusBars=!look.dark;isAppearanceLightNavigationBars=!look.dark}
    }
    LaunchedEffect(revision) {
     if(store.keepScreenOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    RadioUI(store,revision)
   }
  }
 }
 @Composable private fun RadioUI(store:Store,revision:Int) {
  var saverOpen by remember {mutableStateOf(false)}
  var lastTouch by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
  var folderPicking by remember {mutableStateOf(false)}
  var stations by remember { mutableStateOf(store.stations()) }
  var tab by remember { mutableIntStateOf(if(openCalendar) 1 else 0) }
  var settingsPage by remember {mutableIntStateOf(if(openCalendar) 5 else 0)}
  // Incremented on every navigation to Settings so the pager jumps even when the page number is unchanged.
  var settingsRequest by remember {mutableIntStateOf(0)}
  var query by remember { mutableStateOf("") }
  var searchOpen by remember { mutableStateOf(false) }
  var dialog by remember { mutableStateOf("") }
  var name by remember { mutableStateOf("") }
  var url by remember { mutableStateOf("") }
  var logo by remember { mutableStateOf("") }
  var group by remember { mutableStateOf("") }
  var reorderId by remember {mutableStateOf("")}
  var editing by remember { mutableStateOf<Station?>(null) }
  var pending by remember { mutableStateOf<List<Station>>(emptyList()) }
  var busy by remember { mutableStateOf(false) }
  LaunchedEffect(openCalendar) {if(openCalendar) {tab=1;settingsPage=5;settingsRequest++;saverOpen=false;dialog="";searchOpen=false;reorderId="";openCalendar=false}}
  val current by RadioService.current.collectAsState()
  val playing by RadioService.playing.collectAsState()
  val active by RadioService.active.collectAsState()
  val status by RadioService.status.collectAsState()
  val sleepAt by RadioService.sleepAt.collectAsState()
  val history by RadioService.history.collectAsState()
  val updateState by Updater.state.collectAsState()
  var announcedUpdate by remember {mutableStateOf("")}
  val scope=rememberCoroutineScope()
  val snack=remember { SnackbarHostState() }
  val clipboard=LocalClipboardManager.current
  fun message(s:String) { DebugLog.event("UI","message=$s");scope.launch { snack.currentSnackbarData?.dismiss();snack.showSnackbar(s) } }
  fun importText(text:String,base:String?=null) { pending=Playlist.parse(text,base);DebugLog.event("IMPORT","parsed=${pending.size} remote=${base!=null}"); require(pending.isNotEmpty()) { tr("Δεν βρέθηκαν σταθμοί","No stations found") }; dialog="preview" }
  val noPicker=tr("Δεν υπάρχει επιλογέας αρχείων στη συσκευή.","No file picker is available on this device.")
  val debugPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {granted ->
   if(granted) DebugLog.setEnabled(true) else message(tr("Η άδεια αποθήκευσης είναι απαραίτητη για logs στα Downloads σε Android 9.","Storage permission is required for logs in Downloads on Android 9."))
  }
  fun toggleDebug(value:Boolean) {
   if(value && Build.VERSION.SDK_INT<=28 && androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.WRITE_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED) debugPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
   else DebugLog.setEnabled(value)
  }
  val folderPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) {uri ->
   DebugLog.event("FOLDER","picker selected=${uri!=null}");folderPicking=false;lastTouch=SystemClock.elapsedRealtime()
   if(uri!=null) runCatching {
    contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
    DebugLog.event("FOLDER","persisted read permission")
    val old=store.photoFolder;store.photoFolder=uri.toString()
    if(old.isNotBlank() && old!=uri.toString()) runCatching {contentResolver.releasePersistableUriPermission(Uri.parse(old),Intent.FLAG_GRANT_READ_URI_PERMISSION)}
    message(tr("Ο φάκελος φωτογραφιών αποθηκεύτηκε","Photo folder saved"))
   }.onFailure {DebugLog.error("FOLDER",it);message(tr("Δεν ήταν δυνατή η πρόσβαση στον φάκελο. Διάλεξε άλλον φάκελο.","Could not access the folder. Choose another folder."))}
  }
  LaunchedEffect(foreground,lastResume,tab,dialog,searchOpen,reorderId,folderPicking,revision,lastTouch,saverOpen) {
   if(!foreground) {saverOpen=false;return@LaunchedEffect}
   if(!store.saverOn || tab!=0 || dialog.isNotEmpty() || searchOpen || reorderId.isNotEmpty() || folderPicking || saverOpen) return@LaunchedEffect
   delay((store.saverIdle*1000L-(SystemClock.elapsedRealtime()-maxOf(lastTouch,lastResume))).coerceAtLeast(0))
   DebugLog.event("SAVER","idle activation");saverOpen=true
  }
  // "Station colours" theme: extract the dominant colour of the playing station's logo.
  val activeLogo=stations.find {it.id==current}?.logo.orEmpty()
  val stationTheme=remember(revision) {store.theme==Themes.STATION}
  LaunchedEffect(stationTheme,activeLogo) {
   if(stationTheme && Playlist.valid(activeLogo)) logoSeed(applicationContext,activeLogo)?.let {(h,s) ->
    val seed=String.format(Locale.US,"%.1f,%.3f",h,s)
    if(seed!=store.themeSeed) store.themeSeed=seed
   }
  }
  val notifications=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if(!it) message(tr("Οι ειδοποιήσεις είναι απενεργοποιημένες","Notifications are turned off")) }
  val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
   busy=true
   try { val text=withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)!!.use { it.readLimited().toString(Charsets.UTF_8) } }; importText(text) }
   catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία εισαγωγής","Import failed")) } finally { busy=false }
  } }
  val txtExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri -> if(uri!=null) scope.launch {
   try { val text=Playlist.exportTxt(stations); withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }; message(tr("Η λίστα TXT εξάχθηκε","TXT list exported")) } catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία εξαγωγής","Export failed")) }
  } }
  val exporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri -> if(uri!=null) scope.launch {
   try { val text=Playlist.export(stations); withContext(Dispatchers.IO) { contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) } }; message(tr("Η λίστα εξάχθηκε","List exported")) } catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία εξαγωγής","Export failed")) }
  } }
  fun play(s:Station) { try { RadioService.send(this,"PLAY",s.id) } catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία εκκίνησης","Could not start playback")) } }
  fun service(action:String) { try { RadioService.send(this@MainActivity,action) } catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία εκκίνησης","Could not start playback")) } }
  LaunchedEffect(Unit) {
   val cm=getSystemService(android.net.ConnectivityManager::class.java)
   val caps=cm.getNetworkCapabilities(cm.activeNetwork)
   DebugLog.event("DEVICE","network=${caps!=null} validated=${caps?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)} persistedFolders=${contentResolver.persistedUriPermissions.size} notifications=${androidx.core.app.NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()}")
   if(freshLaunch && store.appAuto && store.startup()!=null) { freshLaunch=false; try { RadioService.send(this@MainActivity,"APP") } catch(e:Exception) { DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία αυτόματης εκκίνησης","Auto-start failed")) } }
   // Ask once; afterwards the user can enable notifications from Android settings (no snackbar on every launch).
   val prefs=getSharedPreferences("radio",0)
   if(Build.VERSION.SDK_INT>=33 && androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !prefs.getBoolean("askedNotifications",false)) {
    prefs.edit().putBoolean("askedNotifications",true).apply();notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
   }
   val error=prefs.getString("bootError",null)
   if(error!=null) { message(tr("Το Android εμπόδισε την αυτόματη εκκίνηση. Άνοιξε τον σταθμό χειροκίνητα.","Android blocked the automatic start. Start the station manually.")); prefs.edit().remove("bootError").apply() }
  }
  // Tapped the "update ready" notification.
  LaunchedEffect(openUpdate) {if(openUpdate) {openUpdate=false;tab=0;saverOpen=false;dialog="update";Updater.installWhenIdle(this@MainActivity)}}
  // Automatic updates: installs by itself as soon as the radio stops playing.
  LaunchedEffect(updateState,active) {
   val ready=updateState as? UpdateState.Ready
   if(ready!=null && ready.auto && !active && !ready.needsPermission) {Updater.installWhenIdle(this@MainActivity);return@LaunchedEffect}
   // Otherwise tell the user once per version (ready-but-waiting, needs permission, or auto-update switched off).
   val release=(updateState as? UpdateState.Available)?.release ?: ready?.release ?: return@LaunchedEffect
   if(dialog=="update" || announcedUpdate==release.version) return@LaunchedEffect
   announcedUpdate=release.version
   val text=when {
    ready!=null && ready.needsPermission -> tr("Η ενημέρωση ${release.version} θέλει άδεια εγκατάστασης","Update ${release.version} needs install permission")
    ready!=null -> tr("Η ${release.version} είναι έτοιμη — θα μπει όταν σταματήσει το ραδιόφωνο","${release.version} is ready — installs when the radio stops")
    else -> tr("Νέα έκδοση ${release.version} διαθέσιμη","New version ${release.version} available")
   }
   val result=snack.showSnackbar(text,actionLabel=tr("Δες","View"),withDismissAction=true,duration=SnackbarDuration.Long)
   if(result==SnackbarResult.ActionPerformed) dialog="update"
  }
  BackHandler(enabled=tab==1 && !saverOpen) {tab=0;lastTouch=SystemClock.elapsedRealtime()}
  val broadcast by RadioService.broadcast.collectAsState()
  val buffered by RadioService.bufferedSeconds.collectAsState()
  Scaffold(modifier=Modifier.pointerInput(Unit) {
   awaitPointerEventScope {while(true) {
    val event=awaitPointerEvent(PointerEventPass.Initial)
    if(event.changes.any {it.pressed || it.previousPressed}) lastTouch=SystemClock.elapsedRealtime()
   }}
  },snackbarHost={ SnackbarHost(snack) },topBar={
   if(tab==1) Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal=8.dp).height(52.dp),verticalAlignment=Alignment.CenterVertically) {
    FilledTonalButton({tab=0},Modifier.height(44.dp),shape=RoundedCornerShape(14.dp)) {Text("‹ "+tr("Πίσω στον player","Back to player"))}
   }
  }) { padding ->
   if(tab==0) CarRadioScreen(Modifier.padding(padding),store.playerRight,stations,current,playing,active,status,broadcast,buffered,query,{query=it},
    showBars=remember(revision) {store.showBars},sleepAt=sleepAt,hasHistory=history.isNotEmpty(),
    activeGroup=remember(revision) {store.activeGroup},onGroup={store.activeGroup=it},
    onPlay={ (stations.find { it.id==current } ?: store.startup() ?: stations.firstOrNull())?.let(::play) },
    onPause={ service("PAUSE") },
    onNext={ service("NEXT") },onPrevious={ service("PREVIOUS") },
    onSearch={searchOpen=true},onSettings={settingsPage=0;settingsRequest++;tab=1},
    onSleep={dialog="sleep"},onHistory={dialog="history"},
    onReorder={reorderId=it.id},
    onStation={play(it)},onEdit={editing=it;name=it.name;url=it.url;logo=it.logo;group=it.group;dialog="add"},
    )
   else CarSettingsScreen(Modifier.padding(padding),store,stations,revision,
    onFolder={folderPicking=true;runCatching {folderPicker.launch(null)}.onFailure {folderPicking=false;DebugLog.error("FOLDER_PICKER",it);message(tr("Δεν υπάρχει επιλογέας φακέλου στη συσκευή.","No folder picker is available on this device."))}},onSaverPreview={DebugLog.event("SAVER","preview");saverOpen=true},onDebug={toggleDebug(it)},
    onAdd={editing=null;name="";url="";logo="";group=store.activeGroup.takeIf {it!=Groups.NONE}.orEmpty();dialog="add"},onImport={dialog="import"},
    onExportM3u={runCatching {exporter.launch("AutoTuneRadio-stations.m3u")}.onFailure {DebugLog.error("EXPORT",it);message(noPicker)}},onExportTxt={runCatching {txtExporter.launch("AutoTuneRadio-stations.txt")}.onFailure {DebugLog.error("EXPORT",it);message(noPicker)}},
    onSystem={runCatching {startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:$packageName")))}.onFailure {message(tr("Δεν ήταν δυνατό να ανοίξουν οι ρυθμίσεις Android.","Could not open Android settings."))}},initialPage=settingsPage,pageRequest=settingsRequest,onUpdates={dialog="update"},
    onAutoGroup={val (list,n)=Groups.autoAssign(stations);if(n>0) {stations=list;store.save(list)};message(if(n>0) tr("Μπήκε ομάδα σε $n σταθμούς","$n stations grouped") else tr("Δεν βρέθηκε ομάδα από τα ονόματα. Όρισέ την από το ⋮.","No group found from the names. Set it from ⋮."))})
  }
  if(saverOpen) ScreenSaver(this@MainActivity,store,stations.find {it.id==current}?.name.orEmpty(),broadcast) {DebugLog.event("SAVER","dismiss");saverOpen=false;lastTouch=SystemClock.elapsedRealtime()}
  if(reorderId.isNotEmpty()) {
   val index=stations.indexOfFirst {it.id==reorderId}
   val station=stations.getOrNull(index)
   if(station==null) { LaunchedEffect(reorderId) {reorderId=""} }
   else AlertDialog(onDismissRequest={reorderId=""},title={Text(tr("Σειρά σταθμού","Station order"))},text={Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
    Text(station.name,fontWeight=FontWeight.Bold,fontSize=20.sp)
    Text(tr("Θέση ${index+1} από ${stations.size}","Position ${index+1} of ${stations.size}"))
    fun move(target:Int) {
     if(target in stations.indices && target!=index) {val reordered=stations.toMutableList();val moving=reordered.removeAt(index);reordered.add(target,moving);stations=reordered;store.save(stations);DebugLog.event("STATIONS","reorder from=$index to=$target")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     FilledTonalButton({move(index-1)},Modifier.weight(1f).heightIn(min=60.dp),enabled=index>0) {Text("↑ "+tr("Πάνω","Up"))}
     FilledTonalButton({move(index+1)},Modifier.weight(1f).heightIn(min=60.dp),enabled=index<stations.lastIndex) {Text("↓ "+tr("Κάτω","Down"))}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     OutlinedButton({move(0)},Modifier.weight(1f).heightIn(min=52.dp),enabled=index>0) {Text("⤒ "+tr("Πρώτος","First"))}
     OutlinedButton({move(stations.lastIndex)},Modifier.weight(1f).heightIn(min=52.dp),enabled=index<stations.lastIndex) {Text("⤓ "+tr("Τελευταίος","Last"))}
    }
    Text(tr("Η σειρά αποθηκεύεται αμέσως και ισχύει και για το τιμόνι.","The order is saved immediately and also applies to the steering-wheel buttons."),fontSize=13.sp)
   }},confirmButton={TextButton({reorderId=""}) {Text(tr("Έτοιμο","Done"))}})
  }
  if(searchOpen) AlertDialog(onDismissRequest={searchOpen=false},title={Text(tr("Αναζήτηση · ${stations.size} σταθμοί","Search · ${stations.size} stations"))},text={
   OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),placeholder={Text(tr("Σταθμός…","Station…"))},singleLine=true)
  },confirmButton={TextButton({searchOpen=false}) {Text(tr("Εμφάνιση","Show"))}},dismissButton={TextButton({query="";searchOpen=false}) {Text(tr("Όλοι","All"))}})
  if(dialog=="add") AlertDialog(onDismissRequest={dialog=""},title={Text(if(editing==null) tr("Νέος σταθμός","New station") else tr("Επεξεργασία σταθμού","Edit station"))},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   OutlinedTextField(name,{name=it},label={Text(tr("Όνομα","Name"))},singleLine=true)
   OutlinedTextField(url,{url=it},label={Text("Stream URL (HTTP/HTTPS)")},singleLine=true)
   OutlinedTextField(logo,{logo=it},label={Text(tr("URL λογότυπου (προαιρετικό)","Logo URL (optional)"))},singleLine=true)
   GroupPicker(group,Groups.used(stations)) {group=it}
   Text(tr("Δέχεται απευθείας stream, HLS .m3u8 ή σύνδεσμο .pls/.m3u του σταθμού.","Accepts a direct stream, HLS .m3u8 or the station's .pls/.m3u link."),fontSize=12.sp)
  }},confirmButton={TextButton({
   if(name.isBlank() || !Playlist.valid(url.trim()) || (logo.isNotBlank() && !Playlist.valid(logo.trim()))) message(tr("Συμπλήρωσε όνομα και έγκυρα HTTP/HTTPS URLs","Enter a name and valid HTTP/HTTPS URLs")) else {
    val s=Station(editing?.id ?: java.util.UUID.randomUUID().toString(),name.trim(),url.trim(),logo.trim(),Groups.normalize(group)); val list=stations.filterNot { it.id==s.id }
    if(list.any { it.url==s.url }) message(tr("Αυτό το URL υπάρχει ήδη","This URL already exists")) else { stations=if(editing==null) stations+s else stations.map {if(it.id==s.id) s else it};store.save(stations);dialog="" }
   }
  }) { Text(tr("Αποθήκευση","Save")) }},dismissButton={Row { if(editing!=null) TextButton({dialog="delete"}) { Text(tr("Διαγραφή","Delete"),color=MaterialTheme.colorScheme.error) }; TextButton({dialog=""}) { Text(tr("Άκυρο","Cancel")) } }})
  if(dialog=="delete") AlertDialog(onDismissRequest={dialog=""},title={Text(tr("Διαγραφή ${editing?.name};","Delete ${editing?.name}?"))},text={Text(tr("Ο σταθμός θα αφαιρεθεί από τη λίστα.","The station will be removed from the list."))},confirmButton={TextButton({
   val id=editing?.id; if(current==id) service("STOP"); stations=stations.filterNot { it.id==id };store.save(stations); if(store.chosen==id) store.chosen=""; if(store.last==id) store.last="";dialog=""
  }) { Text(tr("Διαγραφή","Delete"),color=MaterialTheme.colorScheme.error) }},dismissButton={TextButton({dialog=""}) { Text(tr("Άκυρο","Cancel")) }})
  if(dialog=="import") AlertDialog(onDismissRequest={dialog=""},title={Text(tr("Εισαγωγή σταθμών","Import stations"))},text={Text(tr("M3U, απλές M3U8, PLS ή TXT: όνομα, stream URL, logo URL (####Stations####). Θα δεις προεπισκόπηση πριν την προσθήκη.","M3U, simple M3U8, PLS or TXT: name, stream URL, logo URL (####Stations####). You will see a preview before anything is added."))},confirmButton={TextButton({dialog="";runCatching {importer.launch(arrayOf("*/*"))}.onFailure {DebugLog.error("IMPORT",it);message(noPicker)}}) { Text(tr("Από αρχείο","From file")) }},dismissButton={Row { TextButton({url="";dialog="remote"}) { Text(tr("Από URL","From URL")) }; TextButton({dialog=""}) {Text(tr("Άκυρο","Cancel"))} }})
  if(dialog=="remote") AlertDialog(onDismissRequest={if(!busy) dialog=""},title={Text(tr("URL λίστας","List URL"))},text={OutlinedTextField(url,{url=it},label={Text("https://…/stations.m3u")},singleLine=true)},confirmButton={TextButton({scope.launch {
   busy=true; try { val (text,base)=withContext(Dispatchers.IO) { Playlist.download(url.trim()) }; importText(text,base) } catch(e:Exception) {DebugLog.error("APP",e);message(e.message ?: tr("Αποτυχία λήψης","Download failed"))} finally {busy=false}
  }},enabled=!busy) {Text(if(busy) tr("Λήψη…","Downloading…") else tr("Φόρτωση","Load"))}},dismissButton={TextButton({dialog=""},enabled=!busy) {Text(tr("Άκυρο","Cancel"))}})
  if(dialog=="preview") {
   val fresh=remember(pending,stations) {pending.count {p -> stations.none {it.url==p.url}}}
   AlertDialog(onDismissRequest={dialog=""},title={Text(tr("${pending.size} σταθμοί βρέθηκαν","${pending.size} stations found"))},text={Column {
    Text(tr("Νέοι: $fresh · Υπάρχουν ήδη: ${pending.size-fresh}","New: $fresh · Already in list: ${pending.size-fresh}"),color=MaterialTheme.colorScheme.primary,fontSize=13.sp)
    LazyColumn(Modifier.heightIn(max=320.dp)) { items(pending) { s -> Column(Modifier.padding(vertical=8.dp)) {Text(s.name,fontWeight=FontWeight.Bold);Text(s.url,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)} } }
   }},confirmButton={TextButton({val count=store.merge(pending);stations=store.stations();DebugLog.event("IMPORT","merged added=$count total=${stations.size}");dialog="";message(tr("Προστέθηκαν $count νέοι σταθμοί","$count new stations added"))}) {Text(tr("Εισαγωγή όλων","Import all"))}},dismissButton={TextButton({dialog=""}) {Text(tr("Άκυρο","Cancel"))}})
  }
  if(dialog=="update") UpdateDialog(updateState) {dialog=""}
  if(dialog=="sleep") SleepDialog(sleepAt,active,store.sleepMinutes,onSet={minutes ->
   if(minutes>0) store.sleepMinutes=minutes
   runCatching {RadioService.send(this@MainActivity,"SLEEP",minutes=minutes)}.onFailure {DebugLog.error("SLEEP",it)}
   dialog=""
   message(if(minutes>0) tr("Το ραδιόφωνο θα σβήσει σε $minutes λεπτά","The radio will stop in $minutes minutes") else tr("Ο χρονοδιακόπτης ύπνου ακυρώθηκε","Sleep timer cancelled"))
  },onDismiss={dialog=""})
  if(dialog=="history") HistoryDialog(history,
   onCopy={clipboard.setText(AnnotatedString(it.title));message(tr("Αντιγράφηκε","Copied"))},
   onSearch={entry ->
    val ok=runCatching {startActivity(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY,entry.title))}.isSuccess ||
     runCatching {startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+Uri.encode(entry.title))))}.isSuccess
    if(!ok) message(tr("Δεν βρέθηκε εφαρμογή αναζήτησης/browser.","No search app or browser found."))
   },
   onClear={RadioService.history.value=emptyList();dialog=""},onDismiss={dialog=""})
 }
}

@Composable fun SleepDialog(sleepAt:Long,active:Boolean,last:Int,onSet:(Int)->Unit,onDismiss:()->Unit) {
 var now by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(sleepAt) {while(sleepAt>0) {now=SystemClock.elapsedRealtime();delay(1000)}}
 AlertDialog(onDismissRequest=onDismiss,icon={Icon(AppIcons.Timer,null)},title={Text(tr("Χρονοδιακόπτης ύπνου","Sleep timer"))},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
  if(sleepAt>0) {
   val left=((sleepAt-now)/1000).coerceAtLeast(0)
   Text(tr("Απομένουν ","Remaining ")+String.format(Locale.US,"%d:%02d",left/60,left%60),fontSize=22.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)
  }
  if(!active) Text(tr("Ξεκίνα πρώτα έναν σταθμό.","Start a station first."),color=MaterialTheme.colorScheme.error)
  Text(tr("Ο ήχος χαμηλώνει απαλά για 8 δευτερόλεπτα και μετά σταματά.","The sound fades out gently over 8 seconds, then stops."),fontSize=13.sp)
  listOf(listOf(15,30,45),listOf(60,90,120)).forEach {row ->
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    row.forEach {m ->
     if(m==last) FilledTonalButton({onSet(m)},Modifier.weight(1f).heightIn(min=56.dp),enabled=active,contentPadding=PaddingValues(0.dp)) {Text("$m′",fontSize=18.sp)}
     else OutlinedButton({onSet(m)},Modifier.weight(1f).heightIn(min=56.dp),enabled=active,contentPadding=PaddingValues(0.dp)) {Text("$m′",fontSize=18.sp)}
    }
   }
  }
 }},confirmButton={TextButton(onDismiss) {Text(tr("Κλείσιμο","Close"))}},dismissButton={if(sleepAt>0) TextButton({onSet(0)}) {Text(tr("Ακύρωση χρονοδιακόπτη","Cancel timer"),color=MaterialTheme.colorScheme.error)}})
}

@Composable fun HistoryDialog(history:List<TrackEntry>,onCopy:(TrackEntry)->Unit,onSearch:(TrackEntry)->Unit,onClear:()->Unit,onDismiss:()->Unit) {
 val fmt=remember(Lang.english) {SimpleDateFormat("HH:mm",Lang.locale)}
 AlertDialog(onDismissRequest=onDismiss,icon={Icon(AppIcons.MusicNote,null)},title={Text(tr("Τι έπαιξε","Recently played"))},text={
  if(history.isEmpty()) Text(tr("Δεν υπάρχουν ακόμα τίτλοι. Εμφανίζονται όταν ο σταθμός στέλνει πληροφορίες τραγουδιού.","No titles yet. They appear when the station sends song information."))
  else LazyColumn(Modifier.heightIn(max=380.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
   items(history) {entry ->
    Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHighest)) {Column(Modifier.fillMaxWidth().padding(start=12.dp,top=10.dp,end=4.dp)) {
     Text(entry.title,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis)
     Text("${entry.station} · ${fmt.format(Date(entry.time))}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
     Row {
      TextButton({onCopy(entry)}) {Text(tr("Αντιγραφή","Copy"))}
      TextButton({onSearch(entry)}) {Text(tr("Αναζήτηση στο web","Search the web"))}
     }
    }}
   }
  }
 },confirmButton={TextButton(onDismiss) {Text(tr("Κλείσιμο","Close"))}},dismissButton={if(history.isNotEmpty()) TextButton(onClear) {Text(tr("Καθαρισμός","Clear"))}})
}

/** Pick a preset genre, an existing custom group, or type a new one. */
@OptIn(ExperimentalLayoutApi::class)
@Composable fun GroupPicker(value:String,used:List<String>,onChange:(String)->Unit) {
 val options=(listOf("")+Groups.presets.map {it.key}+used).distinct()
 val custom=value.isNotBlank() && Groups.presets.none {it.key==value}
 Text(tr("Ομάδα","Group"),fontWeight=FontWeight.Bold)
 FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
  options.forEach {g ->
   FilterChip(selected=g==value,onClick={onChange(g)},label={Text(if(g.isBlank()) tr("Καμία","None") else Groups.label(g))})
  }
 }
 OutlinedTextField(if(custom) value else "",{onChange(it)},label={Text(tr("Άλλη ομάδα (γράψε όνομα)","Other group (type a name)"))},singleLine=true)
}
