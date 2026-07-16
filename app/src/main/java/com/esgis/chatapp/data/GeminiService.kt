package com.esgis.chatapp.data

import com.esgis.chatapp.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Appels à l'API Gemini en streaming SSE (token par token).
 * Endpoint : models/{model}:streamGenerateContent?alt=sse
 * Clé lue depuis BuildConfig (local.properties -> GEMINI_API_KEY).
 */
object GeminiService {

    // Modèle Flash accessible en tier gratuit sur ce projet (vérifié en 200 OK).
    // gemini-2.0-flash renvoyait quota=0 ; gemini-flash-lite-latest fonctionne
    // (alias qui pointe vers le dernier Flash-Lite, ici gemini-3.1-flash-lite).
    private const val MODEL = "gemini-flash-lite-latest"
    private const val BASE_URL =
        "https://generativelanguage.googleapis.com/v1beta/models"

    private val client = HttpClient(Android)
    private val json = Json { ignoreUnknownKeys = true }

    /** Un tour de conversation à envoyer au modèle. */
    data class Turn(val text: String, val fromAi: Boolean)

    /**
     * Émet les fragments de texte au fur et à mesure qu'ils arrivent.
     * @param history les N derniers messages (exigence du sujet : historique envoyé).
     */
    fun streamReply(persona: AiPersona, history: List<Turn>): Flow<String> = flow {
        val key = BuildConfig.GEMINI_API_KEY
        if (key.isBlank()) {
            emit("[Clé API Gemini manquante — renseigne GEMINI_API_KEY dans local.properties]")
            return@flow
        }

        val url = "$BASE_URL/$MODEL:streamGenerateContent?alt=sse&key=$key"
        val requestBody = GeminiRequest(
            systemInstruction = GeminiContent(parts = listOf(GeminiPart(persona.systemPrompt))),
            contents = history.map {
                GeminiContent(
                    role = if (it.fromAi) "model" else "user",
                    parts = listOf(GeminiPart(it.text))
                )
            }
        )

        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(requestBody))
        }.execute { response ->
            when {
                response.status.value == 429 -> {
                    emit("⏳ Trop de requêtes (limite gratuite atteinte). Réessaie dans un instant.")
                    return@execute
                }
                !response.status.isSuccess() -> {
                    emit("[Erreur IA ${response.status.value}]")
                    return@execute
                }
            }
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: continue
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty() || payload == "[DONE]") continue
                parseToken(payload)?.let { if (it.isNotEmpty()) emit(it) }
            }
        }
    }.catch { e ->
        emit("[Erreur IA : ${e.message}]")
    }.flowOn(Dispatchers.IO)

    private fun parseToken(payload: String): String? = runCatching {
        json.decodeFromString<GeminiStreamResponse>(payload)
            .candidates.firstOrNull()
            ?.content?.parts?.joinToString("") { it.text }
    }.getOrNull()

    // ---- DTOs requête ----
    @Serializable
    private data class GeminiRequest(
        @SerialName("system_instruction") val systemInstruction: GeminiContent,
        val contents: List<GeminiContent>
    )

    @Serializable
    private data class GeminiContent(
        val role: String? = null,
        val parts: List<GeminiPart> = emptyList()
    )

    @Serializable
    private data class GeminiPart(val text: String)

    // ---- DTOs réponse (streaming) ----
    @Serializable
    private data class GeminiStreamResponse(
        val candidates: List<GeminiCandidate> = emptyList()
    )

    @Serializable
    private data class GeminiCandidate(val content: GeminiContent? = null)
}
