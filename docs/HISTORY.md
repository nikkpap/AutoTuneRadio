# AutoTune Radio v0.23

Ελληνικά / English, δυναμικά θέματα (Material You, χρώματα σταθμού, αυτόματο ημέρα/νύχτα), χρονοδιακόπτης ύπνου, «Τι έπαιξε», χαμήλωμα για πλοήγηση, αυτόματη επανασύνδεση δικτύου, υποστήριξη .pls/.m3u σταθμών και διορθώσεις. Δες το CHANGELOG_0.23.md.

# AutoTune Radio v0.22

Player shortcut spacing (Home, Phone and optional apps) increased from 4 dp to 12 dp.

# AutoTune Radio v0.21

New: Settings → Display → App buttons. Choose the Phone app and up to two optional shortcuts using the searchable installed-app picker. See SHORTCUTS.md for details.

# v0.20 — Home & Phone

Two 48 dp shortcuts above the player logo: system Home and default dialer (ACTION_DIAL). Opening the dialer does not place a call or request CALL_PHONE permission. Playback continues when leaving the player; existing audio-focus handling applies when a call begins. Unavailable activities show a message instead of crashing.

# Android Auto — v0.19

Προστέθηκαν λίστα σταθμών, χειρισμός αναπαραγωγής, αναζήτηση σταθμού μέσω media search και λογότυπα για Android Auto. Δες το ANDROID_AUTO.md για εγκατάσταση και δοκιμές στη συσκευή.

# AutoTune Radio 0.18 — Android Studio source project

## Έκδοση 0.18 — Ένα κουμπί ICS
Ρυθμίσεις → Ημερολόγιο → «Αρχείο ICS» ανοίγει τον επιλογέα αρχείου για εισαγωγή ή αντικατάσταση του αποθηκευμένου ICS. Αφαιρέθηκε το ξεχωριστό κουμπί «Εισαγωγή αρχείου .ics». Κατά την εισαγωγή το ίδιο κουμπί εμφανίζει «Εισαγωγή…» και οι επιλογές πηγής απενεργοποιούνται προσωρινά. Ακύρωση ή αποτυχία διατηρεί την προηγούμενη πηγή και το αποθηκευμένο αρχείο. Ίδιο application ID και ίδιο κλειδί υπογραφής με την έκδοση 0.17 για αναβάθμιση.


## Επιτυχές Android build 0.17 — 10/10/2026 (ιστορικό)
Το APK 0.17.0 χτίστηκε και ελέγχθηκε. Πέρασαν 9 JUnit δοκιμές playlist και
44 Java checks για ICS/υπενθυμίσεις/logs. Διορθώθηκε η πρόσβαση στο ύψος
του screensaver και διατηρήθηκε το debug κλειδί στο project για επόμενα builds.
Το `autotune-debug.keystore` είναι μόνο για δοκιμές, όχι παραγωγή.
Δεν έγινε δοκιμή σε συσκευή. Αναλυτικά: [APK_BUILD_VERIFICATION.md](APK_BUILD_VERIFICATION.md).
Οι παλαιότερες σημειώσεις «δεν έγινε Android build» παρακάτω αφορούν προηγούμενες παραδόσεις.


## Έκδοση 0.17
Η επαναφορά του equalizer επαναφέρει τις αρχικές τιμές του επιλεγμένου preset (Rock, Pop, Bass, Ομιλία ή Flat). Το preset βάσης διατηρείται όταν αλλάζουν οι μπάντες χειροκίνητα και μετά από επανεκκίνηση. Παλιές Custom ρυθμίσεις χωρίς αποθηκευμένο preset βάσης χρησιμοποιούν Flat.
Στο ημερολόγιο αφαιρέθηκε το διπλό κουμπί επιλογής συσκευής· ο επιλογέας ανοίγει από το «Συσκευή».
Έλεγχος διαδρομών κώδικα· δεν έχει γίνει Android build ή δοκιμή σε συσκευή.

