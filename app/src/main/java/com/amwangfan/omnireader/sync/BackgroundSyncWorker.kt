package com.amwangfan.omnireader.sync

import android.content.Context
import android.os.Build
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
import com.amwangfan.omnireader.data.DeviceRequest
import com.amwangfan.omnireader.data.LocalBook
import com.amwangfan.omnireader.data.LocalBookStore
import com.amwangfan.omnireader.data.OmniApi
import com.amwangfan.omnireader.data.PutProgressRequest
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit

class BackgroundSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    private val preferences = AppPreferences(appContext)
    private val api = OmniApi()
    private val localBookStore = LocalBookStore(appContext)

    override suspend fun doWork(): Result {
        val serverUrl = preferences.serverUrl
        if (serverUrl.isBlank() || preferences.accessToken.isBlank()) {
            return Result.success()
        }

        return try {
            authenticated { token ->
                api.upsertDevice(
                    serverUrl,
                    token,
                    DeviceRequest(
                        id = preferences.deviceId,
                        displayName = deviceDisplayName(),
                    ),
                )
            }
            val remoteBookIds = authenticated { token -> api.listBooks(serverUrl, token) }
                .mapTo(mutableSetOf()) { it.id }
            localBookStore.loadBooks()
                .filter { it.id in remoteBookIds }
                .forEach { reconcileProgress(serverUrl, it) }
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

    private suspend fun reconcileProgress(serverUrl: String, local: LocalBook) {
        val remote = authenticated { token -> api.getProgress(serverUrl, token, local.id) }
        val remoteUpdatedAt = remote?.let {
            runCatching { Instant.parse(it.updatedAt).toEpochMilli() }.getOrNull()
        }
        if (remote != null && remoteUpdatedAt == null) return
        when (decideProgressSync(local.progressUpdatedAtEpochMillis, remoteUpdatedAt)) {
            ProgressSyncAction.PullRemote -> {
                val chapterIndex = remote?.let { chapterIndexFromLocator(it.locator) } ?: return
                localBookStore.updateProgress(local.id, chapterIndex, remoteUpdatedAt ?: return)
            }
            ProgressSyncAction.PushLocal -> uploadLocalProgress(serverUrl, local)
            ProgressSyncAction.None -> Unit
        }
    }

    private suspend fun uploadLocalProgress(serverUrl: String, local: LocalBook) {
        if (local.progressUpdatedAtEpochMillis <= 0) return
        authenticated { token ->
            api.putProgress(
                serverUrl,
                token,
                local.id,
                PutProgressRequest(
                    deviceId = preferences.deviceId,
                    locator = "chapter:${local.currentChapterIndex}",
                    updatedAt = Instant.ofEpochMilli(local.progressUpdatedAtEpochMillis).toString(),
                ),
            )
        }
    }

    private suspend fun <T> authenticated(block: suspend (String) -> T): T {
        val token = preferences.accessToken
        try {
            return block(token)
        } catch (error: ApiException) {
            if (error.statusCode != 401) throw error
        }
        val refreshToken = preferences.refreshToken
        if (refreshToken.isBlank()) throw ApiException(401, "Session expired")
        val refreshed = api.refresh(preferences.serverUrl, refreshToken)
        preferences.updateAccessToken(refreshed.accessToken)
        return block(refreshed.accessToken)
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

        private fun deviceDisplayName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { "Android device" }
    }
}
