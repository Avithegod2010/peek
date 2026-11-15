package com.avi.peek.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.avi.peek.data.local.LibraryStore
import com.avi.peek.data.local.PeekDatabase
import com.avi.peek.data.local.SearchStore
import com.avi.peek.data.local.SessionStore
import com.avi.peek.data.local.SettingsStore
import com.avi.peek.data.remote.GlobalSearchClient
import com.avi.peek.data.remote.GuestInstagramClient
import com.avi.peek.data.repo.AccountManager
import com.avi.peek.data.repo.BackupManager
import com.avi.peek.data.repo.DownloadQueue
import com.avi.peek.data.repo.DownloadStore
import com.avi.peek.data.repo.PeekRepository
import com.avi.peek.data.repo.PrivateBackupManager
import com.avi.peek.data.repo.ProfileDownloadManager
import com.avi.peek.data.repo.RequestBudget
import com.avi.peek.data.repo.RequestLog
import com.avi.peek.data.repo.VaultStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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

    val sessionStore: SessionStore by lazy { SessionStore(appContext) }

    val network: NetworkMonitor by lazy { NetworkMonitor(appContext) }

    val okHttp by lazy { GuestInstagramClient.defaultClient() }

    val client: GuestInstagramClient by lazy { GuestInstagramClient(okHttp) }

    val accountManager: AccountManager by lazy { AccountManager(sessionStore, client) }

    val requestBudget: RequestBudget by lazy { RequestBudget(appContext, settings) }

    val vault: VaultStore by lazy { VaultStore(appContext) }

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
            accountManager = accountManager,
            requestBudget = requestBudget,
            vault = vault,
            noteForCode = { code -> libraryStore.noteFor(code) },
        )
    }

    val downloadQueue: DownloadQueue by lazy {
        DownloadQueue(repository, appScope, settings, network, appContext)
    }

    val profileDownloadManager: ProfileDownloadManager by lazy {
        ProfileDownloadManager(repository, downloadQueue, appScope)
    }

    val backupManager: BackupManager by lazy { BackupManager(appContext, repository) }

    val privateBackupManager: PrivateBackupManager by lazy {
        PrivateBackupManager(appContext, database)
    }

    val requestLog: RequestLog by lazy { RequestLog(appContext) }

    val imageLoader: ImageLoader by lazy {
        val cacheMb = runBlocking {
            runCatching { settings.settings.first().imageCacheMb }.getOrDefault(320)
        }
        ImageLoader.Builder(appContext)
            .crossfade(true)
            .okHttpClient(okHttp)
            .memoryCache {
                MemoryCache.Builder(appContext)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(appContext.cacheDir, "image_cache"))
                    .maxSizeBytes(cacheMb.toLong() * 1024 * 1024)
                    .build()
            }
            .build()
    }
}
