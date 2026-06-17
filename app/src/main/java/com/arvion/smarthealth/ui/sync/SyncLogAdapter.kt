package com.arvion.smarthealth.ui.sync

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arvion.smarthealth.R
import com.arvion.smarthealth.model.SyncType
import java.time.LocalDate

data class DateSyncStatus(
    val date: LocalDate,
    val syncedTypes: Set<SyncType>
)

class SyncLogAdapter : ListAdapter<DateSyncStatus, SyncLogAdapter.ViewHolder>(DIFF_CALLBACK) {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dateText: TextView = view.findViewById(R.id.syncDate)
        val typesText: TextView = view.findViewById(R.id.syncTypes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sync_log_entry, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.dateText.text = item.date.toString()
        holder.typesText.text = listOf(
            SyncType.EXERCISE to "E",
            SyncType.SLEEP to "S",
            SyncType.HEART_RATE to "H",
            SyncType.DAILY_SUMMARY to "D"
        ).joinToString("  ") { (type, label) ->
            if (type in item.syncedTypes) "✓$label" else "○$label"
        }
    }

    companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<DateSyncStatus>() {
            override fun areItemsTheSame(old: DateSyncStatus, new: DateSyncStatus) =
                old.date == new.date
            override fun areContentsTheSame(old: DateSyncStatus, new: DateSyncStatus) =
                old == new
        }
    }
}
