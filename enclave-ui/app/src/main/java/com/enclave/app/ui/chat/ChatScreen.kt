@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.chat

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import android.graphics.Shader
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import com.enclave.app.ui.kiss.physics.LipPhysicsEngine
import com.enclave.app.ui.kiss.haptics.KissHapticManager
import com.enclave.app.ui.kiss.audio.KissAudioSynthesizer

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale as modifierScale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.media.MusicSyncController
import com.enclave.app.media.MemoryMediaDataSource
import com.enclave.app.ui.kiss.components.InteractiveCanvas
import com.enclave.app.models.RecordedKissPayload
import com.enclave.app.models.RecordedKissPoint
import android.os.VibrationEffect
import android.content.Context
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInParent
import kotlin.math.roundToInt


// Soft Blush Palette
val BlushBackground = ComposeColor(0xFFFFF5F6)
val BlushSent = ComposeColor(0xFFFCE2E6)
val BlushReceived = ComposeColor(0xFFE8D4D6)
val CharcoalText = ComposeColor(0xFF2A1B1D)

val OutfitFont = FontFamily.Serif
val InterFont = FontFamily.Default

@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    musicSyncController: MusicSyncController?,
    kissViewModel: com.enclave.app.ui.kiss.KissViewModel,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel? = null,
    loungeViewModel: com.enclave.app.ui.lounge.LoungeViewModel? = null,
    signalingClient: com.enclave.app.webrtc.SignalingClient,
    autoShowKissCanvas: Boolean = false,
    onKissCanvasClosed: () -> Unit = {},
    onAudioCallClick: () -> Unit = {},
    onVideoCallClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val isPartnerTyping by viewModel.partnerTyping.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val partnerProfile by profileViewModel?.partnerProfile?.collectAsState() ?: remember { mutableStateOf(null) }
    val partnerStatus by loungeViewModel?.partnerStatus?.collectAsState() ?: remember { mutableStateOf(null) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }


    // Compute partner display name and last-seen text
    val partnerName = partnerProfile?.displayName?.ifBlank { partnerProfile?.username }?.ifBlank { "Partner" } ?: "Partner"
    val partnerInitials = partnerName.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
    val isPartnerOnline = partnerProfile?.isOnline == true
    val lastSeenText = if (!isPartnerOnline && (partnerProfile?.lastSeen ?: 0L) > 0L) {
        val diffMs = System.currentTimeMillis() - (partnerProfile?.lastSeen ?: 0L)
        when {
            diffMs < 60_000 -> "Last seen just now"
            diffMs < 3_600_000 -> "Last seen ${diffMs / 60_000}m ago"
            diffMs < 86_400_000 -> "Last seen ${diffMs / 3_600_000}h ago"
            else -> "Last seen ${diffMs / 86_400_000}d ago"
        }
    } else ""

    var showAttachmentSheet by remember { mutableStateOf(false) }
    val attachmentSheetState = rememberModalBottomSheetState()
    
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                viewModel.sendMediaMessage(bytes, mimeType)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            val stream = java.io.ByteArrayOutputStream()
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            val bytes = stream.toByteArray()
            viewModel.sendMediaMessage(bytes, "image/jpeg")
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "*/*"
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                viewModel.sendMediaMessage(bytes, mimeType)
            }
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val mimeType = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val inputStream = context.contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null) {
                viewModel.sendMediaMessage(bytes, mimeType)
            }
        }
    }

    var showKissCanvas by remember { mutableStateOf(false) }
    LaunchedEffect(autoShowKissCanvas) {
        if (autoShowKissCanvas) {
            showKissCanvas = true
        }
    }
    val activePlaybackKiss by viewModel.activePlaybackKiss.collectAsState()
    var lightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }

    androidx.activity.compose.BackHandler(enabled = showKissCanvas || lightboxMessage != null || showAttachmentSheet || activePlaybackKiss != null || isSearchActive) {
        if (showKissCanvas) {
            showKissCanvas = false
        } else if (lightboxMessage != null) {
            lightboxMessage = null
        } else if (showAttachmentSheet) {
            showAttachmentSheet = false
        } else if (activePlaybackKiss != null) {
            viewModel.clearPlaybackKiss()
        } else if (isSearchActive) {
            isSearchActive = false
            viewModel.updateSearchQuery("")
        }
    }


    // Microphone Permission Launcher
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startAudioRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to record voice notes", Toast.LENGTH_SHORT).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = BlushBackground,
            topBar = {
                GlassmorphicTopBar(
                    uiState = uiState,
                    partnerName = partnerName,
                    partnerInitials = partnerInitials.ifBlank { "P" },
                    isPartnerOnline = isPartnerOnline,
                    lastSeenText = lastSeenText,
                    partnerAvatarUrl = partnerProfile?.avatarUrl,
                    profileViewModel = profileViewModel,
                    partnerDisplayName = partnerProfile?.displayName,
                    partnerUsername = partnerProfile?.username,
                    partnerBio = partnerProfile?.bio,
                    partnerStatusText = partnerStatus?.statusText,
                    onAudioCallClick = onAudioCallClick,
                    onVideoCallClick = onVideoCallClick,
                    onKissClick = { showKissCanvas = !showKissCanvas },
                    onProfileClick = onProfileClick,
                    isSearchActive = isSearchActive,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onToggleSearch = { active ->
                        isSearchActive = active
                        if (!active) {
                            viewModel.updateSearchQuery("")
                        }
                    }
                )
            },
            bottomBar = {
                ChatInputBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onAttachClick = {
                        showAttachmentSheet = true
                    },
                    onRecordVoiceClick = {
                        val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (hasAudio) {
                            viewModel.startAudioRecording()
                        } else {
                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                CoListeningLounge(musicSyncController)

                val displayMessages = if (isSearchActive && searchQuery.isNotEmpty()) {
                    searchResults
                } else {
                    messages
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    reverseLayout = true
                ) {
                    if (isPartnerTyping && !isSearchActive) {
                        item {
                            BouncingTypingIndicator()
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                    items(displayMessages.reversed()) { message ->
                        SwipeToReplyMessageBubble(
                            message = message,
                            viewModel = viewModel,
                            searchQuery = if (isSearchActive) searchQuery else "",
                            onMediaClick = {
                                if (message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE" || message.messageType == "MEDIA_VIDEO") {
                                    lightboxMessage = message
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        if (showKissCanvas) {
            KissGestureCanvasOverlay(
                viewModel = kissViewModel,
                signalingClient = signalingClient,
                onClose = { 
                    showKissCanvas = false
                    onKissCanvasClosed()
                },
                onSendRecordedKiss = { payload ->
                    viewModel.sendRecordedKiss(payload)
                    showKissCanvas = false
                    onKissCanvasClosed()
                }
            )
        }

        activePlaybackKiss?.let { payload ->
            RecordedKissPlaybackOverlay(
                payload = payload,
                onClose = { viewModel.clearPlaybackKiss() }
            )
        }

        lightboxMessage?.let { msg ->
            LightboxOverlay(
                message = msg,
                viewModel = viewModel,
                onClose = { lightboxMessage = null }
            )
        }
    }

    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = attachmentSheetState,
            containerColor = BlushBackground
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Share Content",
                    fontFamily = OutfitFont,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CharcoalText,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachmentOptionItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        color = ComposeColor(0xFFE598A7),
                        onClick = {
                            showAttachmentSheet = false
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            takePictureLauncher.launch(null)
                        }
                    )
                    AttachmentOptionItem(
                        icon = Icons.Default.Image,
                        label = "Gallery",
                        color = ComposeColor(0xFF88B04B),
                        onClick = {
                            showAttachmentSheet = false
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                        }
                    )
                    AttachmentOptionItem(
                        icon = Icons.Default.AttachFile,
                        label = "File",
                        color = ComposeColor(0xFF5B5EA6),
                        onClick = {
                            showAttachmentSheet = false
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            pickFileLauncher.launch("*/*")
                        }
                    )
                    AttachmentOptionItem(
                        icon = Icons.Default.MusicNote,
                        label = "Audio",
                        color = ComposeColor(0xFFEFC050),
                        onClick = {
                            showAttachmentSheet = false
                            com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                            pickAudioLauncher.launch("audio/*")
                        }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}


@Composable
fun GlassmorphicTopBar(
    uiState: ChatUiState,
    partnerName: String = "Partner",
    partnerInitials: String = "P",
    isPartnerOnline: Boolean = false,
    lastSeenText: String = "",
    partnerAvatarUrl: String? = null,
    profileViewModel: com.enclave.app.ui.profile.ProfileViewModel? = null,
    partnerDisplayName: String? = null,
    partnerUsername: String? = null,
    partnerBio: String? = null,
    partnerStatusText: String? = null,
    onAudioCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onKissClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onToggleSearch: (Boolean) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .background(ComposeColor.White.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        if (isSearchActive) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onToggleSearch(false) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Cancel Search",
                        tint = CharcoalText
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            text = "Search messages...",
                            fontFamily = InterFont,
                            color = CharcoalText.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = ComposeColor.Transparent,
                        unfocusedContainerColor = ComposeColor.Transparent,
                        disabledContainerColor = ComposeColor.Transparent,
                        focusedIndicatorColor = ComposeColor.Transparent,
                        unfocusedIndicatorColor = ComposeColor.Transparent
                    ),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = InterFont,
                        fontSize = 14.sp,
                        color = CharcoalText
                    )
                )
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = CharcoalText
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Avatar + name + online status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onProfileClick() }
                ) {
                    // Avatar circle with initials or decrypted E2EE avatar
                    com.enclave.app.ui.profile.E2eeAvatar(
                        avatarBase64 = partnerAvatarUrl,
                        isMe = false,
                        profileViewModel = profileViewModel,
                        initials = partnerInitials.ifBlank { "P" },
                        modifier = Modifier.size(44.dp),
                        displayName = partnerDisplayName ?: partnerName,
                        username = partnerUsername,
                        bio = partnerBio,
                        statusText = partnerStatusText,
                        enablePreview = true
                    )

                    // Online indicator dot
                    Box(
                        modifier = Modifier
                            .offset(x = (-12).dp, y = 14.dp)
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(ComposeColor.White)
                            .padding(2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(
                                    if (isPartnerOnline) ComposeColor(0xFF4CAF50)
                                    else ComposeColor(0xFFBDBDBD)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text(
                            text = partnerName,
                            fontFamily = OutfitFont,
                            fontWeight = FontWeight.Bold,
                            color = CharcoalText,
                            fontSize = 16.sp
                        )
                        Text(
                            text = when {
                                uiState is ChatUiState.Secured && isPartnerOnline -> "Online · E2EE"
                                uiState is ChatUiState.Secured && lastSeenText.isNotBlank() -> lastSeenText
                                uiState is ChatUiState.Secured -> "Signal-Grade E2EE Active"
                                uiState is ChatUiState.Connecting -> "Connecting securely..."
                                uiState is ChatUiState.Handshaking -> "Exchanging PreKeys..."
                                uiState is ChatUiState.WaitingForPartner -> "Waiting for partner..."
                                else -> "Connection failed"
                            },
                            fontFamily = InterFont,
                            color = if (uiState is ChatUiState.Secured && isPartnerOnline)
                                ComposeColor(0xFF4CAF50) else CharcoalText.copy(alpha = 0.6f),
                            fontSize = 11.sp
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onToggleSearch(true) }) {
                        Icon(Icons.Default.Search, contentDescription = "Search Messages", tint = CharcoalText)
                    }
                    IconButton(onClick = onKissClick) {
                        Icon(Icons.Default.Favorite, contentDescription = "Kiss", tint = ComposeColor(0xFFE598A7))
                    }
                    // Separate Audio Call button (Signal-style 📞)
                    IconButton(onClick = onAudioCallClick) {
                        Icon(Icons.Default.Call, contentDescription = "Audio Call", tint = CharcoalText)
                    }
                    // Separate Video Call button 📹
                    IconButton(onClick = onVideoCallClick) {
                        Icon(Icons.Default.Videocam, contentDescription = "Video Call", tint = CharcoalText)
                    }
                }
            }
        }
    }
}


@Composable
fun SwipeToReplyMessageBubble(
    message: ChatMessage,
    viewModel: ChatViewModel,
    searchQuery: String = "",
    onMediaClick: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    // Read Receipt trigger when layout draws
    LaunchedEffect(Unit) {
        if (!message.isFromMe && message.deliveryStatus != "READ") {
            viewModel.markMessageAsRead(message.id)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    val newOffset = offsetX + delta
                    // Swipe right = reply, positive offset only
                    if (newOffset >= 0f && newOffset < 150f) {
                        offsetX = newOffset
                    }
                },
                onDragStopped = {
                    if (offsetX > 80f) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.setReplyToMessage(message)
                    }
                    offsetX = 0f
                }
            ),
        contentAlignment = if (message.isFromMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            contentAlignment = if (message.isFromMe) Alignment.BottomStart else Alignment.BottomEnd
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .background(
                        color = if (message.isFromMe) BlushSent else BlushReceived,
                        shape = RoundedCornerShape(
                            topStart = 20.dp,
                            topEnd = 20.dp,
                            bottomStart = if (message.isFromMe) 20.dp else 4.dp,
                            bottomEnd = if (message.isFromMe) 4.dp else 20.dp
                        )
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showContextMenu = true
                            },
                            onTap = {
                                if (message.messageType == "MEDIA") {
                                    onMediaClick()
                                } else if (message.messageType == "RECORDED_KISS") {
                                    viewModel.playRecordedKiss(message.id)
                                }
                            }
                        )
                    }
                    .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Column(horizontalAlignment = Alignment.End) {
                    if (message.quotedMsgId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CharcoalText.copy(alpha = 0.05f))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = CharcoalText.copy(alpha = 0.5f),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.quotedMsgSender ?: "Partner",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = CharcoalText
                                )
                                Text(
                                    text = message.quotedMsgText ?: "",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 10.sp,
                                    color = CharcoalText.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    if (message.messageType == "RECORDED_KISS") {
                        val infiniteTransition = rememberInfiniteTransition()
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 0.95f,
                            targetValue = 1.05f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1200, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            )
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(ComposeColor(0xFFFFF0F2))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clickable { viewModel.playRecordedKiss(message.id) }
                        ) {
                            Text(
                                text = "IMPRESSION",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = ComposeColor(0xFFE36B87),
                                modifier = Modifier.modifierScale(scale)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Tap to replay impression",
                                fontFamily = OutfitFont,
                                fontSize = 12.sp,
                                color = ComposeColor(0xFFE598A7),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (message.messageType == "VOICE") {
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
                                modifier = Modifier.size(32.dp).background(CharcoalText.copy(alpha = 0.08f), CircleShape)
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
                                            .background(if (isPlaying) ComposeColor(0xFFE598A7) else CharcoalText.copy(alpha = 0.3f))
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
                    } else if (message.messageType == "MEDIA" || message.messageType == "MEDIA_IMAGE") {
                        var bitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                        var isLoading by remember { mutableStateOf(true) }
                        
                        LaunchedEffect(message.id) {
                            val bytes = viewModel.getMediaBytes(message.id)
                            if (bytes != null) {
                                bitmapState = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            }
                            isLoading = false
                        }
                        
                        DisposableEffect(message.id) {
                            onDispose {
                                bitmapState = null
                            }
                        }
                        
                        if (isLoading) {
                            Box(
                                modifier = Modifier
                                    .size(120.dp)
                                    .background(CharcoalText.copy(alpha = 0.05f), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    color = CharcoalText.copy(alpha = 0.3f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        } else {
                            bitmapState?.let { bmp ->
                                androidx.compose.foundation.Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Decrypted Image Preview",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onMediaClick() }
                                )
                            } ?: Text("Failed to decrypt image", color = CharcoalText, fontSize = 13.sp)
                        }
                    } else if (message.messageType == "MEDIA_VIDEO") {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(CharcoalText.copy(alpha = 0.1f))
                                .clickable { onMediaClick() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayCircleFilled,
                                contentDescription = "Play Video",
                                tint = ComposeColor(0xFFE598A7),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Play Video",
                                color = CharcoalText.copy(alpha = 0.7f),
                                fontSize = 11.sp,
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                            )
                        }
                    } else if (message.messageType == "MEDIA_AUDIO") {
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
                                modifier = Modifier.size(36.dp).background(ComposeColor(0xFFE598A7), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play audio file",
                                    tint = ComposeColor.White,
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
                    } else {
                        val text = message.text
                        if (searchQuery.isNotEmpty() && text.contains(searchQuery, ignoreCase = true)) {
                            val annotatedString = androidx.compose.ui.text.buildAnnotatedString {
                                var startIndex = 0
                                while (startIndex < text.length) {
                                    val index = text.indexOf(searchQuery, startIndex, ignoreCase = true)
                                    if (index == -1) {
                                        append(text.substring(startIndex))
                                        break
                                    }
                                    append(text.substring(startIndex, index))
                                    pushStyle(
                                        androidx.compose.ui.text.SpanStyle(
                                            background = androidx.compose.ui.graphics.Color.Yellow,
                                            color = CharcoalText
                                        )
                                    )
                                    append(text.substring(index, index + searchQuery.length))
                                    pop()
                                    startIndex = index + searchQuery.length
                                }
                            }
                            Text(
                                text = annotatedString,
                                fontFamily = InterFont,
                                fontSize = 14.sp,
                                color = CharcoalText,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        } else {
                            Text(
                                text = text,
                                fontFamily = InterFont,
                                fontSize = 14.sp,
                                color = CharcoalText,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(message.timestamp)),
                            fontSize = 9.sp,
                            color = CharcoalText.copy(alpha = 0.5f)
                        )
                        if (message.isFromMe) {
                            Spacer(modifier = Modifier.width(3.dp))
                            val (tickIcon, tickTint) = when (message.deliveryStatus) {
                                "READ" -> Pair(Icons.Default.DoneAll, ComposeColor(0xFFE36B87)) // Intimate premium blush-rose/berry read status
                                "DELIVERED" -> Pair(Icons.Default.DoneAll, CharcoalText.copy(alpha = 0.55f)) // Double check for delivered status
                                else -> Pair(Icons.Default.Check, CharcoalText.copy(alpha = 0.35f)) // Single check for sent status
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

            if (message.reaction.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = if (message.isFromMe) (-8).dp else 8.dp,
                            y = 6.dp
                        )
                        .clip(CircleShape)
                        .background(ComposeColor.White)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clickable {
                            viewModel.toggleReaction(message.id, message.reaction)
                        }
                ) {
                    Text(text = message.reaction, fontSize = 11.sp)
                }
            }
        }

        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier
                .background(ComposeColor.White.copy(alpha = 0.95f))
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
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = CharcoalText) },
                onClick = {
                    viewModel.setReplyToMessage(message)
                    showContextMenu = false
                }
            )
            if (message.messageType == "TEXT") {
                DropdownMenuItem(
                    text = { Text("Copy", color = CharcoalText) },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = CharcoalText) },
                    onClick = {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(message.text))
                        showContextMenu = false
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Delete", color = ComposeColor.Red) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = ComposeColor.Red) },
                onClick = {
                    showContextMenu = false
                    showDeleteConfirmation = true
                }
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete Message", fontFamily = OutfitFont, fontWeight = FontWeight.Bold) },
                text = { Text("Are you sure you want to delete this message? This action cannot be undone.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteMessage(message.id)
                            showDeleteConfirmation = false
                        }
                    ) {
                        Text("Delete", color = ComposeColor.Red)
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
            .padding(16.dp)
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Elegant Quoted Reply Preview block sitting directly above Chat input rows
            if (replyTo != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor.White.copy(alpha = 0.8f))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Reply,
                        contentDescription = "Reply",
                        tint = ComposeColor(0xFFE598A7),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (replyTo!!.isFromMe) "You" else "Partner",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = ComposeColor(0xFFE598A7)
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
                            tint = ComposeColor.Gray,
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
                    val infiniteTransition = rememberInfiniteTransition()
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        )
                    )

                    IconButton(
                        onClick = { viewModel.cancelAudioRecording() },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = BlushSent,
                            contentColor = CharcoalText
                        ),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Cancel Recording",
                            tint = ComposeColor(0xFFE598A7)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(ComposeColor.White.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .alpha(alpha)
                                .background(ComposeColor.Red, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        val mins = recordingDuration / 60
                        val secs = recordingDuration % 60
                        Text(
                            text = "%02d:%02d".format(mins, secs),
                            fontFamily = OutfitFont,
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
                                        .background(ComposeColor(0xFFE598A7), CircleShape)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    FloatingActionButton(
                        onClick = { viewModel.stopAudioRecording() },
                        containerColor = ComposeColor(0xFFE598A7),
                        contentColor = BlushBackground,
                        shape = CircleShape
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send Voice")
                    }
                } else {
                    IconButton(
                        onClick = {
                            if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                Toast.makeText(context, "Cannot send attachments: Connection not secured.", Toast.LENGTH_SHORT).show()
                            } else {
                                onAttachClick()
                            }
                        },
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
                            onClick = { showTimerMenu = true },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (disappearingMode > 0) ComposeColor(0xFFE598A7).copy(alpha = 0.2f) else BlushSent,
                                contentColor = if (disappearingMode > 0) ComposeColor(0xFFE598A7) else CharcoalText
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
                            onDismissRequest = { showTimerMenu = false },
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
                                        viewModel.setDisappearingMode(secs)
                                        showTimerMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Keyboard Privacy: force Gboard/Swiftkey Incognito mode
                    TextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Secure message...", fontFamily = InterFont, color = CharcoalText.copy(alpha = 0.5f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ComposeColor.White.copy(alpha = 0.6f),
                            unfocusedContainerColor = ComposeColor.White.copy(alpha = 0.6f),
                            focusedIndicatorColor = ComposeColor.Transparent,
                            unfocusedIndicatorColor = ComposeColor.Transparent,
                            focusedTextColor = CharcoalText,
                            unfocusedTextColor = CharcoalText
                        ),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Send,
                            keyboardType = KeyboardType.Password
                        ),
                        visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (text.isBlank()) {
                        IconButton(
                            onClick = {
                                if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                    Toast.makeText(context, "Cannot record voice: Connection not secured.", Toast.LENGTH_SHORT).show()
                                } else {
                                    onRecordVoiceClick()
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = BlushSent,
                                contentColor = ComposeColor(0xFFE598A7)
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Record Audio"
                            )
                        }
                    } else {
                        FloatingActionButton(
                            onClick = { 
                                if (text.isNotBlank()) {
                                    if (uiState !is ChatUiState.Secured && uiState !is ChatUiState.WaitingForPartner) {
                                        Toast.makeText(context, "Cannot send message: Connection not secured.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        viewModel.sendMessage(text)
                                        text = ""
                                    }
                                }
                            },
                            containerColor = CharcoalText,
                            contentColor = BlushBackground,
                            shape = CircleShape
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BouncingTypingIndicator() {
    Row(
        modifier = Modifier.padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { index ->
            val infiniteTransition = rememberInfiniteTransition()
            val bounce by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -8f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, delayMillis = index * 100, easing = LinearOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .offset(y = bounce.dp)
                    .background(ComposeColor(0xFFE598A7), CircleShape)
            )
        }
    }
}

@Composable
fun KissGestureCanvasOverlay(
    viewModel: com.enclave.app.ui.kiss.KissViewModel,
    signalingClient: com.enclave.app.webrtc.SignalingClient,
    onClose: () -> Unit,
    onSendRecordedKiss: (String) -> Unit = {}
) {
    val localPayload by viewModel.localPayload.collectAsState()
    val remotePayload by viewModel.remotePayload.collectAsState()
    val sentImpression by viewModel.sentImpression.collectAsState()
    val receivedImpression by viewModel.receivedImpression.collectAsState()

    val context = LocalContext.current
    val localActive = localPayload?.isTouching == true
    val remoteActive = remotePayload?.isTouching == true
    val isMutual = localActive && remoteActive

    val coroutineScope = rememberCoroutineScope()
    var audioStreamer by remember { mutableStateOf<com.enclave.app.ui.kiss.audio.IntimateAudioStreamer?>(null) }

    DisposableEffect(signalingClient) {
        val streamer = com.enclave.app.ui.kiss.audio.IntimateAudioStreamer(
            context = context,
            onSendSdp = { sdp ->
                coroutineScope.launch {
                    val type = if (sdp.type == org.webrtc.SessionDescription.Type.OFFER) "KISS_AUDIO_OFFER" else "KISS_AUDIO_ANSWER"
                    val wrap = com.enclave.app.webrtc.SignalMessageWrapper(
                        type = type,
                        senderId = viewModel.myId,
                        targetId = viewModel.partnerId,
                        payload = sdp.description
                    )
                    signalingClient.sendRawMessage(Json.encodeToString(wrap))
                }
            },
            onSendIceCandidate = { candidate ->
                coroutineScope.launch {
                    val payload = Json.encodeToString(
                        com.enclave.app.ui.kiss.audio.KissIceCandidatePayload(
                            sdpMid = candidate.sdpMid,
                            sdpMLineIndex = candidate.sdpMLineIndex,
                            sdp = candidate.sdp
                        )
                    )
                    val wrap = com.enclave.app.webrtc.SignalMessageWrapper(
                        type = "KISS_AUDIO_ICE_CANDIDATE",
                        senderId = viewModel.myId,
                        targetId = viewModel.partnerId,
                        payload = payload
                    )
                    signalingClient.sendRawMessage(Json.encodeToString(wrap))
                }
            }
        )
        
        audioStreamer = streamer
        
        // Start negotiation if caller
        val isCaller = viewModel.myId < viewModel.partnerId
        if (isCaller) {
            streamer.startNegotiation()
        }
        
        onDispose {
            streamer.close()
        }
    }

    LaunchedEffect(signalingClient, audioStreamer) {
        val streamer = audioStreamer ?: return@LaunchedEffect
        signalingClient.incomingRawMessages.collect { rawText ->
            try {
                val msg = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.webrtc.SignalMessageWrapper>(rawText)
                if (msg.senderId != viewModel.partnerId) return@collect
                
                when (msg.type) {
                    "KISS_AUDIO_OFFER" -> {
                        msg.payload?.let { sdpText ->
                            streamer.handleOffer(sdpText)
                        }
                    }
                    "KISS_AUDIO_ANSWER" -> {
                        msg.payload?.let { sdpText ->
                            streamer.handleAnswer(sdpText)
                        }
                    }
                    "KISS_AUDIO_ICE_CANDIDATE" -> {
                        msg.payload?.let { payloadText ->
                            val candidatePayload = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.ui.kiss.audio.KissIceCandidatePayload>(payloadText)
                            streamer.handleRemoteCandidate(
                                sdpMid = candidatePayload.sdpMid,
                                sdpMLineIndex = candidatePayload.sdpMLineIndex,
                                sdp = candidatePayload.sdp
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("KissGestureCanvasOverlay", "Error parsing intimate audio signal", e)
            }
        }
    }

    LaunchedEffect(localActive, audioStreamer) {
        val streamer = audioStreamer ?: return@LaunchedEffect
        if (localActive) {
            // Unmute mic, route to earpiece, and set full remote playout volume
            streamer.setMicrophoneMuted(false)
            streamer.routeToEarpiece()
            streamer.setRemotePlayoutVolume(1.0)
        } else {
            // Mute mic and heavily duck remote volume by -20dB (0.15) to break the feedback loop
            streamer.setMicrophoneMuted(true)
            streamer.setRemotePlayoutVolume(0.15)
        }
    }

    var isRecordingKiss by remember { mutableStateOf(false) }
    val recordedPoints = remember { mutableStateListOf<com.enclave.app.models.RecordedKissPoint>() }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }
    val rawMeshRes = com.enclave.app.R.raw.partner_lip_mesh
    val localEngine = remember { LipPhysicsEngine(context, rawMeshRes) }
    val remoteEngine = remember { LipPhysicsEngine(context, rawMeshRes) }
    val hapticManager = remember { KissHapticManager(context) }
    val audioSynthesizer = remember { KissAudioSynthesizer() }

    // LERP variables for micro-stutter suppression
    var localLerpPressure by remember { mutableStateOf(0f) }
    var localLerpTouchMajor by remember { mutableStateOf(0f) }
    var localLerpTouchMinor by remember { mutableStateOf(0f) }

    var remoteLerpPressure by remember { mutableStateOf(0f) }
    var remoteLerpTouchMajor by remember { mutableStateOf(0f) }
    var remoteLerpTouchMinor by remember { mutableStateOf(0f) }

    val lerpAlpha = 0.18f

    // Gravity state for physical device tilt sag
    val sensorManager = remember { context.getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    val gravitySensor = remember { sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY) }
    var gravityX by remember { mutableStateOf(0f) }
    var gravityY by remember { mutableStateOf(0f) }

    DisposableEffect(sensorManager, gravitySensor) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event != null) {
                    gravityX = -event.values[0]
                    gravityY = event.values[1]
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(Unit) {
        audioSynthesizer.start()
        onDispose {
            audioSynthesizer.stop()
        }
    }

    LaunchedEffect(localPayload, remotePayload, gravityX, gravityY) {
        while (true) {
            withFrameNanos {
                // 1. Local Engine Tick
                val local = localPayload
                if (local != null && local.isTouching) {
                    localLerpPressure += (local.pressure - localLerpPressure) * lerpAlpha
                    localLerpTouchMajor += (local.touchMajor - localLerpTouchMajor) * lerpAlpha
                    localLerpTouchMinor += (local.touchMinor - localLerpTouchMinor) * lerpAlpha

                    localEngine.updatePhysics(
                        activeTouchX = 0f,
                        activeTouchY = 0f,
                        touchMajor = localLerpTouchMajor,
                        touchMinor = localLerpTouchMinor,
                        orientationRad = 0f,
                        pressure = localLerpPressure,
                        canvasWidth = canvasSize.width.coerceAtLeast(1f),
                        gravityX = gravityX,
                        gravityY = gravityY
                    )
                    hapticManager.processEngineState(localEngine.engineKineticEnergy, localEngine.engineMeshStress)
                    audioSynthesizer.updateTelemetry(localEngine.engineKineticEnergy, localEngine.engineMeshStress)
                } else {
                    localLerpPressure += (0f - localLerpPressure) * lerpAlpha
                    localLerpTouchMajor += (0f - localLerpTouchMajor) * lerpAlpha
                    localLerpTouchMinor += (0f - localLerpTouchMinor) * lerpAlpha

                    localEngine.updatePhysics(null, null, 0f, 0f, 0f, 0f, canvasSize.width.coerceAtLeast(1f), gravityX, gravityY)
                    audioSynthesizer.updateTelemetry(localEngine.engineKineticEnergy, localEngine.engineMeshStress)
                }

                // 2. Remote Engine Tick
                val remote = remotePayload
                if (remote != null && remote.isTouching) {
                    remoteLerpPressure += (remote.pressure - remoteLerpPressure) * lerpAlpha
                    remoteLerpTouchMajor += (remote.touchMajor - remoteLerpTouchMajor) * lerpAlpha
                    remoteLerpTouchMinor += (remote.touchMinor - remoteLerpTouchMinor) * lerpAlpha

                    remoteEngine.updatePhysics(
                        activeTouchX = 0f,
                        activeTouchY = 0f,
                        touchMajor = remoteLerpTouchMajor,
                        touchMinor = remoteLerpTouchMinor,
                        orientationRad = 0f,
                        pressure = remoteLerpPressure,
                        canvasWidth = canvasSize.width.coerceAtLeast(1f),
                        gravityX = 0f,
                        gravityY = 0f
                    )
                } else {
                    remoteLerpPressure += (0f - remoteLerpPressure) * lerpAlpha
                    remoteLerpTouchMajor += (0f - remoteLerpTouchMajor) * lerpAlpha
                    remoteLerpTouchMinor += (0f - remoteLerpTouchMinor) * lerpAlpha

                    remoteEngine.updatePhysics(null, null, 0f, 0f, 0f, 0f, canvasSize.width.coerceAtLeast(1f), 0f, 0f)
                }
            }
        }
    }

    val haptic = LocalHapticFeedback.current
    var closeButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var recordButtonBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.transport.incomingEvents.collect { envelope ->
            if (!envelope.isExpired()) {
                val delayMs = envelope.computePlaybackDelayMs()
                launch(Dispatchers.Default) {
                    if (delayMs > 0) {
                        delay(delayMs)
                    }
                    val payload = envelope.payload
                    
                    viewModel.updateRemotePayload(payload)
                    
                    if (payload.isTouching) {
                        val vibe = viewModel.vibrator
                        val intensity = payload.pressure.coerceIn(0f, 1f)
                        val amplitude = (70 + intensity * 185f).toInt().coerceIn(50, 255)
                        val duration = (40L + payload.touchSize * 100f).toLong().coerceIn(20L, 200L)

                        try {
                            vibe.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                        } catch (e: Exception) {
                            android.util.Log.e("KissGestureCanvasOverlay", "Haptic feedback failed", e)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(isMutual) {
        if (isMutual) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val mutualPulse by animateFloatAsState(
        targetValue = if (isMutual) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "mutual_pulse"
    )

    val blurEffect = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.graphics.RenderEffect.createBlurEffect(
                30f, 30f, android.graphics.Shader.TileMode.DECAL
            ).asComposeRenderEffect()
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF0D0610)), // Deep velvet dark
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    renderEffect = blurEffect,
                    compositingStrategy = CompositingStrategy.Offscreen
                )
                .pointerInput(Unit) {
                    val activePointersMap = mutableMapOf<Long, com.enclave.app.models.KissGestureFrame>()
                    this.awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            canvasSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())
                            
                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) {
                                viewModel.sendKissImpression(
                                    size = 0f,
                                    pressure = 0f,
                                    x = 0f,
                                    y = 0f,
                                    isTouching = false
                                )
                                activePointersMap.clear()
                                event.changes.forEach { it.consume() }
                                continue
                            }
                            
                            event.changes.forEach { change ->
                                val pos = change.position
                                val inRecord = recordButtonBounds?.contains(pos) == true
                                val inClose = closeButtonBounds?.contains(pos) == true
                                
                                if (inRecord || inClose) {
                                    return@forEach
                                }

                                if (change.pressed) {
                                    if (!change.previousPressed) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    val pressure = change.pressure.coerceIn(0f, 1.2f)
                                    val wasActive = activePointersMap.containsKey(change.id.value)
                                    if (!isIntentionalImpressionTouch(pressure = pressure, wasActive = wasActive)) {
                                        return@forEach
                                    }

                                    val motionEvent = getRawMotionEvent(event)
                                    val pointerId = change.id.value.toInt()
                                    val pointerIndex = motionEvent?.findPointerIndex(pointerId) ?: -1
                                    val touchSize = if (pointerIndex >= 0 && motionEvent != null) {
                                        motionEvent.getSize(pointerIndex)
                                    } else {
                                        0.05f
                                    }
                                    val touchMajor = if (pointerIndex >= 0 && motionEvent != null) {
                                        motionEvent.getTouchMajor(pointerIndex)
                                    } else {
                                        0f
                                    }
                                    val touchMinor = if (pointerIndex >= 0 && motionEvent != null) {
                                        motionEvent.getTouchMinor(pointerIndex)
                                    } else {
                                        0f
                                    }
                                    val orientation = if (pointerIndex >= 0 && motionEvent != null) {
                                        motionEvent.getOrientation(pointerIndex)
                                    } else {
                                        0f
                                    }
                                    val baseRadius = 22f
                                    val scaleFactor = 750f
                                    val touchRadius = (baseRadius + touchSize * scaleFactor).coerceIn(18f, 150f)

                                    val xPctVal = if (size.width > 0) change.position.x / size.width.toFloat() else 0f
                                    val yPctVal = if (size.height > 0) change.position.y / size.height.toFloat() else 0f

                                    val frame = com.enclave.app.models.KissGestureFrame(
                                        xPct = if (xPctVal.isNaN()) 0f else xPctVal,
                                        yPct = if (yPctVal.isNaN()) 0f else yPctVal,
                                        action = "KISS_TOUCH_MOVE",
                                        pressure = pressure.coerceIn(0f, 1f),
                                        touchRadius = touchRadius,
                                        fingerIndex = change.id.value.toInt(),
                                        touchMajor = touchMajor,
                                        touchMinor = touchMinor,
                                        orientation = orientation
                                    )
                                    activePointersMap[change.id.value] = frame
                                    change.consume()
                                } else {
                                    activePointersMap.remove(change.id.value)
                                    change.consume()
                                }
                            }
                            
                            val allFrames = activePointersMap.values.toList()
                            
                            if (allFrames.isNotEmpty()) {
                                val agg = allFrames.toAggregatedImpression()
                                if (agg != null) {
                                    viewModel.sendKissImpression(
                                        size = agg.radiusPx,
                                        pressure = agg.pressure,
                                        x = agg.xPct,
                                        y = agg.yPct,
                                        isTouching = true,
                                        touchMajor = agg.touchMajor,
                                        touchMinor = agg.touchMinor,
                                        orientation = agg.orientation
                                    )
                                }
                                
                                if (isRecordingKiss) {
                                    val offset = System.currentTimeMillis() - recordingStartTime
                                    allFrames.forEach { p ->
                                        recordedPoints.add(
                                            com.enclave.app.models.RecordedKissPoint(
                                                xPct = p.xPct,
                                                yPct = p.yPct,
                                                pressure = p.pressure,
                                                touchRadius = p.touchRadius,
                                                timeOffsetMs = offset
                                            )
                                        )
                                    }
                                }
                            } else {
                                viewModel.sendKissImpression(
                                    size = 0f,
                                    pressure = 0f,
                                    x = 0f,
                                    y = 0f,
                                    isTouching = false
                                )
                                activePointersMap.clear()
                            }
                        }
                    }
                }
        ) {
            val w = size.width
            val h = size.height

            // Draw partner's live impression
            remotePayload?.let { payload ->
                if (payload.isTouching) {
                    drawLipPhysicsMesh(
                        engine = remoteEngine,
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize,
                        color = ComposeColor(0xFFFF1F6B),
                        alpha = 0.85f,
                        pressure = remoteLerpPressure,
                        orientation = payload.orientation,
                        canvasWidth = w
                    )
                }
            }

            // Draw my live impression
            localPayload?.let { payload ->
                if (payload.isTouching) {
                    val scale = if (isMutual) mutualPulse else 1f
                    val targetAlpha = if (remoteActive) 0.08f else 0.85f

                    drawLipPhysicsMesh(
                        engine = localEngine,
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize * scale,
                        color = ComposeColor(0xFFF72585),
                        alpha = targetAlpha,
                        pressure = localLerpPressure,
                        orientation = payload.orientation,
                        canvasWidth = w
                    )
                }
            }

            // Frozen sent impression (fading out)
            sentImpression?.let { payload ->
                if (payload.isTouching) {
                    drawLipImpression(
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize * 1.1f,
                        color = ComposeColor(0xFFFFB3C6),
                        alpha = 0.6f,
                        glowRadius = payload.touchSize * 1.3f,
                        pressure = payload.pressure,
                        touchMajor = payload.touchMajor * 1.1f,
                        touchMinor = payload.touchMinor * 1.1f,
                        orientation = payload.orientation
                    )
                }
            }

            // Frozen received impression (fading out)
            receivedImpression?.let { payload ->
                if (payload.isTouching) {
                    drawLipImpression(
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize * 1.1f,
                        color = ComposeColor(0xFFFF1F6B).copy(alpha = 0.6f),
                        alpha = 0.6f,
                        glowRadius = payload.touchSize * 1.3f,
                        pressure = payload.pressure,
                        touchMajor = payload.touchMajor * 1.1f,
                        touchMinor = payload.touchMinor * 1.1f,
                        orientation = payload.orientation
                    )
                }
            }

            // Mutual press sparkle overlay
            if (isMutual) {
                val sparkles = listOf(
                    Offset(w * 0.2f, h * 0.3f), Offset(w * 0.8f, h * 0.25f),
                    Offset(w * 0.15f, h * 0.7f), Offset(w * 0.85f, h * 0.65f),
                    Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f)
                )
                sparkles.forEach { pos ->
                    drawCircle(
                        color = ComposeColor(0xFFFFD700).copy(alpha = 0.55f),
                        radius = 6f * mutualPulse,
                        center = pos
                    )
                }
            }
        }

        // Title
        Text(
            text = "Touch Impression",
            fontFamily = OutfitFont,
            fontSize = 22.sp,
            color = ComposeColor(0xFFFFF5F6).copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 32.dp)
        )

        // Instruction
        Text(
            text = if (isMutual) {
                "Mutual Touch Active"
            } else if (isRecordingKiss) {
                "Recording Impression... keep touching the screen"
            } else {
                "Press and hold to create your live impression"
            },
            fontFamily = OutfitFont,
            fontSize = if (isMutual) 18.sp else 12.sp,
            color = if (isMutual) ComposeColor(0xFFFFB3C6) else if (isRecordingKiss) ComposeColor.Red else ComposeColor(0xFFFFF5F6).copy(alpha = 0.4f),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 66.dp)
        )

        // Record Mode toggle layout
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 24.dp)
                .onGloballyPositioned { coordinates ->
                    recordButtonBounds = coordinates.boundsInParent()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    if (isRecordingKiss) {
                        isRecordingKiss = false
                        if (recordedPoints.isNotEmpty()) {
                            val payload = com.enclave.app.models.RecordedKissPayload(recordedPoints.toList())
                            val jsonPayload = kotlinx.serialization.json.Json.encodeToString(payload)
                            onSendRecordedKiss(jsonPayload)
                        }
                    } else {
                        isRecordingKiss = true
                        recordedPoints.clear()
                        recordingStartTime = System.currentTimeMillis()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isRecordingKiss) ComposeColor.Red.copy(alpha = 0.3f) else ComposeColor.White.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    imageVector = if (isRecordingKiss) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                    contentDescription = "Record Kiss",
                    tint = if (isRecordingKiss) ComposeColor.Red else ComposeColor.White
                )
            }
            Text(
                text = if (isRecordingKiss) "Stop & Send" else "Record Kiss",
                color = ComposeColor.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        // Close button
        Button(
            onClick = {
                if (isRecordingKiss) {
                    isRecordingKiss = false
                    recordedPoints.clear()
                }
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1A0A12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .onGloballyPositioned { coordinates ->
                    closeButtonBounds = coordinates.boundsInParent()
                }
        ) {
            Text("✕ Close", fontFamily = OutfitFont, color = ComposeColor(0xFFFFF5F6), fontSize = 13.sp)
        }
    }
}

private data class AggregatedImpression(
    val xPct: Float,
    val yPct: Float,
    val pressure: Float,
    val radiusPx: Float,
    val touchMajor: Float,
    val touchMinor: Float,
    val orientation: Float
)

private fun List<com.enclave.app.models.KissGestureFrame>.toAggregatedImpression(): AggregatedImpression? {
    if (isEmpty()) return null
    val avgX = map { it.xPct }.filterNot { it.isNaN() }.average().let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val avgY = map { it.yPct }.filterNot { it.isNaN() }.average().let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val sumPressure = map { it.pressure }.filterNot { it.isNaN() }.sum().let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1.2f)
    val maxRadius = map { it.touchRadius }.filterNot { it.isNaN() }.maxOrNull() ?: 22f

    val maxMajor = map { it.touchMajor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val maxMinor = map { it.touchMinor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val avgOrientation = map { it.orientation }.filterNot { it.isNaN() }.average().let { if (it.isNaN()) 0f else it.toFloat() }

    val spread = if (size > 1) {
        map {
            val dx = it.xPct - avgX
            val dy = it.yPct - avgY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }.filterNot { it.isNaN() }.average().let { if (it.isNaN()) 0f else it.toFloat() } * 420f
    } else {
        0f
    }

    val cohesiveRadius = (maxRadius * 0.88f + spread + sumPressure * 18f).let { if (it.isNaN()) 22f else it }.coerceIn(22f, 120f)
    return AggregatedImpression(
        xPct = avgX,
        yPct = avgY,
        pressure = sumPressure,
        radiusPx = cohesiveRadius,
        touchMajor = if (maxMajor > 0f) maxMajor else cohesiveRadius * 2.2f,
        touchMinor = if (maxMinor > 0f) maxMinor else cohesiveRadius * 1.4f,
        orientation = avgOrientation
    )
}

private fun isIntentionalImpressionTouch(pressure: Float, wasActive: Boolean): Boolean {
    if (wasActive) return true
    // Light accidental brushes and palm-noise tend to appear as ultra-low pressure spikes.
    // Keep threshold lenient so a real soft touch still passes.
    return pressure >= 0.045f
}


@Composable
fun CoListeningLounge(musicSyncController: MusicSyncController?) {
    if (musicSyncController == null) return
    val currentTrackName by musicSyncController.currentTrackName.collectAsState()
    val isPlaying by musicSyncController.isPlaying.collectAsState()
    
    var dismissedTrackName by rememberSaveable { mutableStateOf("") }
    
    if (currentTrackName.isNotEmpty() && currentTrackName != dismissedTrackName) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = BlushSent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "Music",
                        tint = ComposeColor(0xFFE598A7),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Co-Listening",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CharcoalText
                        )
                        Text(
                            text = currentTrackName,
                            fontSize = 12.sp,
                            color = CharcoalText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { musicSyncController.togglePlayPause() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = CharcoalText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { dismissedTrackName = currentTrackName },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hide",
                            tint = CharcoalText.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawLipPhysicsMesh(
    engine: LipPhysicsEngine,
    cx: Float,
    cy: Float,
    radius: Float,
    color: ComposeColor,
    alpha: Float,
    pressure: Float = 0.5f,
    orientation: Float = 0f,
    canvasWidth: Float
) {
    if (engine.nodes.isEmpty()) return
    
    val p = pressure.coerceIn(0.01f, 1f)
    val baseAlpha = alpha * (0.35f + p * 0.65f)
    val drawingScale = (canvasWidth * 0.6f) / 4.5f

    withTransform({
        rotate(degrees = Math.toDegrees(orientation.toDouble()).toFloat(), pivot = Offset(cx, cy))
    }) {
        // 1. Build & Draw Smooth Bezier Contour Envelope for Outer boundary (IDs 0 to 141)
        val path = Path()
        val startNode = engine.nodes.find { it.id == 0 }
        if (startNode != null) {
            val startX = cx + startNode.currentX * drawingScale
            val startY = cy + startNode.currentY * drawingScale
            path.moveTo(startX, startY)
            
            var prevX = startX
            var prevY = startY
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                val nextX = cx + node.currentX * drawingScale
                val nextY = cy + node.currentY * drawingScale
                val midX = (prevX + nextX) / 2f
                val midY = (prevY + nextY) / 2f
                
                path.quadraticBezierTo(prevX, prevY, midX, midY)
                prevX = nextX
                prevY = nextY
            }
            path.close()
            
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = baseAlpha * 0.5f), color.copy(alpha = baseAlpha * 0.05f)),
                    center = Offset(cx, cy),
                    radius = drawingScale * 1.5f
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 2. Draw defined sharp outer perimeter rim using a perimeter line Path
        val perimeterPath = Path()
        val perimeterStartNode = engine.nodes.find { it.id == 0 }
        if (perimeterStartNode != null) {
            val startX = cx + perimeterStartNode.currentX * drawingScale
            val startY = cy + perimeterStartNode.currentY * drawingScale
            perimeterPath.moveTo(startX, startY)
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                val nextX = cx + node.currentX * drawingScale
                val nextY = cy + node.currentY * drawingScale
                perimeterPath.lineTo(nextX, nextY)
            }
            perimeterPath.close()

            drawPath(
                path = perimeterPath,
                color = color.copy(alpha = baseAlpha * 0.8f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 4.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 3. Draw Subtle Inner Springs as Glowing Screen-blended Wireframes underneath
        for (spring in engine.springs) {
            val n1 = spring.node1
            val n2 = spring.node2
            
            val x1 = cx + n1.currentX * drawingScale
            val y1 = cy + n1.currentY * drawingScale
            val x2 = cx + n2.currentX * drawingScale
            val y2 = cy + n2.currentY * drawingScale

            drawLine(
                color = color.copy(alpha = baseAlpha * 0.15f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 2.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

/**
 * Organic touch-impression renderer.
 *
 * This intentionally avoids any predefined lip/emoji silhouette.
 * It draws a pressure-driven irregular stamp made of micro-blobs + grain lines
 * so every touch looks natural and unique to motion/pressure.
 */
fun DrawScope.drawLipImpression(
    cx: Float,
    cy: Float,
    radius: Float,
    color: ComposeColor,
    alpha: Float,
    glowRadius: Float = 0f,
    pressure: Float = 0.5f,
    touchMajor: Float = 0f,
    touchMinor: Float = 0f,
    orientation: Float = 0f
) {
    val p = pressure.coerceIn(0.01f, 1f)
    val pigment = ComposeColor(
        red = color.red + (1f - color.red) * (1f - p) * 0.35f,
        green = color.green + (1f - color.green) * (1f - p) * 0.35f,
        blue = color.blue + (1f - color.blue) * (1f - p) * 0.35f,
        alpha = 1f
    )
    val baseAlpha = alpha * (0.32f + p * 0.68f)

    // Fallbacks if major/minor are not supplied or are zero
    val baseWidth = if (touchMajor > 0f) touchMajor else radius * 2.2f
    val baseHeight = if (touchMinor > 0f) touchMinor else radius * 1.4f

    // Scale slightly based on pressure
    val finalWidth = baseWidth * (0.9f + p * 0.2f)
    val finalHeight = baseHeight * (0.9f + p * 0.2f)

    withTransform({
        rotate(degrees = Math.toDegrees(orientation.toDouble()).toFloat(), pivot = Offset(cx, cy))
    }) {
        // Draw the soft main glowing oval
        if (glowRadius > 0f) {
            val baseGlowWidth = if (touchMajor > 0f) touchMajor * 1.25f else glowRadius * 2.2f
            val baseGlowHeight = if (touchMinor > 0f) touchMinor * 1.25f else glowRadius * 1.4f
            val finalGlowWidth = baseGlowWidth * (0.9f + p * 0.2f)
            val finalGlowHeight = baseGlowHeight * (0.9f + p * 0.2f)
            drawOval(
                color = pigment.copy(alpha = baseAlpha * 0.12f),
                topLeft = Offset(cx - finalGlowWidth * 0.5f, cy - finalGlowHeight * 0.5f),
                size = androidx.compose.ui.geometry.Size(finalGlowWidth, finalGlowHeight),
                blendMode = BlendMode.Screen
            )
        }

        // Draw organic micro-blobs distributed within the ellipse area
        val microBlobCount = (6 + p * 20f).toInt().coerceIn(6, 28)
        for (i in 0 until microBlobCount) {
            val t = i.toFloat() / microBlobCount.toFloat()
            val ang = t * (Math.PI * 2.0) + (cx * 0.0013f + cy * 0.0017f + i * 0.37f)
            val rJitter = 0.15f + ((i * 73) % 100) / 170f
            val x = cx + kotlin.math.cos(ang).toFloat() * (finalWidth * 0.5f) * rJitter
            val y = cy + kotlin.math.sin(ang).toFloat() * (finalHeight * 0.5f) * rJitter * (0.82f + p * 0.36f)
            val dot = radius * (0.16f + ((i * 31) % 100) / 520f) * (0.7f + p * 0.6f)
            drawCircle(
                color = pigment.copy(alpha = baseAlpha * (0.45f + (i % 5) * 0.08f).coerceAtMost(0.95f)),
                radius = dot,
                center = Offset(x, y),
                blendMode = BlendMode.Screen
            )
        }

        // Draw organic horizontal skin/lip grain lines that span across the ellipse width
        val grainLines = (8 + p * 32f).toInt().coerceIn(8, 40)
        val spanX = finalWidth * 0.55f
        val spanY = finalHeight * 0.55f
        for (i in 0 until grainLines) {
            val yy = cy - spanY * 0.5f + (i / grainLines.toFloat()) * spanY
            val wobble = kotlin.math.sin((i * 0.73f + cx * 0.002f).toDouble()).toFloat() * finalHeight * 0.04f
            val start = Offset(cx - spanX * 0.5f, yy + wobble)
            val end = Offset(cx + spanX * 0.5f, yy - wobble * 0.6f)
            drawLine(
                color = pigment.copy(alpha = baseAlpha * 0.20f),
                start = start,
                end = end,
                strokeWidth = (radius * 0.028f * (0.45f + p)).coerceAtLeast(0.7f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

@Composable
fun RecordedKissPlaybackOverlay(
    payload: RecordedKissPayload,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val vibrator = remember {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
        vm.defaultVibrator
    }

    val renderedPoints = remember { mutableStateListOf<RecordedKissPoint>() }
    
    LaunchedEffect(payload) {
        val startTime = System.currentTimeMillis()
        val totalPoints = payload.points
        if (totalPoints.isEmpty()) {
            onClose()
            return@LaunchedEffect
        }
        
        totalPoints.forEach { point ->
            val elapsed = System.currentTimeMillis() - startTime
            val waitTime = point.timeOffsetMs - elapsed
            if (waitTime > 0) {
                delay(waitTime)
            }
            renderedPoints.add(point)
            
            try {
                val amplitude = (point.pressure * 255f).toInt().coerceIn(50, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(80, amplitude))
            } catch (_: Exception) {}
        }
        
        delay(1500)
        vibrator.cancel()
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ComposeColor(0xFF0D0610).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Impression Playback...",
            fontFamily = OutfitFont,
            fontSize = 20.sp,
            color = ComposeColor(0xFFFFF5F6).copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        ) {
            val w = size.width
            val h = size.height
            renderedPoints.forEach { point ->
                val cx = point.xPct * w
                val cy = point.yPct * h
                val radius = (point.touchRadius.coerceIn(20f, 80f) + point.pressure * 40f)
                
                drawLipImpression(
                    cx = cx,
                    cy = cy,
                    radius = radius,
                    color = ComposeColor(0xFFFF1F6B),
                    alpha = 0.85f,
                    glowRadius = radius * 1.5f,
                    pressure = point.pressure
                )
            }
        }

        Button(
            onClick = {
                vibrator.cancel()
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = ComposeColor(0xFF1A0A12)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Text("✕ Stop", color = ComposeColor(0xFFFFF5F6))
        }
    }
}

@Composable
fun LightboxOverlay(message: ChatMessage, viewModel: ChatViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var bitmapState by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var decryptedBytesState by remember { mutableStateOf<ByteArray?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val tempVideoFile = remember { java.io.File(context.cacheDir, "decrypted_video_${System.currentTimeMillis()}.mp4") }
    var isVideoPrepared by remember { mutableStateOf(false) }

    LaunchedEffect(message.id) {
        isLoading = true
        val bytes = viewModel.getMediaBytes(message.id)
        if (bytes != null) {
            decryptedBytesState = bytes
            if (message.messageType == "MEDIA_VIDEO") {
                // Save secure temporary video file for local playback
                try {
                    val fos = java.io.FileOutputStream(tempVideoFile)
                    fos.write(bytes)
                    fos.flush()
                    fos.close()
                    isVideoPrepared = true
                } catch (e: Exception) {
                    e.printStackTrace()
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
                // Shred video cache with zero bytes
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
        modifier = Modifier.fillMaxSize().background(ComposeColor.Black),
        contentAlignment = Alignment.Center
    ) {
        if (isLoading) {
            CircularProgressIndicator(color = ComposeColor.White)
        } else {
            if (message.messageType == "MEDIA_VIDEO") {
                if (isVideoPrepared) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { ctx ->
                            android.widget.VideoView(ctx).apply {
                                setVideoPath(tempVideoFile.absolutePath)
                                val mediaController = android.widget.MediaController(ctx)
                                mediaController.setAnchorView(this)
                                setMediaController(mediaController)
                                setOnPreparedListener { start() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().aspectRatio(16f/9f)
                    )
                } else {
                    Text("Failed to load video", color = ComposeColor.White)
                }
            } else {
                bitmapState?.let { bmp ->
                    androidx.compose.foundation.Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Decrypted Image",
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                    )
                } ?: Text("Failed to decrypt image", color = ComposeColor.White)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = 20.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = ComposeColor.White)
        }
    }
}

@Composable
fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: ComposeColor,
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

private fun getRawMotionEvent(event: androidx.compose.ui.input.pointer.PointerEvent): android.view.MotionEvent? {
    try {
        val field = event.javaClass.getDeclaredFields().firstOrNull { it.type == android.view.MotionEvent::class.java }
        if (field != null) {
            field.isAccessible = true
            return field.get(event) as? android.view.MotionEvent
        }
        val method = event.javaClass.getDeclaredMethods().firstOrNull { it.returnType == android.view.MotionEvent::class.java }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(event) as? android.view.MotionEvent
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

