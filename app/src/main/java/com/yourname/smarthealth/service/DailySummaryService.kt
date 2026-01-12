package com.yourname.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.yourname.smarthealth.mapper.RecordSessionMapper.toDailySummaryActivityModel
import com.yourname.smarthealth.model.DailySummary
import com.yourname.smarthealth.service.api.DailySummaryApiService
import com.yourname.smarthealth.utils.Constants.TAG
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DailySummaryService(
    val healthDataStore: HealthDataStore,
    val exerciserService: ExerciseService,
    val dailySummaryApiService: DailySummaryApiService
) {

    suspend fun dailySummary(dateTime: LocalDateTime) {
        val totalSteps = readTotalSteps(dateTime)
        val activeTimeInMinutes = readTotalActiveTimeInMinutes(dateTime)
        val exerciseCalories = readTotalActiveCaloriesBurned(dateTime)
        val totalBurnedCalories = readTotalCaloriesBurned(dateTime)
        val distanceWhileActive = readTotalDistance(dateTime)
        val exerciseList = exerciserService.readExercises(dateTime = dateTime)

        val dailySummary = DailySummary(
            date = dateTime.toLocalDate(),
            totalSteps = totalSteps,
            activeTimeInMinutes = activeTimeInMinutes,
            exerciseCalories = exerciseCalories,
            totalBurnedCalories = totalBurnedCalories,
            distanceWhileActive = distanceWhileActive,
            exerciseList = exerciseList.map { Pair(it.dataSource, it.sessions) }.flatMap { pair ->
                pair.second.map {
                    it.toDailySummaryActivityModel(
                        dataSource = "${pair.first?.appId}:${pair.first?.deviceId}"
                    )
                }
            }
        )
        Log.d(TAG, "Daily Summary: $dailySummary")
        this.dailySummaryApiService.sendToApi(dailySummary)
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
}