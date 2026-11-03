package gr.radiorise.player

import android.content.Context
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** A colour theme. Every screen reads its colours from MaterialTheme / LocalLook, so a change applies instantly. */
data class Palette(val id:String,val gr:String,val en:String,val dark:Boolean,val primary:Color,val secondary:Color,val tertiary:Color,val background:Color,val surface:Color,val gradA:Color,val gradB:Color) {
 val label:String get()=tr(gr,en)
}

/** Resolved look: Material colour scheme + player gradient + accent that stays readable on the black screensaver. */
data class AppLook(val scheme:ColorScheme,val gradA:Color,val gradB:Color,val dark:Boolean,val accentOnBlack:Color)

val LocalLook=compositionLocalOf {Themes.look(Themes.ocean)}

object Themes {
 const val AUTO="auto"
 const val YOU="you"
 const val STATION="station"
 val ocean=Palette("ocean","Ωκεανός","Ocean",true,Color(0xFF65E2C7),Color(0xFFACA5FF),Color(0xFFFFC27A),Color(0xFF0B1020),Color(0xFF151C30),Color(0xFF1B3D45),Color(0xFF1D203B))
 val midnight=Palette("midnight","Μεσάνυχτα AMOLED","Midnight AMOLED",true,Color(0xFF8AB4FF),Color(0xFFB39DFF),Color(0xFF7CE0C3),Color(0xFF000000),Color(0xFF0B0C10),Color(0xFF0F1A2E),Color(0xFF040508))
 val sunset=Palette("sunset","Ηλιοβασίλεμα","Sunset",true,Color(0xFFFFB27A),Color(0xFFFF8FB4),Color(0xFFFFDA75),Color(0xFF150B12),Color(0xFF22131D),Color(0xFF4D1F2E),Color(0xFF2A1531))
 val forest=Palette("forest","Δάσος","Forest",true,Color(0xFFA1E38F),Color(0xFFDCCB7E),Color(0xFF7ED3D1),Color(0xFF09120C),Color(0xFF131F17),Color(0xFF1C3B26),Color(0xFF11251F))
 val royal=Palette("royal","Βασιλικό μωβ","Royal purple",true,Color(0xFFCDAEFF),Color(0xFFFFA2DA),Color(0xFF8FD4FF),Color(0xFF100B1D),Color(0xFF1B142D),Color(0xFF35205C),Color(0xFF191338))
 val crimson=Palette("crimson","Κόκκινο racing","Racing red",true,Color(0xFFFF8F85),Color(0xFFFFC47E),Color(0xFF9CC8FF),Color(0xFF130909),Color(0xFF211212),Color(0xFF4D1616),Color(0xFF1F1010))
 val amber=Palette("amber","Κεχριμπάρι νύχτας","Night amber",true,Color(0xFFFFC75F),Color(0xFFFF9D5C),Color(0xFFB7E07A),Color(0xFF0F0B05),Color(0xFF1C160C),Color(0xFF3A2A0E),Color(0xFF1E1408))
 val daylight=Palette("daylight","Ημέρα (ανοιχτό)","Daylight (light)",false,Color(0xFF00696B),Color(0xFF5552B0),Color(0xFF9A4600),Color(0xFFF3F6F9),Color(0xFFFFFFFF),Color(0xFFCDEDE8),Color(0xFFE1E0FA))
 val presets=listOf(ocean,midnight,sunset,forest,royal,crimson,amber,daylight)
 fun preset(id:String)=presets.find {it.id==id}

