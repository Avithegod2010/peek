package com.avi.peek.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Best-effort screenshot and screen-recording protection for private surfaces.
 *
 * Android cannot prevent a determined attacker with root or a camera, but
 * FLAG_SECURE keeps the content out of the recent-apps thumbnail and blocks
 * normal screenshot/screen-record paths. A reference count is used because a
 * private dialog or nested screen may acquire the flag while another one is
 * still active.
 */
object SecureWindow {
    private val counts = WeakHashMap<Activity, Int>()

    fun acquire(activity: Activity) {
        synchronized(counts) {
            val next = (counts[activity] ?: 0) + 1
            counts[activity] = next
            if (next == 1) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    fun release(activity: Activity) {
        synchronized(counts) {
            val current = counts[activity] ?: return
            if (current <= 1) {
                counts.remove(activity)
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                counts[activity] = current - 1
            }
        }
    }
}

@Composable
fun SecureScreen(enabled: Boolean = true) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity, enabled) {
        if (enabled && activity != null) SecureWindow.acquire(activity)
        onDispose {
            if (enabled && activity != null) SecureWindow.release(activity)
        }
    }
}
