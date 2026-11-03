package gr.radiorise.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private fun sizeLabel(bytes:Long)=if(bytes<=0) "" else String.format(Locale.US,"%.1f MB",bytes/1_048_576f)

/** Step-by-step update dialog: check → download → install. */
@Composable fun UpdateDialog(state:UpdateState,onDismiss:()->Unit) {
 val context=LocalContext.current
 val radioActive by RadioService.active.collectAsState()
 fun openPage(url:String) {runCatching {context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))}}
 val releasesPage="https://github.com/${BuildConfig.UPDATE_REPO}/releases"
 AlertDialog(onDismissRequest={Updater.dismiss();onDismiss()},icon={Icon(Icons.Default.Refresh,null)},
  title={Text(tr("Ενημέρωση εφαρμογής","App update"))},
  text={Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
   Text(tr("Εγκατεστημένη: ","Installed: ")+BuildConfig.VERSION_NAME,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
   when(state) {
    UpdateState.Idle,UpdateState.Checking -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     CircularProgressIndicator(Modifier.size(28.dp));Text(tr("Έλεγχος στο GitHub…","Checking GitHub…"))
    }
    is UpdateState.UpToDate -> Text(tr("✓ Έχεις την τελευταία έκδοση (${state.latest}).","✓ You have the latest version (${state.latest})."),color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
    is UpdateState.Available -> ReleaseSummary(state.release,tr("Νέα έκδοση διαθέσιμη!","New version available!"))
    is UpdateState.Downloading -> {
     ReleaseSummary(state.release,tr("Λήψη…","Downloading…"))
     LinearProgressIndicator(progress={state.progress},modifier=Modifier.fillMaxWidth().height(8.dp))
     Text("${(state.progress*100).toInt()}%",fontWeight=FontWeight.Bold)
    }
    is UpdateState.Ready -> {
     ReleaseSummary(state.release,tr("Η λήψη ολοκληρώθηκε ✓","Download complete ✓"))
     if(state.needsPermission) {
      Text(tr("Ένα βήμα ακόμα:","One more step:"),fontWeight=FontWeight.Bold)
      Text(tr("1. Πάτα «Άδεια εγκατάστασης».\n2. Ενεργοποίησε «Να επιτρέπεται από αυτή την πηγή».\n3. Γύρνα πίσω — η εγκατάσταση συνεχίζει μόνη της.","1. Tap “Allow installing”.\n2. Turn on “Allow from this source”.\n3. Come back — the installation continues by itself."))
     } else {
      if(state.auto && radioActive) Text(tr("Θα εγκατασταθεί μόνη της όταν σταματήσει το ραδιόφωνο. Ή πάτα «Εγκατάσταση τώρα» (ο ήχος θα κοπεί για λίγα δευτερόλεπτα).","It installs by itself when the radio stops. Or tap “Install now” (sound stops for a few seconds)."),color=MaterialTheme.colorScheme.primary)
      Text(tr("Αν εμφανιστεί η οθόνη του Android, πάτα «Ενημέρωση». Οι σταθμοί και οι ρυθμίσεις μένουν.","If Android's screen appears, tap “Update”. Stations and settings are kept."))
     }
    }
    is UpdateState.Installing -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     CircularProgressIndicator(Modifier.size(28.dp));Text(tr("Εγκατάσταση… επιβεβαίωσε στην οθόνη του Android.","Installing… confirm on Android's screen."))
    }
    is UpdateState.Error -> {
     Text(state.message,color=MaterialTheme.colorScheme.error)
     Text(tr("Μπορείς επίσης να κατεβάσεις το APK από τη σελίδα του GitHub.","You can also download the APK from the GitHub page."),fontSize=13.sp)
    }
   }
  }},
  confirmButton={
   when(state) {
    is UpdateState.Available -> Button({Updater.download(context,state.release)}) {Text(tr("Λήψη & εγκατάσταση","Download & install"))}
    is UpdateState.Ready -> if(state.needsPermission) Button({Updater.openInstallPermission(context)}) {Text(tr("Άδεια εγκατάστασης","Allow installing"))}
     else Button({Updater.install(context)}) {Text(tr("Εγκατάσταση τώρα","Install now"))}
    is UpdateState.Error -> Button({if(state.release!=null) Updater.download(context,state.release) else Updater.check(context)}) {Text(tr("Ξανά","Retry"))}
    is UpdateState.UpToDate -> Button({Updater.dismiss();onDismiss()}) {Text("OK")}
    else -> TextButton({Updater.dismiss();onDismiss()}) {Text(tr("Κλείσιμο","Close"))}
   }
  },
  dismissButton={
   when(state) {
    is UpdateState.Available -> TextButton({Updater.dismiss();onDismiss()}) {Text(tr("Αργότερα","Later"))}
    is UpdateState.Error -> TextButton({openPage(state.release?.pageUrl?.ifBlank {null} ?: releasesPage)}) {Text("GitHub")}
    is UpdateState.Ready -> TextButton({onDismiss()}) {Text(tr("Αργότερα","Later"))}
    else -> {}
   }
  })
}

