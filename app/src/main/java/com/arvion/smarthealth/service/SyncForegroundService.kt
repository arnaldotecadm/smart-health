package com.arvion.smarthealth.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.arvion.smarthealth.MainActivity
import com.arvion.smarthealth.R
import com.arvion.smarthealth.data.SyncDirection
import com.arvion.smarthealth.data.SyncPreferences
import com.arvion.smarthealth.data.SyncState
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.SyncType
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.arvion.smarthealth.utils.Constants
import com.auth0.android.jwt.JWT
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import java.time.LocalDate
import kotlin.time.Duration.Companion.seconds

/**
 * Foreground service that runs the full health-data sync in a persistent process.
 *
 * Because it is a foreground service, Android will not kill the process when the user
 * presses Home or switches apps. A visible notification informs the user that sync is
 * in progress and lets them stop it via a notification action.
 *
 * Start via [startIntent]; stop via [stopIntent] or the notification "Stop" action.
 */
class SyncForegroundService : Service() {

    // Service-scoped coroutine — cancelled in onDestroy so no leaks.
    private val job = SupervisorJob()
    private val serviceScope = CoroutineScope(job + Dispatchers.Main)
    private var syncJob: Job? = null

    private lateinit var syncPreferences: SyncPreferences
    private lateinit var userRepository: UserRepository
    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        syncPreferences = SyncPreferences(applicationContext)
        userRepository = UserRepository(applicationContext)
        val healthDataStore = HealthDataService.getStore(applicationContext)

