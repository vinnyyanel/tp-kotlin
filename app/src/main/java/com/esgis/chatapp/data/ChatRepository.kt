package com.esgis.chatapp.data

/**
 * Contrat de la couche données (abstraction du backend).
 * Permet d'injecter une implémentation factice dans les tests de ViewModels.
 */
interface ChatRepository {

    val currentUserId: String?
    val isLoggedIn: Boolean

    // ---------- AUTH ----------
    suspend fun signUp(email: String, password: String, username: String)
    suspend fun signIn(email: String, password: String)
    suspend fun signOut()

    // ---------- PROFILS ----------
    suspend fun getContacts(): List<Profile>
    suspend fun getUsername(userId: String): String?

    // ---------- CONVERSATIONS ----------
    suspend fun getConversation(conversationId: String): Conversation?
    suspend fun getOtherParticipantId(conversationId: String): String?
    suspend fun getConversationsUi(): List<ConversationUi>
    suspend fun createHumanConversation(otherUserId: String): String
    suspend fun getOrCreateHumanConversation(otherUserId: String): String
    suspend fun createAiConversation(persona: AiPersona): String

    // ---------- MESSAGES ----------
    suspend fun getMessages(conversationId: String): List<Message>
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        isFromAi: Boolean = false
    ): Message

    suspend fun markConversationRead(conversationId: String)

    suspend fun uploadMedia(
        conversationId: String,
        bytes: ByteArray,
        fileName: String,
        type: String
    ): Message
}
