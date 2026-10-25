package com.halo.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.launcher.Graph
import com.halo.launcher.data.AppInfo
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import com.halo.launcher.resume.ResumeResult
import com.halo.launcher.ui.components.AppGlyph
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.ProgressLine
import com.halo.launcher.ui.components.clock
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.screens.HomeScreen
import com.halo.launcher.ui.screens.InputsScreen
import com.halo.launcher.ui.screens.LibraryScreen
import com.halo.launcher.ui.screens.QuickRing
import com.halo.launcher.ui.screens.SettingsScreen
import com.halo.launcher.ui.screens.WatchingScreen
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloLook
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloType
import com.halo.launcher.ui.theme.LocalHaloLook
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.KeyEvent as NativeKeyEvent

enum class Screen { Home, Watching, Library, Settings, Inputs }

data class SheetAction(
    val label: String,
    val icon: String,
    val run: () -> Unit,
)

data class Sheet(
    val title: String,
    val subtitle: String,
    val actions: List<SheetAction>,
)

/** Everything screens need to act: navigation, resume, sheets. One object, passed down. */
class HaloController(
    val graph: Graph,
    val go: (Screen) -> Unit,
    val play: (MediaEntry, Boolean) -> Unit,
    val openApp: (AppInfo) -> Unit,
    val openIntent: (android.content.Intent, String) -> Unit,
    val showSheet: (Sheet) -> Unit,
    val openRing: () -> Unit,
    val toast: (String) -> Unit,
) {
    fun appName(pkg: String) = graph.apps.label(pkg)

    fun app(pkg: String) = graph.apps.find(pkg)

    fun entrySheet(e: MediaEntry) =
        showSheet(
            Sheet(
                e.title,
                appName(e.packageName),
                listOfNotNull(
                    SheetAction(
                        if (e.kind ==
                            MediaKind.Music
                        ) {
                            "Play"
                        } else {
                            "Resume" +
                                if (e.positionMs >
                                    5000
                                ) {
                                    " at ${clock(e.positionMs)}"
                                } else {
                                    ""
                                }
                        },
                        Icons.Play,
                    ) { play(e, false) },
                    SheetAction("Start over", Icons.Restart) { play(e, true) }.takeIf { e.kind == MediaKind.Video },
                    SheetAction("Open ${appName(e.packageName)}", Icons.Apps) { graph.apps.launch(e.packageName) },
                    SheetAction("Remove from Continue Watching", Icons.Trash) { graph.history.remove(e.id) },
                    SheetAction("Stop showing ${appName(e.packageName)} here", Icons.Close) {
                        graph.prefs.update { it.copy(mutedSources = it.mutedSources + e.packageName) }
                    },
                ),
            ),
        )

    fun appSheet(a: AppInfo) {
        val pinned = a.packageName in graph.prefs.state.value.dock
        showSheet(
            Sheet(
                a.label,
                if (pinned) "In your Dock" else "Not in your Dock",
                listOfNotNull(
                    SheetAction("Open", Icons.Play) { openApp(a) },
                    SheetAction(if (pinned) "Remove from Dock" else "Add to Dock", Icons.Pin) { graph.prefs.togglePin(a.packageName) },
                    SheetAction("Move left", Icons.Back) { graph.prefs.moveInDock(a.packageName, -1) }.takeIf { pinned },
                    SheetAction("Move right", Icons.Chevron) { graph.prefs.moveInDock(a.packageName, 1) }.takeIf { pinned },
                    SheetAction("App info", Icons.Info) {
                        val i =
                            android.content
                                .Intent(
                                    android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    android.net.Uri.parse(
                                        "package:" + a.packageName,
                                    ),
                                ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { appContext?.startActivity(i) }
                    },
                ),
            ),
        )
    }

    var appContext: android.content.Context? = null
}

@Composable
fun HaloRoot(
    graph: Graph,
    resumeTick: Int,
    homeTick: Int,
    context: android.content.Context,
) {
    val prefs by graph.prefs.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf(Screen.Home) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var ring by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<Pair<MediaEntry, Boolean>?>(null) }
    var openingApp by remember { mutableStateOf<AppInfo?>(null) }
    var openingIntent by remember { mutableStateOf<android.content.Intent?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Pressing Home while Halo is open returns to the Home screen.
    LaunchedEffect(homeTick) {
        if (homeTick > 0) {
            screen = Screen.Home
            sheet = null
            ring = false
        }
    }
    // Coming back from an app: clear the hand-off overlay and refresh system data.
    LaunchedEffect(resumeTick) {
        opening = null
        openingApp = null
        graph.watchNext.refresh()
        graph.apps.refreshRecent()
        graph.seedDockIfNeeded()
    }
    val apps by graph.apps.apps.collectAsStateWithLifecycle()
    LaunchedEffect(apps.size) { graph.seedDockIfNeeded() }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(3200)
            toast = null
        }
    }

    val controller =
        remember(graph) {
            HaloController(
                graph = graph,
                go = { screen = it },
                play = { e, fromStart -> opening = e to fromStart },
                openApp = { a -> openingApp = a; openingIntent = null },
            openIntent = { i, pkg -> openingIntent = i; openingApp = graph.apps.find(pkg) ?: AppInfo(pkg, graph.apps.label(pkg), android.content.ComponentName(pkg, ""), null, null, com.halo.launcher.data.AppGroup.Other, false) },
                showSheet = { sheet = it },
                openRing = { ring = true },
                toast = { toast = it },
            ).also { it.appContext = context.applicationContext }
        }

    // The hand-off: show the app icon zoom, then really open the app at the item.
    LaunchedEffect(opening) {
        val (e, fromStart) = opening ?: return@LaunchedEffect
        delay(650)
        val r = graph.resume.resume(e, fromStart)
        when (r) {
            ResumeResult.Failed -> {
                opening = null
                toast = "Couldn't open ${graph.apps.label(e.packageName)}"
            }
            ResumeResult.AppOnly -> toast = "${graph.apps.label(e.packageName)} opened — it doesn't let other apps jump straight to a title"
            ResumeResult.Search -> toast = "${graph.apps.label(e.packageName)} is finding “${e.title}” for you"
            else -> Unit
        }
        delay(4000)
        opening = null
    }
    LaunchedEffect(openingApp) {
        val a = openingApp ?: return@LaunchedEffect
        delay(450)
        val intent = openingIntent
        val ok = if (intent != null) runCatching { context.startActivity(intent); true }.getOrDefault(false) else graph.apps.launch(a.packageName)
        if (!ok) toast = "Couldn't open ${a.label}"
        delay(3000)
        openingApp = null
    }

    BackHandler(enabled = sheet == null && !ring && opening == null) { if (screen != Screen.Home) screen = Screen.Home }

    val look = HaloLook(glassBoost = prefs.glassBoost, ambientMotion = prefs.ambientMotion, reduceMotion = prefs.reduceMotion)
    CompositionLocalProvider(LocalHaloLook provides look) {
        Box(
            Modifier
                .fillMaxSize()
                .background(HaloColor.Ink)
                .onPreviewKeyEvent { ev ->
                    val k = ev.nativeKeyEvent
                    if (k.action != NativeKeyEvent.ACTION_UP) return@onPreviewKeyEvent false
                    when (k.keyCode) {
                        NativeKeyEvent.KEYCODE_MENU, NativeKeyEvent.KEYCODE_BOOKMARK -> {
                            if (sheet == null) ring = !ring
                            true
                        }
                        NativeKeyEvent.KEYCODE_SEARCH -> {
                            screen = Screen.Library
                            true
                        }
                        NativeKeyEvent.KEYCODE_TV_INPUT -> {
                            screen = Screen.Inputs
                            true
                        }
                        else -> false
                    }
                },
        ) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (
                        fadeIn(tween(HaloMotion.Standard)) +
                            scaleIn(tween(HaloMotion.Emphasized, easing = HaloMotion.EaseOut), initialScale = 0.97f)
                    ) togetherWith
                        (fadeOut(tween(HaloMotion.Fast)) + scaleOut(tween(HaloMotion.Standard), targetScale = 1.02f))
                },
                label = "screen",
            ) { s ->
                when (s) {
                    Screen.Home -> HomeScreen(controller, resumeTick)
                    Screen.Watching -> WatchingScreen(controller)
                    Screen.Library -> LibraryScreen(controller)
                    Screen.Settings -> SettingsScreen(controller, resumeTick)
                    Screen.Inputs -> InputsScreen(controller)
                }
            }

            AnimatedVisibility(
                ring,
                enter = fadeIn(tween(HaloMotion.Standard)) + scaleIn(initialScale = 0.9f),
                exit =
                    fadeOut() + scaleOut(targetScale = 0.95f),
            ) {
                QuickRing(controller, onClose = { ring = false })
            }

            sheet?.let { sh -> ActionSheet(sh, onDismiss = { sheet = null }) }

            AnimatedVisibility(opening != null || openingApp != null, enter = fadeIn(tween(240)), exit = fadeOut(tween(400))) {
                val e = opening?.first
                val a = openingApp ?: e?.let { graph.apps.find(it.packageName) }
                HandOff(
                    app = a,
                    appName = a?.label ?: e?.let { graph.apps.label(it.packageName) } ?: "",
                    line =
                        when {
                            e == null -> "Opening"
                            opening?.second == true -> "Starting ${e.title} from the beginning"
                            e.kind == MediaKind.Music -> "Playing ${e.title}"
                            e.positionMs > 5000 -> "Resuming ${e.title} at ${clock(e.positionMs)}"
                            else -> "Opening ${e.title}"
                        },
                )
            }

            AnimatedVisibility(
                toast != null,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                Box(
                    Modifier
                        .glass(
                            HaloGlass.Thick,
                            HaloRadius.Pill,
                        ).background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    HText(toast.orEmpty(), HaloType.Label.copy(fontSize = HaloType.Body.fontSize))
                }
            }
        }
    }
}

