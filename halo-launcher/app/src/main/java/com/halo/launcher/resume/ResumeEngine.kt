package com.halo.launcher.resume

import android.app.ActivityOptions
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.browse.MediaBrowser
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.service.media.MediaBrowserService
import com.halo.launcher.data.AppRepository
import com.halo.launcher.data.MediaEntry
import com.halo.launcher.data.MediaKind
import com.halo.launcher.service.SessionHub
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

enum class ResumeResult(
    val exact: Boolean,
) {
    DeepLink(true),
    PlayerIntent(true),
    LiveSession(true),
    AppLink(true),
    Resumption(true),
    Search(false),
    AppOnly(false),
    Failed(false),
}

/**
 * Opens the right app *at the right thing*. Tries, most precise first:
 *  1. Watch Next deep link the app published (exact episode / film).
 *  2. The app's own player intent for this title (session activity / media notification) + play.
 *  3. The app's live media session: bring it forward, play, seek to your spot.
 *  4. A content link built from what the app reported (YouTube video id with timestamp,
 *     Spotify URI, any http/app-scheme media URI) — opened inside that app.
 *  5. Android playback resumption (MediaBrowserService + EXTRA_RECENT) — music/podcast apps.
 *  6. The app's "play from search" with the title.
 *  7. Open the app.
 */
