package com.yourname.smarthealth.service

import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


interface ApiService {
    @POST("exercises")
    fun createPost(@Body postData: HealthDataPointModel): Call<HealthDataPointModel>
}
