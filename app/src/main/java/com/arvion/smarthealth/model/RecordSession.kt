package com.arvion.smarthealth.model

import java.time.Duration
import java.time.Instant

interface RecordSession {
    val startTime: Instant
    val endTime: Instant
    val duration: Duration
}