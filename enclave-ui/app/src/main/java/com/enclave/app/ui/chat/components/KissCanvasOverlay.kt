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

// ─── Touch aggregation helpers (private to this file) ─────────────────────────

private data class AggregatedImpression(
    val xPct: Float,
    val yPct: Float,
    val pressure: Float,
    val radiusPx: Float,
    val touchMajor: Float,
    val touchMinor: Float,
    val orientation: Float
)

private fun List<KissGestureFrame>.toAggregatedImpression(): AggregatedImpression? {
    if (isEmpty()) return null
    val avgX = map { it.xPct }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val avgY = map { it.yPct }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1f)
    val sumPressure = map { it.pressure }.filterNot { it.isNaN() }.sum()
        .let { if (it.isNaN()) 0f else it.toFloat() }.coerceIn(0f, 1.2f)
    val maxRadius = map { it.touchRadius }.filterNot { it.isNaN() }.maxOrNull() ?: 22f
    val maxMajor = map { it.touchMajor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val maxMinor = map { it.touchMinor }.filterNot { it.isNaN() }.maxOrNull() ?: 0f
    val avgOrientation = map { it.orientation }.filterNot { it.isNaN() }.average()
        .let { if (it.isNaN()) 0f else it.toFloat() }

    val spread = if (size > 1) {
        map {
            val dx = it.xPct - avgX
            val dy = it.yPct - avgY
            kotlin.math.sqrt(dx * dx + dy * dy)
        }.filterNot { it.isNaN() }.average()
            .let { if (it.isNaN()) 0f else it.toFloat() } * 420f
    } else 0f

    val cohesiveRadius = (maxRadius * 0.88f + spread + sumPressure * 18f)
        .let { if (it.isNaN()) 22f else it }.coerceIn(22f, 120f)
    return AggregatedImpression(
        xPct = avgX, yPct = avgY, pressure = sumPressure, radiusPx = cohesiveRadius,
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

/** Reflect raw MotionEvent out of Compose's PointerEvent via reflection for accurate
 *  [touchMajor]/[touchMinor]/[orientation]/[size] reads. */
internal fun getRawMotionEvent(event: androidx.compose.ui.input.pointer.PointerEvent): android.view.MotionEvent? {
    try {
        val field = event.javaClass.getDeclaredFields()
            .firstOrNull { it.type == android.view.MotionEvent::class.java }
        if (field != null) {
            field.isAccessible = true
            return field.get(event) as? android.view.MotionEvent
        }
        val method = event.javaClass.getDeclaredMethods()
            .firstOrNull { it.returnType == android.view.MotionEvent::class.java }
        if (method != null) {
            method.isAccessible = true
            return method.invoke(event) as? android.view.MotionEvent
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return null
}

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
                    val activePointersMap = mutableMapOf<Long, KissGestureFrame>()
                    this.awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            canvasSize = androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat())

                            val activePointers = event.changes.filter { it.pressed }
                            if (activePointers.isEmpty()) {
                                viewModel.sendKissImpression(size = 0f, pressure = 0f, x = 0f, y = 0f, isTouching = false)
                                activePointersMap.clear()
                                event.changes.forEach { it.consume() }
                                continue
                            }

                            event.changes.forEach { change ->
                                val pos = change.position
                                val inRecord = recordButtonBounds?.contains(pos) == true
                                val inClose = closeButtonBounds?.contains(pos) == true
                                if (inRecord || inClose) return@forEach

                                if (change.pressed) {
                                    if (!change.previousPressed) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }
                                    val pressure = change.pressure.coerceIn(0f, 1.2f)
                                    val wasActive = activePointersMap.containsKey(change.id.value)
                                    if (!isIntentionalImpressionTouch(pressure, wasActive)) return@forEach

                                    val motionEvent = getRawMotionEvent(event)
                                    var pointerIndex = -1
                                    if (motionEvent != null) {
                                        for (i in 0 until motionEvent.pointerCount) {
                                            val xDiff = Math.abs(motionEvent.getX(i) - change.position.x)
                                            val yDiff = Math.abs(motionEvent.getY(i) - change.position.y)
                                            if (xDiff < 25f && yDiff < 25f) {
                                                pointerIndex = i; break
                                            }
                                        }
                                    }
                                    val touchSize = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getSize(pointerIndex) else 0.05f
                                    val touchMajor = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getTouchMajor(pointerIndex) else 0f
                                    val touchMinor = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getTouchMinor(pointerIndex) else 0f
                                    val orientation = if (pointerIndex >= 0 && motionEvent != null) motionEvent.getOrientation(pointerIndex) else 0f
                                    val touchRadius = (22f + touchSize * 750f).coerceIn(18f, 150f)

                                    val xPctVal = if (size.width > 0) change.position.x / size.width.toFloat() else 0f
                                    val yPctVal = if (size.height > 0) change.position.y / size.height.toFloat() else 0f

                                    val frame = KissGestureFrame(
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
                                            RecordedKissPoint(
                                                xPct = p.xPct, yPct = p.yPct,
                                                pressure = p.pressure, touchRadius = p.touchRadius,
                                                timeOffsetMs = offset
                                            )
                                        )
                                    }
                                }
                            } else {
                                viewModel.sendKissImpression(size = 0f, pressure = 0f, x = 0f, y = 0f, isTouching = false)
                                activePointersMap.clear()
                            }
                        }
                    }
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

