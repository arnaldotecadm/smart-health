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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
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
     * @param prefetchedSleepScore Sleep score already extracted from a range read.
     *                              When provided, avoids a duplicate Samsung Health SDK read.
     * @param skipDbCheck When true, skips the per-date DB deduplication check (caller
     *                    guarantees this date has not been synced yet, e.g. via bulk pre-load).
     */
    suspend fun processDailySummary(
        dateTime: LocalDateTime,
        prefetchedExercise: List<HealthDataPoint>? = null,
        prefetchedSleepScore: Long? = null,
        skipDbCheck: Boolean = false
    ) {
        val date = dateTime.toLocalDate()
        val alreadySynced =
            !skipDbCheck && syncLogDao.getSyncLog(date, SyncType.DAILY_SUMMARY) != null
        if (!alreadySynced) {
            val data = readData(dateTime, prefetchedExercise, prefetchedSleepScore)
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
                Log.d(TAG, "Daily summary sync successful for $date")
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
     * Syncs daily summaries for all [dates] using prefetched exercise and sleep data,
     * avoiding redundant SDK calls for those types.
     * Sleep scores for all pending dates are fetched in a single range SDK call.
     * Per-day aggregate metrics (steps, calories, distance, active time) still require
     * individual SDK aggregate calls since the Samsung Health SDK does not bucket
     * multi-day aggregate filters.
     */
    suspend fun processBatch(
        dates: List<LocalDate>,
        syncedKeys: Set<Pair<LocalDate, SyncType>>,
        prefetchedExerciseByDate: Map<LocalDate, List<HealthDataPoint>> = emptyMap()
    ) {
        val pendingDates = dates.filter { (it to SyncType.DAILY_SUMMARY) !in syncedKeys }
        if (pendingDates.isEmpty()) return

        // Fetch sleep scores for all dates in a single SDK call.
        // (The raw SDK object holds SLEEP_SCORE; the mapped SleepSession model does not,
        //  so we cannot reuse prefetchedSleepByDate for this.)
        val sleepScoresByDate: Map<LocalDate, Long> =
            readSleepScoresForRange(dates.min(), dates.max())

        pendingDates.forEach { date ->
            val dateTime = date.atStartOfDay()
            val data = readData(
                dateTime,
                prefetchedExercise = prefetchedExerciseByDate[date],
                prefetchedSleepScore = sleepScoresByDate[date]
            )
            if (sendDataToAPI(data)) {
                syncLogDao.insert(SyncLog(
                    date = date,
                    syncType = SyncType.DAILY_SUMMARY,
                    dateTime = LocalDateTime.now(),
                    totalRecords = 1
                ))
                Log.d(TAG, "Daily summary sync successful for $date")
            } else {
                Log.e(TAG, "Daily summary sync failed for $date: API returned unsuccessful response")
            }
        }
    }

    /**
     * Fetches sleep scores for [startDate]–[endDate] in a single SDK call.
     * Returns a map of local date → sleep score (0 when no record exists for that date).
     */
    suspend fun readSleepScoresForRange(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Long> {
        val localtimeFilter = LocalTimeFilter.of(startDate.atTime(LocalTime.MIN), endDate.atTime(LocalTime.MAX))
        val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .setOrdering(Ordering.DESC)
            .build()
        return healthDataStore.readData(readDataRequest).dataList
            .groupBy { sdkPoint ->
                sdkPoint.startTime.atZone(ZoneId.systemDefault()).toLocalDate()
            }
            .mapValues { (_, points) ->
                (points.firstOrNull()?.getValue(DataType.SleepType.SLEEP_SCORE) ?: 0).toLong()
            }
    }

    /**
     * Reads all data required to build a [DailySummary].
     *
     * Aggregate metrics (steps, calories, distance, active time) run concurrently via [async].
     * When [prefetchedExercise] is supplied the exercise SDK read is skipped entirely.
     * When [prefetchedSleepScore] is supplied the sleep SDK read is skipped entirely.
     */
    suspend fun readData(
        dateTime: LocalDateTime,
        prefetchedExercise: List<HealthDataPoint>? = null,
        prefetchedSleepScore: Long? = null
    ): DailySummary = coroutineScope {
        val stepsDeferred = async { readTotalSteps(dateTime) }
        val activeTimeDeferred = async { readTotalActiveTimeInMinutes(dateTime) }
        val exerciseCalDeferred = async { readTotalActiveCaloriesBurned(dateTime) }
        val totalCalDeferred = async { readTotalCaloriesBurned(dateTime) }
        val distanceDeferred = async { readTotalDistance(dateTime) }
        val sleepScoreDeferred = if (prefetchedSleepScore != null) null
                                 else async { readSleepScore(dateTime) }

        DailySummary(
            userId = 0L,
            date = dateTime.toInstant(ZoneOffset.UTC),
            totalSteps = stepsDeferred.await(),
            activeTimeInMinutes = activeTimeDeferred.await(),
            exerciseCalories = exerciseCalDeferred.await(),
            totalBurnedCalories = totalCalDeferred.await(),
            distanceWhileActive = distanceDeferred.await(),
            sleepScore = prefetchedSleepScore ?: (sleepScoreDeferred?.await() ?: 0L)
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
        return sleepScore.toLong()
    }

    suspend fun sendDataToAPI(data: DailySummary): Boolean {
        return dailySummaryApiService.sendToApi(data)
    }

    suspend fun sendBatchToAPI(data: List<DailySummary>): Boolean {
        return dailySummaryApiService.sendListToApi(data)
    }
}