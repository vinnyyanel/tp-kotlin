package com.esgis.chatapp.data

import com.esgis.chatapp.di.SupabaseModule
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Présence en ligne via Supabase Realtime Presence.
 * Chaque utilisateur connecté "track" son id sur un canal partagé ;
 * on maintient l'ensemble des ids actuellement en ligne.
 */
object PresenceManager {

    private val supabase = SupabaseModule.client
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _online = MutableStateFlow<Set<String>>(emptySet())
    val online: StateFlow<Set<String>> = _online.asStateFlow()

    private var channel: RealtimeChannel? = null
    private var job: Job? = null
    private var currentUser: String? = null

    fun start(userId: String) {
        if (channel != null && currentUser == userId) return
        stop()
        currentUser = userId
        job = scope.launch {
            val ch = supabase.channel("online-users")
            channel = ch
            val presenceFlow = ch.presenceChangeFlow()
            ch.subscribe(true)
            ch.track(buildJsonObject { put("user_id", userId) })
            presenceFlow.collect { action ->
                val set = _online.value.toMutableSet()
                action.joins.values.forEach { p ->
                    p.state["user_id"]?.jsonPrimitive?.contentOrNull?.let { set.add(it) }
                }
                action.leaves.values.forEach { p ->
                    p.state["user_id"]?.jsonPrimitive?.contentOrNull?.let { set.remove(it) }
                }
                _online.value = set
            }
        }
    }

    fun stop() {
        val ch = channel
        channel = null
        currentUser = null
        _online.value = emptySet()
        job?.cancel()
        job = null
        if (ch != null) scope.launch { runCatching { ch.unsubscribe() } }
    }
}
