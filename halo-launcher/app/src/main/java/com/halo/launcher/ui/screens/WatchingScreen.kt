package com.halo.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.halo.launcher.data.HaloPrefs
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import com.halo.launcher.data.WatchOrder
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.AmbientBackground
import com.halo.launcher.ui.components.AppBadge
import com.halo.launcher.ui.components.AppGlyph
import com.halo.launcher.ui.components.Artwork
import com.halo.launcher.ui.components.Chip
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.PillButton
import com.halo.launcher.ui.components.ProgressLine
import com.halo.launcher.ui.components.Toggle
import com.halo.launcher.ui.components.ago
import com.halo.launcher.ui.components.artModel
import com.halo.launcher.ui.components.clock
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.components.rememberArtPalette
import com.halo.launcher.ui.components.timeLeft
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSpace
import com.halo.launcher.ui.theme.HaloType

private enum class KindFilter(
    val label: String,
) {
    All("All"),
    Video("Shows & Movies"),
    Music("Music"),
    Games("Games"),
}

@Composable
fun WatchingScreen(c: HaloController) {
    val g = c.graph
    val prefs by g.prefs.state.collectAsStateWithLifecycle()
    val all by g.continueItems.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(KindFilter.All) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var showPrefs by remember { mutableStateOf(false) }
    val list =
        remember(all, kind) {
            when (kind) {
                KindFilter.All -> all
                KindFilter.Video -> all.filter { it.kind == MediaKind.Video }
                KindFilter.Music -> all.filter { it.kind == MediaKind.Music }
                KindFilter.Games -> all.filter { it.kind == MediaKind.Game }
            }
        }
    val sel = list.firstOrNull { it.id == selectedId } ?: list.firstOrNull()
    val firstRow = remember { FocusRequester() }
    val firstChip = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { if (list.isNotEmpty()) firstRow.requestFocus() else firstChip.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(rememberArtPalette(sel?.artModel()), dim = 0.15f)
        Column(Modifier.fillMaxSize()) {
            TopBar(c, Screen.Watching, all.size)
            Row(
                Modifier.fillMaxSize().padding(horizontal = HaloSpace.gutter, vertical = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                // ---------- Featured ----------
                Column(Modifier.width(540.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            HText("Watching", HaloType.Title1)
                            HText(
                                "${list.size} in progress · across ${list.map { it.packageName }.distinct().size} apps",
                                HaloType.Label,
                                HaloColor.TextSecondary,
                            )
                        }
                        PillButton("Preferences", { showPrefs = true }, icon = Icons.Sliders, modifier = Modifier.height(28.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KindFilter.entries.forEachIndexed { i, k ->
                            Chip(
                                k.label,
                                kind == k,
                                {
                                    kind = k
                                    selectedId = null
                                },
                                if (i ==
                                    0
                                ) {
                                    Modifier.focusRequester(firstChip)
                                } else {
                                    Modifier
                                },
                            )
                        }
                    }
                    AnimatedContent(sel, contentKey = { it?.id }, transitionSpec = {
                        fadeIn(tween(HaloMotion.Emphasized)) togetherWith
                            fadeOut(tween(HaloMotion.Fast))
                    }, label = "featured") { e ->
                        if (e == null) EmptyWatching(c) else Featured(c, e, prefs)
                    }
                }
                // ---------- Up next queue ----------
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        HText("Up next", HaloType.Headline, modifier = Modifier.weight(1f))
                        HText(prefs.order.label, HaloType.Caption, HaloColor.TextTertiary)
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                            QueueRow(
                                c,
                                e,
                                selected = e.id == sel?.id,
                                spoiler = prefs.spoilerSafe,
                                onFocus = { selectedId = e.id },
                                modifier = if (i == 0) Modifier.focusRequester(firstRow) else Modifier,
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            showPrefs,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(HaloMotion.Emphasized, easing = HaloMotion.EaseOut)) { it / 3 } + fadeIn(),
            exit = slideOutHorizontally { it / 3 } + fadeOut(),
        ) { PrefsPanel(c, prefs, onClose = { showPrefs = false }) }
    }
}

@Composable
private fun Featured(
    c: HaloController,
    e: MediaEntry,
    prefs: HaloPrefs,
) {
    val app = c.app(e.packageName)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).glass(HaloGlass.Thin, HaloRadius.Sheet)) {
            Artwork(e, app, Modifier.fillMaxSize(), blurred = prefs.spoilerSafe && e.episodeLabel != null, iconSize = 64.dp)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.82f)),
                ),
            )
            Row(Modifier.align(Alignment.TopStart).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppBadge(app, c.appName(e.packageName))
                AppBadge(null, ago(e.updatedAt))
                if (e.intentUri != null) AppBadge(null, "Exact resume")
            }
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (e.subtitle.isNotBlank()) HText(e.subtitle, HaloType.Label, HaloColor.TextSecondary)
                HText(e.title, HaloType.Title1, maxLines = 2)
                if (e.durationMs >
                    0
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ProgressLine(e.progress, Modifier.width(180.dp), height = 3.dp)
                        HText(timeLeft(e), HaloType.Label, HaloColor.TextSecondary)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(
                (if (e.kind == MediaKind.Music) "Play in " else "Resume in ") + c.appName(e.packageName),
                { c.play(e, false) },
                primary = true,
                icon = Icons.Play,
                iconFilled = true,
                trailing = e.positionMs.takeIf { it > 5000 && e.kind != MediaKind.Music }?.let { clock(it) },
            )
            if (e.kind == MediaKind.Video) PillButton("Start over", { c.play(e, true) }, icon = Icons.Restart)
            PillButton("Remove", { c.graph.history.remove(e.id) }, icon = Icons.Trash)
        }
        HText(
            "Opens ${c.appName(e.packageName)} straight into this title — no app home screen, no searching.",
            HaloType.Caption,
            HaloColor.TextTertiary,
        )
    }
}

