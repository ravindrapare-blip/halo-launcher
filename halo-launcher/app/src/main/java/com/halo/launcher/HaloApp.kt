package com.halo.launcher

import android.app.Application
import com.halo.launcher.data.AppGroup
import com.halo.launcher.data.AppRepository
import com.halo.launcher.data.HistoryStore
import com.halo.launcher.data.InputsRepository
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import com.halo.launcher.data.Prefs
import com.halo.launcher.data.WatchNextSource
import com.halo.launcher.data.WatchOrder
import com.halo.launcher.resume.ResumeEngine
import com.halo.launcher.system.SystemActions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class HaloApp : Application() {
    lateinit var graph: Graph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = Graph(this)
        // After an update/reboot Android sometimes forgets to re-bind the media listener; ask again.
        runCatching {
            android.service.notification.NotificationListenerService.requestRebind(
                android.content.ComponentName(this, com.halo.launcher.service.MediaListenerService::class.java),
            )
        }
    }
}

/** Tiny hand-rolled dependency graph — one instance of everything for the whole launcher. */
class Graph(
    app: Application,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs = Prefs(app)
    val apps = AppRepository(app, scope)
    val history = HistoryStore(app, scope)
    val watchNext = WatchNextSource(app, scope)
    val inputs = InputsRepository(app)
    val resume = ResumeEngine(app, apps)
    val system = SystemActions(app, scope)
    val contentSearch =
        com.halo.launcher.data
            .ContentSearch(app)

    /** Everything in progress across apps, merged, filtered and ordered by the user's preferences. */
    val continueItems: StateFlow<List<MediaEntry>> =
        combine(history.items, watchNext.items, prefs.state) { mine, system, p ->
            val merged = LinkedHashMap<String, MediaEntry>()
            // Watch Next rows win: they carry an exact deep link.
            (system + mine).forEach { e ->
                val key = e.packageName + "|" + e.title.lowercase()
                val prev = merged[key]
                merged[key] =
                    when {
                        prev == null -> e
                        prev.intentUri != null ->
                            prev.copy(
                                artPath = prev.artPath ?: e.artPath,
                                positionMs = if (e.updatedAt > prev.updatedAt) e.positionMs else prev.positionMs,
                                updatedAt = maxOf(e.updatedAt, prev.updatedAt),
                            )
                        else -> if (e.updatedAt > prev.updatedAt) e else prev
                    }
            }
            merged.values
                .filter { it.packageName !in p.mutedSources }
                .filter { p.includeMusic || it.kind != MediaKind.Music }
                .filter { !(p.hideFinished && it.isFinished && it.kind == MediaKind.Video) }
                .let { list ->
                    when (p.order) {
                        WatchOrder.Recent -> list.sortedByDescending { it.updatedAt }
                        WatchOrder.EndingSoon -> list.sortedBy { if (it.durationMs > 0) it.remainingMs else Long.MAX_VALUE }
                        WatchOrder.ByApp ->
                            list.sortedWith(
                                compareBy<MediaEntry> { apps.label(it.packageName).lowercase() }.thenByDescending { it.updatedAt },
                            )
                    }
                }
        }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** First-run dock: the most media-heavy apps the user already has. */
    fun seedDockIfNeeded() {
        val p = prefs.state.value
        if (p.dockInitialised) return
        val list = apps.apps.value
        if (list.isEmpty()) return
        val pick =
            (
                list.filter { it.group == AppGroup.Watch } + list.filter { it.group == AppGroup.Listen } +
                    list.filter { it.group == AppGroup.Play } + list
            ).map { it.packageName }.distinct().take(6)
        prefs.update { it.copy(dock = pick, dockInitialised = true) }
    }
}
