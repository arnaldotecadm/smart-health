package com.yourname.smarthealth.service.api

import android.util.Log
import com.yourname.smarthealth.utils.Constants.TAG
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel

class ExerciseApiService : ApiBackend() {

    override suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ) {
        try {
            val response = apiService.postExercise(healthDataPoint)
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