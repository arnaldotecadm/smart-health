package com.arvion.smarthealth.service.api

import android.content.Context
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class HeartRateSeriesApiService(context: Context) : ApiBackend(context) {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>): List<Boolean> {
        val response = apiService.postHeartRateSeries(healthDataPoints)
        if (response.isSuccessful) {
            //return response.body()!!
            return listOf(true)
        } else {
            throw Exception("Failed to send Heart Rate to API: ${response.code()} - ${response.message()}")
        }
    }
}