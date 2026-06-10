package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.mapper.RecordSessionMapper.toDailySummaryActivityModel
import com.arvion.smarthealth.model.DailySummary
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.LocalTime

class DailySummaryService(
    context: Context,
    val healthDataStore: HealthDataStore,
    val exerciserService: ExerciseService,
    val dailySummaryApiService: DailySummaryApiService
) {

    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    suspend fun processDailySummary(dateTime: LocalDateTime) {
        val date = dateTime.toLocalDate()
        val syncLogByDateAndType = syncLogDao.getSyncLog(date, SyncType.DAILY_SUMMARY)
        if (syncLogByDateAndType == null) {
            val data = readData(dateTime)
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
                Log.e(TAG, "Error sending data to API")
            }
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
        }
        val afterProcessing = syncLogDao.getSyncLog(date, SyncType.DAILY_SUMMARY)
        Log.e(
            TAG,
            "After processing, sync log for $date and ${SyncType.DAILY_SUMMARY}: $afterProcessing"
        )
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
            userId = 0L,
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