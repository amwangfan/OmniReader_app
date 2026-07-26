package com.amwangfan.omnireader.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.amwangfan.omnireader.data.ApiException
import com.amwangfan.omnireader.data.AppPreferences
import com.amwangfan.omnireader.data.BookSyncState
import com.amwangfan.omnireader.data.DeviceIdentityProvider
import com.amwangfan.omnireader.data.LocalBook
import com.amwangfan.omnireader.data.LocalBookStore
import com.amwangfan.omnireader.data.OmniApi
import com.amwangfan.omnireader.data.OmniReadingSyncGateway
import com.amwangfan.omnireader.data.ReadingStateStore
import com.amwangfan.omnireader.data.ReadingSyncCoordinator
import java.io.IOException
import java.util.concurrent.TimeUnit

class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val preferences = AppPreferences(appContext)
    private val api = OmniApi()
    private val localBookStore = LocalBookStore(appContext)
    private val stateStore = ReadingStateStore(appContext.filesDir)
    private val identity = DeviceIdentityProvider(appContext, preferences).current()

    override suspend fun doWork(): Result {
        val serverUrl = preferences.serverUrl
        if (serverUrl.isBlank() || preferences.accessToken.isBlank()) {
            return Result.success()
        }

        return try {
            authenticated { token ->
                val coordinator = ReadingSyncCoordinator(
                    OmniReadingSyncGateway(api, serverUrl, token),
                    stateStore,
                    identity,
                )
                coordinator.syncAllDirty()
                localBookStore.loadBooks()
                    .mapNotNull { it.remoteProgressBookId() }
                    .distinct()
                    .forEach { coordinator.syncBook(it) }
            }
            Result.success()
        } catch (error: ApiException) {
            when {
                error.statusCode == 401 -> {
                    preferences.clearSession()
                    Result.failure()
                }
                error.statusCode >= 500 -> Result.retry()
                else -> Result.failure()
            }
        } catch (_: IOException) {
            Result.retry()
        } catch (_: Throwable) {
            Result.failure()
        }
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val token = preferences.accessToken
        if (token.isBlank()) throw ApiException(401, "Session expired")
        return block(token)
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "omnireader-periodic-sync"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<BackgroundSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}

private fun LocalBook.remoteProgressBookId(): String? =
    remoteBookId ?: id.takeIf { syncState == BookSyncState.SYNCED }
