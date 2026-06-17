package com.arvion.smarthealth.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.LocalDate

/**
 * Process-level singleton holding the live state of the health sync operation.
 *
 * [com.arvion.smarthealth.service.SyncForegroundService] writes to these flows while
 * [com.arvion.smarthealth.ui.sync.SyncViewModel] exposes them to the UI. Because both
 * share the same object, the Fragment updates correctly regardless of whether the app is
 * in the foreground or background.
 */
object SyncState {
    val isSyncing = MutableStateFlow(false)
    val currentSyncDate = MutableStateFlow<LocalDate?>(null)
    /** (processed, total) — both 0 when not running. */
    val syncProgress = MutableStateFlow(0 to 0)
    val statusMessage = MutableSharedFlow<String>(extraBufferCapacity = 4)
}