## Έκδοση 0.16 — Αρχείο ICS
Ρυθμίσεις → Ημερολόγιο → Αρχείο ICS / Εισαγωγή αρχείου .ics. Μία εισαγόμενη πηγή ICS αποθηκεύεται ιδιωτικά στο app και μπορεί να εναλλάσσεται με το ημερολόγιο συσκευής. Υποστηρίζονται οι ίδιες εμφανίσεις στον screensaver και οι υπενθυμίσεις/Snooze/Cancel. Δεν απαιτείται READ_CALENDAR για την πηγή ICS και δεν γίνεται εγγραφή στο Android Calendar Provider.

Το αρχείο είναι snapshot: αλλαγές στο αρχικό αρχείο δεν συγχρονίζονται αυτόματα. Εισάγεις ξανά το ενημερωμένο ICS για αντικατάσταση. Η Ανάγνωση/Ανανέωση χρησιμοποιεί το αποθηκευμένο αντίγραφο. Πριν αντικατασταθεί το προηγούμενο αρχείο γίνεται έλεγχος parsing και expansion. Όριο 2 MB/5.000 VEVENT· η εγγραφή χρησιμοποιεί AtomicFile. Δεν χρειάζεται διατήρηση άδειας του εξωτερικού αρχείου μετά την αντιγραφή.

Υποστήριξη: UTF-8/folded lines, SUMMARY/LOCATION escapes, DTSTART/DTEND, ωριαίο DURATION, UTC, IANA TZID (π.χ. Europe/Athens), floating times στη ζώνη της συσκευής/X-WR-TIMEZONE, DAILY/WEEKLY/MONTHLY/simple YEARLY, INTERVAL/COUNT/UNTIL, συνηθισμένα BYDAY/BYMONTHDAY/BYMONTH/WKST, EXDATE/RDATE και μεμονωμένα RECURRENCE-ID overrides/cancellations. Highest SEQUENCE προτιμάται σε διπλά UID. Όπως στην πηγή συσκευής, ολοήμερα δεν εμφανίζονται. Τα embedded VALARM αγνοούνται και ισχύουν οι χρόνοι που επιλέχθηκαν στο app.

Δεν είναι πλήρης υλοποίηση όλων των παραλλαγών RFC 5545. Άγνωστο/custom TZID (π.χ. μη αναγνωρισμένο Windows timezone), BYSETPOS, EXRULE, RDATE PERIOD, RECURRENCE-ID RANGE, υποημερήσιο RRULE, YEARLY με BYDAY/BYMONTHDAY και nominal day/week durations απορρίπτονται με μήνυμα αντί να προγραμματιστούν λανθασμένες ώρες. Σε αυτές τις περιπτώσεις εξήγαγε σε UTC ή ως μεμονωμένες συναντήσεις. Το παλιό εισαγόμενο αρχείο διατηρείται αν αποτύχει η νέα εισαγωγή.

Πέρασαν 20 εκτελεσμένα ICS checks και τα 24 προηγούμενα reminder/log checks. Δεν έχει γίνει Android build/device verification λόγω του καταγεγραμμένου Gradle download blocker.

## Έκδοση 0.15

Το ημερολόγιο είναι πλέον πραγματική λειτουργία, όχι placeholder. Ρυθμίσεις → Ημερολόγιο: παραχώρησε READ_CALENDAR, επίλεξε ένα πραγματικό ημερολόγιο της συσκευής, ενεργοποίησε εμφάνιση στον screensaver και/ή ειδοποιήσεις, επίλεξε π.χ. 30+5 λεπτά και χρόνο Snooze. Σε Android 13+ χρειάζεται άδεια ειδοποιήσεων. Σε Android 12+ υπάρχει κουμπί για άδεια ακριβών alarms· χωρίς αυτήν χρησιμοποιούνται inexact alarms που μπορεί να καθυστερήσουν.

Διαβάζονται CalendarContract.Instances, ώστε να εμφανίζονται και επαναλαμβανόμενες συναντήσεις με τις σωστές ημερομηνίες/ζώνες ώρας. Εμφανίζονται τρέχουσες/επόμενες συναντήσεις 7 ημερών, εκτός ολοήμερων, canceled και declined. Η πηγή είναι ο ήδη συγχρονισμένος Calendar Provider της συσκευής· το app δεν συνδέεται ανεξάρτητα στο Google/Microsoft cloud και δεν γράφει ή διαγράφει events.

