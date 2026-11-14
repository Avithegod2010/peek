package com.avi.peek.ui.auth

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.updatePadding
import java.util.Locale

/**
 * Optional Instagram web sign-in. Peek never reads the password fields and
 * never injects JavaScript into the page. When Instagram finishes signing in,
 * the WebView's session cookies are returned to the Account screen, which
 * stores them encrypted with the Android Keystore.
 */
class InstagramLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var hostLabel: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var finished = false

    private val pollCookies = object : Runnable {
        override fun run() {
            if (finished) return
            val url = webView.url.orEmpty()
            val cookieHeader = CookieManager.getInstance()
                .getCookie("https://www.instagram.com") ?: ""
            if (!hasSessionCookie(cookieHeader)) {
                handler.postDelayed(this, 700)
                return
            }
            val lower = url.lowercase(Locale.US)
            val stillSigningIn = lower.contains("/accounts/login") ||
                lower.contains("/challenge") ||
                lower.contains("/two_factor") ||
                lower.contains("/checkpoint")
            if (stillSigningIn) {
                handler.postDelayed(this, 700)
                return
            }
            finishWithCookies()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Start from a clean Instagram cookie jar so a cancelled attempt cannot
        // silently reuse an older session the next time this screen opens.
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setBackgroundColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        val close = TextView(this).apply {
            text = "Cancel"
            setTextColor(Color.parseColor("#1B1B21"))
            textSize = 15f
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { finishCancelled() }
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, dp(12), 0)
        }
        val title = TextView(this).apply {
            text = "Instagram sign in"
            setTextColor(Color.parseColor("#1B1B21"))
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        hostLabel = TextView(this).apply {
            text = "instagram.com"
            setTextColor(Color.parseColor("#605A71"))
            textSize = 11f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }
        titles.addView(title)
        titles.addView(hostLabel)
        header.addView(
            close,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        header.addView(
            titles,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
        )

        webView = WebView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f,
            )
            setBackgroundColor(Color.WHITE)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.loadsImagesAutomatically = true
            settings.mediaPlaybackRequiresUserGesture = true
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            @Suppress("DEPRECATION")
            settings.setSavePassword(false)
            settings.userAgentString = MOBILE_USER_AGENT
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val uri = request.url
                    val host = uri.host?.lowercase(Locale.US).orEmpty()
                    val trusted = host == "instagram.com" ||
                        host.endsWith(".instagram.com") ||
                        host == "facebook.com" ||
                        host.endsWith(".facebook.com") ||
                        host == "fb.com" ||
                        host.endsWith(".fb.com") ||
                        host == "messenger.com" ||
                        host.endsWith(".messenger.com")
                    if (trusted) return false
                    return runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                        true
                    }.getOrDefault(true)
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    hostLabel.text = url?.let { uri ->
                        runCatching { android.net.Uri.parse(uri).host }.getOrNull()
                    } ?: "instagram.com"
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    super.onPageFinished(view, url)
                    handler.removeCallbacks(pollCookies)
                    handler.postDelayed(pollCookies, 700)
                }
            }
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        root.addView(webView)
        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars(),
            )
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        webView.loadUrl("https://www.instagram.com/accounts/login/")
        handler.postDelayed(pollCookies, 1_500)
    }

    private fun hasSessionCookie(header: String): Boolean = header
        .split(';')
        .any { part ->
            val trimmed = part.trim()
            trimmed.startsWith("sessionid=") && trimmed.substringAfter('=').isNotBlank()
        }

    private fun finishWithCookies() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        val cookieManager = CookieManager.getInstance()
        val urls = listOf(
            "https://www.instagram.com/",
            "https://www.instagram.com/accounts/login/",
            "https://i.instagram.com/",
        )
        val cookies = LinkedHashMap<String, String>()
        urls.forEach { url ->
            val header = cookieManager.getCookie(url).orEmpty()
            header.split(';').forEach { part ->
                val trimmed = part.trim()
                val index = trimmed.indexOf('=')
                if (index > 0) {
                    val name = trimmed.substring(0, index).trim()
                    val value = trimmed.substring(index + 1).trim()
                    if (name.isNotBlank() && value.isNotBlank()) cookies[name] = value
                }
            }
        }
        if (cookies["sessionid"].isNullOrBlank()) {
            finished = false
            handler.postDelayed(pollCookies, 700)
            return
        }
        val payload = ArrayList<String>(cookies.map { "${it.key}=${it.value}" })
        setResult(
            RESULT_OK,
            Intent().putStringArrayListExtra(EXTRA_COOKIES, payload),
        )
        finish()
    }

    private fun finishCancelled() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        setResult(RESULT_CANCELED)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_COOKIES = "instagram_cookies"
        private const val MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    }
}
