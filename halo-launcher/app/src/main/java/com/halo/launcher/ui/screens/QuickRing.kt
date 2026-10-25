package com.halo.launcher.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.launcher.service.SessionHub
import com.halo.launcher.system.SystemPage
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloType
import kotlin.math.cos
import kotlin.math.sin
import android.view.KeyEvent as NativeKeyEvent

private data class Node(
    val name: String,
    val icon: String,
)

private val NODES =
    listOf(
        Node("Volume", Icons.Volume),
        Node("Now Playing", Icons.Play),
        Node("Sleep Timer", Icons.Moon),
        Node("Picture", Icons.Picture),
        Node("Sound Out", Icons.Speaker),
        Node("Inputs", Icons.Inputs),
        Node("Screen Saver", Icons.Screensaver),
        Node("Mute", Icons.Mute),
    )
private val SLEEP = listOf(0, 15, 30, 60, 90, 120)

/**
 * Halo quick ring — a radial control centre. ◀ ▶ rotate, ▲ ▼ adjust, OK does it, Back closes.
 * Open it with the Menu key or the ring button on the dock.
 */
@Composable
fun QuickRing(
    c: HaloController,
    onClose: () -> Unit,
) {
    val sys = c.graph.system
    var idx by remember { mutableIntStateOf(0) }
    var tick by remember { mutableIntStateOf(0) } // forces re-read of volume after changes
    val now by SessionHub.now.collectAsStateWithLifecycle()
    val sleepAt by sys.sleepAt.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    BackHandler { onClose() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val sleepMin = sleepAt?.let { ((it - System.currentTimeMillis()) / 60_000).toInt() + 1 } ?: 0

    fun adjust(dir: Int) {
        when (idx) {
            0 -> sys.volumeStep(dir > 0)
            2 -> {
                val cur = SLEEP.indexOfFirst { it >= sleepMin }.coerceAtLeast(0)
                sys.setSleepTimer(SLEEP[(cur + dir).coerceIn(0, SLEEP.lastIndex)])
            }
        }
        tick++
    }

    fun primary() {
        when (idx) {
            0 -> sys.volumeStep(true)
            1 -> if (!SessionHub.playPauseActive()) c.toast("Nothing is playing")
            2 -> adjust(1)
            3 -> {
                onClose()
                sys.open(SystemPage.Picture)
            }
            4 -> {
                onClose()
                sys.open(SystemPage.Sound)
            }
            5 -> {
                onClose()
                c.go(Screen.Inputs)
            }
            6 -> {
                onClose()
                sys.startScreensaver()
            }
            7 -> sys.toggleMute()
        }
        tick++
    }

    val vol = remember(tick, idx) { sys.volume }
    val maxVol = remember { sys.maxVolume.coerceAtLeast(1) }
    val muted = remember(tick, idx) { sys.isMuted }
    val (value, pct) =
        when (idx) {
            0 -> "$vol" to vol.toFloat() / maxVol
            1 -> (now?.let { if (it.playing) "Pause" else "Play" } ?: "—") to (if (now?.playing == true) 1f else 0f)
            2 -> (if (sleepMin <= 0) "Off" else "$sleepMin min") to (sleepMin / 120f)
            7 -> (if (muted) "Muted" else "On") to (if (muted) 0f else 1f)
            else -> "Open" to 0f
        }
    val sub =
        when (idx) {
            1 -> now?.let { it.title.ifBlank { c.appName(it.packageName) } } ?: "No media session"
            0, 2 -> "▲ ▼ to adjust"
            else -> "OK to open"
        }
    val arc by animateFloatAsState(pct.coerceIn(0f, 1f), HaloMotion.focus(), label = "arc")

    Box(
        Modifier
            .fillMaxSize()
            .background(HaloColor.Scrim.copy(alpha = 0.72f))
            .focusRequester(focus)
            .onKeyEvent { ev ->
                val k = ev.nativeKeyEvent
                if (k.action != NativeKeyEvent.ACTION_DOWN) return@onKeyEvent k.keyCode in RING_KEYS
                when (k.keyCode) {
                    NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                        idx = (idx + 1) % NODES.size
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                        idx = (idx + NODES.size - 1) % NODES.size
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_UP -> {
                        adjust(1)
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                        adjust(-1)
                        true
                    }
                    NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER -> {
                        primary()
                        true
                    }
                    else -> false
                }
            }.focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(440.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(380.dp).glass(HaloGlass.Thin, HaloRadius.Pill))
            // Centre dial with value arc.
            Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val sw = 5.dp.toPx()
                    drawArc(
                        Color.White.copy(alpha = 0.14f),
                        -90f,
                        360f,
                        false,
                        Offset(sw / 2, sw / 2),
                        Size(
                            size.width - sw,
                            size.height - sw,
                        ),
                        style = Stroke(sw),
                    )
                    drawArc(
                        Color.White,
                        -90f,
                        360f * arc,
                        false,
                        Offset(sw / 2, sw / 2),
                        Size(size.width - sw, size.height - sw),
                        style = Stroke(sw, cap = StrokeCap.Round),
                    )
                }
                Column(
                    Modifier.size(180.dp).glass(HaloGlass.Thick, HaloRadius.Pill).background(Color(0xB30E0F16)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    HaloIcon(NODES[idx].icon, 20.dp, HaloColor.TextSecondary)
                    HText(NODES[idx].name, HaloType.Label, HaloColor.TextSecondary, Modifier.padding(top = 4.dp))
                    HText(
                        value,
                        HaloType.Title1.copy(fontWeight = FontWeight.Light, textAlign = TextAlign.Center),
                        modifier = Modifier.width(150.dp),
                    )
                    HText(sub, HaloType.Caption, HaloColor.TextTertiary, Modifier.width(140.dp).padding(top = 4.dp), maxLines = 2)
                }
            }
            NODES.forEachIndexed { i, n ->
                val a = Math.toRadians(i * 45.0 - 90.0)
                val r = 168f
                val on = i == idx
                val s by animateFloatAsState(if (on) 1.18f else 1f, HaloMotion.focus(), label = "node$i")
                val bg by animateColorAsState(if (on) Color.White.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.1f), label = "bg$i")
                Column(
                    Modifier
                        .offset((r * cos(a)).toFloat().dp, (r * sin(a)).toFloat().dp)
                        .pointerInput(i) {
                            detectTapGestures {
                                idx = i
                                primary()
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(54.dp)
                            .graphicsLayer {
                                scaleX = s
                                scaleY = s
                            }.background(bg, HaloRadius.Pill),
                        contentAlignment = Alignment.Center,
                    ) { HaloIcon(n.icon, 22.dp, if (on) HaloColor.OnLight else HaloColor.Text, filled = n.icon == Icons.Play) }
                }
            }
        }
        // Labels sit outside the ring so the circle stays clean.
        HText(
            "◀ ▶ choose · ▲ ▼ adjust · OK select · Back close",
            HaloType.Caption,
            HaloColor.TextTertiary,
            Modifier.align(Alignment.BottomCenter).padding(bottom = 26.dp),
        )
        HText(NODES[idx].name, HaloType.Title2, modifier = Modifier.align(Alignment.TopCenter).padding(top = 30.dp))
    }
}

private val RING_KEYS =
    setOf(
        NativeKeyEvent.KEYCODE_DPAD_RIGHT,
        NativeKeyEvent.KEYCODE_DPAD_LEFT,
        NativeKeyEvent.KEYCODE_DPAD_UP,
        NativeKeyEvent.KEYCODE_DPAD_DOWN,
        NativeKeyEvent.KEYCODE_DPAD_CENTER,
        NativeKeyEvent.KEYCODE_ENTER,
    )
