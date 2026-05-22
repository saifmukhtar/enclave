package com.enclave.app.ui.kiss

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.models.KissGestureFrame
import com.enclave.app.models.LipImpressionFrame
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.LenientJson
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.math.abs
import java.util.ArrayDeque
import java.util.UUID

/**
 * Tracks active local and remote touch timelines, runs dynamic pace-sensitive haptic loops,
 * detects mutual simultaneous press, and manages the lip-impression and invitation lifecycle.
 */
class KissWorkflowViewModel(
    application: Application,
    private val signalingClient: SignalingClient,
    val myId: String,
    private val partnerId: String
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Current active touch points on my screen
    private val _localFrame = MutableStateFlow<LipImpressionFrame?>(null)
    val localFrame: StateFlow<LipImpressionFrame?> = _localFrame.asStateFlow()

    // Partner's touch frame received over WebSocket
    private val _remoteFrame = MutableStateFlow<LipImpressionFrame?>(null)
    val remoteFrame: StateFlow<LipImpressionFrame?> = _remoteFrame.asStateFlow()

    // Sent impression — the "frozen" blob that floats to partner's screen
    private val _sentImpression = MutableStateFlow<LipImpressionFrame?>(null)
    val sentImpression: StateFlow<LipImpressionFrame?> = _sentImpression.asStateFlow()

    // Received impression — the partner's kiss mark shown on my screen
    private val _receivedImpression = MutableStateFlow<LipImpressionFrame?>(null)
    val receivedImpression: StateFlow<LipImpressionFrame?> = _receivedImpression.asStateFlow()

    // Mutual press state — both sides pressing at the same time
    private val _isMutualPress = MutableStateFlow(false)
    val isMutualPress: StateFlow<Boolean> = _isMutualPress.asStateFlow()

    // Expose partner waiting status for premium in-app UI prompt
    private val _partnerWaitingForKiss = MutableStateFlow(false)
    val partnerWaitingForKiss: StateFlow<Boolean> = _partnerWaitingForKiss.asStateFlow()

    // Live remote kiss intensity calculation (pressure + velocity + touch size)
    private val _remoteIntensity = MutableStateFlow(0f)
    val remoteIntensity: StateFlow<Float> = _remoteIntensity.asStateFlow()
    private val _localIntensity = MutableStateFlow(0f)
    val localIntensity: StateFlow<Float> = _localIntensity.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val recordedPoints = mutableListOf<com.enclave.app.models.RecordedKissPoint>()
    private var recordingStartTime = 0L
    private var lastInviteSentTime = 0L
    private var mutualHapticJob: Job? = null
    private val partnerSignatureSeed: Int = abs(partnerId.hashCode()).coerceAtLeast(1)
    private val outboundQueue = ArrayDeque<SignalMessageWrapper>()
    private val maxQueueSize = 100
    private var localSequenceCounter = 0L
    private val localSessionId = UUID.randomUUID().toString()
    private var lastRemoteSequence = -1L
    private var lastRemoteSessionId = ""
    private val syncLeadMs = 140L
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("kiss_haptic_profiles", Context.MODE_PRIVATE)
    }
    private var partnerStyleIntensity = prefs.getFloat("${partnerId}_avg_intensity", 0.5f).coerceIn(0f, 1f)
    private var partnerStyleVelocity = prefs.getFloat("${partnerId}_avg_velocity", 0.35f).coerceIn(0f, 1f)

    fun startRecordingKiss() {
        _isRecording.value = true
        recordedPoints.clear()
        recordingStartTime = System.currentTimeMillis()
    }

    fun stopRecordingKiss(): String? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        if (recordedPoints.isEmpty()) return null
        val payload = com.enclave.app.models.RecordedKissPayload(recordedPoints.toList())
        recordedPoints.clear()
        return Json.encodeToString(payload)
    }

    fun cancelRecordingKiss() {
        _isRecording.value = false
        recordedPoints.clear()
    }

    fun clearPartnerWaitingForKiss() {
        _partnerWaitingForKiss.value = false
    }

    private val vibrator by lazy {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    init {
        // Flush pending kiss events when websocket reconnects.
        viewModelScope.launch {
            signalingClient.connectionState.collect { state ->
                if (state == SignalingClient.ConnectionState.CONNECTED) {
                    flushQueuedMessages()
                }
            }
        }

        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { message ->
                try {
                    val wrap = LenientJson.decodeFromString<SignalMessageWrapper>(message)
                    if (wrap.senderId != partnerId) return@collect

                    when (wrap.type) {
                        "KISS_INVITATION" -> {
                            // Partner touched screen and is waiting. Trigger discreet in-app alert and OS notification.
                            _partnerWaitingForKiss.value = true
                            showDiscreetSystemNotification()
                        }
                        "KISS_WORKFLOW_TRIGGER" -> {
                            wrap.payload?.let {
                                val frame = LenientJson.decodeFromString<KissGestureFrame>(it)
                                val lipFrame = LipImpressionFrame(
                                    points = listOf(frame),
                                    sequenceNumber = 0L,
                                    sourceSessionId = ""
                                )
                                _remoteFrame.value = lipFrame
                                checkMutualPress()
                            }
                        }
                        "KISS_LIP_IMPRESSION" -> {
                            wrap.payload?.let {
                                val frame = LenientJson.decodeFromString<LipImpressionFrame>(it)
                                if (!isRemoteFrameFresh(frame)) {
                                    return@let
                                }
                                val oldFrame = _remoteFrame.value
                                val smoothedFrame = smoothRemoteFrame(frame, oldFrame)
                                _remoteFrame.value = smoothedFrame
                                _partnerWaitingForKiss.value = false // Partner joined, clear waiting invite
                                checkMutualPress()

                                _remoteIntensity.value = computeIntensity(smoothedFrame.points, oldFrame).coerceIn(0f, 1f)
                                updatePartnerStyleMemory(smoothedFrame.points, oldFrame)
                                
                                val wasActive = oldFrame?.points?.any { p -> p.action != "KISS_TOUCH_UP" } == true
                                val nowActive = smoothedFrame.points.any { p -> p.action != "KISS_TOUCH_UP" }
                                if (wasActive && !nowActive) {
                                    _receivedImpression.value = oldFrame
                                    viewModelScope.launch {
                                        delay(3000)
                                        _receivedImpression.value = null
                                    }
                                }
                            }
                        }
                        "KISS_MUTUAL_SYNC" -> {
                            val remoteStartAt = wrap.payload?.toLongOrNull()
                            if (remoteStartAt != null) {
                                val now = System.currentTimeMillis()
                                val delayMs = (remoteStartAt - now).coerceIn(0L, 260L)
                                viewModelScope.launch {
                                    if (delayMs > 0L) delay(delayMs)
                                    checkMutualPress()
                                    if (_isMutualPress.value) {
                                        startMutualHapticEngine()
                                    }
                                }
                            } else {
                                checkMutualPress()
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    /** Called each frame as the user presses on the kiss canvas */
    fun updateLocalFrame(points: List<KissGestureFrame>) {
        localSequenceCounter += 1L
        val frame = LipImpressionFrame(
            points = points,
            isMutualPress = _isMutualPress.value,
            sequenceNumber = localSequenceCounter,
            sourceSessionId = localSessionId
        )
        val oldLocalFrame = _localFrame.value
        _localFrame.value = frame
        _localIntensity.value = computeIntensity(points, oldLocalFrame).coerceIn(0f, 1f)
        checkMutualPress()

        // Throttled WebSocket invite trigger when we start touching and partner is not active
        val hasActiveTouch = points.any { it.action != "KISS_TOUCH_UP" }
        val partnerNotTouching = _remoteFrame.value?.points?.any { it.action != "KISS_TOUCH_UP" } != true
        if (hasActiveTouch && partnerNotTouching) {
            val now = System.currentTimeMillis()
            if (now - lastInviteSentTime > 8000L) {
                lastInviteSentTime = now
                viewModelScope.launch {
                    try {
                        val msg = SignalMessageWrapper(
                            type = "KISS_INVITATION",
                            senderId = myId,
                            targetId = partnerId,
                            payload = ""
                        )
                        sendKissMessageReliably(msg)
                    } catch (_: Exception) {}
                }
            }
        }

        if (_isRecording.value) {
            val offset = System.currentTimeMillis() - recordingStartTime
            points.filter { it.action != "KISS_TOUCH_UP" }.forEach { p ->
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

        viewModelScope.launch {
            try {
                val msg = SignalMessageWrapper(
                    type = "KISS_LIP_IMPRESSION",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(frame)
                )
                sendKissMessageReliably(msg)
            } catch (_: Exception) {}
        }
    }

    /** Called when the user lifts all fingers — freeze and "send" the impression */
    fun sendLipImpression(points: List<KissGestureFrame>) {
        if (points.isEmpty()) return
        localSequenceCounter += 1L
        val impression = LipImpressionFrame(
            points = points,
            sequenceNumber = localSequenceCounter,
            sourceSessionId = localSessionId
        )
        _sentImpression.value = impression

        viewModelScope.launch {
            delay(3000)
            _sentImpression.value = null
        }
        updateLocalFrame(points.map { it.copy(action = "KISS_TOUCH_UP") })
    }

    /** Checks if both partners are pressing simultaneously */
    private fun checkMutualPress() {
        val localActive = _localFrame.value?.points?.any { it.action != "KISS_TOUCH_UP" } == true
        val remoteActive = _remoteFrame.value?.points?.any { it.action != "KISS_TOUCH_UP" } == true
        val nowMutual = localActive && remoteActive

        if (nowMutual) {
            if (!_isMutualPress.value) {
                _isMutualPress.value = true
                val scheduledStart = System.currentTimeMillis() + syncLeadMs
                viewModelScope.launch {
                    val wait = (scheduledStart - System.currentTimeMillis()).coerceAtLeast(0L)
                    if (wait > 0L) delay(wait)
                    if (_isMutualPress.value) startMutualHapticEngine()
                }
                viewModelScope.launch {
                    try {
                        val msg = SignalMessageWrapper(
                            type = "KISS_MUTUAL_SYNC",
                            senderId = myId,
                            targetId = partnerId,
                            payload = scheduledStart.toString()
                        )
                        sendKissMessageReliably(msg)
                    } catch (_: Exception) {}
                }
            }
        } else {
            if (_isMutualPress.value) {
                _isMutualPress.value = false
                stopMutualHapticEngine()
            }
        }
    }

    /** Starts a dynamic pace-sensitive mutual touch haptic pulse loop */
    private fun startMutualHapticEngine() {
        mutualHapticJob?.cancel()
        mutualHapticJob = viewModelScope.launch {
            val vibe = vibrator
            while (isActive && _isMutualPress.value) {
                try {
                    // Blend both partners' active touch intensities for mutual sensation.
                    val blendedIntensity = ((_remoteIntensity.value * 0.62f) + (_localIntensity.value * 0.38f))
                        .coerceIn(0.08f, 1.0f)
                    val signature = buildSignaturePattern(partnerSignatureSeed, blendedIntensity)

                    vibe.vibrate(VibrationEffect.createWaveform(signature.timings, signature.amplitudes, -1))
                    delay(signature.totalDurationMs)
                } catch (_: Exception) {
                    try { vibe.vibrate(VibrationEffect.createOneShot(100, 150)) } catch (_: Exception) {}
                    delay(200)
                }
            }
        }
    }

    /** Instantly terminates the mutual haptic loop and cancels active vibration */
    private fun stopMutualHapticEngine() {
        mutualHapticJob?.cancel()
        mutualHapticJob = null
        try {
            vibrator.cancel()
        } catch (_: Exception) {}
    }

    /** Triggers a discreet, private OS system notification with secret phrasing */
    private fun showDiscreetSystemNotification() {
        try {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channelId = "enclave_privacy_signals"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                if (manager.getNotificationChannel(channelId) == null) {
                    val channel = NotificationChannel(
                        channelId,
                        "Private Signals",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Secure private notifications for intimate signals"
                    }
                    manager.createNotificationChannel(channel)
                }
            }

            val intent = Intent(context, Class.forName("com.enclave.app.MainActivity")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("AUTO_LAUNCH_KISS", true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                999,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // discreet notification with secret phrasing: "Someone is thinking of you... ❤️"
            val notification = androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_menu_compass) // Discreet compass menu icon for privacy
                .setContentTitle("Private Notification")
                .setContentText("Someone is thinking of you... ❤️")
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            manager.notify(8888, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun triggerMutualKissHaptic() {
        // Obsoleted in favor of dynamic startMutualHapticEngine loop
    }

    private fun isRemoteFrameFresh(frame: LipImpressionFrame): Boolean {
        val remoteSession = frame.sourceSessionId
        val remoteSeq = frame.sequenceNumber

        // Backward compatibility with older peers not sending sequence/session yet.
        if (remoteSession.isBlank() || remoteSeq <= 0L) {
            return true
        }

        if (lastRemoteSessionId != remoteSession) {
            lastRemoteSessionId = remoteSession
            lastRemoteSequence = remoteSeq
            return true
        }

        if (remoteSeq <= lastRemoteSequence) {
            return false
        }

        lastRemoteSequence = remoteSeq
        return true
    }

    private suspend fun sendKissMessageReliably(message: SignalMessageWrapper) {
        if (!signalingClient.isConnected()) {
            enqueueMessage(message)
            return
        }
        try {
            signalingClient.sendRawMessage(Json.encodeToString(message))
        } catch (_: Exception) {
            enqueueMessage(message)
        }
    }

    private fun enqueueMessage(message: SignalMessageWrapper) {
        synchronized(outboundQueue) {
            // Coalesce high-frequency frame updates: keep only latest live impression snapshot.
            if (message.type == "KISS_LIP_IMPRESSION") {
                outboundQueue.removeAll { it.type == "KISS_LIP_IMPRESSION" }
            }
            if (outboundQueue.size >= maxQueueSize) {
                outboundQueue.removeFirst()
            }
            outboundQueue.addLast(message)
        }
    }

    private suspend fun flushQueuedMessages() {
        while (signalingClient.isConnected()) {
            val next = synchronized(outboundQueue) {
                if (outboundQueue.isEmpty()) null else outboundQueue.removeFirst()
            } ?: break
            try {
                signalingClient.sendRawMessage(Json.encodeToString(next))
            } catch (_: Exception) {
                enqueueMessage(next)
                break
            }
        }
    }

    private data class SignaturePattern(
        val timings: LongArray,
        val amplitudes: IntArray,
        val totalDurationMs: Long
    )

    private fun buildSignaturePattern(seed: Int, intensity: Float): SignaturePattern {
        val personalizedIntensity = (intensity * 0.7f + partnerStyleIntensity * 0.3f).coerceIn(0f, 1f)
        val personalizedVelocity = partnerStyleVelocity.coerceIn(0f, 1f)
        val profile = when {
            personalizedIntensity < 0.34f -> "soft"
            personalizedIntensity < 0.72f -> "steady"
            else -> "intense"
        }
        val baseAmp = (70 + personalizedIntensity * 185f).toInt().coerceIn(50, 255)
        val paceShift = (personalizedVelocity * 18f).toLong()
        val timings: LongArray
        val amplitudes: IntArray
        when (profile) {
            "soft" -> {
                val p1 = 52L + (seed % 19)
                val p2 = 66L + (seed % 23)
                val gap = (70L + (seed % 21) - paceShift).coerceAtLeast(30L)
                timings = longArrayOf(0L, p1, gap, p2, gap + 20L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.55f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.72f).toInt().coerceIn(1, 255), 0
                )
            }
            "steady" -> {
                val p1 = 40L + (seed % 17)
                val p2 = 48L + (seed % 15)
                val p3 = 56L + (seed % 19)
                val gap = (46L + (seed % 18) - paceShift).coerceAtLeast(20L)
                timings = longArrayOf(0L, p1, gap, p2, gap, p3, gap + 8L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.66f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.84f).toInt().coerceIn(1, 255), 0,
                    baseAmp, 0
                )
            }
            else -> {
                val p1 = 30L + (seed % 13)
                val p2 = 34L + (seed % 11)
                val p3 = 40L + (seed % 15)
                val p4 = 48L + (seed % 17)
                val gap = (24L + (seed % 12) - paceShift).coerceAtLeast(12L)
                timings = longArrayOf(0L, p1, gap, p2, gap, p3, gap, p4, gap + 6L)
                amplitudes = intArrayOf(
                    0,
                    (baseAmp * 0.70f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.85f).toInt().coerceIn(1, 255), 0,
                    (baseAmp * 0.94f).toInt().coerceIn(1, 255), 0,
                    baseAmp, 0
                )
            }
        }
        return SignaturePattern(
            timings = timings,
            amplitudes = amplitudes,
            totalDurationMs = timings.sum().coerceAtLeast(120L)
        )
    }

    private fun smoothRemoteFrame(
        incoming: LipImpressionFrame,
        previous: LipImpressionFrame?
    ): LipImpressionFrame {
        if (previous == null) return incoming
        val prevByFinger = previous.points.associateBy { it.fingerIndex }
        val smoothing = 0.35f

        val smoothedPoints = incoming.points.map { point ->
            val prev = prevByFinger[point.fingerIndex]
            if (prev == null || point.action == "KISS_TOUCH_UP") {
                point
            } else {
                point.copy(
                    xPct = prev.xPct + (point.xPct - prev.xPct) * smoothing,
                    yPct = prev.yPct + (point.yPct - prev.yPct) * smoothing,
                    pressure = (prev.pressure + (point.pressure - prev.pressure) * 0.45f).coerceIn(0f, 1f),
                    touchRadius = (prev.touchRadius + (point.touchRadius - prev.touchRadius) * 0.45f).coerceIn(8f, 120f)
                )
            }
        }
        return incoming.copy(points = smoothedPoints)
    }

    private fun updatePartnerStyleMemory(
        points: List<KissGestureFrame>,
        previousFrame: LipImpressionFrame?
    ) {
        val active = points.filter { it.action != "KISS_TOUCH_UP" }
        if (active.isEmpty()) return
        val targetIntensity = computeIntensity(points, previousFrame).coerceIn(0f, 1f)

        val curr = active.firstOrNull()
        val prev = previousFrame?.points?.firstOrNull { it.action != "KISS_TOUCH_UP" && it.fingerIndex == curr?.fingerIndex }
        val velocity = if (curr != null && prev != null) {
            val dx = curr.xPct - prev.xPct
            val dy = curr.yPct - prev.yPct
            (kotlin.math.sqrt(dx * dx + dy * dy) * 12f).coerceIn(0f, 1f)
        } else {
            0f
        }

        partnerStyleIntensity = (partnerStyleIntensity * 0.92f + targetIntensity * 0.08f).coerceIn(0f, 1f)
        partnerStyleVelocity = (partnerStyleVelocity * 0.9f + velocity * 0.1f).coerceIn(0f, 1f)
        prefs.edit()
            .putFloat("${partnerId}_avg_intensity", partnerStyleIntensity)
            .putFloat("${partnerId}_avg_velocity", partnerStyleVelocity)
            .apply()
    }

    private fun computeIntensity(
        points: List<KissGestureFrame>,
        previousFrame: LipImpressionFrame?
    ): Float {
        val activePoints = points.filter { p -> p.action != "KISS_TOUCH_UP" }
        if (activePoints.isEmpty()) return 0f

        val avgPressure = activePoints.map { it.pressure }.average().toFloat().coerceIn(0f, 1f)
        val avgRadius = activePoints.map { it.touchRadius }.average().toFloat().coerceIn(10f, 100f)
        val activeNew = activePoints.firstOrNull()
        val activeOld = previousFrame?.points?.firstOrNull { p -> p.action != "KISS_TOUCH_UP" }
        val velocity = if (activeNew != null && activeOld != null) {
            val dx = activeNew.xPct - activeOld.xPct
            val dy = activeNew.yPct - activeOld.yPct
            (kotlin.math.sqrt(dx * dx + dy * dy) * 12f).coerceIn(0f, 1f)
        } else {
            0f
        }

        return (
            (avgPressure * 0.52f) +
                (velocity * 0.3f) +
                (((avgRadius - 20f) / 80f).coerceIn(0f, 1f) * 0.18f)
            ).coerceIn(0f, 1f)
    }

    // Legacy single-frame send for backward compatibility
    fun sendTouchFrame(xPct: Float, yPct: Float, action: String) {
        val frame = KissGestureFrame(xPct, yPct, action)
        updateLocalFrame(listOf(frame))
    }
}
