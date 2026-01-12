package com.yourname.smarthealth.model

import java.time.LocalDate

data class DailySummary(
    val date: LocalDate,
    val totalSteps: Long,
    val activeTimeInMinutes: Long,
    val exerciseCalories: Long,
    val totalBurnedCalories: Long,
    val distanceWhileActive: Long,
    val exerciseList: List<DailySummaryActivity>
)
