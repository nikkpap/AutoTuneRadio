# AGENTS.md — AutoTune Radio

Οδηγίες για κάθε AI agent (Claude, Codex κ.λπ.) ή προγραμματιστή που δουλεύει σε αυτό το repo.
**Στόχος:** κάθε αλλαγή φτάνει στο `main`, το GitHub Actions χτίζει υπογεγραμμένο APK, βγάζει Release,
και οι συσκευές ενημερώνονται μόνες τους (OTA). Μην σπάσεις αυτή την αλυσίδα.

*Instructions for any AI agent or developer. Goal: every change lands on `main` → GitHub Actions builds a
signed APK → publishes a Release → installed apps update themselves (OTA). Do not break this chain.*

---

## 1. Το project με μια ματιά

| | |
|---|---|
| App | AutoTune Radio — internet radio για οθόνες αυτοκινήτου + Android Auto |
| Package / applicationId | `gr.radiorise.player` (**ποτέ μην το αλλάξεις** — αλλιώς δεν γίνεται ενημέρωση) |
| Γλώσσα / UI | Kotlin, Jetpack Compose (Material 3), Media3 ExoPlayer |
| minSdk / targetSdk / compileSdk | 28 / 34 / 35 |
| JDK | 17 |
| Repo | `github.com/nikkpap/AutoTuneRadio` (**public**) |
| Default branch | `main` |
| Workflow | `.github/workflows/android.yml` («Build & Release») |
| OTA κώδικας | `app/src/main/java/gr/radiorise/player/Updater.kt`, `UpdateScreen.kt` |

---

## 2. Η ροή (push → build → release → OTA)

```
push στο main
  └─► GitHub Actions «Build & Release»
        1. Επαναφορά κλειδιού από το secret SIGNING_KEYSTORE_BASE64 → app/autotune-debug.keystore
        2. Έκδοση = appVersionBase (gradle.properties) + "." + github.run_number   π.χ. 0.23.11
        3. verification/run-core-checks.sh            (Java checks)
        4. ./gradlew assembleDebug testDebugUnitTest lintDebug
        5. dist/AutoTuneRadio-v<έκδοση>.apk + SHA256SUMS.txt
        6. GitHub Release  tag v<έκδοση>, σημειώσεις = μήνυμα του commit, make_latest
  └─► Εφαρμογή (Updater.kt)
        • ελέγχει api.github.com/repos/nikkpap/AutoTuneRadio/releases/latest
          σε κάθε άνοιγμα (≥30′) και κάθε 6 ώρες (WorkManager)
        • κατεβάζει το πρώτο asset *.apk, ελέγχει μέγεθος + SHA-256 (πεδίο "digest")
        • εγκαθιστά με PackageInstaller — ΠΟΤΕ όσο παίζει το ραδιόφωνο
```

**Κανόνες που ενεργοποιούν / σταματούν Release**

| Περίπτωση | Release; |
|---|---|
| push στο `main` με αλλαγή σε κώδικα/πόρους | ✅ ναι |
| μήνυμα commit περιέχει `[no release]` | ❌ όχι (μόνο build/έλεγχοι) |
| αλλάζουν **μόνο** αρχεία `*.md` ή `docs/**` | ❌ δεν τρέχει καν |
| pull request | ❌ μόνο build/έλεγχοι |
| λείπει το secret κλειδιού | ❌ build με προσωρινό κλειδί, χωρίς Release (warning) |
| αποτυχία compile / test / lint | ❌ κόκκινο ✗, καμία έκδοση — οι συσκευές δεν επηρεάζονται |
| Actions → Build & Release → **Run workflow** | ✅ νέα έκδοση χωρίς αλλαγές |

---

## 3. Υπογραφή (το πιο κρίσιμο σημείο)

Το Android εγκαθιστά ενημέρωση **μόνο** αν το νέο APK έχει **ίδια υπογραφή** με το εγκατεστημένο.
Αν αλλάξει το κλειδί, όλοι οι χρήστες πρέπει να απεγκαταστήσουν (χάνουν σταθμούς/ρυθμίσεις).

| Στοιχείο | Τιμή |
|---|---|
| Αρχείο | `app/autotune-debug.keystore` (**δεν** μπαίνει στο git — `.gitignore`: `*.keystore`, `*.jks`) |
| GitHub secret | `SIGNING_KEYSTORE_BASE64` = base64 του αρχείου (μία γραμμή) |
| Alias / passwords | `androiddebugkey` / `android` / `android` (προεπιλογές στο `app/build.gradle.kts`) |
| Προαιρετικά secrets | `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD` (αν ποτέ αλλάξουν) |
| SHA-256 πιστοποιητικού | `79:AC:33:…:5D:EA` — έλεγξε ότι κάθε Release έχει αυτό |
| Build type | `debug` υπογεγραμμένο με το παραπάνω κλειδί (το `signingConfigs.debug` το φορτώνει αν υπάρχει το αρχείο) |