@Composable private fun ReleaseSummary(release:ReleaseInfo,headline:String) {
 Text(headline,fontWeight=FontWeight.Bold,fontSize=18.sp,color=MaterialTheme.colorScheme.primary)
 Text(tr("Έκδοση ","Version ")+release.version+(sizeLabel(release.size).takeIf {it.isNotBlank()}?.let {" · $it"} ?: ""),fontWeight=FontWeight.Medium)
 if(release.notes.isNotBlank()) Surface(color=MaterialTheme.colorScheme.surfaceContainerHighest,shape=MaterialTheme.shapes.medium) {
  Text(release.notes,Modifier.fillMaxWidth().heightIn(max=220.dp).verticalScroll(rememberScrollState()).padding(12.dp),fontSize=13.sp)
 }
}

/** Card for Settings → About. */
@Composable fun UpdateSettings(store:Store,revision:Int,onOpen:()->Unit) {
 val context=LocalContext.current
 val auto=remember(revision) {store.autoUpdate}
 val state by Updater.state.collectAsState()
 Text(tr("Ενημερώσεις","Updates"),fontWeight=FontWeight.Bold,fontSize=20.sp)
 Text(tr("Οι νέες εκδόσεις έρχονται από το GitHub (${BuildConfig.UPDATE_REPO}). Εγκατεστημένη: ${BuildConfig.VERSION_NAME}","New versions come from GitHub (${BuildConfig.UPDATE_REPO}). Installed: ${BuildConfig.VERSION_NAME}"),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
 if(state is UpdateState.Available) Text(tr("Διαθέσιμη: ","Available: ")+(state as UpdateState.Available).release.version,color=MaterialTheme.colorScheme.primary,fontWeight=FontWeight.Bold)
 Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
  Column(Modifier.weight(1f)) {
   Text(tr("Αυτόματες ενημερώσεις","Automatic updates"),fontWeight=FontWeight.Bold)
   Text(tr("Ελέγχει σε κάθε άνοιγμα και κάθε 6 ώρες, κατεβάζει και εγκαθιστά μόνο του — ποτέ όσο παίζει το ραδιόφωνο. Κλειστό: μόνο ειδοποίηση.","Checks on every open and every 6 hours, downloads and installs by itself — never while the radio plays. Off: notify only."),fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
  }
  Switch(auto,{store.autoUpdate=it})
 }
 Button({if(state is UpdateState.Idle || state is UpdateState.UpToDate || state is UpdateState.Error) Updater.check(context);onOpen()},Modifier.fillMaxWidth().heightIn(min=56.dp)) {
  Text(if(state is UpdateState.Available || state is UpdateState.Ready || state is UpdateState.Downloading) tr("Δες τη νέα έκδοση","See the new version") else tr("Έλεγχος για ενημερώσεις","Check for updates"))
 }
}
