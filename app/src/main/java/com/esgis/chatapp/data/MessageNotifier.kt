package com.esgis.chatapp.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.esgis.chatapp.di.SupabaseModule
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * Notifications locales : écoute globale des nouveaux messages via Realtime,
 * et notifie pour ceux reçus des autres dans une conversation qu'on ne regarde pas.
 * (Fallback local accepté par le cahier des charges, sans FCM.)
 */
object MessageNotifier {

    private val supabase = SupabaseModule.client
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var job: Job? = null
    private var appContext: Context? = null
    private var myId: String? = null
    private var myConversationIds: Set<String> = emptySet()

    /** Conversation actuellement ouverte : pas de notification pour celle-ci. */
    @Volatile
    var activeConversationId: String? = null

    private const val CHANNEL_ID = "messages"
    private var notifId = 1000

    fun start(context: Context, userId: String) {
        appContext = context.applicationContext
        if (myId == userId && job != null) return
        stop()
        myId = userId
        createChannel()
        RealtimeMessages.start()
        job = scope.launch {
            refreshConversationIds()
            // consomme le canal Realtime partagé (uniquement les insertions)
            RealtimeMessages.inserts.collect { handle(it) }
        }
    }

    private suspend fun refreshConversationIds() {
        runCatching {
            myConversationIds = supabase.from("conversations").select()
                .decodeList<Conversation>().map { it.id }.toSet()
        }
    }

    private suspend fun handle(msg: Message) {
        val me = myId ?: return
        if (msg.senderId == me || msg.isFromAi) return
        if (msg.conversationId == activeConversationId) return
        if (msg.conversationId !in myConversationIds) {
            refreshConversationIds()
            if (msg.conversationId !in myConversationIds) return
        }
        showNotification(msg)
    }

    private fun showNotification(msg: Message) {
        val ctx = appContext ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        val text = when {
            !msg.content.isNullOrBlank() -> msg.content!!
            msg.mediaType == "image" -> "📷 Photo"
            msg.mediaType == "audio" -> "🎤 Message audio"
            else -> "Nouveau message"
        }
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("Nouveau message")
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(notifId++, notif) }
    }

    private fun createChannel() {
        val ctx = appContext ?: return
        val channel = NotificationChannel(
            CHANNEL_ID, "Messages", NotificationManager.IMPORTANCE_HIGH
        )
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
