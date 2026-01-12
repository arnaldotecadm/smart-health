package com.yourname.smarthealth.model

import java.time.Duration
import java.time.Instant

data class HeartRateSeries(
    override val startTime: Instant,
    override val endTime: Instant,
    override val duration: Duration,
    val heartRate: Float,
    val max: Float,
    val min: Float
) : RecordSession
