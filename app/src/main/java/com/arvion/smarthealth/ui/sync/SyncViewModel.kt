package com.arvion.smarthealth.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arvion.smarthealth.data.SyncConfig
import com.arvion.smarthealth.data.SyncPreferences
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.service.ExerciseSyncWorker
import com.arvion.smarthealth.service.HeartRateSyncWorker
import com.arvion.smarthealth.service.SleepSyncWorker
import com.arvion.smarthealth.service.SyncDailySummaryWorker
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Thin bridge between sync state and the Sync UI.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val syncPreferences = SyncPreferences(context)
    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    val isSyncing: StateFlow<Boolean> = combine(
        SyncState.exercise.isSyncing,
        SyncState.sleep.isSyncing,
        SyncState.heartRate.isSyncing,
        SyncState.dailySummary.isSyncing
    ) { ex, sl, hr, ds -> ex || sl || hr || ds }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val currentSyncDate: StateFlow<LocalDate?> = SyncState.currentSyncDate.asStateFlow()
    val syncProgress: StateFlow<Pair<Int, Int>> = SyncState.syncProgress.asStateFlow()
    val statusMessage: SharedFlow<String> = SyncState.statusMessage.asSharedFlow()

    /** Reactive sync configuration — updates automatically when preferences change. */
    val syncConfig: StateFlow<SyncConfig> = syncPreferences.observeChanges()
        .onStart { emit(Unit) }
        .map { syncPreferences.currentConfig }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            syncPreferences.currentConfig
        )

    /** Sync logs grouped by date, ordered newest first. */
    val syncLogs: StateFlow<List<DateSyncStatus>> = syncLogDao.getAllSyncLogs()
        .map { logs ->
            logs.groupBy { it.date }
                .map { (date, entries) ->
                    DateSyncStatus(date = date, syncedTypes = entries.map { it.syncType }.toSet())
                }
                .sortedByDescending { it.date }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun startSync() {
        ExerciseSyncWorker.schedule(context)
        SleepSyncWorker.schedule(context)
        HeartRateSyncWorker.schedule(context)
        SyncDailySummaryWorker.schedule(context)
    }

    fun stopSync() {
        ExerciseSyncWorker.cancel(context)
        SleepSyncWorker.cancel(context)
        HeartRateSyncWorker.cancel(context)
        SyncDailySummaryWorker.cancel(context)
    }
}
