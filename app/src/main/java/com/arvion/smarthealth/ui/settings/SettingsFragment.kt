package com.arvion.smarthealth.ui.settings

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.arvion.smarthealth.R
import com.arvion.smarthealth.data.SyncPreferences
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SettingsFragment : PreferenceFragmentCompat() {

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        setupDatePreference()
        setupCursorSummary()
        setupClearLogPreference()
    }

    override fun onResume() {
        super.onResume()
        // Refresh cursor summary every time screen becomes visible
        setupCursorSummary()
    }

    private fun setupDatePreference() {
        findPreference<Preference>(SyncPreferences.KEY_OLDEST_SYNC_DATE)?.apply {
            val prefs = SyncPreferences(requireContext())
            summary = prefs.oldestSyncDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            setOnPreferenceClickListener {
                showDatePicker(prefs)
                true
            }
        }

        // Refresh summary when direction changes so the date pref always shows current value
        findPreference<ListPreference>(SyncPreferences.KEY_SYNC_DIRECTION)
            ?.setOnPreferenceChangeListener { _, _ ->
                // ListPreference handles its own summary via useSimpleSummaryProvider
                true
            }
    }

    private fun setupCursorSummary() {
        val prefs = SyncPreferences(requireContext())
        findPreference<Preference>(SyncPreferences.KEY_LAST_SYNC_CURSOR)?.summary =
            prefs.lastSyncCursor
                ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
                ?.let { getString(R.string.sync_resume_from, it) }
                ?: getString(R.string.pref_cursor_not_set)
    }

    private fun setupClearLogPreference() {
        findPreference<Preference>("pref_clear_sync_log")?.setOnPreferenceClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.pref_clear_log_confirm_title))
                .setMessage(getString(R.string.pref_clear_log_confirm_message))
                .setPositiveButton(getString(R.string.confirm)) { _, _ ->
                    viewModel.clearSyncLog()
                    setupCursorSummary()
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.pref_clear_log_title) + " — done",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
            true
        }
    }

    private fun showDatePicker(prefs: SyncPreferences) {
        val currentMillis = prefs.oldestSyncDate
            .atStartOfDay(ZoneId.of("UTC"))
            .toInstant()
            .toEpochMilli()

        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.pref_oldest_date_title))
            .setSelection(currentMillis)
            .setCalendarConstraints(constraints)
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val selected = Instant.ofEpochMilli(selection)
                .atZone(ZoneId.of("UTC"))
                .toLocalDate()
            prefs.lastSyncCursor = null  // reset cursor when oldest date changes
            preferenceManager.sharedPreferences
                ?.edit()
                ?.putString(SyncPreferences.KEY_OLDEST_SYNC_DATE, selected.toString())
                ?.apply()
            findPreference<Preference>(SyncPreferences.KEY_OLDEST_SYNC_DATE)?.summary =
                selected.format(DateTimeFormatter.ISO_LOCAL_DATE)
            setupCursorSummary()
        }

        picker.show(parentFragmentManager, "settings_date_picker")
    }
}