/** Full-screen "opening the app" moment: the app icon blooms, a thin loader runs. */
@Composable
private fun HandOff(
    app: AppInfo?,
    appName: String,
    line: String,
) {
    val zoom = remember { Animatable(0.4f) }
    val bar = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { zoom.animateTo(1f, HaloMotion.focus()) }
        bar.animateTo(1f, tween(1400, easing = HaloMotion.EaseOut))
    }
    Box(Modifier.fillMaxSize().background(HaloColor.Ink), contentAlignment = Alignment.Center) {
        Box(Modifier.size(460.dp).background(Brush.radialGradient(listOf(Color(0x553B6BFF), Color.Transparent))))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(90.dp).graphicsLayer {
                    scaleX = zoom.value
                    scaleY = zoom.value
                    alpha = zoom.value.coerceIn(0f, 1f)
                },
            ) {
                if (app?.icon != null) {
                    AppGlyph(app.icon, 90.dp)
                } else {
                    Box(Modifier.fillMaxSize().glass(HaloGlass.Thick, HaloRadius.Sheet))
                }
            }
            HText("Opening $appName", HaloType.Title2)
            HText(line, HaloType.Body, HaloColor.TextSecondary)
            ProgressLine(bar.value, Modifier.width(130.dp), height = 2.dp)
        }
    }
}

