package com.esgis.chatapp.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esgis.chatapp.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AuthUiState {
    data object Idle : AuthUiState
    data object Loading : AuthUiState
    data object Success : AuthUiState
    data class Error(val message: String) : AuthUiState
}

class AuthViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val state = _state.asStateFlow()

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = AuthUiState.Error("Email et mot de passe requis.")
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            runCatching { repo.signIn(email.trim(), password) }
                .onSuccess { _state.value = AuthUiState.Success }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Échec de la connexion.") }
        }
    }

    fun signUp(email: String, password: String, username: String) {
        if (email.isBlank() || password.isBlank() || username.isBlank()) {
            _state.value = AuthUiState.Error("Nom, email et mot de passe requis.")
            return
        }
        viewModelScope.launch {
            _state.value = AuthUiState.Loading
            runCatching { repo.signUp(email.trim(), password, username.trim()) }
                .onSuccess { _state.value = AuthUiState.Success }
                .onFailure { _state.value = AuthUiState.Error(it.message ?: "Échec de l'inscription.") }
        }
    }

    fun resetError() {
        if (_state.value is AuthUiState.Error) _state.value = AuthUiState.Idle
    }
}
