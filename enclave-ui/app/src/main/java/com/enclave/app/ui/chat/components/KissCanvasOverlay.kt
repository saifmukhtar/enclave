package com.enclave.app.ui.chat.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enclave.app.models.KissGestureFrame
import com.enclave.app.models.RecordedKissPayload
import com.enclave.app.models.RecordedKissPoint
import com.enclave.app.ui.kiss.haptics.KissHapticManager
import com.enclave.app.ui.kiss.physics.LipPhysicsEngine
import com.enclave.app.ui.theme.OutfitFont
import com.enclave.app.ui.theme.BlushBackground
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import com.enclave.app.ui.kiss.*

// ─── Kiss Gesture Canvas Overlay ──────────────────────────────────────────────

@Composable
fun KissGestureCanvasOverlay(
    viewModel: com.enclave.app.ui.kiss.KissViewModel,
    signalingClient: SignalingClient,
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
                    val type = if (sdp.type == org.webrtc.SessionDescription.Type.OFFER)
                        "KISS_AUDIO_OFFER" else "KISS_AUDIO_ANSWER"
                    val wrap = SignalMessageWrapper(
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
                    val wrap = SignalMessageWrapper(
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

        val isCaller = viewModel.myId < viewModel.partnerId
        if (isCaller) streamer.startNegotiation()

        onDispose { streamer.close() }
    }

    LaunchedEffect(signalingClient, audioStreamer) {
        val streamer = audioStreamer ?: return@LaunchedEffect
        signalingClient.incomingRawMessages.collect { rawText ->
            try {
                val msg = com.enclave.app.webrtc.LenientJson
                    .decodeFromString<SignalMessageWrapper>(rawText)
                if (msg.senderId != viewModel.partnerId) return@collect
                when (msg.type) {
                    "KISS_AUDIO_OFFER" -> msg.payload?.let { streamer.handleOffer(it) }
                    "KISS_AUDIO_ANSWER" -> msg.payload?.let { streamer.handleAnswer(it) }
                    "KISS_AUDIO_ICE_CANDIDATE" -> msg.payload?.let { payloadText ->
                        val cp = com.enclave.app.webrtc.LenientJson
                            .decodeFromString<com.enclave.app.ui.kiss.audio.KissIceCandidatePayload>(payloadText)
                        streamer.handleRemoteCandidate(cp.sdpMid, cp.sdpMLineIndex, cp.sdp)
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
            streamer.setMicrophoneMuted(false)
            streamer.routeToEarpiece()
            streamer.setRemotePlayoutVolume(1.0)
        } else {
            streamer.setMicrophoneMuted(true)
            streamer.setRemotePlayoutVolume(0.15)
        }
    }

    var isRecordingKiss by remember { mutableStateOf(false) }
    val recordedPoints = remember { mutableStateListOf<RecordedKissPoint>() }
    var recordingStartTime by remember { mutableStateOf(0L) }
    var canvasSize by remember { mutableStateOf(androidx.compose.ui.geometry.Size.Zero) }

    val rawMeshRes = com.enclave.app.R.raw.partner_lip_mesh
    val localEngine = remember { LipPhysicsEngine(context, rawMeshRes) }
    val remoteEngine = remember { LipPhysicsEngine(context, rawMeshRes) }
    val hapticManager = remember { KissHapticManager(context) }

    // LERP state for micro-stutter suppression
    var localLerpPressure by remember { mutableStateOf(0f) }
    var localLerpTouchMajor by remember { mutableStateOf(0f) }
    var localLerpTouchMinor by remember { mutableStateOf(0f) }
    var remoteLerpPressure by remember { mutableStateOf(0f) }
    var remoteLerpTouchMajor by remember { mutableStateOf(0f) }
    var remoteLerpTouchMinor by remember { mutableStateOf(0f) }
    val lerpAlpha = 0.18f

    // Gravity sensor for physical device-tilt simulation
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
        onDispose { sensorManager.unregisterListener(listener) }
    }



    // Per-frame physics tick for both local and remote engines
    LaunchedEffect(localPayload, remotePayload, gravityX, gravityY) {
        while (true) {
            withFrameNanos {
                val local = localPayload
                if (local != null && local.isTouching) {
                    localLerpPressure += (local.pressure - localLerpPressure) * lerpAlpha
                    localLerpTouchMajor += (local.touchMajor - localLerpTouchMajor) * lerpAlpha
                    localLerpTouchMinor += (local.touchMinor - localLerpTouchMinor) * lerpAlpha
                    localEngine.updatePhysics(0f, 0f, localLerpTouchMajor, localLerpTouchMinor,
                        0f, localLerpPressure, canvasSize.width.coerceAtLeast(1f), gravityX, gravityY)
                    hapticManager.processEngineState(localEngine.engineKineticEnergy, localEngine.engineMeshStress)
                } else {
                    localLerpPressure += (0f - localLerpPressure) * lerpAlpha
                    localLerpTouchMajor += (0f - localLerpTouchMajor) * lerpAlpha
                    localLerpTouchMinor += (0f - localLerpTouchMinor) * lerpAlpha
                    localEngine.updatePhysics(null, null, 0f, 0f, 0f, 0f, canvasSize.width.coerceAtLeast(1f), gravityX, gravityY)
                }

                val remote = remotePayload
                if (remote != null && remote.isTouching) {
                    remoteLerpPressure += (remote.pressure - remoteLerpPressure) * lerpAlpha
                    remoteLerpTouchMajor += (remote.touchMajor - remoteLerpTouchMajor) * lerpAlpha
                    remoteLerpTouchMinor += (remote.touchMinor - remoteLerpTouchMinor) * lerpAlpha
                    remoteEngine.updatePhysics(0f, 0f, remoteLerpTouchMajor, remoteLerpTouchMinor,
                        0f, remoteLerpPressure, canvasSize.width.coerceAtLeast(1f), 0f, 0f)
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
                    if (delayMs > 0) delay(delayMs)
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
        if (isMutual) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val mutualPulse by animateFloatAsState(
        targetValue = if (isMutual) 1.15f else 1f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse),
        label = "mutual_pulse"
    )

    val blurEffect = remember {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.DECAL)
                .asComposeRenderEffect()
        } else null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0610)),
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
                    detectKissGestures(
                        recordButtonBounds = recordButtonBounds,
                        closeButtonBounds = closeButtonBounds,
                        haptic = haptic,
                        isRecordingKiss = isRecordingKiss,
                        recordingStartTime = recordingStartTime,
                        onImpressionClear = {
                            viewModel.sendKissImpression(size = 0f, pressure = 0f, x = 0f, y = 0f, isTouching = false)
                        },
                        onImpressionUpdate = { agg ->
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
                        },
                        onRecordPoints = { pts ->
                            recordedPoints.addAll(pts)
                        },
                        onCanvasSize = { size ->
                            canvasSize = size
                        }
                    )
                }
        ) {
            val w = size.width
            val h = size.height

            remotePayload?.let { payload ->
                if (payload.isTouching) {
                    drawLipPhysicsMesh(
                        engine = remoteEngine,
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize,
                        color = Color(0xFFFF1F6B),
                        alpha = 0.85f,
                        pressure = remoteLerpPressure,
                        orientation = payload.orientation,
                        canvasWidth = w
                    )
                }
            }

            localPayload?.let { payload ->
                if (payload.isTouching) {
                    val scale = if (isMutual) mutualPulse else 1f
                    val targetAlpha = if (remoteActive) 0.08f else 0.85f
                    drawLipPhysicsMesh(
                        engine = localEngine,
                        cx = payload.xPct * w,
                        cy = payload.yPct * h,
                        radius = payload.touchSize * scale,
                        color = Color(0xFFF72585),
                        alpha = targetAlpha,
                        pressure = localLerpPressure,
                        orientation = payload.orientation,
                        canvasWidth = w
                    )
                }
            }

            sentImpression?.let { payload ->
                if (payload.isTouching) {
                    drawLipImpression(
                        cx = payload.xPct * w, cy = payload.yPct * h,
                        radius = payload.touchSize * 1.1f, color = Color(0xFFFFB3C6),
                        alpha = 0.6f, glowRadius = payload.touchSize * 1.3f,
                        pressure = payload.pressure, touchMajor = payload.touchMajor * 1.1f,
                        touchMinor = payload.touchMinor * 1.1f, orientation = payload.orientation
                    )
                }
            }

            receivedImpression?.let { payload ->
                if (payload.isTouching) {
                    drawLipImpression(
                        cx = payload.xPct * w, cy = payload.yPct * h,
                        radius = payload.touchSize * 1.1f, color = Color(0xFFFF1F6B).copy(alpha = 0.6f),
                        alpha = 0.6f, glowRadius = payload.touchSize * 1.3f,
                        pressure = payload.pressure, touchMajor = payload.touchMajor * 1.1f,
                        touchMinor = payload.touchMinor * 1.1f, orientation = payload.orientation
                    )
                }
            }

            if (isMutual) {
                listOf(
                    Offset(w * 0.2f, h * 0.3f), Offset(w * 0.8f, h * 0.25f),
                    Offset(w * 0.15f, h * 0.7f), Offset(w * 0.85f, h * 0.65f),
                    Offset(w * 0.5f, h * 0.15f), Offset(w * 0.5f, h * 0.85f)
                ).forEach { pos ->
                    drawCircle(color = Color(0xFFFFD700).copy(alpha = 0.55f), radius = 6f * mutualPulse, center = pos)
                }
            }
        }

        Text(
            text = "Touch Impression",
            fontFamily = OutfitFont,
            fontSize = 22.sp,
            color = BlushBackground.copy(alpha = 0.85f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp)
        )

        Text(
            text = when {
                isMutual -> "Mutual Touch Active"
                isRecordingKiss -> "Recording Impression... keep touching the screen"
                else -> "Press and hold to create your live impression"
            },
            fontFamily = OutfitFont,
            fontSize = if (isMutual) 18.sp else 12.sp,
            color = when {
                isMutual -> Color(0xFFFFB3C6)
                isRecordingKiss -> Color.Red
                else -> BlushBackground.copy(alpha = 0.4f)
            },
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 66.dp)
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 24.dp)
                .onGloballyPositioned { recordButtonBounds = it.boundsInParent() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                onClick = {
                    if (isRecordingKiss) {
                        isRecordingKiss = false
                        if (recordedPoints.isNotEmpty()) {
                            val payload = RecordedKissPayload(recordedPoints.toList())
                            onSendRecordedKiss(Json.encodeToString(payload))
                        }
                    } else {
                        isRecordingKiss = true
                        recordedPoints.clear()
                        recordingStartTime = System.currentTimeMillis()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = if (isRecordingKiss) Color.Red.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    imageVector = if (isRecordingKiss) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                    contentDescription = "Record Kiss",
                    tint = if (isRecordingKiss) Color.Red else Color.White
                )
            }
            Text(
                text = if (isRecordingKiss) "Stop & Send" else "Record Kiss",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 11.sp
            )
        }

        Button(
            onClick = {
                if (isRecordingKiss) { isRecordingKiss = false; recordedPoints.clear() }
                onClose()
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0A12)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
                .onGloballyPositioned { closeButtonBounds = it.boundsInParent() }
        ) {
            Text("✕ Close", fontFamily = OutfitFont, color = BlushBackground, fontSize = 13.sp)
        }
    }
}

