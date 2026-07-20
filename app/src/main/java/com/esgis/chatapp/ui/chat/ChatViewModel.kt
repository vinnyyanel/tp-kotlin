package com.esgis.chatapp.ui.chat

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.esgis.chatapp.data.AiPersona
import com.esgis.chatapp.data.AudioPlayer
import com.esgis.chatapp.data.AudioRecorder
import com.esgis.chatapp.data.ChatRepository
import com.esgis.chatapp.data.GeminiService
import com.esgis.chatapp.data.Message
import com.esgis.chatapp.data.MessageNotifier
import com.esgis.chatapp.data.PresenceManager
import com.esgis.chatapp.data.RealtimeMessages
import com.esgis.chatapp.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val isAiChat: Boolean = false,
    val persona: AiPersona = AiPersona.GENERAL,
    val aiTyping: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
    val otherUserId: String? = null,
    val otherName: String? = null,
    val recording: Boolean = false
)

class ChatViewModel(
    private val conversationId: String,
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val repo: ChatRepository = ServiceLocator.repository
) : ViewModel() {

    val myId: String? = repo.currentUserId

    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()

    /** true si l'autre participant est en ligne (la Vue n'accède pas à PresenceManager). */
    val otherOnline: StateFlow<Boolean> =
        combine(PresenceManager.online, _state) { online, st ->
            st.otherUserId != null && st.otherUserId in online
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private companion object {
        const val TEMP_AI_ID = "temp-ai-streaming"
        const val HISTORY_LIMIT = 20
    }

    init {
        MessageNotifier.activeConversationId = conversationId
        bootstrap()
    }

    override fun onCleared() {
        if (MessageNotifier.activeConversationId == conversationId) {
            MessageNotifier.activeConversationId = null
        }
        player.stop()
        super.onCleared()
    }

    private fun bootstrap() {
        viewModelScope.launch {
            val conv = runCatching { repo.getConversation(conversationId) }.getOrNull()
            val msgs = runCatching { repo.getMessages(conversationId) }.getOrElse { emptyList() }
            val other = if (conv?.isAiChat == true) null
            else runCatching { repo.getOtherParticipantId(conversationId) }.getOrNull()
            val otherName = other?.let { runCatching { repo.getUsername(it) }.getOrNull() }
            _state.update {
                it.copy(
                    messages = msgs,
                    isAiChat = conv?.isAiChat == true,
                    persona = AiPersona.fromId(conv?.aiPersona),
                    loading = false,
                    otherUserId = other,
                    otherName = otherName
                )
            }
            runCatching { repo.markConversationRead(conversationId) }
            observeRealtime()
        }
    }

    private fun observeRealtime() {
        RealtimeMessages.start()
        viewModelScope.launch {
            merge(RealtimeMessages.inserts, RealtimeMessages.updates)
                .filter { it.conversationId == conversationId }
                .collect { msg ->
                    addOrUpdate(msg)
                    if (msg.senderId != myId) {
                        runCatching { repo.markConversationRead(conversationId) }
                    }
                }
        }
    }

    private fun addOrUpdate(msg: Message) {
        _state.update { st ->
            val exists = msg.id != null && st.messages.any { it.id == msg.id }
            if (exists) {
                st.copy(messages = st.messages.map { if (it.id == msg.id) msg else it })
            } else {
                st.copy(messages = st.messages + msg)
            }
        }
    }

    fun send(text: String) {
        val content = text.trim()
        if (content.isEmpty()) return
        viewModelScope.launch {
            runCatching { repo.sendMessage(conversationId, content, isFromAi = false) }
                .onSuccess { addOrUpdate(it) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Envoi impossible.") } }

            if (_state.value.isAiChat) streamAiReply()
        }
    }

    /** Envoie une photo (bytes déjà lus depuis l'URI). */
    fun sendImage(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            runCatching { repo.uploadMedia(conversationId, bytes, fileName, "image") }
                .onSuccess { addOrUpdate(it) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Envoi de l'image impossible.") } }
        }
    }

    /** Envoie un message audio (bytes de l'enregistrement). */
    fun sendAudio(bytes: ByteArray, fileName: String) {
        viewModelScope.launch {
            runCatching { repo.uploadMedia(conversationId, bytes, fileName, "audio") }
                .onSuccess { addOrUpdate(it) }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "Envoi de l'audio impossible.") } }
        }
    }

    // ---------- AUDIO (piloté par le ViewModel, pas par la Vue) ----------
    fun startRecording() {
        if (recorder.start()) _state.update { it.copy(recording = true) }
    }

    fun stopRecordingAndSend() {
        val bytes = recorder.stop()
        _state.update { it.copy(recording = false) }
        if (bytes != null) sendAudio(bytes, "audio.m4a")
    }

    fun playAudio(url: String) = player.play(url)

    private suspend fun streamAiReply() {
        _state.update { it.copy(aiTyping = true) }

        // Historique (N derniers messages) envoyé au modèle — exigence du sujet.
        val history = _state.value.messages
            .takeLast(HISTORY_LIMIT)
            .mapNotNull { m -> m.content?.let { GeminiService.Turn(it, m.isFromAi) } }

        // Bulle temporaire qui se remplit token par token.
        _state.update {
            it.copy(
                messages = it.messages + Message(
                    id = TEMP_AI_ID,
                    conversationId = conversationId,
                    content = "",
                    isFromAi = true
                )
            )
        }

        var accumulated = ""
        runCatching {
            GeminiService.streamReply(_state.value.persona, history).collect { token ->
                accumulated += token
                _state.update { st ->
                    st.copy(messages = st.messages.map {
                        if (it.id == TEMP_AI_ID) it.copy(content = accumulated) else it
                    })
                }
            }
        }

        // Retire la bulle temporaire, puis persiste la réponse finale.
        _state.update {
            it.copy(
                messages = it.messages.filterNot { m -> m.id == TEMP_AI_ID },
                aiTyping = false
            )
        }
        if (accumulated.isNotBlank()) {
            runCatching { repo.sendMessage(conversationId, accumulated, isFromAi = true) }
                .onSuccess { addOrUpdate(it) }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}

/**
 * Factory : fournit le conversationId et les dépendances Android (audio),
 * pour que le ViewModel reste testable avec des doublures.
 */
class ChatViewModelFactory(
    private val conversationId: String,
    context: Context
) : ViewModelProvider.Factory {
    private val appContext = context.applicationContext

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(
            conversationId = conversationId,
            recorder = AudioRecorder(appContext),
            player = AudioPlayer()
        ) as T
}
