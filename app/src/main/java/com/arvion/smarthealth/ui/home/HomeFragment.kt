package com.arvion.smarthealth.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.arvion.smarthealth.R
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.time.LocalDate

class HomeFragment : Fragment() {

    private val viewModel: HomeViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* permission result handled silently */ }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkNotificationPermission()
        bindCard(
            startButton = view.findViewById(R.id.readExerciseButton),
            stopButton = view.findViewById(R.id.stopExerciseButton),
            statusLabel = view.findViewById(R.id.exerciseStatusLabel),
            lastSyncedText = view.findViewById(R.id.exerciseLastSyncedText),
            contentView = view.findViewById(R.id.exerciseContent),
            progressView = view.findViewById(R.id.exerciseProgress),
            isSyncing = viewModel.exerciseSyncing,
            lastSync = viewModel.exerciseLastSync,
            currentDate = viewModel.exerciseCurrentDate,
            onStart = { viewModel.startExercise() },
            onStop = { viewModel.stopExercise() }
        )
        bindCard(
            startButton = view.findViewById(R.id.readSleepButton),
            stopButton = view.findViewById(R.id.stopSleepButton),
            statusLabel = view.findViewById(R.id.sleepStatusLabel),
            lastSyncedText = view.findViewById(R.id.sleepLastSyncedText),
            contentView = view.findViewById(R.id.sleepContent),
            progressView = view.findViewById(R.id.sleepProgress),
            isSyncing = viewModel.sleepSyncing,
            lastSync = viewModel.sleepLastSync,
            currentDate = viewModel.sleepCurrentDate,
            onStart = { viewModel.startSleep() },
            onStop = { viewModel.stopSleep() }
        )
        bindCard(
            startButton = view.findViewById(R.id.readDailySummary),
            stopButton = view.findViewById(R.id.stopDailySummaryButton),
            statusLabel = view.findViewById(R.id.dailySummaryStatusLabel),
            lastSyncedText = view.findViewById(R.id.dailySummaryLastSyncedText),
            contentView = view.findViewById(R.id.dailySummaryContent),
            progressView = view.findViewById(R.id.dailySummaryProgress),
            isSyncing = viewModel.dailySummarySyncing,
            lastSync = viewModel.dailySummaryLastSync,
            currentDate = viewModel.dailySummaryCurrentDate,
            onStart = { viewModel.startDailySummary() },
            onStop = { viewModel.stopDailySummary() }
        )
        bindCard(
            startButton = view.findViewById(R.id.readHeartRate),
            stopButton = view.findViewById(R.id.stopHeartRateButton),
            statusLabel = view.findViewById(R.id.heartRateStatusLabel),
            lastSyncedText = view.findViewById(R.id.heartRateLastSyncedText),
            contentView = view.findViewById(R.id.heartRateContent),
            progressView = view.findViewById(R.id.heartRateProgress),
            isSyncing = viewModel.heartRateSyncing,
            lastSync = viewModel.heartRateLastSync,
            currentDate = viewModel.heartRateCurrentDate,
            onStart = { viewModel.startHeartRate() },
            onStop = { viewModel.stopHeartRate() }
        )
    }

    private fun bindCard(
        startButton: MaterialButton,
        stopButton: MaterialButton,
        statusLabel: TextView,
        lastSyncedText: TextView,
        contentView: View,
        progressView: View,
        isSyncing: Flow<Boolean>,
        lastSync: Flow<LocalDate?>,
        currentDate: Flow<LocalDate?>,
        onStart: () -> Unit,
        onStop: () -> Unit
    ) {
        startButton.setOnClickListener { onStart() }
        stopButton.setOnClickListener { onStop() }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    isSyncing.collect { syncing ->
                        startButton.visibility = if (syncing) View.GONE else View.VISIBLE
                        stopButton.visibility = if (syncing) View.VISIBLE else View.GONE
                        contentView.visibility = if (syncing) View.GONE else View.VISIBLE
                        progressView.visibility = if (syncing) View.VISIBLE else View.GONE
                        if (syncing) {
                            statusLabel.text = getString(R.string.syncing)
                            statusLabel.setTextColor(
                                ContextCompat.getColor(requireContext(), R.color.primary)
                            )
                        }
                    }
                }
                launch {
                    lastSync.collect { date ->
                        lastSyncedText.text = if (date != null) {
                            getString(R.string.last_synced_date, date.toString())
                        } else {
                            getString(R.string.never_synced)
                        }
                    }
                }
                launch {
                    currentDate.collect { date ->
                        if (date == null) {
                            // Will be set by isSyncing = false path
                        }
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                kotlinx.coroutines.flow.combine(isSyncing, lastSync) { syncing, last ->
                    Pair(syncing, last)
                }.collect { (syncing, last) ->
                    if (!syncing) {
                        statusLabel.text = if (last != null) {
                            getString(R.string.up_to_date)
                        } else {
                            getString(R.string.never_synced)
                        }
                        statusLabel.setTextColor(
                            ContextCompat.getColor(
                                requireContext(),
                                if (last != null) R.color.success else android.R.color.darker_gray
                            )
                        )
                    }
                }
            }
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
