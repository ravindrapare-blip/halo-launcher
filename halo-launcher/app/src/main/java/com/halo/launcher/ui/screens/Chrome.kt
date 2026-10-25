package com.halo.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.halo.launcher.ui.HaloController
import com.halo.launcher.ui.Screen
import com.halo.launcher.ui.components.HText
import com.halo.launcher.ui.components.HaloFocusable
import com.halo.launcher.ui.components.IconCircle
import com.halo.launcher.ui.components.Icons
import com.halo.launcher.ui.components.glass
import com.halo.launcher.ui.theme.HaloColor
import com.halo.launcher.ui.theme.HaloGlass
import com.halo.launcher.ui.theme.HaloRadius
import com.halo.launcher.ui.theme.HaloSpace
import com.halo.launcher.ui.theme.HaloType
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun rememberNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/** Clock · section tabs · quick icons. Same bar on every main screen so navigation never moves. */
@Composable
fun TopBar(
    c: HaloController,
    current: Screen,
    count: Int,
) {
    val prefs by c.graph.prefs.state
        .collectAsStateWithLifecycle()
    val now = rememberNow()
    val time = SimpleDateFormat(if (prefs.clock24h) "H:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    val date = SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(now))
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = HaloSpace.gutter)
            .padding(top = HaloSpace.top)
            .height(40.dp),
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HText(time, HaloType.Clock)
            HText(date, HaloType.Label, HaloColor.TextSecondary, Modifier.padding(bottom = 5.dp))
        }
        Row(
            Modifier.align(Alignment.Center).glass(HaloGlass.Regular, HaloRadius.Pill).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Tab("Home", current == Screen.Home) { c.go(Screen.Home) }
            Tab("Watching", current == Screen.Watching, badge = count.takeIf { it > 0 }?.toString()) { c.go(Screen.Watching) }
            Tab("Library", current == Screen.Library) { c.go(Screen.Library) }
        }
        Row(
            Modifier.align(Alignment.CenterEnd).glass(HaloGlass.Regular, HaloRadius.Pill).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconCircle(Icons.Search, "Search", { c.go(Screen.Library) })
            IconCircle(Icons.Inputs, "Inputs", { c.go(Screen.Inputs) })
            IconCircle(Icons.Sliders, "Settings", { c.go(Screen.Settings) })
        }
    }
}

@Composable
private fun Tab(
    label: String,
    selected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    HaloFocusable(
        onClick = onClick,
        onFocus = null,
        modifier = Modifier.height(28.dp),
        shape = HaloRadius.Pill,
        focusedScale = 1.05f,
        ring = false,
        fill = if (selected) Color.White.copy(alpha = 0.94f) else Color.Transparent,
        focusedFill = if (selected) Color.White else Color.White.copy(alpha = 0.22f),
    ) { _ ->
        Row(
            Modifier.align(Alignment.Center).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            HText(
                label,
                HaloType.Label.copy(
                    fontSize = HaloType.Body.fontSize * 0.85f,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
                if (selected) HaloColor.OnLight else HaloColor.Text,
            )
            badge?.let {
                Box(Modifier.size(15.dp).glass(HaloGlass.Thick, HaloRadius.Pill), contentAlignment = Alignment.Center) {
                    HText(it, HaloType.Caption.copy(fontWeight = FontWeight.Bold), if (selected) HaloColor.OnLight else HaloColor.Text)
                }
            }
        }
    }
}

/** Title block for secondary screens (Settings, Inputs). */
@Composable
fun ScreenTitle(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.glass(HaloGlass.Regular, HaloRadius.Pill)) { IconCircle(Icons.Back, "Back to Home", onBack, size = 30.dp) }
        Column {
            HText(title, HaloType.Title1)
            subtitle?.let { HText(it, HaloType.Label, HaloColor.TextSecondary) }
        }
    }
}

@Composable
fun SectionLabel(
    text: String,
    sub: String? = null,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HText(text, HaloType.Headline)
        sub?.let { HText(it, HaloType.Label, HaloColor.TextTertiary) }
    }
}

@Composable
fun Gap(
    w: Int = 0,
    h: Int = 0,
) = androidx.compose.foundation.layout
    .Spacer(Modifier.width(w.dp).height(h.dp))
