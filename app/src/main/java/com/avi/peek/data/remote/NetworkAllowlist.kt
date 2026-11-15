package com.avi.peek.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * The only hosts Peek is allowed to contact.
 *
 * Instagram serves pages/API from instagram.com and media from
 * cdninstagram.com / fbcdn.net. Name discovery uses DuckDuckGo's HTML
 * endpoint. Anything else is a bug or a redirect we do not want to follow.
 */
object NetworkAllowlist {
    private val exact = setOf(
        "instagram.com",
        "www.instagram.com",
        "i.instagram.com",
        "cdninstagram.com",
        "www.cdninstagram.com",
        "duckduckgo.com",
        "html.duckduckgo.com",
    )

    private val suffixes = listOf(
        "instagram.com",
        "cdninstagram.com",
        "fbcdn.net",
        "duckduckgo.com",
    )

    fun isAllowed(host: String): Boolean {
        val clean = host.trim().trimEnd('.').lowercase()
        if (clean.isEmpty()) return false
        return clean in exact || suffixes.any { clean.endsWith(".$it") }
    }

    fun isAllowed(url: HttpUrl): Boolean = url.isHttps && isAllowed(url.host)

    fun isAllowedUrl(rawUrl: String): Boolean {
        val url = rawUrl.toHttpUrlOrNull() ?: return false
        return isAllowed(url)
    }
}

class NetworkAllowlistInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!NetworkAllowlist.isAllowed(request.url)) {
            throw IOException("Peek blocked a request to an unapproved host: ${request.url.host}")
        }
        return chain.proceed(request)
    }
}
