package com.arvion.smarthealth.service.api

import android.util.Log
import com.arvion.smarthealth.utils.Constants.TAG
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

class HeartRateSeriesApiService : ApiBackend() {

    override suspend fun sendListToApi(healthDataPoints: List<HealthDataPointModel>): List<Boolean> {
        val response = apiService.postHeartRateSeries(healthDataPoints)
        if (response.isSuccessful) {
            //return response.body()!!
            return listOf(true)
        } else {
            Log.i(
                TAG,
                "There was an error while trying to persist the data into the backend: ${response.errorBody()}"
            )
        }
        return listOf(false)
    }
}