package com.arvion.smarthealth.model

import java.time.Duration
import java.time.Instant

data class DailySummaryActivity(
    val calories: Float,
    val duration: Duration,
    val exerciseType: String,
    val startTime: Instant,
    val dataSource: String
)
