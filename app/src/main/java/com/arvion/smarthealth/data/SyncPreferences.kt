package com.arvion.smarthealth.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.arvion.smarthealth.model.SyncType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

enum class SyncDirection { BACKWARD, FORWARD }

data class SyncConfig(
    val oldestDate: LocalDate,
    val direction: SyncDirection,
    /** The last date successfully processed; null if no sync has run yet or was reset. */
    val lastCursor: LocalDate?
)

class SyncPreferences(context: Context) {

    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    companion object {
        const val KEY_OLDEST_SYNC_DATE = "pref_oldest_sync_date"
        const val KEY_SYNC_DIRECTION = "pref_sync_direction"
        const val KEY_LAST_SYNC_CURSOR = "pref_last_sync_cursor"
        val DEFAULT_OLDEST_DATE: LocalDate = LocalDate.parse("2024-01-01")

        private fun cursorKey(type: SyncType) = "pref_cursor_${type.name.lowercase()}"
    }

    val oldestSyncDate: LocalDate
        get() = prefs.getString(KEY_OLDEST_SYNC_DATE, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: DEFAULT_OLDEST_DATE

    val syncDirection: SyncDirection
        get() = prefs.getString(KEY_SYNC_DIRECTION, null)
            ?.let { runCatching { SyncDirection.valueOf(it) }.getOrNull() }
            ?: SyncDirection.BACKWARD

    var lastSyncCursor: LocalDate?
        get() = prefs.getString(KEY_LAST_SYNC_CURSOR, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value != null) putString(KEY_LAST_SYNC_CURSOR, value.toString())
            else remove(KEY_LAST_SYNC_CURSOR)
        }.apply()

    val currentConfig: SyncConfig
        get() = SyncConfig(oldestSyncDate, syncDirection, lastSyncCursor)

    fun clearCursor() {
        prefs.edit().remove(KEY_LAST_SYNC_CURSOR).apply()
    }

    fun getCursor(type: SyncType): LocalDate? =
        prefs.getString(cursorKey(type), null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    fun setCursor(type: SyncType, date: LocalDate?) =
        prefs.edit().apply {
            if (date != null) putString(cursorKey(type), date.toString())
            else remove(cursorKey(type))
        }.apply()

    fun clearCursor(type: SyncType) = setCursor(type, null)

    fun getConfigForType(type: SyncType): SyncConfig =
        SyncConfig(oldestSyncDate, syncDirection, getCursor(type))

    /** Emits Unit whenever any sync preference changes. */
    fun observeChanges(): Flow<Unit> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
