package com.enclave.app.ui.call

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.StopScreenShare
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.EglBase
import org.webrtc.RendererCommon
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack
import kotlin.math.roundToInt

@Composable
fun VideoCallScreen(viewModel: CallViewModel) {
    val callState by viewModel.callState.collectAsState()
    val localVideoTrack by viewModel.localVideoTrack.collectAsState()
    val remoteVideoTrack by viewModel.remoteVideoTrack.collectAsState()
    val isMuted by viewModel.isMuted.collectAsState()
    val isSpeakerphoneOn by viewModel.isSpeakerphoneOn.collectAsState()
    
    // Picture-in-Picture State tracking
    val isInPiPMode by viewModel.isInPiPMode.collectAsState()
    val isScreenSharing by viewModel.isScreenSharing.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    val callPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val cameraGranted = permissions[android.Manifest.permission.CAMERA] == true
        val audioGranted = permissions[android.Manifest.permission.RECORD_AUDIO] == true
        if (cameraGranted && audioGranted) {
            viewModel.acceptCall()
        } else {
            android.widget.Toast.makeText(context, "Camera & Microphone permissions are required to accept the call", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    AnimatedVisibility(
        visible = callState != CallState.IDLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (isInPiPMode) {
            // 1. Picture-in-Picture Mode: Show ONLY the remote participant track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                if (remoteVideoTrack != null) {
                    VideoRenderer(
                        videoTrack = remoteVideoTrack,
                        eglBaseContext = viewModel.eglContext,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (localVideoTrack != null) {
                    VideoRenderer(
                        videoTrack = localVideoTrack,
                        eglBaseContext = viewModel.eglContext,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // 2. Full UI Mode: Standard active/ringing screen layouts
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFFFF5F6)) // Minimalist Blush Canvas
            ) {
                // Background video track or ringing screen
                if (callState == CallState.ACTIVE && remoteVideoTrack != null) {
                    VideoRenderer(
                        videoTrack = remoteVideoTrack,
                        eglBaseContext = viewModel.eglContext,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = when (callState) {
                                    CallState.RINGING_OUTGOING -> "Ringing..."
                                    CallState.RINGING_INCOMING -> "Incoming Secure Call"
                                    CallState.CONNECTING -> "Connecting..."
                                    else -> ""
                                },
                                fontSize = 24.sp,
                                color = Color(0xFF2A1B1D),
                                fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Enclave End-to-End Encrypted",
                                fontSize = 14.sp,
                                color = Color(0xFF2A1B1D).copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Floating Draggable Local Preview
                if (localVideoTrack != null && callState != CallState.RINGING_INCOMING) {
                    var offsetX by remember { mutableStateOf(50f) }
                    var offsetY by remember { mutableStateOf(50f) }

                    Box(
                        modifier = Modifier
                            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                            .size(120.dp, 160.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFFFCE2E6)) // Soft Rose Base
                    ) {
                        VideoRenderer(
                            videoTrack = localVideoTrack,
                            eglBaseContext = viewModel.eglContext,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Floating Dark Semi-Transparent Controls Bar (Crisp & High Contrast, No Blur)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 48.dp)
                        .fillMaxWidth(0.9f)
                        .height(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (callState == CallState.RINGING_INCOMING) {
                            // Accept Call
                            IconButton(
                                onClick = {
                                    val hasCamera = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.CAMERA
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    val hasAudio = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context, android.Manifest.permission.RECORD_AUDIO
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    
                                    if (hasCamera && hasAudio) {
                                        viewModel.acceptCall()
                                    } else {
                                        callPermissionsLauncher.launch(
                                            arrayOf(
                                                android.Manifest.permission.CAMERA,
                                                android.Manifest.permission.RECORD_AUDIO
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFF4CAF50), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White)
                            }

                            // Reject Call
                            IconButton(
                                onClick = { viewModel.rejectCall() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFFF44336), CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White)
                            }
                        } else {
                            // Toggle Audio Mute
                            IconButton(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (isMuted) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = Color.White
                                )
                            }

                            // Toggle Camera Front/Back Lens
                            IconButton(
                                onClick = { viewModel.toggleCamera() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cameraswitch,
                                    contentDescription = "Flip Lens",
                                    tint = Color.White
                                )
                            }

                            // Toggle Screen Share
                            IconButton(
                                onClick = { viewModel.toggleScreenShare() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (isScreenSharing) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                                    contentDescription = "Screen Share",
                                    tint = Color.White
                                )
                            }

                            // Toggle Speakerphone
                            IconButton(
                                onClick = { viewModel.toggleSpeakerphone() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(if (isSpeakerphoneOn) Color.White.copy(alpha = 0.2f) else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Speakerphone",
                                    tint = Color.White
                                )
                            }

                            // End Active Call
                            IconButton(
                                onClick = { viewModel.endCall() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFFF44336), CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoRenderer(
    videoTrack: VideoTrack?,
    eglBaseContext: EglBase.Context,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
            }
        },
        update = { view ->
            if (videoTrack != null) {
                videoTrack.addSink(view)
            } else {
                videoTrack?.removeSink(view)
            }
        },
        modifier = modifier
    )
}

private val Color.Companion.white: Color
    get() = Color(0xFFFFFFFF)
