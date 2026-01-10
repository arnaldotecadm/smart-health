package com.yourname.smarthealth.service

import android.util.Log
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.mapper.HealthDataPointMapper.toModel
import com.yourname.smarthealth.service.api.SleepApiService
import com.yourname.smarthealth.utils.Constants.TAG
import java.time.LocalDateTime
import java.time.LocalTime

class SleepService(
    val healthDataStore: HealthDataStore,
    val sleepApiService: SleepApiService
) {

    suspend fun readSleep(dateTime: LocalDateTime) {
        try {
            val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
            val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

            val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
            val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(localtimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val dataList = healthDataStore.readData(readDataRequest).dataList

            val healthDataPoints = dataList.toModel(dataType = DataTypes.SLEEP)

            sleepApiService.sendListToApi(healthDataPoints)
        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
    }

}