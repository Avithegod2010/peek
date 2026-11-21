package com.avi.peek.data.remote

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * End-to-end JVM coverage for [NetworkAllowlistInterceptor] in front of a real
 * HTTP server.
 *
 * The MockWebServer is served on localhost but addressed with allowlisted host
 * names (DNS is pinned to loopback and its self-signed certificate carries the
 * matching SANs), so requests run through the full OkHttp interceptor chain --
 * redirect following included -- exactly as the app uses it.
 */
class AllowlistInterceptorTest {

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("www.instagram.com")
            .addSubjectAlternativeName("i.instagram.com")
            .addSubjectAlternativeName("scontent-arn2-1.cdninstagram.com")
            // Not allowlisted, but TLS-valid: the redirect test needs the
            // handshake to succeed so the allowlist, not the certificate, is
            // what refuses the follow-up request.
            .addSubjectAlternativeName("evil.example")
            .build()

        val serverCertificates = HandshakeCertificates.Builder()
            .heldCertificate(certificate)
            .build()
        server = MockWebServer()
        server.useHttps(serverCertificates.sslSocketFactory(), false)
        // Bind IPv4 loopback explicitly: the DNS override below always returns
        // 127.0.0.1, whatever the JVM's address preference is.
        server.start(InetAddress.getByName("127.0.0.1"), 0)

        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        client = OkHttpClient.Builder()
            .dns(LoopbackDns)
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .addInterceptor(NetworkAllowlistInterceptor())
            .addNetworkInterceptor(NetworkAllowlistInterceptor())
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun acceptsAllowlistedInstagramHost() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("profile"))

        client.newCall(get(url("www.instagram.com", "/p/abc/"))).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("profile", response.body?.string())
        }
        assertEquals("/p/abc/", server.takeRequest().path)
    }

    @Test
    fun acceptsAllowlistedCdnSubdomain() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("image"))

        client.newCall(get(url("scontent-arn2-1.cdninstagram.com", "/v/t51/x.jpg"))).execute().use { response ->
            assertEquals(200, response.code)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rejectsLookalikeHostBeforeAnyConnection() {
        val error = assertBlocked { url("instagram.com.evil.example", "/p/abc/") }

        assertTrue(error.message.orEmpty().contains("Peek blocked"))
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test
    fun rejectsCleartextEvenForAllowlistedHost() {
        val error = assertBlocked { "http://www.instagram.com:${server.port}/p/abc/" }

        assertTrue(error.message.orEmpty().contains("Peek blocked"))
        assertEquals("no request may reach the server", 0, server.requestCount)
    }

    @Test
    fun blocksRedirectFromAllowedHostToNonAllowlistedHost() {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "https://evil.example:${server.port}/tracker.jpg"),
        )

        val error = assertBlocked { url("www.instagram.com", "/p/abc/") }

        assertTrue(error.message.orEmpty().contains("Peek blocked"))
        assertTrue(error.message.orEmpty().contains("evil.example"))
        // The allowlisted request went out; the redirect target never did.
        assertEquals(1, server.requestCount)
    }

    private fun url(host: String, path: String): String = "https://$host:${server.port}$path"

    private fun get(url: String): Request = Request.Builder().url(url).build()

    private fun assertBlocked(url: () -> String): IOException {
        try {
            client.newCall(get(url())).execute().use { response ->
                fail("expected the allowlist to block this request, got HTTP ${response.code}")
            }
        } catch (expected: IOException) {
            return expected
        }
        throw AssertionError("unreachable: fail() always throws")
    }

    private object LoopbackDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> =
            listOf(InetAddress.getByName("127.0.0.1"))
    }
}
