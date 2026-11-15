package com.avi.peek.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.staticCompositionLocalOf

fun Context.openUrl(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

fun Context.openInstagram(path: String = "") {
    val uri = Uri.parse("https://www.instagram.com/$path")
    val appIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.instagram.android")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(appIntent)
    } catch (_: ActivityNotFoundException) {
        openUrl(uri.toString())
    } catch (_: Exception) {
        openUrl(uri.toString())
    }
}

fun Context.shareText(text: String, title: String = "Share") {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    startActivity(Intent.createChooser(send, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Context.copyText(text: String) {
    val clipboard = getSystemService(android.content.ClipboardManager::class.java) ?: return
    val clip = android.content.ClipData.newPlainText("Peek", text).apply {
        description.extras = android.os.PersistableBundle().apply {
            // Same key as ClipDescription.EXTRA_IS_SENSITIVE, available from
            // API 33; setting it on older versions is harmless.
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
    }
    clipboard.setPrimaryClip(clip)
    clearSensitiveClipboardLater(clipboard, text)
}

private fun clearSensitiveClipboardLater(
    clipboard: android.content.ClipboardManager,
    copied: String,
) {
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        val current = clipboard.primaryClip?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)?.text?.toString()
        if (current == copied) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("", ""))
            }
        }
    }, SENSITIVE_CLIPBOARD_MILLIS)
}

private const val SENSITIVE_CLIPBOARD_MILLIS = 60_000L

class PeekHaptics(
    private val context: Context,
    private val enabled: Boolean,
) {
    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
    }.getOrNull()

    fun tap() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        } else {
            vibrate(VibrationEffect.createOneShot(12, 90))
        }
    }

    fun toggle() {
        if (!enabled) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else {
            vibrate(VibrationEffect.createOneShot(16, 120))
        }
    }

    fun confirm() {
        if (!enabled) return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
        } else {
            VibrationEffect.createOneShot(24, 180)
        }
        vibrate(effect)
    }

    fun longPress() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 14, 36, 20), -1))
    }

    fun heart() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 16, 28, 28), -1))
    }

    fun success() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 40, 18), -1))
    }

    fun error() {
        if (!enabled) return
        vibrate(VibrationEffect.createWaveform(longArrayOf(0, 30, 40, 30), -1))
    }

    private fun vibrate(effect: VibrationEffect) {
        runCatching { vibrator?.vibrate(effect) }
    }
}

val LocalPeekHaptics = staticCompositionLocalOf<PeekHaptics> {
    error("PeekHaptics not provided")
}
