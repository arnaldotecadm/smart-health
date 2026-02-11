package com.arvion.smarthealth.service

import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.service.api.SleepApiService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.LocalTime

class SleepService(
    val healthDataStore: HealthDataStore,
    val sleepApiService: SleepApiService
) {

    suspend fun processSleepSession(dateTime: LocalDateTime) {
        val data = readData(dateTime)
        sendDataToAPI(data)
    }

    suspend fun readData(dateTime: LocalDateTime): List<HealthDataPoint> {
        val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
        val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

        val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
        val readDataRequest = DataTypes.SLEEP.readDataRequestBuilder
            .setLocalTimeFilter(localtimeFilter)
            .setOrdering(Ordering.DESC)
            .build()

        val dataList = healthDataStore.readData(readDataRequest).dataList

        return dataList.toModel(dataType = DataTypes.SLEEP)
    }

    suspend fun sendDataToAPI(data: List<HealthDataPoint>) {
        sleepApiService.sendListToApi(data)
    }
}