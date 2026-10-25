package com.halo.launcher.data

import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppRepository(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val pm: PackageManager = context.packageManager
    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps

    private val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                c: Context?,
                i: Intent?,
            ) = refresh()
        }

    init {
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    private val _recent = MutableStateFlow<List<AppInfo>>(emptyList())

    /** Apps in the order you last opened them (needs Usage access). */
    val recent: StateFlow<List<AppInfo>> = _recent

    fun refresh() {
        scope.launch {
            _apps.value = withContext(Dispatchers.IO) { load() }
            refreshRecent()
        }
    }

    fun refreshRecent() {
        scope.launch {
            val order = withContext(Dispatchers.IO) { lastOpened() }
            val all = _apps.value
            _recent.value = order.mapNotNull { p -> all.firstOrNull { it.packageName == p } }.take(12)
        }
    }

    private fun lastOpened(): List<String> =
        runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
            val end = System.currentTimeMillis()
            val ev = usm.queryEvents(end - 7L * 24 * 3600 * 1000, end)
            val last = HashMap<String, Long>()
            val e =
                android.app.usage.UsageEvents
                    .Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                @Suppress("DEPRECATION")
                if (e.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND) last[e.packageName] = e.timeStamp
            }
            val homes =
                pm
                    .queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
                    .map {
                        it.activityInfo.packageName
                    }.toSet()
            last.entries
                .filter { it.key != context.packageName && it.key !in homes }
                .sortedByDescending { it.value }
                .map { it.key }
        }.getOrDefault(emptyList())

    fun find(pkg: String): AppInfo? = _apps.value.firstOrNull { it.packageName == pkg }

    fun label(pkg: String): String =
        find(pkg)?.label ?: runCatching {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() })

    fun launchIntent(pkg: String): Intent? =
        (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    fun launch(pkg: String): Boolean {
        val intent = launchIntent(pkg) ?: return false
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun load(): List<AppInfo> {
        val self = context.packageName
        val tv = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)
        val phone = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val byPkg = LinkedHashMap<String, Pair<android.content.pm.ResolveInfo, Boolean>>()
        tv.forEach { byPkg[it.activityInfo.packageName] = it to true }
        phone.forEach { if (!byPkg.containsKey(it.activityInfo.packageName)) byPkg[it.activityInfo.packageName] = it to false }
        val usage = usageRanking()
        return byPkg.values
            .filter { it.first.activityInfo.packageName != self }
            .map { (ri, isTv) ->
                val ai = ri.activityInfo
                AppInfo(
                    packageName = ai.packageName,
                    label = ri.loadLabel(pm).toString(),
                    component = ComponentName(ai.packageName, ai.name),
                    icon = ri.loadIcon(pm).toImage(144, 144),
                    banner = (ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm))?.toImage(320, 180),
                    group = groupOf(ai.applicationInfo),
                    isTvApp = isTv,
                )
            }.sortedWith(compareByDescending<AppInfo> { usage[it.packageName] ?: 0L }.thenBy { it.label.lowercase() })
    }

    private fun groupOf(ai: ApplicationInfo): AppGroup =
        when (ai.category) {
            ApplicationInfo.CATEGORY_VIDEO, ApplicationInfo.CATEGORY_IMAGE -> AppGroup.Watch
            ApplicationInfo.CATEGORY_AUDIO -> AppGroup.Listen
            ApplicationInfo.CATEGORY_GAME -> AppGroup.Play
            else ->
                @Suppress("DEPRECATION")
                if ((ai.flags and ApplicationInfo.FLAG_IS_GAME) != 0) AppGroup.Play else guessGroup(ai.packageName)
        }

    private fun guessGroup(pkg: String): AppGroup {
        val p = pkg.lowercase()
        return when {
            listOf(
                "music",
                "spotify",
                "audio",
                "radio",
                "podcast",
                "gaana",
                "saavn",
                "wynk",
                "soundcloud",
            ).any { it in p } -> AppGroup.Listen
            listOf("game", "play.games", "arcade").any { it in p } -> AppGroup.Play
            listOf(
                "video",
                "tv",
                "netflix",
                "youtube",
                "prime",
                "hotstar",
                "disney",
                "jio",
                "sonyliv",
                "zee5",
                "plex",
                "kodi",
                "vlc",
                "mx",
                "hulu",
                "max",
            ).any {
                it in
                    p
            } -> AppGroup.Watch
            else -> AppGroup.Other
        }
    }

    /** Most-used first when Usage Access is granted; empty map otherwise. */
    private fun usageRanking(): Map<String, Long> =
        runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return emptyMap()
            val end = System.currentTimeMillis()
            usm
                .queryUsageStats(UsageStatsManager.INTERVAL_BEST, end - 14L * 24 * 3600 * 1000, end)
                .groupBy { it.packageName }
                .mapValues { (_, v) -> v.sumOf { it.totalTimeInForeground } }
        }.getOrDefault(emptyMap())

    private fun Drawable.toImage(
        w: Int,
        h: Int,
    ): ImageBitmap? =
        runCatching {
            val iw = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(w) else w
            val ih = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(h) else h
            toBitmap(iw, ih).asImageBitmap()
        }.getOrNull()
}
