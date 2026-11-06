package com.avi.peek.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

data class WebProfileHit(
    val username: String,
    val displayName: String,
)

/**
 * Finds Instagram profiles by a person's name. Instagram's own guest search is
 * login-gated, so Peek asks DuckDuckGo's lightweight HTML endpoint for public
 * instagram.com results and then reads only the public profile pages.
 */
class GlobalSearchClient(
    private val okHttp: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {

    suspend fun search(query: String, limit: Int = 8): List<WebProfileHit> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val url = "https://html.duckduckgo.com/html/".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("q", "site:instagram.com $query")
            ?.build()
            ?: return@withContext emptyList()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        try {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("Search returned ${response.code}")
                val html = response.body?.string() ?: return@withContext emptyList()
                parse(html, limit)
            }
        } catch (e: IOException) {
            throw e
        }
    }

    private fun parse(html: String, limit: Int): List<WebProfileHit> {
        val resultRegex = Regex(
            """<a[^>]+class="result__a"[^>]*href="([^"]+)"[^>]*>(.*?)</a>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        )
        val seen = LinkedHashSet<String>()
        val hits = ArrayList<WebProfileHit>()
        for (match in resultRegex.findAll(html)) {
            val href = decodeEntities(match.groupValues[1])
            val title = cleanTitle(match.groupValues[2])
            val target = resolveTarget(href) ?: continue
            val path = target.toHttpUrlOrNull()?.pathSegments ?: continue
            if (path.size < 1) continue
            val username = path.first().trim().lowercase()
            if (username.isBlank() || username in RESERVED) continue
            if (!username.matches(USERNAME_REGEX)) continue
            val byTitle = Regex("""^(.+?)\s*\(@([A-Za-z0-9._]+)\)""").find(title)
            val displayName = byTitle?.groupValues?.getOrNull(1)?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: title.substringBefore(" | ").substringBefore(" • ").trim().ifBlank { username }
            if (seen.add(username)) {
                hits += WebProfileHit(username = username, displayName = displayName)
                if (hits.size >= limit) break
            }
        }
        return hits
    }

    private fun resolveTarget(href: String): String? {
        val normalized = if (href.startsWith("//")) "https:$href" else href
        val url = normalized.toHttpUrlOrNull() ?: return null
        if (url.host.contains("duckduckgo.com")) {
            val encoded = url.queryParameter("uddg") ?: return null
            return runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
        }
        if (url.host.contains("instagram.com")) return url.toString()
        return null
    }

    private fun cleanTitle(raw: String): String {
        return decodeEntities(raw.replace(Regex("<[^>]+>"), ""))
            .replace(Regex("\\s+"), " ")
            .trim()
            .removeSuffix("• Instagram photos and videos")
            .trim()
    }

    private fun decodeEntities(value: String): String {
        return value
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }

    private companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"
        val USERNAME_REGEX = Regex("""[a-z0-9._]{2,30}""")
        val RESERVED = setOf("p", "reel", "reels", "stories", "explore", "tv", "accounts", "direct", "about", "legal", "developer")
    }
}
