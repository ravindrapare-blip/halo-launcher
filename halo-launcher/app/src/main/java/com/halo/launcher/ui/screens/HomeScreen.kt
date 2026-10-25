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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.halo.launcher.data.AppInfo
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.AmbientBackground
import com.halo.launcher.ui.components.AppGlyph
import com.halo.launcher.ui.components.AppTile
import com.halo.launcher.ui.components.Artwork
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.MediaCard
import com.halo.launcher.ui.components.PillButton
import com.halo.launcher.ui.components.ProgressLine
import com.halo.launcher.ui.components.ago
import com.halo.launcher.ui.components.artModel
import com.halo.launcher.ui.components.clock
import com.halo.launcher.ui.components.floatAbove
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.components.rememberArtPalette
import com.halo.launcher.ui.components.timeLeft
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSize
import com.halo.launcher.ui.theme.HaloSpace
import com.halo.launcher.ui.theme.HaloType
import java.util.Calendar

@Composable
fun HomeScreen(
    c: HaloController,
    resumeTick: Int,
) {
    val g = c.graph
    val prefs by g.prefs.state.collectAsStateWithLifecycle()
    val all by g.continueItems.collectAsStateWithLifecycle()
    val apps by g.apps.apps.collectAsStateWithLifecycle()
    val recent by g.apps.recent.collectAsStateWithLifecycle()
    var filterPkg by remember { mutableStateOf<String?>(null) }
    val items = remember(all, filterPkg) { if (filterPkg == null) all else all.filter { it.packageName == filterPkg } }
    var heroId by remember { mutableStateOf<String?>(null) }
    val hero = items.firstOrNull { it.id == heroId } ?: items.firstOrNull()
    val accessOn = remember(resumeTick) { g.system.mediaAccessOn() }
    val showRow = prefs.showOnHome && items.isNotEmpty()

    val firstCard = remember { FocusRequester() }
    val firstDock = remember { FocusRequester() }
    LaunchedEffect(showRow) { runCatching { if (showRow) firstCard.requestFocus() else firstDock.requestFocus() } }

    val palette = rememberArtPalette(hero?.artModel())
    Box(Modifier.fillMaxSize()) {
        AmbientBackground(palette)

        // Hero artwork, bleeding off the right edge and fading into the glass.
        AnimatedContent(
            targetState = hero,
            modifier = Modifier.align(Alignment.TopEnd),
            transitionSpec = { fadeIn(tween(HaloMotion.Emphasized)) togetherWith fadeOut(tween(HaloMotion.Standard)) },
            contentKey = { it?.id },
            label = "heroArt",
        ) { h ->
            if (h != null && h.artPath != null) {
                Box(Modifier.width(600.dp).height(338.dp)) {
                    Artwork(h, c.app(h.packageName), Modifier.fillMaxSize(), blurred = prefs.spoilerSafe && h.episodeLabel != null)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0f to HaloColor.Ink,
                                0.45f to HaloColor.Ink.copy(alpha = 0.55f),
                                1f to Color.Transparent,
                            ),
                        ),
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to HaloColor.Ink)))
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            TopBar(c, Screen.Home, all.size)

            // ---------- Hero ----------
            Box(Modifier.padding(start = HaloSpace.gutter, top = 24.dp).width(500.dp).height(214.dp)) {
                AnimatedContent(
                    targetState = hero,
                    transitionSpec = {
                        (
                            fadeIn(tween(HaloMotion.Emphasized)) +
                                slideInVertically(tween(HaloMotion.Emphasized, easing = HaloMotion.EaseOut)) { it / 10 }
                        ) togetherWith
                            fadeOut(tween(HaloMotion.Fast))
                    },
                    contentKey = { it?.id },
                    label = "hero",
                ) { h ->
                    if (h == null) Welcome(c, accessOn, prefs.name) else Hero(c, h)
                }
            }

            // ---------- Rows: Continue Watching · Recently opened ----------
            LazyColumn(Modifier.padding(top = 6.dp).height(146.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                item(key = "continue") {
                    Column(Modifier.height(140.dp)) {
                        if (showRow) {
                            Row(
                                Modifier.padding(horizontal = HaloSpace.gutter),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                SectionLabel(
                                    "Continue Watching",
                                    filterPkg?.let { "From " + c.appName(it) }
                                        ?: "Across ${items.map { it.packageName }.distinct().size} apps",
                                )
                                if (filterPkg != null) PillButton("Show all apps", { filterPkg = null }, Modifier.height(22.dp))
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = HaloSpace.gutter, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                itemsIndexed(items, key = { _, e -> e.id }) { i, e ->
                                    MediaCard(
                                        entry = e,
                                        app = c.app(e.packageName),
                                        appName = c.appName(e.packageName),
                                        spoilerSafe = prefs.spoilerSafe,
                                        onClick = { c.play(e, false) },
                                        onLongClick = { c.entrySheet(e) },
                                        onFocus = { heroId = e.id },
                                        modifier = if (i == 0) Modifier.focusRequester(firstCard) else Modifier,
                                    )
                                }
                                item(key = "see-all") {
                                    HaloFocusable(
                                        onClick = { c.go(Screen.Watching) },
                                        modifier = Modifier.width(64.dp).height(HaloSize.cardH),
                                        fill = Color.White.copy(alpha = 0.07f),
                                        focusedFill = Color.White.copy(alpha = 0.16f),
                                        description = "See all in Watching",
                                    ) {
                                        Column(
                                            Modifier.align(Alignment.Center),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            HaloIcon(Icons.Chevron, 14.dp)
                                            HText("See all", HaloType.Caption)
                                        }
                                    }
                                }
                            }
                        } else if (!accessOn) {
                            SectionLabel("Continue Watching", "Off", Modifier.padding(horizontal = HaloSpace.gutter))
                            AccessCard(c, Modifier.padding(horizontal = HaloSpace.gutter, vertical = 12.dp))
                        }
                    }
                }
                if (recent.isNotEmpty()) {
                    item(key = "recent") {
                        Column(Modifier.height(140.dp)) {
                            SectionLabel("Recently opened", "Your latest apps", Modifier.padding(horizontal = HaloSpace.gutter))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = HaloSpace.gutter, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(recent, key = { it.packageName }) { a ->
                                    AppTile(
                                        a,
                                        onClick = { c.openApp(a) },
                                        onLongClick = { c.appSheet(a) },
                                        modifier = Modifier.width(HaloSize.cardW),
                                        pinned =
                                            a.packageName in prefs.dock,
                                        showLabel = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Dock(
            c,
            apps,
            prefs.dock,
            filterPkg,
            onFilter = { pkg ->
                filterPkg = if (filterPkg == pkg) null else pkg
                heroId = null
            },
            firstFocus = firstDock,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Hero(
    c: HaloController,
    h: MediaEntry,
) {
    val app = c.app(h.packageName)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.glass(HaloGlass.Regular, HaloRadius.Pill).padding(start = 3.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                app?.icon?.let { AppGlyph(it, 15.dp) }
                HText(
                    (
                        if (h.kind ==
                            MediaKind.Music
                        ) {
                            "Listening in "
                        } else {
                            "Continue in "
                        }
                    ) + c.appName(h.packageName),
                    HaloType.Label.copy(fontWeight = FontWeight.SemiBold),
                )
            }
            HText(ago(h.updatedAt), HaloType.Label, HaloColor.TextTertiary)
        }
        HText(h.title, HaloType.Display, maxLines = 2)
        val meta =
            listOfNotNull(
                h.subtitle.takeIf {
                    it.isNotBlank()
                },
                h.durationMs.takeIf { it > 0 && h.kind != MediaKind.Music }?.let { clock(it) },
            ).joinToString("  ·  ")
        if (meta.isNotBlank()) HText(meta, HaloType.Body, HaloColor.TextSecondary)
        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PillButton(
                text = if (h.kind == MediaKind.Music) "Play" else "Resume",
                onClick = { c.play(h, false) },
                primary = true,
                icon = Icons.Play,
                iconFilled = true,
                trailing = h.positionMs.takeIf { it > 5000 && h.kind != MediaKind.Music }?.let { clock(it) },
            )
            if (h.kind == MediaKind.Video) PillButton("Start over", { c.play(h, true) }, icon = Icons.Restart)
            PillButton("More", { c.entrySheet(h) }, icon = Icons.List)
            if (h.durationMs > 0) {
                Column(Modifier.padding(start = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    ProgressLine(h.progress, Modifier.width(110.dp))
                    HText(timeLeft(h), HaloType.Caption, HaloColor.TextSecondary)
                }
            }
        }
    }
}

@Composable
private fun Welcome(
    c: HaloController,
    accessOn: Boolean,
    name: String,
) {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet =
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HText(if (name.isBlank()) greet else "$greet, $name", HaloType.Display, maxLines = 2)
        HText(
            if (accessOn) "Start something in any app — it will wait for you here." else "Let Halo see what's playing, and your shows, films and music line up here, ready to resume.",
            HaloType.Body,
            HaloColor.TextSecondary,
            maxLines = 2,
        )
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!accessOn) {
                PillButton("Turn on Continue Watching", {
                    if (!c.graph.system.openMediaAccess()) c.go(Screen.Settings)
                }, primary = true, icon = Icons.Sparkle)
            }
            PillButton("Browse apps", { c.go(Screen.Library) }, primary = accessOn, icon = Icons.Apps)
        }
    }
}

@Composable
fun AccessCard(
    c: HaloController,
    modifier: Modifier = Modifier,
) {
    HaloFocusable(
        onClick = { if (!c.graph.system.openMediaAccess()) c.go(Screen.Settings) },
        modifier = modifier.width(420.dp).height(HaloSize.cardH),
        fill = Color.White.copy(alpha = 0.06f),
        focusedFill = Color.White.copy(alpha = 0.12f),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).glass(HaloGlass.Thick, HaloRadius.Pill),
                contentAlignment = Alignment.Center,
            ) { HaloIcon(Icons.Sparkle, 16.dp) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                HText("Continue Watching is off", HaloType.Headline)
                HText(
                    "Select to allow notification access for Halo. See Settings › Access if it's hidden.",
                    HaloType.Caption,
                    HaloColor.TextSecondary,
                    maxLines = 2,
                )
            }
        }
    }
}