Οι υπενθυμίσεις αποθηκεύονται ανά ημερολόγιο/event/occurrence/offset. Χρησιμοποιούνται AlarmManager exact/inexact, background WorkManager ανανέωση ανά 15 λεπτά και ContentObserver όσο τρέχει η διεργασία. Τα alarms περιορίζονται στα 128 πλησιέστερα εντός 48 ωρών και ανανεώνονται αυτόματα, για να αποφεύγεται το όριο alarms της συσκευής. Επιτρέπεται catchup έως 5 λεπτά κατά την ανανέωση, χωρίς burst παλαιών υπενθυμίσεων. Κάθε alarm ελέγχει ξανά την πραγματική συνάντηση πριν ειδοποιήσει. Snooze αντικαθιστά τις υπόλοιπες ειδοποιήσεις ενός occurrence με μία νέα· Cancel ακυρώνει μόνο αυτό το occurrence. Το swipe απλώς κλείνει την τρέχουσα ειδοποίηση. Δεν ειδοποιούνται συναντήσεις που έχουν ήδη τελειώσει.

Boot, update, αλλαγή ώρας/timezone και grant exact-alarm permission ζητούν επαναπρογραμματισμό. Denied/revoked READ_CALENDAR ή Off ακυρώνει τα σχετικά alarms/notifications. Αν λείπει Calendar Provider στη μονάδα αυτοκινήτου εμφανίζεται σχετικό μήνυμα. Οι περιορισμοί Android/κατασκευαστή και force-stop μπορούν να σταματήσουν alarms/work μέχρι να ξανανοίξει το app.

Επίσης: fallback στον τελευταίο/πρώτο διαθέσιμο σταθμό όταν λείπει ο επιλεγμένος εκκίνησης, ασφαλέστερα notification playback actions, αποσύνδεση listeners πριν από release/rebuild του player, scrolling στο station editor, χειρισμός έλλειψης file/folder picker σε head units και διακοπή περιοδικού debug writer όταν Debug Off.

**Έλεγχος:** 24 πραγματικά εκτελεσμένα Java checks υπενθυμίσεων/log redaction. Android assembleDebug/testDebugUnitTest/lintDebug μπλοκαρίστηκαν πριν αρχίσουν, επειδή δεν κατεβαίνει το Gradle από services.gradle.org. Καμία επιβεβαίωση APK/device. Δες verification/VERIFICATION.md για όρια και δοκιμές στη συσκευή. Οι παρακάτω σημειώσεις παλιότερων εκδόσεων είναι ιστορικές.

Νέο ανεξάρτητο app, εμπνευσμένο από τη λειτουργία custom URL stations του CustomRadioPlayer. Δεν περιλαμβάνει κώδικα ή γραφικά από το XAPK.

## Build
1. Άνοιξε τον φάκελο AutoTuneRadio στο Android Studio και κάνε Gradle Sync.
2. Gradle JDK: 17 ή 21. Εγκατάστησε Android SDK 35 όταν ζητηθεί.
3. Build > Build App Bundle(s) / APK(s) > Build APK(s), ή `gradlew.bat assembleDebug`.
4. APK: app/build/outputs/apk/debug/app-debug.apk.

## Χρήση
Πρόσθεσε σταθμό με όνομα και απευθείας HTTP/HTTPS stream URL. Εισαγωγή M3U, απλών M3U8, PLS και TXT, από τοπικό αρχείο ή URL. Προεπισκόπηση πριν την εισαγωγή, συγχώνευση χωρίς διπλά ίδια URL, επεξεργασία/διαγραφή, αναζήτηση, εξαγωγή M3U. HLS playlists προστίθενται ως ένας σταθμός από το URL stream και δεν εισάγονται ως πολλοί σταθμοί.

