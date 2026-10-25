package com.halo.launcher.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.halo.launcher.data.TvInputItem
import com.halo.launcher.system.SystemPage
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.AmbientBackground
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.HaloIcon
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.PillButton
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloMotion
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSpace
import com.halo.launcher.ui.theme.HaloType

private val TINTS =
    listOf(
        Color(0xFF20E3B2) to Color(0xFF1B54E0),
        Color(0xFFFF7A45) to Color(0xFFB5244F),
        Color(0xFFC77DFF) to Color(0xFF5A189A),
        Color(0xFFFF4D8D) to Color(0xFF7A1FD1),
        Color(0xFF6AB8FF) to Color(0xFF2A4A9F),
        Color(0xFFFFC24D) to Color(0xFFE5572B),
    )

@Composable
fun InputsScreen(c: HaloController) {
    val inputs = remember { c.graph.inputs.inputs() }
    var sel by remember { mutableStateOf(0) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    val tint = TINTS[sel % TINTS.size]

    Box(Modifier.fillMaxSize()) {
        AmbientBackground(listOf(tint.first, tint.second, tint.first), dim = 0.15f)
        Column(
            Modifier.fillMaxSize().padding(horizontal = HaloSpace.gutter, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    ScreenTitle(
                        "Inputs",
                        if (inputs.isEmpty()) "No inputs shared by this TV" else "${inputs.size} sources",
                    ) { c.go(Screen.Home) }
                }
                PillButton("Input & CEC settings", { c.graph.system.open(SystemPage.All) }, icon = Icons.Sliders)
            }
            if (inputs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(300.dp).glass(HaloGlass.Thin, HaloRadius.Sheet), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HaloIcon(Icons.Hdmi, 40.dp)
                        HText("This device didn't list any HDMI inputs", HaloType.Title2)
                        HText(
                            "Streaming sticks have none; on TVs, inputs appear here automatically.",
                            HaloType.Body,
                            HaloColor.TextSecondary,
                        )
                        PillButton("Back to Home", { c.go(Screen.Home) }, Modifier.focusRequester(first), primary = true)
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    AnimatedContent(sel, transitionSpec = {
                        fadeIn(tween(HaloMotion.Emphasized)) togetherWith
                            fadeOut(tween(HaloMotion.Fast))
                    }, label = "inputPreview") { i ->
                        Preview(
                            inputs[i],
                            TINTS[i % TINTS.size],
                        ) { if (!c.graph.inputs.switchTo(inputs[i])) c.toast("This TV didn't accept the switch") }
                    }
                    LazyColumn(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(4.dp),
                    ) {
                        itemsIndexed(inputs, key = { _, x -> x.id }) { i, inp ->
                            InputRow(
                                inp,
                                TINTS[i % TINTS.size],
                                selected = i == sel,
                                onFocus = { sel = i },
                                onClick = { if (!c.graph.inputs.switchTo(inp)) c.toast("This TV didn't accept the switch") },
                                modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Preview(
    inp: TvInputItem,
    tint: Pair<Color, Color>,
    onSwitch: () -> Unit,
) {
    val t = rememberInfiniteTransition(label = "float")
    val y by t.animateFloat(0f, 1f, infiniteRepeatable(tween(3500, easing = LinearEasing), RepeatMode.Reverse), label = "y")
    Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(
                    292.dp,
                ).glass(
                    HaloGlass.Thin,
                    HaloRadius.Sheet,
                ).background(
                    Brush.linearGradient(listOf(tint.first.copy(alpha = 0.55f), tint.second.copy(alpha = 0.7f), HaloColor.InkRaised)),
                ),
        ) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .offset(y = (-14 + y * -10).dp)
                    .size(110.dp)
                    .glass(HaloGlass.Thick, HaloRadius.Sheet),
                contentAlignment = Alignment.Center,
            ) {
                HaloIcon(Icons.Hdmi, 54.dp, strokeWidth = 1.3f)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                HText(inp.typeLabel + (inp.deviceName?.let { " · $it" } ?: ""), HaloType.Label, HaloColor.TextSecondary)
                HText(inp.label, HaloType.Title1)
            }
        }
        PillButton("Switch to ${inp.label}", onSwitch, primary = true, icon = Icons.Chevron)
    }
}

@Composable
private fun InputRow(
    inp: TvInputItem,
    tint: Pair<Color, Color>,
    selected: Boolean,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    HaloFocusable(
        onClick = onClick,
        onFocus = onFocus,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = HaloRadius.Sheet,
        focusedScale = 1.03f,
        ring = false,
        fill = if (selected) Color.White.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.05f),
        focusedFill = Color.White.copy(alpha = 0.94f),
        description = "${inp.label}, ${inp.typeLabel}",
    ) { f ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(38.dp).background(Brush.linearGradient(listOf(tint.first, tint.second)), HaloRadius.Tile),
                contentAlignment = Alignment.Center,
            ) {
                HaloIcon(Icons.Hdmi, 20.dp)
            }
            Column(Modifier.weight(1f)) {
                HText(
                    inp.label,
                    HaloType.Label.copy(fontSize = HaloType.Body.fontSize, fontWeight = FontWeight.SemiBold),
                    if (f) HaloColor.OnLight else HaloColor.Text,
                )
                HText(
                    inp.typeLabel + (
                        inp.deviceName?.let {
                            " · $it"
                        } ?: ""
                    ),
                    HaloType.Caption,
                    if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary,
                )
            }
            HaloIcon(Icons.Chevron, 11.dp, if (f) HaloColor.OnLightSecondary else HaloColor.TextTertiary)
        }
    }
}
