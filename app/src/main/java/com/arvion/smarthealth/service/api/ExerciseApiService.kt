package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class ExerciseApiService : ApiBackend() {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>): List<Boolean> {
        return healthDataPoints.map { sendToApi(it) }
    }

    override suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ): Boolean {
        val response = apiService.postExercise(healthDataPoint)
        if (response.isSuccessful) {
            //return response.body()!!
            return true
        } else {
            throw Exception("Failed to send Exercises to API: ${response.code()} - ${response.message()}")
        }
    }
}