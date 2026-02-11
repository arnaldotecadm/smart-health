package com.arvion.smarthealth.service.api

import android.util.Log
import com.arvion.smarthealth.model.DailySummary
import com.arvion.smarthealth.utils.Constants.TAG

class DailySummaryApiService(val apiBackend: ApiBackend) {

    suspend fun sendToApi(
        dailySummary: DailySummary
    ) {
        val response = apiBackend.apiService.postDailySummary(dailySummary)
        if (response.isSuccessful) {
            return response.body()!!
        } else {
            throw Exception("Failed to send daily summary to API: ${response.code()} - ${response.message()}")
        }
    }
}