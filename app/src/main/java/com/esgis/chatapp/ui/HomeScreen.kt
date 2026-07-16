package com.esgis.chatapp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.esgis.chatapp.ui.conversations.ConversationsScreen
import com.esgis.chatapp.ui.users.UsersScreen

/**
 * Conteneur principal après connexion : barre d'onglets en bas
 * (Discussions | Utilisateurs). L'écran de chat reste plein écran.
 */
@Composable
fun HomeScreen(
    onOpenChat: (String) -> Unit,
    onLoggedOut: () -> Unit
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Text("💬") },
                    label = { Text("Discussions") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Text("👥") },
                    label = { Text("Utilisateurs") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> ConversationsScreen(onOpenChat = onOpenChat, onLoggedOut = onLoggedOut)
                else -> UsersScreen(onOpenChat = onOpenChat)
            }
        }
    }
}
