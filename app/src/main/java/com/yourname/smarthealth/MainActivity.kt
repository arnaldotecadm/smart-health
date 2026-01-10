package com.yourname.smarthealth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.yourname.smarthealth.service.ExerciseService
import com.yourname.smarthealth.service.SleepService
import com.yourname.smarthealth.service.api.ExerciseApiService
import com.yourname.smarthealth.service.api.SleepApiService
import com.yourname.smarthealth.utils.Constants.TAG
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class MainActivity() : AppCompatActivity() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        exerciseService = ExerciseService(
            healthDataStore = HealthDataService.getStore(applicationContext),
            exerciseApiService = ExerciseApiService()
        )
        sleepService = SleepService(
            healthDataStore = HealthDataService.getStore(applicationContext),
            sleepApiService = SleepApiService()
        )

        val readStepsButton: Button = findViewById(R.id.readStepsButton)
        readStepsButton.setOnClickListener {
            readStepCount()
        }

        val readExerciseButton: Button = findViewById(R.id.readExerciseButton)
        readExerciseButton.setOnClickListener {
            lifecycleScope.launch {
                exerciseService.readExercises(LocalDateTime.now())
            }
        }

        val readSleepButton: Button = findViewById(R.id.readSleepButton)
        readSleepButton.setOnClickListener {
            lifecycleScope.launch {
                sleepService.readSleep(LocalDateTime.now())
            }
        }
        lifecycleScope.launch {
            checkForPermissions(this@MainActivity)
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    private fun readStepCount(
        startTime: LocalDateTime = LocalDateTime.now().minusDays(120),
        endTime: LocalDateTime = LocalDateTime.now()
    ) {
        lifecycleScope.launch {
            try {
                val healthDataStore = HealthDataService.getStore(applicationContext)
                val localtimeFilter = LocalTimeFilter.of(startTime, endTime)

                val readRequest = DataType.StepsType.TOTAL.requestBuilder
                    .setLocalTimeFilterWithGroup(
                        localtimeFilter,
                        LocalTimeGroup.of(LocalTimeGroupUnit.DAILY, 1)
                    )
                    .setOrdering(Ordering.ASC)
                    .build()

                val dataList = healthDataStore.aggregateData(readRequest).dataList
                dataList.forEach {
                    val hourlyStepCount = it.value
                }
                val dailyStepCount = dataList.sumOf { it.value as Long }
                Log.d(TAG, "Daily step count: $dailyStepCount")
            } catch (exception: Exception) {
                Log.e(TAG, "Error reading steps", exception)
            }
        }
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

    suspend fun getTotalStepsPerDay(healthDataStore: HealthDataStore) {
        val todayDateTime = LocalDateTime.now().with(LocalTime.MIDNIGHT)
        val todayLocalTimeFilter = LocalTimeFilter.of(todayDateTime, todayDateTime.plusDays(1))
        val hourlyTimeGroup = LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
        val stepsAggregateRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(todayLocalTimeFilter, hourlyTimeGroup)
            .setOrdering(Ordering.ASC)
            .build()
        val hourlyStepsResponse = healthDataStore.aggregateData(stepsAggregateRequest)
        val dataListSteps = hourlyStepsResponse.dataList
        val sumOf = dataListSteps.sumOf { it.value!! }
        Log.i(TAG, sumOf.toString())
        dataListSteps.forEach { stepData ->
            val hourlySteps: Long = stepData.value!!
            Log.i(TAG, hourlySteps.toString())
        }

    }
}
