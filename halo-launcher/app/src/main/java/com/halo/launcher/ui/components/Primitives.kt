package com.halo.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.halo.launcher.ui.theme.GlassLevel
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloType
import com.halo.launcher.ui.theme.LocalHaloLook
import android.view.KeyEvent as NativeKeyEvent

@Composable
fun HText(
    text: String,
    style: TextStyle = HaloType.Body,
    color: Color = HaloColor.Text,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
) {
    BasicText(text = text, style = style.copy(color = color), modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

/** Frosted glass: translucent fill, lit top edge, faint bottom edge. */
@Composable
fun Modifier.glass(
    level: GlassLevel,
    shape: Shape = HaloRadius.Panel,
): Modifier {
    val boost = LocalHaloLook.current.glassBoost
    val fill = (level.fill + boost).coerceIn(0.02f, 0.4f)
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = fill * 1.25f), Color.White.copy(alpha = fill * 0.75f))))
        .border(
            width = 0.5.dp,
            brush = Brush.verticalGradient(listOf(Color.White.copy(alpha = level.edgeTop), Color.White.copy(alpha = level.edgeBottom))),
            shape = shape,
        )
}

/**
 * The single focus primitive used everywhere: D-pad focusable, OK = click, hold OK = long-press,
 * springy scale + white ring + soft glow when focused. Touch / mouse also work.
 */
@Composable
fun HaloFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onFocus: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    shape: Shape = HaloRadius.Card,
    focusedScale: Float = HaloMotion.FocusScaleCard,
    fill: Color = Color.Transparent,
    focusedFill: Color = fill,
    ring: Boolean = true,
    glow: Color = Color.Black,
    description: String? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val reduce = LocalHaloLook.current.reduceMotion
    val scale by animateFloatAsState(if (focused && !reduce) focusedScale else 1f, HaloMotion.focus(), label = "scale")
    val ringAlpha by animateFloatAsState(if (focused && ring) 1f else 0f, HaloMotion.settle(), label = "ring")
    val bg by animateColorAsState(if (focused) focusedFill else fill, HaloMotion.settle(), label = "fill")
    Box(
        modifier =
            modifier
                .zIndex(if (focused) 1f else 0f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.shadow(elevation = if (focused) 18.dp else 4.dp, shape = shape, ambientColor = glow, spotColor = glow)
                .clip(shape)
                .background(bg)
                .border(2.dp, Color.White.copy(alpha = 0.92f * ringAlpha), shape)
                .semantics {
                    role = Role.Button
                    description?.let { contentDescription = it }
                    this.onClick(label = null) {
                        onClick()
                        true
                    }
                }.onFocusChanged { s ->
                    if (s.isFocused && !focused) onFocus?.invoke()
                    if (s.isFocused != focused) onFocusChange?.invoke(s.isFocused)
                    focused = s.isFocused
                }.onKeyEvent { ev ->
                    val k = ev.nativeKeyEvent
                    val isOk =
                        k.keyCode == NativeKeyEvent.KEYCODE_DPAD_CENTER ||
                            k.keyCode == NativeKeyEvent.KEYCODE_ENTER ||
                            k.keyCode == NativeKeyEvent.KEYCODE_NUMPAD_ENTER ||
                            k.keyCode == NativeKeyEvent.KEYCODE_BUTTON_A
                    if (!isOk) return@onKeyEvent false
                    when (k.action) {
                        NativeKeyEvent.ACTION_DOWN -> {
                            if (k.repeatCount == 0) longFired = false
                            if (k.repeatCount > 0 && onLongClick != null && !longFired) {
                                longFired = true
                                onLongClick()
                            }
                            true
                        }
                        NativeKeyEvent.ACTION_UP -> {
                            if (!longFired) onClick()
                            longFired = false
                            true
                        }
                        else -> false
                    }
                }.focusable()
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick?.invoke() })
                },
        content = { content(focused) },
    )
}

/**
 * A single soft light sweep across a surface the moment it gains focus — the "glass catching
 * light" cue. Runs once per focus, costs one gradient draw.
 */