// ─── Recorded Kiss Playback Overlay ───────────────────────────────────────────

@Composable
fun RecordedKissPlaybackOverlay(payload: RecordedKissPayload, onClose: () -> Unit) {
    val context = LocalContext.current
    val vibrator = remember {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
        vm.defaultVibrator
    }
    val renderedPoints = remember { mutableStateListOf<RecordedKissPoint>() }

    LaunchedEffect(payload) {
        val startTime = System.currentTimeMillis()
        val totalPoints = payload.points
        if (totalPoints.isEmpty()) { onClose(); return@LaunchedEffect }
        totalPoints.forEach { point ->
            val elapsed = System.currentTimeMillis() - startTime
            val waitTime = point.timeOffsetMs - elapsed
            if (waitTime > 0) delay(waitTime)
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
            .background(Color(0xFF0D0610).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Impression Playback...",
            fontFamily = OutfitFont,
            fontSize = 20.sp,
            color = BlushBackground.copy(alpha = 0.9f),
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
                    cx = cx, cy = cy, radius = radius,
                    color = Color(0xFFFF1F6B), alpha = 0.85f,
                    glowRadius = radius * 1.5f, pressure = point.pressure
                )
            }
        }
        Button(
            onClick = { vibrator.cancel(); onClose() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A0A12)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp)
        ) {
            Text("✕ Stop", color = BlushBackground)
        }
    }
}