Ρυθμίσεις: ενεργοποίηση αναπαραγωγής μετά την εκκίνηση συσκευής, καθυστέρηση 0–300 δευτερόλεπτα, συγκεκριμένος ή τελευταίος σταθμός, επαναφορά μετά την κλήση. Δεν ανοίγει οθόνη πάνω από άλλες εφαρμογές: ξεκινά η αναπαραγωγή με ειδοποίηση. Η καθυστέρηση μετρά από το BOOT_COMPLETED μετά το ξεκλείδωμα, όχι από το πάτημα του power.

Μετά την εγκατάσταση άνοιξε το app τουλάχιστον μία φορά και επίλεξε σταθμό. Επίτρεψε ειδοποιήσεις. Σε συσκευές που διαθέτουν auto-start setting, ενεργοποίησέ το. Αν ο κατασκευαστής εμποδίζει τις υπηρεσίες, δοκίμασε χρήση μπαταρίας χωρίς περιορισμούς. Force-stop από τις ρυθμίσεις σταματά τα boot broadcasts μέχρι να ξανανοίξεις το app.

## Ήχος και κλήσεις
Audio focus του Android: παύση σε προσωρινή απώλεια (κλήση ή άλλη εφαρμογή), επανασύνδεση στον ίδιο live σταθμό όταν επιστρέφει. Δεν χρειάζεται πρόσβαση στο ιστορικό/αριθμούς τηλεφώνου. Αν το Android αναφέρει μόνιμη απώλεια focus, δεν γίνεται αυτόματη επαναφορά. Χειροκίνητη παύση ακυρώνει επιστροφή, pending boot timer και retries. Αποσύνδεση ακουστικών σταματά την αναπαραγωγή. Η παύση δεν αποθηκεύει το χαμένο περιεχόμενο live. Έως πέντε προσπάθειες επανασύνδεσης σε σφάλμα.

## Συμβατότητα και όρια
minSdk 28 (Android 9), compileSdk 35, targetSdk 34 για προσωπική εγκατάσταση εκτός Play Store. Το Android απαγορεύει media playback foreground services από BOOT_COMPLETED για apps που στοχεύουν API 35+. Αν αυξηθεί targetSdk, το boot flow πρέπει να αλλάξει σε notification/manual start ή υποστήριξη auto-launch από το περιβάλλον συσκευής. Αυτή η έκδοση δεν είναι έτοιμη για υποβολή στο Play Store.

Πρόκειται για αρχική έκδοση source project. Δεν έχει πραγματοποιηθεί Android build ούτε δοκιμή σε πραγματική συσκευή στο περιβάλλον δημιουργίας: δεν υπήρχε Android SDK. Απαιτείται δοκιμή reboot, εισερχόμενης/εξερχόμενης κλήσης, Bluetooth και κλειδωμένης οθόνης στη συσκευή προορισμού. Δεν υπόσχεται αυτόματο boot σε κάθε ROM. Η υπηρεσία μπορεί να τερματιστεί από το Android μετά από μακρά παύση/κλήση. Η ακριβής μορφή ιδιωτικού JSON backup του αρχικού app δεν έχει επαληθευτεί· δεν υποστηρίζεται μεταφορά αυτού του backup. Για υπάρχοντες σταθμούς χρησιμοποίησε M3U/PLS ή URLs.

Δεν περιλαμβάνονται έτοιμοι σταθμοί, διαφημίσεις ή analytics. HTTP streams επιτρέπονται για συμβατότητα παλιών σταθμών· προτίμησε HTTPS.

## Έλεγχος στη συσκευή
- Πρόσθεσε δύο σταθμούς και επαλήθευσε αναπαραγωγή/εναλλαγή.
- Εισήγαγε τη sample-stations.m3u: περιέχει παραδείγματα, όχι πραγματικά ενεργά streams. Εισήγαγέ την δύο φορές: τη δεύτερη δεν πρέπει να προστεθούν διπλά.
- Ενεργοποίησε boot, επίλεξε πραγματικό σταθμό, βάλε 10 sec, επανεκκίνησε και ξεκλείδωσε.
- Ενώ παίζει κάνε κλήση: πρέπει να διακοπεί και να επανασυνδεθεί μετά.
- Πάτησε Παύση στη διάρκεια διακοπής: δεν πρέπει να επανέλθει αυτόματα.
- Δοκίμασε διακοπή δικτύου, επαναφορά, αποσύνδεση ακουστικών.

