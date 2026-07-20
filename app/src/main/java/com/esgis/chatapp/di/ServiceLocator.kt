package com.esgis.chatapp.di

import com.esgis.chatapp.data.ChatRepository
import com.esgis.chatapp.data.SupabaseChatRepository

/**
 * DI minimaliste : un simple singleton (pas de Hilt, cf. cahier des charges).
 * Expose l'abstraction [ChatRepository] — l'implémentation reste interchangeable.
 */
object ServiceLocator {
    val repository: ChatRepository by lazy { SupabaseChatRepository() }
}
