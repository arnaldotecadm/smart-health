package com.arvion.smarthealth.service.api

import com.arvion.smarthealth.utils.Utilities.gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

open class ApiBackend {
    private val BASE_URL = "http://192.168.1.131:8080/"

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(GzipRequestInterceptor())
        .build()
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    open suspend fun sendListToApi(
        healthDataPoints: List<HealthDataPointModel>
    ) {
    }

    open suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ) {
    }
}