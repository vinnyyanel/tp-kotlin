package com.esgis.chatapp.data

import com.esgis.chatapp.di.SupabaseModule
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ChatRepository {

    private val supabase = SupabaseModule.client
    private val json = Json { ignoreUnknownKeys = true }

    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id

    val isLoggedIn: Boolean
        get() = currentUserId != null

    // ---------- AUTH ----------
    suspend fun signUp(email: String, password: String, username: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            // Repris par le trigger handle_new_user() pour créer le profil.
            this.data = buildJsonObject { put("username", username) }
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() = supabase.auth.signOut()

    // ---------- PROFILS / CONTACTS ----------
    /** Tous les profils humains sauf soi-même (pour démarrer une conversation). */
    suspend fun getContacts(): List<Profile> {
        val me = currentUserId
        return supabase.from("profiles").select().decodeList<Profile>()
            .filter { it.id != me && !it.isAi }
    }

    private suspend fun getProfile(userId: String): Profile? =
        supabase.from("profiles").select {
            filter { eq("id", userId) }
        }.decodeSingleOrNull<Profile>()

    // ---------- CONVERSATIONS ----------
    suspend fun getConversation(conversationId: String): Conversation? =
        supabase.from("conversations").select {
            filter { eq("id", conversationId) }
        }.decodeSingleOrNull<Conversation>()

    /**
     * Liste unifiée des conversations de l'utilisateur, triée par dernier message.
     * Le RLS Supabase ne renvoie déjà QUE mes conversations (is_member).
     */
    suspend fun getConversationsUi(): List<ConversationUi> {
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
                    lastMessageAt = c.lastMessageAt
                )
            }
        }
    }

    /** Crée une conversation humaine avec un contact et renvoie son id. */
    suspend fun createHumanConversation(otherUserId: String): String {
        val me = currentUserId ?: error("Non authentifié")
        // id généré côté client -> pas besoin de return=representation
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

    /** Crée une conversation avec l'agent IA (persona) et renvoie son id. */
    suspend fun createAiConversation(persona: AiPersona): String {
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

    // ---------- HISTORIQUE ----------
    suspend fun getMessages(conversationId: String): List<Message> =
        supabase.from("messages").select {
            filter { eq("conversation_id", conversationId) }
            order("created_at", Order.ASCENDING)
        }.decodeList<Message>()

    // ---------- ENVOI ----------
    /** Insère un message et renvoie la ligne créée (id + created_at générés). */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        isFromAi: Boolean = false
    ): Message =
        supabase.from("messages").insert(
            Message(
                conversationId = conversationId,
                senderId = if (isFromAi) null else currentUserId,
                content = content,
                isFromAi = isFromAi
            )
        ) { select() }.decodeSingle<Message>()

    // ---------- ACCUSÉ DE LECTURE ----------
    /** Marque comme lus les messages des autres dans une conversation. */
    suspend fun markConversationRead(conversationId: String) {
        val me = currentUserId ?: return
        supabase.from("messages").update({ set("status", "read") }) {
            filter {
                eq("conversation_id", conversationId)
                neq("sender_id", me)
            }
        }
    }

    // ---------- MÉDIAS (photo / audio) — P1 ----------
    /** Upload un média dans le bucket Storage puis insère le message. Renvoie le message créé. */
    suspend fun uploadMedia(
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

    // ---------- TEMPS RÉEL ----------
    /**
     * Flow qui émet les messages insérés ET mis à jour (statut lu) dans la conversation.
     * Les UPDATE permettent à l'expéditeur de voir passer ✓ -> ✓✓ en direct.
     */
    suspend fun observeMessages(conversationId: String): Flow<Message> {
        val channel = supabase.channel("messages_$conversationId")
        val inserts = channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.map { action -> json.decodeFromString<Message>(action.record.toString()) }
        val updates = channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "messages"
            filter("conversation_id", FilterOperator.EQ, conversationId)
        }.map { action -> json.decodeFromString<Message>(action.record.toString()) }
        channel.subscribe()
        return merge(inserts, updates)
    }
}
