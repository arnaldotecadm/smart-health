package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkerParameters
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.utils.Constants
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.SleepApiService
import com.samsung.android.sdk.health.data.HealthDataService
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class SleepSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {

    override val syncType   = SyncType.SLEEP
    override val typeState  = SyncState.sleep
    override val batchDays  = 3

    override suspend fun syncDateBatch(
        dates: List<LocalDate>,
        syncedKeys: Set<Pair<LocalDate, SyncType>>
    ) {
        val store = HealthDataService.getStore(applicationContext)
        SleepService(applicationContext, store, SleepApiService(applicationContext))
            .processBatch(dates, syncedKeys)
    }

    override fun chainNextRun(context: Context) =
        enqueueImmediate<SleepSyncWorker>(context, WORK_NAME)

    companion object {
        const val WORK_NAME          = "SmartHealth_Sleep"
        private const val WORK_NAME_BG = "SmartHealth_Sleep_P"

        fun schedule(context: Context) {
            enqueueImmediate<SleepSyncWorker>(context, WORK_NAME)
            val periodic = PeriodicWorkRequestBuilder<SleepSyncWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(networkConstraints).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME_BG, ExistingPeriodicWorkPolicy.KEEP, periodic)
            Log.i(Constants.TAG, "SleepSync: scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME_BG)
            SyncState.sleep.isSyncing.value = false
            Log.i(Constants.TAG, "SleepSync: cancelled")
        }
    }
}
