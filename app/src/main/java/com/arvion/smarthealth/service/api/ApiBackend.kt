package com.arvion.smarthealth.service.api

import android.content.Context
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.utils.Utilities.gson
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

open class ApiBackend(context: Context) {
    private val BASE_URL = "http://192.168.1.131:8080/"

    private val userRepository = UserRepository(context)

    val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(GzipRequestInterceptor())
        .addInterceptor(AuthInterceptor(userRepository))
        .build()
        private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    open suspend fun sendListToApi(
        healthDataPoints: List<HealthDataPointModel>
    ): List<Boolean> {
        return emptyList()
    }

    open suspend fun sendToApi(
        healthDataPoint: HealthDataPointModel
    ): Boolean {
        return false
    }
}
