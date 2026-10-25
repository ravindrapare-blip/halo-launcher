package com.halo.launcher.ui.components

import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.halo.launcher.data.AppInfo
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSize
import com.halo.launcher.ui.theme.HaloType
import com.halo.launcher.ui.theme.LocalHaloLook
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

fun MediaEntry.artModel(): Any? = artPath?.let { if (it.startsWith("/")) File(it) else it }

/** Three colours pulled from artwork (vibrant / dark / muted); falls back to the default palette. */
@Composable
fun rememberArtPalette(model: Any?): List<Color> {
    val context = LocalContext.current
    var colors by remember(model) { mutableStateOf(HaloColor.AmbientDefault) }
    LaunchedEffect(model) {
        if (model == null) return@LaunchedEffect
        val req =
            ImageRequest
                .Builder(context)
                .data(model)
                .allowHardware(false)
                .size(96)
                .build()
        val bmp =
            (context.imageLoader.execute(req) as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap } ?: return@LaunchedEffect
        val p = Palette.from(bmp).maximumColorCount(12).generate()

        fun c(sw: Palette.Swatch?) = sw?.let { Color(it.rgb) }
        val a = c(p.vibrantSwatch) ?: c(p.dominantSwatch) ?: return@LaunchedEffect
        val b = c(p.darkVibrantSwatch) ?: c(p.darkMutedSwatch) ?: a.copy(alpha = 1f)
        val d = c(p.lightVibrantSwatch) ?: c(p.mutedSwatch) ?: a
        colors = listOf(a, b, d)
    }
    return colors
}

/** Slow-drifting light blobs behind everything; drawn in one Canvas so it costs almost nothing. */
@Composable
fun AmbientBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    dim: Float = 0f,
) {
    val look = LocalHaloLook.current
    val c0 by animateColorAsState(colors.getOrElse(0) { HaloColor.AmbientDefault[0] }, HaloMotion.color(), label = "a0")
    val c1 by animateColorAsState(colors.getOrElse(1) { HaloColor.AmbientDefault[1] }, HaloMotion.color(), label = "a1")
    val c2 by animateColorAsState(colors.getOrElse(2) { HaloColor.AmbientDefault[2] }, HaloMotion.color(), label = "a2")
    val t = rememberInfiniteTransition(label = "ambient")
    val phase by if (look.ambientMotion && !look.reduceMotion) {
        t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(24_000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    } else {
        remember { mutableStateOf(0.8f) }
    }
    Canvas(modifier.fillMaxSize().background(HaloColor.Ink)) {
        val w = size.width
        val h = size.height

        fun blob(
            color: Color,
            cx: Float,
            cy: Float,
            r: Float,
            alpha: Float,
        ) {
            drawCircle(
                Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)), center = Offset(cx, cy), radius = r),
                r,
                Offset(cx, cy),
            )
        }
        blob(c0, w * (0.78f + 0.05f * cos(phase)), h * (0.18f + 0.06f * sin(phase)), w * 0.55f, 0.85f)
        blob(c1, w * (0.12f + 0.06f * sin(phase * 1.3f)), h * (0.95f + 0.05f * cos(phase)), w * 0.5f, 0.7f)
        blob(c2, w * (0.86f + 0.04f * sin(phase * 0.7f)), h * (0.86f + 0.05f * cos(phase * 1.1f)), w * 0.32f, 0.55f)
        drawRect(
            Brush.horizontalGradient(
                0f to HaloColor.Ink.copy(alpha = 0.92f),
                0.42f to HaloColor.Ink.copy(alpha = 0.5f),
                0.72f to Color.Transparent,
            ),
        )
        drawRect(Brush.verticalGradient(0.4f to Color.Transparent, 1f to HaloColor.Ink.copy(alpha = 0.95f)))
        if (dim > 0f) drawRect(HaloColor.Ink.copy(alpha = dim))
    }
}

/** Artwork with a graceful generated fallback (gradient + app icon) when an app gives none. */
@Composable
fun Artwork(
    entry: MediaEntry,
    app: AppInfo?,
    modifier: Modifier = Modifier,
    blurred: Boolean = false,
    iconSize: Dp = 28.dp,
) {
    val model = entry.artModel()
    // Cheap fallback gradient (no palette work per card — that only runs for the hero).
    val pal = HaloColor.AmbientDefault
    Box(modifier.background(Brush.linearGradient(listOf(pal[0].copy(alpha = 0.55f), pal[1].copy(alpha = 0.8f), HaloColor.InkRaised)))) {
        if (model == null) {
            app?.icon?.let { AppGlyph(it, iconSize, Modifier.align(Alignment.Center)) }
        } else {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().then(if (blurred && Build.VERSION.SDK_INT >= 31) Modifier.blur(18.dp) else Modifier),
            )
            if (blurred && Build.VERSION.SDK_INT < 31) Box(Modifier.fillMaxSize().background(HaloColor.Ink.copy(alpha = 0.75f)))
        }
    }
}

@Composable
fun AppGlyph(
    icon: ImageBitmap,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(icon, null, modifier.size(size).clip(HaloRadius.Tile), contentScale = ContentScale.Fit, filterQuality = FilterQuality.High)
}

