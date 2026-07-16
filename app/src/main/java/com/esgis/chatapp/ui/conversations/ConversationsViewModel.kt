package com.esgis.chatapp.ui.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.esgis.chatapp.data.AiPersona
import com.esgis.chatapp.data.ConversationUi
import com.esgis.chatapp.data.MessageNotifier
import com.esgis.chatapp.data.PresenceManager
import com.esgis.chatapp.data.RealtimeMessages
import com.esgis.chatapp.data.Profile
import com.esgis.chatapp.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ConversationsUiState {
    data object Loading : ConversationsUiState
    data class Data(val conversations: List<ConversationUi>) : ConversationsUiState
    data class Error(val message: String) : ConversationsUiState
}

class ConversationsViewModel : ViewModel() {

    private val repo = ServiceLocator.repository

    private val _state = MutableStateFlow<ConversationsUiState>(ConversationsUiState.Loading)
    val state = _state.asStateFlow()

    private val _contacts = MutableStateFlow<List<Profile>>(emptyList())
    val contacts = _contacts.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()

    /** Ensemble des utilisateurs actuellement en ligne (présence Realtime). */
    val onlineUsers = PresenceManager.online

    val myId: String? = repo.currentUserId

    init {
        repo.currentUserId?.let { PresenceManager.start(it) }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ConversationsUiState.Loading
            runCatching { repo.getConversationsUi() }
                .onSuccess { _state.value = ConversationsUiState.Data(it) }
                .onFailure { _state.value = ConversationsUiState.Error(it.message ?: "Erreur de chargement.") }
        }
    }

    fun loadContacts() {
        viewModelScope.launch {
            runCatching { repo.getContacts() }
                .onSuccess { _contacts.value = it }
                .onFailure { _message.value = it.message }
        }
    }

    fun startHumanChat(otherUserId: String, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.getOrCreateHumanConversation(otherUserId) }
                .onSuccess { refresh(); onReady(it) }
                .onFailure { _message.value = it.message ?: "Impossible de créer la conversation." }
        }
    }

    fun startAiChat(persona: AiPersona, onReady: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { repo.createAiConversation(persona) }
                .onSuccess { refresh(); onReady(it) }
                .onFailure { _message.value = it.message ?: "Impossible de créer le chat IA." }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            PresenceManager.stop()
            MessageNotifier.stop()
            RealtimeMessages.stop()
            runCatching { repo.signOut() }
            onDone()
        }
    }

    fun clearMessage() {
        _message.value = null
    }
}
