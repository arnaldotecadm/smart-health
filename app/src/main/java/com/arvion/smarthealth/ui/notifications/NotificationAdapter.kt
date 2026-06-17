package com.arvion.smarthealth.ui.notifications

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.arvion.smarthealth.R
import com.arvion.smarthealth.model.NotificationLog
import java.time.format.DateTimeFormatter

class NotificationAdapter : ListAdapter<NotificationLog, NotificationAdapter.ViewHolder>(DiffCallback) {

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.notificationTitle)
        val message: TextView = view.findViewById(R.id.notificationMessage)
        val time: TextView = view.findViewById(R.id.notificationTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val notification = getItem(position)
        holder.title.text = notification.title
        holder.message.text = notification.message
        holder.time.text = notification.timestamp.format(formatter)
    }

    companion object DiffCallback : DiffUtil.ItemCallback<NotificationLog>() {
        override fun areItemsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: NotificationLog, newItem: NotificationLog): Boolean {
            return oldItem == newItem
        }
    }
}
