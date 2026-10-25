package com.halo.launcher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.launcher.BuildConfigInfo
import com.halo.launcher.data.WatchOrder
import com.halo.launcher.system.SystemPage
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.AmbientBackground
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.Toggle
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSpace
import com.halo.launcher.ui.theme.HaloType

private data class Cat(
    val name: String,
    val sub: String,
    val icon: String,
    val a: Color,
    val b: Color,
)

private val CATS =
    listOf(
        Cat("Watching", "Continue Watching", Icons.Film, Color(0xFF5B8CFF), Color(0xFF3A1BB8)),
        Cat("Look & Feel", "Glass, motion, clock", Icons.Sparkle, Color(0xFFFF7A45), Color(0xFFB5244F)),
        Cat("Access", "Permissions & launcher", Icons.Shield, Color(0xFF20E3B2), Color(0xFF1B54E0)),
        Cat("TV Settings", "Picture, sound, network", Icons.Picture, Color(0xFF7A8199), Color(0xFF2A2E3D)),
        Cat("About", "Halo ${BuildConfigInfo.VERSION}", Icons.Info, Color(0xFFC77DFF), Color(0xFF5A189A)),
    )

private sealed interface Row_ {
    data class Toggle_(
        val title: String,
        val sub: String,
        val on: Boolean,
        val flip: () -> Unit,
    ) : Row_

    data class Value(
        val title: String,
        val sub: String,
        val value: String,
        val ok: Boolean? = null,
        val act: () -> Unit,
    ) : Row_

    data class Note(
        val text: String,
    ) : Row_
}

