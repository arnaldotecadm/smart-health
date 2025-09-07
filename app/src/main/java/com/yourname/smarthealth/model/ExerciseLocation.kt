package com.yourname.smarthealth.model

import java.time.Instant

data class ExerciseLocation(
    val accuracy: Float?,
    val altitude: Float?,
    val latitude: Float,
    val longitude: Float,
    val timestamp: Instant
)
