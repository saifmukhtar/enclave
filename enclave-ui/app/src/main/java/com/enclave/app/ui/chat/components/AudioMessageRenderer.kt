package com.enclave.app.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.media.MemoryMediaDataSource
import com.enclave.app.ui.chat.ChatMessage
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.theme.CharcoalText
import com.enclave.app.ui.theme.RoseAccent

@Composable
fun VoiceMessageContent(message: ChatMessage, viewModel: ChatViewModel) {
    val activePlayingId by viewModel.activePlayingVoiceMessageId.collectAsState()
    val isPlaying = activePlayingId == message.id
    var durationState by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(message.id) {
        val bytes = viewModel.getMediaBytes(message.id)
        if (bytes != null) {
            try {
                val mp = android.media.MediaPlayer()
                val dataSource = MemoryMediaDataSource(bytes)
                mp.setDataSource(dataSource)
                mp.prepare()
                durationState = mp.duration / 1000
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        IconButton(
            onClick = { viewModel.playVoiceMessage(message.id) },
            modifier = Modifier
                .size(32.dp)
                .background(CharcoalText.copy(alpha = 0.08f), CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play voice capsule",
                tint = CharcoalText,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.width(80.dp)
        ) {
            val heights = listOf(10, 16, 8, 20, 14, 24, 12, 18, 8, 14, 10)
            heights.forEach { h ->
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(h.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(if (isPlaying) RoseAccent else CharcoalText.copy(alpha = 0.3f))
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = durationState?.let { d -> "${d / 60}:${(d % 60).toString().padStart(2, '0')}" } ?: "0:00",
            fontSize = 11.sp,
            color = CharcoalText.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun AudioFileMessageContent(message: ChatMessage, viewModel: ChatViewModel) {
    val activePlayingId by viewModel.activePlayingVoiceMessageId.collectAsState()
    val isPlaying = activePlayingId == message.id
    var durationState by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(message.id) {
        val bytes = viewModel.getMediaBytes(message.id)
        if (bytes != null) {
            try {
                val mp = android.media.MediaPlayer()
                val dataSource = MemoryMediaDataSource(bytes)
                mp.setDataSource(dataSource)
                mp.prepare()
                durationState = mp.duration / 1000
                mp.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalText.copy(alpha = 0.05f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        IconButton(
            onClick = { viewModel.playVoiceMessage(message.id) },
            modifier = Modifier
                .size(36.dp)
                .background(RoseAccent, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Play audio file",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "🎵 Shared Audio",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = CharcoalText
            )
            Text(
                text = durationState?.let { d -> "${d / 60}:${(d % 60).toString().padStart(2, '0')}" } ?: "0:00",
                fontSize = 10.sp,
                color = CharcoalText.copy(alpha = 0.6f)
            )
        }
    }
}
