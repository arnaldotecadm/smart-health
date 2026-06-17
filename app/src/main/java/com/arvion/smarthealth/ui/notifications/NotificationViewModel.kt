package com.arvion.smarthealth.ui.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.arvion.smarthealth.database.AppDatabase
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val dao = AppDatabase.getDatabase(application).notificationLogDao()

    val notifications = dao.getAllNotifications().asLiveData()

    fun clearAll() {
        viewModelScope.launch {
            dao.deleteAll()
        }
    }
}
