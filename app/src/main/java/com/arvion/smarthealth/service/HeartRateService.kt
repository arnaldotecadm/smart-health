package com.arvion.smarthealth.service

import android.util.Log
import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.LocalTime

class HeartRateService(
    val healthDataStore: HealthDataStore,
    val heartRateSeriesApiService: HeartRateSeriesApiService
) {

    suspend fun processHeartRates(dateTime: LocalDateTime) {
        val data = readData(dateTime)
        data.chunked(1_000).forEach { batch ->
            sendToApi(batch)
        }
    }

    suspend fun readData(dateTime: LocalDateTime): List<HealthDataPoint> {
        try {
            val startTime = dateTime.toLocalDate().atTime(LocalTime.MIN)
            val endTime = dateTime.toLocalDate().atTime(LocalTime.MAX)

            val localtimeFilter = LocalTimeFilter.of(startTime, endTime)
            val readDataRequest = DataTypes.HEART_RATE.readDataRequestBuilder
                .setLocalTimeFilter(localtimeFilter)
                .setOrdering(Ordering.DESC)
                .build()

            val dataList = healthDataStore.readData(readDataRequest).dataList.filter {
                it.getValue(DataType.HeartRateType.SERIES_DATA)?.isNotEmpty() ?: false
            }

            return dataList.toModel(dataType = DataTypes.HEART_RATE)

        } catch (exception: Exception) {
            Log.e(TAG, "Error reading steps", exception)
        }
        return emptyList()
    }

    suspend fun sendToApi(data: List<HealthDataPoint>) {
        try {
            heartRateSeriesApiService.sendListToApi(data)
        } catch (exception: Exception) {
            Log.e(TAG, "Error Heart Rate Series", exception)
        }
    }
}