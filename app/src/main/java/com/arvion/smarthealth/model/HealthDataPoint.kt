package com.arvion.smarthealth.model

import java.time.Instant
import java.time.ZoneOffset

data class HealthDataPoint(
    val clientDataId: String?,
    val clientVersion: Int?,
    val dataSource: DataSource?,
    val endTime: Instant?,
    val startTime: Instant,
    val uid: String,
    val updateTime: Instant?,
    val zoneOffset: ZoneOffset?,
    val sessions: List<RecordSession>
)