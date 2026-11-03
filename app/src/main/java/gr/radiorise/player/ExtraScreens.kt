package gr.radiorise.player

import android.content.Context
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.documentfile.provider.DocumentFile
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EqState(val frequencies:List<Int> = emptyList(),val minimum:Int = -1500,val maximum:Int = 1500,val levels:List<Int> = emptyList(),val message:String = tr("Ξεκίνα έναν σταθμό για να εμφανιστούν οι μπάντες του equalizer.","Start a station to show the equalizer bands."))

@Composable fun EqualizerSettings(store:Store) {
 val state by RadioService.equalizerState.collectAsState()
 var enabled by remember {mutableStateOf(store.eqOn)}
 var preset by remember {mutableStateOf(store.eqPreset)}
 val resetPreset = remember(preset) {store.eqBasePreset}
 fun label(key:String)=if(key=="Voice") tr("Ομιλία","Voice") else key
 val resetLabel = label(resetPreset)
 var levels by remember(state) {mutableStateOf(state.levels)}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text("Equalizer",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold)
   Switch(enabled,{enabled=it;store.eqOn=it})
  }
  Text(tr("Οι επιλογές αποθηκεύονται και εφαρμόζονται και στον επόμενο σταθμό.","Choices are saved and also apply to the next station."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  listOf(listOf("Flat","Bass","Voice"),listOf("Rock","Pop")).forEach { row ->
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
    row.forEach { key ->
     val on=resetPreset==key
     if(on) FilledTonalButton({preset=key;store.eqPreset=key},Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(4.dp)) {Text("✓ ${label(key)}",maxLines=1)}
     else OutlinedButton({preset=key;store.eqPreset=key},Modifier.weight(1f).heightIn(min=52.dp),contentPadding=PaddingValues(4.dp)) {Text(label(key),maxLines=1)}
    }
   }
  }
  Text(if(preset=="Custom") "Preset: $resetLabel · "+tr("Χειροκίνητη ρύθμιση","Manual adjustment") else "Preset: $resetLabel",color=MaterialTheme.colorScheme.primary)
  Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {Text(tr("Κατάσταση: ","Status: ")+state.message,Modifier.fillMaxWidth().padding(12.dp),fontSize=15.sp)}
  state.frequencies.forEachIndexed {index,hz ->
   val level=levels.getOrElse(index){0}
   Text("${if(hz>=1000) String.format(Locale.US,"%.1f kHz",hz/1000f) else "$hz Hz"} · ${String.format(Locale.US,"%+.1f dB",level/100f)}")
   Slider(value=level.toFloat(),onValueChange={value ->
    // Preserve every currently audible band when changing from a preset to Custom.
    if(preset!="Custom") state.frequencies.forEachIndexed {i,f ->store.setEqLevel(f,levels.getOrElse(i){0})}
    levels=levels.toMutableList().also {if(index in it.indices) it[index]=value.toInt()}
    preset="Custom";store.setEqLevel(hz,value.toInt())
   },valueRange=state.minimum.toFloat()..state.maximum.toFloat(),enabled=enabled && state.maximum>state.minimum)
  }
  OutlinedButton({store.eqPreset=resetPreset;preset=resetPreset},Modifier.fillMaxWidth().heightIn(min=52.dp)) {Text(tr("Επαναφορά αρχικών ρυθμίσεων: ","Reset to preset: ")+resetLabel)}
 }
}

@Composable private fun Choice(selected:Boolean,text:String,modifier:Modifier,onClick:()->Unit) {
 if(selected) FilledTonalButton(onClick,modifier,contentPadding=PaddingValues(0.dp)) {Text("✓ $text",maxLines=1)}
 else OutlinedButton(onClick,modifier,contentPadding=PaddingValues(0.dp)) {Text(text,maxLines=1)}
}

