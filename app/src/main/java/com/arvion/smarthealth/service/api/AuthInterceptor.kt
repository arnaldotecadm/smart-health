package com.arvion.smarthealth.service.api

import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    @Volatile
    var cachedToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = cachedToken

        val request = chain.request().newBuilder().apply {
            token?.let { addHeader("Authorization", "Bearer $it") }
        }.build()

        val response = chain.proceed(request)

        if (response.code == 401) {
            cachedToken = null
        }
        return response
    }
}
