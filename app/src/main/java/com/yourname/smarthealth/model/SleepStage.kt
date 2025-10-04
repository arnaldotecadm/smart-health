package com.yourname.smarthealth.model

import java.time.Instant

data class SleepStage(
    val startTime: Instant,
    val endTime: Instant,
    val stage: SleepStageType
)
