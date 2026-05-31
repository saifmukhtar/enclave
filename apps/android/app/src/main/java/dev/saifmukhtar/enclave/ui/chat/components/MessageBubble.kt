@file:OptIn(ExperimentalMaterial3Api::class)
package dev.saifmukhtar.enclave.ui.chat.components

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
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import dev.saifmukhtar.enclave.ui.chat.ChatMessage
import dev.saifmukhtar.enclave.ui.chat.ChatViewModel
import dev.saifmukhtar.enclave.ui.theme.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val timeFormatter = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault())

// ─── Swipe-to-Reply Message Bubble ───────────────────────────────────────────

@Composable
fun SwipeToReplyMessageBubble(
    message: ChatMessage,
    viewModel: ChatViewModel,
    searchQuery: String = "",
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectedChange: (Boolean) -> Unit = {},
    // Consecutive-media grouping helpers
    isGroupedMedia: Boolean = false,
    isGroupStart: Boolean = false,
    isGroupEnd: Boolean = false,
    onMediaClick: () -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

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

    // Bubble corner radii — square where the group joins, rounded at group boundaries
    val cornerFull = 24.dp
    val cornerSquare = 4.dp
    val bubbleShape = if (isGroupedMedia) {
        if (message.isFromMe) {
            RoundedCornerShape(
                topStart = cornerFull,
                topEnd = if (isGroupStart) cornerFull else cornerSquare,
                bottomStart = cornerFull,
                bottomEnd = if (isGroupEnd) cornerFull else cornerSquare
            )
        } else {
            RoundedCornerShape(
                topStart = if (isGroupStart) cornerFull else cornerSquare,
                topEnd = cornerFull,
                bottomStart = if (isGroupEnd) cornerFull else cornerSquare,
                bottomEnd = cornerFull
            )
        }
    } else {
        RoundedCornerShape(
            topStart = cornerFull,
            topEnd = cornerFull,
            bottomStart = if (message.isFromMe) cornerFull else 4.dp,
            bottomEnd = if (message.isFromMe) 4.dp else cornerFull
        )
    }

    // Media messages (image/video with no reply quote) bleed edge-to-edge in the bubble
    val isFullBleedMedia = message.quotedMsgId == null &&
        (message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE" || message.messageType == "MEDIA_VIDEO")

    var offsetX by remember { mutableStateOf(0f) }
    val replyOffsetAnim by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reply_offset"
    )

    val currentDensity = androidx.compose.ui.platform.LocalDensity.current
    val customDensity = remember(currentDensity) {
        androidx.compose.ui.unit.Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale.coerceAtMost(1.15f)
        )
    }

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides customDensity
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isSelected) Color(0xFFE598A7).copy(alpha = 0.2f) else Color.Transparent)
                .pointerInput(isSelectionMode) {
                    if (!isSelectionMode) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val triggered = if (message.isFromMe) {
                                    offsetX < -120f
                                } else {
                                    offsetX > 120f
                                }
                                if (triggered) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setReplyToMessage(message)
                                }
                                offsetX = 0f
                            },
                            onDragCancel = {
                                offsetX = 0f
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                offsetX = if (message.isFromMe) {
                                    (offsetX + dragAmount).coerceIn(-180f, 0f)
                                } else {
                                    (offsetX + dragAmount).coerceIn(0f, 180f)
                                }
                            }
                        )
                    }
                }
                .pointerInput(isSelectionMode) {
                    if (!isSelectionMode) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showContextMenu = true
                            }
                        )
                    }
                },
            contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            val swipeProgress = if (message.isFromMe) {
                (replyOffsetAnim / -180f).coerceIn(0f, 1f)
            } else {
                (replyOffsetAnim / 180f).coerceIn(0f, 1f)
            }

            if (swipeProgress > 0.05f) {
                Box(
                    modifier = Modifier
                        .align(if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 8.dp)
                        .graphicsLayer {
                            alpha = swipeProgress
                            scaleX = 0.5f + (swipeProgress * 0.5f)
                            scaleY = 0.5f + (swipeProgress * 0.5f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(RoseAccent.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Reply,
                            contentDescription = "Reply",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier.offset { IntOffset(replyOffsetAnim.roundToInt(), 0) },
                contentAlignment = if (message.isFromMe) Alignment.BottomStart else Alignment.BottomEnd
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scaleFactor
                            scaleY = scaleFactor
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                pivotFractionX = if (message.isFromMe) 1.0f else 0.0f,
                                pivotFractionY = 1.0f
                            )
                        }
                        .widthIn(max = 280.dp)
                        .clip(bubbleShape)
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
                        .pointerInput(isSelectionMode) {
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
                                    if (isSelectionMode) {
                                        onSelectedChange(!isSelected)
                                    } else {
                                        showContextMenu = true
                                    }
                                },
                                onTap = {
                                    if (isSelectionMode) {
                                        onSelectedChange(!isSelected)
                                    } else {
                                        if (message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE") {
                                            onMediaClick()
                                        } else if (message.messageType == "RECORDED_KISS") {
                                            viewModel.playRecordedKiss(message.id)
                                        }
                                    }
                                }
                            )
                        }
                        .padding(
                            start = if (isFullBleedMedia) 0.dp else 12.dp,
                            end = if (isFullBleedMedia) 0.dp else 12.dp,
                            top = if (isFullBleedMedia) 0.dp else 8.dp,
                            bottom = if (isFullBleedMedia) 0.dp else 8.dp
                        )
                ) {
                    val contentColor = if (message.isFromMe) Color.White else CharcoalText
                    Box {
                        Column(horizontalAlignment = Alignment.End) {
                            // Quoted reply preview (only possible when !isFullBleedMedia by definition)
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
                                        val myProfile by viewModel.myProfile.collectAsState()
                                        val partnerProfile by viewModel.partnerProfile.collectAsState()
                                        val partnerUsername = partnerProfile?.username?.ifBlank { null } ?: "Partner"
                                        val myUsername = myProfile?.username?.ifBlank { null } ?: "Me"
                                        val resolvedQuotedSender = when (message.quotedMsgSender) {
                                            "You", "Me" -> myUsername
                                            "Partner" -> partnerUsername
                                            else -> message.quotedMsgSender ?: partnerUsername
                                        }
                                        Text(
                                            text = resolvedQuotedSender,
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
                            MessageContent(
                                message = message,
                                searchQuery = searchQuery,
                                viewModel = viewModel,
                                isSelectionMode = isSelectionMode,
                                onMediaClick = onMediaClick,
                                contentColor = contentColor
                            )

                            // For non-media messages: timestamp sits below content
                            if (!isFullBleedMedia) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = timeFormatter.format(java.util.Date(message.timestamp)),
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

                        // For full-bleed media: timestamp is overlaid at bottom-right with shadow
                        if (isFullBleedMedia) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 9.dp, bottom = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.End
                            ) {
                                Text(
                                    text = timeFormatter.format(java.util.Date(message.timestamp)),
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.75f),
                                            blurRadius = 6f
                                        )
                                    )
                                )
                                if (message.isFromMe) {
                                    Spacer(modifier = Modifier.width(3.dp))
                                    val (tickIcon, tickTint) = when (message.deliveryStatus) {
                                        "READ" -> Pair(Icons.Default.DoneAll, Color.White)
                                        "DELIVERED" -> Pair(Icons.Default.DoneAll, Color.White.copy(alpha = 0.85f))
                                        else -> Pair(Icons.Default.Check, Color.White.copy(alpha = 0.65f))
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
                val isSavableMedia = message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE" || message.messageType == "MEDIA_VIDEO"
                if (isSavableMedia) {
                    val coroutineScope = rememberCoroutineScope()
                    DropdownMenuItem(
                        text = { Text("Save to Vault", color = CharcoalText) },
                        leadingIcon = {
                            Icon(Icons.Default.Save, contentDescription = null, tint = CharcoalText)
                        },
                        onClick = {
                            showContextMenu = false
                            coroutineScope.launch {
                                val bytes = viewModel.getMediaBytes(message.id)
                                if (bytes != null) {
                                    val mime = if (message.messageType == "MEDIA_VIDEO") "video/mp4" else "image/jpeg"
                                    var thumb: ByteArray? = null
                                    if (message.messageType == "MEDIA_VIDEO") {
                                        thumb = viewModel.generateVideoThumbnail(context, bytes)
                                    }
                                    val ok = viewModel.saveToVault("chat_${message.id}", mime, bytes, thumb)
                                    if (ok) {
                                        android.widget.Toast.makeText(context, "Saved to Vault successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "Failed to save to Vault", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to load decrypted media bytes", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Select", color = CharcoalText) },
                    leadingIcon = {
                        Icon(Icons.Default.Check, contentDescription = null, tint = CharcoalText)
                    },
                    onClick = {
                        showContextMenu = false
                        onSelectedChange(true)
                    }
                )
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
                        Text("Are you sure you want to delete this message?")
                    },
                    confirmButton = {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (message.isFromMe) {
                                TextButton(
                                    onClick = {
                                        viewModel.deleteMessage(message.id, forEveryone = true)
                                        showDeleteConfirmation = false
                                    }
                                ) {
                                    Text("Delete for Everyone", color = Color.Red, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = {
                                    viewModel.deleteMessage(message.id, forEveryone = false)
                                    showDeleteConfirmation = false
                                }
                            ) {
                                Text("Delete for Me", color = Color.Red)
                            }
                            TextButton(onClick = { showDeleteConfirmation = false }) {
                                Text("Cancel", color = CharcoalText)
                            }
                        }
                    },
                    dismissButton = null,
                    containerColor = BlushBackground
                )
            }
        }
    }
}

// ─── Per-message content renderer ────────────────────────────────────────────

@Composable
private fun MessageContent(
    message: ChatMessage,
    searchQuery: String,
    viewModel: ChatViewModel,
    isSelectionMode: Boolean,
    onMediaClick: () -> Unit,
    contentColor: Color
) {
    when (message.messageType) {
        "RECORDED_KISS" -> KissMessageContent(message = message, viewModel = viewModel)
        "VOICE" -> VoiceMessageContent(message = message, viewModel = viewModel)
        "MEDIA", "MEDIA_IMAGE" -> ImageMessageContent(message = message, viewModel = viewModel, isSelectionMode = isSelectionMode, onMediaClick = onMediaClick)
        "MEDIA_VIDEO" -> VideoMessageContent(message = message, viewModel = viewModel, isSelectionMode = isSelectionMode, onMediaClick = onMediaClick)
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

    // Pinch-to-zoom state
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

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
                        modifier = Modifier.fillMaxSize()
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
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                    if (scale > 1f) {
                                        offsetX += pan.x
                                        offsetY += pan.y
                                    } else {
                                        offsetX = 0f
                                        offsetY = 0f
                                    }
                                }
                            }
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            )
                    )
                } ?: Text("Failed to decrypt image", color = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 40.dp, end = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isSavableMedia = message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE" || message.messageType == "MEDIA_VIDEO"
            if (isSavableMedia) {
                var isSavingToVault by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                IconButton(
                    onClick = {
                        if (isSavingToVault) return@IconButton
                        isSavingToVault = true
                        scope.launch {
                            val bytes = viewModel.getMediaBytes(message.id)
                            if (bytes != null) {
                                val mime = if (message.messageType == "MEDIA_VIDEO") "video/mp4" else "image/jpeg"
                                var thumb: ByteArray? = null
                                if (message.messageType == "MEDIA_VIDEO") {
                                    thumb = viewModel.generateVideoThumbnail(context, bytes)
                                }
                                val ok = viewModel.saveToVault("chat_${message.id}", mime, bytes, thumb)
                                if (ok) {
                                    android.widget.Toast.makeText(context, "Saved to Vault successfully!", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "Failed to save to Vault", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(context, "Failed to load decrypted media bytes", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            isSavingToVault = false
                        }
                    },
                    enabled = !isSavingToVault
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save to Vault",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onClose
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
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
