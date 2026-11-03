# AutoTune Radio

Internet ραδιόφωνο για οθόνες αυτοκινήτου (Android 9+), με Android Auto, equalizer, screensaver, ημερολόγιο,
ελληνικά / English και δυναμικά θέματα. Package: `gr.radiorise.player`.

**Κατέβασμα:** [τελευταία έκδοση (Releases)](https://github.com/nikkpap/AutoTuneRadio/releases/latest) → αρχείο `AutoTuneRadio-v….apk`.

---

## Νέα έκδοση = push στο GitHub (όχι build στον υπολογιστή)

Κάνεις push στο GitHub, εκείνο χτίζει το APK μόνο του, και οι συσκευές ενημερώνονται αυτόματα.

### Μία φορά μόνο: άνοιξε το project από το GitHub
1. Android Studio → **File → New → Project from Version Control…**
2. URL: `https://github.com/nikkpap/AutoTuneRadio` → **Clone**.
   - Αν ο φάκελος υπάρχει ήδη (π.χ. από το ZIP), άλλαξε το όνομα σε `…\AutoTuneRadio-git`.
   - Αν λείπει το Git, πάτα **Download and Install** στο ίδιο παράθυρο και ξαναπάτα Clone.
3. «Trust and open project?» → **Trust Project**. Περίμενε το Gradle Sync (1–3 λεπτά την πρώτη φορά).
4. Σύνδεση για Push: **File → Settings → Version Control → GitHub → + → Log In via GitHub…** → Authorize (nikkpap) → OK.
5. Για να τρέχει και τοπικά με το ίδιο κλειδί: βάλε το `autotune-debug.keystore` μέσα στον φάκελο `app/`
   (δεν ανεβαίνει ποτέ στο GitHub).

### Κάθε φορά που θες νέα έκδοση
1. Κάνε τις αλλαγές σου (αν θες, δοκίμασε με **Run ▶**).
2. **Ctrl+K** (Commit) → γράψε τι άλλαξες, π.χ. «Διόρθωση buffer». Αυτό το κείμενο φαίνεται στη συσκευή ως «τι νέο».
3. **Ctrl+Shift+K** → **Push**.
4. Τέλος. Μέσα σε 3–5 λεπτά:
   - το GitHub χτίζει το `AutoTuneRadio-v0.23.X.apk` και βγάζει νέο Release
     ([Actions](https://github.com/nikkpap/AutoTuneRadio/actions): πράσινο ✓ = έτοιμο),
   - οι συσκευές το βρίσκουν στο επόμενο άνοιγμα της εφαρμογής ή μέσα σε 6 ώρες, το κατεβάζουν και το
     **εγκαθιστούν μόνες τους — ποτέ όσο παίζει το ραδιόφωνο** (περιμένουν να σταματήσει).

Ο αριθμός έκδοσης ανεβαίνει αυτόματα (0.23.3, 0.23.4…).

### Χρήσιμα
- **Νέο APK χωρίς αλλαγές:** GitHub → Actions → **Build & Release** → **Run workflow**.
- **Push χωρίς νέα έκδοση** (μισοτελειωμένη δουλειά): γράψε `[no release]` στο μήνυμα του commit.
- **Πέρασμα σε 0.24.x:** στο `gradle.properties` άλλαξε `appVersionBase=0.23` σε `0.24`.
- **Κόκκινο ✗ στο Actions:** κάτι δεν μεταγλωττίζεται ή αποτυγχάνει κάποιο test. Δεν βγαίνει έκδοση,
  άρα οι συσκευές δεν επηρεάζονται. Άνοιξέ το και στείλε το σφάλμα.
- **Απλώς ένα APK στο χέρι:** Build → Build App Bundle(s) / APK(s) → Build APK(s). Αυτό δεν ενημερώνει τις συσκευές.
- **Στη συσκευή:** Ρυθμίσεις → Σχετικά → *Αυτόματες ενημερώσεις* (on/off) και *Έλεγχος για ενημερώσεις*.
  Την πρώτη φορά το Android ζητά «Εγκατάσταση άγνωστων εφαρμογών» — η εφαρμογή σε πάει εκεί.
  Σε Android 12+ μετά την πρώτη ενημέρωση δεν ζητά καν επιβεβαίωση· σε παλιότερα πατάς μία φορά «Ενημέρωση».

### Κλειδί υπογραφής (έχει ήδη γίνει)
Το κλειδί είναι αποθηκευμένο ως GitHub Secret `SIGNING_KEYSTORE_BASE64` (Settings → Secrets and variables → Actions).
Μην το ανεβάσεις ποτέ στο repo. Αν χαθεί, οι νέες εκδόσεις δεν θα μπαίνουν πάνω από τις παλιές.

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