        exerciseService = ExerciseService(applicationContext, healthDataStore, ExerciseApiService(applicationContext))
        sleepService = SleepService(applicationContext, healthDataStore, SleepApiService(applicationContext))
        dailySummaryService = DailySummaryService(applicationContext, healthDataStore, DailySummaryApiService(ApiBackend(applicationContext)))
        heartRateService = HeartRateService(applicationContext, healthDataStore, HeartRateSeriesApiService(applicationContext))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startSync()
            ACTION_STOP -> stopSync()
        }
        // START_STICKY: if the process is killed (e.g. OEM memory pressure after clearing
        // from recents), Android will restart the service and re-deliver a null intent.
        // Combined with stopWithTask="false" in the manifest, this keeps sync alive
        // even when the user swipes the app away from the recents screen.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the user swipes the app away from recents.
     * Re-enqueue a START action so the service resumes after Android restarts the process.
     * Only re-starts if a sync was actually in progress when the task was removed.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (SyncState.isSyncing.value) {
            Log.i(Constants.TAG, "SyncService: task removed while syncing — re-scheduling start")
            startService(startIntent(this))
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // Sync orchestration
    // -------------------------------------------------------------------------

    private fun startSync() {
        if (SyncState.isSyncing.value) return

        startForeground(NOTIFICATION_ID, buildProgressNotification("Starting sync…"))

        syncJob = serviceScope.launch {
            val token = userRepository.getValidToken()
            if (token == null) {
                finishWithStatus("Sync Stopped", "Not signed in. Please sign in from the app menu.")
                return@launch
            }
            ApiBackend.authInterceptor.cachedToken = token

            SyncState.isSyncing.value = true
            SyncState.syncProgress.value = 0 to 0

            var stopTitle = "Sync Complete"
            var stopMessage = "All health data has been uploaded"
            try {
                runSync()
            } catch (e: CancellationException) {
                stopTitle = "Sync Paused"
                stopMessage = "Sync has been stopped. It will resume from the last checkpoint next time."
            } catch (e: Exception) {
                Log.e(Constants.TAG, "Sync error", e)
                stopTitle = "Sync Stopped"
                stopMessage = "Sync stopped due to an error: ${e.message ?: "unknown error"}"
            } finally {
                SyncState.isSyncing.value = false
                SyncState.currentSyncDate.value = null
                SyncState.syncProgress.value = 0 to 0
                SyncState.statusMessage.tryEmit(stopMessage)
                finishWithStatus(stopTitle, stopMessage)
            }
        }
    }

    private fun stopSync() {
        syncJob?.cancel()
        // finishWithStatus is called from the finally block in startSync
    }

    /**
     * Removes the foreground designation (keeping the notification visible and dismissable)
     * then updates its content to the final status before stopping the service.
     * This guarantees the user sees an outcome message regardless of app state.
     */
    private fun finishWithStatus(title: String, message: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Detach first so the notification becomes a regular (dismissable) one
        stopForeground(STOP_FOREGROUND_DETACH)
        // Then update content to show the final outcome
        nm.notify(NOTIFICATION_ID, buildStatusNotification(title, message))
        stopSelf()
    }

    private suspend fun runSync() {
        val db = AppDatabase.getDatabase(applicationContext)
        val syncLogDao = db.syncLogDao()

        val config = syncPreferences.currentConfig
        val oldestDate = config.oldestDate
        val startDate = config.lastCursor
            ?: when (config.direction) {
                SyncDirection.BACKWARD -> LocalDate.now()
                SyncDirection.FORWARD -> oldestDate
            }

        val allDates = when (config.direction) {
            SyncDirection.BACKWARD ->
                generateSequence(startDate) { it.minusDays(1) }
                    .takeWhile { !it.isBefore(oldestDate) }
                    .toList()
            SyncDirection.FORWARD ->
                generateSequence(startDate) { it.plusDays(1) }
                    .takeWhile { !it.isAfter(LocalDate.now()) }
                    .toList()
        }

        val syncedKeys = syncLogDao.getSyncedDatesInRange(oldestDate, LocalDate.now())
            .map { it.date to it.syncType }
            .toHashSet()

        val allTypes = SyncType.entries.toSet()
        val pendingDates = allDates.filter { date -> allTypes.any { type -> (date to type) !in syncedKeys } }
        val total = pendingDates.size
        val skipped = allDates.size - total

        Log.i(Constants.TAG, "SyncService: ${allDates.size} dates, $skipped already synced, $total pending")
        SyncState.syncProgress.value = 0 to total

        var processed = 0
        // Process 3 dates at a time. Each batch runs all dates concurrently, and within
        // each date Exercise+Sleep+HeartRate are themselves run in parallel so a single
        // batch of 3 has up to 9 independent Samsung Health SDK calls in-flight at once.
        // Daily Summary runs after Exercise per-date so it can reuse the fetched exercise data.
        allDates.chunked(3).forEach { batch ->
            yield()

            val batchNeedsSync = batch.any { date -> allTypes.any { type -> (date to type) !in syncedKeys } }
            if (!batchNeedsSync) {
                syncPreferences.lastSyncCursor = batch.last()
                return@forEach
            }

            // Token refresh once per batch (tokens expire in ~1 hour; check at every batch
            // boundary so we never hit the server mid-batch with a stale token).
            val cachedToken = ApiBackend.authInterceptor.cachedToken
            if (cachedToken == null || isTokenExpiringSoon(cachedToken)) {
                Log.i(Constants.TAG, "SyncService: token refresh needed at batch ${batch.first()}–${batch.last()}")
                val fresh = userRepository.getValidToken()
                if (fresh != null) {
                    ApiBackend.authInterceptor.cachedToken = fresh
                    Log.i(Constants.TAG, "SyncService: token refreshed successfully")
                } else {
                    throw IllegalStateException("Authentication token expired and could not be refreshed. Please sign in again.")
                }
            }

            val batchPending = batch.count { date -> allTypes.any { type -> (date to type) !in syncedKeys } }
            processed += batchPending
            val rangeLabel = if (batch.size == 1) batch.first().toString() else "${batch.first()} – ${batch.last()}"
            SyncState.currentSyncDate.value = batch.first()
            SyncState.syncProgress.value = processed to total
            updateProgressNotification(rangeLabel, processed to total)
            Log.d(Constants.TAG, "SyncService: batch $rangeLabel ($processed/$total)")

            // One SDK call per service type covers all 3 dates. Exercise, Sleep and HeartRate
            // run concurrently. Daily Summary follows with prefetched exercise data so it
            // makes zero extra exercise or sleep-score SDK reads.
            supervisorScope {
                val exerciseDeferred = async {
                    withTimeoutOrNull(90.seconds) {
                        exerciseService.processBatch(batch, syncedKeys)
                    }.also { if (it == null) Log.w(Constants.TAG, "SyncService: exercise batch timed out for $rangeLabel") }
                        ?: emptyMap()
                }

                val sleepJob = launch {
                    withTimeoutOrNull(90.seconds) {
                        sleepService.processBatch(batch, syncedKeys)
                    } ?: Log.w(Constants.TAG, "SyncService: sleep batch timed out for $rangeLabel")
                }

                launch {
                    withTimeoutOrNull(90.seconds) {
                        heartRateService.processBatch(batch, syncedKeys)
                    } ?: Log.w(Constants.TAG, "SyncService: heart rate batch timed out for $rangeLabel")
                }

                val exerciseByDate = exerciseDeferred.await()
                sleepJob.join()

                withTimeoutOrNull(90.seconds) {
                    dailySummaryService.processBatch(batch, syncedKeys, exerciseByDate)
                } ?: Log.w(Constants.TAG, "SyncService: daily summary batch timed out for $rangeLabel")
            }

            syncPreferences.lastSyncCursor = batch.last()
        }

        syncPreferences.clearCursor()
    }

    // -------------------------------------------------------------------------
    // Notification helpers
    // -------------------------------------------------------------------------

    /** Returns true if [token] is null or expires within 10 minutes. */
    private fun isTokenExpiringSoon(token: String): Boolean = try {
        JWT(token).isExpired(600) // 10-minute buffer
    } catch (e: Exception) {
        true
    }

    /** Ongoing progress notification shown while sync is running. */
    private fun buildProgressNotification(text: String, progress: Pair<Int, Int>? = null): android.app.Notification {
        val tapIntent = openAppIntent()
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (progress != null && progress.second > 0) {
            "$text  (${progress.first}/${progress.second})"
        } else {
            text
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(getString(R.string.sync_notification_title))
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setContentIntent(tapIntent)
            .addAction(R.drawable.ic_sync, getString(R.string.sync_notification_stop), stopPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /** Dismissable outcome notification shown after sync ends (any reason). */
    private fun buildStatusNotification(title: String, message: String): android.app.Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setOngoing(false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

    private fun updateProgressNotification(text: String, progress: Pair<Int, Int>) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildProgressNotification(text, progress))
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    // -------------------------------------------------------------------------
    // Companion
    // -------------------------------------------------------------------------

    companion object {
        const val ACTION_START = "com.arvion.smarthealth.SYNC_START"
        const val ACTION_STOP = "com.arvion.smarthealth.SYNC_STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "sync_channel" // matches NotificationHelper.CHANNEL_ID

        fun startIntent(context: Context): Intent =
            Intent(context, SyncForegroundService::class.java).setAction(ACTION_START)

        fun stopIntent(context: Context): Intent =
            Intent(context, SyncForegroundService::class.java).setAction(ACTION_STOP)
    }
}