/** App badge used on cards: small icon + name on a dark glass chip. */
@Composable
fun AppBadge(
    app: AppInfo?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(
                HaloRadius.Pill,
            ).background(Color.Black.copy(alpha = 0.42f))
            .padding(start = 3.dp, end = 7.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        app?.icon?.let { AppGlyph(it, 12.dp) }
        HText(name, HaloType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
    }
}

fun timeLeft(e: MediaEntry): String =
    when {
        e.kind == com.halo.launcher.data.MediaKind.Music -> e.subtitle.ifBlank { "Continue listening" }
        e.durationMs <= 0 -> "Continue"
        else -> {
            val m = (e.remainingMs / 60_000).toInt()
            if (m >= 60) {
                "${m / 60}h ${m % 60}m left"
            } else if (m < 1) {
                "Almost done"
            } else {
                "$m min left"
            }
        }
    }

fun clock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val x = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, x) else "%d:%02d".format(m, x)
}

fun ago(t: Long): String {
    val d = System.currentTimeMillis() - t
    val h = d / 3_600_000
    return when {
        d < 60 * 60_000 -> "Just now"
        h < 24 -> "${h}h ago"
        h < 48 -> "Yesterday"
        h < 24 * 7 -> "${h / 24} days ago"
        else -> "Earlier"
    }
}

/** Continue-watching card: artwork, app badge, title, progress, time left. */
@Composable
fun MediaCard(
    entry: MediaEntry,
    app: AppInfo?,
    appName: String,
    spoilerSafe: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = HaloSize.cardW,
) {
    HaloFocusable(
        onClick = onClick,
        onLongClick = onLongClick,
        onFocus = onFocus,
        modifier = modifier.width(width).height(width * 0.5625f),
        description = "${entry.title}, $appName, ${timeLeft(entry)}",
    ) { focused ->
        Artwork(entry, app, Modifier.fillMaxSize().sheen(focused), blurred = spoilerSafe && entry.episodeLabel != null)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.78f))))
        AppBadge(app, appName, Modifier.align(Alignment.TopStart).padding(6.dp))
        if (focused) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(30.dp)
                    .clip(HaloRadius.Pill)
                    .background(Color.White.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) { HaloIcon(Icons.Play, 13.dp, HaloColor.OnLight, filled = true) }
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            HText(entry.title, HaloType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (entry.durationMs > 0) ProgressLine(entry.progress, Modifier.weight(1f), height = 2.dp)
                HText(timeLeft(entry), HaloType.Caption, HaloColor.TextSecondary)
            }
        }
    }
}

/** App tile: banner (16:9) for TV apps, otherwise icon on a glass squircle. */
@Composable
fun AppTile(
    app: AppInfo,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    pinned: Boolean = false,
    showLabel: Boolean = true,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HaloFocusable(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            shape = HaloRadius.Card,
            focusedScale = 1.1f,
            fill = Color.White.copy(alpha = 0.07f),
            focusedFill = Color.White.copy(alpha = 0.14f),
            description = app.label + if (pinned) ", in Dock" else "",
        ) {
            val banner = app.banner
            if (banner != null) {
                Image(banner, null, Modifier.fillMaxSize().sheen(it), contentScale = ContentScale.Crop)
            } else {
                app.icon?.let { AppGlyph(it, 34.dp, Modifier.align(Alignment.Center)) }
            }
            if (pinned) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(HaloRadius.Pill)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) { HaloIcon(Icons.Check, 10.dp, HaloColor.OnLight, strokeWidth = 3f) }
            }
        }
        if (showLabel) HText(app.label, HaloType.Label, HaloColor.TextSecondary)
    }
}

/** Primary (white) or glass pill button. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    primary: Boolean = false,
    trailing: String? = null,
    iconFilled: Boolean = false,
    onFocus: (() -> Unit)? = null,
) {
    HaloFocusable(
        onClick = onClick,
        onFocus = onFocus,
        modifier = modifier.height(HaloSize.button),
        shape = HaloRadius.Pill,
        focusedScale = HaloMotion.FocusScaleButton,
        fill = if (primary) Color.White else Color.White.copy(alpha = 0.12f),
        focusedFill = if (primary) Color.White else Color.White.copy(alpha = 0.9f),
        ring = !primary,
        glow = Color.Black,
    ) { focused ->
        val fg = if (primary || focused) HaloColor.OnLight else HaloColor.Text
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            icon?.let { HaloIcon(it, 12.dp, fg, filled = iconFilled, strokeWidth = 2.2f) }
            HText(text, HaloType.Headline.copy(fontSize = HaloType.Label.fontSize * 1.1f), fg)
            trailing?.let { HText(it, HaloType.Label, if (primary || focused) HaloColor.OnLightSecondary else HaloColor.TextTertiary) }
        }
    }
}

@Composable
fun IconCircle(
    icon: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = HaloSize.iconButton,
) {
    HaloFocusable(
        onClick = onClick,
        modifier = modifier.size(size),
        shape = HaloRadius.Pill,
        focusedScale = 1.12f,
        fill = Color.Transparent,
        focusedFill = Color.White.copy(alpha = 0.92f),
        ring = false,
        description = description,
    ) { focused ->
        HaloIcon(
            icon,
            size * 0.46f,
            if (focused) HaloColor.OnLight else HaloColor.Text,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
fun Chip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HaloFocusable(
        onClick = onClick,
        modifier = modifier.height(HaloSize.chip),
        shape = HaloRadius.Pill,
        focusedScale = 1.06f,
        fill = if (selected) Color.White.copy(alpha = 0.94f) else Color.White.copy(alpha = 0.07f),
        focusedFill = if (selected) Color.White else Color.White.copy(alpha = 0.2f),
    ) {
        HText(
            text,
            HaloType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            if (selected) HaloColor.OnLight else HaloColor.Text,
            Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
        )
    }
}
