#!/usr/bin/env bash
# Schermtest: installeert de debug-APK, zet het IP van de nep-ESP en maakt screenshots.
set -u
OUT=shots
mkdir -p $OUT
PKG=nl.julian.zztimer
APK=android/app/build/outputs/apk/debug/app-debug.apk
UI="python3 ui-test/ui.py"

shot() { sleep "${2:-1.5}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "screenshot $1"; }
scen() { echo "$1" > /tmp/esp_scenario.json; }
swipe() { adb shell input swipe 540 1750 540 750 450; sleep 1; }
swipe_top() { for i in 1 2 3 4; do adb shell input swipe 540 700 540 2000 200; done; sleep 0.8; }
launch() { adb shell am force-stop $PKG; adb shell am start -n $PKG/.MainActivity >/dev/null; }

adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb install -r -g "$APK"
adb shell wm size; adb shell wm density

# Voorkeuren: IP van de nep-ESP en klassen voor de vlaggen
cat > /tmp/zz_timer.xml <<'XML'
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name="host">10.0.2.2</string>
    <string name="flag_classes">Optimist, ILCA 7</string>
</map>
XML
adb push /tmp/zz_timer.xml /data/local/tmp/zz_timer.xml
adb shell am start -n $PKG/.MainActivity >/dev/null; sleep 3; adb shell am force-stop $PKG
adb shell run-as $PKG mkdir -p shared_prefs
adb shell run-as $PKG cp /data/local/tmp/zz_timer.xml shared_prefs/zz_timer.xml
adb shell run-as $PKG cat shared_prefs/zz_timer.xml

if $UI dumpok; then echo "uiautomator werkt"; else echo "uiautomator werkt NIET"; fi

run_set() {   # $1 = voorvoegsel
  local P=$1
  scen '{"running": true, "elapsed_s": 100, "proc_s": 300}'
  launch; sleep 0.5; adb exec-out screencap -p > "$OUT/${P}00_splash.png"
  shot ${P}01_timer 6
  swipe; shot ${P}02_timer_scroll1
  swipe; shot ${P}03_timer_scroll2
  swipe; shot ${P}03b_timer_scroll3
  swipe_top
  $UI tap "Vlaggen" 810 2225; shot ${P}04_flags 2
  swipe; shot ${P}05_flags_scroll1
  swipe; shot ${P}06_flags_scroll2
  swipe; shot ${P}07_flags_scroll3
  swipe; shot ${P}07b_flags_scroll4
  swipe_top
  $UI tap "Instellingen vlaggen en stem" 1006 190; shot ${P}08_flagsettings 2
  adb shell input keyevent BACK; sleep 1
  $UI tap "Timer" 270 2225; sleep 1
  $UI tap "Instellingen" 1006 190; shot ${P}09_pin 2
  adb shell input keyevent TAB; adb shell input text 1234; sleep 0.5
  shot ${P}09b_pin_typed 0.5
  $UI tap "Openen" 800 1400 || { adb shell input keyevent TAB; adb shell input keyevent TAB; adb shell input keyevent ENTER; }
  shot ${P}10_settings 2.5
  swipe; shot ${P}11_settings_scroll1
  swipe; shot ${P}12_settings_scroll2
  swipe; shot ${P}12b_settings_scroll3
  adb shell input keyevent BACK; sleep 1
  if $UI scrollto "Geplande start toevoegen"; then
    $UI tap "Geplande start toevoegen"; shot ${P}13_schedule_dialog 2
    adb shell input keyevent BACK; sleep 1
  fi
  swipe_top
  # Rust
  scen '{"running": false, "elapsed_s": 0, "proc_s": 300}'; shot ${P}14_timer_idle 2.5
  # 3 minuten, 30 s gelopen
  scen '{"running": true, "elapsed_s": 30, "proc_s": 180}'
  $UI tap "Vlaggen" 810 2225; shot ${P}15_flags_3min 2.5
  swipe; shot ${P}16_flags_3min_scroll
  swipe_top
  # Laatste seconden voor de start (5 min)
  scen '{"running": true, "elapsed_s": 293, "proc_s": 300}'; shot ${P}17_flags_soon 1.5
  $UI tap "Timer" 270 2225; shot ${P}18_timer_soon 1.5
}

adb shell cmd uimode night no
run_set L_
adb shell cmd uimode night yes
run_set D_
adb shell cmd uimode night no

# Grote tekst
adb shell settings put system font_scale 1.3
scen '{"running": true, "elapsed_s": 100, "proc_s": 300}'
launch; shot F_01_timer 6
swipe; shot F_02_timer_scroll1
swipe; shot F_03_timer_scroll2
swipe_top
$UI tap "Vlaggen" 810 2225; shot F_04_flags 2
swipe; shot F_05_flags_scroll1
adb shell settings put system font_scale 1.0

# Zonder verbinding
sudo pkill -f fake_esp.py || true
launch; shot X_01_timer_disconnected 9
$UI tap "Vlaggen" 810 2225; shot X_02_flags_disconnected 2
swipe; shot X_03_flags_disconnected_scroll

# Liggend
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1
sleep 2; shot X_04_landscape_flags 2
$UI tap "Timer" 1200 980; shot X_05_landscape_timer 2
adb shell settings put system user_rotation 0
ls -la $OUT
adb logcat -d -t 3000 > $OUT/logcat.txt 2>&1 || true
