package com.arvion.smarthealth.data

import com.arvion.smarthealth.model.SyncType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Process-level singleton holding live sync state.
 *
 * Workers write per-type state; ViewModels read it.
 * Legacy top-level flows kept for SyncFragment/SyncForegroundService compat.
 */
object SyncState {

    data class TypeState(
        val isSyncing: MutableStateFlow<Boolean> = MutableStateFlow(false),
        val currentDate: MutableStateFlow<LocalDate?> = MutableStateFlow(null),
        val progress: MutableStateFlow<Pair<Int, Int>> = MutableStateFlow(0 to 0)
    )

    val exercise = TypeState()
    val sleep = TypeState()
    val heartRate = TypeState()
    val dailySummary = TypeState()

    fun forType(type: SyncType): TypeState = when (type) {
        SyncType.EXERCISE -> exercise
        SyncType.SLEEP -> sleep
        SyncType.HEART_RATE -> heartRate
        SyncType.DAILY_SUMMARY -> dailySummary
    }

    // Legacy — SyncForegroundService and SyncFragment still compile against these
    val isSyncing = MutableStateFlow(false)
    val currentSyncDate = MutableStateFlow<LocalDate?>(null)
    val syncProgress = MutableStateFlow(0 to 0)
    val statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
