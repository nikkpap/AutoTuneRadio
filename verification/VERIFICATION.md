# AutoTune Radio 0.18 — Current build verification

Android build completed on 10/10/2026: assembleDebug, 9 Playlist JUnit tests,
44 existing Java checks, lintDebug (0 errors, 13 warnings), APK v2 signature and zipalign passed.
Removed the duplicate ICS import button. The remaining "Αρχείο ICS" button opens
the document picker for import and replacement. Version code is 18; the signing
certificate matches the delivered v0.17 APK. Device UI verification remains pending.
See [APK_BUILD_VERIFICATION.md](../APK_BUILD_VERIFICATION.md) for changes and limitations.
Device/emulator checks below remain pending. The older download blocker was
resolved for this delivery by using an isolated build toolchain.

## Historical verification notes

# AutoTune Radio 0.16 — Verification

| Check | Result |
|---|---|
| Compile production ReminderTiming.java + LogRedactor.java with Java 17 | Passed |
| Execute 24 reminder/log checks | Passed |
| XML parse, manifest components/permissions, settings integration, source delimiter checks | Passed |
| Archive integrity | Passed at packaging |
| Android assembleDebug, testDebugUnitTest and lintDebug | Blocked before execution: Gradle distribution unavailable (UnknownHostException: services.gradle.org) |
| Existing Kotlin PlaylistTest suite | Not executed due to same build blocker |
| Compose rendering, Calendar Provider, alarms, notifications, EQ, boot, steering, calls | Device/emulator verification required |

The 24 executed checks cover 30+5 reminders together, deadline ordering, zero-minute reminder, delivered/dismissed deduplication, independent recurring occurrences and calendar IDs, duplicate candidates, bounded catchup, ended meetings, snooze timing/dedup, maximum alarm count/48-hour horizon, UTC timing over spring/autumn DST, invalid offsets, URI credential masking, content/file/mail redaction, preserved diagnostic lines and log-line limits. They do not prove Android framework behavior.

Run core checks (Java 17): `bash verification/run-core-checks.sh`.
Run full Android verification after Gradle/SDK setup: `gradlew.bat assembleDebug testDebugUnitTest lintDebug` on Windows, or `./gradlew assembleDebug testDebugUnitTest lintDebug` on Linux.

## Required device checks

1. Install/open, grant calendar access, choose a real local calendar. Deny once and then grant from Android settings. Verify empty provider/no calendar and revoked permission.
2. Enable saver/notifications. Create a meeting 35 minutes ahead in that calendar and select 30+5. Verify both notifications and no duplicate after refresh/reopen. Test repeating meetings and timezone changes.
3. Snooze the first notification, then Cancel another occurrence. Verify snooze survives app process restart; Cancel prevents later thresholds only for that occurrence and does not delete the event.
4. Move/delete/decline a meeting and refresh. Verify old alarms do not notify. Turn notifications Off and verify pending alarms and existing meeting notifications are canceled.
5. Grant/deny exact-alarm permission and notification permission/channel. Test normal sleep/Doze, reboot and app update. Head-unit power/wake behavior needs a real test; force-stop blocks scheduled work until reopening.
6. Clock/photos saver with folder permission persisted across reboot. Verify agenda, touch/back dismissal, no interruption to radio, deleted/empty folder fallback and updated meetings.
7. Playback start/last/fixed station, buffer changes, incoming call pause/resume, steering next/previous inversion, active-station autoscroll, reorder persistence, manual logo URLs, TXT/M3U/PLS imports/exports.
8. EQ Bass/custom/Flat/Off while playing, across station and buffer/player changes. Enable Debug before reproducing and inspect Downloads/AutoTuneRadio/Logs. Actual audible EQ effect depends on the device and remains unconfirmed.
9. Debug On/Off, Downloads write failure, Android 9 storage permission grant/deny, file/folder picker unavailable, crash log best effort. Debug files accumulate until manually removed.

The previously reported car boot-autostart problem is not verified as solved. This release adds diagnostics and playback lifecycle fixes; no claim of reliable boot playback on that unit is made.

## ICS 0.16
20 additional Java checks executed: UTC, stable UID identity, folded text, escaping, ignoring nested VALARM, DST local-hour preservation, COUNT/EXDATE/RDATE, weekly multi-day recurrence, monthly invalid-date skipping, moved/canceled instances, all-day exclusion, floating time, invalid/custom timezone, unsupported rules, invalid interval/date, malformed file, inclusive UNTIL and duplicate SEQUENCE revisions. Total: 44 executed Java checks.

Device checks still needed: import via document picker without READ_CALENDAR; restart then display/notify from saved ICS; switch device calendar ↔ ICS; replace with changed/removed events and confirm old alarms cancel; reject invalid file without losing old import; deny notification permission; verify Snooze/Cancel on imported meetings. Only one imported ICS is stored; replacing it is explicit in the UI.
