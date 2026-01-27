package com.arvion.smarthealth.model

import java.time.Instant

data class ExerciseLog(
    val cadence: Float?,
    val count: Int?,
    val heartRate: Float?,
    val power: Float?,
    val speed: Float?,
    val timestamp: Instant
)
