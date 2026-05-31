package dev.saifmukhtar.enclave.ui.kiss

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.models.KissGestureFrame
import dev.saifmukhtar.enclave.models.LipImpressionFrame
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import dev.saifmukhtar.enclave.webrtc.LenientJson
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

    private val recordedPoints = mutableListOf<dev.saifmukhtar.enclave.models.RecordedKissPoint>()
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
        val payload = dev.saifmukhtar.enclave.models.RecordedKissPayload(recordedPoints.toList())
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

    private val hapticFeedbackUseCase = HapticFeedbackUseCase(context)
    private val kissSyncUseCase = KissSyncUseCase(signalingClient, viewModelScope)

    init {
        viewModelScope.launch {
            kissSyncUseCase.incomingMessages.collect { wrap ->
                try {
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
                        kissSyncUseCase.sendKissMessageReliably(msg)
                    } catch (_: Exception) {}
                }
            }
        }

        if (_isRecording.value) {
            val offset = System.currentTimeMillis() - recordingStartTime
            points.filter { it.action != "KISS_TOUCH_UP" }.forEach { p ->
                recordedPoints.add(
                    dev.saifmukhtar.enclave.models.RecordedKissPoint(
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
                kissSyncUseCase.sendKissMessageReliably(msg)
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
                        kissSyncUseCase.sendKissMessageReliably(msg)
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
            hapticFeedbackUseCase.startMutualHapticLoop(
                remoteIntensityFlow = remoteIntensity,
                localIntensityFlow = localIntensity,
                partnerSignatureSeed = partnerSignatureSeed,
                partnerStyleIntensityFlow = { partnerStyleIntensity },
                partnerStyleVelocityFlow = { partnerStyleVelocity },
                isMutualPressActive = { _isMutualPress.value }
            )
        }
    }

    /** Instantly terminates the mutual haptic loop and cancels active vibration */
    private fun stopMutualHapticEngine() {
        mutualHapticJob?.cancel()
        mutualHapticJob = null
        hapticFeedbackUseCase.stopHaptics()
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

            val intent = Intent(context, Class.forName("dev.saifmukhtar.enclave.MainActivity")).apply {
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
            android.util.Log.e("Enclave", "Exception caught", e)
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
