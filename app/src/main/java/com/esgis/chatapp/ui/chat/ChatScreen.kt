package com.esgis.chatapp.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.esgis.chatapp.data.AudioRecorder
import com.esgis.chatapp.data.Message
import com.esgis.chatapp.data.PresenceManager
import com.esgis.chatapp.ui.components.Avatar
import com.esgis.chatapp.ui.theme.OnlineGreen
import com.esgis.chatapp.ui.theme.ReadBlue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModelFactory(conversationId))
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) viewModel.sendImage(bytes, "photo.jpg")
            }
        }
    }

    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val audioPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted && recorder.start()) recording = true
    }
    fun toggleRecording() {
        if (recording) {
            val bytes = recorder.stop()
            recording = false
            if (bytes != null) viewModel.sendAudio(bytes, "audio.m4a")
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                if (recorder.start()) recording = true
            } else {
                audioPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val title = if (state.isAiChat) "Assistant IA · ${state.persona.label}"
    else state.otherName ?: "Discussion"
    val online by PresenceManager.online.collectAsState()
    val otherOnline = state.otherUserId != null && state.otherUserId in online

    LaunchedEffect(state.messages.size, state.aiTyping) {
        val count = state.messages.size
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(
                            label = title,
                            isAi = state.isAiChat,
                            online = otherOnline && !state.isAiChat,
                            size = 38.dp
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            Text(title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = when {
                                    state.isAiChat -> "toujours disponible"
                                    otherOnline -> "en ligne"
                                    else -> "hors ligne"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (otherOnline && !state.isAiChat) OnlineGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.messages, key = { it.id ?: it.hashCode().toString() }) { msg ->
                            MessageBubble(msg = msg, isMine = msg.senderId == viewModel.myId && !msg.isFromAi)
                        }
                    }
                }
            }

            if (state.aiTyping) {
                Text(
                    text = "Assistant en train d'écrire…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            InputBar(
                input = input,
                onInputChange = { input = it },
                recording = recording,
                onAttach = {
                    imagePicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onSend = {
                    val text = input
                    input = ""
                    viewModel.send(text)
                },
                onMic = { toggleRecording() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InputBar(
    input: String,
    onInputChange: (String) -> Unit,
    recording: Boolean,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onMic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onAttach) {
                    Icon(
                        Icons.Rounded.AttachFile,
                        contentDescription = "Joindre une photo",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextField(
                    value = input,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…") },
                    maxLines = 4,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        val sendMode = input.isNotBlank()
        Surface(
            shape = CircleShape,
            color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        ) {
            IconButton(onClick = { if (sendMode) onSend() else onMic() }) {
                Icon(
                    imageVector = when {
                        sendMode -> Icons.AutoMirrored.Rounded.Send
                        recording -> Icons.Rounded.Stop
                        else -> Icons.Rounded.Mic
                    },
                    contentDescription = if (sendMode) "Envoyer" else "Enregistrer un audio",
                    tint = if (recording) MaterialTheme.colorScheme.onError
                    else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

private fun playAudioUrl(url: String) {
    runCatching {
        MediaPlayer().apply {
            setDataSource(url)
            setOnPreparedListener { start() }
            setOnCompletionListener { it.release() }
            setOnErrorListener { mp, _, _ -> mp.release(); true }
            prepareAsync()
        }
    }
}

@Composable
private fun MessageBubble(msg: Message, isMine: Boolean) {
    val bubbleColor = when {
        isMine -> MaterialTheme.colorScheme.primaryContainer
        msg.isFromAi -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val onBubble = when {
        isMine -> MaterialTheme.colorScheme.onPrimaryContainer
        msg.isFromAi -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val shape = if (isMine) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = shape,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.mediaType == "image" && msg.mediaUrl != null) {
                    AsyncImage(
                        model = msg.mediaUrl,
                        contentDescription = "Photo",
                        modifier = Modifier
                            .widthIn(max = 240.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                if (msg.mediaType == "audio" && msg.mediaUrl != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { playAudioUrl(msg.mediaUrl) }
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = "Lire l'audio",
                            tint = onBubble
                        )
                        Text("Message audio", color = onBubble, fontWeight = FontWeight.Medium)
                    }
                }
                if (!msg.content.isNullOrBlank()) {
                    Text(text = msg.content, color = onBubble)
                }
                if (isMine) {
                    Row(
                        modifier = Modifier.align(Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        msg.createdAt?.let {
                            Text(
                                text = formatTime(it),
                                style = MaterialTheme.typography.labelSmall,
                                color = onBubble.copy(alpha = 0.7f)
                            )
                        }
                        Icon(
                            imageVector = if (msg.status == "read") Icons.Rounded.DoneAll else Icons.Rounded.Done,
                            contentDescription = if (msg.status == "read") "Lu" else "Envoyé",
                            tint = if (msg.status == "read") ReadBlue else onBubble.copy(alpha = 0.7f),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(iso: String): String {
    val t = iso.indexOf('T')
    return if (t >= 0 && iso.length >= t + 6) iso.substring(t + 1, t + 6) else iso.take(10)
}
