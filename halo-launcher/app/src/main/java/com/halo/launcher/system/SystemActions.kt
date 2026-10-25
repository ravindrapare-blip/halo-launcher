package com.halo.launcher.system

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Process
import android.provider.Settings
import com.halo.launcher.service.HomeButtonService
import com.halo.launcher.service.SessionHub
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class SystemPage(
    val label: String,
    val action: String,
) {
    Picture("Picture", "android.settings.DISPLAY_SETTINGS"),
    Sound("Sound", Settings.ACTION_SOUND_SETTINGS),
    Network("Network & Internet", Settings.ACTION_WIFI_SETTINGS),
    Remotes("Remotes & Accessories", Settings.ACTION_BLUETOOTH_SETTINGS),
    Apps("Apps", Settings.ACTION_APPLICATION_SETTINGS),
    DateTime("Date & time", Settings.ACTION_DATE_SETTINGS),
    Accessibility("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS),
    Screensaver("Screen saver", "android.settings.DREAM_SETTINGS"),
    All("All settings", Settings.ACTION_SETTINGS),
}

class SystemActions(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val audio = context.getSystemService(AudioManager::class.java)

    // ---------- Settings shortcuts ----------
    fun open(page: SystemPage): Boolean = start(Intent(page.action)) || start(Intent(Settings.ACTION_SETTINGS))

    fun openMediaAccess(): Boolean =
        start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) ||
            start(
                Intent("android.settings.NOTIFICATION_LISTENER_DETAIL_SETTINGS").putExtra(
                    "android.provider.extra.NOTIFICATION_LISTENER_COMPONENT_NAME",
                    ComponentName(context, "com.halo.launcher.service.MediaListenerService").flattenToString(),
                ),
            )

    fun openUsageAccess(): Boolean = start(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openAccessibility(): Boolean = start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openHomeSettings(): Boolean = start(Intent(Settings.ACTION_HOME_SETTINGS))

    private fun start(i: Intent): Boolean {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(context.packageManager) == null) return false
        return runCatching {
            context.startActivity(i)
            true
        }.getOrDefault(false)
    }

    // ---------- Status ----------
    fun mediaAccessOn() = SessionHub.isEnabled(context)

    fun homeButtonOn() = HomeButtonService.isEnabled(context)

    fun usageAccessOn(): Boolean =
        runCatching {
            val ops = context.getSystemService(AppOpsManager::class.java)
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)

    fun isDefaultHome(): Boolean {
        val r = context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
        return r?.activityInfo?.packageName == context.packageName
    }

    // ---------- Volume ----------
    val maxVolume: Int get() = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 100
    val volume: Int get() = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    val isMuted: Boolean get() = audio?.isStreamMute(AudioManager.STREAM_MUSIC) ?: false

    fun volumeStep(up: Boolean) =
        audio?.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )

    fun toggleMute() = audio?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)

    // ---------- Screen saver ----------
    fun startScreensaver(): Boolean =
        start(Intent(Intent.ACTION_MAIN).setComponent(ComponentName("com.android.systemui", "com.android.systemui.Somnambulator"))) ||
            open(SystemPage.Screensaver)

    // ---------- Sleep timer ----------
    private val _sleepAt = MutableStateFlow<Long?>(null)
    val sleepAt: StateFlow<Long?> = _sleepAt
    private var sleepJob: Job? = null

    /** Pauses whatever is playing and starts the screen saver when the timer ends. */
    fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            _sleepAt.value = null
            return
        }
        val at = System.currentTimeMillis() + minutes * 60_000L
        _sleepAt.value = at
        sleepJob =
            scope.launch {
                delay(at - System.currentTimeMillis())
                SessionHub.pauseAll()
                startScreensaver()
                _sleepAt.value = null
            }
    }
}
