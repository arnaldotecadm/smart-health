package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.model.DailySummary

class DailySummaryApiService(val apiBackend: ApiBackend) {

    suspend fun sendToApi(
        dailySummary: DailySummary
    ): Boolean {
        val response = apiBackend.apiService.postDailySummary(dailySummary)
        if (response.isSuccessful) {
            //return response.body()!!
            return true
        } else {
            throw Exception("Failed to send daily summary to API: ${response.code()} - ${response.message()}")
        }
    }
}