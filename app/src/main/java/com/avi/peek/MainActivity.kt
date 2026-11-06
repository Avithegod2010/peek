package com.avi.peek

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.avi.peek.di.ServiceLocator
import com.avi.peek.ui.PeekRoot
import com.avi.peek.util.DeepLinkBus
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DeepLinkBus.offer(intent?.dataString)

        var ready = false
        splash.setKeepOnScreenCondition { !ready }
        lifecycleScope.launch {
            ServiceLocator.settings.settings.first()
            ready = true
            if (BuildConfig.DEBUG) {
                val demo = intent?.getStringExtra("demo_username")
                if (!demo.isNullOrBlank()) {
                    ServiceLocator.settings.setOnboarded(true)
                    demo.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEachIndexed { index, username ->
                        runCatching { ServiceLocator.repository.follow(username) }
                        if (index < 3) delay(500)
                    }
                }
            }
        }

        setContent {
            val settings by ServiceLocator.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            settings?.let { PeekRoot(it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        DeepLinkBus.offer(intent.dataString)
    }
}
