package com.halo.launcher.data

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Halo's own "continue watching" history, filled from media sessions of other apps.
 * Kept as one small JSON file — fast to load on boot, no database needed.
 */
class HistoryStore(
    context: Context,
    private val scope: CoroutineScope,
) {
    private val file = File(context.filesDir, "history.json")
    private val artDir = File(context.filesDir, "art").apply { mkdirs() }
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    private val _items = MutableStateFlow(load())
    val items: StateFlow<List<MediaEntry>> = _items
    private var saveJob: Job? = null

    private fun load(): List<MediaEntry> =
        runCatching {
            if (file.exists()) json.decodeFromString<List<MediaEntry>>(file.readText()) else emptyList()
        }.getOrDefault(emptyList())

    fun upsert(entry: MediaEntry) {
        _items.update { list ->
            val rest = list.filterNot { it.id == entry.id }
            (listOf(entry) + rest).sortedByDescending { it.updatedAt }.take(MAX)
        }
        scheduleSave()
    }

    /** Updates only position fields; keeps the entry's place unless it moved forward in time. */
    fun updatePosition(
        id: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        _items.update { list ->
            list
                .map {
                    if (it.id ==
                        id
                    ) {
                        it.copy(
                            positionMs = positionMs,
                            durationMs =
                                if (durationMs >
                                    0
                                ) {
                                    durationMs
                                } else {
                                    it.durationMs
                                },
                            updatedAt = System.currentTimeMillis(),
                        )
                    } else {
                        it
                    }
                }.sortedByDescending { it.updatedAt }
        }
        scheduleSave()
    }

    fun remove(id: String) {
        _items.update { l -> l.filterNot { it.id == id } }
        File(artDir, "$id.jpg").delete()
        scheduleSave()
    }

    fun clear() {
        _items.value = emptyList()
        artDir.listFiles()?.forEach { it.delete() }
        scheduleSave()
    }

    fun saveArt(
        id: String,
        bmp: Bitmap,
    ): String? =
        runCatching {
            val out = File(artDir, "$id.jpg")
            val scale = 640f / bmp.width.coerceAtLeast(1)
            val b = if (scale < 1f) Bitmap.createScaledBitmap(bmp, 640, (bmp.height * scale).toInt().coerceAtLeast(1), true) else bmp
            out.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            out.absolutePath
        }.getOrNull()

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob =
            scope.launch(Dispatchers.IO) {
                delay(800)
                runCatching {
                    val tmp = File(file.parentFile, "history.tmp")
                    tmp.writeText(json.encodeToString(_items.value))
                    tmp.renameTo(file)
                }
            }
    }

    companion object {
        const val MAX = 40

        fun idFor(
            pkg: String,
            title: String,
            subtitle: String,
        ): String {
            val md =
                MessageDigest
                    .getInstance(
                        "SHA-1",
                    ).digest("$pkg|${title.trim().lowercase()}|${subtitle.trim().lowercase()}".toByteArray())
            return md.take(10).joinToString("") { "%02x".format(it) }
        }
    }
}