class ResumeEngine(
    private val context: Context,
    private val apps: AppRepository,
) {
    suspend fun resume(
        entry: MediaEntry,
        fromStart: Boolean = false,
    ): ResumeResult {
        // 1 ─ Watch Next
        entry.intentUri?.let { uri ->
            val ok =
                runCatching {
                    context.startActivity(Intent.parseUri(uri, Intent.URI_INTENT_SCHEME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    true
                }.getOrDefault(false)
            if (ok) return ResumeResult.DeepLink
        }

        val live = SessionHub.controllerFor(entry.packageName)
        val liveIsThis = live != null && sameTitle(live, entry)

        // 2 ─ App's own player intent for this exact item
        SessionHub.playerIntentFor(entry.id, entry.packageName)?.let { pi ->
            if (send(pi)) {
                val c = live ?: waitForSession(entry.packageName)
                if (c != null) {
                    delay(400)
                    if (c.playbackState?.state != PlaybackState.STATE_PLAYING) c.transportControls.play()
                    if (fromStart) {
                        c.transportControls.seekTo(0)
                    } else if (!liveIsThis) {
                        seekWhenReady(c, entry)
                    }
                }
                return ResumeResult.PlayerIntent
            }
        }

        // 3 ─ Live session holding this title
        if (live != null && (liveIsThis || entry.kind == MediaKind.Music)) {
            apps.launch(entry.packageName)
            delay(350)
            live.transportControls.play()
            if (fromStart) live.transportControls.seekTo(0)
            return ResumeResult.LiveSession
        }
        if (live != null && entry.mediaId != null) {
            apps.launch(entry.packageName)
            live.transportControls.playFromMediaId(entry.mediaId, null)
            seekWhenReady(live, entry, fromStart)
            return ResumeResult.LiveSession
        }

        // 4 ─ Content link inside the app
        contentLink(entry, fromStart)?.let { intent ->
            if (intent.resolveActivity(context.packageManager) != null &&
                runCatching {
                    context.startActivity(intent)
                    true
                }.getOrDefault(false)
            ) {
                return ResumeResult.AppLink
            }
        }

        // 5 ─ Playback resumption
        browserService(entry.packageName)?.let { cn ->
            val controller = withTimeoutOrNull(4_000) { connect(cn) }
            if (controller != null) {
                apps.launch(entry.packageName)
                if (entry.mediaId != null) {
                    controller.transportControls.playFromMediaId(entry.mediaId, null)
                } else {
                    controller.transportControls.play()
                }
                seekWhenReady(controller, entry, fromStart)
                return ResumeResult.Resumption
            }
        }

        // 6 ─ Play from search
        val search =
            Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                .setPackage(entry.packageName)
                .putExtra(SearchManager.QUERY, entry.title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (entry.kind == MediaKind.Music) search.putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/audio")
        if (search.resolveActivity(context.packageManager) != null &&
            runCatching {
                context.startActivity(search)
                true
            }.getOrDefault(false)
        ) {
            return ResumeResult.Search
        }

        // 7 ─ Just open it
        return if (apps.launch(entry.packageName)) ResumeResult.AppOnly else ResumeResult.Failed
    }

    /** Builds an in-app link from ids the app itself reported. Only well-known, documented formats. */
    private fun contentLink(
        e: MediaEntry,
        fromStart: Boolean,
    ): Intent? {
        val pkg = e.packageName
        val sec = if (fromStart) 0 else e.positionMs / 1000
        val id = e.mediaId.orEmpty()
        val uri: Uri? =
            when {
                pkg.startsWith("com.google.android.youtube") && YT_ID.matches(id) ->
                    Uri.parse("https://www.youtube.com/watch?v=$id" + if (sec > 5) "&t=${sec}s" else "")
                pkg.startsWith("com.google.android.youtube") && e.mediaUri?.contains("youtube.com/watch") == true -> Uri.parse(e.mediaUri)
                pkg.startsWith("com.spotify") && id.startsWith("spotify:") -> Uri.parse(id)
                e.mediaUri != null &&
                    Uri.parse(e.mediaUri).scheme?.lowercase()?.let {
                        it.isNotEmpty() && it !in LOCAL_SCHEMES
                    } == true -> Uri.parse(e.mediaUri)
                id.contains("://") && !id.startsWith("content") && !id.startsWith("file") -> Uri.parse(id)
                else -> null
            }
        return uri?.let { Intent(Intent.ACTION_VIEW, it).setPackage(pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    }

    private fun send(pi: PendingIntent): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val opts =
                    ActivityOptions
                        .makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                pi.send(context, 0, null, null, null, null, opts.toBundle())
            } else {
                pi.send()
            }
            true
        }.getOrDefault(false)

    private fun sameTitle(
        c: MediaController,
        e: MediaEntry,
    ): Boolean {
        val t =
            c.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: c.metadata
                ?.description
                ?.title
                ?.toString()
        return t != null && t.trim().equals(e.title.trim(), ignoreCase = true)
    }

    private suspend fun waitForSession(pkg: String): MediaController? {
        repeat(12) {
            SessionHub.controllerFor(pkg)?.let { return it }
            delay(250)
        }
        return null
    }

    private suspend fun seekWhenReady(
        c: MediaController,
        entry: MediaEntry,
        fromStart: Boolean = false,
    ) {
        val target = if (fromStart) 0L else entry.positionMs
        if (entry.kind == MediaKind.Music && !fromStart) return
        if (target < 5_000 && !fromStart) return
        repeat(24) {
            val st = c.playbackState
            if (st != null && (st.state == PlaybackState.STATE_PLAYING || st.state == PlaybackState.STATE_PAUSED)) {
                if ((st.actions and PlaybackState.ACTION_SEEK_TO) != 0L &&
                    kotlin.math.abs(st.position - target) > 8_000
                ) {
                    c.transportControls.seekTo(target)
                }
                return
            }
            delay(250)
        }
    }

    private fun browserService(pkg: String): ComponentName? =
        context.packageManager
            .queryIntentServices(Intent(MediaBrowserService.SERVICE_INTERFACE).setPackage(pkg), 0)
            .firstOrNull()
            ?.serviceInfo
            ?.let { ComponentName(it.packageName, it.name) }

    private suspend fun connect(cn: ComponentName): MediaController? =
        suspendCancellableCoroutine { cont ->
            var browser: MediaBrowser? = null
            val cb =
                object : MediaBrowser.ConnectionCallback() {
                    override fun onConnected() {
                        val b = browser ?: return
                        if (cont.isActive) cont.resume(runCatching { MediaController(context, b.sessionToken) }.getOrNull())
                    }

                    override fun onConnectionFailed() {
                        if (cont.isActive) cont.resume(null)
                    }

                    override fun onConnectionSuspended() {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            val hints = Bundle().apply { putBoolean(MediaBrowserService.BrowserRoot.EXTRA_RECENT, true) }
            val b = MediaBrowser(context, cn, cb, hints)
            browser = b
            runCatching { b.connect() }.onFailure { if (cont.isActive) cont.resume(null) }
            cont.invokeOnCancellation { runCatching { browser?.disconnect() } }
        }

    companion object {
        private val YT_ID = Regex("^[A-Za-z0-9_-]{11}$")
        private val LOCAL_SCHEMES = setOf("content", "file", "android.resource")
    }
}
