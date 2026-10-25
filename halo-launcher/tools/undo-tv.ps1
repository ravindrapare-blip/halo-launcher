param([Parameter(Mandatory=$true)][string]$Tv)
$dev = "${Tv}:5555"; adb connect $dev
adb -s $dev shell pm enable com.google.android.apps.tv.launcherx
adb -s $dev shell pm enable com.google.android.tungsten.setupwraith
Write-Host "Stock home restored. Press Home on the remote."
