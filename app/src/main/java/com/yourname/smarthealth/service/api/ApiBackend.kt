package com.yourname.smarthealth.service.api

import com.yourname.smarthealth.utils.Utilities.gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel

abstract class ApiBackend {
    private val BASE_URL = "http://192.168.1.131:8080/"
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    protected val apiService: ApiService = retrofit.create(ApiService::class.java)

    suspend fun sendListToApi(
        healthDataPoints: List<HealthDataPointModel>
    ) {
        healthDataPoints.forEach { sendToApi(it) }
    }

    abstract suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    )
}