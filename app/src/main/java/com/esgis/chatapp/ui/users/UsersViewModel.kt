package com.esgis.chatapp.ui.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esgis.chatapp.data.Profile
import com.esgis.chatapp.data.PresenceManager
import com.esgis.chatapp.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UsersUiState {
    data object Loading : UsersUiState
    data class Data(val users: List<Profile>) : UsersUiState
    data class Error(val message: String) : UsersUiState
}

class UsersViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow<UsersUiState>(UsersUiState.Loading)
    val state = _state.asStateFlow()

    /** Utilisateurs actuellement en ligne (présence Realtime). */
    val onlineUsers = PresenceManager.online

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UsersUiState.Loading
            runCatching { repo.getContacts() }
                .onSuccess { _state.value = UsersUiState.Data(it) }
                .onFailure { _state.value = UsersUiState.Error(it.message ?: "Erreur de chargement.") }
        }
    }

    /** Ouvre (crée) une conversation avec l'utilisateur choisi. */
    fun startChat(userId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.getOrCreateHumanConversation(userId) }
                .onSuccess { onReady(it) }
                .onFailure { _message.value = it.message ?: "Impossible d'ouvrir la conversation." }
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
