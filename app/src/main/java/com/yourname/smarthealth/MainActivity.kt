package com.yourname.smarthealth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.yourname.smarthealth.service.DailySummaryService
import com.yourname.smarthealth.service.ExerciseService
import com.yourname.smarthealth.service.UpdateApiWorker
import com.yourname.smarthealth.service.HeartRateService
import com.yourname.smarthealth.service.SleepService
import com.yourname.smarthealth.service.api.ApiBackend
import com.yourname.smarthealth.service.api.DailySummaryApiService
import com.yourname.smarthealth.service.api.ExerciseApiService
import com.yourname.smarthealth.service.api.HeartRateSeriesApiService
import com.yourname.smarthealth.service.api.SleepApiService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class MainActivity() : AppCompatActivity() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dataStore = HealthDataService.getStore(applicationContext)
        val dateTimeToRetrieve = LocalDateTime.now()

        exerciseService = ExerciseService(
            healthDataStore = dataStore,
            exerciseApiService = ExerciseApiService()
        )
        sleepService = SleepService(
            healthDataStore = dataStore,
            sleepApiService = SleepApiService()
        )
        dailySummaryService = DailySummaryService(
            healthDataStore = dataStore,
            exerciserService = exerciseService,
            dailySummaryApiService = DailySummaryApiService(ApiBackend())
        )
        heartRateService = HeartRateService(
            healthDataStore = dataStore,
            heartRateSeriesApiService = HeartRateSeriesApiService()
        )

        val readExerciseButton: Button = findViewById(R.id.readExerciseButton)
        readExerciseButton.setOnClickListener {
            lifecycleScope.launch {
                exerciseService.processExercises(dateTimeToRetrieve)
            }
        }

        val readSleepButton: Button = findViewById(R.id.readSleepButton)
        readSleepButton.setOnClickListener {
            lifecycleScope.launch {
                sleepService.processSleepSession(dateTimeToRetrieve)
            }
        }

        val readDailySummary: Button = findViewById(R.id.readDailySummary)
        readDailySummary.setOnClickListener {
            lifecycleScope.launch {
                dailySummaryService.processDailySummary(dateTimeToRetrieve)
            }
        }

        val readHeartRateButton: Button = findViewById(R.id.readHeartRate)
        readHeartRateButton.setOnClickListener {
            lifecycleScope.launch {
                heartRateService.processHeartRates(dateTimeToRetrieve)
            }
        }

        val triggerWorkerButton: Button = findViewById(R.id.triggerWorkerButton)
        triggerWorkerButton.setOnClickListener {
            triggerOneTimeWorker()
        }

        val updateAllMetricsButton: Button = findViewById(R.id.updateAll)
        updateAllMetricsButton.setOnClickListener {
            lifecycleScope.launch {
                updateAll()
            }
        }

        lifecycleScope.launch {
            checkForPermissions(this@MainActivity)
        }

        //schedulePeriodicExerciseProcessing()
    }

    private fun triggerOneTimeWorker() {
        val updateAPIWorkRequest = OneTimeWorkRequestBuilder<UpdateApiWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(updateAPIWorkRequest)
    }

    private fun schedulePeriodicExerciseProcessing() {
        val exerciseWorkRequest = PeriodicWorkRequestBuilder<UpdateApiWorker>(15, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(applicationContext).enqueueUniquePeriodicWork(
            "UpdateAPIProcessingWork",
            ExistingPeriodicWorkPolicy.KEEP,
            exerciseWorkRequest
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    suspend fun updateAll() {
        var dateTimeToRetrieve = LocalDateTime.now()
        do {
            Toast.makeText(
                this,
                "Processing data from ${dateTimeToRetrieve.toLocalDate()}",
                Toast.LENGTH_SHORT
            ).show()
            exerciseService.processExercises(dateTimeToRetrieve)
            sleepService.processSleepSession(dateTimeToRetrieve)
            dailySummaryService.processDailySummary(dateTimeToRetrieve)
            heartRateService.processHeartRates(dateTimeToRetrieve)
            dateTimeToRetrieve = dateTimeToRetrieve.minusDays(1)
        } while (dateTimeToRetrieve.isAfter(LocalDate.parse("2024-01-01").atStartOfDay()))

        Toast.makeText(
            this,
            "FINISHED",
            Toast.LENGTH_LONG
        ).show()
    }

    suspend fun checkForPermissions(activity: Activity) {
        val permSet = mutableSetOf(
            Permission.of(DataTypes.HEART_RATE, AccessType.READ),
            Permission.of(DataTypes.STEPS, AccessType.READ),
            Permission.of(DataTypes.SLEEP_GOAL, AccessType.READ),
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
            Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ),
            Permission.of(DataTypes.EXERCISE, AccessType.READ),
            Permission.of(DataTypes.EXERCISE_LOCATION, AccessType.READ),
            Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ),
            Permission.of(DataTypes.NUTRITION, AccessType.READ),
            Permission.of(DataTypes.NUTRITION_GOAL, AccessType.READ),
        )

        try {
            val healthDataStore = HealthDataService.getStore(activity.applicationContext)
            val grantedPermissions = healthDataStore.getGrantedPermissions(permSet)

            if (grantedPermissions.containsAll(permSet)) {
                // All Permissions already granted
            } else {
                // Partial or No permission, we need to request for the permission popup
                healthDataStore.requestPermissions(permSet, activity)
            }
        } catch (error: HealthDataException) {
            if (error is ResolvablePlatformException && error.hasResolution) {
                error.resolve(activity)
            }
            // handle other types of HealthDataException
            error.printStackTrace()
        }
    }
}
