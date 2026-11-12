package com.avi.peek.widget

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.avi.peek.MainActivity
import com.avi.peek.R
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.PrivateAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings panic switch. It only hides private content; revealing again
 * always goes through the in-app biometric prompt.
 */
class PeekPanicTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val hidden = runCatching {
                ServiceLocator.settings.settings.first().privateContentHidden
            }.getOrDefault(false)
            withContextMain { render(hidden) }
        }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val hidden = runCatching {
                ServiceLocator.settings.settings.first().privateContentHidden
            }.getOrDefault(false)
            if (!hidden) {
                PrivateAccess.hide()
                runCatching { ServiceLocator.settings.setPrivateContentHidden(true) }
                runCatching { ServiceLocator.repository.sealPrivateVault() }
                withContextMain {
                    render(true)
                    Toast.makeText(this@PeekPanicTileService, "Private content hidden", Toast.LENGTH_SHORT).show()
                }
            } else {
                openApp()
            }
        }
    }

    private fun render(hidden: Boolean) {
        val tile = qsTile ?: return
        tile.label = if (hidden) "Peek private hidden" else "Hide Peek private"
        tile.state = if (hidden) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        }
        tile.updateTile()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private suspend fun withContextMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
    }
}
