package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.time.LocalDateTime
import java.time.LocalTime

class HeartRateService(
    context: Context,
    val healthDataStore: HealthDataStore,
    val heartRateSeriesApiService: HeartRateSeriesApiService
) {

    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    suspend fun processHeartRates(dateTime: LocalDateTime, skipDbCheck: Boolean = false) {
        val date = dateTime.toLocalDate()
        val alreadySynced = !skipDbCheck && syncLogDao.getSyncLog(date, SyncType.HEART_RATE) != null
        if (!alreadySynced) {
            val data = readData(dateTime)
            if (data.isEmpty()) {
                Log.d(TAG, "No heart rate data for $date, skipping.")
                return
            }
            val batches = data.chunked(1_000)
            val returnAPI = coroutineScope {
                batches.mapIndexed { index, batch ->
                    async {
                        runCatching { sendToApi(batch) }
                            .onFailure { Log.e(TAG, "Heart rate batch ${index + 1}/${batches.size} failed for $date: ${it.message}") }
                            .getOrDefault(false)
                    }
                }.awaitAll()
            }
            val failedCount = returnAPI.count { !it }
            if (failedCount == 0) {
                syncLogDao.insert(
                    SyncLog(
                        date = date,
                        syncType = SyncType.HEART_RATE,
                        dateTime = LocalDateTime.now(),
                        totalRecords = returnAPI.size
                    )
                )
            } else {
                Log.e(TAG, "Heart rate sync failed for $date: $failedCount/${returnAPI.size} batches not accepted by API")
            }
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
        }
    }

    suspend fun readData(dateTime: LocalDateTime): List<HealthDataPoint> {
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
    }

    suspend fun sendToApi(data: List<HealthDataPoint>): Boolean {
        return heartRateSeriesApiService.sendListToApi(data).all { it }
    }
}