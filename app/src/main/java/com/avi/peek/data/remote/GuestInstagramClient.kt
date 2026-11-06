package com.avi.peek.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class GuestInstagramClient(
    private val okHttp: OkHttpClient = defaultClient(),
    private val parser: SsrParser = SsrParser(),
) {

    suspend fun fetchProfile(username: String): ScrapedProfile = withContext(Dispatchers.IO) {
        val url = "https://www.instagram.com/${username}/"
        val html = getHtml(url, isDocument = true)
        val parsed = parser.parseProfile(html, username)
            ?: throw classifyEmpty(html, username)
        parsed
    }

    suspend fun fetchPost(code: String): ScrapedPost = withContext(Dispatchers.IO) {
        val url = "https://www.instagram.com/p/$code/"
        val html = getHtml(url, isDocument = true, referer = "https://www.instagram.com/")
        val parsed = parser.parsePost(html, code)
            ?: throw classifyEmpty(html, code)
        parsed
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
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0.0.0 Safari/537.36"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
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
