package com.esgis.chatapp.ui.conversations

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import com.esgis.chatapp.data.ConversationUi
import com.esgis.chatapp.data.MessageNotifier
import com.esgis.chatapp.data.Profile
import com.esgis.chatapp.ui.chat.PersonaPickerDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationsScreen(
    onOpenChat: (String) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: ConversationsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val message by viewModel.message.collectAsState()
    val onlineUsers by viewModel.onlineUsers.collectAsState()

    var showContacts by remember { mutableStateOf(false) }
    var showPersonaPicker by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Notifications locales : permission (Android 13+) + démarrage de l'écoute globale.
    val context = LocalContext.current
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        viewModel.myId?.let { MessageNotifier.start(context.applicationContext, it) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Conversations") },
                actions = {
                    TextButton(onClick = { showPersonaPicker = true }) { Text("🤖 IA") }
                    TextButton(onClick = { viewModel.logout(onLoggedOut) }) { Text("Quitter") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                viewModel.loadContacts()
                showContacts = true
            }) {
                Text("＋", fontSize = 24.sp)
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is ConversationsUiState.Loading -> {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                is ConversationsUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Erreur : ${s.message}", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { viewModel.refresh() }) { Text("Réessayer") }
                    }
                }

                is ConversationsUiState.Data -> {
                    if (s.conversations.isEmpty()) {
                        Text(
                            "Aucune conversation.\nTouche 🤖 IA ou ＋ pour commencer.",
                            modifier = Modifier.align(Alignment.Center).padding(24.dp)
                        )
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(s.conversations, key = { it.id }) { conv ->
                                val isOnline = conv.otherUserId != null && conv.otherUserId in onlineUsers
                                ConversationRow(conv, isOnline) { onOpenChat(conv.id) }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContacts) {
        ContactsDialog(
            contacts = contacts,
            onDismiss = { showContacts = false },
            onPick = { profile ->
                showContacts = false
                viewModel.startHumanChat(profile.id, onOpenChat)
            }
        )
    }

    if (showPersonaPicker) {
        PersonaPickerDialog(
            onDismiss = { showPersonaPicker = false },
            onPersonaSelected = { persona ->
                showPersonaPicker = false
                viewModel.startAiChat(persona, onOpenChat)
            }
        )
    }
}

@Composable
private fun ConversationRow(conv: ConversationUi, isOnline: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conv.isAiChat) Text("🤖  ")
                Text(
                    text = conv.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            when {
                conv.isAiChat -> Text(
                    "Agent IA",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
                isOnline -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(OnlineGreen)
                    )
                    Text(
                        "  en ligne",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnlineGreen
                    )
                }
                else -> Text(
                    "hors ligne",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        conv.lastMessageAt?.let {
            Text(
                text = formatTime(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContactsDialog(
    contacts: List<Profile>,
    onDismiss: () -> Unit,
    onPick: (Profile) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nouvelle conversation") },
        text = {
            if (contacts.isEmpty()) {
                Text("Aucun autre utilisateur inscrit pour l'instant.")
            } else {
                LazyColumn {
                    items(contacts, key = { it.id }) { profile ->
                        Text(
                            text = profile.username,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(profile) }
                                .padding(vertical = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Fermer") } }
    )
}

private val OnlineGreen = Color(0xFF2FBF4B)

/** ISO 8601 -> "HH:mm" (best-effort, sans dépendance date). */
private fun formatTime(iso: String): String {
    val t = iso.indexOf('T')
    return if (t >= 0 && iso.length >= t + 6) iso.substring(t + 1, t + 6) else iso.take(10)
}
