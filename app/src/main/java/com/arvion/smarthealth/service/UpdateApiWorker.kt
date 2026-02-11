package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.arvion.smarthealth.utils.Constants
import com.samsung.android.sdk.health.data.HealthDataService
import java.time.LocalDateTime

class UpdateApiWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    val exerciseService = ExerciseService(
        context = appContext,
        healthDataStore = HealthDataService.getStore(applicationContext),
        exerciseApiService = ExerciseApiService()
    )

    val sleepService = SleepService(
        healthDataStore = HealthDataService.getStore(applicationContext),
        sleepApiService = SleepApiService()
    )

    val dailySummaryService = DailySummaryService(
        healthDataStore = HealthDataService.getStore(applicationContext),
        exerciserService = exerciseService,
        dailySummaryApiService = DailySummaryApiService(ApiBackend())
    )

    val heartRateService = HeartRateService(
        healthDataStore = HealthDataService.getStore(applicationContext),
        heartRateSeriesApiService = HeartRateSeriesApiService()
    )

    override suspend fun doWork(): Result {
        return try {
            Log.i(Constants.TAG, "Processing exercises in background")
            val dateTimeToRetrieve = LocalDateTime.now()
            exerciseService.processExercises(dateTimeToRetrieve)
            sleepService.processSleepSession(dateTimeToRetrieve)
            dailySummaryService.processDailySummary(dateTimeToRetrieve)
            heartRateService.processHeartRates(dateTimeToRetrieve)
            Result.success()
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Error processing exercises in background", e)
            Result.failure()
        }
    }
}