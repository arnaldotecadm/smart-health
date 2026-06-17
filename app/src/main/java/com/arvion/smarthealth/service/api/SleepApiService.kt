package com.arvion.smarthealth.service.api

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class SleepApiService(context: Context) : ApiBackend(context) {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>): List<Boolean> {
        if (healthDataPoints.isEmpty()) return emptyList()
        val response = apiService.postSleeps(healthDataPoints)
        if (response.isSuccessful) {
            return List(healthDataPoints.size) { true }
        } else {
            throw Exception("Failed to send Sleeps to API: ${response.code()} - ${response.message()}")
        }
    }

    override suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ): Boolean {
        return sendListToApi(listOf(healthDataPoint)).first()
    }
}