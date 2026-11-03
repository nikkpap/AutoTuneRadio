@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class,androidx.compose.material3.ExperimentalMaterial3Api::class)
package gr.radiorise.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

@Composable fun StationLogo(s:Station?,size:Int) {
 Surface(shape=RoundedCornerShape((size*0.24f).dp),color=MaterialTheme.colorScheme.surfaceContainerHighest) {
  Box(Modifier.size(size.dp).padding((size/9).dp),contentAlignment=Alignment.Center) {
   val initials:@Composable ()->Unit = {Text(s?.name.orEmpty().take(2).uppercase(),fontSize=(size/3.4f).sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}
   if(!s?.logo.isNullOrBlank()) SubcomposeAsyncImage(model=s!!.logo,contentDescription=tr("Λογότυπο ","Logo ")+s.name,contentScale=ContentScale.Fit,modifier=Modifier.fillMaxSize(),error={initials()})
   else if(s==null) Image(painterResource(R.drawable.app_logo),"AutoTune Radio",Modifier.fillMaxSize(),contentScale=ContentScale.Fit) else initials()
  }
 }
}

@Composable fun CarRadioScreen(modifier:Modifier,playerRight:Boolean,stations:List<Station>,current:String,playing:Boolean,active:Boolean,status:String,broadcast:String,buffered:Long,query:String,onQuery:(String)->Unit,
 showBars:Boolean,sleepAt:Long,hasHistory:Boolean,activeGroup:String,onGroup:(String)->Unit,
 onPlay:()->Unit,onPause:()->Unit,onNext:()->Unit,onPrevious:()->Unit,onSearch:()->Unit,onSettings:()->Unit,onSleep:()->Unit,onHistory:()->Unit,onReorder:(Station)->Unit,onStation:(Station)->Unit,onEdit:(Station)->Unit) {
 val context=LocalContext.current
 val listState=rememberLazyListState()
 val look=LocalLook.current
 val colors=MaterialTheme.colorScheme
 val groups=remember(stations) {Groups.used(stations)}
 val hasUngrouped=stations.any {it.group.isBlank()}
 // A group that no longer exists (renamed/deleted) falls back to "All".
 val group=if(activeGroup.isBlank() || activeGroup in groups || (activeGroup==Groups.NONE && hasUngrouped && groups.isNotEmpty())) activeGroup else ""
 val inGroup=stations.filter {Groups.matches(it,group)}
 val filtered=inGroup.filter { it.name.contains(query,true) }
 LaunchedEffect(current,stations) {
  if(current.isNotEmpty()) {
   // Only the search text may hide the playing station; the group the user picked stays as it is.
   val visibleList=if(filtered.none { it.id==current } && inGroup.any { it.id==current }) {onQuery("");inGroup} else filtered
   val index=visibleList.indexOfFirst {it.id==current}
   if(index>=0) {
    withFrameNanos { }; withFrameNanos { }
    val layout=listState.layoutInfo
    val item=layout.visibleItemsInfo.find {it.index==index}
    if(item==null || item.offset<layout.viewportStartOffset || item.offset+item.size>layout.viewportEndOffset) listState.animateScrollToItem(index)
   }
  }
 }
 val activeStation=stations.find { it.id==current }
 // Minutes left on the sleep timer (refreshes every 15 s while set).
 var now by remember {mutableLongStateOf(SystemClock.elapsedRealtime())}
 LaunchedEffect(sleepAt) {while(sleepAt>0) {now=SystemClock.elapsedRealtime();delay(15_000)}}
 val sleepLeft:Long=if(sleepAt>0) (((sleepAt-now)/60_000)+1).coerceAtLeast(1L) else 0L
 BoxWithConstraints(modifier.fillMaxSize().padding(8.dp)) {
  val landscape=maxWidth>maxHeight
  val compact=if(landscape) maxHeight<330.dp else maxHeight<640.dp
  val playerPane:@Composable (Modifier)->Unit = { pane ->
   Box(pane.clip(RoundedCornerShape(28.dp)).background(Brush.linearGradient(listOf(look.gradA,look.gradB))).padding(if(compact) 12.dp else 18.dp)) {
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.SpaceBetween,horizontalAlignment=Alignment.CenterHorizontally) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
      Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
       FilledTonalIconButton(onClick={
        try {context.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
        catch(e:Exception) {DebugLog.error("OPEN_HOME",e);Toast.makeText(context,tr("Δεν είναι διαθέσιμη η αρχική οθόνη.","The home screen is not available."),Toast.LENGTH_SHORT).show()}
       },modifier=Modifier.size(48.dp)) {Icon(Icons.Default.Home,tr("Αρχική οθόνη συσκευής","Device home screen"))}
       PlayerAppShortcuts()
      }
      if(sleepLeft>0) AssistChip(onClick=onSleep,label={Text("$sleepLeft′")},leadingIcon={Icon(AppIcons.Timer,null,Modifier.size(18.dp))},modifier=Modifier.height(36.dp))
      Surface(shape=RoundedCornerShape(50),color=colors.primary.copy(alpha=if(playing) 0.20f else 0.10f)) {
       Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        LiveDot(playing,colors.primary,8.dp)
        Text(if(playing) "LIVE" else "RADIO",color=colors.primary,fontWeight=FontWeight.Bold,fontSize=13.sp,letterSpacing=1.5.sp)
       }
      }
     }
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(18.dp)) {
      val logoScale by animateFloatAsState(if(playing) 1f else 0.94f,label="logoScale")
      Box(contentAlignment=Alignment.Center) {
       if(playing) Box(Modifier.size(if(compact) 80.dp else 118.dp).background(Brush.radialGradient(listOf(colors.primary.copy(alpha=0.35f),Color.Transparent)),CircleShape))
       Box(Modifier.scale(logoScale)) {StationLogo(activeStation,if(compact) 64 else 96)}
      }
      Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
       Text(activeStation?.name ?: tr("Ο σταθμός σου","Your station"),fontSize=if(compact) 21.sp else 27.sp,fontWeight=FontWeight.Bold,maxLines=2,overflow=TextOverflow.Ellipsis,color=colors.onSurface)
       // Long song titles scroll slowly instead of being cut. Tap → recently played list.
       Text(broadcast.ifBlank { tr("Ο σταθμός δεν στέλνει τίτλο εκπομπής","The station sends no programme title") },
        Modifier.fillMaxWidth().basicMarquee(iterations=Int.MAX_VALUE).clickable(enabled=hasHistory,onClick=onHistory),
        fontSize=if(compact) 14.sp else 17.sp,color=colors.onSurfaceVariant,maxLines=1)
       if(showBars) VisualizerBars(playing,colors.primary,Modifier.height(if(compact) 14.dp else 20.dp).padding(top=4.dp))
      }
     }
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
      val side=if(compact) 64.dp else 76.dp
      FilledTonalIconButton(onPrevious,Modifier.size(side),enabled=stations.isNotEmpty(),shape=RoundedCornerShape(24.dp)) { Icon(AppIcons.SkipPrevious,tr("Προηγούμενος","Previous"),Modifier.size(34.dp)) }
      FilledIconButton({if(active) onPause() else onPlay()},Modifier.size(if(compact) 80.dp else 96.dp),enabled=stations.isNotEmpty(),shape=CircleShape) {
       Icon(if(active) AppIcons.Pause else Icons.Default.PlayArrow,if(active) tr("Παύση","Pause") else tr("Αναπαραγωγή","Play"),Modifier.size(46.dp))
      }
      FilledTonalIconButton(onNext,Modifier.size(side),enabled=stations.isNotEmpty(),shape=RoundedCornerShape(24.dp)) { Icon(AppIcons.SkipNext,tr("Επόμενος","Next"),Modifier.size(34.dp)) }
     }
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
       Text(status,fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=colors.onSurfaceVariant)
       Text("Buffer: $buffered s",fontSize=11.sp,color=colors.onSurfaceVariant.copy(alpha=0.75f))
      }
      IconButton(onSleep,Modifier.size(48.dp)) {Icon(AppIcons.Timer,tr("Χρονοδιακόπτης ύπνου","Sleep timer"),tint=if(sleepAt>0) colors.primary else colors.onSurface)}
      IconButton(onHistory,Modifier.size(48.dp)) {Icon(AppIcons.MusicNote,tr("Τι έπαιξε","Recently played"),tint=if(hasHistory) colors.onSurface else colors.onSurface.copy(alpha=0.45f))}
      IconButton(onSearch,Modifier.size(48.dp)) {Icon(Icons.Default.Search,tr("Αναζήτηση σταθμού","Search station"),tint=if(query.isNotEmpty()) colors.primary else colors.onSurface)}
      IconButton(onSettings,Modifier.size(48.dp)) {Icon(Icons.Default.Settings,tr("Ρυθμίσεις","Settings"))}
     }
    }
   }
  }
  val listPane:@Composable (Modifier)->Unit = { pane ->
   Column(pane,verticalArrangement=Arrangement.spacedBy(8.dp)) {
    if(groups.isNotEmpty()) {
     val chips=listOf("")+groups+(if(hasUngrouped) listOf(Groups.NONE) else emptyList())
     LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(horizontal=2.dp)) {
      items(chips,key={it}) {g ->
       val count=stations.count {Groups.matches(it,g)}
       FilterChip(selected=g==group,onClick={onGroup(g)},label={Text("${Groups.label(g)} · $count",fontSize=15.sp,maxLines=1)},modifier=Modifier.height(44.dp),
        leadingIcon=if(g==group) {{Icon(Icons.Default.Check,null,Modifier.size(18.dp))}} else null)
      }
     }
    }
    if(query.isNotEmpty()) Row(verticalAlignment=Alignment.CenterVertically) {
     Text(tr("Φίλτρο: ","Filter: ")+query,Modifier.weight(1f),fontSize=12.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
     TextButton({onQuery("")},contentPadding=PaddingValues(horizontal=8.dp)) {Text(tr("Όλοι","All"))}
    }
    LazyColumn(Modifier.weight(1f),state=listState,verticalArrangement=Arrangement.spacedBy(8.dp)) {
     if(stations.isEmpty()) item {
      Card(colors=CardDefaults.cardColors(containerColor=colors.surfaceContainerHigh)) {Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
       Text(tr("Δεν υπάρχουν σταθμοί ακόμα","No stations yet"),fontWeight=FontWeight.Bold,fontSize=18.sp)
       Text(tr("Πρόσθεσε URL σταθμού ή εισήγαγε λίστα TXT/M3U/PLS από Ρυθμίσεις → Σταθμοί.","Add a station URL or import a TXT/M3U/PLS list from Settings → Stations."),color=colors.onSurfaceVariant)
       Button(onSettings,Modifier.heightIn(min=52.dp)) {Text(tr("Άνοιγμα ρυθμίσεων","Open settings"))}
      }}
     }
     else if(filtered.isEmpty()) item {Text(tr("Κανένας σταθμός δεν ταιριάζει με «$query».","No station matches “$query”."),Modifier.padding(16.dp),color=colors.onSurfaceVariant)}
     itemsIndexed(filtered,key={_,s -> s.id}) {i,s ->
      val isCurrent=s.id==current
      Card(modifier=Modifier.fillMaxWidth().combinedClickable(onClick={onStation(s)},onLongClick={onReorder(s)}),shape=RoundedCornerShape(18.dp),
       colors=CardDefaults.cardColors(containerColor=if(isCurrent) colors.primaryContainer else colors.surfaceContainerHigh),
       border=if(isCurrent) BorderStroke(1.5.dp,colors.primary.copy(alpha=0.7f)) else null) {
       Row(Modifier.padding(10.dp).heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("${stations.indexOf(s).takeIf {it>=0}?.plus(1) ?: (i+1)}",Modifier.width(24.dp),fontSize=13.sp,color=colors.onSurfaceVariant,fontWeight=FontWeight.Medium)
        StationLogo(s,52)
        Column(Modifier.weight(1f)) {
         Text(s.name,fontWeight=FontWeight.Bold,maxLines=1,overflow=TextOverflow.Ellipsis,color=if(isCurrent) colors.onPrimaryContainer else colors.onSurface)
         if(isCurrent && playing) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
          if(showBars) MiniBars(true,colors.primary)
          Text(tr("Παίζει τώρα","Playing now"),fontSize=12.sp,color=colors.primary)
         } else Text(if(isCurrent && active) tr("Σύνδεση…","Connecting…") else if(s.group.isNotBlank()) Groups.label(s.group) else "Internet radio",fontSize=12.sp,color=if(isCurrent) colors.primary else colors.onSurfaceVariant)
        }
        IconButton({onEdit(s)},Modifier.size(48.dp)) {Icon(Icons.Default.MoreVert,tr("Επεξεργασία","Edit"))}
       }
      }
     }
    }
   }
  }
  if(landscape) Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(10.dp)) { if(playerRight) {listPane(Modifier.weight(1f).fillMaxHeight());playerPane(Modifier.weight(1f).fillMaxHeight())} else {playerPane(Modifier.weight(1f).fillMaxHeight());listPane(Modifier.weight(1f).fillMaxHeight())} }
  else Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(10.dp)) { if(playerRight) {listPane(Modifier.weight(1f).fillMaxWidth());playerPane(Modifier.weight(1f).fillMaxWidth())} else {playerPane(Modifier.weight(1f).fillMaxWidth());listPane(Modifier.weight(1f).fillMaxWidth())} }
 }
}
@Composable private fun SettingSwitch(title:String,detail:String,value:Boolean,onChange:(Boolean)->Unit) {
 Row(Modifier.fillMaxWidth().heightIn(min=72.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
  Column(Modifier.weight(1f)) {Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(detail,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Switch(value,onChange)
 }
}
@Composable private fun SectionCard(content:@Composable ColumnScope.()->Unit) {
 Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surfaceContainerHigh),shape=RoundedCornerShape(22.dp)) {
  Column(Modifier.fillMaxWidth().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
 }
}

private data class ThemeOption(val id:String,val label:String,val swatch:List<Color>)

/** Theme gallery: tap applies immediately (colours cross-fade). */
@Composable fun ThemePicker(store:Store,revision:Int) {
 val context=LocalContext.current
 val selected=remember(revision) {store.theme}
 val options=buildList {
  add(ThemeOption(Themes.AUTO,tr("Αυτόματο ημέρα / νύχτα","Auto day / night"),listOf(Themes.daylight.background,Themes.daylight.primary,Themes.ocean.background,Themes.ocean.primary)))
  Themes.presets.forEach {add(ThemeOption(it.id,it.label,listOf(it.gradA,it.background,it.primary,it.secondary)))}
  if(Build.VERSION.SDK_INT>=31) {val s=dynamicDarkColorScheme(context);add(ThemeOption(Themes.YOU,"Material You",listOf(s.primaryContainer,s.background,s.primary,s.tertiary)))}
  add(ThemeOption(Themes.STATION,tr("Χρώματα σταθμού","Station colours"),listOf(Color(0xFFFF6B6B),Color(0xFF1B1530),Color(0xFF4ECDC4),Color(0xFFFFD166))))
 }
 Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
  Icon(AppIcons.Palette,null,tint=MaterialTheme.colorScheme.primary)
  Text(tr("Θέμα εμφάνισης","Theme"),fontWeight=FontWeight.Bold,fontSize=22.sp)
 }
 Text(tr("Πάτησε ένα θέμα — εφαρμόζεται αμέσως, χωρίς επανεκκίνηση.","Tap a theme — it applies instantly, no restart needed."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 options.chunked(2).forEach {row ->
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
   row.forEach {o ->
    val on=o.id==selected
    Card(onClick={store.theme=o.id},modifier=Modifier.weight(1f).heightIn(min=64.dp),shape=RoundedCornerShape(16.dp),
     colors=CardDefaults.cardColors(containerColor=if(on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
     border=if(on) BorderStroke(2.dp,MaterialTheme.colorScheme.primary) else null) {
     Row(Modifier.fillMaxWidth().padding(10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      Box(Modifier.size(40.dp).clip(CircleShape).background(Brush.sweepGradient(o.swatch+o.swatch.first())))
      Text(o.label,Modifier.weight(1f),fontSize=14.sp,fontWeight=if(on) FontWeight.Bold else FontWeight.Normal,maxLines=2,overflow=TextOverflow.Ellipsis)
      if(on) Icon(Icons.Default.Check,null,tint=MaterialTheme.colorScheme.primary)
     }
    }
   }
   if(row.size==1) Spacer(Modifier.weight(1f))
  }
 }
 if(selected==Themes.STATION) Text(tr("Τα χρώματα προσαρμόζονται στο λογότυπο του σταθμού που παίζει.","Colours adapt to the logo of the station that is playing."),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
 if(selected==Themes.AUTO) Text(tr("Ακολουθεί το σκοτεινό θέμα του Android (π.χ. προβολείς / νύχτα στο αυτοκίνητο).","Follows Android's dark theme (e.g. headlights / night mode in the car)."),fontSize=13.sp,color=MaterialTheme.colorScheme.primary)
}

@Composable fun CarSettingsScreen(modifier:Modifier,store:Store,stations:List<Station>,revision:Int,onFolder:()->Unit,onSaverPreview:()->Unit,onDebug:(Boolean)->Unit,onAdd:()->Unit,onImport:()->Unit,onExportM3u:()->Unit,onExportTxt:()->Unit,onSystem:()->Unit,initialPage:Int=0,pageRequest:Int=0,onUpdates:()->Unit={},onAutoGroup:()->Unit={}) {
 val context=LocalContext.current
 val pager=rememberPagerState(initialPage=initialPage,pageCount={7})
 val scope=rememberCoroutineScope()
 LaunchedEffect(initialPage,pageRequest) {pager.scrollToPage(initialPage.coerceIn(0,6))}
 var reverseWheel by remember {mutableStateOf(store.reverseWheel)}
 var playerRight by remember {mutableStateOf(store.playerRight)}
 var appAuto by remember {mutableStateOf(store.appAuto)};var boot by remember {mutableStateOf(store.boot)}
 var resume by remember {mutableStateOf(store.resume)};var delay by remember {mutableIntStateOf(store.delay)}
 var buffer by remember {mutableIntStateOf(store.buffer)};var chosen by remember {mutableStateOf(store.chosen)}
 var duck by remember {mutableStateOf(store.duckNav)}
 var bars by remember {mutableStateOf(store.showBars)}
 var screenOn by remember {mutableStateOf(store.keepScreenOn)}
 var skipGroup by remember {mutableStateOf(store.skipInGroup)}
 val debugOn=remember(revision) {context.getSharedPreferences("radio",0).getBoolean("debugOn",false)}
 var pick by remember {mutableStateOf(false)}
 val colors=MaterialTheme.colorScheme
 Column(modifier.fillMaxSize().padding(horizontal=12.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   Text(tr("Ρυθμίσεις","Settings"),Modifier.weight(1f),fontSize=24.sp,fontWeight=FontWeight.Bold)
   Text("v${BuildConfig.VERSION_NAME}",fontSize=12.sp,color=colors.onSurfaceVariant)
  }
  ScrollableTabRow(selectedTabIndex=pager.currentPage,edgePadding=0.dp,containerColor=Color.Transparent,divider={}) {
   listOf(tr("Εκκίνηση","Startup"),tr("Ήχος","Sound"),tr("Οθόνη","Display"),tr("Σταθμοί","Stations"),"Screensaver",tr("Ημερολόγιο","Calendar"),tr("Σχετικά","About")).forEachIndexed { i,label ->
    Tab(selected=pager.currentPage==i,onClick={scope.launch {pager.animateScrollToPage(i)}},modifier=Modifier.height(48.dp)) {
     Text(label,Modifier.padding(horizontal=14.dp),fontSize=14.sp,maxLines=1,softWrap=false,fontWeight=if(pager.currentPage==i) FontWeight.Bold else FontWeight.Normal)
    }
   }
  }
  HorizontalPager(state=pager,modifier=Modifier.weight(1f).fillMaxWidth(),verticalAlignment=Alignment.Top) { section ->
   LazyColumn(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=16.dp)) {
   if(section==0) {
    item { SectionCard {
     SettingSwitch(tr("Στο άνοιγμα του app","When the app opens"),tr("Παίζει αυτόματα ο σταθμός εκκίνησης","Automatically plays the startup station"),appAuto){appAuto=it;store.appAuto=it}
     SettingSwitch(tr("Με την εκκίνηση της συσκευής","When the device boots"),tr("Παίζει μετά την εκκίνηση της συσκευής (boot)","Plays after the device has started (boot)"),boot){boot=it;store.boot=it}
    } }
    item { Card(onClick={pick=true},colors=CardDefaults.cardColors(containerColor=colors.surfaceContainerHigh),shape=RoundedCornerShape(22.dp)) {Column(Modifier.fillMaxWidth().padding(22.dp)) {Text(tr("Σταθμός εκκίνησης","Startup station"),color=colors.primary);Text(stations.find { it.id==chosen }?.name ?: tr("Ο τελευταίος που άκουγα","The last one I listened to"),fontWeight=FontWeight.Bold,fontSize=20.sp);Text(tr("Πάτησε για επιλογή  ›","Tap to choose  ›"),fontSize=13.sp,color=colors.onSurfaceVariant)} } }
    item { SectionCard {
     Text(tr("Καθυστέρηση · $delay δευτ.","Delay · $delay s"),fontSize=20.sp,fontWeight=FontWeight.Bold)
     TimePresets(listOf(0,5,10,30),delay) {delay=it;store.delay=it}
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
      FilledTonalButton({delay=(delay-1).coerceAtLeast(0);store.delay=delay},Modifier.size(58.dp),contentPadding=PaddingValues(0.dp)){Text("−",fontSize=25.sp)}
      Text("$delay",Modifier.weight(1f),fontSize=28.sp,fontWeight=FontWeight.Bold)
      FilledTonalButton({delay=(delay+1).coerceAtMost(300);store.delay=delay},Modifier.size(58.dp),contentPadding=PaddingValues(0.dp)){Text("+",fontSize=25.sp)}
     }
     Text(tr("0–300 δευτ. από το άνοιγμα ή το boot. Η Παύση ακυρώνει την αναμονή.","0–300 s after opening or boot. Pause cancels the countdown."),fontSize=13.sp,color=colors.onSurfaceVariant)
    } }
   }
   if(section==1) {
    item {SectionCard {EqualizerSettings(store)}}
    item {SectionCard {
     SettingSwitch(tr("Χαμήλωμα για πλοήγηση","Lower volume for navigation"),tr("Οι φωνητικές οδηγίες χαμηλώνουν την ένταση αντί να σταματούν το live stream.","Voice directions lower the volume instead of stopping the live stream."),duck) {duck=it;store.duckNav=it}
     SettingSwitch(tr("Μετά την κλήση","After a call"),tr("Επιστροφή στον ίδιο live σταθμό","Return to the same live station"),resume){resume=it;store.resume=it}
     SettingSwitch(tr("Αντιστροφή κουμπιών τιμονιού","Reverse steering-wheel buttons"),tr("Επόμενος ↔ προηγούμενος μόνο στις εξωτερικές εντολές media. Τα κουμπιά στην οθόνη παραμένουν ίδια.","Next ↔ previous only for external media commands. On-screen buttons stay the same."),reverseWheel) {reverseWheel=it;store.reverseWheel=it}
    }}
    item {SectionCard {
     Text(tr("Buffer εκκίνησης · $buffer δευτ.","Start buffer · $buffer s"),fontWeight=FontWeight.Bold,fontSize=20.sp)
     TimePresets(listOf(2,5,10,20),buffer) {buffer=it;store.buffer=it}
     Slider(buffer.toFloat(),{buffer=it.toInt();store.buffer=buffer},valueRange=1f..30f,steps=28)
     Text(tr("Γρήγορο: 2 · Ισορροπημένο: 5 · Ασταθές δίκτυο: 10–20 δευτ.","Fast: 2 · Balanced: 5 · Unstable network: 10–20 s"),fontSize=14.sp)
     Text(tr("Ο χρόνος ήχου που συγκεντρώνεται πριν αρχίσει ή επανέλθει η αναπαραγωγή. Μεγαλύτερο buffer αυξάνει την αναμονή και την απόσταση από το live. Εφαρμόζεται στην επόμενη έναρξη/αλλαγή σταθμού.","Audio collected before playback starts or recovers. A larger buffer adds waiting time and distance from live. Applies on the next start/station change."),fontSize=13.sp,color=colors.onSurfaceVariant)
    }}
   }
   if(section==2) {
    item {SectionCard {ThemePicker(store,revision)}}
    item {SectionCard {
     SettingSwitch(tr("Κινούμενες μπάρες ήχου","Animated sound bars"),tr("Διακοσμητικές μπάρες όσο παίζει ο σταθμός.","Decorative bars while the station plays."),bars) {bars=it;store.showBars=it}
     SettingSwitch(tr("Οθόνη πάντα αναμμένη","Keep screen on"),tr("Η οθόνη δεν σβήνει όσο ο player είναι ανοιχτός (χρήσιμο σε οθόνη αυτοκινήτου).","The screen stays on while the player is open (useful on a car display)."),screenOn) {screenOn=it;store.keepScreenOn=it}
    }}
    item {SectionCard {AppShortcutSettings()}}
    item {SectionCard {
     Text(tr("Διάταξη split 50 / 50","Split layout 50 / 50"),fontWeight=FontWeight.Bold,fontSize=22.sp)
     Text(tr("Διάλεξε πλευρά για τον player. Οι σταθμοί μεταφέρονται στην απέναντι πλευρά.","Choose the player side. Stations move to the opposite side."))
     val left=tr("Player αριστερά · Σταθμοί δεξιά","Player left · Stations right");val right=tr("Σταθμοί αριστερά · Player δεξιά","Stations left · Player right")
     Button({playerRight=false;store.playerRight=false},Modifier.fillMaxWidth().heightIn(min=64.dp),colors=ButtonDefaults.buttonColors(containerColor=if(!playerRight) colors.primary else colors.surfaceVariant,contentColor=if(!playerRight) colors.onPrimary else colors.onSurface)) {Text(if(!playerRight) "✓ $left" else left)}
     Button({playerRight=true;store.playerRight=true},Modifier.fillMaxWidth().heightIn(min=64.dp),colors=ButtonDefaults.buttonColors(containerColor=if(playerRight) colors.primary else colors.surfaceVariant,contentColor=if(playerRight) colors.onPrimary else colors.onSurface)) {Text(if(playerRight) "✓ $right" else right)}
     Text(tr("Σε κάθετη οθόνη: αριστερά = πάνω, δεξιά = κάτω. Η επιλογή αποθηκεύεται.","In portrait: left = top, right = bottom. The choice is saved."),fontSize=13.sp,color=colors.onSurfaceVariant)
    }}
   }
   if(section==3) {
    item {SectionCard {
     Text(tr("Διαχείριση σταθμών","Manage stations"),fontWeight=FontWeight.Bold,fontSize=20.sp)
     Text(tr("Στη λίστα: ${stations.size} σταθμοί","In the list: ${stations.size} stations"),color=colors.primary)
     Button(onImport,Modifier.fillMaxWidth().heightIn(min=60.dp)){Text(tr("Εισαγωγή από αρχείο / URL","Import from file / URL"))}
     OutlinedButton(onAdd,Modifier.fillMaxWidth().heightIn(min=60.dp)){Text(tr("Προσθήκη σταθμού με URL","Add a station by URL"))}
    }}
    item {SectionCard {
     Text(tr("Αντίγραφο σταθμών","Station backup"),fontWeight=FontWeight.Bold,fontSize=20.sp)
     Button(onExportTxt,Modifier.fillMaxWidth().heightIn(min=60.dp),enabled=stations.isNotEmpty()){Text(tr("Εξαγωγή TXT με λογότυπα","Export TXT with logos"))}
     OutlinedButton(onExportM3u,Modifier.fillMaxWidth().heightIn(min=60.dp),enabled=stations.isNotEmpty()){Text(tr("Εξαγωγή M3U","Export M3U"))}
    }}
    item {SectionCard {
     val used=Groups.used(stations)
     Text(tr("Ομάδες σταθμών","Station groups"),fontWeight=FontWeight.Bold,fontSize=20.sp)
     Text(tr("Ειδήσεις, Αθλητικά, Rock, Pop… Στον player εμφανίζονται κουμπιά για να βλέπεις μόνο μία ομάδα. Ομάδα ορίζεις από το ⋮ κάθε σταθμού.","News, Sports, Rock, Pop… The player shows buttons to see one group at a time. Set a group from each station's ⋮ menu."),fontSize=13.sp,color=colors.onSurfaceVariant)
     Text(if(used.isEmpty()) tr("Δεν υπάρχουν ακόμα ομάδες.","No groups yet.") else used.joinToString(" · ") {g -> "${Groups.label(g)} (${stations.count {it.group==g}})"},color=colors.primary)
     Button(onAutoGroup,Modifier.fillMaxWidth().heightIn(min=60.dp),enabled=stations.any {it.group.isBlank()}) {Text(tr("Αυτόματη ομαδοποίηση από τα ονόματα","Auto-group by station names"))}
     Text(tr("Βάζει ομάδα μόνο στους σταθμούς που δεν έχουν, π.χ. «ΣΠΟΡ FM» → Αθλητικά, «1055Rock» → Rock. Μπορείς να το αλλάξεις μετά.","Only fills stations without a group, e.g. “Sport FM” → Sports, “1055Rock” → Rock. You can change it afterwards."),fontSize=13.sp,color=colors.onSurfaceVariant)
     SettingSwitch(tr("Τιμόνι μέσα στην ομάδα","Steering wheel within group"),tr("Επόμενος / προηγούμενος αλλάζει μόνο σταθμούς της ομάδας που έχεις επιλέξει.","Next / previous only switches between stations of the selected group."),skipGroup) {skipGroup=it;store.skipInGroup=it}
    }}
    item {OutlinedButton(onSystem,Modifier.fillMaxWidth().heightIn(min=60.dp)){Text(tr("Ρυθμίσεις συσκευής / μπαταρίας","Device / battery settings"))}}
   }
   if(section==4) {
    item {SectionCard {SaverSettings(store,onFolder,onSaverPreview,revision)}}
   }
   if(section==5) {
    item {SectionCard {CalendarSettings(store)}}
   }
   if(section==6) {
    item {SectionCard {
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      Icon(AppIcons.Translate,null,tint=colors.primary)
      Text(tr("Γλώσσα / Language","Language / Γλώσσα"),fontWeight=FontWeight.Bold,fontSize=20.sp)
     }
     Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
      listOf(false to "🇬🇷  Ελληνικά",true to "🇬🇧  English").forEach {(en,label) ->
       val on=Lang.english==en
       if(on) Button({},Modifier.weight(1f).heightIn(min=60.dp)) {Text("✓ $label",fontSize=17.sp)}
       else OutlinedButton({Lang.set(context,en)},Modifier.weight(1f).heightIn(min=60.dp)) {Text(label,fontSize=17.sp)}
      }
     }
     Text(tr("Η αλλαγή γίνεται αμέσως σε όλη την εφαρμογή, στις ειδοποιήσεις και στο Android Auto.","The change applies instantly to the whole app, notifications and Android Auto."),fontSize=13.sp,color=colors.onSurfaceVariant)
    }}
    item {SectionCard {
     Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
      Image(painterResource(R.drawable.app_logo),tr("Λογότυπο AutoTune Radio","AutoTune Radio logo"),Modifier.size(76.dp),contentScale=ContentScale.Fit)
      Column(Modifier.weight(1f)) {
       Text("AutoTune Radio",fontSize=26.sp,fontWeight=FontWeight.Bold,color=colors.primary)
       Text(tr("Έκδοση ","Version ")+BuildConfig.VERSION_NAME,fontSize=14.sp,color=colors.onSurfaceVariant)
      }
     }
     Text(tr("Το ραδιόφωνό σου, σε κάθε διαδρομή.","Your radio, on every journey."),fontSize=19.sp,fontWeight=FontWeight.Medium)
     Text(tr("Οι αγαπημένοι σου σταθμοί σε μια καθαρή οθόνη, με μεγάλα χειριστήρια, λογότυπα και πληροφορίες εκπομπής όταν τις παρέχει ο σταθμός.","Your favourite stations on a clean screen, with big controls, logos and programme info when the station provides it."),fontSize=16.sp)
    }}
    item {SectionCard {UpdateSettings(store,revision,onUpdates)}}
    item {SectionCard {
     Text(tr("Τι νέο στην 0.23","What's new in 0.23"),fontSize=19.sp,fontWeight=FontWeight.Bold)
     listOf(
      tr("Ελληνικά / English για όλη την εφαρμογή","Greek / English for the whole app"),
      tr("Θέματα με άμεση εφαρμογή, Material You και χρώματα από το λογότυπο του σταθμού","Instantly applied themes, Material You and colours from the station logo"),
      tr("Χρονοδιακόπτης ύπνου με απαλό σβήσιμο","Sleep timer with gentle fade-out"),
      tr("«Τι έπαιξε»: ιστορικό τίτλων με αντιγραφή και αναζήτηση","“Recently played”: title history with copy and search"),
      tr("Χαμήλωμα έντασης στις οδηγίες πλοήγησης","Volume ducking for navigation prompts"),
      tr("Αυτόματη επανασύνδεση όταν επιστρέψει το δίκτυο","Automatic reconnect when the network returns"),
      tr("Υποστήριξη σταθμών με σύνδεσμο .pls/.m3u και ανακατευθύνσεις http↔https","Stations with .pls/.m3u links and http↔https redirects"),
      tr("Ενημερώσεις μέσα από την εφαρμογή (GitHub Releases)","In-app updates (GitHub Releases)")
     ).forEach {Text("•  $it",fontSize=14.sp)}
    }}
    item {SectionCard {DebugSettings(debugOn,onDebug)}}
    item {SectionCard {
     Text(tr("Δημιουργός","Creator"),fontSize=14.sp,color=colors.primary)
     Text("ALU DEV TEAM © 2026",fontSize=18.sp,fontWeight=FontWeight.Bold)
     Text("Nikolaos K. Paridis ツ (nikkpap)",fontSize=18.sp,fontWeight=FontWeight.Medium)
     OutlinedButton(onClick={
      runCatching {context.startActivity(Intent(Intent.ACTION_SENDTO,Uri.parse("mailto:nikkpap@gmail.com")).putExtra(Intent.EXTRA_SUBJECT,"AutoTune Radio ${BuildConfig.VERSION_NAME}"))}
       .onFailure {Toast.makeText(context,tr("Δεν βρέθηκε εφαρμογή email στη συσκευή.","No email app found on this device."),Toast.LENGTH_LONG).show()}
     },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {Text("nikkpap@gmail.com")}
     Text(tr("Είμαστε ανοιχτοί σε συζητήσεις, ιδέες και προτάσεις για το AutoTune Radio. Ας φτιάξουμε μαζί το ραδιόφωνο που θέλουμε να ακούμε. Στην υγειά μας!","We are open to discussions, ideas and suggestions for AutoTune Radio. Let's build the radio we want to listen to, together. Cheers!"),fontSize=16.sp)
    }}
    item {SectionCard {
     Text(tr("Μικρές συμβουλές","Quick tips"),fontSize=19.sp,fontWeight=FontWeight.Bold)
     Text(tr("Κράτησε πατημένο έναν σταθμό για να αλλάξεις τη θέση του στη λίστα.","Long-press a station to change its position in the list."))
     Text(tr("Πάτα τον τίτλο του τραγουδιού για να δεις τι έπαιξε πριν.","Tap the song title to see what played before."))
     Text(tr("Από τις Ρυθμίσεις μπορείς να εισάγεις σταθμούς, να προσθέσεις δικά σου stream και λογότυπα και να διαλέξεις την πλευρά του player.","In Settings you can import stations, add your own streams and logos and choose the player side."))
     Text(tr("Χειρίσου την εφαρμογή όταν το όχημα είναι σταματημένο.","Operate the app only when the vehicle is stopped."),fontSize=13.sp,color=colors.onSurfaceVariant)
    }}
   }
   }
  }
 }
 if(pick) AlertDialog(onDismissRequest={pick=false},title={Text(tr("Σταθμός εκκίνησης","Startup station"))},text={LazyColumn(Modifier.heightIn(max=360.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  item {Card(onClick={chosen="";store.chosen="";pick=false}) {Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(chosen.isEmpty(),null);Text(tr("Ο τελευταίος που άκουγα","The last one I listened to"))}}}
  items(stations,key={it.id}) {s -> Card(onClick={chosen=s.id;store.chosen=s.id;pick=false}) {Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {RadioButton(chosen==s.id,null);Text(s.name)}}}
 }},confirmButton={TextButton({pick=false}){Text(tr("Κλείσιμο","Close"))}})
}

@Composable private fun TimePresets(values:List<Int>,selected:Int,onSelect:(Int)->Unit) {
 Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
  values.forEach { n ->
   OutlinedButton({onSelect(n)},Modifier.weight(1f).height(56.dp),contentPadding=PaddingValues(0.dp),shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.outlinedButtonColors(containerColor=if(selected==n) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,contentColor=if(selected==n) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)) {
    Text(n.toString(),fontSize=18.sp,fontWeight=FontWeight.Bold,maxLines=1,softWrap=false)
   }
  }
 }
}
