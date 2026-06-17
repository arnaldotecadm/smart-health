package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arvion.smarthealth.data.SyncDirection
import com.arvion.smarthealth.data.SyncPreferences
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.arvion.smarthealth.utils.Constants
import com.auth0.android.jwt.JWT
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Periodic background worker that continues syncing health data even when the app is closed.
 *
 * Scheduled via [schedule] when the user starts a full sync. Runs every [INTERVAL_MINUTES]
 * minutes as long as the device has a network connection. Each run processes up to
 * [MAX_DATES_PER_RUN] pending dates across all four sync types, resuming from
 * [SyncPreferences.lastSyncCursor].
 *
 * WorkManager survives app close (but not force-stop), making this the complement to
 * [SyncForegroundService] which handles the active foreground sync.
 */
class SyncDailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(Constants.TAG, "BackgroundSync: worker started")

        if (!PermissionService().hasAllPermissions(applicationContext)) {
            Log.w(Constants.TAG, "BackgroundSync: Samsung Health permissions not granted, skipping")
            return Result.failure()
        }

        val token = UserRepository(applicationContext).getValidToken()
        if (token == null) {
            Log.w(Constants.TAG, "BackgroundSync: no valid auth token, will retry")
            return Result.retry()
        }
        ApiBackend.authInterceptor.cachedToken = token

        return try {
            val syncedCount = processNextBatch()
            Log.i(Constants.TAG, "BackgroundSync: finished, processed $syncedCount dates")
            Result.success()
        } catch (e: Exception) {
            Log.e(Constants.TAG, "BackgroundSync: worker failed", e)
            Result.retry()
        }
    }

    private suspend fun processNextBatch(): Int {
        val syncPrefs = SyncPreferences(applicationContext)
        val config = syncPrefs.currentConfig
        val oldestDate = config.oldestDate

        val startDate = config.lastCursor
            ?: when (config.direction) {
                SyncDirection.BACKWARD -> LocalDate.now()
                SyncDirection.FORWARD -> oldestDate
            }

        val allDates = when (config.direction) {
            SyncDirection.BACKWARD ->
                generateSequence(startDate) { it.minusDays(1) }
                    .takeWhile { !it.isBefore(oldestDate) }
                    .toList()
            SyncDirection.FORWARD ->
                generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(LocalDate.now()) }
                    .toList()
        }

        val db = AppDatabase.getDatabase(applicationContext)
        val syncLogDao = db.syncLogDao()
        val syncedKeys = syncLogDao.getSyncedDatesInRange(oldestDate, LocalDate.now())
            .map { it.date to it.syncType }
            .toHashSet()

        val allTypes = SyncType.entries.toSet()
        val pendingDates = allDates
            .filter { date -> allTypes.any { type -> (date to type) !in syncedKeys } }
            .take(MAX_DATES_PER_RUN)

        if (pendingDates.isEmpty()) {
            Log.i(Constants.TAG, "BackgroundSync: nothing to sync, clearing cursor")
            syncPrefs.clearCursor()
            return 0
        }

        Log.i(Constants.TAG, "BackgroundSync: processing ${pendingDates.size} dates")

        val healthDataStore = HealthDataService.getStore(applicationContext)
        val exerciseService = ExerciseService(applicationContext, healthDataStore, ExerciseApiService(applicationContext))
        val sleepService = SleepService(applicationContext, healthDataStore, SleepApiService(applicationContext))
        val heartRateService = HeartRateService(applicationContext, healthDataStore, HeartRateSeriesApiService(applicationContext))
        val dailySummaryService = DailySummaryService(applicationContext, healthDataStore, DailySummaryApiService(ApiBackend(applicationContext)))

        var processed = 0
        for (date in pendingDates) {
            // Proactively refresh token if expiring within 10 minutes
            val cachedTok = ApiBackend.authInterceptor.cachedToken
            if (cachedTok == null || isTokenExpiringSoon(cachedTok)) {
                val fresh = UserRepository(applicationContext).getValidToken()
                    ?: return processed // can't refresh — stop, WorkManager will retry next run
                ApiBackend.authInterceptor.cachedToken = fresh
            }

            val dateTime = date.atStartOfDay()

            if ((date to SyncType.EXERCISE) !in syncedKeys) {
                withTimeoutOrNull(25_000L) {
                    exerciseService.processExercises(dateTime, skipDbCheck = true)
                } ?: Log.w(Constants.TAG, "BackgroundSync: exercise timed out for $date")
            }

            if ((date to SyncType.SLEEP) !in syncedKeys) {
                withTimeoutOrNull(25_000L) {
                    sleepService.processSleepSession(dateTime, skipDbCheck = true)
                } ?: Log.w(Constants.TAG, "BackgroundSync: sleep timed out for $date")
            }

            if ((date to SyncType.HEART_RATE) !in syncedKeys) {
                withTimeoutOrNull(25_000L) {
                    heartRateService.processHeartRates(dateTime, skipDbCheck = true)
                } ?: Log.w(Constants.TAG, "BackgroundSync: heart rate timed out for $date")
            }

            if ((date to SyncType.DAILY_SUMMARY) !in syncedKeys) {
                withTimeoutOrNull(25_000L) {
                    dailySummaryService.processDailySummary(dateTime, skipDbCheck = true)
                } ?: Log.w(Constants.TAG, "BackgroundSync: daily summary timed out for $date")
            }

            syncPrefs.lastSyncCursor = date
            processed++
        }

        return processed
    }

    private fun isTokenExpiringSoon(token: String): Boolean = try {
        JWT(token).isExpired(600)
    } catch (e: Exception) {
        true
    }

    companion object {
        const val WORK_NAME = "SmartHealthBackgroundSync"
        private const val MAX_DATES_PER_RUN = 50
        private const val INTERVAL_MINUTES = 30L

        /**
         * Enqueues the periodic background sync. Safe to call multiple times —
         * [ExistingPeriodicWorkPolicy.KEEP] leaves an already-running schedule untouched.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncDailySummaryWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)

            Log.i(Constants.TAG, "BackgroundSync: periodic sync scheduled every ${INTERVAL_MINUTES}min")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            Log.i(Constants.TAG, "BackgroundSync: periodic sync cancelled")
        }
    }
}