 /** Builds a dark palette around the dominant hue of the current station logo. */
 fun fromSeed(hue:Float,sat:Float):Palette {
  val s=sat.coerceIn(0.35f,0.85f)
  fun h(offset:Float)=(hue+offset+360f)%360f
  return Palette(STATION,"Χρώματα σταθμού","Station colours",true,
   Color.hsl(h(0f),s,0.72f),Color.hsl(h(45f),s*0.85f,0.76f),Color.hsl(h(180f),0.55f,0.74f),
   Color.hsl(h(0f),0.32f,0.055f),Color.hsl(h(0f),0.26f,0.105f),Color.hsl(h(0f),0.45f,0.19f),Color.hsl(h(35f),0.35f,0.12f))
 }
 fun parseSeed(value:String):Pair<Float,Float>? {
  val parts=value.split(',');if(parts.size!=2) return null
  val hue=parts[0].toFloatOrNull() ?: return null;val sat=parts[1].toFloatOrNull() ?: return null
  return hue.coerceIn(0f,360f) to sat.coerceIn(0f,1f)
 }
 fun look(p:Palette)=AppLook(scheme(p),p.gradA,p.gradB,p.dark,if(p.dark) p.primary else lerp(p.primary,Color.White,0.45f))
 fun scheme(p:Palette):ColorScheme {
  val bg=p.background;val sf=p.surface
  return if(p.dark) {
   val on=Color(0xFFE7EAF1)
   darkColorScheme(
    primary=p.primary,onPrimary=lerp(p.primary,Color.Black,0.8f),primaryContainer=lerp(sf,p.primary,0.30f),onPrimaryContainer=lerp(p.primary,Color.White,0.65f),inversePrimary=lerp(p.primary,Color.Black,0.45f),
    secondary=p.secondary,onSecondary=lerp(p.secondary,Color.Black,0.8f),secondaryContainer=lerp(sf,p.secondary,0.24f),onSecondaryContainer=lerp(p.secondary,Color.White,0.65f),
    tertiary=p.tertiary,onTertiary=lerp(p.tertiary,Color.Black,0.8f),tertiaryContainer=lerp(sf,p.tertiary,0.24f),onTertiaryContainer=lerp(p.tertiary,Color.White,0.65f),
    background=bg,onBackground=on,surface=bg,onSurface=on,surfaceVariant=lerp(sf,p.primary,0.10f),onSurfaceVariant=lerp(Color(0xFFA9B3C4),p.primary,0.12f),
    surfaceTint=p.primary,inverseSurface=on,inverseOnSurface=bg,outline=lerp(sf,Color.White,0.32f),outlineVariant=lerp(sf,Color.White,0.14f),
    surfaceBright=lerp(sf,Color.White,0.12f),surfaceDim=bg,surfaceContainerLowest=lerp(bg,Color.Black,0.4f),surfaceContainerLow=lerp(bg,sf,0.6f),
    surfaceContainer=sf,surfaceContainerHigh=lerp(sf,Color.White,0.045f),surfaceContainerHighest=lerp(sf,Color.White,0.08f)
   )
  } else {
   val on=Color(0xFF161B22)
   lightColorScheme(
    primary=p.primary,onPrimary=Color.White,primaryContainer=lerp(Color.White,p.primary,0.20f),onPrimaryContainer=lerp(p.primary,Color.Black,0.55f),inversePrimary=lerp(p.primary,Color.White,0.45f),
    secondary=p.secondary,onSecondary=Color.White,secondaryContainer=lerp(Color.White,p.secondary,0.18f),onSecondaryContainer=lerp(p.secondary,Color.Black,0.55f),
    tertiary=p.tertiary,onTertiary=Color.White,tertiaryContainer=lerp(Color.White,p.tertiary,0.18f),onTertiaryContainer=lerp(p.tertiary,Color.Black,0.55f),
    background=bg,onBackground=on,surface=bg,onSurface=on,surfaceVariant=lerp(bg,p.primary,0.08f),onSurfaceVariant=Color(0xFF4A5566),
    surfaceTint=p.primary,inverseSurface=Color(0xFF2B3038),inverseOnSurface=Color(0xFFEFF1F5),outline=Color(0xFF7A8494),outlineVariant=Color(0xFFC6CCD6),
    surfaceBright=Color.White,surfaceDim=lerp(bg,Color.Black,0.06f),surfaceContainerLowest=Color.White,surfaceContainerLow=lerp(Color.White,bg,0.6f),
    surfaceContainer=lerp(bg,p.primary,0.03f),surfaceContainerHigh=lerp(bg,p.primary,0.055f),surfaceContainerHighest=lerp(bg,p.primary,0.08f)
   )
  }
 }
}

