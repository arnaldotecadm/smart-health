package com.arvion.smarthealth.model

import java.time.Duration

data class DailySummaryActivity(
    val calories: Float,
    val duration: Duration,
    val exerciseType: String,
    val dataSource: String
)
