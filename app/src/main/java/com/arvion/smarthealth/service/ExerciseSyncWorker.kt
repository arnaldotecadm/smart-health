package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.utils.Constants
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.samsung.android.sdk.health.data.HealthDataService
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ExerciseSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {

    override val syncType   = SyncType.EXERCISE
    override val typeState  = SyncState.exercise
    override val batchDays  = 3

    override suspend fun syncDateBatch(
        dates: List<LocalDate>,
        syncedKeys: Set<Pair<LocalDate, SyncType>>
    ) {
        val store = HealthDataService.getStore(applicationContext)
        ExerciseService(applicationContext, store, ExerciseApiService(applicationContext))
            .processBatch(dates, syncedKeys)
    }

    override fun chainNextRun(context: Context) =
        enqueueImmediate<ExerciseSyncWorker>(context, WORK_NAME)

    companion object {
        const val WORK_NAME          = "SmartHealth_Exercise"
        private const val WORK_NAME_BG = "SmartHealth_Exercise_P"

        /** Start immediate one-time sync + schedule periodic background catch-up. */
        fun schedule(context: Context) {
            enqueueImmediate<ExerciseSyncWorker>(context, WORK_NAME)
            val periodic = PeriodicWorkRequestBuilder<ExerciseSyncWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(networkConstraints).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME_BG, ExistingPeriodicWorkPolicy.KEEP, periodic)
            Log.i(Constants.TAG, "ExerciseSync: scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME_BG)
            SyncState.exercise.isSyncing.value = false
            Log.i(Constants.TAG, "ExerciseSync: cancelled")
        }
    }
}
