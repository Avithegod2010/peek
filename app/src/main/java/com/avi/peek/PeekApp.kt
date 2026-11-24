package com.avi.peek

import android.app.Application
import android.os.StrictMode
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.avi.peek.di.ServiceLocator
import com.avi.peek.util.PeekProcessObserver
import com.avi.peek.util.PrivateAccess
import com.avi.peek.work.PeekNotifications
import com.avi.peek.work.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PeekApp : Application(), Configuration.Provider, ImageLoaderFactory {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build(),
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectLeakedSqlLiteObjects()
                    .penaltyLog()
                    .build(),
            )
        }
        ServiceLocator.init(this)
        PeekNotifications.createChannel(this)
        PrivateAccess.onLocked = {
            appScope.launch {
                runCatching { ServiceLocator.repository.sealPrivateVault() }
            }
        }
        // ON_STOP is the only "Peek left the screen" signal that covers every
        // exit (home, recents, screen off, another app). It re-seals revealed
        // private content and re-arms the app lock.
        ProcessLifecycleOwner.get().lifecycle.addObserver(PeekProcessObserver())
        appScope.launch {
            ServiceLocator.settings.settings.collect { current ->
                PrivateAccess.setAutoLockMinutes(current.privateAutoLockMinutes)
            }
        }
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
