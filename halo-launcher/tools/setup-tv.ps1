# Halo — one-time TV setup over ADB (Windows PowerShell).
# Usage:  .\setup-tv.ps1 -Tv 192.168.1.50 -Apk .\app-release.apk
param([Parameter(Mandatory=$true)][string]$Tv, [string]$Apk = "app-release.apk")
$pkg = "com.halo.launcher"; $dev = "${Tv}:5555"
adb connect $dev
Write-Host "Accept the 'Allow USB debugging' prompt on the TV if it appears..."; Start-Sleep 4
adb -s $dev install -r $Apk
adb -s $dev shell cmd notification allow_listener "$pkg/$pkg.service.MediaListenerService"
adb -s $dev shell appops set $pkg GET_USAGE_STATS allow
adb -s $dev shell pm grant $pkg com.android.providers.tv.permission.READ_EPG_DATA 2>$null
adb -s $dev shell pm grant $pkg com.android.providers.tv.permission.ACCESS_WATCHED_PROGRAMS 2>$null
adb -s $dev shell cmd package set-home-activity "$pkg/.MainActivity"
$yn = Read-Host "Disable the stock Google TV home so Home always opens Halo? (y/N)"
if ($yn -eq "y") {
  adb -s $dev shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  adb -s $dev shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
  Write-Host "Done. Undo any time with .\undo-tv.ps1 -Tv $Tv"
}
adb -s $dev shell am start -n "$pkg/.MainActivity"
Write-Host "Halo is installed."
