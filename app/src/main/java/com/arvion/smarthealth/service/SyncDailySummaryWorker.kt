package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.utils.NotificationHelper
import com.auth0.android.jwt.JWT
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class SyncDailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "SyncDailySummaryWorker"
    private val initialDate = LocalDate.parse("2024-01-01")

    override suspend fun doWork(): Result {
        Log.d(TAG, "Background sync worker started at ${LocalDateTime.now()}")
        
        // 1. Check Samsung Health Permissions first
        val permissionService = PermissionService()
        if (!permissionService.hasAllPermissions(applicationContext)) {
            Log.d(TAG, "Samsung Health permissions not granted yet, skipping sync")
            // We don't retry here, we wait for user to open app and grant them
            return Result.failure()
        }

        val userRepository = UserRepository(applicationContext)
        
        // Wait up to 5 seconds for a valid token to appear (handles startup race conditions)
        var token: String? = null
        for (i in 1..5) {
            token = userRepository.getJwtToken()
            if (token != null && !JWT(token).isExpired(5)) break
            Log.d(TAG, "Waiting for token... attempt $i")
            kotlinx.coroutines.delay(1000)
        }

        if (token == null || JWT(token).isExpired(5)) {
            Log.d(TAG, "No valid token after waiting, skipping background sync")
            return Result.retry()
        }

        NotificationHelper.showNotification(
            applicationContext,
            0,
            "Sync Started",
            "Background sync for Daily Summary started at ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}"
        )

        return try {
            ApiBackend.authInterceptor.cachedToken = token
            val db = AppDatabase.getDatabase(applicationContext)
            val syncLogDao = db.syncLogDao()

            val dataStore = HealthDataService.getStore(applicationContext)
            val dailySummaryService = DailySummaryService(
                context = applicationContext,
                healthDataStore = dataStore,
                dailySummaryApiService = DailySummaryApiService(ApiBackend(applicationContext))
            )

            val endDate = LocalDate.now()

            // Find dates that need syncing
            val syncedDates = syncLogDao.getSyncedDatesInRange(initialDate, endDate)
                .filter { it.syncType == SyncType.DAILY_SUMMARY }
                .map { it.date }
                .toSet()

            val datesToSync = generateSequence(initialDate) { it.plusDays(1) }
                .takeWhile { !it.isAfter(endDate) }
                .filter { it !in syncedDates }
                .toList()

            if (datesToSync.isEmpty()) {
                Log.d(TAG, "No summaries to sync")
                NotificationHelper.showNotification(
                    applicationContext,
                    0,
                    "Sync Completed",
                    "All daily summaries are already up to date."
                )
            } else {
                Log.d(TAG, "Starting background sync for ${datesToSync.size} days")

                var count = 0
                // Limit execution to 1 minute
                withTimeoutOrNull(60_000) {
                    for (date in datesToSync) {
                        try {
                            dailySummaryService.processDailySummary(date.atStartOfDay(), skipDbCheck = true)
                            Log.d(TAG, "Synced daily summary for $date")
                            count++
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to sync daily summary for $date", e)
                        }
                    }
                }

                NotificationHelper.showNotification(
                    applicationContext,
                    0,
                    "Sync Finished",
                    "Successfully synced $count daily summaries."
                )
            }
            // Recursive scheduling on success
            scheduleNextRun()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker execution failed", e)
            NotificationHelper.showNotification(
                applicationContext,
                0,
                "Sync Error",
                "Background sync failed: ${e.localizedMessage}"
            )
            Result.retry()
        }
    }

    private fun scheduleNextRun() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val nextWork = OneTimeWorkRequestBuilder<SyncDailySummaryWorker>()
            .setInitialDelay(10, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "SyncDailySummaryRecursive",
            ExistingWorkPolicy.REPLACE,
            nextWork
        )
        Log.d(TAG, "Scheduled next sync in 10 minutes")
    }
}
