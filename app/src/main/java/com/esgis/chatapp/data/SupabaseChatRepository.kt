package com.esgis.chatapp.data

import com.esgis.chatapp.di.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** Implémentation Supabase du [ChatRepository]. */
class SupabaseChatRepository : ChatRepository {

    private val supabase = SupabaseModule.client

    override val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    override val isLoggedIn: Boolean
        get() = currentUserId != null

    // ---------- AUTH ----------
    override suspend fun signUp(email: String, password: String, username: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            // Repris par le trigger handle_new_user() pour créer le profil.
            this.data = buildJsonObject { put("username", username) }
        }
    }

    override suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    override suspend fun signOut() {
        supabase.auth.signOut()
    }

    // ---------- PROFILS / CONTACTS ----------
    override suspend fun getContacts(): List<Profile> {
        val me = currentUserId
        return supabase.from("profiles").select().decodeList<Profile>()
            .filter { it.id != me && !it.isAi }
    }

    private suspend fun getProfile(userId: String): Profile? =
        supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingleOrNull<Profile>()

    override suspend fun getUsername(userId: String): String? = getProfile(userId)?.username

    // ---------- CONVERSATIONS ----------
    override suspend fun getConversation(conversationId: String): Conversation? =
        supabase.from("conversations").select {
            filter { eq("id", conversationId) }
        }.decodeSingleOrNull<Conversation>()

    override suspend fun getOtherParticipantId(conversationId: String): String? {
        val me = currentUserId
        val parts = supabase.from("participants").select {
            filter { eq("conversation_id", conversationId) }
        }.decodeList<Participant>()
        return parts.map { it.userId }.firstOrNull { it != me }
    }

    /**
     * Liste unifiée des conversations, triée par dernier message.
     * Le RLS Supabase ne renvoie déjà QUE mes conversations (is_member).
     */
    override suspend fun getConversationsUi(): List<ConversationUi> {
        val me = currentUserId
        val convs = supabase.from("conversations").select {
            order("last_message_at", Order.DESCENDING)
        }.decodeList<Conversation>()

        return convs.map { c ->
            if (c.isAiChat) {
                val persona = AiPersona.fromId(c.aiPersona)
                ConversationUi(
                    id = c.id,
                    title = "Assistant IA · ${persona.label}",
                    isAiChat = true,
                    aiPersona = c.aiPersona,
                    lastMessageAt = c.lastMessageAt
                )
            } else {
                val parts = supabase.from("participants").select {
                    filter { eq("conversation_id", c.id) }
                }.decodeList<Participant>()
                val otherId = parts.map { it.userId }.firstOrNull { it != me }
                val name = otherId?.let { getProfile(it)?.username } ?: "Conversation"
                ConversationUi(
                    id = c.id,
                    title = name,
                    isAiChat = false,
                    lastMessageAt = c.lastMessageAt,
                    otherUserId = otherId
                )
            }
        }
    }

    override suspend fun createHumanConversation(otherUserId: String): String {
        val me = currentUserId ?: error("Non authentifié")
        // id généré côté client -> pas de return=representation
        // (sinon la policy SELECT is_member échoue tant qu'on n'est pas participant).
        val convId = UUID.randomUUID().toString()
        supabase.from("conversations").insert(
            buildJsonObject {
                put("id", convId)
                put("is_ai_chat", false)
            }
        )
        // Mon participant d'abord (is_member = true), puis l'autre.
        supabase.from("participants").insert(Participant(convId, me))
        supabase.from("participants").insert(Participant(convId, otherUserId))
        return convId
    }

    override suspend fun getOrCreateHumanConversation(otherUserId: String): String {
        val me = currentUserId ?: error("Non authentifié")
        val convs = supabase.from("conversations").select {
            filter { eq("is_ai_chat", false) }
        }.decodeList<Conversation>()
        for (c in convs) {
            val members = supabase.from("participants").select {
                filter { eq("conversation_id", c.id) }
            }.decodeList<Participant>().map { it.userId }.toSet()
            if (members == setOf(me, otherUserId)) return c.id
        }
        return createHumanConversation(otherUserId)
    }

    override suspend fun createAiConversation(persona: AiPersona): String {
        val me = currentUserId ?: error("Non authentifié")
        val convId = UUID.randomUUID().toString()
        supabase.from("conversations").insert(
            buildJsonObject {
                put("id", convId)
                put("is_ai_chat", true)
                put("ai_persona", persona.id)
            }
        )
        supabase.from("participants").insert(Participant(convId, me))
        return convId
    }

    // ---------- MESSAGES ----------
    override suspend fun getMessages(conversationId: String): List<Message> =
        supabase.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<Message>()

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        isFromAi: Boolean
    ): Message =
        supabase.from("messages").insert(
            Message(
                conversationId = conversationId,
                senderId = if (isFromAi) null else currentUserId,
                content = content,
                isFromAi = isFromAi
            )
        ) { select() }.decodeSingle<Message>()

    /** Marque comme lus les messages des autres (uniquement ceux non lus). */
    override suspend fun markConversationRead(conversationId: String) {
        val me = currentUserId ?: return
        supabase.from("messages").update({ set("status", "read") }) {
            filter {
                eq("conversation_id", conversationId)
                neq("sender_id", me)
                neq("status", "read")
            }
        }
    }

    // ---------- MÉDIAS ----------
    override suspend fun uploadMedia(
        conversationId: String,
        bytes: ByteArray,
        fileName: String,
        type: String
    ): Message {
        val path = "$conversationId/${System.currentTimeMillis()}_$fileName"
        supabase.storage.from("media").upload(path, bytes)
        val publicUrl = supabase.storage.from("media").publicUrl(path)
        return supabase.from("messages").insert(
            Message(
                conversationId = conversationId,
                senderId = currentUserId,
                mediaUrl = publicUrl,
                mediaType = type
            )
        ) { select() }.decodeSingle<Message>()
    }
}
