package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.LocalTime

class ExerciseService(
    context: Context,
    private val healthDataStore: HealthDataStore,
    private val exerciseApiService: ExerciseApiService
) {
    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    /**
     * Fetches and syncs exercise data for the given date.
     *
     * @param skipDbCheck When true, skips the per-date DB deduplication check (caller
     *                    guarantees this date has not been synced yet, e.g. via bulk pre-load).
     * @return The fetched [HealthDataPoint] list, or null if the date was already synced
     *         and no fetch was performed.
     */
    suspend fun processExercises(
        dateTime: LocalDateTime,
        skipDbCheck: Boolean = false
    ): List<HealthDataPoint>? {
        val date = dateTime.toLocalDate()
        val alreadySynced = !skipDbCheck && syncLogDao.getSyncLog(date, SyncType.EXERCISE) != null
        if (!alreadySynced) {
            val data = readData(dateTime)
            if (data.isEmpty()) {
                Log.d(TAG, "No exercise data for $date, skipping.")
                return null
            }
            val returnAPI = sendDataToAPI(data)
            val failedCount = returnAPI.count { !it }
            if (failedCount == 0) {
                syncLogDao.insert(
                    SyncLog(
                        date = date,
                        syncType = SyncType.EXERCISE,
                        dateTime = LocalDateTime.now(),
                        totalRecords = returnAPI.size
                    )
                )
            } else {
                Log.e(TAG, "Exercise sync failed for $date: $failedCount/${returnAPI.size} records not accepted by API")
            }
            return data
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
            return null
        }
    }

    suspend fun readData(dateTime: LocalDateTime): List<HealthDataPoint> {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readDataRequest = DataTypes.EXERCISE.readDataRequestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .setOrdering(Ordering.DESC)
            .build()

        val dataList = healthDataStore.readData(readDataRequest).dataList

        return dataList.toModel(dataType = DataTypes.EXERCISE)
    }

    suspend fun sendDataToAPI(data: List<HealthDataPoint>): List<Boolean> {
        return exerciseApiService.sendListToApi(data)
    }
}
