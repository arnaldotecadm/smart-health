package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.model.DailySummary
import com.arvion.smarthealth.model.HealthDataPoint
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface ApiService {
    @POST("exercises")
    suspend fun postExercise(@Body postData: HealthDataPoint): Response<Unit>

    @POST("sleeps")
    suspend fun postSleep(@Body postData: HealthDataPoint): Response<Unit>

    @POST("daily-summary")
    suspend fun postDailySummary(@Body data: DailySummary): Response<Unit>

    @POST("heart-rate")
    suspend fun postHeartRateSeries(@Body data: List<HealthDataPoint>): Response<Unit>
}