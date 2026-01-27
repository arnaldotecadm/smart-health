package com.arvion.smarthealth.model

import java.time.LocalDate

data class DailySummary(
    val date: LocalDate,
    val totalSteps: Long,
    val activeTimeInMinutes: Long,
    val exerciseCalories: Long,
    val totalBurnedCalories: Long,
    val distanceWhileActive: Long,
    val sleepScore: Long,
    val exerciseList: List<DailySummaryActivity>
)
