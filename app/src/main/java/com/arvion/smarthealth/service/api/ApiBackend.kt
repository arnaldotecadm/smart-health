package com.arvion.smarthealth.service.api

import android.content.Context
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.utils.Utilities.gson
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.arvion.smarthealth.model.HealthDataPoint as HealthDataPointModel

open class ApiBackend(context: Context) {
    private val BASE_URL = "http://192.168.1.131:8080/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(sharedClient)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    init {
        // Register the silent-refresh provider once. Every ApiBackend instance shares the
        // same authInterceptor singleton, so guard against overwriting an existing provider.
        if (authInterceptor.tokenProvider == null) {
            val appContext = context.applicationContext
            authInterceptor.tokenProvider = {
                runBlocking { UserRepository(appContext).getValidToken() }
            }
        }
    }

    companion object {
        val authInterceptor = AuthInterceptor()

        @Volatile
        private var _sharedClient: OkHttpClient? = null

        val sharedClient: OkHttpClient
            get() = _sharedClient ?: synchronized(this) {
                _sharedClient ?: OkHttpClient.Builder()
                    .addInterceptor(GzipRequestInterceptor())
                    .addInterceptor(authInterceptor)
                    .connectionPool(ConnectionPool(5, 2, TimeUnit.MINUTES))
                    .build()
                    .also { _sharedClient = it }
            }
    }

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
