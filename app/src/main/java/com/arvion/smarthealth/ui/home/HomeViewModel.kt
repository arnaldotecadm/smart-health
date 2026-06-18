package com.arvion.smarthealth.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.ExerciseSyncWorker
import com.arvion.smarthealth.service.HeartRateSyncWorker
import com.arvion.smarthealth.service.SleepSyncWorker
import com.arvion.smarthealth.service.SyncDailySummaryWorker
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val dao = AppDatabase.getDatabase(context).syncLogDao()

    // Last synced date per type (live from Room)
    val exerciseLastSync = dao.getLatestSyncDateFlow(SyncType.EXERCISE)
    val sleepLastSync = dao.getLatestSyncDateFlow(SyncType.SLEEP)
    val heartRateLastSync = dao.getLatestSyncDateFlow(SyncType.HEART_RATE)
    val dailySummaryLastSync = dao.getLatestSyncDateFlow(SyncType.DAILY_SUMMARY)

    // Per-type syncing state
    val exerciseSyncing: StateFlow<Boolean> = SyncState.exercise.isSyncing.asStateFlow()
    val sleepSyncing: StateFlow<Boolean> = SyncState.sleep.isSyncing.asStateFlow()
    val heartRateSyncing: StateFlow<Boolean> = SyncState.heartRate.isSyncing.asStateFlow()
    val dailySummarySyncing: StateFlow<Boolean> = SyncState.dailySummary.isSyncing.asStateFlow()

    // Per-type current date being synced
    val exerciseCurrentDate: StateFlow<LocalDate?> = SyncState.exercise.currentDate.asStateFlow()
    val sleepCurrentDate: StateFlow<LocalDate?> = SyncState.sleep.currentDate.asStateFlow()
    val heartRateCurrentDate: StateFlow<LocalDate?> = SyncState.heartRate.currentDate.asStateFlow()
    val dailySummaryCurrentDate: StateFlow<LocalDate?> = SyncState.dailySummary.currentDate.asStateFlow()

    fun startExercise() = ExerciseSyncWorker.schedule(context)
    fun stopExercise() = ExerciseSyncWorker.cancel(context)

    fun startSleep() = SleepSyncWorker.schedule(context)
    fun stopSleep() = SleepSyncWorker.cancel(context)

    fun startHeartRate() = HeartRateSyncWorker.schedule(context)
    fun stopHeartRate() = HeartRateSyncWorker.cancel(context)

    fun startDailySummary() = SyncDailySummaryWorker.schedule(context)
    fun stopDailySummary() = SyncDailySummaryWorker.cancel(context)
}
