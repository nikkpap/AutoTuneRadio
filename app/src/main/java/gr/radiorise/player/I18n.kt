package gr.radiorise.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Lightweight GR/EN switch. The value is Compose snapshot state, so every screen that calls tr()
 * recomposes instantly when the language changes — no restart, no Activity recreation.
 * First launch follows the device language (Greek → GR, anything else → EN).
 */
object Lang {
 var english by mutableStateOf(false)
  private set
 fun init(c:Context) {
  val saved=c.getSharedPreferences("radio",0).getString("lang","").orEmpty()
  val en=when(saved) {"en" -> true; "el" -> false; else -> Locale.getDefault().language!="el"}
  english=en;Tr.english=en
 }
 fun set(c:Context,en:Boolean) {
  english=en;Tr.english=en
  c.getSharedPreferences("radio",0).edit().putString("lang",if(en) "en" else "el").apply()
  DebugLog.event("LANG","english=$en")
 }
 val locale:Locale get()=if(english) Locale.ENGLISH else Locale.forLanguageTag("el-GR")
}

/** Returns the Greek or English text for the active app language. */
fun tr(gr:String,en:String):String=if(Lang.english) en else gr
