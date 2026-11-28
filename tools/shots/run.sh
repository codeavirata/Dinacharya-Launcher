#!/usr/bin/env bash
# Runs the picture tour on the emulator that is already up (the GitHub workflow "Screenshots" starts it), then copies the pictures,
# the tour's log and Android's log into ./shots-result. It never fails the job: what happened is written in shots-result/status.txt.
#
#   APK  the shots build   (default app/build/outputs/apk/shots/app-shots.apk)
#   OUT  where to copy to  (default shots-result)
#   WAIT how long to wait for the tour, in seconds (default 1800)
set -u
APK="${APK:-app/build/outputs/apk/shots/app-shots.apk}"
OUT="${OUT:-shots-result}"
WAIT="${WAIT:-1800}"
PKG=app.dinacharya.launcher.shots
HOME_DIR="/sdcard/Android/data/$PKG/files/shots"

rm -rf "$OUT"
mkdir -p "$OUT"
status() { echo "$*" | tee -a "$OUT/status.txt"; }

adb wait-for-device
# wait for the system to finish starting
for i in $(seq 1 90); do
  [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ] && break
  sleep 2
done
adb shell input keyevent 82 || true            # wake and unlock
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb shell settings put system screen_off_timeout 1800000 || true
adb shell svc power stayon true || true

# A fixed moment, so "Now" is the same slot in every picture: Friday 27 November 2026, 10:42 in India (05:12 UTC).
adb shell settings put global auto_time 0 || true
adb shell settings put global auto_time_zone 0 || true
adb root >/dev/null 2>&1 || true
adb wait-for-device
adb shell "cmd alarm set-timezone Asia/Kolkata" >/dev/null 2>&1 || adb shell "service call alarm 3 s16 Asia/Kolkata" >/dev/null 2>&1 || adb shell setprop persist.sys.timezone Asia/Kolkata || true
sleep 1
EPOCH=$(date -u -d '2026-11-27 05:12:00' +%s)
adb shell "cmd alarm set-time ${EPOCH}000" >/dev/null 2>&1 || adb shell "date -u 112705122026.00" >/dev/null 2>&1 || true
sleep 1

status "clock now: $(adb shell date | tr -d '\r')  zone: $(adb shell getprop persist.sys.timezone | tr -d '\r')  root: $(adb shell id -u | tr -d '\r')"
status "device: $(adb shell getprop ro.product.model | tr -d '\r') Android $(adb shell getprop ro.build.version.release | tr -d '\r') (API $(adb shell getprop ro.build.version.sdk | tr -d '\r')), $(adb shell wm size | tr -d '\r' | tail -1), $(adb shell wm density | tr -d '\r' | tail -1)"

adb uninstall "$PKG" >/dev/null 2>&1 || true
if ! adb install -r "$APK"; then
  status "RESULT: install-failed"
  exit 0
fi

adb logcat -c || true
adb shell am start -n "$PKG/app.dinacharya.wallpaper.ShotActivity" --ez auto true
status "tour started $(date -u +%H:%M:%S)"

result=timeout
dead=0
waited=0
while [ "$waited" -lt "$WAIT" ]; do
  sleep 10
  waited=$((waited + 10))
  if adb shell "test -f $HOME_DIR/DONE && echo yes" | tr -d '\r' | grep -q yes; then
    result=done
    break
  fi
  if [ -z "$(adb shell pidof "$PKG" | tr -d '\r')" ]; then
    dead=$((dead + 1))
    if [ "$dead" -ge 3 ]; then
      result=crashed
      break
    fi
  else
    dead=0
  fi
done
status "tour ended: $result after ${waited}s"

adb exec-out screencap -p > "$OUT/real-screen-at-end.png" 2>/dev/null || true
adb logcat -d -v time > "$OUT/logcat-full.txt" 2>/dev/null || true
grep -E "DinaShots|AndroidRuntime|FATAL|DinaCrash|CrashLog" "$OUT/logcat-full.txt" > "$OUT/logcat.txt" || true
adb pull "$HOME_DIR/." "$OUT/" >/dev/null 2>&1 || status "pull from $HOME_DIR failed"
count=$(find "$OUT" -name '*.png' ! -name 'real-screen*' | wc -l)
status "pictures pulled: $count"
status "RESULT: $result"
exit 0
