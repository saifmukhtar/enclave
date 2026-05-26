@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.ui.chat.ChatMessage
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.theme.*
import kotlin.math.roundToInt

// ─── Swipe-to-Reply Message Bubble ───────────────────────────────────────────

@Composable
fun SwipeToReplyMessageBubble(
    message: ChatMessage,
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onMediaClick: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    val isPressed = remember { mutableStateOf(false) }
    val scaleFactor by animateFloatAsState(
        targetValue = if (isPressed.value) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "bubble_scale"
    )

    LaunchedEffect(Unit) {
        if (!message.isFromMe && message.deliveryStatus != "READ") {
            viewModel.markMessageAsRead(message.id)
        }
    }

    val bubbleShape = RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = if (message.isFromMe) 24.dp else 4.dp,
        bottomEnd = if (message.isFromMe) 4.dp else 24.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .swipeToReplyGesture(
                onReplyTriggered = { viewModel.setReplyToMessage(message) },
                haptic = haptic
            ),
        contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            contentAlignment = if (message.isFromMe) Alignment.BottomStart else Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .graphicsLayer(
                        scaleX = scaleFactor,
                        scaleY = scaleFactor,
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            pivotFractionX = if (message.isFromMe) 1.0f else 0.0f,
                            pivotFractionY = 1.0f
                        )
                    )
                    .widthIn(max = 280.dp)
                    .border(
                        width = 1.dp,
                        color = if (message.isFromMe) Color.Transparent else Color(0xFFFFE4E8),
                        shape = bubbleShape
                    )
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.linearGradient(
                            colors = if (message.isFromMe) {
                                listOf(RoseAccent, RoseDeep)
                            } else {
                                listOf(Color.White, Color(0xFFFFF9FA))
                            }
                        ),
                        shape = bubbleShape
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                isPressed.value = true
                                try {
                                    awaitRelease()
                                } finally {
                                    isPressed.value = false
                                }
                            },
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showContextMenu = true
                            },
                            onTap = {
                                if (message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE") {
                                    onMediaClick()
                                } else if (message.messageType == "RECORDED_KISS") {
                                    viewModel.playRecordedKiss(message.id)
                                }
                            }
                        )
                    }
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    // Quoted reply preview
                    if (message.quotedMsgId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (message.isFromMe) Color.White.copy(alpha = 0.15f) else CharcoalText.copy(alpha = 0.05f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = if (message.isFromMe) Color.White.copy(alpha = 0.7f) else CharcoalText.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.quotedMsgSender ?: "Partner",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = if (message.isFromMe) Color.White else CharcoalText
                                )
                                Text(
                                    text = message.quotedMsgText ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                    color = if (message.isFromMe) Color.White.copy(alpha = 0.8f) else CharcoalText.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // Message content body
                    val contentColor = if (message.isFromMe) Color.White else CharcoalText
                    MessageContent(
                        message = message,
                        searchQuery = searchQuery,
                        viewModel = viewModel,
                        onMediaClick = onMediaClick,
                        contentColor = contentColor
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Timestamp + delivery tick
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = java.text.SimpleDateFormat(
                                "hh:mm a",
                                java.util.Locale.getDefault()
                            ).format(java.util.Date(message.timestamp)),
                            fontSize = 9.sp,
                            color = if (message.isFromMe) Color.White.copy(alpha = 0.7f) else CharcoalText.copy(alpha = 0.5f)
                        )
                        if (message.isFromMe) {
                            Spacer(modifier = Modifier.width(3.dp))
                            val (tickIcon, tickTint) = when (message.deliveryStatus) {
                                "READ" -> Pair(Icons.Default.DoneAll, Color(0xFFFFF0F2))
                                "DELIVERED" -> Pair(Icons.Default.DoneAll, Color.White.copy(alpha = 0.8f))
                                else -> Pair(Icons.Default.Check, Color.White.copy(alpha = 0.5f))
                            }
                            Icon(
                                imageVector = tickIcon,
                                contentDescription = message.deliveryStatus,
                                tint = tickTint,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            // Emoji reaction badge
            if (message.reaction.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = if (message.isFromMe) (-8).dp else 8.dp,
                            y = 6.dp
                        )
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.dp, Color(0xFFFFE4E8), CircleShape)
                        .clickable {
                            viewModel.toggleReaction(message.id, message.reaction)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = message.reaction, fontSize = 11.sp)
                }
            }
        }

        // Context menu
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .background(Color.White.copy(alpha = 0.95f))
                .width(180.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("❤️", "👍", "😂", "😮", "😢", "🙏").forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 18.sp,
                        modifier = Modifier
                            .clickable {
                                viewModel.toggleReaction(message.id, emoji)
                                showContextMenu = false
                            }
                            .padding(4.dp)
                    )
                }
            }
            HorizontalDivider(color = CharcoalText.copy(alpha = 0.08f), thickness = 1.dp)
            DropdownMenuItem(
                text = { Text("Reply", color = CharcoalText) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = CharcoalText)
                },
                onClick = {
                    viewModel.setReplyToMessage(message)
                    showContextMenu = false
                }
            )
            if (message.messageType == "TEXT") {
                DropdownMenuItem(
                    text = { Text("Copy", color = CharcoalText) },
                    leadingIcon = {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CharcoalText)
                    },
                    onClick = {
                        clipboardManager.setText(AnnotatedString(message.text))
                        showContextMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = Color.Red) },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red)
                },
                onClick = {
                    showContextMenu = false
                    showDeleteConfirmation = true
                }
            )
        }

        // Delete confirmation dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = {
                    Text("Delete Message", fontFamily = PlayfairFont, fontWeight = FontWeight.Bold)
                },
                text = {
                    Text("Are you sure you want to delete this message? This action cannot be undone.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessage(message.id)
                            showDeleteConfirmation = false
                        }
                    ) {
                        Text("Delete", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel", color = CharcoalText)
                    }
                },
                containerColor = BlushBackground
            )
        }
    }
}

