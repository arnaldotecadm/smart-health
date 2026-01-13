package com.yourname.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.mapper.RecordSessionMapper.toDailySummaryActivityModel
import com.yourname.smarthealth.model.DailySummary
import com.yourname.smarthealth.service.api.DailySummaryApiService
import com.yourname.smarthealth.utils.Constants.TAG
import java.time.LocalDateTime
import java.time.LocalTime

class DailySummaryService(
    val healthDataStore: HealthDataStore,
    val exerciserService: ExerciseService,
    val dailySummaryApiService: DailySummaryApiService
) {

    suspend fun processDailySummary(dateTime: LocalDateTime) {
        val data = readData(dateTime)
        sendDataToAPI(data)
    }

    suspend fun readData(dateTime: LocalDateTime): DailySummary {
        val totalSteps = readTotalSteps(dateTime)
        val activeTimeInMinutes = readTotalActiveTimeInMinutes(dateTime)
        val exerciseCalories = readTotalActiveCaloriesBurned(dateTime)
        val totalBurnedCalories = readTotalCaloriesBurned(dateTime)
        val distanceWhileActive = readTotalDistance(dateTime)
        val exerciseList = exerciserService.readData(dateTime = dateTime)
        val sleepScore = readSleepScore(dateTime)

        return DailySummary(
            date = dateTime.toLocalDate(),
            totalSteps = totalSteps,
            activeTimeInMinutes = activeTimeInMinutes,
            exerciseCalories = exerciseCalories,
            totalBurnedCalories = totalBurnedCalories,
            distanceWhileActive = distanceWhileActive,
            sleepScore = sleepScore,
            exerciseList = exerciseList.map { Pair(it.dataSource, it.sessions) }.flatMap { pair ->
                pair.second.map {
                    it.toDailySummaryActivityModel(
                        dataSource = "${pair.first?.appId}:${pair.first?.deviceId}"
                    )
                }
            }
        )
    }

    suspend fun readTotalDistance(dateTime: LocalDateTime): Long {
        try {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Distance", exception)
        }
        return 0
    }

    suspend fun readTotalActiveTimeInMinutes(dateTime: LocalDateTime): Long {
        try {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Active Time", exception)
        }
        return 0
    }

    suspend fun readTotalCaloriesBurned(dateTime: LocalDateTime): Long {
        try {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Calories Burned", exception)
        }
        return 0
    }

    suspend fun readTotalActiveCaloriesBurned(dateTime: LocalDateTime): Long {
        try {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Active Calories Burned", exception)
        }
        return 0
    }

    suspend fun readTotalSteps(dateTime: LocalDateTime): Long {
        try {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
        return 0
    }

    suspend fun readSleepScore(dateTime: LocalDateTime): Long {
        try {
            val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
            val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

            val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
            val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(localtimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val dataList = healthDataStore.readData(readDataRequest).dataList
            val sleepScore = dataList.first().getValue(DataType.SleepType.SLEEP_SCORE) ?: 0
            Log.d(TAG, "Daily sleep score: $sleepScore")
            return sleepScore.toLong()
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
        return 0
    }

    suspend fun sendDataToAPI(data: DailySummary) {
        try {
            dailySummaryApiService.sendToApi(data)
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
    }
}