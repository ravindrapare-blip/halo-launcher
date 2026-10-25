package com.halo.launcher.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class WatchOrder(
    val label: String,
) {
    Recent("Most recent"),
    EndingSoon("Ending soon"),
    ByApp("By app"),
}

data class HaloPrefs(
    val dock: List<String> = emptyList(),
    val dockInitialised: Boolean = false,
    val order: WatchOrder = WatchOrder.Recent,
    val spoilerSafe: Boolean = false,
    val hideFinished: Boolean = true,
    val showOnHome: Boolean = true,
    val includeMusic: Boolean = true,
    val mutedSources: Set<String> = emptySet(),
    val clock24h: Boolean = false,
    val glassBoost: Float = 0f,
    val ambientMotion: Boolean = true,
    val reduceMotion: Boolean = false,
    val name: String = "",
)

/** Small, synchronous preference store exposed as a StateFlow. */
class Prefs(
    context: Context,
) {
    private val sp = context.getSharedPreferences("halo", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<HaloPrefs> = _state

    private fun read() =
        HaloPrefs(
            dock = sp.getString("dock", "")!!.split(',').filter { it.isNotBlank() },
            dockInitialised = sp.getBoolean("dockInit", false),
            order = runCatching { WatchOrder.valueOf(sp.getString("order", WatchOrder.Recent.name)!!) }.getOrDefault(WatchOrder.Recent),
            spoilerSafe = sp.getBoolean("spoiler", false),
            hideFinished = sp.getBoolean("hideFinished", true),
            showOnHome = sp.getBoolean("showOnHome", true),
            includeMusic = sp.getBoolean("includeMusic", true),
            mutedSources = sp.getStringSet("muted", emptySet())!!.toSet(),
            clock24h = sp.getBoolean("clock24", false),
            glassBoost = sp.getFloat("glassBoost", 0f),
            ambientMotion = sp.getBoolean("ambient", true),
            reduceMotion = sp.getBoolean("reduceMotion", false),
            name = sp.getString("name", "")!!,
        )

    fun update(block: (HaloPrefs) -> HaloPrefs) {
        val n = block(_state.value)
        sp
            .edit()
            .putString("dock", n.dock.joinToString(","))
            .putBoolean("dockInit", n.dockInitialised)
            .putString("order", n.order.name)
            .putBoolean("spoiler", n.spoilerSafe)
            .putBoolean("hideFinished", n.hideFinished)
            .putBoolean("showOnHome", n.showOnHome)
            .putBoolean("includeMusic", n.includeMusic)
            .putStringSet("muted", n.mutedSources)
            .putBoolean("clock24", n.clock24h)
            .putFloat("glassBoost", n.glassBoost)
            .putBoolean("ambient", n.ambientMotion)
            .putBoolean("reduceMotion", n.reduceMotion)
            .putString("name", n.name)
            .apply()
        _state.value = n
    }

    fun togglePin(
        pkg: String,
        max: Int = 7,
    ) = update { p ->
        val d = p.dock.toMutableList()
        if (pkg in d) {
            d.remove(pkg)
        } else if (d.size < max) {
            d.add(pkg)
        }
        p.copy(dock = d, dockInitialised = true)
    }

    fun moveInDock(
        pkg: String,
        delta: Int,
    ) = update { p ->
        val d = p.dock.toMutableList()
        val i = d.indexOf(pkg)
        val j = (i + delta).coerceIn(0, d.lastIndex)
        if (i >= 0 && i != j) {
            d.removeAt(i)
            d.add(j, pkg)
        }
        p.copy(dock = d)
    }
}
