package com.esgis.chatapp.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.esgis.chatapp.data.AudioRecorder
import com.esgis.chatapp.data.Message
import com.esgis.chatapp.data.PresenceManager
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

    val title = if (state.isAiChat) "🤖 Assistant IA · ${state.persona.label}" else "Discussion"
    val online by PresenceManager.online.collectAsState()
    val otherOnline = state.otherUserId != null && state.otherUserId in online

    // Auto-scroll vers le dernier message.
    LaunchedEffect(state.messages.size, state.aiTyping) {
        val count = state.messages.size
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title)
                        if (!state.isAiChat) {
                            Text(
                                text = if (otherOnline) "en ligne" else "hors ligne",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (otherOnline) Color(0xFF2FBF4B)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
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
                        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
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
                    text = "🤖 en train d'écrire…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        imagePicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Text("📎", fontSize = 20.sp)
                }
                TextButton(onClick = { toggleRecording() }) {
                    Text(
                        if (recording) "⏹" else "🎤",
                        fontSize = 20.sp,
                        color = if (recording) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message…") },
                    maxLines = 4
                )
                TextButton(
                    onClick = {
                        val text = input
                        input = ""
                        viewModel.send(text)
                    },
                    enabled = input.isNotBlank()
                ) {
                    Text("Envoyer", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Lecture d'un message audio en streaming depuis son URL publique. */
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(14.dp),
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
                        Text("▶  Message audio", fontWeight = FontWeight.Medium)
                    }
                }
                if (!msg.content.isNullOrBlank()) {
                    Text(text = msg.content)
                }
                if (isMine) {
                    Text(
                        text = if (msg.status == "read") "✓✓ lu" else "✓ envoyé",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}
