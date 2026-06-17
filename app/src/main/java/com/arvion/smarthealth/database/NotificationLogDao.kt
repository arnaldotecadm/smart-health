package com.arvion.smarthealth.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.arvion.smarthealth.model.NotificationLog
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationLogDao {
    @Insert
    suspend fun insert(log: NotificationLog)

    @Query("SELECT * FROM notification_log ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationLog>>

    @Query("DELETE FROM notification_log")
    suspend fun deleteAll()
}
