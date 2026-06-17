package com.arvion.smarthealth.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.arvion.smarthealth.R
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.model.NotificationLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

object NotificationHelper {
    private const val CHANNEL_ID = "sync_channel"
    private const val CHANNEL_NAME = "Health Sync Notifications"
    private const val CHANNEL_DESC = "Notifications about health data background synchronization"

    // To ensure unique IDs during a single session if needed, 
    // though using timestamp is often safer across restarts.
    private val notificationIdCounter = AtomicInteger(1000)

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Shows a notification and persists it to the database.
     * Use 0 for id to generate a unique ID based on timestamp.
     */
    fun showNotification(context: Context, id: Int, title: String, message: String) {
        val notificationId = if (id == 0) (System.currentTimeMillis() % Int.MAX_VALUE).toInt() else id
        
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(notificationId, builder.build())
            } catch (e: SecurityException) {
            }
        }

        // Persist to DB asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                db.notificationLogDao().insert(
                    NotificationLog(title = title, message = message)
                )
            } catch (e: Exception) {
                // Log error if needed
            }
        }
    }
}
