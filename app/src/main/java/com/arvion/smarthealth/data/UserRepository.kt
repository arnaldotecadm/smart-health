package com.arvion.smarthealth.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

class UserRepository(private val context: Context) {

    companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
    }

    val userIdFlow: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[USER_ID]
    }

    val jwtTokenFlow: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[JWT_TOKEN]
    }

    suspend fun getJwtToken(): String? {
        return jwtTokenFlow.firstOrNull()
    }

    suspend fun saveUserId(userId: String) {
        context.userDataStore.edit { prefs ->
            prefs[USER_ID] = userId
        }
    }

    suspend fun saveJwtToken(token: String) {
        context.userDataStore.edit { prefs ->
            prefs[JWT_TOKEN] = token
        }
    }

    suspend fun clearUser() {
        context.userDataStore.edit { it.clear() }
    }
}
