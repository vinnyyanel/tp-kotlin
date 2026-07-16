package com.esgis.chatapp.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val id: String,
    val username: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("is_ai") val isAi: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    @SerialName("is_ai_chat") val isAiChat: Boolean = false,
    @SerialName("ai_persona") val aiPersona: String? = null,
    @SerialName("last_message_at") val lastMessageAt: String? = null
)

@Serializable
data class Message(
    val id: String? = null, // généré par Postgres
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("sender_id") val senderId: String? = null,
    val content: String? = null,
    @SerialName("media_url") val mediaUrl: String? = null,
    @SerialName("media_type") val mediaType: String? = null,
    val status: String = "sent",
    @SerialName("is_from_ai") val isFromAi: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Participant(
    @SerialName("conversation_id") val conversationId: String,
    @SerialName("user_id") val userId: String
)

/** Modèle prêt pour l'affichage dans la liste des conversations. */
data class ConversationUi(
    val id: String,
    val title: String,
    val isAiChat: Boolean,
    val aiPersona: String? = null,
    val lastMessageAt: String? = null
)
