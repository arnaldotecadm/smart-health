package com.arvion.smarthealth.ui.sync

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arvion.smarthealth.R
import com.arvion.smarthealth.data.SyncDirection
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SyncFragment : Fragment() {

    private val viewModel: SyncViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_sync, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.syncLogRecyclerView)
        val statusCard = view.findViewById<MaterialCardView>(R.id.statusCard)
        val currentDateText = view.findViewById<TextView>(R.id.currentDateText)
        val syncProgressText = view.findViewById<TextView>(R.id.syncProgressText)
        val startSyncButton = view.findViewById<MaterialButton>(R.id.startSyncButton)
        val stopSyncButton = view.findViewById<MaterialButton>(R.id.stopSyncButton)
        val syncButtonProgress = view.findViewById<View>(R.id.syncButtonProgress)
        val emptyLogText = view.findViewById<TextView>(R.id.emptyLogText)
        val configSummaryText = view.findViewById<TextView>(R.id.configSummaryText)
        val cursorSummaryText = view.findViewById<TextView>(R.id.cursorSummaryText)
        val settingsButton = view.findViewById<MaterialButton>(R.id.settingsButton)

        val adapter = SyncLogAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        startSyncButton.setOnClickListener { viewModel.startSync() }
        stopSyncButton.setOnClickListener { viewModel.stopSync() }
        settingsButton.setOnClickListener {
            findNavController().navigate(R.id.navigation_settings)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.isSyncing.collect { syncing ->
                        statusCard.visibility = if (syncing) View.VISIBLE else View.GONE
                        startSyncButton.isEnabled = !syncing
                        startSyncButton.text = if (syncing) "" else getString(R.string.start_full_sync)
                        syncButtonProgress.visibility = if (syncing) View.VISIBLE else View.GONE
                        stopSyncButton.visibility = if (syncing) View.VISIBLE else View.GONE
                        settingsButton.isEnabled = !syncing
                    }
                }

                launch {
                    viewModel.syncProgress.collect { (processed, total) ->
                        syncProgressText.text = if (total > 0) "$processed / $total dates" else ""
                        syncProgressText.visibility = if (total > 0) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.currentSyncDate.collect { date ->
                        currentDateText.text = if (date != null) {
                            getString(R.string.syncing_date, date.toString())
                        } else {
                            getString(R.string.sync_idle)
                        }
                    }
                }

                launch {
                    viewModel.syncLogs.collect { logs ->
                        adapter.submitList(logs)
                        emptyLogText.visibility = if (logs.isEmpty()) View.VISIBLE else View.GONE
                    }
                }

                launch {
                    viewModel.syncConfig.collect { config ->
                        val dirLabel = when (config.direction) {
                            SyncDirection.BACKWARD -> "← Backward"
                            SyncDirection.FORWARD -> "→ Forward"
                        }
                        configSummaryText.text = getString(
                            R.string.sync_config_summary,
                            config.oldestDate.toString(),
                            dirLabel
                        )
                        if (config.lastCursor != null) {
                            cursorSummaryText.visibility = View.VISIBLE
                            cursorSummaryText.text = getString(
                                R.string.sync_resume_from,
                                config.lastCursor.toString()
                            )
                        } else {
                            cursorSummaryText.visibility = View.GONE
                        }
                    }
                }

                launch {
                    viewModel.statusMessage.collectLatest { message ->
                        if (message.isNotEmpty()) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }
}

