package com.halo.launcher.service

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import com.halo.launcher.HaloApp
import com.halo.launcher.data.AppGroup
import com.halo.launcher.data.HistoryStore
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** What is playing right now, for the Halo ring and the "Now playing" pill. */
data class NowPlaying(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val playing: Boolean,
)

/** Live media controllers, shared between the listener service and the resume engine. */
object SessionHub {
    @Volatile var controllers: List<MediaController> = emptyList()
        internal set
    private val _now = MutableStateFlow<NowPlaying?>(null)
    val now: StateFlow<NowPlaying?> = _now

    internal fun publish(n: NowPlaying?) {
        _now.value = n
    }

    fun controllerFor(pkg: String): MediaController? = controllers.firstOrNull { it.packageName == pkg }

    /**
     * The app's own "open the player" intent for the item it is holding right now
     * (MediaSession.sessionActivity, or the media notification's tap action). Sending it lands
     * exactly on the player for that title — the most precise thing Android gives a launcher.
     * Lives in memory only (PendingIntents can't be saved), so it covers everything since boot.
     */
    internal val playerIntents = java.util.concurrent.ConcurrentHashMap<String, android.app.PendingIntent>()
    internal val lastEntryForPackage = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun playerIntentFor(
        entryId: String,
        pkg: String,
    ): android.app.PendingIntent? = if (lastEntryForPackage[pkg] == entryId) playerIntents[entryId] ?: playerIntents["pkg:$pkg"] else null

    fun playPauseActive(): Boolean {
        val c =
            controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: controllers.firstOrNull() ?: return false
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        return true
    }

    fun pauseAll() = controllers.forEach { runCatching { it.transportControls.pause() } }

    fun isEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
        return flat.contains(ComponentName(context, MediaListenerService::class.java).flattenToString()) ||
            flat.contains(context.packageName + "/")
    }
}

/**
 * Notification-listener access is the only public way for a launcher to see other apps'
 * media sessions. We use it purely to record title / artwork / position of what you play.
 */