/** Floating glass dock: All apps · your pinned apps · Halo ring. */
@Composable
private fun Dock(
    c: HaloController,
    apps: List<AppInfo>,
    dock: List<String>,
    filterPkg: String?,
    onFilter: (String) -> Unit,
    firstFocus: FocusRequester,
    modifier: Modifier,
) {
    val pinned = dock.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    val counts =
        c.graph.continueItems
            .collectAsStateWithLifecycle()
            .value
            .groupingBy { it.packageName }
            .eachCount()
    Box(modifier) {
        // Glass sits behind the row (not clipping it) so focused icons and labels can rise above the dock.
        Box(Modifier.matchParentSize().glass(HaloGlass.Regular, HaloRadius.Sheet))
        Row(
            Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            DockButton("All apps", Modifier.focusRequester(firstFocus).takeIf { pinned.isEmpty() } ?: Modifier, { c.go(Screen.Library) }) {
                HaloIcon(Icons.Grid, 18.dp, it, filled = true)
            }
            Box(Modifier.width(0.5.dp).height(30.dp).background(HaloColor.HairlineStrong))
            pinned.forEachIndexed { i, a ->
                val n = counts[a.packageName] ?: 0
                DockApp(
                    a,
                    label = if (n > 0) "${a.label} · $n in progress" else a.label,
                    active = filterPkg == a.packageName,
                    hasProgress = n > 0,
                    modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { c.openApp(a) },
                    onLongClick = { c.appSheet(a) },
                )
            }
            if (pinned.isEmpty()) {
                HText(
                    "Hold OK on any app in Library to pin it here",
                    HaloType.Label,
                    HaloColor.TextTertiary,
                    Modifier.padding(horizontal = 8.dp),
                )
            }
            Box(Modifier.width(0.5.dp).height(30.dp).background(HaloColor.HairlineStrong))
            DockButton("Halo quick ring", Modifier, { c.openRing() }) { tint ->
                Box(Modifier.size(18.dp).glass(HaloGlass.Thick, HaloRadius.Pill), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(9.dp).background(tint, HaloRadius.Pill))
                }
            }
        }
    }
}