@Composable
private fun EmptyWatching(c: HaloController) {
    Box(Modifier.fillMaxWidth().height(250.dp).glass(HaloGlass.Thin, HaloRadius.Sheet), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HText("Nothing in progress yet", HaloType.Title2)
            HText(
                if (c.graph.system.mediaAccessOn()) "Play something in any app and it shows up here." else "Turn on Continue Watching access in Settings › Access.",
                HaloType.Body,
                HaloColor.TextSecondary,
            )
        }
    }
}

@Composable
private fun QueueRow(
    c: HaloController,
    e: MediaEntry,
    selected: Boolean,
    spoiler: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier,
) {
    val app = c.app(e.packageName)
    HaloFocusable(
        onClick = { c.play(e, false) },
        onLongClick = { c.entrySheet(e) },
        onFocus = onFocus,
        modifier = modifier.fillMaxWidth().height(50.dp),
        shape = HaloRadius.Panel,
        focusedScale = 1.03f,
        ring = false,
        fill = if (selected) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.04f),
        focusedFill = Color.White.copy(alpha = 0.94f),
        description = "${e.title}, ${c.appName(e.packageName)}, ${timeLeft(e)}. OK to play, hold for options",
    ) { f ->
        val fg = if (f) HaloColor.OnLight else HaloColor.Text
        val fg2 = if (f) HaloColor.OnLightSecondary else HaloColor.TextSecondary
        Row(
            Modifier.fillMaxSize().padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.width(74.dp).fillMaxHeight().background(Color.Black, HaloRadius.Card)) {
                Artwork(
                    e,
                    app,
                    Modifier.fillMaxSize().glass(HaloGlass.Thin, HaloRadius.Card),
                    blurred = spoiler && e.episodeLabel != null,
                    iconSize = 20.dp,
                )
                app?.icon?.let { AppGlyph(it, 12.dp, Modifier.align(Alignment.TopStart).padding(3.dp)) }
                if (e.durationMs >
                    0
                ) {
                    ProgressLine(
                        e.progress,
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 4.dp, vertical = 3.dp),
                        height = 2.dp,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HText(e.title, HaloType.Label.copy(fontWeight = FontWeight.SemiBold, fontSize = HaloType.Body.fontSize * 0.92f), fg)
                HText(c.appName(e.packageName) + (e.subtitle.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), HaloType.Caption, fg2)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                HText(timeLeft(e), HaloType.Caption.copy(fontWeight = FontWeight.SemiBold), fg)
                HText(ago(e.updatedAt), HaloType.Caption, fg2)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrefsPanel(
    c: HaloController,
    prefs: HaloPrefs,
    onClose: () -> Unit,
) {
    val first = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val g = c.graph
    val sources =
        remember(g.continueItems.value, g.history.items.value) {
            (g.history.items.value + g.watchNext.items.value).map { it.packageName }.distinct()
        }
    Column(
        Modifier
            .padding(end = 30.dp)
            .width(340.dp)
            .fillMaxHeight()
            .padding(vertical = 26.dp)
            .glass(HaloGlass.Thick, HaloRadius.Sheet)
            .background(Color(0xB3141620))
            .padding(18.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HText("Watching preferences", HaloType.Title2)
                HText("Shapes this screen and the Home row", HaloType.Caption, HaloColor.TextSecondary)
            }
            PillButton("Done", onClose, Modifier.height(26.dp).focusRequester(first))
        }
        HText("Order", HaloType.Label, HaloColor.TextSecondary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WatchOrder.entries.forEach { o -> Chip(o.label, prefs.order == o, { g.prefs.update { it.copy(order = o) } }) }
        }
        PrefToggle(
            "Spoiler-safe artwork",
            "Blurs episode stills",
            prefs.spoilerSafe,
        ) { g.prefs.update { it.copy(spoilerSafe = !it.spoilerSafe) } }
        PrefToggle("Hide when finished", "Leaves the list at 95% watched", prefs.hideFinished) {
            g.prefs.update { it.copy(hideFinished = !it.hideFinished) }
        }
        PrefToggle("Include music", "Show what you were listening to", prefs.includeMusic) {
            g.prefs.update { it.copy(includeMusic = !it.includeMusic) }
        }
        PrefToggle(
            "Show on Home",
            "The Continue Watching row",
            prefs.showOnHome,
        ) { g.prefs.update { it.copy(showOnHome = !it.showOnHome) } }
        if (sources.isNotEmpty()) {
            HText("Show progress from", HaloType.Label, HaloColor.TextSecondary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                sources.forEach { pkg ->
                    val on = pkg !in prefs.mutedSources
                    Chip(c.appName(pkg), on, {
                        g.prefs.update { p -> p.copy(mutedSources = if (on) p.mutedSources + pkg else p.mutedSources - pkg) }
                    })
                }
            }
        }
        PillButton("Clear history", { g.history.clear() }, icon = Icons.Trash)
    }
}

@Composable
fun PrefToggle(
    title: String,
    sub: String,
    on: Boolean,
    onToggle: () -> Unit,
) {
    HaloFocusable(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth().height(42.dp),
        shape = HaloRadius.Panel,
        focusedScale = 1.02f,
        ring = false,
        fill = Color.White.copy(alpha = 0.05f),
        focusedFill = Color.White.copy(alpha = 0.16f),
        description = "$title, ${if (on) "on" else "off"}",
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                HText(title, HaloType.Label.copy(fontSize = HaloType.Body.fontSize * 0.92f, fontWeight = FontWeight.Medium))
                HText(sub, HaloType.Caption, HaloColor.TextTertiary)
            }
            Toggle(on)
        }
    }
}
