package com.avi.peek.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GuestInstagramClient(
    private val okHttp: OkHttpClient = defaultClient(),
    private val parser: SsrParser = SsrParser(),
) {

    @Volatile
    private var lsdToken: String? = null

    private val lsdRegex = Regex("""LSD",\[\],\{"token":"([^"]+)"""")

    suspend fun fetchProfile(username: String): ScrapedProfile = withContext(Dispatchers.IO) {
        val url = "https://www.instagram.com/${username}/"
        val html = getHtml(url, isDocument = true)
        lsdToken = lsdRegex.find(html)?.groupValues?.getOrNull(1)
        val parsed = parser.parseProfile(html, username)
            ?: throw classifyEmpty(html, username)
        if (hasSession() && parsed.media.isEmpty() && !parsed.userId.isNullOrBlank()) {
            val page = fetchAuthenticatedUserFeed(parsed.userId, null, 12)
            if (page != null && page.nodes.isNotEmpty()) {
                parsed.copy(
                    media = page.nodes,
                    hasNextPage = page.hasNextPage,
                    endCursor = page.endCursor,
                )
            } else {
                parsed
            }
        } else {
            parsed
        }
    }

    /**
     * Fetches one page of the profile grid with Instagram's logged-out Relay
     * query. This is the same request the web profile uses for "Show more posts".
     */
    suspend fun fetchProfilePostsPage(
        username: String,
        userId: String,
        after: String?,
        first: Int = 12,
    ): ProfilePostsPage = withContext(Dispatchers.IO) {
        if (hasSession() && userId.isNotBlank()) {
            fetchAuthenticatedUserFeed(userId, after, first)?.let { return@withContext it }
        }
        val lsd = lsdToken ?: run {
            fetchProfile(username)
            lsdToken
        } ?: throw GuestException(GuestErrorKind.PARSE, "Couldn't open an Instagram guest session")

        val variables = buildJsonObject {
            if (after != null) put("after", JsonPrimitive(after)) else put("after", JsonNull)
            put("first", first)
            put("id", userId)
            put("__relay_internal__pv__PolarisSEOIsCrawlerrelayprovider", false)
        }.toString()

        val form = FormBody.Builder()
            .add("av", "0")
            .add("__d", "www")
            .add("__user", "0")
            .add("__a", "1")
            .add("__req", "1")
            .add("__ccg", "GOOD")
            .add("lsd", lsd)
            .add("jazoest", "22260")
            .add("fb_api_caller_class", "RelayModern")
            .add(
                "fb_api_req_friendly_name",
                "PolarisLoggedOutDesktopWWWProfilePostsTabContentQuery_connection",
            )
            .add("server_timestamps", "true")
            .add("variables", variables)
            .add("doc_id", PROFILE_POSTS_DOC_ID)
            .build()

        val request = Request.Builder()
            .url("https://www.instagram.com/api/graphql")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-IG-App-ID", APP_ID)
            .header("X-FB-LSD", lsd)
            .header("X-CSRFToken", cookieJar.value("csrftoken").orEmpty())
            .header("X-ASBD-ID", "359341")
            .header("X-FB-Friendly-Name", "PolarisLoggedOutDesktopWWWProfilePostsTabContentQuery_connection")
            .header("X-IG-WWW-Claim", "0")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/$username/")
            .header("Origin", "https://www.instagram.com")
            .header("Sec-Fetch-Dest", "empty")
            .header("Sec-Fetch-Mode", "cors")
            .header("Sec-Fetch-Site", "same-origin")
            .post(form)
            .build()

        try {
            okHttp.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.code != 200) {
                    val error = when (response.code) {
                        401, 403 -> GuestException(
                            GuestErrorKind.LOGIN_REQUIRED,
                            "Instagram wants a login for deeper profile pages",
                        )
                        429 -> GuestException(
                            GuestErrorKind.RATE_LIMITED,
                            "Instagram is rate limiting profile pages. Try again in a few minutes.",
                        )
                        in 500..599 -> GuestException(GuestErrorKind.SERVER, "Instagram had a server error")
                        else -> GuestException(GuestErrorKind.UNKNOWN, "Unexpected response ${response.code}")
                    }
                    throw error
                }
                parser.parseProfilePostsPage(body)
                    ?: throw GuestException(GuestErrorKind.PARSE, "Couldn't read older posts")
            }
        } catch (e: GuestException) {
            throw e
        } catch (e: IOException) {
            throw GuestException(GuestErrorKind.NETWORK, "Couldn't reach Instagram", e)
        }
    }

    suspend fun fetchPost(code: String, pk: String? = null): ScrapedPost = withContext(Dispatchers.IO) {
        val url = "https://www.instagram.com/p/$code/"
        try {
            val html = getHtml(url, isDocument = true, referer = "https://www.instagram.com/")
            parser.parsePost(html, code)
                ?: throw classifyEmpty(html, code)
        } catch (e: GuestException) {
            if (hasSession() && !pk.isNullOrBlank()) {
                fetchMediaInfo(pk)?.let { return@withContext it }
            }
            throw e
        }
    }

    /** Reads the signed-in viewer so Settings can show which account is connected. */
    suspend fun resolveViewer(): ViewerIdentity? = withContext(Dispatchers.IO) {
        val html = runCatching {
            getHtml("https://www.instagram.com/", isDocument = true)
        }.getOrNull() ?: return@withContext null
        val block = Regex("\"viewer\"\\s*:\\s*\\{[\\s\\S]{0,2500}?\\}")
            .find(html)
            ?.value
            .orEmpty()
        val username = Regex("\"username\"\\s*:\\s*\"([^\"]+)\"")
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
        val userId = Regex("\"pk\"\\s*:\\s*\"?(\\d+)\"?")
            .find(block)
            ?.groupValues
            ?.getOrNull(1)
            ?: sessionUserId()
        if (username == null && userId == null) return@withContext null
        ViewerIdentity(username = username, userId = userId)
    }

    fun hasSession(): Boolean = !cookieJar.value("sessionid").isNullOrBlank()

    fun sessionUserId(): String? = cookieJar.value("ds_user_id")

    fun restoreSession(cookies: List<Pair<String, String>>) {
        cookieJar.restore(cookies)
        lsdToken = null
    }

    fun clearSession() {
        cookieJar.clear()
        lsdToken = null
    }

    private fun fetchAuthenticatedUserFeed(
        userId: String,
        after: String?,
        first: Int,
    ): ProfilePostsPage? {
        val url = "https://www.instagram.com/api/v1/feed/user/$userId/"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("count", first.toString())
            .apply { if (!after.isNullOrBlank()) addQueryParameter("max_id", after) }
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-IG-App-ID", APP_ID)
            .header("X-ASBD-ID", "359341")
            .header("X-IG-WWW-Claim", "0")
            .header("X-CSRFToken", cookieJar.value("csrftoken").orEmpty())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/")
            .get()
            .build()
        return try {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    throw GuestException(
                        GuestErrorKind.RATE_LIMITED,
                        "Instagram is rate limiting profile pages. Try again in a few minutes.",
                    )
                }
                if (response.code != 200) return null
                parser.parseUserFeed(response.body?.string().orEmpty())
            }
        } catch (e: GuestException) {
            throw e
        } catch (e: IOException) {
            null
        }
    }

    private fun fetchMediaInfo(pk: String): ScrapedPost? {
        val request = Request.Builder()
            .url("https://www.instagram.com/api/v1/media/$pk/info/")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("X-IG-App-ID", APP_ID)
            .header("X-ASBD-ID", "359341")
            .header("X-IG-WWW-Claim", "0")
            .header("X-CSRFToken", cookieJar.value("csrftoken").orEmpty())
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "https://www.instagram.com/")
            .get()
            .build()
        return try {
            okHttp.newCall(request).execute().use { response ->
                if (response.code == 429) {
                    throw GuestException(
                        GuestErrorKind.RATE_LIMITED,
                        "Instagram is rate limiting requests. Try again in a few minutes.",
                    )
                }
                if (response.code != 200) return null
                parser.parseApiPost(response.body?.string().orEmpty())
            }
        } catch (e: GuestException) {
            throw e
        } catch (e: IOException) {
            null
        }
    }

    suspend fun fetchHighlightMedia(highlightId: String): List<MediaChild> = withContext(Dispatchers.IO) {
        val url = "https://www.instagram.com/stories/highlights/$highlightId/"
        val html = getHtml(url, isDocument = true, referer = "https://www.instagram.com/")
        val items = parser.parseHighlightMedia(html)
        if (items.isEmpty()) {
            throw GuestException(
                GuestErrorKind.PARSE,
                "Instagram doesn't expose this highlight's items to logged-out guests",
            )
        }
        items
    }

    suspend fun download(
        url: String,
        destination: File,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.instagram.com/")
            .header("Accept", "*/*")
            .build()
        try {
            okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw GuestException(
                        kind = if (response.code == 403) GuestErrorKind.BLOCKED else GuestErrorKind.NETWORK,
                        message = "Media download failed (${response.code})",
                    )
                }
                val body = response.body ?: throw GuestException(GuestErrorKind.NETWORK, "Empty media body")
                val total = body.contentLength()
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            onProgress(downloaded, total)
                        }
                    }
                }
            }
        } catch (e: GuestException) {
            destination.delete()
            throw e
        } catch (e: IOException) {
            destination.delete()
            throw GuestException(GuestErrorKind.NETWORK, "Media download failed", e)
        }
    }

    private suspend fun getHtml(
        url: String,
        isDocument: Boolean,
        referer: String? = null,
    ): String {
        var lastError: GuestException? = null
        repeat(2) { attempt ->
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header(
                    "Accept",
                    if (isDocument) {
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8"
                    } else {
                        "*/*"
                    },
                )
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("x-ig-app-id", APP_ID)
                .header("Sec-Fetch-Dest", "document")
                .header("Sec-Fetch-Mode", "navigate")
                .header("Sec-Fetch-Site", "none")
                .header("Sec-Fetch-User", "?1")
                .header("Upgrade-Insecure-Requests", "1")
                .apply { referer?.let { header("Referer", it) } }
                .build()

            try {
                okHttp.newCall(request).execute().use { response ->
                    if (response.code == 200) {
                        return response.body?.string()
                            ?: throw GuestException(GuestErrorKind.PARSE, "Empty response")
                    }
                    lastError = when (response.code) {
                        404 -> GuestException(GuestErrorKind.NOT_FOUND, "That profile or post doesn't exist")
                        429 -> GuestException(GuestErrorKind.RATE_LIMITED, "Instagram is rate limiting requests")
                        401, 403 -> GuestException(GuestErrorKind.LOGIN_REQUIRED, "Instagram wants a login for this")
                        in 500..599 -> GuestException(GuestErrorKind.SERVER, "Instagram had a server error")
                        else -> GuestException(GuestErrorKind.UNKNOWN, "Unexpected response ${response.code}")
                    }
                    if (response.code in 500..599 && attempt == 0) {
                        delay(900)
                    } else {
                        throw lastError!!
                    }
                }
            } catch (e: GuestException) {
                if (e.kind != GuestErrorKind.SERVER || attempt == 1) throw e
                lastError = e
            } catch (e: IOException) {
                lastError = GuestException(GuestErrorKind.NETWORK, "Couldn't reach Instagram", e)
                if (attempt == 0) delay(700)
            }
        }
        throw lastError ?: GuestException(GuestErrorKind.UNKNOWN)
    }

    private fun classifyEmpty(html: String, username: String): GuestException {
        val lower = html.lowercase()
        return when {
            lower.contains("please wait a few minutes") || lower.contains("require_login") ->
                GuestException(GuestErrorKind.RATE_LIMITED, "Instagram is asking for a pause. Try again in a few minutes.")
            lower.contains("page not found") || lower.contains("isn't available") || lower.contains("page isn&#39;t available") ->
                GuestException(GuestErrorKind.NOT_FOUND, "@$username could not be found")
            lower.contains("log in") && lower.length < 700_000 ->
                GuestException(GuestErrorKind.LOGIN_REQUIRED, "Instagram didn't return public data for @$username")
            else -> GuestException(GuestErrorKind.PARSE, "Couldn't read the public page for @$username")
        }
    }

    companion object {
        private const val APP_ID = "936619743392459"
        private const val PROFILE_POSTS_DOC_ID = "28904005159232689"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"

        internal val cookieJar = PeekCookieJar()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        fun normalizeUsername(raw: String): String? {
            var value = raw.trim()
            if (value.isEmpty()) return null
            value = value.substringBefore('?').substringBefore('#').trimEnd('/')
            val instagramHost = Regex("""(?i)(?:https?://)?(?:www\.)?instagram\.com/([^/]+)""")
            instagramHost.find(value)?.let { value = it.groupValues[1] }
            value = value.removePrefix("@")
            value = value.substringAfterLast('/')
            if (value.lowercase() in setOf("p", "reel", "reels", "stories", "explore", "tv", "accounts", "direct")) return null
            if (!value.matches(Regex("""[A-Za-z0-9._]{1,30}"""))) return null
            if (value.startsWith(".") || value.endsWith(".")) return null
            return value.lowercase()
        }
    }
}