@Composable
fun SettingsScreen(
    c: HaloController,
    resumeTick: Int,
) {
    val g = c.graph
    val prefs by g.prefs.state.collectAsStateWithLifecycle()
    val wnAvailable by g.watchNext.available.collectAsStateWithLifecycle()
    var cat by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val sys = g.system
    // Re-read permission state whenever we come back from a system screen.
    val access = remember(resumeTick) { listOf(sys.mediaAccessOn(), sys.usageAccessOn(), sys.homeButtonOn(), sys.isDefaultHome()) }

    val rows: List<Row_> =
        when (cat) {
            0 ->
                listOf(
                    Row_.Value("Order", "How Continue Watching is sorted", prefs.order.label) {
                        g.prefs.update { it.copy(order = WatchOrder.entries[(it.order.ordinal + 1) % WatchOrder.entries.size]) }
                    },
                    Row_.Toggle_("Spoiler-safe artwork", "Blurs episode stills", prefs.spoilerSafe) {
                        g.prefs.update { it.copy(spoilerSafe = !it.spoilerSafe) }
                    },
                    Row_.Toggle_("Hide when finished", "Titles leave the list at 95% watched", prefs.hideFinished) {
                        g.prefs.update { it.copy(hideFinished = !it.hideFinished) }
                    },
                    Row_.Toggle_("Include music", "Show what you were listening to", prefs.includeMusic) {
                        g.prefs.update { it.copy(includeMusic = !it.includeMusic) }
                    },
                    Row_.Toggle_(
                        "Show on Home",
                        "The Continue Watching row",
                        prefs.showOnHome,
                    ) { g.prefs.update { it.copy(showOnHome = !it.showOnHome) } },
                    Row_.Value(
                        "Hidden apps",
                        "Apps you stopped showing",
                        if (prefs.mutedSources.isEmpty()) "None" else "${prefs.mutedSources.size} · Reset",
                    ) {
                        g.prefs.update { it.copy(mutedSources = emptySet()) }
                    },
                    Row_.Value("Clear history", "Removes every in-progress item Halo saved", "Clear") {
                        g.history.clear()
                        c.toast("History cleared")
                    },
                )
            1 ->
                listOf(
                    Row_.Value("Glass", "How frosted panels look", glassName(prefs.glassBoost)) {
                        g.prefs.update { it.copy(glassBoost = nextGlass(it.glassBoost)) }
                    },
                    Row_.Toggle_("Ambient motion", "Slow light drifting behind everything", prefs.ambientMotion) {
                        g.prefs.update { it.copy(ambientMotion = !it.ambientMotion) }
                    },
                    Row_.Toggle_("Reduce motion", "Calmer focus animations", prefs.reduceMotion) {
                        g.prefs.update { it.copy(reduceMotion = !it.reduceMotion) }
                    },
                    Row_.Toggle_(
                        "24-hour clock",
                        "Shows 21:30 instead of 9:30",
                        prefs.clock24h,
                    ) { g.prefs.update { it.copy(clock24h = !it.clock24h) } },
                )
            2 ->
                listOf(
                    Row_.Value(
                        "Continue Watching access",
                        "Lets Halo see what's playing in other apps",
                        if (access[0]) "On" else "Turn on",
                        access[0],
                    ) {
                        if (!sys.openMediaAccess()) c.toast("Hidden on this TV — use the ADB command in the setup guide")
                    },
                    Row_.Value(
                        "Exact resume (Watch Next)",
                        "System list with deep links, if your TV allows it",
                        if (wnAvailable) "Available" else "Not allowed",
                        wnAvailable,
                    ) {
                        g.watchNext.refresh()
                    },
                    Row_.Value("Usage access", "Sorts Library by what you use most", if (access[1]) "On" else "Turn on", access[1]) {
                        if (!sys.openUsageAccess()) c.toast("Hidden on this TV — use the ADB command in the setup guide")
                    },
                    Row_.Value("Default launcher", "Halo opens when the TV starts", if (access[3]) "Halo" else "Not yet", access[3]) {
                        if (!sys.openHomeSettings()) c.toast("This TV hides the launcher choice — see the setup guide")
                    },
                    Row_.Value(
                        "Home button helper",
                        "Sends the remote's Home button to Halo",
                        if (access[2]) "On" else "Turn on",
                        access[2],
                    ) {
                        if (!sys.openAccessibility()) c.toast("Couldn't open Accessibility settings")
                    },
                    Row_.Note(
                        "On Google TV, the reliable way to make Halo your home screen is the one-time ADB step in the setup guide. It can be undone any time.",
                    ),
                )
            3 -> SystemPage.entries.map { p -> Row_.Value(p.label, "Opens the TV's own settings", "Open") { sys.open(p) } }
            else ->
                listOf(
                    Row_.Value("Version", "Halo for Android TV", BuildConfigInfo.VERSION) {},
                    Row_.Note(
                        "How resume works: Halo first uses the exact deep link an app publishes (Watch Next). If that isn't shared, it wakes the app's own media session and plays from your spot. Apps that share neither still open, so you can continue from their own screen.",
                    ),
                )
        }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(listOf(CATS[cat].a, CATS[cat].b, CATS[cat].a), dim = 0.2f)
        Row(
            Modifier.fillMaxSize().padding(horizontal = HaloSpace.gutter, vertical = 34.dp),
            horizontalArrangement = Arrangement.spacedBy(30.dp),
        ) {
            Column(Modifier.width(260.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ScreenTitle("Settings", null) { c.go(Screen.Home) }
                Gap(h = 12)
                CATS.forEachIndexed { i, ct ->
                    val sel = i == cat
                    HaloFocusable(
                        onClick = { cat = i },
                        onFocus = { cat = i },
                        modifier = Modifier.fillMaxWidth().height(46.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                        shape = HaloRadius.Panel,
                        focusedScale = 1.03f,
                        ring = false,
                        fill = if (sel) Color.White.copy(alpha = 0.12f) else Color.Transparent,
                        focusedFill = Color.White.copy(alpha = 0.94f),
                    ) { f ->
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                Modifier.size(30.dp).background(Brush.linearGradient(listOf(ct.a, ct.b)), HaloRadius.Card),
                                contentAlignment = Alignment.Center,
                            ) {
                                HaloIcon(ct.icon, 15.dp)
                            }
                            Column(Modifier.weight(1f)) {
                                HText(
                                    ct.name,
                                    HaloType.Label.copy(fontSize = HaloType.Body.fontSize, fontWeight = FontWeight.SemiBold),
                                    if (f) HaloColor.OnLight else HaloColor.Text,
                                )
                                HText(ct.sub, HaloType.Caption, if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary)
                            }
                        }
                    }
                }
            }
            AnimatedContent(
                cat,
                modifier = Modifier.weight(1f),
                transitionSpec = {
                    (
                        fadeIn(tween(HaloMotion.Standard)) +
                            slideInVertically(tween(HaloMotion.Emphasized, easing = HaloMotion.EaseOut)) { 30 }
                    ) togetherWith
                        fadeOut(tween(HaloMotion.Fast))
                },
                label = "settingsPane",
            ) { idx ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HText(CATS[idx].sub, HaloType.Label, HaloColor.TextSecondary, Modifier.padding(top = 6.dp))
                    HText(CATS[idx].name, HaloType.Title1)
                    Gap(h = 6)
                    Column(
                        Modifier.fillMaxWidth().glass(HaloGlass.Thin, HaloRadius.Sheet).padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        rows.forEach { r -> SettingRow(r) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(r: Row_) {
    when (r) {
        is Row_.Note ->
            HText(
                r.text,
                HaloType.Caption,
                HaloColor.TextTertiary,
                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                maxLines = 4,
            )
        is Row_.Toggle_ -> RowShell(r.title, r.sub, r.flip) { Toggle(r.on) }
        is Row_.Value ->
            RowShell(r.title, r.sub, r.act) { f ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.ok?.let { Box(Modifier.size(7.dp).background(if (it) HaloColor.Live else Color(0xFFFFC24D), HaloRadius.Pill)) }
                    HText(
                        r.value,
                        HaloType.Label.copy(fontSize = HaloType.Body.fontSize * 0.9f),
                        if (f) HaloColor.OnLightSecondary else HaloColor.TextSecondary,
                    )
                    HaloIcon(Icons.Chevron, 10.dp, if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary)
                }
            }
    }
}

@Composable
private fun RowShell(
    title: String,
    sub: String,
    onClick: () -> Unit,
    trailing: @Composable (Boolean) -> Unit,
) {
    HaloFocusable(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(46.dp),
        shape = HaloRadius.Panel,
        focusedScale = 1.02f,
        ring = false,
        fill = Color.Transparent,
        focusedFill = Color.White.copy(alpha = 0.94f),
        description = title,
    ) { f ->
        Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HText(
                    title,
                    HaloType.Label.copy(fontSize = HaloType.Body.fontSize, fontWeight = FontWeight.Medium),
                    if (f) HaloColor.OnLight else HaloColor.Text,
                )
                HText(sub, HaloType.Caption, if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary)
            }
            trailing(f)
        }
    }
}

private val GLASS = listOf(-0.03f to "Clear", 0f to "Balanced", 0.04f to "Frosted", 0.08f to "Solid")

private fun glassName(v: Float) = GLASS.minBy { kotlin.math.abs(it.first - v) }.second

private fun nextGlass(v: Float): Float {
    val i = GLASS.indexOfFirst { it.second == glassName(v) }
    return GLASS[(i + 1) % GLASS.size].first
}
