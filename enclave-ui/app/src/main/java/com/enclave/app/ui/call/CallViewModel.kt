package com.enclave.app.ui.call

import android.app.Application
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.data.local.CallLogDao
import com.enclave.app.data.local.CallLogEntity
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.WebRtcManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.json.JSONObject
import org.webrtc.*
import java.util.UUID

enum class CallState {
    IDLE, RINGING_OUTGOING, RINGING_INCOMING, CONNECTING, ACTIVE
}

enum class RingingState {
    IDLE, CALLING, RINGING, UNREACHABLE
}

class CallViewModel(
    application: Application,
    private val signalingClient: SignalingClient,
    val partnerId: String,
    private val callLogDao: CallLogDao? = null
) : AndroidViewModel(application) {

    val eglContext: EglBase.Context = EglBase.create().eglBaseContext

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _ringingState = MutableStateFlow(RingingState.IDLE)
    val ringingState: StateFlow<RingingState> = _ringingState.asStateFlow()

    private var toneGenerator: android.media.ToneGenerator? = null
    private var offlineTimeoutJob: kotlinx.coroutines.Job? = null
    private var callingToneJob: kotlinx.coroutines.Job? = null

    private val _localVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val localVideoTrack: StateFlow<VideoTrack?> = _localVideoTrack.asStateFlow()

    private val _remoteVideoTrack = MutableStateFlow<VideoTrack?>(null)
    val remoteVideoTrack: StateFlow<VideoTrack?> = _remoteVideoTrack.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    /** True when the current/last call was audio-only (no camera). */
    private val _isAudioOnly = MutableStateFlow(false)
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    // 1. Picture-in-Picture dynamic tracking
    private val _isInPiPMode = MutableStateFlow(false)
    val isInPiPMode: StateFlow<Boolean> = _isInPiPMode.asStateFlow()

    // 2. Screen Sharing States & Events
    private val _isScreenSharing = MutableStateFlow(false)
    val isScreenSharing: StateFlow<Boolean> = _isScreenSharing.asStateFlow()

    private val _isCameraEnabled = MutableStateFlow(true)
    val isCameraEnabled: StateFlow<Boolean> = _isCameraEnabled.asStateFlow()

    private val _requestScreenShare = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestScreenShare: SharedFlow<Unit> = _requestScreenShare.asSharedFlow()

    private var webRtcManager: WebRtcManager? = null
    private var pendingOfferSdp: String? = null
    private var incomingCallType: String = "VIDEO"
    private val bufferedIceCandidates = mutableListOf<IceCandidate>()

    // Call log tracking
    private var currentCallType: String = "VIDEO"
    private var currentDirection: String = "OUTGOING"
    private var callStartTime: Long = 0L

    // 3. Proximity Screen-Off WakeLock & Sensor routing
    private val sensorManager = application.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val powerManager = application.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var proximityWakeLock: PowerManager.WakeLock? = null

    private val proximityListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            val distance = event?.values?.get(0) ?: return
            val isNear = distance < (proximitySensor?.maximumRange ?: 5f)
            if (isNear) {
                webRtcManager?.routeToEarpiece()
                _isSpeakerphoneOn.value = false
            } else {
                webRtcManager?.routeToSpeaker()
                _isSpeakerphoneOn.value = true
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        observeSignaling()
    }

    fun updatePiPState(pip: Boolean) {
        _isInPiPMode.value = pip
    }

    fun toggleCamera() {
        webRtcManager?.toggleCamera()
    }

    fun toggleScreenShare() {
        if (_isScreenSharing.value) {
            webRtcManager?.stopScreenCapture()
            _isScreenSharing.value = false
        } else {
            _requestScreenShare.tryEmit(Unit)
        }
    }

    fun startScreenCapture(intent: Intent) {
        viewModelScope.launch {
            kotlinx.coroutines.delay(300) // Ensure foreground service starts before getMediaProjection (Android 14)
            webRtcManager?.startScreenCapture(intent)
            _isScreenSharing.value = true
        }
    }

    private fun observeSignaling() {
        viewModelScope.launch {
            signalingClient.incomingWebRtcMessages.collect { msg ->
                when (msg.type) {
                    "WEBRTC_OFFER", "OFFER" -> {
                        if (_callState.value == CallState.IDLE) {
                            pendingOfferSdp = msg.payload
                            // Check if the incoming offer specifies audio-only
                            incomingCallType = try {
                                val json = JSONObject(msg.payload ?: "{}")
                                if (json.optString("callType") == "AUDIO") "AUDIO" else "VIDEO"
                            } catch (_: Exception) { "VIDEO" }
                            _callState.value = CallState.RINGING_INCOMING
                            currentDirection = "INCOMING"
                            currentCallType = incomingCallType
                            
                            // Send ringing feedback instantly back to caller
                            viewModelScope.launch {
                                signalingClient.sendWebRtcMessage(partnerId, "WEBRTC_RINGING", "")
                            }
                        }
                    }
                    "WEBRTC_ANSWER", "ANSWER" -> {
                        val payload = msg.payload
                        if (payload != null && (_callState.value == CallState.RINGING_OUTGOING || _callState.value == CallState.CONNECTING)) {
                            cancelOfflineTimeoutTimer()
                            startRingingTone() // Keep ringing until active
                            setRemoteSdp(payload)
                        }
                    }
                    "WEBRTC_RINGING" -> {
                        if (_callState.value == CallState.RINGING_OUTGOING) {
                            cancelOfflineTimeoutTimer()
                            _ringingState.value = RingingState.RINGING
                            startRingingTone()
                        }
                    }
                    "DELIVERY_STATUS" -> {
                        val payloadStr = msg.payload
                        if (payloadStr != null && _callState.value == CallState.RINGING_OUTGOING) {
                            try {
                                val json = JSONObject(payloadStr)
                                val delivered = json.optBoolean("delivered", false)
                                val typeTarget = json.optString("type", "")
                                if (typeTarget == "WEBRTC_OFFER" || typeTarget == "OFFER") {
                                    if (delivered) {
                                        _ringingState.value = RingingState.RINGING
                                        startRingingTone()
                                    } else {
                                        _ringingState.value = RingingState.CALLING
                                        startCallingBeeps()
                                        startOfflineTimeoutTimer()
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    "ICE_CANDIDATE" -> {
                        val payloadStr = msg.payload
                        if (payloadStr != null) {
                            val json = JSONObject(payloadStr)
                            val candidate = IceCandidate(
                                json.getString("sdpMid"),
                                json.getInt("sdpMLineIndex"),
                                json.getString("candidate")
                            )
                            if (webRtcManager?.peerConnection != null) {
                                webRtcManager?.peerConnection?.addIceCandidate(candidate)
                            } else {
                                bufferedIceCandidates.add(candidate)
                            }
                        }
                    }
                    "WEBRTC_HANGUP" -> {
                        if (_callState.value != CallState.IDLE) {
                            cleanup("MISSED")
                        }
                    }
                }
            }
        }
    }

    private fun playTone(toneType: Int, durationMs: Int = -1) {
        stopTone()
        try {
            toneGenerator = android.media.ToneGenerator(android.media.AudioManager.STREAM_VOICE_CALL, 100)
            toneGenerator?.startTone(toneType, durationMs)
        } catch (e: Exception) {
            Log.e("CallViewModel", "Failed to play tone", e)
        }
    }

    private fun stopTone() {
        try {
            toneGenerator?.stopTone()
            toneGenerator?.release()
        } catch (_: Exception) {}
        toneGenerator = null
    }

    private fun startCallingBeeps() {
        callingToneJob?.cancel()
        callingToneJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive && _ringingState.value == RingingState.CALLING) {
                playTone(android.media.ToneGenerator.TONE_SUP_PIP, 400)
                delay(2000)
            }
        }
    }

    private fun startRingingTone() {
        callingToneJob?.cancel()
        callingToneJob = viewModelScope.launch(Dispatchers.Main) {
            playTone(android.media.ToneGenerator.TONE_SUP_RINGTONE)
        }
    }

    private fun startOfflineTimeoutTimer() {
        offlineTimeoutJob?.cancel()
        offlineTimeoutJob = viewModelScope.launch(Dispatchers.Main) {
            delay(10000) // 10 seconds timeout
            if (_ringingState.value == RingingState.CALLING) {
                _ringingState.value = RingingState.UNREACHABLE
                callingToneJob?.cancel()
                playTone(android.media.ToneGenerator.TONE_SUP_CONGESTION, 3000)
                delay(3000)
                cleanup("MISSED")
            }
        }
    }

    private fun cancelOfflineTimeoutTimer() {
        offlineTimeoutJob?.cancel()
        offlineTimeoutJob = null
    }

    /**
     * Start a call. Pass "AUDIO" for audio-only, "VIDEO" for video call (default).
     */
    fun startCall(callType: String = "VIDEO") {
        if (_callState.value != CallState.IDLE) return
        currentCallType = callType
        currentDirection = "OUTGOING"
        _isAudioOnly.value = callType == "AUDIO"
        _callState.value = CallState.RINGING_OUTGOING

        val manager = WebRtcManager(getApplication(), eglContext, callType == "AUDIO")
        webRtcManager = manager
        _isSpeakerphoneOn.value = if (manager.isExternalAudioDeviceConnected()) false else (callType != "AUDIO")

        if (callType == "AUDIO") {
            manager.startAudioOnlyCapture()
        } else {
            manager.startLocalCapture()
            _localVideoTrack.value = manager.localVideoTrack
        }

        manager.createPeerConnection(createPeerConnectionObserver())

        manager.peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(desc: SessionDescription) {
                manager.peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                    override fun onSetSuccess() {
                        viewModelScope.launch {
                            // Embed callType in the offer payload so receiver knows
                            val payload = JSONObject().apply {
                                put("sdp", desc.description)
                                put("callType", callType)
                            }.toString()
                            signalingClient.sendWebRtcMessage(partnerId, "WEBRTC_OFFER", payload)
                        }
                    }
                }, desc)
            }
        }, MediaConstraints())
    }

    fun acceptCall() {
        if (_callState.value != CallState.RINGING_INCOMING || pendingOfferSdp == null) return
        _callState.value = CallState.CONNECTING
        _isAudioOnly.value = incomingCallType == "AUDIO"

        val manager = WebRtcManager(getApplication(), eglContext, incomingCallType == "AUDIO")
        webRtcManager = manager
        _isSpeakerphoneOn.value = if (manager.isExternalAudioDeviceConnected()) false else (incomingCallType != "AUDIO")

        if (incomingCallType == "AUDIO") {
            manager.startAudioOnlyCapture()
        } else {
            manager.startLocalCapture()
            _localVideoTrack.value = manager.localVideoTrack
        }

        manager.createPeerConnection(createPeerConnectionObserver())

        // Extract raw SDP from the payload (may be a JSON object wrapping the SDP)
        val rawSdp = try {
            val json = JSONObject(pendingOfferSdp ?: "")
            json.getString("sdp")
        } catch (_: Exception) {
            pendingOfferSdp ?: ""
        }

        val remoteDesc = SessionDescription(SessionDescription.Type.OFFER, rawSdp)
        manager.peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                bufferedIceCandidates.forEach { manager.peerConnection?.addIceCandidate(it) }
                bufferedIceCandidates.clear()

                manager.peerConnection?.createAnswer(object : SimpleSdpObserver() {
                    override fun onCreateSuccess(desc: SessionDescription) {
                        manager.peerConnection?.setLocalDescription(object : SimpleSdpObserver() {
                            override fun onSetSuccess() {
                                viewModelScope.launch {
                                    signalingClient.sendWebRtcMessage(partnerId, "WEBRTC_ANSWER", desc.description)
                                    transitionToActive()
                                }
                            }
                        }, desc)
                    }
                }, MediaConstraints())
            }
        }, remoteDesc)
    }

    private fun transitionToActive() {
        callStartTime = System.currentTimeMillis()
        _callState.value = CallState.ACTIVE
        acquireProximityLockAndSensors()
        callingToneJob?.cancel()
        callingToneJob = null
        stopTone()
    }

    private fun acquireProximityLockAndSensors() {
        try {
            if (proximityWakeLock == null) {
                proximityWakeLock = powerManager.newWakeLock(
                    PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                    "Enclave:ProximityCheekLock"
                )
            }
            if (proximityWakeLock?.isHeld == false) {
                proximityWakeLock?.acquire(10 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w("CallViewModel", "Proximity screen off wakelock failed: ${e.message}")
        }
        proximitySensor?.let {
            sensorManager.registerListener(proximityListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    private fun releaseProximityLockAndSensors() {
        try {
            if (proximityWakeLock?.isHeld == true) {
                proximityWakeLock?.release()
            }
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
        }
        sensorManager.unregisterListener(proximityListener)
    }

    fun rejectCall() {
        viewModelScope.launch {
            signalingClient.sendWebRtcMessage(partnerId, "WEBRTC_HANGUP", "")
        }
        cleanup("REJECTED")
    }

    fun endCall() {
        viewModelScope.launch {
            signalingClient.sendWebRtcMessage(partnerId, "WEBRTC_HANGUP", "")
        }
        cleanup("CONNECTED")
    }

    fun toggleMute() {
        val nextVal = !_isMuted.value
        _isMuted.value = nextVal
        webRtcManager?.localAudioTrack?.setEnabled(!nextVal)
    }

    fun toggleSpeakerphone() {
        val nextVal = !_isSpeakerphoneOn.value
        _isSpeakerphoneOn.value = nextVal
        if (nextVal) webRtcManager?.routeToSpeaker() else webRtcManager?.routeToEarpiece()
    }

    fun toggleCameraEnabled() {
        val manager = webRtcManager ?: return
        val nextVal = !_isCameraEnabled.value
        _isCameraEnabled.value = nextVal

        if (nextVal) {
            if (manager.localVideoTrack == null) {
                manager.startLocalCapture()
                _localVideoTrack.value = manager.localVideoTrack
                manager.localVideoTrack?.let { track ->
                    manager.peerConnection?.addTrack(track, listOf("stream_val"))
                }
                viewModelScope.launch {
                    signalingClient.sendWebRtcMessage(partnerId, "PROFILE_UPDATE", "{\"videoEnabled\":true}")
                }
            } else {
                manager.localVideoTrack?.setEnabled(true)
            }
            _isAudioOnly.value = false
        } else {
            manager.localVideoTrack?.setEnabled(false)
            _isAudioOnly.value = (currentCallType == "AUDIO")
        }
    }

    private fun setRemoteSdp(sdp: String) {
        val remoteDesc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        webRtcManager?.peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                transitionToActive()
            }
        }, remoteDesc)
    }

    private fun createPeerConnectionObserver() = object : PeerConnection.Observer {
        override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            if (state == PeerConnection.IceConnectionState.FAILED ||
                state == PeerConnection.IceConnectionState.CLOSED) {
                viewModelScope.launch { cleanup("CONNECTED") }
            }
        }
        override fun onIceConnectionReceivingChange(p0: Boolean) {}
        override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}

        override fun onIceCandidate(candidate: IceCandidate) {
            val json = JSONObject().apply {
                put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
                put("candidate", candidate.sdp)
            }
            viewModelScope.launch {
                signalingClient.sendWebRtcMessage(partnerId, "ICE_CANDIDATE", json.toString())
            }
        }

        override fun onIceCandidatesRemoved(p0: Array<out IceCandidate>?) {}

        override fun onAddStream(stream: MediaStream) {
            if (stream.videoTracks.isNotEmpty()) {
                _remoteVideoTrack.value = stream.videoTracks[0]
            }
        }

        override fun onRemoveStream(p0: MediaStream?) {}
        override fun onDataChannel(p0: DataChannel?) {}
        override fun onRenegotiationNeeded() {}

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            val track = receiver.track()
            if (track is VideoTrack) {
                _remoteVideoTrack.value = track
            }
        }
    }

    private fun cleanup(status: String = "MISSED") {
        releaseProximityLockAndSensors()
        
        callingToneJob?.cancel()
        callingToneJob = null
        offlineTimeoutJob?.cancel()
        offlineTimeoutJob = null
        stopTone()
        _ringingState.value = RingingState.IDLE

        // Log the call to Room database
        if (_callState.value != CallState.IDLE) {
            val durationSecs = if (callStartTime > 0L)
                ((System.currentTimeMillis() - callStartTime) / 1000).toInt()
            else 0
            val resolvedStatus = if (callStartTime > 0L) "CONNECTED" else status
            viewModelScope.launch {
                callLogDao?.insert(
                    CallLogEntity(
                        id = UUID.randomUUID().toString(),
                        callType = currentCallType,
                        direction = currentDirection,
                        status = resolvedStatus,
                        startedAt = if (callStartTime > 0L) callStartTime else System.currentTimeMillis(),
                        durationSeconds = durationSecs
                    )
                )
            }
        }

        webRtcManager?.close()
        webRtcManager = null
        _localVideoTrack.value = null
        _remoteVideoTrack.value = null
        _isAudioOnly.value = false
        pendingOfferSdp = null
        bufferedIceCandidates.clear()
        callStartTime = 0L
        _callState.value = CallState.IDLE
        _isScreenSharing.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(err: String?) {
            Log.e("SimpleSdpObserver", "SDP Create Failure: $err")
        }
        override fun onSetFailure(err: String?) {
            Log.e("SimpleSdpObserver", "SDP Set Failure: $err")
        }
    }
}
