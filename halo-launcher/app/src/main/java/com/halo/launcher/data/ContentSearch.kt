package com.halo.launcher.data

import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.xmlpull.v1.XmlPullParser

/** A film, show, song… found inside another app, with the intent that opens exactly that item. */
@Immutable
data class ContentHit(
    val packageName: String,
    val title: String,
    val subtitle: String,
    val image: String?,
    val intent: Intent,
)

/** An app that can take a search query itself ("Search in YouTube"). */
@Immutable
data class AppSearchTarget(
    val packageName: String,
    val intent: Intent,
)

/**
 * Searches *inside* apps the same way Android TV's global search does: apps publish a
 * search-suggestion provider (android.app.searchable) whose rows carry a deep link to the exact
 * title. Some apps protect theirs with a system-only permission — those are skipped quietly and
 * still appear as "Search in <app>" shortcuts that open the app's own search with your words.
 */
class ContentSearch(
    private val context: Context,
) {
    private data class Provider(
        val pkg: String,
        val authority: String,
        val path: String?,
        val selection: String?,
        val action: String?,
        val data: String?,
        val component: ComponentName,
    )

    private val pm = context.packageManager

    @Volatile private var cache: List<Provider>? = null

    private fun providers(): List<Provider> = cache ?: discover().also { cache = it }

    fun invalidate() {
        cache = null
    }

    private fun discover(): List<Provider> {
        val own = context.packageName
        return pm
            .queryIntentActivities(Intent(Intent.ACTION_SEARCH), PackageManager.GET_META_DATA)
            .mapNotNull { ri ->
                val ai = ri.activityInfo
                if (ai.packageName == own) return@mapNotNull null
                runCatching {
                    val parser = ai.loadXmlMetaData(pm, "android.app.searchable") ?: return@runCatching null
                    val res = pm.getResourcesForApplication(ai.applicationInfo)
                    var p: Provider? = null
                    parser.use { x ->
                        while (x.next() != XmlPullParser.END_DOCUMENT) {
                            if (x.eventType == XmlPullParser.START_TAG && x.name == "searchable") {
                                fun attr(name: String): String? {
                                    val id = x.getAttributeResourceValue(NS, name, 0)
                                    return if (id != 0) runCatching { res.getString(id) }.getOrNull() else x.getAttributeValue(NS, name)
                                }
                                val auth = attr("searchSuggestAuthority") ?: break
                                p =
                                    Provider(
                                        ai.packageName,
                                        auth,
                                        attr("searchSuggestPath"),
                                        attr("searchSuggestSelection"),
                                        attr("searchSuggestIntentAction"),
                                        attr("searchSuggestIntentData"),
                                        ComponentName(ai.packageName, ai.name),
                                    )
                                break
                            }
                        }
                    }
                    p
                }.getOrNull()
            }.distinctBy { it.authority }
    }

    suspend fun search(
        query: String,
        perApp: Int = 8,
    ): List<ContentHit> =
        withContext(Dispatchers.IO) {
            val q = query.trim()
            if (q.length < 2) return@withContext emptyList()
            coroutineScope {
                providers()
                    .map { p ->
                        async {
                            withTimeoutOrNull(
                                1_800,
                            ) { runCatching { queryOne(p, q, perApp) }.getOrDefault(emptyList()) } ?: emptyList()
                        }
                    }.awaitAll()
                    .flatten()
            }
        }

    /** Apps that accept a search query directly — used as "Search in …" shortcuts. */
    fun searchTargets(
        query: String,
        packages: List<String>,
    ): List<AppSearchTarget> =
        packages.mapNotNull { pkg ->
            val i =
                Intent(
                    Intent.ACTION_SEARCH,
                ).setPackage(pkg).putExtra(SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (i.resolveActivity(pm) != null) AppSearchTarget(pkg, i) else null
        }

    private fun queryOne(
        p: Provider,
        q: String,
        limit: Int,
    ): List<ContentHit> {
        val b = Uri.Builder().scheme("content").authority(p.authority)
        p.path?.let { b.appendEncodedPath(it) }
        b.appendPath(SearchManager.SUGGEST_URI_PATH_QUERY)
        if (p.selection == null) b.appendPath(q)
        b.appendQueryParameter("limit", limit.toString())
        val c =
            context.contentResolver.query(b.build(), null, p.selection, if (p.selection != null) arrayOf(q) else null, null)
                ?: return emptyList()
        return c.use { cur ->
            fun s(col: String) =
                cur
                    .getColumnIndex(col)
                    .takeIf { it >= 0 }
                    ?.let { cur.getString(it) }
                    ?.takeIf { it.isNotBlank() }
            val out = ArrayList<ContentHit>()
            while (cur.moveToNext() && out.size < limit) {
                val title = s(SearchManager.SUGGEST_COLUMN_TEXT_1) ?: continue
                val action = s(SearchManager.SUGGEST_COLUMN_INTENT_ACTION) ?: p.action ?: Intent.ACTION_VIEW
                var data = s(SearchManager.SUGGEST_COLUMN_INTENT_DATA) ?: p.data
                s(SearchManager.SUGGEST_COLUMN_INTENT_DATA_ID)?.let { id -> data = (data?.trimEnd('/') ?: "") + "/" + Uri.encode(id) }
                val intent = Intent(action).setPackage(p.pkg).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                data?.let { intent.data = Uri.parse(it) }
                s(SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA)?.let { intent.putExtra(SearchManager.EXTRA_DATA_KEY, it) }
                intent.putExtra(SearchManager.QUERY, q)
                if (intent.data == null && action == Intent.ACTION_VIEW) intent.component = p.component
                val sub =
                    listOfNotNull(
                        s(SearchManager.SUGGEST_COLUMN_TEXT_2),
                        s(SearchManager.SUGGEST_COLUMN_PRODUCTION_YEAR),
                    ).joinToString(" · ")
                out +=
                    ContentHit(
                        p.pkg,
                        title,
                        sub,
                        s(SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE) ?: s(SearchManager.SUGGEST_COLUMN_ICON_1),
                        intent,
                    )
            }
            out
        }
    }

    companion object {
        private const val NS = "http://schemas.android.com/apk/res/android"
    }
}
