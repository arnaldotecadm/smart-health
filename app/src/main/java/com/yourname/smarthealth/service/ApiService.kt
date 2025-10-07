package com.yourname.smarthealth.service

import com.yourname.smarthealth.model.HealthDataPoint as HealthDataPointModel
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST


interface ApiService {
    @POST("exercises")
    fun postExercise(@Body postData: HealthDataPointModel): Call<HealthDataPointModel>

    @POST("sleeps")
    fun postSleep(@Body postData: HealthDataPointModel): Call<HealthDataPointModel>
}
