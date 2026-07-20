package com.esgis.chatapp

import com.esgis.chatapp.data.AiPersona
import com.esgis.chatapp.data.ChatRepository
import com.esgis.chatapp.data.Conversation
import com.esgis.chatapp.data.ConversationUi
import com.esgis.chatapp.data.Message
import com.esgis.chatapp.data.Profile

/** Doublure en mémoire du [ChatRepository] : aucun réseau, comportement pilotable. */
class FakeChatRepository : ChatRepository {

    var loggedInUser: String? = null
    var shouldFail: Boolean = false
    var failMessage: String = "Erreur réseau"

    var contacts: List<Profile> = emptyList()
    var conversations: List<ConversationUi> = emptyList()

    val signUpCalls = mutableListOf<Triple<String, String, String>>()
    val signInCalls = mutableListOf<Pair<String, String>>()
    val sentMessages = mutableListOf<Message>()
    val readConversations = mutableListOf<String>()

    override val currentUserId: String?
        get() = loggedInUser

    override val isLoggedIn: Boolean
        get() = loggedInUser != null

    private fun failIfNeeded() {
        if (shouldFail) throw IllegalStateException(failMessage)
    }

    override suspend fun signUp(email: String, password: String, username: String) {
        failIfNeeded()
        signUpCalls += Triple(email, password, username)
        loggedInUser = "user-$username"
    }

    override suspend fun signIn(email: String, password: String) {
        failIfNeeded()
        signInCalls += (email to password)
        loggedInUser = "user-1"
    }

    override suspend fun signOut() {
        loggedInUser = null
    }

    override suspend fun getContacts(): List<Profile> {
        failIfNeeded()
        return contacts
    }

    override suspend fun getUsername(userId: String): String? =
        contacts.firstOrNull { it.id == userId }?.username

    override suspend fun getConversation(conversationId: String): Conversation? = null

    override suspend fun getOtherParticipantId(conversationId: String): String? = null

    override suspend fun getConversationsUi(): List<ConversationUi> {
        failIfNeeded()
        return conversations
    }

    override suspend fun createHumanConversation(otherUserId: String): String {
        failIfNeeded()
        return "conv-$otherUserId"
    }

    override suspend fun getOrCreateHumanConversation(otherUserId: String): String {
        failIfNeeded()
        return "conv-$otherUserId"
    }

    override suspend fun createAiConversation(persona: AiPersona): String {
        failIfNeeded()
        return "conv-ai-${persona.id}"
    }

    override suspend fun getMessages(conversationId: String): List<Message> = emptyList()

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        isFromAi: Boolean
    ): Message {
        failIfNeeded()
        val msg = Message(
            id = "m${sentMessages.size}",
            conversationId = conversationId,
            content = content,
            isFromAi = isFromAi
        )
        sentMessages += msg
        return msg
    }

    override suspend fun markConversationRead(conversationId: String) {
        readConversations += conversationId
    }

    override suspend fun uploadMedia(
        conversationId: String,
        bytes: ByteArray,
        fileName: String,
        type: String
    ): Message {
        failIfNeeded()
        val msg = Message(
            id = "media${sentMessages.size}",
            conversationId = conversationId,
            mediaUrl = "https://example.test/$fileName",
            mediaType = type
        )
        sentMessages += msg
        return msg
    }
}
