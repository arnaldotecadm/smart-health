package com.yourname.smarthealth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.yourname.smarthealth.service.DailySummaryService
import com.yourname.smarthealth.service.ExerciseService
import com.yourname.smarthealth.service.HeartRateService
import com.yourname.smarthealth.service.SleepService
import com.yourname.smarthealth.service.api.ApiBackend
import com.yourname.smarthealth.service.api.DailySummaryApiService
import com.yourname.smarthealth.service.api.ExerciseApiService
import com.yourname.smarthealth.service.api.HeartRateSeriesApiService
import com.yourname.smarthealth.service.api.SleepApiService
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class MainActivity() : AppCompatActivity() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val dateTimeToRetrieve = LocalDateTime.now().minusDays(0)

        exerciseService = ExerciseService(
            healthDataStore = HealthDataService.getStore(applicationContext),
            exerciseApiService = ExerciseApiService()
        )
        sleepService = SleepService(
            healthDataStore = HealthDataService.getStore(applicationContext),
            sleepApiService = SleepApiService()
        )
        dailySummaryService = DailySummaryService(
            healthDataStore = HealthDataService.getStore(applicationContext),
            exerciserService = exerciseService,
            dailySummaryApiService = DailySummaryApiService(ApiBackend())
        )
        heartRateService = HeartRateService(
            healthDataStore = HealthDataService.getStore(applicationContext),
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
                heartRateService.processExercises(dateTimeToRetrieve)
            }
        }

        lifecycleScope.launch {
            checkForPermissions(this@MainActivity)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
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