@Composable
fun Modifier.sheen(focused: Boolean): Modifier {
    val p =
        remember {
            androidx.compose.animation.core
                .Animatable(-1f)
        }
    val reduce = LocalHaloLook.current.reduceMotion
    androidx.compose.runtime.LaunchedEffect(focused) {
        if (focused &&
            !reduce
        ) {
            p.snapTo(-0.4f)
            p.animateTo(
                1.4f,
                androidx.compose.animation.core
                    .tween(900, easing = HaloMotion.EaseOut),
            )
        } else {
            p.snapTo(-1f)
        }
    }
    return this.drawWithContent {
        drawContent()
        val v = p.value
        if (v > -0.5f && v < 1.5f) {
            val w = size.width
            val x = w * v
            drawRect(
                Brush.linearGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.22f),
                    1f to Color.Transparent,
                    start =
                        androidx.compose.ui.geometry
                            .Offset(x - w * 0.35f, 0f),
                    end =
                        androidx.compose.ui.geometry
                            .Offset(x + w * 0.05f, size.height),
                ),
            )
        }
    }
}

/** Places content centred above its anchor without taking any layout space (dock labels, hints). */
fun Modifier.floatAbove(gap: Dp): Modifier =
    this.layout { m, c ->
        val p = m.measure(c.copy(minWidth = 0, maxWidth = androidx.compose.ui.unit.Constraints.Infinity, minHeight = 0))
        layout(0, 0) { p.place(-p.width / 2, -p.height - gap.roundToPx()) }
    }

