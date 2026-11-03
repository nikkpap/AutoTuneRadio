# AutoTune Radio 0.18 — Android build, 10/10/2026

## Αλλαγή

Αφαιρέθηκε το ξεχωριστό κουμπί «Εισαγωγή αρχείου .ics» από Ρυθμίσεις →
Ημερολόγιο. Το «Αρχείο ICS» ανοίγει τον ίδιο επιλογέα για εισαγωγή ή
αντικατάσταση, ακόμη και όταν υπάρχει ήδη αποθηκευμένο αρχείο.
Η ένδειξη «Εισαγωγή…» εμφανίζεται μέσα σε αυτό το κουμπί όσο διαβάζεται
το αρχείο. Ο υπάρχων χειρισμός ακύρωσης/σφαλμάτων διατηρήθηκε.

Άλλαξε μόνο το CalendarScreen.kt ως λειτουργικός κώδικας. Το versionCode
αυξήθηκε από 17 σε 18, versionName 0.18.0, ώστε το APK να μπορεί να
αναβαθμίσει το προηγούμενο 0.17.0. Διατηρήθηκε το ίδιο application ID
και το ίδιο app/autotune-debug.keystore. Το κλειδί είναι μόνο για δοκιμές.

## Επαλήθευση

| Έλεγχος | Αποτέλεσμα |
|---|---|
| assembleDebug / Kotlin / Java / Compose | Επιτυχής |
| Playlist JUnit | 9 επιτυχείς, 0 αποτυχίες |
| Υπενθυμίσεις και log checks | 24 επιτυχείς |
| ICS checks | 20 επιτυχείς |
| Android Lint | 0 errors, 13 warnings |
| APK Signature Scheme v2 | Έγκυρη υπογραφή |
| Υπογραφή προηγούμενου APK 0.17 | Ίδιο πιστοποιητικό SHA-256 |
| zipalign, 4-byte / 16 KB native alignment | Επιτυχής |
| Label / package / έκδοση APK | Επαληθευμένα |
| MatchMate project και παραδοτέα | 1437 αρχεία ίδια, 0 αλλαγές |

Τα warnings προϋπήρχαν: SharedPreferences, target API, backup rules,
static references, launcher icon και Kotlin nullable/deprecated actions.

## APK

- Label: AutoTune Radio
- Application ID: gr.radiorise.player
- versionCode: 18 / versionName: 0.18.0
- minSdk: 28 / targetSdk: 34 / compileSdk: 35
- ABIs: arm64-v8a, armeabi-v7a, x86, x86_64
- Μέγεθος: 33,035,440 bytes
- SHA-256: 38dd0933d429d64883c7427899c0b2412f3646fb23875edab543d9f56129fd41
- Πιστοποιητικό SHA-256: 79ac3356512a37bda2273824bc1c3759446199e25e4e6443185f3d1c2d6c5dea

## Επανάληψη

JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, Android SDK 35.

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug -Pkotlin.incremental=false -Pkotlin.compiler.execution.strategy=in-process
bash verification/run-core-checks.sh
```

Το ZIP περιέχει το project και το ίδιο δοκιμαστικό κλειδί υπογραφής,
χωρίς build outputs, caches, SDK ή local.properties.

## Όρια ελέγχου

Δεν έγινε εγκατάσταση, οπτικός έλεγχος Compose ή δοκιμή document picker
σε συσκευή/emulator. Δεν έγιναν δοκιμές hardware, playback, alarms/Doze,
EQ ή boot autostart. Το προϋπάρχον ζήτημα boot autostart παραμένει
μη επιβεβαιωμένο ως επιλυμένο. Οι οδηγίες device verification διατηρούνται
στο verification/VERIFICATION.md. Το MatchMate δεν τροποποιήθηκε.