internal class PeekCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val hostCookies = store.getOrPut(url.host) { mutableListOf() }
        synchronized(hostCookies) {
            cookies.forEach { cookie ->
                val index = hostCookies.indexOfFirst { it.name == cookie.name }
                if (index >= 0) hostCookies[index] = cookie else hostCookies += cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val hostCookies = store[url.host] ?: return emptyList()
        val now = System.currentTimeMillis()
        return synchronized(hostCookies) {
            hostCookies.filter { it.matches(url) && (it.expiresAt == Long.MAX_VALUE || it.expiresAt > now) }
        }
    }

    fun value(name: String): String? = store.values.firstNotNullOfOrNull { hostCookies ->
        synchronized(hostCookies) { hostCookies.firstOrNull { it.name == name }?.value }
    }

    fun restore(cookies: List<Pair<String, String>>) {
        if (cookies.isEmpty()) return
        val hostCookies = store.getOrPut("www.instagram.com") { mutableListOf() }
        synchronized(hostCookies) {
            cookies.forEach { (name, value) ->
                if (name.isBlank() || value.isBlank()) return@forEach
                val cookie = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .domain("instagram.com")
                    .path("/")
                    .secure()
                    .httpOnly()
                    .expiresAt(Long.MAX_VALUE)
                    .build()
                val index = hostCookies.indexOfFirst { it.name == name }
                if (index >= 0) hostCookies[index] = cookie else hostCookies += cookie
            }
        }
    }

    fun clear() {
        store.clear()
    }
}
