package com.avi.peek.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
fun DarkSystemBars() {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val old = controller.isAppearanceLightStatusBars
        val oldNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = old
            controller.isAppearanceLightNavigationBars = oldNav
        }
    }
}

@Composable
fun LightSystemBars() {
    val view = LocalView.current
    val activity = view.context.findActivity() ?: return
    DisposableEffect(Unit) {
        val controller = WindowCompat.getInsetsController(activity.window, view)
        val old = controller.isAppearanceLightStatusBars
        val oldNav = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = old
            controller.isAppearanceLightNavigationBars = oldNav
        }
    }
}
