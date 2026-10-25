package com.halo.launcher.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.halo.launcher.data.ContentHit
import com.halo.launcher.ui.components.AppBadge
import com.halo.launcher.ui.components.PillButton
import com.halo.launcher.ui.components.sheen
import com.halo.launcher.ui.theme.HaloSize
import kotlinx.coroutines.delay
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.launcher.data.AppGroup
import com.halo.launcher.data.AppInfo
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.system.SystemPage
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.AmbientBackground
import com.halo.launcher.ui.components.AppGlyph
import com.halo.launcher.ui.components.AppTile
import com.halo.launcher.ui.components.Chip
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.IconCircle
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.components.timeLeft
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloType

private sealed interface Hit {
    data class Media(
        val e: MediaEntry,
    ) : Hit

    data class Setting(
        val page: SystemPage,
    ) : Hit

    data class Place(
        val label: String,
        val screen: Screen,
    ) : Hit
}

@Composable
fun LibraryScreen(c: HaloController) {
    val g = c.graph
    val apps by g.apps.apps.collectAsStateWithLifecycle()
    val prefs by g.prefs.state.collectAsStateWithLifecycle()
    val media by g.continueItems.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf<AppGroup?>(null) }
    var editing by remember { mutableStateOf(false) }
    val fieldFocus = remember { FocusRequester() }
    val pillFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    val voice =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK) {
                r.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                    ?.let { query = it }
            }
        }

    val q = query.trim().lowercase()
    val shown =
        remember(apps, q, group) {
            apps.filter {
                (group == null || it.group == group) &&
                    (q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q))
            }
        }
    val hits: List<Hit> =
        remember(q, media) {
            if (q.isEmpty()) {
                emptyList()
            } else {
                media.filter { it.title.lowercase().contains(q) || it.subtitle.lowercase().contains(q) }.take(4).map { Hit.Media(it) } +
                    SystemPage.entries.filter { it.label.lowercase().contains(q) }.map { Hit.Setting(it) } +
                    listOf(
                        Hit.Place("Inputs · HDMI", Screen.Inputs),
                        Hit.Place("Halo settings", Screen.Settings),
                        Hit.Place("Watching", Screen.Watching),
                    ).filter { (it.label.lowercase()).contains(q) }
            }
        }

    LaunchedEffect(Unit) { runCatching { pillFocus.requestFocus() } }
    LaunchedEffect(editing) {
        if (editing) {
            runCatching { fieldFocus.requestFocus() }
            keyboard?.show()
        }
    }

    // Search inside apps (films, shows, songs) — debounced so typing stays smooth.
    var found by remember { mutableStateOf<List<ContentHit>>(emptyList()) }
    LaunchedEffect(q) {
        found = emptyList()
        if (q.length >= 2) {
            delay(380)
            found = g.contentSearch.search(q)
        }
    }
    val targets =
        remember(q, apps) {
            if (q.length < 2) {
                emptyList()
            } else {
                g.contentSearch.searchTargets(
                    query.trim(),
                    apps.filter { it.group == AppGroup.Watch || it.group == AppGroup.Listen }.map { it.packageName },
                ).take(6)
            }
        }

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(HaloColor.AmbientDefault, dim = 0.25f)
        Column(Modifier.fillMaxSize()) {
            TopBar(c, Screen.Library, media.size)
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 14.dp)
                    .glass(HaloGlass.Sheet, HaloRadius.Sheet)
                    .padding(horizontal = 22.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ---------- Search ----------
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HaloFocusable(
                        onClick = { editing = true },
                        modifier = Modifier.weight(1f).height(40.dp).focusRequester(pillFocus),
                        shape = HaloRadius.Pill,
                        focusedScale = 1.01f,
                        fill = Color.White.copy(alpha = 0.1f),
                        focusedFill = Color.White.copy(alpha = 0.18f),
                        description = "Search apps, shows, settings and inputs",
                    ) {
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            HaloIcon(Icons.Search, 15.dp)
                            Box(Modifier.weight(1f)) {
                                if (editing) {
                                    BasicTextField(
                                        value = query,
                                        onValueChange = { query = it },
                                        singleLine = true,
                                        textStyle = HaloType.Body.copy(color = HaloColor.Text, fontSize = HaloType.Headline.fontSize),
                                        cursorBrush = SolidColor(Color.White),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions =
                                            KeyboardActions(onSearch = {
                                                keyboard?.hide()
                                                editing = false
                                                runCatching { pillFocus.requestFocus() }
                                            }),
                                        modifier =
                                            Modifier.fillMaxWidth().focusRequester(fieldFocus).onFocusChanged {
                                                if (!it.isFocused &&
                                                    editing
                                                ) {
                                                    editing = false
                                                }
                                            },
                                    )
                                } else {
                                    HText(
                                        query.ifBlank {
                                            "Search apps, shows, settings, inputs…"
                                        },
                                        HaloType.Body.copy(
                                            fontSize = HaloType.Headline.fontSize,
                                        ),
                                        if (query.isBlank()) HaloColor.TextTertiary else HaloColor.Text,
                                    )
                                }
                            }
                            if (query.isNotEmpty()) HText("${shown.size} apps", HaloType.Caption, HaloColor.TextTertiary)
                        }
                    }
                    Box(Modifier.glass(HaloGlass.Regular, HaloRadius.Pill)) {
                        IconCircle(Icons.Mic, "Voice search", {
                            val i =
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say an app, show or setting")
                            runCatching { voice.launch(i) }.onFailure { c.toast("Voice search isn't available on this TV") }
                        }, size = 40.dp)
                    }
                    if (query.isNotEmpty()) {
                        Box(Modifier.glass(HaloGlass.Regular, HaloRadius.Pill)) {
                            IconCircle(Icons.Close, "Clear search", {
                                query =
                                    ""
                            }, size = 40.dp)
                        }
                    }
                }

                // ---------- Categories ----------
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Chip("All", group == null, { group = null })
                    AppGroup.entries.forEach { gr -> Chip(gr.label, group == gr, { group = gr }) }
                    Box(Modifier.weight(1f))
                    HText(if (g.system.usageAccessOn()) "Most used first" else "A–Z", HaloType.Caption, HaloColor.TextTertiary)
                }

                // ---------- Also in Halo ----------
                if (hits.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(hits) { h -> HitChip(c, h) }
                    }
                }

                // ---------- Inside your apps ----------
                if (found.isNotEmpty()) {
                    SectionLabel("In your apps", "Opens the exact title")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp)) {
                        items(found) { hit -> ContentCard(c, hit) }
                    }
                }
                if (targets.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(targets, key = { it.packageName }) { t ->
                            PillButton(
                                "Search in " + c.appName(t.packageName),
                                { c.openIntent(t.intent, t.packageName) },
                                icon = Icons.Search,
                            )
                        }
                    }
                }

                // ---------- Apps ----------
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(shown, key = { it.packageName }) { a ->
                        AppTile(
                            a,
                            onClick = { c.openApp(a) },
                            onLongClick = { g.prefs.togglePin(a.packageName) },
                            pinned =
                                a.packageName in prefs.dock,
                        )
                    }
                    if (shown.isEmpty()) {
                        item(span = { GridItemSpan(6) }) {
                            HText("No apps match “$query”.", HaloType.Body, HaloColor.TextSecondary, Modifier.padding(8.dp))
                        }
                    }
                }

                // ---------- Dock editor ----------
                DockStrip(c, apps, prefs.dock)
            }
        }
    }
}