@Composable fun SaverSettings(store:Store,onFolder:()->Unit,onPreview:()->Unit,revision:Int) {
 var enabled by remember {mutableStateOf(store.saverOn)}
 var photos by remember {mutableStateOf(store.saverPhotos)}
 var idle by remember {mutableIntStateOf(store.saverIdle)}
 var interval by remember {mutableIntStateOf(store.photoInterval)}
 val folder=remember(revision) {store.photoFolder}
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {Text("Screensaver",Modifier.weight(1f),fontSize=22.sp,fontWeight=FontWeight.Bold);Switch(enabled,{enabled=it;store.saverOn=it})}
  Text(tr("Ανοίγει όσο το app βρίσκεται στην οθόνη, μετά από αδράνεια. Ένα άγγιγμα επιστρέφει στον player.","Opens after inactivity while the app is on screen. One touch returns to the player."))
  Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   Choice(!photos,tr("Ρολόι","Clock"),Modifier.weight(1f).heightIn(min=56.dp)) {photos=false;store.saverPhotos=false}
   Choice(photos,tr("Φωτογραφίες","Photos"),Modifier.weight(1f).heightIn(min=56.dp)) {photos=true;store.saverPhotos=true}
  }
  Text(tr("Μετά από $idle δευτ. αδράνειας","After $idle s of inactivity"),fontWeight=FontWeight.Bold)
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(30,60,120,300).forEach {n ->Choice(idle==n,"$n",Modifier.weight(1f)) {idle=n;store.saverIdle=n}}}
  if(photos) {
   Button(onFolder,Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(tr("Επιλογή φακέλου φωτογραφιών","Choose photo folder"))}
   Text(if(folder.isBlank()) tr("Δεν έχει επιλεγεί φάκελος.","No folder selected.") else tr("Φάκελος επιλεγμένος · οι εικόνες φορτώνονται χωρίς αντιγραφή.","Folder selected · images load without copying."),fontSize=13.sp)
   Text(tr("Αλλαγή φωτογραφίας κάθε $interval δευτ.","Change photo every $interval s"),fontWeight=FontWeight.Bold)
   Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(5,15,30,60).forEach {n ->Choice(interval==n,"$n",Modifier.weight(1f)) {interval=n;store.photoInterval=n}}}
   Text(tr("Χρησιμοποιούνται οι εικόνες μέσα στον επιλεγμένο φάκελο, χωρίς υποφακέλους. Αν δεν υπάρχουν προσβάσιμες εικόνες, εμφανίζεται το ρολόι.","Images inside the selected folder are used, without subfolders. If no images are accessible, the clock is shown."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  OutlinedButton(onPreview,Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text(tr("Προεπισκόπηση","Preview"))}
 }
}

suspend fun loadFramePhotos(context:Context,folder:String):List<Uri> = withContext(Dispatchers.IO) {
 DebugLog.event("PHOTOS","scan folderSelected=${folder.isNotBlank()}")
 if(folder.isBlank()) emptyList() else runCatching {
  DocumentFile.fromTreeUri(context,Uri.parse(folder))?.listFiles()?.filter {it.isFile && it.type?.startsWith("image/")==true}?.sortedBy {it.name.orEmpty()}?.map {it.uri}.orEmpty()
 }.onSuccess {DebugLog.event("PHOTOS","scan count=${it.size}")}.onFailure {DebugLog.error("PHOTOS",it)}.getOrDefault(emptyList())
}

