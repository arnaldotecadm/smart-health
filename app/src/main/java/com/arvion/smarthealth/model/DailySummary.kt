package com.arvion.smarthealth.model

import java.time.Instant

data class DailySummary(
    val userId: Long,
    val date: Instant,
    val totalSteps: Long,
    val activeTimeInMinutes: Long,
    val exerciseCalories: Long,
    val totalBurnedCalories: Long,
    val distanceWhileActive: Long,
    val sleepScore: Long
)
