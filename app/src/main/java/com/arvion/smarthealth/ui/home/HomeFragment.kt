package com.arvion.smarthealth.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.arvion.smarthealth.R
import com.arvion.smarthealth.data.AuthViewModel
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.service.DailySummaryService
import com.arvion.smarthealth.service.ExerciseService
import com.arvion.smarthealth.service.HeartRateService
import com.arvion.smarthealth.service.SleepService
import com.arvion.smarthealth.service.SyncDailySummaryWorker
import com.arvion.smarthealth.service.api.ApiBackend
import com.arvion.smarthealth.service.api.DailySummaryApiService
import com.arvion.smarthealth.service.api.ExerciseApiService
import com.arvion.smarthealth.service.api.HeartRateSeriesApiService
import com.arvion.smarthealth.service.api.SleepApiService
import com.auth0.android.jwt.JWT
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class HomeFragment : Fragment() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService

    private lateinit var userRepository: UserRepository
    private lateinit var authViewModel: AuthViewModel

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(requireContext(), "Notifications enabled", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        userRepository = UserRepository(this.requireContext())
        authViewModel = AuthViewModel(userRepository)
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        checkNotificationPermission()

        val dataStore = HealthDataService.getStore(requireActivity().applicationContext)
        val dateTimeToRetrieve = LocalDateTime.now()

        exerciseService = ExerciseService(
            context = requireContext(),
            healthDataStore = dataStore,
            exerciseApiService = ExerciseApiService(requireContext())
        )
        sleepService = SleepService(
            context = requireContext(),
            healthDataStore = dataStore,
            sleepApiService = SleepApiService(requireContext())
        )
        dailySummaryService = DailySummaryService(
            context = requireContext(),
            healthDataStore = dataStore,
            dailySummaryApiService = DailySummaryApiService(ApiBackend(requireContext()))
        )
        heartRateService = HeartRateService(
            context = requireContext(),
            healthDataStore = dataStore,
            heartRateSeriesApiService = HeartRateSeriesApiService(requireContext())
        )

        view.findViewById<Button>(R.id.readExerciseButton).setOnClickListener {
            performSyncWithLoading(
                view.findViewById(R.id.exerciseContent),
                view.findViewById(R.id.exerciseProgress)
            ) {
                exerciseService.processExercises(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readSleepButton).setOnClickListener {
            performSyncWithLoading(
                view.findViewById(R.id.sleepContent),
                view.findViewById(R.id.sleepProgress)
            ) {
                sleepService.processSleepSession(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readDailySummary).setOnClickListener {
            performSyncWithLoading(
                view.findViewById(R.id.dailySummaryContent),
                view.findViewById(R.id.dailySummaryProgress)
            ) {
                dailySummaryService.processDailySummary(dateTimeToRetrieve)
            }
        }

        view.findViewById<Button>(R.id.readHeartRate).setOnClickListener {
            performSyncWithLoading(
                view.findViewById(R.id.heartRateContent),
                view.findViewById(R.id.heartRateProgress)
            ) {
                heartRateService.processHeartRates(dateTimeToRetrieve)
            }
        }
    }

    private fun performSyncWithLoading(
        contentView: View,
        progressView: View,
        syncAction: suspend () -> Unit
    ) {
        lifecycleScope.launch {
            if (!ensureValidToken()) return@launch

            contentView.visibility = View.GONE
            progressView.visibility = View.VISIBLE
            try {
                syncAction()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Sync failed: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            } finally {
                contentView.visibility = View.VISIBLE
                progressView.visibility = View.GONE
            }
        }
    }

    private suspend fun ensureValidToken(): Boolean {
        val token = userRepository.getJwtToken()
        if (token == null || JWT(token).isExpired(5)) {
            Toast.makeText(
                requireContext(),
                "Session expired. Please log in from the side menu.",
                Toast.LENGTH_SHORT
            ).show()
            return false
        }
        ApiBackend.authInterceptor.cachedToken = token
        return true
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