**Ρύθμιση secret (μία φορά, γίνεται από τον ιδιοκτήτη — τα agents δεν έχουν πρόσβαση στα Secrets):**
1. Windows PowerShell στον φάκελο του project:
   `[Convert]::ToBase64String([IO.File]::ReadAllBytes("app\autotune-debug.keystore")) | Set-Clipboard`
2. GitHub → repo → **Settings → Secrets and variables → Actions → New repository secret**
3. Name `SIGNING_KEYSTORE_BASE64`, Secret = επικόλληση → **Add secret**
4. Actions → τελευταίο run → **Re-run all jobs**

**Τοπικά (Android Studio):** βάλε το `autotune-debug.keystore` μέσα στο `app/` για να ταιριάζει το **Run ▶**
με τις εγκατεστημένες εκδόσεις.

### ⛔ Ποτέ
- Μην κάνεις commit το keystore ή το base64 του (το repo είναι public).
- Μην αλλάξεις `applicationId`, κλειδί, alias ή τον τύπο build (debug→release) χωρίς να ρωτήσεις τον ιδιοκτήτη.
- Μην αφαιρέσεις το `REQUEST_INSTALL_PACKAGES` ή τον `UpdateReceiver` από το `AndroidManifest.xml`.
- Μην αλλάξεις το `UPDATE_REPO` (`nikkpap/AutoTuneRadio` στο `buildConfigField`).
- Μην μετονομάσεις το asset: η εφαρμογή παίρνει το **πρώτο** `*.apk` του Release.
- Μην κάνεις force-push / rewrite στο `main`, ούτε σβήνεις tags/Releases.

---

## 4. Εκδόσεις

- `gradle.properties` → `appVersionBase=0.23` (μόνο αυτό αλλάζει ο άνθρωπος, π.χ. σε `0.24`).
- CI: `BUILD_NUMBER=github.run_number` →
  `versionName = <base>.<run>` (π.χ. `0.23.11`), `versionCode = major*100000 + minor*1000 + run` (π.χ. `23011`).
- Τοπικό build: `0.23.0` / `23000` — πάντα μικρότερο από τα CI builds, άρα το OTA το αντικαθιστά.
- Η σύγκριση στην εφαρμογή είναι αριθμητική ανά τμήμα (`Updater.compare`): `0.23.10 > 0.23.9`.
- Το `versionCode` πρέπει **πάντα να ανεβαίνει**. Μην το βάλεις σταθερό, μην κατεβάσεις το `appVersionBase`.

---

## 5. Πώς δουλεύει ένα agent σε αυτό το repo

1. **Συγχρονισμός πρώτα:** `git fetch origin main && git rebase origin/main` (ο ιδιοκτήτης κάνει κι αυτός push
   από Android Studio ή από το web — π.χ. `AutoTuneRadio-stations.txt`).
2. Κάνε τις αλλαγές. Όλα τα κείμενα UI μέσω `tr("Ελληνικά","English")` (βλ. §7).
3. Τοπικοί έλεγχοι αν υπάρχει Android SDK: `./gradlew assembleDebug testDebugUnitTest lintDebug`
   και πάντα `bash verification/run-core-checks.sh`.
   Χωρίς SDK: το **CI είναι ο compiler** — κάνε push και παρακολούθησε το run.
4. **Commit:** το μήνυμα γίνεται «Τι νέο» στη συσκευή → γράψ' το στα **ελληνικά**, για τον χρήστη:
   ```
   Σύντομος τίτλος αλλαγής

   - τι άλλαξε 1
   - τι άλλαξε 2
   ```
   Μισοτελειωμένη δουλειά → πρόσθεσε `[no release]`.
5. `git push origin main`
6. **Παρακολούθηση** (με `gh`):
   ```bash
   R=$(gh api repos/nikkpap/AutoTuneRadio/actions/runs --jq '.workflow_runs[0].id')
   gh api repos/nikkpap/AutoTuneRadio/actions/runs/$R --jq '.status+" "+(.conclusion//"")'
   gh api repos/nikkpap/AutoTuneRadio/releases/latest --jq '.tag_name'
   ```
   Αποτυχία → logs: `gh api repos/nikkpap/AutoTuneRadio/actions/jobs/<job_id>/logs`
   (ψάξε `e: ` για Kotlin, `Error:` / `Lint found errors` για lint), διόρθωσε, νέο push.
   Το lint έχει `abortOnError = true` — λάθη lint σταματούν την έκδοση (σωστά).
7. Αναφορά στον χρήστη: ποια έκδοση βγήκε (`v0.23.X`) και τι αλλάζει για αυτόν.

---

## 6. OTA — τι πρέπει να ισχύει για να δουλέψει

