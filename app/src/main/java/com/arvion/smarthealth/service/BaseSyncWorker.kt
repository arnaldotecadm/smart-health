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
import com.arvion.smarthealth.data.SyncDirection
import com.arvion.smarthealth.data.SyncPreferences
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.utils.Constants
import com.auth0.android.jwt.JWT
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/**
 * Shared sync loop used by all four per-type workers.
 *
 * Each worker run processes as many pending dates as the [workerTimeBudgetMs] wall-clock
 * budget allows (default 8 min — WorkManager's safe upper limit). When the budget is
 * exhausted before all pending dates are processed, the worker calls [chainNextRun] to
 * immediately enqueue another one-time run so historical sync continues without waiting
 * for the 15-minute periodic interval.
 *
 * The 15-minute [PeriodicWorkRequest] (scheduled by each companion's [scheduleWork]) acts
 * only as a background safety net for new data once the history is fully caught up.
 */
abstract class BaseSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    /** The [SyncType] this worker handles. */
    abstract val syncType: SyncType

    /** Per-type live state exposed to the UI. */
    abstract val typeState: SyncState.TypeState

    /** How many dates per SDK range call (1 for HeartRate, 3 for others). */
    abstract val batchDays: Int

    /** Timeout applied to a single SDK batch call. */
    open val sdkCallTimeoutMs: Long get() = 90_000L

    /**
     * Total wall-clock budget per worker invocation.
     * Staying under 8 minutes gives a safe margin before WorkManager can kill the job.
     */
    open val workerTimeBudgetMs: Long get() = 8 * 60 * 1_000L

    /**
     * Perform the SDK read + API upload for [dates].
     * Called with [syncedKeys] already pre-loaded so individual service calls
     * can skip the per-date DB check.
     */
    abstract suspend fun syncDateBatch(
        dates: List<LocalDate>,
        syncedKeys: Set<Pair<LocalDate, SyncType>>
    )

    /**
     * Enqueue the next immediate one-time run of *this* worker.
     * Each concrete subclass implements this by enqueueing itself with
     * [ExistingWorkPolicy.KEEP] so parallel button taps don't stack up.
     */
    abstract fun chainNextRun(context: Context)

    // -------------------------------------------------------------------------
    // Core worker entry point
    // -------------------------------------------------------------------------

    override suspend fun doWork(): Result {
        Log.i(Constants.TAG, "${syncType.name}Sync: worker started")

        if (!PermissionService().hasAllPermissions(applicationContext)) {
            Log.w(Constants.TAG, "${syncType.name}Sync: Samsung Health permissions not granted")
            return Result.failure()
        }

        val token = UserRepository(applicationContext).getValidToken()
        if (token == null) {
            Log.w(Constants.TAG, "${syncType.name}Sync: no valid token, will retry")
            return Result.retry()
        }
        ApiBackend.authInterceptor.cachedToken = token
        typeState.isSyncing.value = true

        return try {
            val moreRemaining = processAllPending()
            if (moreRemaining) {
                // Budget exhausted — chain the next run immediately so sync continues
                // without waiting for the periodic interval.
                Log.i(Constants.TAG, "${syncType.name}Sync: chaining next run immediately")
                chainNextRun(applicationContext)
            } else {
                Log.i(Constants.TAG, "${syncType.name}Sync: all pending dates processed")
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(Constants.TAG, "${syncType.name}Sync: worker failed", e)
            Result.retry()
        } finally {
            typeState.isSyncing.value = false
            typeState.currentDate.value = null
        }
    }

    // -------------------------------------------------------------------------
    // Main sync loop
    // -------------------------------------------------------------------------

    /**
     * Loads all pending dates for this type and processes them until the time budget
     * is exhausted.
     *
     * @return `true` if the budget was hit before all dates were processed (caller should
     *         chain the next run); `false` if everything is synced.
     */
    private suspend fun processAllPending(): Boolean {
        val syncPrefs = SyncPreferences(applicationContext)
        val config = syncPrefs.getConfigForType(syncType)
        val oldestDate = config.oldestDate

        val startDate = config.lastCursor
            ?: when (config.direction) {
                SyncDirection.BACKWARD -> LocalDate.now()
                SyncDirection.FORWARD  -> oldestDate
            }

        val allDates = when (config.direction) {
            SyncDirection.BACKWARD ->
                generateSequence(startDate) { it.minusDays(1) }
                    .takeWhile { !it.isBefore(oldestDate) }
                    .toList()
            SyncDirection.FORWARD  ->
                generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(LocalDate.now()) }
                    .toList()
        }

        val syncedKeys = AppDatabase.getDatabase(applicationContext).syncLogDao()
            .getSyncedDatesInRange(oldestDate, LocalDate.now())
            .map { it.date to it.syncType }
            .toHashSet()

        val pendingDates = allDates.filter { (it to syncType) !in syncedKeys }

        if (pendingDates.isEmpty()) {
            Log.i(Constants.TAG, "${syncType.name}Sync: nothing pending, clearing cursor")
            syncPrefs.clearCursor(syncType)
            return false
        }

        Log.i(Constants.TAG, "${syncType.name}Sync: ${pendingDates.size} dates pending")

        val deadline = System.currentTimeMillis() + workerTimeBudgetMs

        for (batch in pendingDates.chunked(batchDays)) {
            if (System.currentTimeMillis() >= deadline) {
                Log.i(Constants.TAG, "${syncType.name}Sync: time budget reached, will continue next run")
                return true // more dates remain
            }

            refreshTokenIfNeeded()
            typeState.currentDate.value = batch.first()
            Log.d(Constants.TAG, "${syncType.name}Sync: batch ${batch.min()}–${batch.max()}")

            withTimeoutOrNull(sdkCallTimeoutMs) {
                syncDateBatch(batch, syncedKeys)
            } ?: Log.w(
                Constants.TAG,
                "${syncType.name}Sync: batch timed out for ${batch.min()}–${batch.max()}"
            )

            // Save cursor after each batch so a crash/kill resumes from here
            syncPrefs.setCursor(syncType, batch.last())
        }

        return false // all pending dates were processed
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private suspend fun refreshTokenIfNeeded() {
        val tok = ApiBackend.authInterceptor.cachedToken
        if (tok == null || runCatching { JWT(tok).isExpired(600) }.getOrDefault(true)) {
            val fresh = UserRepository(applicationContext).getValidToken()
            if (fresh != null) ApiBackend.authInterceptor.cachedToken = fresh
        }
    }

    // -------------------------------------------------------------------------
    // Shared scheduling helpers called from each companion object
    // -------------------------------------------------------------------------

    companion object {
        val networkConstraints: Constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        const val PERIODIC_INTERVAL_MINUTES = 15L

        /**
         * Enqueue a one-time immediate run.
         * [ExistingWorkPolicy.KEEP] means a run already in-flight won't be displaced.
         */
        inline fun <reified W : CoroutineWorker> enqueueImmediate(
            context: Context,
            workName: String
        ) {
            val request = OneTimeWorkRequestBuilder<W>()
                .setConstraints(networkConstraints)
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(workName, ExistingWorkPolicy.KEEP, request)
        }
    }
}
