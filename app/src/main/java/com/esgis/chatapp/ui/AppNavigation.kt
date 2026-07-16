package com.esgis.chatapp.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.esgis.chatapp.ui.auth.AuthScreen
import com.esgis.chatapp.ui.chat.ChatScreen
import com.esgis.chatapp.ui.conversations.ConversationsScreen

object Routes {
    const val AUTH = "auth"
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{conversationId}"
    fun chat(conversationId: String) = "chat/$conversationId"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.AUTH) {

        composable(Routes.AUTH) {
            AuthScreen(
                onAuthenticated = {
                    navController.navigate(Routes.CONVERSATIONS) {
                        popUpTo(Routes.AUTH) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.CONVERSATIONS) {
            ConversationsScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onLoggedOut = {
                    navController.navigate(Routes.AUTH) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("conversationId").orEmpty()
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