/** Resolves the selected theme. Re-evaluated when settings change (revision) or the system switches day/night. */
@Composable fun rememberLook(store:Store,revision:Int):AppLook {
 val context=LocalContext.current
 val systemDark=isSystemInDarkTheme()
 val id=remember(revision) {store.theme}
 val seed=remember(revision) {store.themeSeed}
 return remember(id,seed,systemDark) {
  when(id) {
   Themes.AUTO -> Themes.look(if(systemDark) Themes.ocean else Themes.daylight)
   Themes.YOU -> if(Build.VERSION.SDK_INT>=31) {
    val s=if(systemDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    AppLook(s,lerp(s.primaryContainer,s.background,0.25f),lerp(s.secondaryContainer,s.background,0.45f),systemDark,if(systemDark) s.primary else s.inversePrimary)
   } else Themes.look(Themes.ocean)
   Themes.STATION -> Themes.look(Themes.parseSeed(seed)?.let {(h,s) -> Themes.fromSeed(h,s)} ?: Themes.ocean)
   else -> Themes.look(Themes.preset(id) ?: Themes.ocean)
  }
 }
}

private const val THEME_MS=550

/** Smoothly cross-fades every colour role when the theme changes. */
@Composable private fun ColorScheme.animated():ColorScheme {
 val spec=tween<Color>(THEME_MS)
 return copy(
  primary=animateColorAsState(primary,spec,label="primary").value,
  onPrimary=animateColorAsState(onPrimary,spec,label="onPrimary").value,
  primaryContainer=animateColorAsState(primaryContainer,spec,label="primaryContainer").value,
  onPrimaryContainer=animateColorAsState(onPrimaryContainer,spec,label="onPrimaryContainer").value,
  secondary=animateColorAsState(secondary,spec,label="secondary").value,
  secondaryContainer=animateColorAsState(secondaryContainer,spec,label="secondaryContainer").value,
  onSecondaryContainer=animateColorAsState(onSecondaryContainer,spec,label="onSecondaryContainer").value,
  tertiary=animateColorAsState(tertiary,spec,label="tertiary").value,
  background=animateColorAsState(background,spec,label="background").value,
  onBackground=animateColorAsState(onBackground,spec,label="onBackground").value,
  surface=animateColorAsState(surface,spec,label="surface").value,
  onSurface=animateColorAsState(onSurface,spec,label="onSurface").value,
  surfaceVariant=animateColorAsState(surfaceVariant,spec,label="surfaceVariant").value,
  onSurfaceVariant=animateColorAsState(onSurfaceVariant,spec,label="onSurfaceVariant").value,
  outline=animateColorAsState(outline,spec,label="outline").value,
  outlineVariant=animateColorAsState(outlineVariant,spec,label="outlineVariant").value,
  surfaceContainerLow=animateColorAsState(surfaceContainerLow,spec,label="containerLow").value,
  surfaceContainer=animateColorAsState(surfaceContainer,spec,label="container").value,
  surfaceContainerHigh=animateColorAsState(surfaceContainerHigh,spec,label="containerHigh").value,
  surfaceContainerHighest=animateColorAsState(surfaceContainerHighest,spec,label="containerHighest").value
 )
}

@Composable fun AppTheme(look:AppLook,content:@Composable ()->Unit) {
 val scheme=look.scheme.animated()
 val gradA by animateColorAsState(look.gradA,tween(THEME_MS),label="gradA")
 val gradB by animateColorAsState(look.gradB,tween(THEME_MS),label="gradB")
 val accent by animateColorAsState(look.accentOnBlack,tween(THEME_MS),label="accent")
 CompositionLocalProvider(LocalLook provides look.copy(scheme=scheme,gradA=gradA,gradB=gradB,accentOnBlack=accent)) {
  MaterialTheme(colorScheme=scheme,content=content)
 }
}

/** Extracts the dominant colourful hue of a station logo as (hue°, saturation). Null when the logo is grey/unavailable. */
suspend fun logoSeed(context:Context,url:String):Pair<Float,Float>? = withContext(Dispatchers.IO) {
 runCatching {
  val result=context.imageLoader.execute(ImageRequest.Builder(context).data(url).size(64).allowHardware(false).build())
  val bitmap=result.drawable?.toBitmap(32,32) ?: return@runCatching null
  var x=0.0;var y=0.0;var weight=0.0;var saturation=0.0
  val hsv=FloatArray(3)
  for(px in 0 until bitmap.width) for(py in 0 until bitmap.height) {
   val c=bitmap.getPixel(px,py)
   if(android.graphics.Color.alpha(c)<128) continue
   android.graphics.Color.colorToHSV(c,hsv)
   if(hsv[1]<0.2f || hsv[2]<0.2f) continue
   val w=(hsv[1]*hsv[2]).toDouble();val rad=Math.toRadians(hsv[0].toDouble())
   x+=cos(rad)*w;y+=sin(rad)*w;weight+=w;saturation+=hsv[1]*w
  }
  if(weight<0.6) null else (((Math.toDegrees(atan2(y,x))+360.0)%360.0).toFloat() to (saturation/weight).toFloat())
 }.onFailure {DebugLog.error("THEME_SEED",it)}.getOrNull()
}

/** Material icons that are not part of material-icons-core (paths from Material Design Icons, Apache-2.0). */
object AppIcons {
 private fun icon(name:String,vararg paths:String):ImageVector = ImageVector.Builder(name=name,defaultWidth=24.dp,defaultHeight=24.dp,viewportWidth=24f,viewportHeight=24f).apply {
  paths.forEach {addPath(pathData=addPathNodes(it),fill=SolidColor(Color.Black))}
 }.build()
 val Pause by lazy {icon("Pause","M6,19h4V5H6v14zM14,5v14h4V5h-4z")}
 val SkipNext by lazy {icon("SkipNext","M6,18l8.5,-6L6,6v12zM16,6v12h2V6h-2z")}
 val SkipPrevious by lazy {icon("SkipPrevious","M6,6h2v12H6zM9.5,12l8.5,6V6z")}
 val Timer by lazy {icon("Timer","M15,1H9v2h6V1zM11,14h2V8h-2v6zM19.03,7.39l1.42,-1.42c-0.43,-0.51 -0.9,-0.99 -1.41,-1.41l-1.42,1.42C16.07,4.74 14.12,4 12,4c-4.97,0 -9,4.03 -9,9s4.02,9 9,9 9,-4.03 9,-9c0,-2.12 -0.74,-4.07 -1.97,-5.61zM12,20c-3.87,0 -7,-3.13 -7,-7s3.13,-7 7,-7 7,3.13 7,7 -3.13,7 -7,7z")}
 val MusicNote by lazy {icon("MusicNote","M12,3v10.55c-0.59,-0.34 -1.27,-0.55 -2,-0.55 -2.21,0 -4,1.79 -4,4s1.79,4 4,4 4,-1.79 4,-4V7h4V3h-6z")}
 val Palette by lazy {icon("Palette","M12,3c-4.97,0 -9,4.03 -9,9s4.03,9 9,9c0.83,0 1.5,-0.67 1.5,-1.5 0,-0.39 -0.15,-0.74 -0.39,-1.01 -0.23,-0.26 -0.38,-0.61 -0.38,-0.99 0,-0.83 0.67,-1.5 1.5,-1.5H16c2.76,0 5,-2.24 5,-5 0,-4.42 -4.03,-8 -9,-8zM6.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S5.67,9 6.5,9 8,9.67 8,10.5 7.33,12 6.5,12zM9.5,8C8.67,8 8,7.33 8,6.5S8.67,5 9.5,5s1.5,0.67 1.5,1.5S10.33,8 9.5,8zM14.5,8c-0.83,0 -1.5,-0.67 -1.5,-1.5S13.67,5 14.5,5s1.5,0.67 1.5,1.5S15.33,8 14.5,8zM17.5,12c-0.83,0 -1.5,-0.67 -1.5,-1.5S16.67,9 17.5,9s1.5,0.67 1.5,1.5 -0.67,1.5 -1.5,1.5z")}
 val Translate by lazy {icon("Translate","M12.87,15.07l-2.54,-2.51 0.03,-0.03c1.74,-1.94 2.98,-4.17 3.71,-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5,7.92 10.44,9.75 9,11.35 8.07,10.32 7.3,9.19 6.69,8h-2c0.73,1.63 1.73,3.17 2.98,4.56l-5.09,5.02L4,19l5,-5 3.11,3.11 0.76,-2.04zM18.5,10h-2L12,22h2l1.12,-3h4.75L21,22h2l-4.5,-12zM15.88,17l1.62,-4.33L19.12,17h-3.24z")}
}

/** Pulsing "on air" dot. The animation only runs while active (saves battery). */
@Composable fun LiveDot(active:Boolean,color:Color,size:Dp=10.dp) {
 if(!active) {Box(Modifier.size(size).background(color.copy(alpha=0.45f),CircleShape));return}
 val pulse=rememberInfiniteTransition(label="live")
 val a by pulse.animateFloat(0.35f,1f,infiniteRepeatable(tween(900,easing=FastOutSlowInEasing),RepeatMode.Reverse),label="liveAlpha")
 Box(Modifier.size(size).alpha(a).background(color,CircleShape))
}

/** Small animated equaliser bars shown while a station is playing (decorative, no audio analysis). Give the modifier a height. */
@Composable fun VisualizerBars(active:Boolean,color:Color,modifier:Modifier=Modifier,bars:Int=5,barWidth:Dp=4.dp) {
 Row(modifier,horizontalArrangement=Arrangement.spacedBy(barWidth/2),verticalAlignment=Alignment.Bottom) {
  if(active) {
   val transition=rememberInfiniteTransition(label="bars")
   repeat(bars) {i ->
    val level by transition.animateFloat(0.2f,1f,infiniteRepeatable(tween(BAR_MS[i%BAR_MS.size],easing=FastOutSlowInEasing),RepeatMode.Reverse),label="bar$i")
    Box(Modifier.width(barWidth).fillMaxHeight(level).background(color,RoundedCornerShape(barWidth/2)))
   }
  } else repeat(bars) {i ->
   Box(Modifier.width(barWidth).fillMaxHeight(0.18f+0.08f*(i%3)).background(color.copy(alpha=0.5f),RoundedCornerShape(barWidth/2)))
  }
 }
}
private val BAR_MS=listOf(520,740,430,660,580,810,470)

/** Fixed-height wrapper for VisualizerBars. */
@Composable fun MiniBars(active:Boolean,color:Color,height:Dp=14.dp) {
 VisualizerBars(active,color,Modifier.height(height),bars=4,barWidth=3.dp)
}
