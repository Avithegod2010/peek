package com.avi.peek.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.repo.BackupManager
import com.avi.peek.data.repo.DownloadStore
import com.avi.peek.data.repo.PeekRepository
import com.avi.peek.util.NetworkMonitor
import java.io.File

object ServiceLocator {

    private var appContextRef: Context? = null

    val appContext: Context
        get() = requireNotNull(appContextRef) { "ServiceLocator not initialised" }

    fun init(context: Context) {
        if (appContextRef == null) {
            appContextRef = context.applicationContext
        }
    }

    val settings: SettingsStore by lazy { SettingsStore(appContext) }

    val network: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    val okHttp by lazy { GuestInstagramClient.defaultClient() }

    val client: GuestInstagramClient by lazy { GuestInstagramClient(okHttp) }

    val database: PeekDatabase by lazy { PeekDatabase.build(appContext) }

    val downloads: DownloadStore by lazy { DownloadStore(appContext) }

    val repository: PeekRepository by lazy {
        PeekRepository(
            context = appContext,
            db = database,
            client = client,
            settings = settings,
            network = network,
            downloads = downloads,
        )
    }

    val backupManager: BackupManager by lazy { BackupManager(appContext, repository) }

    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(appContext)
            .crossfade(true)
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache"))
                    .maxSizeBytes(320L * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
