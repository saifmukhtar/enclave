package dev.saifmukhtar.enclave.ui.call

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
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
    val isCameraEnabled by viewModel.isCameraEnabled.collectAsState()
    val ringingState by viewModel.ringingState.collectAsState()
    val isAudioOnly by viewModel.isAudioOnly.collectAsState()
    
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

    // Dynamic high-fidelity Call Duration Timer
    var callDurationSeconds by remember { mutableStateOf(0) }
    LaunchedEffect(callState) {
        if (callState == CallState.ACTIVE) {
            callDurationSeconds = 0
            while (true) {
                delay(1000)
                callDurationSeconds++
            }
        }
    }
    val minutes = callDurationSeconds / 60
    val seconds = callDurationSeconds % 60
    val durationText = String.format("%02d:%02d", minutes, seconds)

    AnimatedVisibility(
        visible = callState != CallState.IDLE,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        if (isInPiPMode) {
            // --- 1. PICTURE-IN-PICTURE MODE ---
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
            // --- 2. FULL UI CALLING MODE ---
            val isReallyAudioCall = isAudioOnly || !isCameraEnabled

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF2E171B), // Dark elegant rose
                                Color(0xFF13090A)  // Near-black vault base
                            )
                        )
                    )
            ) {
                if (!isReallyAudioCall && callState == CallState.ACTIVE && remoteVideoTrack != null) {
                    // Fullscreen Video Call rendering
                    VideoRenderer(
                        videoTrack = remoteVideoTrack,
                        eglBaseContext = viewModel.eglContext,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // Audio Call UI or Connecting state with premium pulsing aura
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            // Infinite breathing halo animation for the profile circle
                            val infiniteTransition = rememberInfiniteTransition(label = "halo")
                            val pulseScale1 by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.35f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale1"
                            )
                            val pulseAlpha1 by infiniteTransition.animateFloat(
                                initialValue = 0.4f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha1"
                            )
                            val pulseScale2 by infiniteTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.6f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "scale2"
                            )
                            val pulseAlpha2 by infiniteTransition.animateFloat(
                                initialValue = 0.25f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(2200, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "alpha2"
                            )

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(180.dp)
                            ) {
                                // Halo rings
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .scale(pulseScale2)
                                        .graphicsLayer { alpha = pulseAlpha2 }
                                        .background(Color(0xFFFFC5CF).copy(alpha = 0.3f), CircleShape)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(150.dp)
                                        .scale(pulseScale1)
                                        .graphicsLayer { alpha = pulseAlpha1 }
                                        .background(Color(0xFFFFC5CF).copy(alpha = 0.5f), CircleShape)
                                )
                                
                                // Large premium pink avatar placeholder
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .background(
                                            Brush.radialGradient(
                                                colors = listOf(Color(0xFFFFF0F2), Color(0xFFFFD5DD))
                                            ),
                                            CircleShape
                                        )
                                        .border(2.dp, Color(0xFFFFC5CF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Avatar",
                                        tint = Color(0xFF8C3E4A),
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Text(
                                text = when (callState) {
                                    CallState.RINGING_OUTGOING -> when (ringingState) {
                                        RingingState.CALLING -> "Calling..."
                                        RingingState.RINGING -> "Ringing..."
                                        RingingState.UNREACHABLE -> "Unreachable"
                                        else -> "Calling..."
                                    }
                                    CallState.RINGING_INCOMING -> "Incoming Secure Call"
                                    CallState.CONNECTING -> "Connecting..."
                                    CallState.ACTIVE -> "Secure Audio Call"
                                    else -> ""
                                },
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFF5F6)
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            if (callState == CallState.ACTIVE) {
                                Text(
                                    text = durationText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFFC5CF),
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            Text(
                                text = "🔒 End-to-End Encrypted",
                                fontSize = 13.sp,
                                color = Color(0xFFFFC5CF).copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Camera Switch (Flip) Button — Top Right Corner (Exactly like WhatsApp & Signal)
                if (!isReallyAudioCall && isCameraEnabled && callState == CallState.ACTIVE) {
                    IconButton(
                        onClick = { viewModel.toggleCamera() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 28.dp, end = 24.dp)
                            .size(50.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Cameraswitch,
                            contentDescription = "Flip Lens",
                            tint = Color.White
                        )
                    }
                }

                // Floating Draggable Local Camera Preview (only for Video Calls)
                if (!isReallyAudioCall && localVideoTrack != null && callState != CallState.RINGING_INCOMING) {
                    var offsetX by remember { mutableStateOf(50f) }
                    var offsetY by remember { mutableStateOf(100f) }

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
                            .size(110.dp, 150.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF2E171B))
                            .border(1.5.dp, Color(0xFFFFC5CF).copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    ) {
                        VideoRenderer(
                            videoTrack = localVideoTrack,
                            eglBaseContext = viewModel.eglContext,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // --- PREMIUM GLASSMORPHIC BOTTOM CONTROLS BAR ---
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 44.dp)
                        .fillMaxWidth(0.9f)
                        .height(88.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFF2A1518).copy(alpha = 0.82f))
                        .border(1.2.dp, Color(0xFFFFC5CF).copy(alpha = 0.22f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        if (callState == CallState.RINGING_INCOMING) {
                            // ACCEPT CALL BUTTON
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
                                    .size(56.dp)
                                    .background(Color(0xFF2E7D32), CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(28.dp))
                            }

                            // REJECT CALL BUTTON
                            IconButton(
                                onClick = { viewModel.rejectCall() },
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(Color(0xFFC62828), CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            // 1. MIC MUTE BUTTON
                            IconButton(
                                onClick = { viewModel.toggleMute() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(if (isMuted) Color(0xFFE53935).copy(alpha = 0.35f) else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                    contentDescription = "Mute",
                                    tint = if (isMuted) Color(0xFFFFB3B3) else Color(0xFFFFD5DD)
                                )
                            }

                            // 2. CAMERA TOGGLE (VIDEO ONLY)
                            if (!isAudioOnly) {
                                IconButton(
                                    onClick = { viewModel.toggleCameraEnabled() },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(if (!isCameraEnabled) Color(0xFFC62828).copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                                        contentDescription = "Toggle Camera",
                                        tint = Color(0xFFFFD5DD)
                                    )
                                }
                            }

                            // 3. SCREEN SHARE (VIDEO ONLY)
                            if (!isAudioOnly) {
                                IconButton(
                                    onClick = { viewModel.toggleScreenShare() },
                                    modifier = Modifier
                                        .size(46.dp)
                                        .background(if (isScreenSharing) Color(0xFFFFC5CF).copy(alpha = 0.3f) else Color.Transparent, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = if (isScreenSharing) Icons.AutoMirrored.Filled.StopScreenShare else Icons.AutoMirrored.Filled.ScreenShare,
                                        contentDescription = "Screen Share",
                                        tint = Color(0xFFFFD5DD)
                                    )
                                }
                            }

                            // 4. SPEAKERPHONE
                            IconButton(
                                onClick = { viewModel.toggleSpeakerphone() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .background(if (isSpeakerphoneOn) Color(0xFFFFC5CF).copy(alpha = 0.25f) else Color.Transparent, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isSpeakerphoneOn) Icons.AutoMirrored.Filled.VolumeUp else Icons.AutoMirrored.Filled.VolumeOff,
                                    contentDescription = "Speakerphone",
                                    tint = Color(0xFFFFD5DD)
                                )
                            }

                            // 5. END ACTIVE CALL BUTTON
                            IconButton(
                                onClick = { viewModel.endCall() },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color(0xFFC62828), CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(26.dp))
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
    var viewRef by remember { mutableStateOf<SurfaceViewRenderer?>(null) }

    AndroidView(
        factory = { ctx ->
            SurfaceViewRenderer(ctx).apply {
                init(eglBaseContext, null)
                setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
                setEnableHardwareScaler(true)
                viewRef = this
            }
        },
        update = {
            // Track bindings are fully handled via DisposableEffect context lifecycle triggers
        },
        modifier = modifier
    )

    DisposableEffect(videoTrack, viewRef) {
        val view = viewRef
        if (view != null && videoTrack != null) {
            videoTrack.addSink(view)
        }
        onDispose {
            if (view != null && videoTrack != null) {
                videoTrack.removeSink(view)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewRef?.release()
        }
    }
}
