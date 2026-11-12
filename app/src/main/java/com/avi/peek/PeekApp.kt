package com.avi.peek

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.PrivateAccess
import com.avi.peek.work.PeekNotifications
import com.avi.peek.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PeekApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        PeekNotifications.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                PrivateAccess.hide()
                appScope.launch {
                    runCatching { ServiceLocator.repository.sealPrivateVault() }
                }
            }
        })
        appScope.launch {
            runCatching { ServiceLocator.accountManager.ensureRestored() }
            runCatching { ServiceLocator.repository.sealPrivateVault() }
            val settings = ServiceLocator.settings.settings.first()
            if (settings.backgroundSync) {
                SyncScheduler.schedule(this@PeekApp, settings.backgroundIntervalHours, settings.wifiOnlySync)
            } else {
                SyncScheduler.cancel(this@PeekApp)
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun newImageLoader(): ImageLoader = ServiceLocator.imageLoader
}
