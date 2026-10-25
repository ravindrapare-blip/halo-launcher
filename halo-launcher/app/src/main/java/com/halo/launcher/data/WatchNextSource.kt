package com.halo.launcher.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Reads Android TV's system "Watch Next" list — the same rows Google TV shows in
 * Continue Watching, each with an exact deep link into the episode/movie.
 *
 * Android only lets a third-party launcher read other apps' rows when it holds
 * ACCESS_WATCHED_PROGRAMS. Many TVs don't grant that to sideloaded apps, so this
 * source quietly returns nothing there and Halo relies on its own media history.
 */
class WatchNextSource(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val _items = MutableStateFlow<List<MediaEntry>>(emptyList())
    val items: StateFlow<List<MediaEntry>> = _items
    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    init {
        runCatching { context.contentResolver.registerContentObserver(URI, true, observer) }
        refresh()
    }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val result = runCatching { query() }
            _available.value = result.isSuccess && result.getOrNull() != null
            _items.value = result.getOrNull().orEmpty()
        }
    }

    private fun query(): List<MediaEntry>? {
        val c = context.contentResolver.query(URI, null, null, null, "$C_ENGAGED DESC") ?: return null
        val own = context.packageName
        return c.use { cur ->
            fun str(col: String) = cur.getColumnIndex(col).takeIf { it >= 0 }?.let { cur.getString(it) }

            fun long(col: String) = cur.getColumnIndex(col).takeIf { it >= 0 }?.let { cur.getLong(it) } ?: 0L
            val out = ArrayList<MediaEntry>()
            while (cur.moveToNext() && out.size < 30) {
                val pkg = str(C_PACKAGE) ?: continue
                if (pkg == own) continue
                val title = str(C_TITLE)?.takeIf { it.isNotBlank() } ?: continue
                val s = str(C_SEASON)
                val e = str(C_EPISODE)
                val epTitle = str(C_EP_TITLE)
                val ep =
                    when {
                        s != null && e != null -> "S$s · E$e" + (epTitle?.let { " “$it”" } ?: "")
                        epTitle != null -> epTitle
                        else -> null
                    }
                val type = long(C_TYPE).toInt()
                out +=
                    MediaEntry(
                        id = "wn_" + HistoryStore.idFor(pkg, title, ep.orEmpty()),
                        packageName = pkg,
                        title = title,
                        subtitle = ep.orEmpty(),
                        artPath = str(C_POSTER) ?: str(C_THUMB),
                        positionMs = long(C_POSITION),
                        durationMs = long(C_DURATION),
                        updatedAt = long(C_ENGAGED).takeIf { it > 0 } ?: System.currentTimeMillis(),
                        intentUri = str(C_INTENT),
                        kind = if (type == TYPE_TRACK || type == TYPE_ALBUM) MediaKind.Music else MediaKind.Video,
                        source = MediaSource.WatchNext,
                        episodeLabel = ep,
                    )
            }
            out
        }
    }

    companion object {
        val URI: Uri = Uri.parse("content://android.media.tv/watch_next_program")
        private const val C_PACKAGE = "package_name"
        private const val C_TITLE = "title"
        private const val C_EP_TITLE = "episode_title"
        private const val C_SEASON = "season_display_number"
        private const val C_EPISODE = "episode_display_number"
        private const val C_POSTER = "poster_art_uri"
        private const val C_THUMB = "thumbnail_uri"
        private const val C_POSITION = "last_playback_position_millis"
        private const val C_DURATION = "duration_millis"
        private const val C_INTENT = "intent_uri"
        private const val C_ENGAGED = "last_engagement_time_utc_millis"
        private const val C_TYPE = "type"
        private const val TYPE_TRACK = 7
        private const val TYPE_ALBUM = 8
    }
}
