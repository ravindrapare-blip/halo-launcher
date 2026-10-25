#!/usr/bin/env bash
# Halo — one-time TV setup over ADB (macOS / Linux). Usage: ./setup-tv.sh <TV-IP> [path/to/app-release.apk]
set -e
TV="$1"; APK="${2:-app-release.apk}"; PKG=com.halo.launcher
[ -z "$TV" ] && { echo "Usage: $0 <TV-IP> [apk]"; exit 1; }
adb connect "$TV:5555"
echo "Accept the 'Allow USB debugging' prompt on the TV if it appears..."; sleep 4
adb -s "$TV:5555" install -r "$APK"
S="adb -s $TV:5555 shell"
echo "Granting Continue Watching access (media sessions)..."
$S cmd notification allow_listener $PKG/$PKG.service.MediaListenerService || true
echo "Granting usage access (sort apps by use)..."
$S appops set $PKG GET_USAGE_STATS allow || true
echo "Trying the system Watch Next list (most TVs refuse this; that's fine)..."
$S pm grant $PKG com.android.providers.tv.permission.READ_EPG_DATA 2>/dev/null || true
$S pm grant $PKG com.android.providers.tv.permission.ACCESS_WATCHED_PROGRAMS 2>/dev/null || true
echo "Setting Halo as home..."
$S cmd package set-home-activity $PKG/.MainActivity || true
echo
read -r -p "Disable the stock Google TV home so Home always opens Halo? (y/N) " yn
if [ "$yn" = "y" ] || [ "$yn" = "Y" ]; then
  $S pm disable-user --user 0 com.google.android.apps.tv.launcherx || true
  $S pm disable-user --user 0 com.google.android.tungsten.setupwraith || true
  $S pm disable-user --user 0 com.google.android.tvlauncher 2>/dev/null || true
  echo "Done. Undo any time with ./undo-tv.sh $TV"
fi
$S am start -n $PKG/.MainActivity
echo "Halo is installed."
