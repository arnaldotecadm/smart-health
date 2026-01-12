package com.yourname.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.mapper.HealthDataPointMapper.toModel
import com.yourname.smarthealth.model.HealthDataPoint
import com.yourname.smarthealth.service.api.ExerciseApiService
import com.yourname.smarthealth.utils.Constants.TAG
import java.time.LocalDateTime
import java.time.LocalTime

class ExerciseService(
    val healthDataStore: HealthDataStore,
    val exerciseApiService: ExerciseApiService
) {

    suspend fun processExercises(dateTime: LocalDateTime){
        val exercises = readExercises(dateTime)
        sendToApi(exercises)
    }
    suspend fun readExercises(dateTime: LocalDateTime): List<HealthDataPoint> {
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

    suspend fun sendToApi(data: List<HealthDataPoint>) {
        try {
            exerciseApiService.sendListToApi(data)
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
    }
}