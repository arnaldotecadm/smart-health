package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.mapper.RecordSessionMapper.toDailySummaryActivityModel
import com.arvion.smarthealth.model.DailySummary
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class DailySummaryService(
    context: Context,
    val healthDataStore: HealthDataStore,
    val dailySummaryApiService: DailySummaryApiService
) {

    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    /**
     * Builds and syncs the daily summary for the given date.
     *
     * @param prefetchedExercise Exercise data already fetched by [ExerciseService] for this date.
     *                           When provided, avoids a duplicate Samsung Health SDK read.
     * @param skipDbCheck When true, skips the per-date DB deduplication check (caller
     *                    guarantees this date has not been synced yet, e.g. via bulk pre-load).
     */
    suspend fun processDailySummary(
        dateTime: LocalDateTime,
        prefetchedExercise: List<HealthDataPoint>? = null,
        skipDbCheck: Boolean = false
    ) {
        val date = dateTime.toLocalDate()
        val alreadySynced =
            !skipDbCheck && syncLogDao.getSyncLog(date, SyncType.DAILY_SUMMARY) != null
        if (!alreadySynced) {
            val data = readData(dateTime, prefetchedExercise)
            val returnAPI = sendDataToAPI(data)
            if (returnAPI) {
                syncLogDao.insert(
                    SyncLog(
                        date = date,
                        syncType = SyncType.DAILY_SUMMARY,
                        dateTime = LocalDateTime.now(),
                        totalRecords = 1
                    )
                )
            } else {
                Log.e(
                    TAG,
                    "Daily summary sync failed for $date: API returned unsuccessful response"
                )
            }
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
        }
    }

    /**
     * Reads all data required to build a [DailySummary].
     *
     * All 7 Samsung Health SDK calls run concurrently via [async]. When [prefetchedExercise]
     * is supplied (already fetched by [ExerciseService] earlier in the same sync cycle), the
     * duplicate exercise SDK read is skipped entirely.
     */
    suspend fun readData(
        dateTime: LocalDateTime,
        prefetchedExercise: List<HealthDataPoint>? = null
    ): DailySummary = coroutineScope {
        val stepsDeferred = async { readTotalSteps(dateTime) }
        val activeTimeDeferred = async { readTotalActiveTimeInMinutes(dateTime) }
        val exerciseCalDeferred = async { readTotalActiveCaloriesBurned(dateTime) }
        val totalCalDeferred = async { readTotalCaloriesBurned(dateTime) }
        val distanceDeferred = async { readTotalDistance(dateTime) }
        val sleepScoreDeferred = async { readSleepScore(dateTime) }

        DailySummary(
            userId = 0L,
            date = dateTime.toInstant(ZoneOffset.UTC),
            totalSteps = stepsDeferred.await(),
            activeTimeInMinutes = activeTimeDeferred.await(),
            exerciseCalories = exerciseCalDeferred.await(),
            totalBurnedCalories = totalCalDeferred.await(),
            distanceWhileActive = distanceDeferred.await(),
            sleepScore = sleepScoreDeferred.await()
        )
    }

    suspend fun readTotalDistance(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest = DataType.ActivitySummaryType.TOTAL_DISTANCE.requestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .build()

        val dataList = healthDataStore.aggregateData(readRequest).dataList
        val total = dataList.sumOf { it.value?.toLong() ?: 0L }
        Log.d(TAG, "Daily total Distance: $total")
        return total
    }

    suspend fun readTotalActiveTimeInMinutes(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest = DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME.requestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .build()

        val dataList = healthDataStore.aggregateData(readRequest).dataList
        val total = dataList.sumOf { it.value?.toMinutes() ?: 0L }
        Log.d(TAG, "Daily total Active Time: $total")
        return total
    }

    suspend fun readTotalCaloriesBurned(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest = DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED.requestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .build()

        val dataList = healthDataStore.aggregateData(readRequest).dataList
        val total = dataList.sumOf { it.value?.toLong() ?: 0L }
        Log.d(TAG, "Daily total Calories Burned: $total")
        return total
    }

    suspend fun readTotalActiveCaloriesBurned(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest =
            DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED.requestBuilder
                .setLocalTimeFilter(localtimeFilter)
                .build()

        val dataList = healthDataStore.aggregateData(readRequest).dataList
        val total = dataList.sumOf { it.value?.toLong() ?: 0L }
        Log.d(TAG, "Daily total Active Calories Burned: $total")
        return total
    }

    suspend fun readTotalSteps(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .build()

        val dataList = healthDataStore.aggregateData(readRequest).dataList
        val dailyStepCount = dataList.sumOf { it.value as Long }
        Log.d(TAG, "Daily total steps: $dailyStepCount")
        return dailyStepCount
    }

    suspend fun readSleepScore(dateTime: LocalDateTime): Long {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .setOrdering(Ordering.DESC)
            .build()

        val dataList = healthDataStore.readData(readDataRequest).dataList
        val sleepScore = dataList.firstOrNull()?.getValue(DataType.SleepType.SLEEP_SCORE) ?: 0
        Log.d(TAG, "Daily sleep score: $sleepScore")
        return sleepScore.toLong()
    }

    suspend fun sendDataToAPI(data: DailySummary): Boolean {
        return dailySummaryApiService.sendToApi(data)
    }
}