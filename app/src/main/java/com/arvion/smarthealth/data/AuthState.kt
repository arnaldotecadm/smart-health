package com.arvion.smarthealth.data

sealed class AuthState {
    object Loading : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val userId: String) : AuthState()
}
