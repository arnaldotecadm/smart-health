package com.yourname.smarthealth.model

import java.time.Duration
import java.time.Instant

data class SleepSession(
    override val startTime: Instant,
    override val endTime: Instant,
    override val duration: Duration,
    val stages: List<SleepStage>?,
) : RecordSession
