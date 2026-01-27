package com.arvion.smarthealth.service.api

import android.util.Log
import com.arvion.smarthealth.utils.Constants.TAG
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class SleepApiService : ApiBackend() {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>) {
        healthDataPoints.forEach { sendToApi(it) }
    }

    override suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ) {
        try {
            val response = apiService.postSleep(healthDataPoint)
            if (response.isSuccessful) {
                return response.body()!!
            } else {
                Log.i(
                    TAG,
                    "There was an error while trying to persist the data into the backend: ${response.errorBody()}"
                )
            }
        } catch (e: Exception) {
            Log.i(
                TAG,
                "There was an error while trying to persist the data into the backend: ${e.message}"
            )
        }
    }
}