## Νέο στην 0.3
Υποστήριξη του TXT με κεφαλίδα ####Stations#### και γραμμές όνομα,stream URL,logo URL. Αποθήκευση και εμφάνιση logos, fallback αρχικών σε αποτυχία εικόνας, εξαγωγή στην ίδια μορφή TXT (CSV quoting). Μετατροπή &amp; σε & για σωστά query parameters. Οι υπάρχουσες εγγραφές χωρίς logos παραμένουν συμβατές. Δεν έχει επαληθευτεί Android build/εκτέλεση της 0.3. Τα stream URLs δεν έχουν ελεγχθεί online.

## Νέο στην 0.3
Αυτόματη αναπαραγωγή στο νέο άνοιγμα εφαρμογής (ενεργή από προεπιλογή), με ίδια επιλογή συγκεκριμένου/τελευταίου σταθμού και delay. Δεν ξαναξεκινά stream που ήδη παίζει και δεν επανεκκινεί timer σε περιστροφή οθόνης. Ξεχωριστός διακόπτης για boot συσκευής. Next/previous μέσω MediaSession και κουμπιών ειδοποίησης, κυκλικά με σειρά λίστας (τελευταίος → πρώτος). Αυτό αφορά Bluetooth/hardware media controls· δεν προσθέτει Android Auto κατάλογο εφαρμογής. Απαιτείται πραγματική δοκιμή χειριστηρίου αυτοκινήτου.

## Νέο στην 0.4 — Car UI
Σταθερό split 50/50: player και λίστα με ανεξάρτητη κύλιση. Portrait πάνω/κάτω, landscape αριστερά/δεξιά. Μεγάλα controls προηγούμενο/play-pause/επόμενο, logo σταθμού, live metadata μέσω ICY και MediaMetadata, εμφανής ένδειξη όταν δεν παρέχεται τίτλος, διαθέσιμος buffered ήχος σε δευτερόλεπτα. Αλλαγή τραγουδιού μεταφέρεται και στο MediaSession/notification. Δεν είναι επίγειο FM RDS και δεν μαντεύει τίτλους σταθμών χωρίς metadata.

Ρυθμίσεις σε τρεις ενότητες με μεγαλύτερα κουμπιά: Εκκίνηση, Ήχος, Λίστες. Επιλογή σταθμού με modal αντί μεγάλης λίστας στην αρχική ρυθμίσεων. Delay presets και +/- ενός δευτερολέπτου. Buffer 1–30 sec (default 5), presets 2/5/10/20 sec. Χρησιμοποιείται ως threshold έναρξης και μετά από rebuffer, με min buffer max(15 sec, threshold), max buffer max(30 sec, 2 × threshold). Οι αλλαγές ισχύουν στην επόμενη έναρξη σταθμού και δεν διακόπτουν άμεσα ό,τι παίζει. Μεγάλο buffer αυξάνει χρόνο σύνδεσης και live latency. HLS streams και το διαθέσιμο live window ενδέχεται να περιορίζουν το αποτέλεσμα.

Νέο λογότυπο: generated raster mark σε navy/mint/lavender, κύμα ήχου + play + A. Ενσωματωμένο στο app/src/main/res/drawable/app_logo.png, στο launcher και στο κενό player. Δημιουργήθηκε με built-in image generation. Prompt: modern AutoTune Radio Android launcher icon, midnight navy background, mint sound-wave/play/A symbol, lavender accents, no text, central safe area.

Παραμένει source preview χωρίς επιβεβαιωμένο Android build ή real-device UI test. Απαιτούνται έλεγχοι portrait/landscape σε μικρή οθόνη, ICY stream με μεταβαλλόμενο τίτλο, stream χωρίς metadata, διαφορετικά buffers, κλήσεις και χειριστήριο αυτοκινήτου. Η διάταξη για οθόνη αυτοκινήτου δεν αποτελεί ενσωμάτωση Android Auto.

