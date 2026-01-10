package com.yourname.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.yourname.smarthealth.utils.Constants.TAG
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime

class DailySummary(val healthDataStore: HealthDataStore) {

    suspend fun allAtOnce(dateTime: LocalDateTime){
        readTotalDistance(dateTime)
        readTotalCaloriesBurned(dateTime)
        readTotalActiveCaloriesBurned(dateTime)
        readTotalActiveTimeInMinutes(dateTime)
        readTotalSteps(dateTime)
    }
    suspend fun readTotalDistance(dateTime: LocalDateTime) {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Distance", exception)
        }
    }

    suspend fun readTotalActiveTimeInMinutes(dateTime: LocalDateTime) {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Active Time", exception)
        }
    }

    suspend fun readTotalCaloriesBurned(dateTime: LocalDateTime) {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Calories Burned", exception)
        }
    }

    suspend fun readTotalActiveCaloriesBurned(dateTime: LocalDateTime) {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading total Active Calories Burned", exception)
        }
    }

    suspend fun readTotalSteps(dateTime: LocalDateTime) {
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
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
    }

    private fun aggregateRequest(
        dateTime: LocalDateTime,
        aggregateOperation: AggregateOperation<Duration, AggregateRequest.LocalTimeBuilder<Duration>>
    ): AggregateRequest<Duration> {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readRequest = aggregateOperation.requestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .build()
        return readRequest
    }
}