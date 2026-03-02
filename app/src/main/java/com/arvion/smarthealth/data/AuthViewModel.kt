package com.arvion.smarthealth.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AuthViewModel(
    private val userRepository: UserRepository
) : ViewModel() {

    val authState = userRepository.userIdFlow
        .map { userId ->
            if (userId == null) AuthState.LoggedOut
            else AuthState.LoggedIn(userId)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            AuthState.Loading
        )
}

