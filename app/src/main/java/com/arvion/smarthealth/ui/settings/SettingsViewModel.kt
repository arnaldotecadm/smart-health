package com.arvion.smarthealth.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.arvion.smarthealth.database.AppDatabase
import com.arvion.smarthealth.data.SyncPreferences
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val syncLogDao = AppDatabase.getDatabase(application).syncLogDao()
    private val syncPreferences = SyncPreferences(application)

    fun clearSyncLog() {
        viewModelScope.launch {
            syncLogDao.deleteAll()
            syncPreferences.clearCursor()
        }
    }
}
