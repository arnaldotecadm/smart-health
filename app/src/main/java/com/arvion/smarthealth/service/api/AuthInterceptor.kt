package com.arvion.smarthealth.service.api

import com.auth0.android.jwt.JWT
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor : Interceptor {

    @Volatile var cachedToken: String? = null

    /**
     * Optional provider invoked (on the OkHttp thread via runBlocking) whenever a token
     * is absent or near expiry, and again after a 401 response. Set once by [ApiBackend].
     */
    @Volatile var tokenProvider: (() -> String?)? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        // Proactively refresh if the cached token is missing or about to expire
        if (isExpiringSoon(cachedToken)) {
            tokenProvider?.invoke()?.also { cachedToken = it }
        }

        val request = chain.request().newBuilder().apply {
            cachedToken?.let { addHeader("Authorization", "Bearer $it") }
        }.build()

        val response = chain.proceed(request)

        // On 401, attempt one silent refresh and retry the original request
        if (response.code == 401) {
            response.close()
            cachedToken = null
            val freshToken = tokenProvider?.invoke()
            if (freshToken != null) {
                cachedToken = freshToken
                return chain.proceed(
                    request.newBuilder()
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                )
            }
        }

        return response
    }

    private fun isExpiringSoon(token: String?): Boolean {
        if (token == null) return true
        return try {
            JWT(token).isExpired(300) // refresh if expiring within 5 minutes
        } catch (e: Exception) {
            true
        }
    }
}