@Composable
private fun DockButton(
    desc: String,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable (Color) -> Unit,
) {
    HaloFocusable(
        onClick = onClick,
        modifier = modifier.size(HaloSize.dockIcon),
        shape = HaloRadius.Tile,
        focusedScale = HaloMotion.FocusScaleTile,
        fill = Color.White.copy(alpha = 0.1f),
        focusedFill = Color.White.copy(alpha = 0.94f),
        ring = false,
        description = desc,
    ) { f -> Box(Modifier.align(Alignment.Center)) { icon(if (f) HaloColor.OnLight else HaloColor.Text) } }
}

@Composable
private fun DockApp(
    a: AppInfo,
    label: String,
    active: Boolean,
    hasProgress: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.TopCenter) {
            HaloFocusable(
                onClick = onClick,
                onLongClick = onLongClick,
                onFocusChange = { focused = it },
                modifier = modifier.size(HaloSize.dockIcon),
                shape = HaloRadius.Tile,
                focusedScale = HaloMotion.FocusScaleTile,
                glow = Color.Black,
                description = "$label. Hold OK for options",
            ) {
                val img = a.icon
                if (img != null) AppGlyph(img, HaloSize.dockIcon) else Box(Modifier.fillMaxSize().glass(HaloGlass.Thick, HaloRadius.Tile))
            }
            if (focused) {
                HText(
                    label,
                    HaloType.Label.copy(fontWeight = FontWeight.SemiBold),
                    HaloColor.OnLight,
                    Modifier
                        .floatAbove(
                            12.dp,
                        ).background(Color.White.copy(alpha = 0.95f), HaloRadius.Pill)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
        Box(
            Modifier.padding(top = 4.dp).size(3.dp).background(
                if (active) {
                    Color.White
                } else if (hasProgress) {
                    HaloColor.TextTertiary
                } else {
                    Color.Transparent
                },
                HaloRadius.Pill,
            ),
        )
    }
}
