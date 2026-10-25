#!/usr/bin/env bash
# Restores the stock Google TV home screen. Usage: ./undo-tv.sh <TV-IP>
TV="$1"; adb connect "$TV:5555"; S="adb -s $TV:5555 shell"
$S pm enable com.google.android.apps.tv.launcherx
$S pm enable com.google.android.tungsten.setupwraith
$S pm enable com.google.android.tvlauncher 2>/dev/null
echo "Stock home restored. Press Home on the remote."
