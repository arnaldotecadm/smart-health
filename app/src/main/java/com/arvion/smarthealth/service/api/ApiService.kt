package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.model.DailySummary
import com.arvion.smarthealth.model.HealthDataPoint
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("exercises")
    suspend fun postExercises(@Body postData: List<HealthDataPoint>): Response<Unit>

    @POST("sleeps")
    suspend fun postSleeps(@Body postData: List<HealthDataPoint>): Response<Unit>

    @POST("daily-summary")
    suspend fun postDailySummaries(@Body data: List<DailySummary>): Response<Unit>

    @POST("heart-rate")
    suspend fun postHeartRateSeries(@Body data: List<HealthDataPoint>): Response<Unit>
}