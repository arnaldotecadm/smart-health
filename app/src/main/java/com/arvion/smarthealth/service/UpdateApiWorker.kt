package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.arvion.smarthealth.utils.Constants
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime

class UpdateApiWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val healthDataStore = HealthDataService.getStore(applicationContext)

    private val exerciseService = ExerciseService(
        context = appContext,
        healthDataStore = healthDataStore,
        exerciseApiService = ExerciseApiService(appContext)
    )

    private val sleepService = SleepService(
        context = appContext,
        healthDataStore = healthDataStore,
        sleepApiService = SleepApiService(appContext)
    )

    private val dailySummaryService = DailySummaryService(
        context = appContext,
        healthDataStore = healthDataStore,
        dailySummaryApiService = DailySummaryApiService(ApiBackend(appContext))
    )

    private val heartRateService = HeartRateService(
        context = appContext,
        healthDataStore = healthDataStore,
        heartRateSeriesApiService = HeartRateSeriesApiService(appContext)
    )

    override suspend fun doWork(): Result {
        return try {
            Log.i(Constants.TAG, "Processing health data in background")

            // Prime auth token once for all HTTP calls in this run
            ApiBackend.authInterceptor.cachedToken = UserRepository(applicationContext).getJwtToken()

            val dateTime = LocalDateTime.now()

            // Run Exercise, Sleep, and HeartRate concurrently.
            // DailySummary follows after Exercise finishes so it can reuse pre-fetched data.
            coroutineScope {
                val exerciseDeferred = async { exerciseService.processExercises(dateTime) }
                val sleepDeferred    = async { sleepService.processSleepSession(dateTime) }
                val hrDeferred       = async { heartRateService.processHeartRates(dateTime) }

                val exerciseData = exerciseDeferred.await()
                sleepDeferred.await()
                hrDeferred.await()

                dailySummaryService.processDailySummary(dateTime, prefetchedExercise = exerciseData)
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Error processing health data in background", e)
            Result.failure()
        }
    }
}