@Composable fun ScreenSaver(context:Context,store:Store,station:String,broadcast:String,onClose:()->Unit) {
 Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
  val view=LocalView.current
  DisposableEffect(view) {
   val window=(view.parent as? DialogWindowProvider)?.window
   window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
   onDispose {window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
  }
  BackHandler(onBack=onClose)
  val accent=LocalLook.current.accentOnBlack
  val agenda by CalendarEngine.meetings.collectAsState()
  val calendarStatus by CalendarEngine.status.collectAsState()
  var photos by remember {mutableStateOf<List<Uri>>(emptyList())}
  var loaded by remember {mutableStateOf(false)}
  var index by remember {mutableIntStateOf(0)}
  var now by remember {mutableStateOf(Date())}
  var failed by remember(index,photos) {mutableStateOf(false)}
  val clockFmt=remember(Lang.english) {SimpleDateFormat("HH:mm",Lang.locale)}
  val dateFmt=remember(Lang.english) {SimpleDateFormat("EEEE, d MMMM",Lang.locale)}
  LaunchedEffect(store.photoFolder,store.saverPhotos) {photos=if(store.saverPhotos) loadFramePhotos(context,store.photoFolder) else emptyList();loaded=true}
  LaunchedEffect(Unit) {while(true) {now=Date();delay(1000)}}
  LaunchedEffect(photos) {if(photos.size>1) while(true) {delay(store.photoInterval*1000L);index=(index+1)%photos.size}}
  val positions=listOf(Alignment.TopStart,Alignment.TopEnd,Alignment.BottomEnd,Alignment.BottomStart,Alignment.Center)
  val clockPosition=positions[((now.time/60000)%positions.size).toInt()]
  BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black).clickable(onClick=onClose)) {
   val clockSize=if(maxWidth<400.dp || maxHeight<300.dp) 52.sp else 76.sp
   val maxMeetings=if(maxHeight<400.dp) 1 else 2
   // Photos cross-fade instead of jumping.
   if(photos.isNotEmpty()) Crossfade(targetState=photos[index%photos.size],animationSpec=tween(900),label="photo") {uri ->
    SubcomposeAsyncImage(model=uri,contentDescription=tr("Φωτογραφία","Photo"),modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Fit,onError={DebugLog.error("PHOTO_LOAD",it.result.throwable);failed=true})
   }
   val clockOnly=photos.isEmpty() || failed
   if(store.calendarSaver) {
    val placement=if(photos.isNotEmpty()) Alignment.TopEnd else if(clockPosition==Alignment.TopStart || clockPosition==Alignment.TopEnd) Alignment.BottomEnd else Alignment.TopEnd
    Column(Modifier.align(placement).widthIn(max=360.dp).padding(16.dp).background(Color.Black.copy(alpha=0.7f)).padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
     Text(tr("Συναντήσεις","Meetings"),fontWeight=FontWeight.Bold,color=accent)
     val upcoming=agenda.filter {it.end>now.time}.take(maxMeetings)
     if(upcoming.isEmpty()) Text(if(CalendarEngine.sourceAvailable(context)) tr("Δεν υπάρχουν επόμενες συναντήσεις","No upcoming meetings") else calendarStatus,color=Color.LightGray,fontSize=12.sp)
     upcoming.forEach {m ->MeetingCard(m)}
    }
   }

   Column(Modifier.align(if(clockOnly) clockPosition else Alignment.BottomStart).padding(32.dp).background(Color.Black.copy(alpha=0.55f)).padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
    Text(clockFmt.format(now),fontSize=if(clockOnly) clockSize else 38.sp,color=accent,fontWeight=FontWeight.Light)
    Text(dateFmt.format(now),color=Color.White,fontSize=18.sp)
    if(station.isNotBlank()) Text(station,color=Color.LightGray,fontSize=14.sp)
    if(broadcast.isNotBlank()) Text("♪ $broadcast",Modifier.widthIn(max=420.dp),color=Color.Gray,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    if(store.saverPhotos && loaded && photos.isEmpty()) Text(tr("Δεν βρέθηκαν προσβάσιμες φωτογραφίες","No accessible photos found"),color=Color.LightGray,fontSize=12.sp)
   }
  }
 }
}

@Composable fun DebugSettings(enabled:Boolean,onToggle:(Boolean)->Unit) {
 val output by DebugLog.output.collectAsState()
 Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text("Debug logs",Modifier.weight(1f),fontSize=21.sp,fontWeight=FontWeight.Bold)
   Switch(enabled,onToggle)
  }
  Text(tr("Καταγραφή λειτουργίας και σφαλμάτων για διάγνωση. Ενεργοποίησέ το πριν αναπαράγεις το πρόβλημα.","Records operation and errors for diagnosis. Turn it on before reproducing the problem."))
  Text(output,fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
  Text(tr("Αρχεία στα Downloads → AutoTuneRadio → Logs. Το Off σταματά την καταγραφή και κρατά τα υπάρχοντα αρχεία.","Files in Downloads → AutoTuneRadio → Logs. Off stops logging and keeps existing files."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  OutlinedButton({DebugLog.event("DEBUG","Manual checkpoint");DebugLog.flush()},Modifier.fillMaxWidth().heightIn(min=52.dp),enabled=enabled) {Text(tr("Αποθήκευση logs τώρα","Save logs now"))}
 }
}
