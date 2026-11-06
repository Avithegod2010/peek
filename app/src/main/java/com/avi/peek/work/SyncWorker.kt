package com.avi.peek.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.avi.peek.di.ServiceLocator
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return try {
            val settings = ServiceLocator.settings.settings.first()
            if (!settings.backgroundSync) return Result.success()
            val report = ServiceLocator.repository.syncAll(force = true) ?: return Result.success()
            if (settings.notifications) {
                PeekNotifications.postNewPosts(applicationContext, report)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

object SyncScheduler {
    private const val WORK_NAME = "peek-background-sync"

    fun schedule(context: Context, intervalHours: Int, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(intervalHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