@Composable
private fun HitChip(
    c: HaloController,
    h: Hit,
) {
    val (kind, label, run) =
        when (h) {
            is Hit.Media -> Triple("Watching", "${h.e.title} · ${timeLeft(h.e)}", { c.play(h.e, false) })
            is Hit.Setting ->
                Triple("Setting", h.page.label, {
                    c.graph.system.open(h.page)
                    Unit
                })
            is Hit.Place -> Triple("Halo", h.label, { c.go(h.screen) })
        }
    HaloFocusable(
        onClick = run,
        modifier = Modifier.height(30.dp),
        shape = HaloRadius.Panel,
        focusedScale = 1.05f,
        fill = Color.White.copy(alpha = 0.08f),
        focusedFill = Color.White.copy(alpha = 0.94f),
        ring = false,
    ) { f ->
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HText(
                kind,
                HaloType.Caption.copy(fontWeight = FontWeight.Bold),
                if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary,
                Modifier
                    .background(
                        if (f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.12f),
                        HaloRadius.Pill,
                    ).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            HText(label, HaloType.Label.copy(fontWeight = FontWeight.Medium), if (f) HaloColor.OnLight else HaloColor.Text)
        }
    }
}

@Composable
private fun DockStrip(
    c: HaloController,
    apps: List<AppInfo>,
    dock: List<String>,
) {
    val pinned = dock.mapNotNull { p -> apps.firstOrNull { it.packageName == p } }
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Color.Black.copy(alpha = 0.2f), HaloRadius.Panel)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.width(190.dp)) {
            HText("Your Dock", HaloType.Headline)
            HText(
                if (pinned.size >=
                    7
                ) {
                    "Full — hold OK on a pinned app to remove it"
                } else {
                    "Hold OK on any app to pin · ${7 - pinned.size} spots left"
                },
                HaloType.Caption,
                HaloColor.TextSecondary,
            )
        }
        pinned.forEach { a -> a.icon?.let { AppGlyph(it, 34.dp) } }
        repeat((7 - pinned.size).coerceAtLeast(0)) {
            Box(Modifier.size(34.dp).glass(HaloGlass.Thin, HaloRadius.Tile))
        }
        Box(Modifier.weight(1f))
    }
}

@Composable
private fun ContentCard(
    c: HaloController,
    hit: ContentHit,
) {
    val app = c.app(hit.packageName)
    Column(Modifier.width(HaloSize.cardW), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        HaloFocusable(
            onClick = { c.openIntent(hit.intent, hit.packageName) },
            modifier = Modifier.fillMaxWidth().height(HaloSize.cardH),
            fill = Color.White.copy(alpha = 0.06f),
            description = "${hit.title}, in ${c.appName(hit.packageName)}",
        ) { f ->
            if (hit.image != null) {
                AsyncImage(model = hit.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().sheen(f))
            } else {
                app?.icon?.let { AppGlyph(it, 30.dp, Modifier.align(Alignment.Center)) }
            }
            AppBadge(app, c.appName(hit.packageName), Modifier.align(Alignment.TopStart).padding(5.dp))
        }
        HText(hit.title, HaloType.Label.copy(fontWeight = FontWeight.SemiBold))
        if (hit.subtitle.isNotBlank()) HText(hit.subtitle, HaloType.Caption, HaloColor.TextTertiary)
    }
}
