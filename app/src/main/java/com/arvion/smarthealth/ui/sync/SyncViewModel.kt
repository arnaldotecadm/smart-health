package com.arvion.smarthealth.ui.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arvion.smarthealth.data.SyncConfig
import com.arvion.smarthealth.data.SyncPreferences
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.service.SyncForegroundService
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/**
 * Thin bridge between [SyncForegroundService] and the Sync UI.
 *
 * All mutable sync state lives in [SyncState] and is written by the service, so the UI
 * remains accurate whether the app is foregrounded or not.  This ViewModel only owns the
 * sync-log query (a pure Room/DB concern) and the start/stop commands.
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val syncPreferences = SyncPreferences(context)
    private val syncLogDao = AppDatabase.getDatabase(context).syncLogDao()

    // -------------------------------------------------------------------------
    // State (delegated to process-level SyncState singleton)
    // -------------------------------------------------------------------------

    val isSyncing: StateFlow<Boolean> = SyncState.isSyncing.asStateFlow()
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

    // -------------------------------------------------------------------------
    // Commands
    // -------------------------------------------------------------------------

    fun startSync() {
        context.startForegroundService(SyncForegroundService.startIntent(context))
    }

    fun stopSync() {
        context.startService(SyncForegroundService.stopIntent(context))
    }
}


