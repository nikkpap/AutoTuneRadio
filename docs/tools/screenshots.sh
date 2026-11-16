#!/usr/bin/env bash
# Takes README screenshots on a running emulator (called by .github/workflows/screenshots.yml).
set -u
PKG=gr.radiorise.player
OUT=docs/screenshots
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$OUT"

adb wait-for-device
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 0
adb shell wm size 1280x800
adb shell wm density 160
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
adb shell cmd uimode night yes
adb install -r -g "$APK"
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS 2>/dev/null || true
adb shell cmd appops set $PKG REQUEST_INSTALL_PACKAGES deny 2>/dev/null || true

COMMON="autoUpdate=false askedNotifications=true appAuto=false boot=false calendarSaver=false calendarNotifications=false saverPhotos=false showBars=true groupsOn=true debugOn=false"

prefs() {   # prefs key=value ... → stop app, write prefs
  adb shell am force-stop $PKG
  python3 docs/tools/make_prefs.py AutoTuneRadio-stations.txt /tmp/radio.xml $COMMON "$@"
  adb push /tmp/radio.xml /data/local/tmp/radio.xml >/dev/null
  adb shell chmod 644 /data/local/tmp/radio.xml
  adb shell "run-as $PKG mkdir -p shared_prefs"
  adb shell "cat /data/local/tmp/radio.xml | run-as $PKG sh -c 'cat > shared_prefs/radio.xml'"
  adb shell "run-as $PKG ls -la shared_prefs" || true
}
launch() { adb shell am start -W -n $PKG/.MainActivity "$@" >/dev/null; sleep 4; }
play()   { adb shell am start-foreground-service -n $PKG/.RadioService -a PLAY --es id "$1" >/dev/null; sleep 14; }
shot()   { adb exec-out screencap -p > "$OUT/$1.png"; echo "saved $1"; }

# 1. Player — Ocean theme, Greek, playing
prefs lang=el theme=ocean activeGroup= saverOn=false last=s2
launch; play s2; shot player-ocean

# 2. Group selected (Chill)
prefs lang=el theme=ocean activeGroup=chill saverOn=false last=s2
launch; play s2; shot player-groups

# 3. Sunset theme
prefs lang=el theme=sunset activeGroup= saverOn=false last=s12
launch; play s12; shot theme-sunset

# 4. Daylight (light) theme
prefs lang=el theme=daylight activeGroup= saverOn=false last=s19
launch; play s19; shot theme-daylight

# 5. Royal purple theme, English UI
prefs lang=en theme=royal activeGroup= saverOn=false last=s20
launch; play s20; shot english-royal

# 6. Settings → Display (themes)
prefs lang=el theme=ocean saverOn=false last=s2
launch -a OPEN_SETTINGS --ei page 2; sleep 2; shot settings-themes

# 7. Settings → Stations (groups)
launch -a OPEN_SETTINGS --ei page 3; sleep 2; shot settings-stations

# 8. Settings → About (language + updates)
launch -a OPEN_SETTINGS --ei page 6; sleep 2; shot settings-about

# 9. Screensaver (clock) after 30 s idle
prefs lang=el theme=ocean saverOn=true saverIdle=30 last=s2
launch; play s2; sleep 26; shot screensaver

adb shell am force-stop $PKG
ls -la "$OUT"