// ─── Per-message content renderer ────────────────────────────────────────────

@Composable
private fun MessageContent(
    message: ChatMessage,
    searchQuery: String,
    viewModel: ChatViewModel,
    onMediaClick: () -> Unit,
    contentColor: Color
) {
    when (message.messageType) {
        "RECORDED_KISS" -> KissMessageContent(message = message, viewModel = viewModel)
        "VOICE" -> VoiceMessageContent(message = message, viewModel = viewModel)
        "MEDIA", "MEDIA_IMAGE" -> ImageMessageContent(message = message, viewModel = viewModel, onMediaClick = onMediaClick)
        "MEDIA_VIDEO" -> VideoMessageContent(onMediaClick = onMediaClick)
        "MEDIA_AUDIO" -> AudioFileMessageContent(message = message, viewModel = viewModel)
        "MEDIA_FILE" -> FileMessageContent(onMediaClick = onMediaClick)
        else -> TextMessageContent(message = message, searchQuery = searchQuery, contentColor = contentColor)
    }
}

@Composable
private fun KissMessageContent(message: ChatMessage, viewModel: ChatViewModel) {
    val infiniteTransition = rememberInfiniteTransition(label = "kiss_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kiss_scale"
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF0F2))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable { viewModel.playRecordedKiss(message.id) }
    ) {
        Text(
            text = "IMPRESSION",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFE36B87),
            modifier = Modifier.scale(scale)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Tap to replay impression",
            fontFamily = PlayfairFont,
            fontSize = 12.sp,
            color = RoseAccent,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TextMessageContent(message: ChatMessage, searchQuery: String, contentColor: Color) {
    val text = message.text
    if (searchQuery.isNotEmpty() && text.contains(searchQuery, ignoreCase = true)) {
        val annotatedString = buildAnnotatedString {
            var startIndex = 0
            while (startIndex < text.length) {
                val index = text.indexOf(searchQuery, startIndex, ignoreCase = true)
                if (index == -1) {
                    append(text.substring(startIndex))
                    break
                }
                append(text.substring(startIndex, index))
                pushStyle(SpanStyle(background = Color.Yellow, color = CharcoalText))
                append(text.substring(index, index + searchQuery.length))
                pop()
                startIndex = index + searchQuery.length
            }
        }
        Text(
            text = annotatedString,
            fontFamily = InterFont,
            fontSize = 14.sp,
            color = contentColor,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    } else {
        Text(
            text = text,
            fontFamily = InterFont,
            fontSize = 14.sp,
            color = contentColor,
            modifier = Modifier.padding(bottom = 2.dp)
        )
    }
}

// ─── Lightbox fullscreen overlay ──────────────────────────────────────────────

@Composable
fun LightboxOverlay(message: ChatMessage, viewModel: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var bitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var decryptedBytesState by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val tempVideoFile = remember {
        java.io.File(context.cacheDir, "decrypted_video_${System.currentTimeMillis()}.mp4")
    }
    var isVideoPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        isLoading = true
        val bytes = viewModel.getMediaBytes(message.id)
        if (bytes != null) {
            decryptedBytesState = bytes
            if (message.messageType == "MEDIA_VIDEO") {
                try {
                    val fos = java.io.FileOutputStream(tempVideoFile)
                    fos.write(bytes)
                    fos.flush()
                    fos.close()
                    isVideoPrepared = true
                } catch (e: Exception) {
                    android.util.Log.e("Enclave", "Exception caught", e)
                }
            } else {
                bitmapState = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            decryptedBytesState?.fill(0)
            decryptedBytesState = null
            bitmapState = null
            if (tempVideoFile.exists()) {
                try {
                    val size = tempVideoFile.length()
                    val zeros = ByteArray(1024)
                    val fos = java.io.FileOutputStream(tempVideoFile)
                    var written = 0L
                    while (written < size) {
                        val toWrite = minOf(zeros.size.toLong(), size - written).toInt()
                        fos.write(zeros, 0, toWrite)
                        written += toWrite
                    }
                    fos.flush()
                    fos.close()
                } catch (_: Exception) {}
                tempVideoFile.delete()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
        } else {
            if (message.messageType == "MEDIA_VIDEO") {
                if (isVideoPrepared) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoPath(tempVideoFile.absolutePath)
                                val mc = android.widget.MediaController(ctx)
                                mc.setAnchorView(this)
                                setMediaController(mc)
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                } else {
                    Text("Failed to load video", color = Color.White)
                }
            } else if (message.messageType == "MEDIA_FILE") {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Document securely stored in Vault.", color = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Go to Vault to view or export it.", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                }
            } else {
                bitmapState?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Decrypted Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                } ?: Text("Failed to decrypt image", color = Color.White)
            }
        }
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
        }
    }
}

// ─── Attachment option icon ───────────────────────────────────────────────────

@Composable
fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontFamily = InterFont,
            color = CharcoalText,
            fontWeight = FontWeight.Medium
        )
    }
}
