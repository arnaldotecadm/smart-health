package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.model.DailySummary

class DailySummaryApiService(val apiBackend: ApiBackend) {

    suspend fun sendListToApi(
        dailySummaries: List<DailySummary>
    ): Boolean {
        if (dailySummaries.isEmpty()) return true
        val response = apiBackend.apiService.postDailySummaries(dailySummaries)
        if (response.isSuccessful) {
            return true
        } else {
            throw Exception("Failed to send daily summaries to API: ${response.code()} - ${response.message()}")
        }
    }

    suspend fun sendToApi(
        dailySummary: DailySummary
    ): Boolean {
        return sendListToApi(listOf(dailySummary))
    }
}