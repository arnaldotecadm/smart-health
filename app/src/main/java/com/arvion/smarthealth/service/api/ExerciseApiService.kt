package com.arvion.smarthealth.service.api

import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class ExerciseApiService(context: Context) : ApiBackend(context) {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>): List<Boolean> {
        if (healthDataPoints.isEmpty()) return emptyList()
        return coroutineScope {
            healthDataPoints.map { async { sendToApi(it) } }.awaitAll()
        }
    }

    override suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ): Boolean {
        val response = apiService.postExercise(healthDataPoint)
        if (response.isSuccessful) {
            return true
        } else {
            throw Exception("Failed to send Exercises to API: ${response.code()} - ${response.message()}")
        }
    }
}