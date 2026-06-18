package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.utils.Constants
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.samsung.android.sdk.health.data.HealthDataService
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class HeartRateSyncWorker(context: Context, params: WorkerParameters) : BaseSyncWorker(context, params) {

    override val syncType   = SyncType.HEART_RATE
    override val typeState  = SyncState.heartRate
    // 1 day at a time — heart rate records are voluminous; a single day can already
    // be hundreds of data points. A range call spanning multiple days risks
    // very large SDK responses and long serialization times.
    override val batchDays  = 1

    override suspend fun syncDateBatch(
        dates: List<LocalDate>,
        syncedKeys: Set<Pair<LocalDate, SyncType>>
    ) {
        val store = HealthDataService.getStore(applicationContext)
        HeartRateService(applicationContext, store, HeartRateSeriesApiService(applicationContext))
            .processBatch(dates, syncedKeys)
    }

    override fun chainNextRun(context: Context) =
        enqueueImmediate<HeartRateSyncWorker>(context, WORK_NAME)

    companion object {
        const val WORK_NAME          = "SmartHealth_HeartRate"
        private const val WORK_NAME_BG = "SmartHealth_HeartRate_P"

        fun schedule(context: Context) {
            enqueueImmediate<HeartRateSyncWorker>(context, WORK_NAME)
            val periodic = PeriodicWorkRequestBuilder<HeartRateSyncWorker>(
                PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES
            ).setConstraints(networkConstraints).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(WORK_NAME_BG, ExistingPeriodicWorkPolicy.KEEP, periodic)
            Log.i(Constants.TAG, "HeartRateSync: scheduled")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME)
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(WORK_NAME_BG)
            SyncState.heartRate.isSyncing.value = false
            Log.i(Constants.TAG, "HeartRateSync: cancelled")
        }
    }
}
