package com.arvion.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.utils.Constants.TAG
import java.time.LocalDateTime
import java.time.LocalTime

class ExerciseService(
    val healthDataStore: HealthDataStore,
    val exerciseApiService: ExerciseApiService
) {

    suspend fun processExercises(dateTime: LocalDateTime){
        val data = readData(dateTime)
        sendDataToAPI(data)
    }
    suspend fun readData(dateTime: LocalDateTime): List<HealthDataPoint> {
        try {
            val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
            val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

            val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
            val readDataRequest = DataTypes.EXERCISE.readDataRequestBuilder
                .setLocalTimeFilter(localtimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val dataList = healthDataStore.readData(readDataRequest).dataList

            return dataList.toModel(dataType = DataTypes.EXERCISE)

        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
        return emptyList()
    }

    suspend fun sendDataToAPI(data: List<HealthDataPoint>) {
        try {
            exerciseApiService.sendListToApi(data)
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
    }
}