## Νέο στην 0.5
Ρυθμίσεις πάνω δεξιά, χωρίς κάτω navigation bar. Διαχείριση σταθμών (προσθήκη, εισαγωγή αρχείου/URL, εξαγωγές) μόνο μέσα στην ενότητα Σταθμοί των ρυθμίσεων. Αναζήτηση στην κορυφή λίστας με placeholder «Βρες σταθμό από τους Ν…». Αποθηκευμένη επιλογή πλευράς player από Οθόνη: αριστερά/δεξιά στο landscape, πάνω/κάτω στο portrait. Δεν έχει πραγματοποιηθεί Android build ή real-device layout test.

## Νέο στην 0.6
Αφαίρεση τίτλου από header και player. Compact πάνω μπάρα 48 dp, μόνο search/settings δεξιά. Η αναζήτηση μεταφέρθηκε σε dialog με σύντομο placeholder «Σταθμός…». Η κύρια λίστα δεν έχει πεδίο αναζήτησης. Όταν εφαρμόζεται φίλτρο εμφανίζεται μικρή ένδειξη και «Όλοι» για καθαρισμό. Μικρότερα εξωτερικά περιθώρια και απόσταση μεταξύ panes. Καμία αλλαγή στη συμπεριφορά boot: το αναφερθέν πρόβλημα auto-start παραμένει υπό διερεύνηση. Build και πραγματική εμφάνιση δεν έχουν ελεγχθεί.

## Νέο στην 0.7
Διόρθωση wrap των presets (30 sec έσπαγε σε χαρακτήρες). Ίσο πλάτος, σταθερό ύψος 56dp, μόνο αριθμός και μονάδα στον τίτλο. Ίδια διόρθωση στα buffer presets. Οι καρτέλες ρυθμίσεων έχουν φυσικό πλάτος και οριζόντια κύλιση, χωρίς αναδίπλωση λέξεων. Η φωτογραφία ελέγχθηκε· η νέα έκδοση δεν έχει γίνει build ή render σε πραγματική συσκευή.

## Νέο στην 0.8
Compact settings tabs 48dp με γραμματοσειρά 14sp, χωρίς ογκώδη κουμπιά. HorizontalPager: αλλαγή ενότητας με swipe ή πάτημα καρτέλας. Κάθε σελίδα κυλά κατακόρυφα ανεξάρτητα και καταλαμβάνει τον διαθέσιμο χώρο. Back του Android επιστρέφει από ρυθμίσεις στον player. Δεν έχει πραγματοποιηθεί build/real-device gesture test.

## Νέο στην 0.9
Ήχος → Αντιστροφή κουμπιών τιμονιού (default OFF), εφαρμόζεται άμεσα στα MediaSession next/previous callbacks. GUI και κουμπιά ειδοποίησης στέλνουν ξεχωριστές service εντολές και δεν αντιστρέφονται. Το Android δεν εγγυάται αναγνώριση τιμονιού έναντι ακουστικών/άλλων εξωτερικών media controllers· και αυτές οι εντολές επηρεάζονται.
Λίστα με remembered scroll state και αυτόματη εμφάνιση ενεργού σταθμού όταν αλλάζει. Αν νέα επιλογή βρίσκεται εκτός φίλτρου αναζήτησης, το φίλτρο καθαρίζεται ώστε να εμφανιστεί. Search/settings στην κάτω δεξιά γωνία του player, κάτω από τα controls. Χωρίς top app bar στην κύρια οθόνη (διατηρούνται τα system insets). Οι ρυθμίσεις έχουν ξεχωριστό Πίσω. Δεν έχει πραγματοποιηθεί Android build ή πραγματική δοκιμή τιμονιού/scroll/layout. Το προηγούμενο θέμα boot παραμένει ανεπίλυτο.

## Νέο στην 0.10
Παρατεταμένο πάτημα σε κάρτα σταθμού ανοίγει «Σειρά σταθμού» με θέση και Πάνω/Κάτω. Κάθε μετακίνηση αποθηκεύεται αμέσως στην πλήρη λίστα (ακόμη και όταν υπάρχει φίλτρο), χωρίς αλλαγή στα IDs ή στην τρέχουσα αναπαραγωγή. GUI και τιμόνι ακολουθούν τη νέα σειρά. Δεν είναι drag-and-drop: η μετακίνηση γίνεται με τα κουμπιά.
Προσθήκη/επεξεργασία σταθμού με χειροκίνητα stream URL και logo URL. Το logo είναι προαιρετικό και μπορεί να αφαιρεθεί αφήνοντας κενό πεδίο. Επεξεργασία σταθμού διατηρεί τη θέση του στη λίστα. Android build/συσκευή δεν έχουν επαληθευτεί.

