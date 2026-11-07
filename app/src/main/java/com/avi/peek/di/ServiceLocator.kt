package com.avi.peek.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import com.avi.peek.data.local.LibraryStore
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.SearchStore
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GlobalSearchClient
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.repo.BackupManager
import com.avi.peek.data.repo.DownloadQueue
import com.avi.peek.data.repo.DownloadStore
import com.avi.peek.data.repo.PeekRepository
import com.avi.peek.data.repo.ProfileDownloadManager
import com.avi.peek.data.repo.RequestLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

    val searchStore: SearchStore by lazy { SearchStore(appContext) }

    val libraryStore: LibraryStore by lazy { LibraryStore(appContext) }

    val network: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    val okHttp by lazy { GuestInstagramClient.defaultClient() }

    val client: GuestInstagramClient by lazy { GuestInstagramClient(okHttp) }

    val globalSearch: GlobalSearchClient by lazy { GlobalSearchClient(okHttp) }

    val database: PeekDatabase by lazy { PeekDatabase.build(appContext) }

    val downloads: DownloadStore by lazy { DownloadStore(appContext) }

    private val appScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }

    val repository: PeekRepository by lazy {
        PeekRepository(
            context = appContext,
            db = database,
            client = client,
            settings = settings,
            network = network,
            downloads = downloads,
            requestLog = requestLog,
        )
    }

    val downloadQueue: DownloadQueue by lazy {
        DownloadQueue(repository, appScope, settings, network, appContext)
    }

    val profileDownloadManager: ProfileDownloadManager by lazy {
        ProfileDownloadManager(repository, downloadQueue, appScope)
    }

    val backupManager: BackupManager by lazy { BackupManager(appContext, repository) }

    val requestLog: RequestLog by lazy { RequestLog(appContext) }

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
