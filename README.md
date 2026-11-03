# AutoTune Radio

Internet ραδιόφωνο για οθόνες αυτοκινήτου (Android 9+), με Android Auto, equalizer, screensaver, ημερολόγιο,
ελληνικά / English και δυναμικά θέματα. Package: `gr.radiorise.player`.

**Κατέβασμα:** [τελευταία έκδοση (Releases)](https://github.com/nikkpap/AutoTuneRadio/releases/latest) → αρχείο `AutoTuneRadio-v….apk`.

---

## Πώς δουλεύουν οι ενημερώσεις (απλά)

1. Κάνεις αλλαγή στον κώδικα και **push στο `main`**.
2. Το **GitHub Actions** χτίζει το APK, τρέχει τα tests και το υπογράφει με το δικό σου κλειδί.
3. Δημιουργεί αυτόματα **Release** `v0.23.<αριθμός>` με το APK.
4. Στη συσκευή: η εφαρμογή ελέγχει μόνη της κάθε 6 ώρες (ή πατάς **Ρυθμίσεις → Σχετικά → Έλεγχος για ενημερώσεις**)
   → **Λήψη & εγκατάσταση** → **Ενημέρωση** στην οθόνη του Android. Σταθμοί και ρυθμίσεις μένουν.

> Την πρώτη φορά το Android ζητά «Εγκατάσταση άγνωστων εφαρμογών» για το AutoTune Radio.
> Η εφαρμογή σε πάει εκεί· ενεργοποιείς και γυρνάς πίσω — συνεχίζει μόνη της.

Αν δεν θέλεις release σε κάποιο push, γράψε `[no release]` στο μήνυμα του commit.
Νέα κύρια έκδοση (π.χ. 0.24): άλλαξε `val baseVersion = "0.23"` στο `app/build.gradle.kts`.

---

## GitHub: ρύθμιση μία φορά (κλειδί υπογραφής)

Το κλειδί **δεν** ανεβαίνει στο repo (είναι δημόσιο). Μπαίνει ως κρυφό *Secret*:

1. Άνοιξε το αρχείο **`keystore-base64.txt`** (σου το έδωσε το Claude) και αντέγραψε **όλο** το κείμενο.
   *(Ή στα Windows PowerShell, μέσα στον φάκελο του project:*
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\autotune-debug.keystore")) | Set-Clipboard`*)*
2. GitHub → **AutoTuneRadio → Settings → Secrets and variables → Actions → New repository secret**.
3. Name: **`SIGNING_KEYSTORE_BASE64`** · Secret: επικόλληση → **Add secret**.
4. **Actions** → τελευταίο run → **Re-run all jobs** (ή κάνε οποιοδήποτε push). Τέλος!

Χωρίς το Secret, το Actions χτίζει και ελέγχει τον κώδικα αλλά **δεν** βγάζει Release
(ένα APK με άλλο κλειδί δεν θα μπορούσε να ενημερώσει την εγκατεστημένη εφαρμογή).

---

## Build στον υπολογιστή (Android Studio)

1. Βάλε το `autotune-debug.keystore` μέσα στον φάκελο `app/` (δεν ανεβαίνει στο Git — το `.gitignore` το αγνοεί).
2. Άνοιξε τον φάκελο στο Android Studio → περίμενε το Gradle sync (κατεβάζει μόνο του ό,τι χρειάζεται).
3. **Build → Build APK(s)** ή από τερματικό: `gradlew.bat assembleDebug`
4. Έλεγχοι: `gradlew.bat testDebugUnitTest lintDebug` και `bash verification/run-core-checks.sh`.

## Αρχεία

| | |
|---|---|
| `app/src/main/java/gr/radiorise/player/` | κώδικας (Kotlin / Compose) |
| `Updater.kt`, `UpdateScreen.kt` | ενημερώσεις από GitHub Releases |
| `.github/workflows/android.yml` | αυτόματο build & release |
| `docs/CHANGELOG_0.23.md` | τι άλλαξε στην 0.23 |
| `docs/HISTORY.md` | παλαιότερες σημειώσεις εκδόσεων |
| `ANDROID_AUTO.md`, `SHORTCUTS.md` | Android Auto, κουμπιά εφαρμογών |

ALU DEV TEAM © 2026 · Nikolaos K. Paridis (nikkpap)
