package com.yourname.smarthealth.service.api

import com.yourname.smarthealth.utils.Utilities.gson
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel

open class ApiBackend {
    private val BASE_URL = "http://192.168.1.139:8080/"
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    open suspend fun sendListToApi(
        healthDataPoints: List<HealthDataPointModel>
    ) {
        healthDataPoints.forEach { sendToApi(it) }
    }

    open suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ) {
    }
}