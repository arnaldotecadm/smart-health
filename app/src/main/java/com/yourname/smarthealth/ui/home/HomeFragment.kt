package com.yourname.smarthealth.ui.home

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.error.HealthDataException
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataTypes
import com.yourname.smarthealth.R
import com.yourname.smarthealth.service.DailySummaryService
import com.yourname.smarthealth.service.ExerciseService
import com.yourname.smarthealth.service.HeartRateService
import com.yourname.smarthealth.service.SleepService
import com.yourname.smarthealth.service.UpdateApiWorker
import com.yourname.smarthealth.service.api.ApiBackend
import com.yourname.smarthealth.service.api.DailySummaryApiService
import com.yourname.smarthealth.service.api.ExerciseApiService
import com.yourname.smarthealth.service.api.HeartRateSeriesApiService
import com.yourname.smarthealth.service.api.SleepApiService
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class HomeFragment : Fragment() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val dataStore = HealthDataService.getStore(requireActivity().applicationContext)
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

        view.findViewById<Button>(R.id.readExerciseButton).setOnClickListener {
            lifecycleScope.launch {
                exerciseService.processExercises(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readSleepButton).setOnClickListener {
            lifecycleScope.launch {
                sleepService.processSleepSession(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readDailySummary).setOnClickListener {
            lifecycleScope.launch {
                dailySummaryService.processDailySummary(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readHeartRate).setOnClickListener {
            lifecycleScope.launch {
                heartRateService.processHeartRates(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.triggerWorkerButton).setOnClickListener {
            triggerOneTimeWorker()
        }

        view.findViewById<Button>(R.id.updateAll).setOnClickListener {
            lifecycleScope.launch {
                updateAll()
            }
        }

        lifecycleScope.launch {
            checkForPermissions(requireActivity())
        }
    }

    private fun triggerOneTimeWorker() {
        val updateAPIWorkRequest = OneTimeWorkRequestBuilder<UpdateApiWorker>().build()
        WorkManager.getInstance(requireActivity().applicationContext).enqueue(updateAPIWorkRequest)
    }

    suspend fun updateAll() {
        var dateTimeToRetrieve = LocalDateTime.now()
        do {
            Toast.makeText(
                requireContext(),
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
            requireContext(),
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