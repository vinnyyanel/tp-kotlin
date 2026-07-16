package com.esgis.chatapp.di

import com.esgis.chatapp.data.ChatRepository

/**
 * DI minimaliste : un simple singleton (pas de Hilt, cf. cahier des charges).
 * Toute l'app partage la même instance de ChatRepository.
 */
object ServiceLocator {
    val repository: ChatRepository by lazy { ChatRepository() }
}
