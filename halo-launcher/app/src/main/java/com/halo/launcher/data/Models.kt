package com.halo.launcher.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.serialization.Serializable

enum class AppGroup(
    val label: String,
) {
    Watch("Watch"),
    Listen("Listen"),
    Play("Play"),
    Other("Tools"),
}

@Immutable
data class AppInfo(
    val packageName: String,
    val label: String,
    val component: ComponentName,
    val icon: ImageBitmap?,
    val banner: ImageBitmap?,
    val group: AppGroup,
    val isTvApp: Boolean,
)

enum class MediaKind { Video, Music, Game, Other }

enum class MediaSource { Session, WatchNext }

/**
 * One thing the user was watching / listening to, in any app.
 * Persisted as JSON so the list survives reboots.
 */
@Serializable
data class MediaEntry(
    val id: String,
    val packageName: String,
    val title: String,
    val subtitle: String = "",
    val artPath: String? = null, // local file or content/https uri
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val mediaId: String? = null,
    val mediaUri: String? = null,
    val intentUri: String? = null, // exact deep link (from Watch Next) when available
    val kind: MediaKind = MediaKind.Video,
    val source: MediaSource = MediaSource.Session,
    val episodeLabel: String? = null,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isFinished: Boolean get() = durationMs > 0 && progress >= 0.95f
    val remainingMs: Long get() = (durationMs - positionMs).coerceAtLeast(0)
}

@Immutable
data class TvInputItem(
    val id: String,
    val label: String,
    val typeLabel: String,
    val deviceName: String?,
    val isPassthrough: Boolean,
)
