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
    private val context: Context,
    private val healthDataStore: HealthDataStore,
    private val exerciseApiService: ExerciseApiService
) {
    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    suspend fun processExercises(dateTime: LocalDateTime) {
        val date = dateTime.toLocalDate()
        if (syncLogDao.getSyncLog(date, SyncType.EXERCISE.name) == null) {
            val data = readData(dateTime)
            val returnAPI = sendDataToAPI(data)
            if (returnAPI.isNotEmpty() && returnAPI.all { it }) {
                syncLogDao.insert(
                    SyncLog(
                        date = date,
                        syncType = SyncType.EXERCISE,
                        dateTime = LocalDateTime.now(),
                        totalRecords = returnAPI.size
                    )
                )
            } else {
                Log.e(TAG, "Error sending data to API")
            }
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
            syncLogDao.deleteByDate(date)
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

    private suspend fun sendDataToAPI(data: List<HealthDataPoint>): List<Boolean> {
        return exerciseApiService.sendListToApi(data)
    }
}
