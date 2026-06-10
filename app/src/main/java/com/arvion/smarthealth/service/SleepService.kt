package com.arvion.smarthealth.service

import android.content.Context
import android.util.Log
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.mapper.HealthDataPointMapper.toModel
import com.arvion.smarthealth.model.HealthDataPoint
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.SleepApiService
import com.arvion.smarthealth.utils.Constants.TAG
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.LocalDateTime
import java.time.LocalTime

class SleepService(
    context: Context,
    val healthDataStore: HealthDataStore,
    val sleepApiService: SleepApiService
) {
    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    suspend fun processSleepSession(dateTime: LocalDateTime) {
        val date = dateTime.toLocalDate()
        if (syncLogDao.getSyncLog(date, SyncType.SLEEP) == null) {
            val data = readData(dateTime)
            val returnAPI = sendDataToAPI(data)
            if (returnAPI.isNotEmpty() && returnAPI.all { it }) {
                syncLogDao.insert(
                    SyncLog(
                        date = date,
                        syncType = SyncType.SLEEP,
                        dateTime = LocalDateTime.now(),
                        totalRecords = returnAPI.size
                    )
                )
            } else {
                Log.e(TAG, "Error sending data to API")
            }
        } else {
            Log.d(TAG, "Data for $date has already been synced.")
        }
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

    suspend fun sendDataToAPI(data: List<HealthDataPoint>): List<Boolean> {
        return sleepApiService.sendListToApi(data)
    }
}