## Έκδοση 0.11
Νέα καρτέλα Ρυθμίσεις → Σχετικά: λογότυπο, δυναμική έκδοση εφαρμογής, ALU DEV TEAM © 2026, Nikolaos K. Paridis (nikkpap), κουμπί email και σύντομες οδηγίες. Το email ανοίγει εξωτερική εφαρμογή και δεν αποστέλλεται αυτόματα. Δεν έχουν προστεθεί μη επιβεβαιωμένα links GitHub/Telegram.

## Έκδοση 0.12 — Screensaver και Equalizer
- Ρυθμίσεις → Screensaver: On/Off (προεπιλογή Off), ρολόι ή photo frame, αδράνεια 30/60/120/300 sec, αλλαγή εικόνας 5/15/30/60 sec, επιλογή φακέλου και προεπισκόπηση.
- Ενεργοποίηση αυτόματα μόνο στην κύρια οθόνη όσο το app είναι στο foreground. Δεν ενεργοποιείται πάνω σε ρυθμίσεις, dialogs ή picker. Touch/Back κλείνει χωρίς διακοπή ήχου. Το ρολόι μετακινείται ανά λεπτό. Η οθόνη μένει αναμμένη όσο εμφανίζεται ο screensaver.
- Φωτογραφίες απευθείας από επιλεγμένο φάκελο SAF με επίμονη πρόσβαση ανάγνωσης. Χωρίς αντιγραφή, χωρίς υποφακέλους. Απώλεια πρόσβασης/κενός φάκελος: ρολόι. Με νέο άνοιγμα επαναφορτώνεται ο φάκελος.
- Ρυθμίσεις → Ήχος: Equalizer On/Off (προεπιλογή Off), Flat/Bass/Ομιλία/Rock/Pop, χειροκίνητες μπάντες και Flat reset. Χρησιμοποιείται μόνο το audio session του app. Οι διαθέσιμες μπάντες/εύρος καθορίζονται από τη συσκευή, εμφανίζονται μετά την εκκίνηση stream. Αποθήκευση και άμεση εφαρμογή μέσω preferences listener. Επανασύνδεση σε αλλαγή audio session/player, release στο κλείσιμο.
- Αν η συσκευή δεν υποστηρίζει equalizer ή άλλη εφαρμογή έχει τον έλεγχο, εμφανίζεται σχετικό μήνυμα και η αναπαραγωγή συνεχίζεται.

Έλεγχος στη συσκευή: idle→screensaver→touch/back, background→επιστροφή, φάκελος μετά από reboot, κενός/διαγραμμένος φάκελος, αλλαγή σταθμού από τιμόνι μέσα στον screensaver, EQ presets/custom/off και αλλαγή buffer/player. Δεν έχει γίνει Android compilation ή πραγματική δοκιμή συσκευής σε αυτό το περιβάλλον. Το προηγούμενο αναφερόμενο πρόβλημα boot autostart δεν επιλύθηκε σε αυτή την έκδοση.

## Έκδοση 0.13 — Debug logs στα Downloads
Ρυθμίσεις → Σχετικά → Debug logs On/Off. Προεπιλογή Off, επιλογή αποθηκεύεται και ισχύει στην επόμενη εκκίνηση/boot. Τα `.log` γράφονται στο `Download/AutoTuneRadio/Logs`, με timestamp και νέο αρχείο ανά debug session/εκκίνηση, rotation περίπου 2 MB χωρίς διαγραφή παλαιών logs. Background writer, flush ανά 2 sec όταν υπάρχουν αλλαγές, κουμπί «Αποθήκευση logs τώρα», τελικό flush στο Off. Crash flush best effort έως 1.5 sec, όχι εγγυημένο σε process kill/power loss. Οι τελευταίες μη δημοσιευμένες γραμμές μπορεί να χαθούν σε απότομο κλείσιμο.

