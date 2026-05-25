@file:OptIn(ExperimentalMaterial3Api::class)
package com.enclave.app.ui.chat

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.media.MemoryMediaDataSource
import com.enclave.app.media.MusicSyncController
import com.enclave.app.ui.chat.components.*
import com.enclave.app.ui.theme.BlushBackground
import com.enclave.app.ui.theme.OutfitFont

// ─── ChatScreen – thin orchestrator ──────────────────────────────────────────
// All Composable building blocks live in com.enclave.app.ui.chat.components.*
// This file owns only: state wiring, navigation events, and scaffold assembly.

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
    val partnerProfile by profileViewModel?.partnerProfile?.collectAsState()
        ?: remember { mutableStateOf(null) }
    val partnerStatus by loungeViewModel?.partnerStatus?.collectAsState()
        ?: remember { mutableStateOf(null) }

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    var isSearchActive by remember { mutableStateOf(false) }

    // ── Partner display helpers ───────────────────────────────────────────────
    val partnerName = partnerProfile?.displayName
        ?.ifBlank { partnerProfile?.username }?.ifBlank { "Partner" } ?: "Partner"
    val partnerInitials = partnerName.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString("")
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

    // ── Overlay / sheet state ─────────────────────────────────────────────────
    var showAttachmentSheet by remember { mutableStateOf(false) }
    val attachmentSheetState = rememberModalBottomSheetState()
    var showKissCanvas by remember { mutableStateOf(false) }
    val activePlaybackKiss by viewModel.activePlaybackKiss.collectAsState()
    var lightboxMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showHapticDialog by remember { mutableStateOf(false) }

    LaunchedEffect(autoShowKissCanvas) {
        if (autoShowKissCanvas) showKissCanvas = true
    }

    // ── Back-press handling ───────────────────────────────────────────────────
    androidx.activity.compose.BackHandler(
        enabled = showKissCanvas || lightboxMessage != null ||
                showAttachmentSheet || activePlaybackKiss != null || isSearchActive
    ) {
        when {
            showKissCanvas -> showKissCanvas = false
            lightboxMessage != null -> lightboxMessage = null
            showAttachmentSheet -> showAttachmentSheet = false
            activePlaybackKiss != null -> viewModel.clearPlaybackKiss()
            isSearchActive -> { isSearchActive = false; viewModel.updateSearchQuery("") }
        }
    }

    // ── Media launchers ───────────────────────────────────────────────────────
    val context = LocalContext.current

    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "image/jpeg"
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.sendMediaMessage(stream.readBytes(), mimeType)
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        bitmap?.let {
            val stream = java.io.ByteArrayOutputStream()
            it.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, stream)
            viewModel.sendMediaMessage(stream.toByteArray(), "image/jpeg")
        }
    }

    val pickFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "*/*"
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.sendMediaMessage(stream.readBytes(), mimeType)
            }
        }
    }

    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it) ?: "audio/mpeg"
            context.contentResolver.openInputStream(it)?.use { stream ->
                viewModel.sendMediaMessage(stream.readBytes(), mimeType)
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.startAudioRecording()
        else Toast.makeText(
            context,
            "Microphone permission is required to record voice notes",
            Toast.LENGTH_SHORT
        ).show()
    }

    // ── Scaffold ──────────────────────────────────────────────────────────────
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
                        if (!active) viewModel.updateSearchQuery("")
                    }
                )
            },
            bottomBar = {
                ChatInputBar(
                    viewModel = viewModel,
                    uiState = uiState,
                    onAttachClick = { showAttachmentSheet = true },
                    onRecordVoiceClick = {
                        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                            context, android.Manifest.permission.RECORD_AUDIO
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        if (granted) viewModel.startAudioRecording()
                        else recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
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
                                if (message.messageType == "MEDIA" ||
                                    message.messageType == "MEDIA_IMAGE" ||
                                    message.messageType == "MEDIA_VIDEO"
                                ) {
                                    lightboxMessage = message
                                }
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        // ── Full-screen overlays ──────────────────────────────────────────────
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

        if (showHapticDialog) {
            AlertDialog(
                onDismissRequest = { showHapticDialog = false },
                title = { Text("Send Haptic Pattern 📳") },
                text = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        listOf("Heartbeat", "Purr", "Rapid").forEach { pattern ->
                            TextButton(
                                onClick = {
                                    viewModel.sendHapticMessage(pattern)
                                    showHapticDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(pattern, fontSize = 16.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showHapticDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }

    // ── Attachment bottom sheet ───────────────────────────────────────────────
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = attachmentSheetState,
            containerColor = BlushBackground
        ) {
            AttachmentSheet(
                onCamera = {
                    showAttachmentSheet = false
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    takePictureLauncher.launch(null)
                },
                onGallery = {
                    showAttachmentSheet = false
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
                onFile = {
                    showAttachmentSheet = false
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickFileLauncher.launch("*/*")
                },
                onAudio = {
                    showAttachmentSheet = false
                    com.enclave.app.ui.vault.BiometricPromptManager.isSystemPickerActive = true
                    pickAudioLauncher.launch("audio/*")
                },
                onHaptic = {
                    showAttachmentSheet = false
                    showHapticDialog = true
                }
            )
        }
    }
}

// ─── Attachment sheet content (used only by ChatScreen) ───────────────────────
@Composable
private fun AttachmentSheet(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onAudio: () -> Unit,
    onHaptic: () -> Unit
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
            color = com.enclave.app.ui.theme.CharcoalText,
            modifier = Modifier.padding(bottom = 20.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            AttachmentOptionItem(
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                color = androidx.compose.ui.graphics.Color(0xFFE598A7),
                onClick = onCamera
            )
            AttachmentOptionItem(
                icon = Icons.Default.Image,
                label = "Gallery",
                color = androidx.compose.ui.graphics.Color(0xFF88B04B),
                onClick = onGallery
            )
            AttachmentOptionItem(
                icon = Icons.Default.AttachFile,
                label = "File",
                color = androidx.compose.ui.graphics.Color(0xFF5B5EA6),
                onClick = onFile
            )
            AttachmentOptionItem(
                icon = Icons.Default.MusicNote,
                label = "Audio",
                color = androidx.compose.ui.graphics.Color(0xFFEFC050),
                onClick = onAudio
            )
            AttachmentOptionItem(
                icon = Icons.Default.Vibration,
                label = "Haptic",
                color = androidx.compose.ui.graphics.Color(0xFFF06292),
                onClick = onHaptic
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
