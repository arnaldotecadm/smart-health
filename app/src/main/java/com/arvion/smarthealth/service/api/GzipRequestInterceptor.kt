package com.arvion.smarthealth.service.api

import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import okio.GzipSink
import okio.buffer

class GzipRequestInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Do not double-compress
        if (originalRequest.body == null ||
            originalRequest.header("Content-Encoding") != null
        ) {
            return chain.proceed(originalRequest)
        }

        val compressedBody = object : RequestBody() {
            override fun contentType(): MediaType? =
                originalRequest.body!!.contentType()

            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                originalRequest.body!!.writeTo(gzipSink)
                gzipSink.close()
            }
        }

        val compressedRequest = originalRequest.newBuilder()
            .header("Content-Encoding", "gzip")
            .method(originalRequest.method, compressedBody)
            .build()

        return chain.proceed(compressedRequest)
    }
}
