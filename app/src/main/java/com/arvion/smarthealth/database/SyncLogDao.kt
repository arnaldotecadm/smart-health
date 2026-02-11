package com.arvion.smarthealth.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arvion.smarthealth.model.SyncLog
import java.time.LocalDate

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncLog: SyncLog)

    @Query("SELECT * FROM sync_log WHERE date = :date and syncType = :syncType")
    suspend fun getSyncLog(date: LocalDate, syncType: String): SyncLog?

    @Query("DELETE FROM sync_log WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)
}
