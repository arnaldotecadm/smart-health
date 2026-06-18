package com.arvion.smarthealth.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.arvion.smarthealth.model.SyncLog
import com.arvion.smarthealth.model.SyncType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class SyncLogKey(val date: LocalDate, val syncType: SyncType)

@Dao
interface SyncLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(syncLog: SyncLog)

    @Query("SELECT * FROM sync_log WHERE date = :date and syncType = :syncType")
    suspend fun getSyncLog(date: LocalDate, syncType: SyncType): SyncLog?

    @Query(value = "Select min (date) from sync_log")
    suspend fun getMinDate(): LocalDate?

    @Query("DELETE FROM sync_log WHERE date = :date")
    suspend fun deleteByDate(date: LocalDate)

    @Query("DELETE FROM sync_log")
    suspend fun deleteAll()

    @Query("SELECT date, syncType FROM sync_log WHERE date BETWEEN :start AND :end")
    suspend fun getSyncedDatesInRange(start: LocalDate, end: LocalDate): List<SyncLogKey>

    @Query("SELECT MAX(date) FROM sync_log WHERE syncType = :type")
    fun getLatestSyncDateFlow(type: SyncType): Flow<LocalDate?>

    @Query("SELECT * FROM sync_log ORDER BY date DESC, dateTime DESC")
    fun getAllSyncLogs(): Flow<List<SyncLog>>
}