@Composable
private fun ActionSheet(
    sheet: Sheet,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    BackHandler { onDismiss() }
    LaunchedEffect(sheet) { runCatching { first.requestFocus() } }
    Box(Modifier.fillMaxSize().background(HaloColor.Scrim.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .width(340.dp)
                .glass(HaloGlass.Thick, HaloRadius.Sheet)
                .background(Color(0x99141620))
                .padding(18.dp)
                .focusGroup(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HText(sheet.title, HaloType.Title2)
            HText(sheet.subtitle, HaloType.Label, HaloColor.TextSecondary, Modifier.padding(bottom = 8.dp))
            sheet.actions.forEachIndexed { i, a ->
                HaloFocusable(
                    onClick = {
                        onDismiss()
                        a.run()
                    },
                    modifier = Modifier.fillMaxWidth().height(36.dp).then(if (i == 0) Modifier.focusRequester(first) else Modifier),
                    shape = HaloRadius.Panel,
                    focusedScale = 1.03f,
                    ring = false,
                    fill = Color.White.copy(alpha = 0.04f),
                    focusedFill = Color.White.copy(alpha = 0.94f),
                ) { f ->
                    Row(
                        Modifier.align(Alignment.CenterStart).padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        HaloIcon(a.icon, 13.dp, if (f) HaloColor.OnLight else HaloColor.Text, filled = a.icon == Icons.Play)
                        HText(
                            a.label,
                            HaloType.Headline.copy(fontSize = HaloType.Body.fontSize),
                            if (f) HaloColor.OnLight else HaloColor.Text,
                        )
                    }
                }
            }
        }
    }
}
