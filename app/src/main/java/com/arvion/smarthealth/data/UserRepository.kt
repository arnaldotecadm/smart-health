package com.arvion.smarthealth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.arvion.smarthealth.ui.home.HomeFragment
import kotlinx.coroutines.flow.map

class UserRepository(private val context: Context) {

    val userIdFlow = context.userDataStore.data.map { prefs ->
        prefs[HomeFragment.UserKeys.USER_ID]
    }

    suspend fun saveUserId(userId: String) {
        context.userDataStore.edit { prefs ->
            prefs[HomeFragment.UserKeys.USER_ID] = userId
        }
    }

    suspend fun clearUser() {
        context.userDataStore.edit { it.clear() }
    }
}

