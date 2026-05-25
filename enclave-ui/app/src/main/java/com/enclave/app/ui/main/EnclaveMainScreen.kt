package com.enclave.app.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.media.MusicSyncController
import com.enclave.app.ui.call.CallLogScreen
import com.enclave.app.ui.call.CallViewModel
import com.enclave.app.ui.call.VideoCallScreen
import com.enclave.app.ui.chat.ChatScreen
import com.enclave.app.ui.chat.ChatViewModel
import com.enclave.app.ui.kiss.KissViewModel
import com.enclave.app.ui.kiss.KissWorkflowViewModel
import com.enclave.app.ui.lounge.LoungeScreen
import com.enclave.app.ui.lounge.LoungeViewModel
import com.enclave.app.ui.lounge.tabs.*
import com.enclave.app.ui.profile.ProfileScreen
import com.enclave.app.ui.profile.ProfileViewModel
import com.enclave.app.ui.profile.StatusStoriesScreen
import com.enclave.app.ui.profile.StoryViewModel
import com.enclave.app.ui.vault.BiometricPromptManager
import com.enclave.app.ui.vault.VaultScreen
import com.enclave.app.ui.vault.VaultViewModel
import com.enclave.app.webrtc.SignalingClient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnclaveMainScreen(
    chatViewModel: ChatViewModel,
    kissViewModel: KissViewModel,
    kissWorkflowViewModel: KissWorkflowViewModel,
    callViewModel: CallViewModel,
    loungeViewModel: LoungeViewModel,
    profileViewModel: ProfileViewModel,
    storyViewModel: StoryViewModel,
    vaultViewModel: VaultViewModel,
    biometricManager: BiometricPromptManager,
    vaultRepository: VaultRepository,
    signalingClient: SignalingClient,
    musicSyncController: MusicSyncController?,
    autoLaunchKissState: Boolean,
    onKissCanvasClosed: () -> Unit,
    loungeGamesFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeDrawingFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeMusicFactory: androidx.lifecycle.ViewModelProvider.Factory,
    loungeMediaFactory: androidx.lifecycle.ViewModelProvider.Factory
) {
    val context = LocalContext.current
    var currentTab by rememberSaveable { mutableStateOf("chat") }
    var autoShowKissCanvas by remember { mutableStateOf(false) }
    var showProfileScreen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(autoLaunchKissState) {
        if (autoLaunchKissState) {
            currentTab = "chat"
            autoShowKissCanvas = true
        }
    }

    LaunchedEffect(Unit) {
        chatViewModel.navigateToVault.collect {
            currentTab = "vault"
        }
    }

    val audioCallPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            callViewModel.startCall("AUDIO")
        }
    }

    val videoCallPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraOk = permissions[Manifest.permission.CAMERA] == true
        val audioOk = permissions[Manifest.permission.RECORD_AUDIO] == true
        if (cameraOk && audioOk) callViewModel.startCall("VIDEO")
    }

    val callLogDao = remember { EnclaveDatabase.getInstance(context).callLogDao() }

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val serviceIntent = android.content.Intent(context, com.enclave.app.webrtc.ScreenShareService::class.java)
            context.startForegroundService(serviceIntent)
            callViewModel.startScreenCapture(result.data!!)
        }
    }

    LaunchedEffect(callViewModel) {
        callViewModel.requestScreenShare.collect {
            val mediaProjectionManager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
        }
    }

    val callState by callViewModel.callState.collectAsState()
    val activity = context as com.enclave.app.MainActivity
    LaunchedEffect(callState) {
        if (callState == com.enclave.app.ui.call.CallState.ACTIVE) {
            val params = android.app.PictureInPictureParams.Builder()
                .setAutoEnterEnabled(true)
                .setAspectRatio(android.util.Rational(3, 4))
                .build()
            activity.setPictureInPictureParams(params)
        } else {
            val params = android.app.PictureInPictureParams.Builder()
                .setAutoEnterEnabled(false)
                .build()
            activity.setPictureInPictureParams(params)
        }
    }

    DisposableEffect(activity) {
        val listener = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> { info ->
            callViewModel.updatePiPState(info.isInPictureInPictureMode)
        }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(listener)
        }
    }

    val isScreenSharing by callViewModel.isScreenSharing.collectAsState()
    LaunchedEffect(isScreenSharing) {
        if (isScreenSharing) {
            activity.setSecureMode(false) 
        } else {
            activity.setSecureMode(true) 
            val serviceIntent = android.content.Intent(context, com.enclave.app.webrtc.ScreenShareService::class.java)
            context.stopService(serviceIntent)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BackHandler(enabled = showProfileScreen || currentTab != "chat") {
            if (showProfileScreen) {
                showProfileScreen = false
            } else if (currentTab != "chat") {
                currentTab = "chat"
            }
        }

        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = currentTab == "chat",
                        onClick = { currentTab = "chat" },
                        icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat") },
                        label = { Text("Chat") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "calls",
                        onClick = { currentTab = "calls" },
                        icon = { Icon(Icons.Default.Call, contentDescription = "Calls") },
                        label = { Text("Calls") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "stories",
                        onClick = { currentTab = "stories" },
                        icon = {
                            val unviewed by storyViewModel.unviewedCount.collectAsState()
                            BadgedBox(badge = {
                                if (unviewed > 0) Badge { Text(unviewed.toString()) }
                            }) {
                                Icon(Icons.Default.AutoStories, contentDescription = "Stories")
                            }
                        },
                        label = { Text("Status") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "music",
                        onClick = { currentTab = "music" },
                        icon = { Icon(Icons.Default.MusicNote, contentDescription = "Music") },
                        label = { Text("Music") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "vault",
                        onClick = { currentTab = "vault" },
                        icon = { Icon(Icons.Default.Lock, contentDescription = "Vault") },
                        label = { Text("Vault") }
                    )
                    NavigationBarItem(
                        selected = currentTab == "lounge",
                        onClick = { currentTab = "lounge" },
                        icon = { Icon(Icons.Default.Casino, contentDescription = "Lounge") },
                        label = { Text("Lounge") }
                    )
                }
            }
        ) { paddingValues ->
            Box(modifier = Modifier.padding(paddingValues)) {
                when (currentTab) {
                    "chat" -> ChatScreen(
                        viewModel = chatViewModel,
                        musicSyncController = musicSyncController,
                        kissViewModel = kissViewModel,
                        profileViewModel = profileViewModel,
                        loungeViewModel = loungeViewModel,
                        signalingClient = signalingClient,
                        autoShowKissCanvas = autoShowKissCanvas,
                        onKissCanvasClosed = { 
                            autoShowKissCanvas = false
                            onKissCanvasClosed()
                        },
                        onAudioCallClick = {
                            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasAudio) callViewModel.startCall("AUDIO")
                            else audioCallPermissionsLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        },
                        onVideoCallClick = {
                            val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            val hasAudio = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                            if (hasCamera && hasAudio) callViewModel.startCall("VIDEO")
                            else videoCallPermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
                        },
                        onProfileClick = { showProfileScreen = true }
                    )
                    "calls" -> CallLogScreen(callLogDao)
                    "stories" -> StatusStoriesScreen(storyViewModel)
                    "music" -> com.enclave.app.ui.lounge.tabs.MusicLoungeTab(musicSyncController, loungeMusicFactory)
                    "vault" -> VaultScreen(vaultViewModel, biometricManager, vaultRepository)
                    else -> LoungeScreen(loungeViewModel, profileViewModel, musicSyncController, chatViewModel, loungeGamesFactory, loungeDrawingFactory, loungeMusicFactory, loungeMediaFactory)
                }
            }
        }

        // Glassmorphic In-App Notification Overlay for Mutual Intimacy
        val partnerWaiting by kissWorkflowViewModel.partnerWaitingForKiss.collectAsState()
        AnimatedVisibility(
            visible = partnerWaiting,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
        ) {
            Card(
                onClick = {
                    currentTab = "chat"
                    autoShowKissCanvas = true
                    kissWorkflowViewModel.clearPartnerWaitingForKiss()
                },
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xE61E122C)),
                border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "❤️",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Column {
                        Text(
                            text = "Someone is thinking of you...",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Touch to join their warm presence",
                            color = Color(0xFFE598A7),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        VideoCallScreen(callViewModel)

        if (showProfileScreen) {
            ProfileScreen(
                viewModel = profileViewModel,
                onClose = { showProfileScreen = false }
            )
        }
    }
}
