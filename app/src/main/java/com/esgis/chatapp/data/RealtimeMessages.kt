package com.esgis.chatapp.data

import com.esgis.chatapp.di.SupabaseModule
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * UN SEUL canal Realtime pour la table `messages`, partagé par toute l'app
 * (notifications + écran de chat). Évite d'avoir plusieurs souscriptions
 * postgres_changes sur la même table (supabase-kt n'en routerait qu'une).
 * Expose deux flux : insertions et mises à jour (statut lu).
 */
object RealtimeMessages {

    private val supabase = SupabaseModule.client
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var job: Job? = null
    private var channel: RealtimeChannel? = null

    private val _inserts = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val inserts: SharedFlow<Message> = _inserts.asSharedFlow()

    private val _updates = MutableSharedFlow<Message>(extraBufferCapacity = 64)
    val updates: SharedFlow<Message> = _updates.asSharedFlow()

    fun start() {
        if (job != null) return
        job = scope.launch {
            val ch = supabase.channel("messages-global")
            channel = ch
            val insertFlow = ch.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                table = "messages"
            }
            val updateFlow = ch.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
                table = "messages"
            }
            ch.subscribe(true)
            scope.launch {
                insertFlow.collect {
                    runCatching { _inserts.emit(json.decodeFromString<Message>(it.record.toString())) }
                }
            }
            scope.launch {
                updateFlow.collect {
                    runCatching { _updates.emit(json.decodeFromString<Message>(it.record.toString())) }
                }
            }
        }
    }

    fun stop() {
        val ch = channel
        channel = null
        job?.cancel()
        job = null
        if (ch != null) scope.launch { runCatching { ch.unsubscribe() } }
    }
}
