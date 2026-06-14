package com.arvion.smarthealth.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.arvion.smarthealth.R
import com.arvion.smarthealth.data.AuthState
import com.arvion.smarthealth.data.AuthViewModel
import com.arvion.smarthealth.data.UserRepository
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.database.SyncLogDao
import com.arvion.smarthealth.model.SyncType
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
import com.auth0.android.jwt.JWT
import com.google.android.gms.auth.api.identity.BeginSignInRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.SignInClient
import com.samsung.android.sdk.health.data.HealthDataService
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

class HomeFragment : Fragment() {

    private lateinit var exerciseService: ExerciseService
    private lateinit var sleepService: SleepService
    private lateinit var dailySummaryService: DailySummaryService
    private lateinit var heartRateService: HeartRateService
    private lateinit var signInClient: SignInClient

    private lateinit var userRepository: UserRepository
    private lateinit var authViewModel: AuthViewModel

    private lateinit var syncLogDao: SyncLogDao

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        signInClient = Identity.getSignInClient(this.requireActivity())
        userRepository = UserRepository(this.requireContext())
        authViewModel = AuthViewModel(userRepository)
        this.syncLogDao = AppDatabase.getDatabase(this.requireContext()).syncLogDao()
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        trySilentSignIn()
        observeUserSession()

        val dataStore = HealthDataService.getStore(requireActivity().applicationContext)
        //val dateTimeToRetrieve = LocalDateTime.of(2026, 2, 8, 0, 0)
        val dateTimeToRetrieve = LocalDateTime.now()
        AppDatabase.getDatabase(requireContext()).openHelper.writableDatabase

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

        view.findViewById<Button>(R.id.loginGoogle).setOnClickListener {
            lifecycleScope.launch {
                startGoogleLogin()
            }
        }

        view.findViewById<Button>(R.id.logout).setOnClickListener {
            lifecycleScope.launch {
                logout()
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
                updateAll()
            }
        }
    }

    private fun startGoogleLogin() {
        val request = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(false)
                    .build()
            )
            .build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener { result ->
                startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    1001,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
            .addOnFailureListener {
                // Handle failure (no Google accounts, etc.)
            }
    }

    private fun observeUserSession() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authViewModel.authState.collect { state ->
                    when (state) {
                        is AuthState.Loading -> {
                            Toast.makeText(requireContext(), "Loading...", Toast.LENGTH_SHORT)
                                .show()
                        }

                        is AuthState.LoggedOut -> {
                            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT)
                                .show()
                        }

                        is AuthState.LoggedIn -> {
                            Toast.makeText(
                                requireContext(),
                                "Logged in as ${state.userId}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            }
        }
    }

    fun logout() {
        lifecycleScope.launch {
            userRepository.clearUser()
            signInClient.signOut()
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1001) {
            try {
                val credential = signInClient.getSignInCredentialFromIntent(data)
                val idToken = credential.googleIdToken

                if (idToken != null) {
                    // User successfully signed in
                    val jwt = JWT(idToken)
                    val googleUserId = jwt.getClaim("sub").asString()
                    lifecycleScope.launch {
                        userRepository.saveUserId(googleUserId ?: credential.id)
                        userRepository.saveJwtToken(idToken) // Save the JWT token
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Google login failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun trySilentSignIn() {
        val request = BeginSignInRequest.builder()
            .setGoogleIdTokenRequestOptions(
                BeginSignInRequest.GoogleIdTokenRequestOptions.builder()
                    .setSupported(true)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .setFilterByAuthorizedAccounts(true)
                    .build()
            )
            .setAutoSelectEnabled(true)
            .build()

        signInClient.beginSignIn(request)
            .addOnSuccessListener { result ->
                startIntentSenderForResult(
                    result.pendingIntent.intentSender,
                    1001,
                    null,
                    0,
                    0,
                    0,
                    null
                )
            }
            .addOnFailureListener {
                // Silent sign-in failed — normal on first launch
                // DO NOT show login UI here
            }
    }

    private fun triggerOneTimeWorker() {
        val updateAPIWorkRequest = OneTimeWorkRequestBuilder<UpdateApiWorker>().build()
        WorkManager.getInstance(requireActivity().applicationContext).enqueue(updateAPIWorkRequest)
    }

    suspend fun updateAll() {
        // Prime the auth token once — eliminates runBlocking on every HTTP call
        ApiBackend.authInterceptor.cachedToken = userRepository.getJwtToken()

        val startDate = LocalDate.parse("2024-01-01")
        val endDate = LocalDate.now()

        // Pre-load the entire sync state for the date range in a single DB query,
        // replacing ~2,000 individual per-service getSyncLog() calls.
        val syncedSet: Set<Pair<LocalDate, SyncType>> = syncLogDao
            .getSyncedDatesInRange(startDate, endDate)
            .map { it.date to it.syncType }
            .toHashSet()

        val dates = generateSequence(endDate) { it.minusDays(1) }
            .takeWhile { !it.isBefore(startDate) }
            .toList()

        for (date in dates) {
            val dateTime = date.atStartOfDay()

            Toast.makeText(
                requireContext(),
                "Syncing $date",
                Toast.LENGTH_SHORT
            ).show()

            // Run Exercise, Sleep, and HeartRate concurrently for this date.
            // DailySummary follows after Exercise finishes so it can reuse the
            // already-fetched exercise data (eliminates a duplicate SDK read).
            coroutineScope {
                val exerciseDeferred = async {
                    if (date to SyncType.EXERCISE !in syncedSet)
                        exerciseService.processExercises(dateTime, skipDbCheck = true)
                    else null
                }
                val sleepDeferred = async {
                    if (date to SyncType.SLEEP !in syncedSet)
                        sleepService.processSleepSession(dateTime, skipDbCheck = true)
                }
                val hrDeferred = async {
                    if (date to SyncType.HEART_RATE !in syncedSet)
                        heartRateService.processHeartRates(dateTime, skipDbCheck = true)
                }

                val exerciseData = exerciseDeferred.await()
                sleepDeferred.await()
                hrDeferred.await()

                if (date to SyncType.DAILY_SUMMARY !in syncedSet) {
                    dailySummaryService.processDailySummary(
                        dateTime,
                        prefetchedExercise = exerciseData,
                        skipDbCheck = true
                    )
                }
            }
        }

        Toast.makeText(
            requireContext(),
            "FINISHED",
            Toast.LENGTH_LONG
        ).show()
    }
}