/** Stroke / fill icon from a 24x24 SVG path string — keeps the icon set tiny and consistent. */
@Composable
fun HaloIcon(
    path: String,
    size: Dp = 14.dp,
    color: Color = HaloColor.Text,
    filled: Boolean = false,
    strokeWidth: Float = 1.8f,
    modifier: Modifier = Modifier,
) {
    val p: Path = remember(path) { PathParser().parsePathString(path).toPath() }
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension / 24f
        scale(s, s, pivot = androidx.compose.ui.geometry.Offset.Zero) {
            if (filled) {
                drawPath(p, color, style = Fill)
            } else {
                drawPath(p, color, style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    track: Color = Color.White.copy(alpha = 0.22f),
    bar: Color = Color.White,
    height: Dp = 2.5.dp,
) {
    val p by animateFloatAsState(progress.coerceIn(0f, 1f), HaloMotion.enter(), label = "progress")
    Box(modifier.height(height).clip(HaloRadius.Pill).background(track)) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(p)
                .clip(HaloRadius.Pill)
                .background(bar),
        )
    }
}

@Composable
fun Toggle(
    on: Boolean,
    modifier: Modifier = Modifier,
) {
    val x by animateFloatAsState(if (on) 1f else 0f, HaloMotion.focus(), label = "knob")
    val track by animateColorAsState(if (on) HaloColor.Positive else Color.White.copy(alpha = 0.22f), label = "track")
    Box(
        modifier
            .width(34.dp)
            .height(20.dp)
            .clip(HaloRadius.Pill)
            .background(track),
    ) {
        Box(
            Modifier
                .padding(2.dp)
                .offset(x = (14 * x).dp)
                .size(16.dp)
                .shadow(2.dp, HaloRadius.Pill)
                .clip(HaloRadius.Pill)
                .background(Color.White)
                .align(Alignment.CenterStart),
        )
    }
}

/** Common icon paths (24-unit viewBox). */
object Icons {
    const val Search = "M11 4a7 7 0 1 0 0 14 7 7 0 1 0 0-14z M20 20l-4.2-4.2"
    const val Inputs = "M3 6h18v11H3z M8 21h8 M10 10l3 1.5-3 1.5z"
    const val Sliders = "M4 7h9 M17 7h3 M15 5v4 M4 12h3 M11 12h9 M9 10v4 M4 17h11 M19 17h1 M17 15v4"
    const val Grid = "M5 5h4v4H5z M10 5h4v4h-4z M15 5h4v4h-4z M5 10h4v4H5z M10 10h4v4h-4z M15 10h4v4h-4z M5 15h4v4H5z M10 15h4v4h-4z M15 15h4v4h-4z"
    const val Play = "M7 4.5v15l13-7.5z"
    const val Pause = "M7 5h3.5v14H7z M13.5 5H17v14h-3.5z"
    const val Plus = "M12 5v14 M5 12h14"
    const val Minus = "M5 12h14"
    const val Back = "M15 5l-7 7 7 7"
    const val Chevron = "M9 5l7 7-7 7"
    const val Check = "M5 12.5l4.5 4.5L19 7"
    const val Close = "M6 6l12 12 M18 6L6 18"
    const val Mic = "M12 3a3 3 0 0 1 3 3v5a3 3 0 0 1-6 0V6a3 3 0 0 1 3-3z M5 11a7 7 0 0 0 14 0 M12 18v3"
    const val List = "M5 7h14 M5 12h14 M5 17h9"
    const val Restart = "M4 12a8 8 0 1 0 2.3-5.7 M4 4v4h4"
    const val Trash = "M5 7h14 M9 7V4h6v3 M7 7l1 13h8l1-13"
    const val Pin = "M12 17v4 M8 3h8l-1 6 3 3H6l3-3z"
    const val Moon = "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"
    const val Volume = "M4 9h4l5-4v14l-5-4H4z M16.5 8.5a5 5 0 0 1 0 7 M19 6a8.5 8.5 0 0 1 0 12"
    const val Mute = "M4 9h4l5-4v14l-5-4H4z M17 9l5 6 M22 9l-5 6"
    const val Picture = "M3 5h18v12H3z M8 21h8 M12 17v4"
    const val Speaker = "M2 10h20v5H2z M6 12.5h.01 M10 12.5h.01 M14 12.5h.01 M18 12.5h.01"
    const val Screensaver = "M3 18h18 M6 14a6 6 0 0 1 12 0 M12 4v2 M4.5 7.5l1.4 1.4 M19.5 7.5l-1.4 1.4"
    const val Wifi = "M2 9a15 15 0 0 1 20 0 M5 12.5a10 10 0 0 1 14 0 M8.5 16a5 5 0 0 1 7 0 M12 19.5h.01"
    const val Remote = "M8 2h8v20H8z M12 6h.01 M10 10h4 M10 13h4"
    const val Shield = "M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6z"
    const val Gear = "M12 8a4 4 0 1 0 0 8 4 4 0 1 0 0-8z M12 2v3 M12 19v3 M2 12h3 M19 12h3 M4.9 4.9l2.1 2.1 M17 17l2.1 2.1 M4.9 19.1L7 17 M17 7l2.1-2.1"
    const val Sparkle = "M12 3l2.2 5.8L20 11l-5.8 2.2L12 19l-2.2-5.8L4 11l5.8-2.2z"
    const val Home = "M3 11l9-7 9 7 M5 10v10h14V10"
    const val Film = "M5 4h14v16H5z M10 9l5 3-5 3z"
    const val Music = "M9 18V5l11-2v13 M9 18a3 3 0 1 1-6 0 3 3 0 0 1 6 0z M20 16a3 3 0 1 1-6 0 3 3 0 0 1 6 0z"
    const val Game = "M6 9h12a4 4 0 0 1 4 4v1a3 3 0 0 1-5.4 1.8L15 14H9l-1.6 1.8A3 3 0 0 1 2 14v-1a4 4 0 0 1 4-4z M7 11v3 M5.5 12.5h3"
    const val Apps = "M4 4h7v7H4z M13 4h7v7h-7z M4 13h7v7H4z M13 13h7v7h-7z"
    const val Clock = "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 7v5l3 2"
    const val Info = "M12 3a9 9 0 1 0 0 18 9 9 0 1 0 0-18z M12 11v5 M12 8h.01"
    const val Hdmi = "M4 8h16v6l-3 3H7l-3-3z M8 11h8"
}