Καταγράφονται lifecycle app/service, boot attempts, εντολές player/τιμονιού, audio focus, playback state/errors/retries, αλλαγές settings, αποθήκευση/μετακίνηση/import/export σταθμών, folder permission/picker, φωτογραφίες/screensaver, version/device/network snapshot και uncaught crashes. Για EQ: audio session, έλεγχος effect, μπάντες/εύρος, preset, return code του setEnabled, πραγματικό enabled και πραγματικές τιμές μπαντών. Τα πλήρη URI αντικαθίστανται με `[URI]` και δεν καταγράφεται περιεχόμενο φωτογραφιών ή λιστών.

Άδειες: Android 10+ MediaStore.Downloads για τα logs του app, χωρίς broad storage permission. Android 9: WRITE_EXTERNAL_STORAGE με maxSdkVersion=28 και runtime request μόνο όταν ενεργοποιείται debug. Φωτογραφίες/εισαγωγές μέσω SAF επιλογής αρχείου/φακέλου· επίμονη read grant για τον φάκελο φωτογραφιών. Δεν χρειάζεται READ_MEDIA_IMAGES ή MANAGE_EXTERNAL_STORAGE για αυτόν τον τρόπο πρόσβασης. Υπάρχει ήδη MODIFY_AUDIO_SETTINGS για EQ.

Διάγνωση EQ: ενεργοποίησε Debug, ξεκίνα stream, Ήχος → Bass → On/Off, ξαναπήγαινε Σχετικά → Αποθήκευση logs τώρα και στείλε το νέο `.log`. Η κατάσταση EQ είναι πλέον πιο εμφανής. Προστέθηκε fallback attachment στο STATE_READY και επανάληψη attachment στην αλλαγή EQ όταν υπάρχει audio session αλλά λείπει effect. Η λειτουργία στον συγκεκριμένο head unit παραμένει ανεπιβεβαίωτη.

Manual checks: Android 9 grant/deny και επανενεργοποίηση· Android 10+ εμφάνιση log στα Downloads· On→events→save→Off· επανεκκίνηση με Debug On· EQ error/unsupported· αποτυχία πρόσβασης φωτογραφιών· boot attempts. Android build δεν είναι διαθέσιμο λόγω μη προσβάσιμης λήψης Gradle στο περιβάλλον. Το προηγούμενο boot autostart πρόβλημα δεν θεωρείται επιλυμένο.

## Έκδοση 0.14 — Placeholder ημερολογίου
Νέα καρτέλα Ρυθμίσεις → Ημερολόγιο. Αποθηκεύονται επιλογές εμφάνισης στον screensaver, ειδοποιήσεων, πολλαπλών χρόνων υπενθύμισης (0/5/10/15/30/60 λεπτά· προεπιλογή 30+5) και Snooze (5/10/15/30 λεπτά). Δείγματα εμφάνισης ειδοποίησης/screensaver και Snooze/Cancel. Cancel σχεδιάζεται να αφορά μόνο τις υπενθυμίσεις ενός occurrence, όχι διαγραφή event.

Πρόκειται ρητά για placeholder: δεν γίνεται ανάγνωση ή επιλογή πραγματικού device calendar, συγχρονισμός, εμφάνιση πραγματικών events στον screensaver ή scheduling notifications. Δεν ζητείται READ_CALENDAR σε αυτή την έκδοση γιατί δεν χρησιμοποιείται. Η πραγματική υλοποίηση θα χρειαστεί CalendarContract Calendars/Instances (με recurring events/timezones), READ_CALENDAR runtime grant, χειρισμό denied/revoked permission, προγραμματισμό και ανανέωση υπενθυμίσεων, dedup ανά occurrence και threshold, snooze/cancel PendingIntents, επαναφορά μετά reboot και περιορισμούς ειδοποιήσεων Android. Δεν απαιτείται WRITE_CALENDAR για μόνο ανάγνωση και υπενθυμίσεις.

Δεν έχει γίνει Android build ή δοκιμή συσκευής.
