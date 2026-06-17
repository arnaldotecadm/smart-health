package com.arvion.smarthealth.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.arvion.smarthealth.utils.Constants
import com.auth0.android.jwt.JWT
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class UserRepository(private val context: Context) {

    companion object {
        val USER_ID = stringPreferencesKey("user_id")
        val JWT_TOKEN = stringPreferencesKey("jwt_token")
        /** Refresh proactively when the token expires within this many seconds. */
        private const val EXPIRY_LEEWAY_SECONDS = 300L // 5 minutes
    }

    val userIdFlow: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[USER_ID]
    }

    val jwtTokenFlow: Flow<String?> = context.userDataStore.data.map { prefs ->
        prefs[JWT_TOKEN]
    }

    suspend fun getJwtToken(): String? = jwtTokenFlow.firstOrNull()

    /**
     * Returns a valid JWT token, silently refreshing it via Google if it is expired or
     * close to expiry. Returns null only when the user is not signed in at all.
     */
    suspend fun getValidToken(): String? {
        val current = getJwtToken()
        if (current != null && !isExpiringSoon(current)) {
            return current
        }
        Log.i(Constants.TAG, "Token expired or missing — attempting silent refresh")
        return silentlyRefreshToken()
    }

    private fun isExpiringSoon(token: String): Boolean = try {
        JWT(token).isExpired(EXPIRY_LEEWAY_SECONDS)
    } catch (e: Exception) {
        true
    }

    /**
     * Fetches a fresh Google ID token in the background without any UI interaction.
     * Requires the user to have previously signed in so Google Play Services can satisfy
     * the request silently.
     */
    private suspend fun silentlyRefreshToken(): String? = withContext(Dispatchers.IO) {
        try {
            val account = GoogleSignIn.getLastSignedInAccount(context) ?: run {
                Log.w(Constants.TAG, "Silent refresh: no signed-in Google account found")
                return@withContext null
            }
            val googleAccount = account.account ?: run {
                Log.w(Constants.TAG, "Silent refresh: account has no Android Account object")
                return@withContext null
            }
            val serverClientId = context.getString(com.arvion.smarthealth.R.string.default_web_client_id)
            val freshToken = GoogleAuthUtil.getToken(
                context,
                googleAccount,
                "audience:server:client_id:$serverClientId"
            )
            Log.i(Constants.TAG, "Silent token refresh succeeded")
            saveJwtToken(freshToken)
            freshToken
        } catch (e: UserRecoverableAuthException) {
            // Google requires the user to tap a consent screen — can't refresh silently.
            Log.w(Constants.TAG, "Silent refresh requires user interaction: ${e.message}")
            null
        } catch (e: Exception) {
            Log.e(Constants.TAG, "Silent token refresh failed", e)
            null
        }
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
