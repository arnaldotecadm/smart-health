package com.arvion.smarthealth.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.arvion.smarthealth.R
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.service.DailySummaryService
import com.arvion.smarthealth.service.ExerciseService
import com.arvion.smarthealth.service.HeartRateService
import com.arvion.smarthealth.service.SleepService
import com.arvion.smarthealth.service.UpdateApiWorker
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.samsung.android.sdk.health.data.HealthDataService
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
        val dateTimeToRetrieve = LocalDateTime.of(2026, 2, 8, 0, 0)
        AppDatabase.getDatabase(requireContext()).openHelper.writableDatabase

        exerciseService = ExerciseService(
            context = requireContext(),
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
                val metricContent = view.findViewById<LinearLayout>(R.id.metricContent)
                val metricProgress = view.findViewById<ProgressBar>(R.id.metricProgress)
                metricContent.visibility = View.GONE
                metricProgress.visibility = View.VISIBLE
                exerciseService.processExercises(dateTimeToRetrieve)
                metricContent.visibility = View.VISIBLE
                metricProgress.visibility = View.GONE
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
            Toast.makeText(requireContext(), "Reading heart rate...", Toast.LENGTH_SHORT).show()
            lifecycleScope.launch {
                heartRateService.processHeartRates(dateTimeToRetrieve)
            }
        }

        /*
        view.findViewById<Button>(R.id.triggerWorkerButton).setOnClickListener {
            triggerOneTimeWorker()
        }
        */
        view.findViewById<Button>(R.id.updateAll).setOnClickListener {
            lifecycleScope.launch {
                //updateAll()
            }
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
}