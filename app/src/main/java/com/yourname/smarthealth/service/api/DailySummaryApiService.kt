package com.yourname.smarthealth.service.api

import android.util.Log
import com.yourname.smarthealth.model.DailySummary
import com.yourname.smarthealth.utils.Constants.TAG

class DailySummaryApiService(val apiBackend: ApiBackend) {

    suspend fun sendToApi(
        dailySummary: DailySummary
    ) {
        try {
            val response = apiBackend.apiService.postDailySummary(dailySummary)
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