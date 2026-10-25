package com.halo.launcher.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Halo design tokens. Everything visual reads from here — change a value once and the
 * whole launcher follows. Sizes assume the standard TV canvas of 960 x 540 dp (1080p @ 2x).
 */
object HaloColor {
    val Ink = Color(0xFF04050A)
    val InkRaised = Color(0xFF0B0C12)
    val Text = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xB3FFFFFF) // 70%
    val TextTertiary = Color(0x8CFFFFFF) // 55%
    val OnLight = Color(0xFF07080C)
    val OnLightSecondary = Color(0x8C07080C)
    val Focus = Color(0xF0FFFFFF) // focused pill / selected row fill
    val Hairline = Color(0x29FFFFFF) // 16%
    val HairlineStrong = Color(0x3DFFFFFF) // 24%
    val Positive = Color(0xFF34C77B)
    val Live = Color(0xFF5CF2A6)
    val Scrim = Color(0xE604050A)

    /** Default ambient palette used before any artwork is known. */
    val AmbientDefault = listOf(Color(0xFF2B5BFF), Color(0xFF6A2BFF), Color(0xFF3FD0FF))
}

/** Glass levels: fill alpha of the white layer over the ambient background. */
@Immutable
data class GlassLevel(
    val fill: Float,
    val edgeTop: Float,
    val edgeBottom: Float,
)

object HaloGlass {
    val Thin = GlassLevel(fill = 0.06f, edgeTop = 0.22f, edgeBottom = 0.05f)
    val Regular = GlassLevel(fill = 0.10f, edgeTop = 0.30f, edgeBottom = 0.06f)
    val Thick = GlassLevel(fill = 0.16f, edgeTop = 0.36f, edgeBottom = 0.08f)
    val Sheet = GlassLevel(fill = 0.09f, edgeTop = 0.30f, edgeBottom = 0.06f)
}

object HaloRadius {
    val xs = 6.dp
    val s = 10.dp
    val m = 14.dp
    val tile = 13.dp // app squircle at 48dp
    val card = 11.dp
    val l = 20.dp
    val xl = 28.dp
    val pill = 999.dp
    val Card = RoundedCornerShape(card)
    val Tile = RoundedCornerShape(tile)
    val Panel = RoundedCornerShape(l)
    val Sheet = RoundedCornerShape(xl)
    val Pill = RoundedCornerShape(pill)
}

object HaloSpace {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val s: Dp = 8.dp
    val m: Dp = 12.dp
    val l: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp
    val gutter: Dp = 60.dp // screen side margin (TV overscan safe)
    val top: Dp = 26.dp
}

object HaloSize {
    val dockIcon = 44.dp
    val cardW = 150.dp
    val cardH = 84.dp
    val button = 36.dp
    val chip = 24.dp
    val row = 44.dp
    val iconButton = 28.dp
}

private val Family = FontFamily.Default

object HaloType {
    val Display =
        TextStyle(fontFamily = Family, fontSize = 56.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.045).em, lineHeight = 54.sp)
    val Title1 = TextStyle(fontFamily = Family, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.035).em)
    val Title2 = TextStyle(fontFamily = Family, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.02).em)
    val Clock = TextStyle(fontFamily = Family, fontSize = 30.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = (-0.04).em)
    val Headline = TextStyle(fontFamily = Family, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.01).em)
    val Body = TextStyle(fontFamily = Family, fontSize = 12.sp, fontWeight = FontWeight.Normal, lineHeight = 17.sp)
    val Label = TextStyle(fontFamily = Family, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontFamily = Family, fontSize = 8.sp, fontWeight = FontWeight.Medium)
}

object HaloMotion {
    /** Apple-like "ease out quint" for entering content. */
    val EaseOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val Fast = 180
    val Standard = 320
    val Emphasized = 520
    val Ambient = 1400

    fun <T> focus() = spring<T>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

    fun <T> settle() = spring<T>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow)

    fun <T> enter() = tween<T>(Emphasized, easing = EaseOut)

    fun <T> color() = tween<T>(Ambient, easing = EaseOut)

    const val FocusScaleCard = 1.08f
    const val FocusScaleTile = 1.18f
    const val FocusScaleButton = 1.05f
}

/** User-tunable look, fed from Settings. */
@Immutable
data class HaloLook(
    val glassBoost: Float = 0f, // -0.04 .. +0.08 added to every glass fill
    val ambientMotion: Boolean = true,
    val reduceMotion: Boolean = false,
)

val LocalHaloLook = staticCompositionLocalOf { HaloLook() }