- Release **δημοσιευμένο** (όχι draft / prerelease) και `latest`.
- Asset `AutoTuneRadio-v<έκδοση>.apk` (ο CI το ανεβάζει· το GitHub δίνει αυτόματα `digest: sha256:…`).
- Tag `v<έκδοση>` μεγαλύτερο από το εγκατεστημένο `versionName`.
- Ίδια υπογραφή (§3).
- Στη συσκευή: την πρώτη φορά «Εγκατάσταση άγνωστων εφαρμογών» για το AutoTune Radio
  (η εφαρμογή ανοίγει τη σελίδα). Android 12+: σιωπηλή εγκατάσταση μετά την πρώτη αυτο-ενημέρωση
  (`setRequireUserAction(USER_ACTION_NOT_REQUIRED)`)· παλιότερα Android: ένα «Ενημέρωση».
- Ρυθμίσεις → Σχετικά → «Αυτόματες ενημερώσεις» (on/off) και «Έλεγχος για ενημερώσεις».
- Σφάλμα `STATUS_FAILURE_CONFLICT / INCOMPATIBLE` = διαφορετικό κλειδί → έλεγξε το secret.

**Δοκιμή υπογραφής ενός Release (Linux):**
```bash
curl -sSL -o rel.apk https://github.com/nikkpap/AutoTuneRadio/releases/latest/download/AutoTuneRadio-v<έκδοση>.apk
apksigner verify --print-certs rel.apk | grep SHA-256   # πρέπει 79ac33…5dea
```

---

## 7. Συμβάσεις κώδικα

- **Γλώσσα UI:** κάθε κείμενο `tr("ελληνικά","english")` (`I18n.kt`). Στη Java: `Tr.t(...)`.
- **Θέματα:** χρώματα μόνο από `MaterialTheme.colorScheme` / `LocalLook` (`Theme.kt`), όχι σκληρά hex στο UI.
- **Ρυθμίσεις:** `Store` στο `AppCore.kt` (SharedPreferences `"radio"`). Η αλλαγή κάνει recompose μέσω `revision`.
- **Service:** `RadioService.kt` (MediaBrowserService για Android Auto). Μην κάνεις `stopSelf()` πριν το
  `startForeground()` σε εντολές που ξεκίνησαν με `startForegroundService`.
- **Σταθμοί:** `Station(id,name,url,logo,group)`. Εισαγωγή TXT (`όνομα,url,logo,ομάδα`), M3U (`tvg-logo`, `group-title`), PLS.
- **Tests:** `app/src/test/.../PlaylistTest.kt` — πρόσθεσε test για parsing/λογική που αλλάζεις.
- **Εικονίδιο:** `mipmap-*/ic_launcher_{foreground,background,monochrome}.png` + `mipmap-anydpi-v26/*.xml`, και `drawable-nodpi/app_logo.png` (512px).
- Νέες εξαρτήσεις: μόνο από Google Maven / Maven Central, με σταθερή έκδοση.

---

## 8. Γρήγορη αντιμετώπιση

| Σύμπτωμα | Αιτία / λύση |
|---|---|
| Κόκκινο ✗ στο Actions | δες logs (§5.6), διόρθωσε, push |
| Πράσινο αλλά χωρίς Release | λείπει secret, ή `[no release]`, ή pull request |
| `push rejected (fetch first)` | `git fetch origin main && git rebase origin/main && git push` |
| Η συσκευή δεν βλέπει την ενημέρωση | είναι η έκδοση `latest`; υπάρχει `.apk` asset; «Αυτόματες ενημερώσεις» ανοιχτό; |
| «Η εφαρμογή δεν εγκαταστάθηκε» | διαφορετική υπογραφή (τοπικό build χωρίς keystore ή λάθος secret) |
| Δεν εμφανίζεται στο Android Auto | Android Auto → Ρυθμίσεις προγραμματιστή → «Άγνωστες πηγές» + «Προσαρμογή εκκινητή» |

---

## 9. Για άλλο Android project του ιδίου ιδιοκτήτη (ίδιο μοτίβο)

Για να στηθεί το ίδιο σύστημα σε νέο app:
1. Αντέγραψε `.github/workflows/android.yml`, `Updater.kt`, `UpdateScreen.kt`, το `.gitignore`.
2. Στο `app/build.gradle.kts`: `appVersionBase` + `BUILD_NUMBER` versioning, `signingConfigs.debug` από αρχείο,
   `buildConfigField("String","UPDATE_REPO","\"<owner>/<repo>\"")`.
3. Manifest: `REQUEST_INSTALL_PACKAGES`, `<receiver android:name=".UpdateReceiver" android:exported="false"/>`,
   `POST_NOTIFICATIONS`, και εξάρτηση `androidx.work:work-runtime-ktx`.
4. `Application.onCreate` → `Updater.schedule(this)`· `MainActivity.onResume` → `Updater.foreground=true; Updater.autoCheck(this)`.
5. Secret `SIGNING_KEYSTORE_BASE64` με το κλειδί **που υπέγραψε την ήδη εγκατεστημένη έκδοση**.
6. Πρώτη φορά: εγκατάσταση με το χέρι της πρώτης έκδοσης που περιέχει τον Updater· μετά όλα OTA.