class MediaListenerService : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private val callbacks = HashMap<TokenKey, MediaController.Callback>()
    private val artSavedFor = HashMap<String, String>()
    private val graph get() = (application as HaloApp).graph

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> track(list.orEmpty()) }

    private val ticker =
        object : Runnable {
            override fun run() {
                SessionHub.controllers.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }.forEach { capture(it) }
                main.postDelayed(this, 15_000)
            }
        }

    override fun onListenerConnected() {
        val m = getSystemService(MediaSessionManager::class.java) ?: return
        msm = m
        val cn = ComponentName(this, MediaListenerService::class.java)
        runCatching {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            track(m.getActiveSessions(cn))
        }
        main.postDelayed(ticker, 15_000)
    }

    override fun onNotificationPosted(sbn: android.service.notification.StatusBarNotification?) {
        val n = sbn?.notification ?: return
        if (sbn.packageName == packageName) return
        val isMedia =
            n.extras?.containsKey(android.app.Notification.EXTRA_MEDIA_SESSION) == true ||
                n.category == android.app.Notification.CATEGORY_TRANSPORT
        if (isMedia) {
            n.contentIntent?.let { pi ->
                SessionHub.playerIntents["pkg:${sbn.packageName}"] = pi
                SessionHub.lastEntryForPackage[sbn.packageName]?.let { id -> SessionHub.playerIntents.putIfAbsent(id, pi) }
            }
        }
    }

    override fun onListenerDisconnected() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        main.removeCallbacks(ticker)
        SessionHub.controllers.forEach { c -> callbacks.remove(c.sessionToken.key())?.let { c.unregisterCallback(it) } }
        SessionHub.controllers = emptyList()
        SessionHub.publish(null)
    }

    private fun track(list: List<MediaController>) {
        val own = packageName
        val fresh = list.filter { it.packageName != own }
        val keys = fresh.map { it.sessionToken.key() }.toSet()
        // Drop callbacks for sessions that went away.
        SessionHub.controllers.filter { it.sessionToken.key() !in keys }.forEach { c ->
            callbacks.remove(c.sessionToken.key())?.let { runCatching { c.unregisterCallback(it) } }
        }
        fresh.forEach { c ->
            if (!callbacks.containsKey(c.sessionToken.key())) {
                val cb =
                    object : MediaController.Callback() {
                        override fun onMetadataChanged(metadata: MediaMetadata?) = capture(c)

                        override fun onPlaybackStateChanged(state: PlaybackState?) = capture(c)

                        override fun onSessionDestroyed() = capture(c)
                    }
                c.registerCallback(cb, main)
                callbacks[c.sessionToken.key()] = cb
            }
            capture(c)
        }
        SessionHub.controllers = fresh
        publishNow()
    }

    private fun publishNow() {
        val c =
            SessionHub.controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
                ?: SessionHub.controllers.firstOrNull { it.metadata != null }
        val md = c?.metadata
        SessionHub.publish(
            if (c == null || md == null) {
                null
            } else {
                NowPlaying(
                    c.packageName,
                    titleOf(md) ?: "",
                    subtitleOf(md),
                    c.playbackState?.state == PlaybackState.STATE_PLAYING,
                )
            },
        )
    }

    private fun capture(c: MediaController) {
        publishNow()
        val md = c.metadata ?: return
        val state = c.playbackState ?: return
        if (state.state !in RECORDABLE) return
        val title = titleOf(md)?.takeIf { it.isNotBlank() } ?: return
        val pkg = c.packageName
        val subtitle = subtitleOf(md)
        val duration = md.getLong(MediaMetadata.METADATA_KEY_DURATION).coerceAtLeast(0)
        val group = graph.apps.find(pkg)?.group
        val kind =
            when {
                group == AppGroup.Listen -> MediaKind.Music
                group == AppGroup.Play -> MediaKind.Game
                md.getString(MediaMetadata.METADATA_KEY_ALBUM) != null && duration in 1..(15 * 60_000L) -> MediaKind.Music
                else -> MediaKind.Video
            }
        // Music: one card per app (latest track). Video: one card per title.
        val id = if (kind == MediaKind.Music) HistoryStore.idFor(pkg, "listening", "") else HistoryStore.idFor(pkg, title, "")
        var pos = state.position
        if (state.state == PlaybackState.STATE_PLAYING && state.lastPositionUpdateTime > 0) {
            pos += ((SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed).toLong()
        }
        if (duration > 0) pos = pos.coerceIn(0, duration)
        val existing =
            graph.history.items.value
                .firstOrNull { it.id == id }
        SessionHub.lastEntryForPackage[pkg] = id
        c.sessionActivity?.let { SessionHub.playerIntents[id] = it }

        val artKey = "$title|$subtitle"
        val bitmap = artBitmap(md)
        val artUri = artUri(md)
        val needsArt = artSavedFor[id] != artKey
        if (needsArt) artSavedFor[id] = artKey

        val base =
            MediaEntry(
                id = id,
                packageName = pkg,
                title = title,
                subtitle = subtitle,
                artPath = if (needsArt) artUri ?: existing?.takeIf { it.title == title }?.artPath else existing?.artPath ?: artUri,
                positionMs = pos,
                durationMs = duration,
                mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID) ?: md.description?.mediaId,
                mediaUri = md.getString(MediaMetadata.METADATA_KEY_MEDIA_URI),
                kind = kind,
                episodeLabel = subtitle.takeIf { kind == MediaKind.Video && it.isNotBlank() },
            )
        if (existing != null && existing.title == title && !needsArt) {
            graph.history.updatePosition(id, pos, duration)
        } else {
            graph.history.upsert(base)
        }
        if (needsArt && bitmap != null) {
            graph.scope.launch(Dispatchers.IO) {
                graph.history.saveArt(id, bitmap)?.let { path ->
                    graph.history.items.value
                        .firstOrNull { it.id == id }
                        ?.let { graph.history.upsert(it.copy(artPath = path)) }
                }
            }
        }
    }

    private fun titleOf(md: MediaMetadata) =
        md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: md.description?.title?.toString()

    private fun subtitleOf(md: MediaMetadata) =
        (
            md.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: md.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: md.description?.subtitle?.toString()
        ).orEmpty()

    private fun artBitmap(md: MediaMetadata): Bitmap? =
        md.getBitmap(MediaMetadata.METADATA_KEY_ART) ?: md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON) ?: md.description?.iconBitmap

    private fun artUri(md: MediaMetadata): String? =
        (
            md.getString(MediaMetadata.METADATA_KEY_ART_URI) ?: md.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI)
                ?: md.getString(MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI) ?: md.description?.iconUri?.toString()
        )?.takeIf { it.isNotBlank() }

    companion object {
        private val RECORDABLE = setOf(PlaybackState.STATE_PLAYING, PlaybackState.STATE_PAUSED, PlaybackState.STATE_BUFFERING)
    }
}

private typealias TokenKey = Int

private fun android.media.session.MediaSession.Token.key(): Int = hashCode()
