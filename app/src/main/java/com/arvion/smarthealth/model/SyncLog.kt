package com.arvion.smarthealth.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(tableName = "sync_log")
data class SyncLog(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val date: LocalDate,
    val syncType: SyncType,
    val dateTime: LocalDateTime,
    val totalRecords: Int
)
