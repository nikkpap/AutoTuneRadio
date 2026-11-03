package gr.radiorise.player

import android.app.*
import android.content.*
import android.media.audiofx.Equalizer
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.MediaMetadata
import android.net.ConnectivityManager
import android.net.Network
import android.os.*
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.*
import coil.imageLoader
import coil.request.ImageRequest
import androidx.core.graphics.drawable.toBitmap

/** One "now playing" title received from a station (ICY / stream metadata). */
data class TrackEntry(val title:String,val station:String,val time:Long)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class RadioService: android.service.media.MediaBrowserService() {
 companion object {
  val equalizerState=MutableStateFlow(EqState())
  val broadcast=MutableStateFlow("")
  val bufferedSeconds=MutableStateFlow(0L)
  val status=MutableStateFlow(tr("Έτοιμο για ακρόαση","Ready to listen"))
  val current=MutableStateFlow("")
  val playing=MutableStateFlow(false)
  /** True while the user wants sound: playing, connecting, retrying, delayed start or waiting for a call to end. */
  val active=MutableStateFlow(false)
  /** elapsedRealtime() when the sleep timer stops playback; 0 = off. */
  val sleepAt=MutableStateFlow(0L)
  /** Recently heard titles (newest first, current session). */
  val history=MutableStateFlow<List<TrackEntry>>(emptyList())
  private val FOREGROUND_ACTIONS=setOf("PLAY","BOOT","APP","RESUME","NEXT","PREVIOUS")
  fun send(c: Context, action: String, id: String="", minutes:Int=0) {
   val i=Intent(c,RadioService::class.java).setAction(action).putExtra("id",id).putExtra("minutes",minutes)
   if(action in FOREGROUND_ACTIONS) ContextCompat.startForegroundService(c,i) else c.startService(i)
  }
  fun idleText()=tr("Έτοιμο για ακρόαση","Ready to listen")
 }
 private lateinit var player: ExoPlayer
 private lateinit var store: Store
 private lateinit var audio: AudioManager
 private lateinit var focus: AudioFocusRequest
 private lateinit var session: MediaSession
 private val handler=Handler(Looper.getMainLooper())
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
 private var artworkJob:Job?=null
 private var resolveJob:Job?=null
 private var artwork:android.graphics.Bitmap?=null
 private var foregroundStarted=false
 private fun loadArtwork(s:Station?) {
  artworkJob?.cancel(); artwork=null
  if(s==null || !Playlist.valid(s.logo)) return
  artworkJob=scope.launch {
   try {
    val result=imageLoader.execute(ImageRequest.Builder(this@RadioService).data(s.logo).size(256).allowHardware(false).build())
    if(!destroyed && station?.id==s.id) {artwork=result.drawable?.toBitmap(256,256);update()}
   } catch(e:CancellationException) {throw e} catch(e:Exception) {DebugLog.error("AUTO_ARTWORK",e)}
  }
 }
 private var destroyed=false
 private var wanted=false
 private var interrupted=false
 private var hasFocus=false
 private var ducked=false
 private var fadeFactor=1f
 private var waitingNetwork=false
 private var station: Station?=null
 private var task: Runnable?=null
 private var retries=0
 private var equalizer:Equalizer?=null
 private var networkCallback:ConnectivityManager.NetworkCallback?=null
 private val preferenceListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,key ->
  if(key=="stations" || key=="last") handler.post {
   notifyChildrenChanged("stations");notifyChildrenChanged("recent")
   // An edited station (new name/URL/logo) replaces the cached copy; a changed URL reconnects.
   val old=station;val fresh=old?.let {o -> store.stations().find {it.id==o.id}}
   if(key=="stations" && old!=null && fresh!=null && fresh!=old) {
    station=fresh
    if(fresh.logo!=old.logo) loadArtwork(fresh)
    if(fresh.url!=old.url && wanted && !interrupted) live() else update()
   }
  }
  if(key?.startsWith("eq")==true) handler.post { if(equalizer==null && player.audioSessionId>0) attachEqualizer(player.audioSessionId) else applyEqualizer() }
  if(key=="lang") handler.post {
   createChannel()
   if(!wanted && task==null && !interrupted) status.value=if(waitingNetwork) tr("Αναμονή δικτύου…","Waiting for network…") else idleText()
   update()
  }
 }
 private fun releaseEqualizer() { runCatching {equalizer?.release()}; equalizer=null }
 private fun attachEqualizer(sessionId:Int) {
  if(destroyed) return
  DebugLog.event("EQ","attach session=$sessionId enabled=${store.eqOn} preset=${store.eqPreset}")
  releaseEqualizer()
  if(sessionId<=0) {equalizerState.value=EqState(message=tr("Αναμονή audio session του player.","Waiting for the player audio session."));return}
  try {
   equalizer=Equalizer(0,sessionId)
   equalizer!!.setControlStatusListener { _,_ -> handler.post {applyEqualizer()} }
   applyEqualizer()
  } catch(e:Exception) {DebugLog.error("EQ_ATTACH",e);releaseEqualizer();equalizerState.value=EqState(message=tr("Η συσκευή δεν διαθέτει διαθέσιμο equalizer.","This device has no available equalizer."))}
 }
 private fun applyEqualizer() {
  if(destroyed) return
  val eq=equalizer ?: run {DebugLog.event("EQ","apply skipped: no effect session=${player.audioSessionId}");return}
  try {
   val range=eq.bandLevelRange
   val bands=(0 until eq.numberOfBands.toInt()).map {i -> eq.getCenterFreq(i.toShort())/1000}
   DebugLog.event("EQ","apply control=${eq.hasControl()} bands=${bands.joinToString()} range=${range.joinToString()} enabled=${store.eqOn} preset=${store.eqPreset}")
   if(!eq.hasControl()) {equalizerState.value=EqState(message=tr("Το equalizer ελέγχεται από άλλη εφαρμογή.","The equalizer is controlled by another app."));return}
   val curve=store.eqCurve()
   bands.forEachIndexed {i,hz ->
    val level=if(store.eqPreset=="Custom") store.eqLevel(hz) else curve[if(bands.size==1) 2 else (i*4/(bands.size-1)).coerceIn(0,4)]*100
    eq.setBandLevel(i.toShort(),level.coerceIn(range[0].toInt(),range[1].toInt()).toShort())
   }
   val enableResult=eq.setEnabled(store.eqOn)
   DebugLog.event("EQ","setEnabled result=$enableResult actual=${eq.enabled} levels=${bands.indices.map {eq.getBandLevel(it.toShort())}}")
   val message=if(enableResult!=android.media.audiofx.AudioEffect.SUCCESS) tr("Η συσκευή απέρριψε την αλλαγή equalizer ($enableResult).","The device rejected the equalizer change ($enableResult).")
    else if(eq.enabled) tr("Ενεργό · ${bands.size} μπάντες","On · ${bands.size} bands") else tr("Ανενεργό · ${bands.size} μπάντες","Off · ${bands.size} bands")
   equalizerState.value=EqState(bands,range[0].toInt(),range[1].toInt(),bands.indices.map {eq.getBandLevel(it.toShort()).toInt()},message)
  } catch(e:Exception) {DebugLog.error("EQ_APPLY",e);equalizerState.value=EqState(message=tr("Το equalizer δεν είναι διαθέσιμο σε αυτή τη συσκευή.","The equalizer is not available on this device."))}
 }
 private fun createChannel() {
  getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("radio",tr("Αναπαραγωγή ραδιοφώνου","Radio playback"),NotificationManager.IMPORTANCE_LOW))
 }

 override fun onCreate() {
  super.onCreate();DebugLog.event("SERVICE","onCreate"); store=Store(this); audio=getSystemService(AudioManager::class.java)
  createChannel()
  player=buildPlayer()
  focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAcceptsDelayedFocusGain(true).setWillPauseWhenDucked(true).setOnAudioFocusChangeListener({ change ->
   if(destroyed) return@setOnAudioFocusChangeListener
   DebugLog.event("FOCUS","change=$change wanted=$wanted interrupted=$interrupted ducked=$ducked")
   when(change) {
    AudioManager.AUDIOFOCUS_GAIN -> {
     hasFocus=true
     if(ducked) {ducked=false;applyVolume()}
     if(interrupted && wanted && store.resume) { interrupted=false; retries=0; live() }
     else if(interrupted) { interrupted=false; wanted=false; audio.abandonAudioFocusRequest(focus); hasFocus=false; status.value=tr("Σε παύση","Paused"); update() }
    }
    // Navigation prompts / short sounds: lower the volume instead of stopping the live stream.
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> if(store.duckNav && wanted && !interrupted) { ducked=true; applyVolume() } else transientLoss()
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> transientLoss()
    AudioManager.AUDIOFOCUS_LOSS -> { hasFocus=false; interrupted=false; wanted=false; ducked=false; applyVolume(); cancelTask(); resolveJob?.cancel(); player.pause(); status.value=tr("Σε παύση","Paused"); update() }
   }
  },handler).build()
  session=MediaSession(this,"AutoTune Radio")
  session.setCallback(object: MediaSession.Callback() {
   override fun onPlay() { (store.stations().find { it.id==current.value } ?: store.startup())?.let { start(it) } ?: mediaError(tr("Πρόσθεσε σταθμούς από το κινητό.","Add stations from the phone.")) }
   override fun onSkipToNext() {DebugLog.event("MEDIA","next reverse=${store.reverseWheel}"); skip(if(store.reverseWheel) -1 else 1) }
   override fun onSkipToPrevious() {DebugLog.event("MEDIA","previous reverse=${store.reverseWheel}"); skip(if(store.reverseWheel) 1 else -1) }
   override fun onPause() { pause() }
   override fun onStop() { stopPlayback() }
   override fun onPlayFromMediaId(mediaId:String?,extras:Bundle?) {store.stations().find {it.id==mediaId}?.let {start(it)} ?: mediaError(tr("Ο σταθμός δεν είναι διαθέσιμος.","The station is not available."))}
   override fun onPlayFromSearch(query:String?,extras:Bundle?) {playSearch(query)}
  }); session.isActive=true
  session.setSessionActivity(PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
  setSessionToken(session.sessionToken)
  station=store.stations().find {it.id==store.last} ?: store.startup()
  station?.let {current.value=it.id}
  loadArtwork(station)
  update()
  player.addListener(playerListener)
  getSharedPreferences("radio",0).registerOnSharedPreferenceChangeListener(preferenceListener)
  handler.post(bufferPoll)
  registerNetwork()
 }

 private fun transientLoss() {
  hasFocus=false; ducked=false; applyVolume(); interrupted=wanted; cancelTask(); player.pause()
  status.value=tr("Παύση λόγω κλήσης / άλλου ήχου","Paused for a call / other audio"); update()
 }
 private fun applyVolume() { if(::player.isInitialized) player.volume=(if(ducked) 0.25f else 1f)*fadeFactor }

 /** When retries ran out because the network dropped (tunnel, no signal), reconnect automatically once it returns. */
 private fun registerNetwork() {
  val cm=getSystemService(ConnectivityManager::class.java) ?: return
  val callback=object:ConnectivityManager.NetworkCallback() {
   override fun onAvailable(network:Network) {
    handler.postDelayed({
     if(!destroyed && waitingNetwork) {
      waitingNetwork=false
      DebugLog.event("NETWORK","available, reconnecting")
      station?.let {start(it)}
     }
    },1500)
   }
  }
  runCatching {cm.registerDefaultNetworkCallback(callback);networkCallback=callback}.onFailure {DebugLog.error("NETWORK",it)}
 }

 private var configuredBuffer=0
 private val bufferPoll=object:Runnable { override fun run() { bufferedSeconds.value=player.totalBufferedDuration/1000; handler.postDelayed(this,if(wanted) 1000 else 15000) } }
 private fun buildPlayer():ExoPlayer {
  configuredBuffer=store.buffer
  val ms=configuredBuffer*1000
  val load=DefaultLoadControl.Builder().setBufferDurationsMs(maxOf(15000,ms),maxOf(30000,ms*2),ms,ms).setPrioritizeTimeOverSizeThresholds(true).build()
  // Many radio streams redirect http → https (or back); ExoPlayer blocks that unless allowed explicitly.
  val http=DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true).setConnectTimeoutMs(12000).setReadTimeoutMs(20000).setUserAgent("AutoTuneRadio/${BuildConfig.VERSION_NAME}")
  return ExoPlayer.Builder(this).setLoadControl(load).setMediaSourceFactory(DefaultMediaSourceFactory(DefaultDataSource.Factory(this,http))).build().also {
   it.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
   it.setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(),false)
   it.setHandleAudioBecomingNoisy(true)
   it.volume=(if(ducked) 0.25f else 1f)*fadeFactor
  }
 }
 private fun setBroadcast(title:String) {
  val t=title.trim();if(t.isBlank() || t==broadcast.value) return
  broadcast.value=t
  val name=station?.name.orEmpty()
  // Some stations repeat their own name as title; keep history meaningful.
  if(!t.equals(name,true)) {
   val list=history.value
   if(list.firstOrNull()?.let {it.title==t && it.station==name}!=true) history.value=(listOf(TrackEntry(t,name,System.currentTimeMillis()))+list).take(50)
  }
 }
 private val playerListener=object: Player.Listener {
   override fun onAudioSessionIdChanged(audioSessionId:Int) {attachEqualizer(audioSessionId)}
   override fun onMetadata(metadata:Metadata) {
    for(i in 0 until metadata.length()) { val entry=metadata[i]; if(entry is IcyInfo && !entry.title.isNullOrBlank()) setBroadcast(entry.title!!) }
    update()
   }
   override fun onMediaMetadataChanged(metadata:androidx.media3.common.MediaMetadata) { metadata.title?.toString()?.takeIf { it.isNotBlank() }?.let { setBroadcast(it); update() } }
   override fun onIsPlayingChanged(v:Boolean) {DebugLog.event("PLAYER","isPlaying=$v"); playing.value=v; update() }
   override fun onPlayWhenReadyChanged(v:Boolean, reason:Int) { if(reason==Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY) pause() }
   override fun onPlaybackStateChanged(s:Int) {
    DebugLog.event("PLAYER","state=$s")
    if(s==Player.STATE_READY) { if(equalizer==null && player.audioSessionId>0) attachEqualizer(player.audioSessionId);retries=0; status.value=if(player.playWhenReady) "LIVE • ${station?.name.orEmpty()}" else tr("Σε παύση","Paused") }
    if(s==Player.STATE_BUFFERING) status.value=tr("Σύνδεση…","Connecting…")
    // A live stream that "ends" (server closed the connection) is reconnected instead of going silent.
    if(s==Player.STATE_ENDED && wanted && hasFocus && retries<5) { DebugLog.event("PLAYER","stream ended, reconnect"); scheduleRetry() }
    update()
   }
   override fun onPlayerError(e:PlaybackException) {
    DebugLog.error("PLAYER",e); status.value=tr("Αποτυχία σύνδεσης: ","Connection failed: ")+e.errorCodeName
    if(wanted && hasFocus && retries<5) scheduleRetry()
    else {
     val network=e.errorCode in 2000..2999
     if(wanted && network) { waitingNetwork=true; status.value=tr("Αναμονή δικτύου… θα συνδεθεί ξανά αυτόματα","Waiting for network… will reconnect automatically") }
     wanted=false; publishActive()
    }
    update()
   }
  }
 private fun scheduleRetry() {
  retries++; cancelTask(); task=Runnable { task=null; if(wanted && hasFocus) live() }; handler.postDelayed(task!!,(retries*3000).toLong())
  status.value=tr("Επανασύνδεση ($retries/5)…","Reconnecting ($retries/5)…")
 }
 private fun publishActive() { active.value=wanted || task!=null || interrupted }
 override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int {
  DebugLog.event("SERVICE","command=${i?.action} startId=$startId")
  when(i?.action) {
   android.provider.MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH -> {foreground();playSearch(i.getStringExtra(android.app.SearchManager.QUERY))}
   "KEEP_ALIVE" -> {foreground();if(!wanted && !interrupted && task==null && !waitingNetwork) stopPlayback()}
   "PLAY" -> { foreground(); store.stations().find { it.id==i.getStringExtra("id") }?.let { start(it) } ?: stopPlayback() }
   "RESUME" -> {foreground();(store.stations().find { it.id==current.value } ?: store.startup())?.let { start(it) } ?: stopPlayback()}
   "NEXT" -> {foreground();if(store.stations().isEmpty()) stopPlayback() else skip(1)}
   "PREVIOUS" -> {foreground();if(store.stations().isEmpty()) stopPlayback() else skip(-1)}
   "APP", "BOOT" -> {
    if(wanted || interrupted || task!=null) return START_NOT_STICKY
    // Started with startForegroundService(): startForeground() must run before any stopSelf(), otherwise Android crashes the app.
    foreground()
    val enabled=if(i.action=="BOOT") store.boot else store.appAuto
    val s=store.startup()
    if(!enabled || s==null) { stopPlayback(); return START_NOT_STICKY }
    cancelTask(); status.value=tr("Εκκίνηση σε ${store.delay} δευτ.","Starting in ${store.delay} s"); task=Runnable { task=null; start(s) }; handler.postDelayed(task!!,store.delay*1000L); publishActive(); update()
   }
   "SLEEP" -> setSleep(i.getIntExtra("minutes",0))
   "PAUSE" -> { pause(); if(!foregroundStarted) stopSelf() }
   "STOP" -> stopPlayback()
  }
  return START_NOT_STICKY
 }
 private fun skip(direction:Int) {
  DebugLog.event("PLAYER","skip direction=$direction")
  val all=store.stations(); if(all.isEmpty()) return
  val index=all.indexOfFirst { it.id==current.value }
  val next=if(index<0) { if(direction>0) 0 else all.lastIndex } else Math.floorMod(index+direction,all.size)
  start(all[next])
 }
 private fun start(s:Station) {
  DebugLog.event("PLAYER","start id=${s.id} buffer=${store.buffer}")
  runCatching {ContextCompat.startForegroundService(this,Intent(this,RadioService::class.java).setAction("KEEP_ALIVE"))}.onFailure {DebugLog.error("KEEP_ALIVE",it)}
  cancelTask(); broadcast.value=""; bufferedSeconds.value=0; station=s; current.value=s.id; store.last=s.id; wanted=true; interrupted=false; retries=0; waitingNetwork=false
  loadArtwork(s)
  foreground()
  val focusResult=audio.requestAudioFocus(focus)
  DebugLog.event("FOCUS","request result=$focusResult")
  when(focusResult) {
   AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> { hasFocus=true; live() }
   AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> { hasFocus=false; interrupted=true; player.pause(); status.value=tr("Αναμονή ολοκλήρωσης κλήσης","Waiting for the call to end"); update() }
   else -> { wanted=false; player.pause(); status.value=tr("Ο ήχος χρησιμοποιείται από άλλη εφαρμογή","Audio is used by another app"); update() }
  }
  publishActive()
 }
 private fun live() {
  DebugLog.event("PLAYER","live session=${player.audioSessionId} buffer=${store.buffer}")
  if(configuredBuffer!=store.buffer) { releaseEqualizer();player.removeListener(playerListener); player.release(); player=buildPlayer(); player.addListener(playerListener) }
  val s=station ?: return
  resolveJob?.cancel(); player.stop(); status.value=tr("Σύνδεση…","Connecting…"); update()
  if(Playlist.isPlaylistUrl(s.url)) {
   // .pls / .m3u station link: download it and play the first real stream inside.
   resolveJob=scope.launch {
    val resolved=try { withContext(Dispatchers.IO) {Playlist.resolveStream(s.url)} } catch(e:CancellationException) {throw e} catch(e:Exception) {DebugLog.error("RESOLVE",e);null}
    if(destroyed || station?.id!=s.id || !wanted) return@launch
    if(resolved==null) { if(retries<5) scheduleRetry() else {wanted=false;status.value=tr("Η λίστα του σταθμού δεν άνοιξε","Could not open the station playlist");publishActive()}; update(); return@launch }
    playUrl(resolved.first,resolved.second)
   }
  } else playUrl(s.url,s.url.substringBefore('?').lowercase().endsWith(".m3u8"))
 }
 private fun playUrl(url:String,hls:Boolean) {
  val item=if(hls) MediaItem.Builder().setUri(url).setMimeType(MimeTypes.APPLICATION_M3U8).build() else MediaItem.fromUri(url)
  player.setMediaItem(item); player.prepare(); player.play(); publishActive()
 }
 private fun cancelTask() { task?.let(handler::removeCallbacks); task=null }
 private fun pause() {
  DebugLog.event("PLAYER","pause"); cancelTask(); resolveJob?.cancel(); cancelSleep(); wanted=false; interrupted=false; hasFocus=false; waitingNetwork=false; ducked=false; applyVolume()
  player.pause(); audio.abandonAudioFocusRequest(focus); status.value=tr("Σε παύση","Paused"); publishActive(); update()
 }

 // ---- Sleep timer with a gentle 8-second fade-out ----
 private val sleepTask=Runnable { DebugLog.event("SLEEP","fade start"); handler.post(fadeStep) }
 private val fadeStep=object:Runnable { override fun run() {
  fadeFactor-=1f/16f
  if(fadeFactor<=0f) { fadeFactor=1f; sleepAt.value=0; DebugLog.event("SLEEP","stop"); pause(); status.value=tr("Ο χρονοδιακόπτης ύπνου σταμάτησε το ραδιόφωνο","Sleep timer stopped the radio"); update() }
  else { applyVolume(); handler.postDelayed(this,500) }
 } }
 private fun setSleep(minutes:Int) {
  handler.removeCallbacks(sleepTask); handler.removeCallbacks(fadeStep); fadeFactor=1f; applyVolume()
  if(minutes<=0 || !(wanted || task!=null || interrupted)) { sleepAt.value=0; if(!foregroundStarted) stopSelf(); return }
  sleepAt.value=SystemClock.elapsedRealtime()+minutes*60_000L
  handler.postDelayed(sleepTask,minutes*60_000L)
  DebugLog.event("SLEEP","set minutes=$minutes")
 }
 private fun cancelSleep() { handler.removeCallbacks(sleepTask); handler.removeCallbacks(fadeStep); fadeFactor=1f; sleepAt.value=0 }

 private fun pi(action:String)=PendingIntent.getService(this,action.hashCode(),Intent(this,RadioService::class.java).setAction(action),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
 private fun notification():Notification {
  val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
  val busy=wanted || task!=null
  return Notification.Builder(this,"radio").setStyle(Notification.MediaStyle().setMediaSession(session.sessionToken).setShowActionsInCompactView(0,1,2)).setSmallIcon(android.R.drawable.ic_media_play).setContentTitle(station?.name ?: "AutoTune Radio").setContentText(broadcast.value.ifBlank { status.value }).setLargeIcon(artwork).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(busy)
   .addAction(android.R.drawable.ic_media_previous,tr("Προηγούμενος","Previous"),pi("PREVIOUS"))
   .addAction(if(busy) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,if(busy) tr("Παύση","Pause") else tr("Αναπαραγωγή","Play"),pi(if(busy) "PAUSE" else "RESUME"))
   .addAction(android.R.drawable.ic_media_next,tr("Επόμενος","Next"),pi("NEXT"))
   .addAction(android.R.drawable.ic_menu_close_clear_cancel,tr("Κλείσιμο","Close"),pi("STOP")).build()
 }
 private fun foreground() {foregroundStarted=true;startForeground(1,notification()) }
 private fun stopPlayback() {pause();foregroundStarted=false;stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()}
 private fun update() {
  if(destroyed) return
  publishActive()
  session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH or PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP or PlaybackState.ACTION_SKIP_TO_NEXT or PlaybackState.ACTION_SKIP_TO_PREVIOUS).setState(if(player.isPlaying) PlaybackState.STATE_PLAYING else if(wanted && !interrupted) PlaybackState.STATE_BUFFERING else if(station==null) PlaybackState.STATE_NONE else PlaybackState.STATE_PAUSED,0,if(player.isPlaying) 1f else 0f).build())
  station?.let { session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,broadcast.value.ifBlank { it.name }).putString(MediaMetadata.METADATA_KEY_ARTIST,it.name).putString(MediaMetadata.METADATA_KEY_MEDIA_ID,it.id).putString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI,it.logo).putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI,it.logo).putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON,artwork).putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART,artwork).build()) }
  if(foregroundStarted && (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(this,android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED)) getSystemService(NotificationManager::class.java).notify(1,notification())
 }
 override fun onDestroy() {
  destroyed=true;scope.cancel();DebugLog.event("SERVICE","onDestroy");DebugLog.flush()
  networkCallback?.let {cb -> runCatching {getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb)}}
  getSharedPreferences("radio",0).unregisterOnSharedPreferenceChangeListener(preferenceListener); handler.removeCallbacksAndMessages(null); releaseEqualizer(); equalizerState.value=EqState(); broadcast.value=""; bufferedSeconds.value=0; cancelTask(); wanted=false; interrupted=false
  audio.abandonAudioFocusRequest(focus);player.removeListener(playerListener); player.release(); session.release(); playing.value=false; active.value=false; sleepAt.value=0; status.value=idleText(); stopForeground(STOP_FOREGROUND_REMOVE); super.onDestroy()
 }
 private fun playSearch(query:String?) {
  val q=query.orEmpty().trim();val all=store.stations()
  val match=if(q.isBlank()) store.startup() else all.find {it.name.equals(q,true)} ?: all.find {it.name.contains(q,true)} ?: q.split(' ').filter {it.length>2}.let {words -> all.find {s -> words.any {s.name.contains(it,true)}}}
  match?.let {start(it)} ?: mediaError(tr("Δεν βρέθηκε σταθμός. Πρόσθεσε σταθμούς από το κινητό.","No station found. Add stations from the phone."))
 }
 private fun mediaError(message:String) {
  pause();status.value=message
  session.setPlaybackState(PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PLAY_FROM_MEDIA_ID or PlaybackState.ACTION_PLAY_FROM_SEARCH).setState(PlaybackState.STATE_ERROR,0,0f).setErrorMessage(message).build())
 }
 override fun onGetRoot(clientPackageName:String,clientUid:Int,rootHints:Bundle?):BrowserRoot? {
  val ownsPackage=packageManager.getPackagesForUid(clientUid)?.contains(clientPackageName)==true
  val trusted=clientUid==applicationInfo.uid || getSystemService(android.media.session.MediaSessionManager::class.java).isTrustedForMediaControl(android.media.session.MediaSessionManager.RemoteUserInfo(clientPackageName,-1,clientUid))
  val auto=clientPackageName=="com.google.android.projection.gearhead"
  if(!ownsPackage || (!trusted && !auto)) return null
  DebugLog.event("AUTO","browser connected package=$clientPackageName")
  return BrowserRoot(if(rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT)==true) "recent" else "stations",null)
 }
 override fun onLoadChildren(parentId:String,result:Result<MutableList<android.media.browse.MediaBrowser.MediaItem>>) {
  val all=store.stations()
  val selected=when(parentId) {"stations" -> all;"recent" -> listOfNotNull(all.find {it.id==store.last} ?: store.startup());else -> emptyList()}
  result.sendResult(selected.map(::browserItem).toMutableList())
 }
 private fun browserItem(s:Station):android.media.browse.MediaBrowser.MediaItem {
  val description=android.media.MediaDescription.Builder().setMediaId(s.id).setTitle(s.name).setSubtitle("Internet radio")
  if(Playlist.valid(s.logo)) description.setIconUri(android.net.Uri.parse(s.logo))
  return android.media.browse.MediaBrowser.MediaItem(description.build(),android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE)
 }
 override fun onLoadItem(itemId:String,result:Result<android.media.browse.MediaBrowser.MediaItem>) {
  result.sendResult(store.stations().find {it.id==itemId}?.let(::browserItem))
 }
}
class BootReceiver: BroadcastReceiver() {
 override fun onReceive(c:Context,i:Intent) {
  val store=Store(c)
  DebugLog.event("BOOT","received=${i.action} enabled=${store.boot} station=${store.startup()!=null}")
  if(i.action==Intent.ACTION_BOOT_COMPLETED && store.boot && store.startup()!=null) {
   runCatching { RadioService.send(c,"BOOT") }.onFailure { e -> DebugLog.error("BOOT",e);c.getSharedPreferences("radio",0).edit().putString("bootError",e.toString()).apply() }
  }
 }
}
