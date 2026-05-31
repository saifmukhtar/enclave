@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.chat.components

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.chat.ChatUiState
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ChatInputBar(
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    onAttachClick: () -> Unit,
    onRecordVoiceClick: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val isRecording by viewModel.isRecording.collectAsState()
    val recordingDuration by viewModel.recordingDurationSeconds.collectAsState()
    val recordingAmplitude by viewModel.recordingAmplitude.collectAsState()
    val disappearingMode by viewModel.disappearingMode.collectAsState()
    var showTimerMenu by remember { mutableStateOf(false) }

    val replyTo by viewModel.replyToMessage.collectAsState()
    val context = LocalContext.current

    // Helper: send and dismiss keyboard atomically
    val doSend: () -> Unit = {
        if (text.isNotBlank()) {
            if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                Toast.makeText(context, "Cannot send message: Connection not secured.", Toast.LENGTH_SHORT).show()
            } else {
                viewModel.sendMessage(text)
                text = ""
            }
        }
    }

    LaunchedEffect(text) {
        if (text.isNotBlank()) {
            viewModel.sendTypingStatus(true)
            delay(2000)
            viewModel.sendTypingStatus(false)
        } else {
            viewModel.sendTypingStatus(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val myProfile by viewModel.myProfile.collectAsState()
        val partnerProfile by viewModel.partnerProfile.collectAsState()
        val myUsername = myProfile?.username?.ifBlank { null } ?: "Me"
        val partnerUsername = partnerProfile?.username?.ifBlank { null } ?: "Partner"

        Column(modifier = Modifier.fillMaxWidth()) {
            // Reply preview sits above the input row
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = RoseAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (replyTo!!.isFromMe) myUsername else partnerUsername,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RoseAccent
                        )
                        Text(
                            text = replyTo!!.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp,
                            color = CharcoalText.copy(alpha = 0.7f)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setReplyToMessage(null) },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isRecording) {
                    RecordingInputRow(
                        recordingDuration = recordingDuration,
                        recordingAmplitude = recordingAmplitude,
                        onCancel = { viewModel.cancelAudioRecording() },
                        onSend = { viewModel.stopAudioRecording() }
                    )
                } else {
                    StandardInputRow(
                        text = text,
                        onTextChange = { text = it },
                        disappearingMode = disappearingMode,
                        showTimerMenu = showTimerMenu,
                        onToggleTimerMenu = { showTimerMenu = it },
                        onSetDisappearing = { viewModel.setDisappearingMode(it) },
                        onAttachClick = {
                            if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                Toast.makeText(context, "Cannot send attachments: Connection not secured.", Toast.LENGTH_SHORT).show()
                            } else {
                                onAttachClick()
                            }
                        },
                        onRecordVoiceClick = {
                            if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                Toast.makeText(context, "Cannot record voice: Connection not secured.", Toast.LENGTH_SHORT).show()
                            } else {
                                onRecordVoiceClick()
                            }
                        },
                        onSendMessage = doSend,
                        onSendLaterClick = { sendAt ->
                            if (text.isNotBlank()) {
                                if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                    Toast.makeText(context, "Cannot send message: Connection not secured.", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.sendTimeCapsuleMessage(text, sendAt)
                                    Toast.makeText(context, "Message scheduled", Toast.LENGTH_SHORT).show()
                                    text = ""
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.RecordingInputRow(
    recordingDuration: Int,
    recordingAmplitude: Float,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rec_blink")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "rec_alpha"
    )

    IconButton(
        onClick = onCancel,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = BlushSent,
            contentColor = CharcoalText
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Cancel Recording",
            tint = RoseAccent
        )
    }

    Spacer(modifier = Modifier.width(8.dp))

    Row(
        modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .alpha(alpha)
                .background(Color.Red, CircleShape)
        )
        Spacer(modifier = Modifier.width(6.dp))
        val mins = recordingDuration / 60
        val secs = recordingDuration % 60
        Text(
            text = "%02d:%02d".format(mins, secs),
            fontFamily = PlayfairFont,
            fontSize = 14.sp,
            color = CharcoalText
        )
        Spacer(modifier = Modifier.width(16.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(8) { index ->
                val heightFactor = maxOf(0.1f, recordingAmplitude * (1f + (index % 3) * 0.5f))
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .width(3.dp)
                        .fillMaxHeight(heightFactor * 0.8f)
                        .background(RoseAccent, CircleShape)
                )
            }
        }
    }

    Spacer(modifier = Modifier.width(8.dp))

    FloatingActionButton(
        onClick = onSend,
        containerColor = RoseAccent,
        contentColor = BlushBackground,
        shape = CircleShape
    ) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice")
    }
}

@Composable
private fun RowScope.StandardInputRow(
    text: String,
    onTextChange: (String) -> Unit,
    disappearingMode: Long,
    showTimerMenu: Boolean,
    onToggleTimerMenu: (Boolean) -> Unit,
    onSetDisappearing: (Long) -> Unit,
    onAttachClick: () -> Unit,
    onRecordVoiceClick: () -> Unit,
    onSendMessage: () -> Unit,
    onSendLaterClick: (Long) -> Unit
) {
    var showSendLaterMenu by remember { mutableStateOf(false) }

    val isTextBlank = text.isBlank()
    val recordScale by animateFloatAsState(
        targetValue = if (isTextBlank) 1.0f else 0.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "record_scale"
    )
    val sendScale by animateFloatAsState(
        targetValue = if (isTextBlank) 0.0f else 1.0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "send_scale"
    )
    val actionAreaWidth by animateDpAsState(
        targetValue = if (isTextBlank) 48.dp else 100.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "action_width"
    )

    IconButton(
        onClick = onAttachClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = BlushSent,
            contentColor = CharcoalText
        ),
        modifier = Modifier.size(48.dp)
    ) {
        Icon(Icons.Default.Add, contentDescription = "Attach")
    }

    Spacer(modifier = Modifier.width(6.dp))

    Box {
        IconButton(
            onClick = { onToggleTimerMenu(true) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (disappearingMode > 0) RoseAccent.copy(alpha = 0.2f) else BlushSent,
                contentColor = if (disappearingMode > 0) RoseAccent else CharcoalText
            ),
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Disappearing timer"
            )
        }

        DropdownMenu(
            expanded = showTimerMenu,
            onDismissRequest = { onToggleTimerMenu(false) },
            modifier = Modifier.background(BlushReceived)
        ) {
            val options = listOf(
                "Off" to 0L,
                "5 seconds" to 5L,
                "10 seconds" to 10L,
                "30 seconds" to 30L,
                "1 minute" to 60L,
                "1 hour" to 3600L
            )
            options.forEach { (label, secs) ->
                DropdownMenuItem(
                    text = { Text(label, fontFamily = InterFont, color = CharcoalText) },
                    onClick = {
                        onSetDisappearing(secs)
                        onToggleTimerMenu(false)
                    }
                )
            }
        }
    }

    Spacer(modifier = Modifier.width(8.dp))

    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(24.dp)),
        placeholder = {
            Text(
                "Secure message...",
                fontFamily = InterFont,
                color = CharcoalText.copy(alpha = 0.5f)
            )
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White.copy(alpha = 0.6f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.6f),
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            focusedTextColor = CharcoalText,
            unfocusedTextColor = CharcoalText
        ),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Send,
            keyboardType = KeyboardType.Text
        ),
        keyboardActions = KeyboardActions(
            onSend = { onSendMessage() }
        ),
        maxLines = 6,
        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
    )

    Spacer(modifier = Modifier.width(8.dp))

    Box(
        modifier = Modifier.width(actionAreaWidth).height(48.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (recordScale > 0.01f) {
            IconButton(
                onClick = onRecordVoiceClick,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = BlushSent,
                    contentColor = RoseAccent
                ),
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer(scaleX = recordScale, scaleY = recordScale)
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Record Audio"
                )
            }
        }
        if (sendScale > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer(scaleX = sendScale, scaleY = sendScale)
            ) {
                Box {
                    IconButton(
                        onClick = { showSendLaterMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BlushSent,
                            contentColor = RoseAccent
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(Icons.Default.Schedule, contentDescription = "Send Later")
                    }
                    DropdownMenu(
                        expanded = showSendLaterMenu,
                        onDismissRequest = { showSendLaterMenu = false },
                        modifier = Modifier.background(BlushReceived)
                    ) {
                        val now = System.currentTimeMillis()
                        val laterOptions = listOf(
                            "In 1 minute (test)" to now + 60_000L,
                            "In 1 hour" to now + 3600_000L,
                            "Tomorrow" to now + 86400_000L
                        )
                        laterOptions.forEach { (label, time) ->
                            DropdownMenuItem(
                                text = { Text(label, fontFamily = InterFont, color = CharcoalText) },
                                onClick = {
                                    onSendLaterClick(time)
                                    showSendLaterMenu = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(4.dp))
                
                FloatingActionButton(
                    onClick = onSendMessage,
                    containerColor = RoseDeep,
                    contentColor = Color.White,
                    shape = CircleShape,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                }
            }
        }
    }
}


// ─── Typing indicator ─────────────────────────────────────────────────────────

@Composable
fun BouncingTypingIndicator() {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition(label = "typing_dot_$index")
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 100, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bounce_$index"
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = bounce.dp)
                    .background(RoseAccent, CircleShape)
            )
        }
    }
}
