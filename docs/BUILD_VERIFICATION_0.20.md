# v0.20 validation

Gradle assembleDebug, testDebugUnitTest and lintDebug completed successfully.
- 9 JUnit tests passed.
- 44 standalone calendar/reminder/log checks passed.
- Lint: 0 errors; 14 remaining issues (see report).
- APK signature verified and matches uploaded v0.18 APK.
- ZIP alignment checked with zipalign -c -p 4.
- Compiled manifest includes Android Auto metadata, browser service and media search.
- SHA256: `63dfb4c12e1d689ab2a77f4b28e860f3e4359bd1201834331cb77ebc1835f548`
- Signing certificate SHA256: `79ac3356512a37bda2273824bc1c3759446199e25e4e6443185f3d1c2d6c5dea`

No physical phone, car, emulator or Desktop Head Unit testing was performed.
Existing boot-autostart and audible EQ device-specific reports are not claimed resolved.