// ─── DrawScope extension: Lip Physics Mesh ────────────────────────────────────

@Suppress("UNUSED_PARAMETER")
fun DrawScope.drawLipPhysicsMesh(
    engine: LipPhysicsEngine,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
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
        // 1. Smooth Bezier contour fill (outer boundary nodes 0..141)
        val path = Path()
        val startNode = engine.nodes.find { it.id == 0 }
        if (startNode != null) {
            val startX = cx + startNode.currentX * drawingScale
            val startY = cy + startNode.currentY * drawingScale
            path.moveTo(startX, startY)
            var prevX = startX; var prevY = startY
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                val nextX = cx + node.currentX * drawingScale
                val nextY = cy + node.currentY * drawingScale
                val midX = (prevX + nextX) / 2f
                val midY = (prevY + nextY) / 2f
                path.quadraticBezierTo(prevX, prevY, midX, midY)
                prevX = nextX; prevY = nextY
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

        // 2. Sharp perimeter rim
        val perimeterPath = Path()
        val pStart = engine.nodes.find { it.id == 0 }
        if (pStart != null) {
            perimeterPath.moveTo(cx + pStart.currentX * drawingScale, cy + pStart.currentY * drawingScale)
            for (id in 1..141) {
                val node = engine.nodes.find { it.id == id } ?: continue
                perimeterPath.lineTo(cx + node.currentX * drawingScale, cy + node.currentY * drawingScale)
            }
            perimeterPath.close()
            drawPath(
                path = perimeterPath,
                color = color.copy(alpha = baseAlpha * 0.8f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round
                ),
                blendMode = BlendMode.Screen
            )
        }

        // 3. Inner springs as glowing wireframe
        for (spring in engine.springs) {
            val n1 = spring.node1; val n2 = spring.node2
            drawLine(
                color = color.copy(alpha = baseAlpha * 0.15f),
                start = Offset(cx + n1.currentX * drawingScale, cy + n1.currentY * drawingScale),
                end = Offset(cx + n2.currentX * drawingScale, cy + n2.currentY * drawingScale),
                strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round,
                blendMode = BlendMode.Screen
            )
        }
    }
}

// ─── DrawScope extension: Lip Impression (pressure-driven organic stamp) ──────

/**
 * Organic touch-impression renderer.
 *
 * Draws a pressure-driven irregular stamp of micro-blobs + grain lines
 * so every touch looks natural and unique to the motion/pressure applied.
 */
fun DrawScope.drawLipImpression(
    cx: Float,
    cy: Float,
    radius: Float,
    color: Color,
    alpha: Float,
    glowRadius: Float = 0f,
    pressure: Float = 0.5f,
    touchMajor: Float = 0f,
    touchMinor: Float = 0f,
    orientation: Float = 0f
) {
    val p = pressure.coerceIn(0.01f, 1f)
    val pigment = Color(
        red = color.red + (1f - color.red) * (1f - p) * 0.35f,
        green = color.green + (1f - color.green) * (1f - p) * 0.35f,
        blue = color.blue + (1f - color.blue) * (1f - p) * 0.35f,
        alpha = 1f
    )
    val baseAlpha = alpha * (0.32f + p * 0.68f)
    val baseWidth = if (touchMajor > 0f) touchMajor else radius * 2.2f
    val baseHeight = if (touchMinor > 0f) touchMinor else radius * 1.4f
    val finalWidth = baseWidth * (0.9f + p * 0.2f)
    val finalHeight = baseHeight * (0.9f + p * 0.2f)

    withTransform({
        rotate(degrees = Math.toDegrees(orientation.toDouble()).toFloat(), pivot = Offset(cx, cy))
    }) {
        if (glowRadius > 0f) {
            val gw = (if (touchMajor > 0f) touchMajor * 1.25f else glowRadius * 2.2f) * (0.9f + p * 0.2f)
            val gh = (if (touchMinor > 0f) touchMinor * 1.25f else glowRadius * 1.4f) * (0.9f + p * 0.2f)
            drawOval(
                color = pigment.copy(alpha = baseAlpha * 0.12f),
                topLeft = Offset(cx - gw * 0.5f, cy - gh * 0.5f),
                size = androidx.compose.ui.geometry.Size(gw, gh),
                blendMode = BlendMode.Screen
            )
        }

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
                radius = dot, center = Offset(x, y), blendMode = BlendMode.Screen
            )
        }

        val grainLines = (8 + p * 32f).toInt().coerceIn(8, 40)
        val spanX = finalWidth * 0.55f
        val spanY = finalHeight * 0.55f
        for (i in 0 until grainLines) {
            val yy = cy - spanY * 0.5f + (i / grainLines.toFloat()) * spanY
            val wobble = kotlin.math.sin((i * 0.73f + cx * 0.002f).toDouble()).toFloat() * finalHeight * 0.04f
            drawLine(
                color = pigment.copy(alpha = baseAlpha * 0.20f),
                start = Offset(cx - spanX * 0.5f, yy + wobble),
                end = Offset(cx + spanX * 0.5f, yy - wobble * 0.6f),
                strokeWidth = (radius * 0.028f * (0.45f + p)).coerceAtLeast(0.7f),
                cap = StrokeCap.Round, blendMode = BlendMode.Screen
            )
        }
    }
}
