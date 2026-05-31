package dev.saifmukhtar.enclave.ui.kiss.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import dev.saifmukhtar.enclave.data.config.ConfigManager

/**
 * Manages a bidirectional, low-latency live audio streaming connection using WebRTC.
 * Bypasses all standard telephony and software filtering (AEC, noise suppression, AGC)
 * to provide a raw, ASMR-quality audio stream for breathing and touch feedback.
 */
class IntimateAudioStreamer(
    private val context: Context,
    private val onSendSdp: (sdp: SessionDescription) -> Unit,
    private val onSendIceCandidate: (candidate: IceCandidate) -> Unit
) {
    companion object {
        private const val TAG = "IntimateAudioStreamer"
        // Hold active observers in a synchronized set to prevent them from being garbage collected.
        // This is necessary because the native WebRTC layer holds a weak reference to the observer,
        // which can lead to JNI crashes if the observer is garbage collected.
        private val activeObservers = java.util.Collections.synchronizedSet(mutableSetOf<PeerConnection.Observer>())
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var remoteAudioTrack: AudioTrack? = null

    private val peerConnectionObserver = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "Signaling state changed: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "ICE connection state changed: $state")
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {
            Log.d(TAG, "ICE receiving state changed: $receiving")
        }

        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
            Log.d(TAG, "ICE gathering state changed: $state")
        }

        override fun onIceCandidate(candidate: IceCandidate) {
            Log.d(TAG, "ICE candidate gathered: $candidate")
            onSendIceCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {
            Log.d(TAG, "ICE candidates removed.")
        }

        override fun onAddStream(stream: MediaStream) {
            Log.d(TAG, "Remote stream added: ${stream.id}")
            stream.audioTracks?.firstOrNull()?.let { track ->
                track.setEnabled(true)
                remoteAudioTrack = track
                Log.d(TAG, "Remote audio track enabled and playing.")
            }
        }

        override fun onRemoveStream(stream: MediaStream?) {
            Log.d(TAG, "Remote stream removed: ${stream?.id}")
        }

        override fun onDataChannel(channel: DataChannel?) {}

        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed.")
        }

        override fun onAddTrack(receiver: RtpReceiver, mediaStreams: Array<out MediaStream>) {
            Log.d(TAG, "Track added of kind: ${receiver.track()?.kind()}")
            val track = receiver.track()
            if (track is AudioTrack) {
                track.setEnabled(true)
                remoteAudioTrack = track
                Log.d(TAG, "Remote audio track mapped to receiver and enabled.")
            }
        }

        override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
            Log.d(TAG, "Connection state changed: $newState")
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            Log.d(TAG, "Track received via transceiver")
        }

        override fun onStandardizedIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "Standardized ICE connection state changed: $newState")
        }

        override fun onSelectedCandidatePairChanged(event: CandidatePairChangeEvent?) {
            Log.d(TAG, "Selected candidate pair changed")
        }
    }

    init {
        initializeWebRtc()
    }

    private fun initializeWebRtc() {
        // Safely initialize factory to avoid duplicate initialization crashes
        runCatching {
            PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(context)
                    .setEnableInternalTracer(true)
                    .createInitializationOptions()
            )
        }

        // Configure JavaAudioDeviceModule to enable hardware AEC and Noise Suppression for clear audio
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(options)
            .createPeerConnectionFactory()

        createPeerConnection()
    }

    private fun createPeerConnection() {
        val configManager = ConfigManager.getInstance(context)
        val turnUrl = configManager.getTurnServerUrl() ?: ""
        val turnUser = configManager.getTurnUsername() ?: ""
        val turnPass = configManager.getTurnPassword() ?: ""

        val selfHostedStun = turnUrl.replace("turn:", "stun:")
        val iceServers = listOf(
            PeerConnection.IceServer.builder(selfHostedStun).createIceServer(),
            PeerConnection.IceServer.builder(turnUrl)
                .setUsername(turnUser)
                .setPassword(turnPass)
                .createIceServer(),
            PeerConnection.IceServer.builder(turnUrl + "?transport=tcp")
                .setUsername(turnUser)
                .setPassword(turnPass)
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        activeObservers.add(peerConnectionObserver)
        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, peerConnectionObserver)

        // Set up the raw mic track
        setupLocalAudioTrack()
    }

    private fun setupLocalAudioTrack() {
        // Configure MediaConstraints to enable WebRTC software-level noise suppression and echo cancellation for clear communication
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("echoCancellation", "true"))
        }

        val audioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        val track = peerConnectionFactory?.createAudioTrack("ASMR_local_audio", audioSource)
        localAudioTrack = track

        if (track != null) {
            peerConnection?.addTrack(track, listOf("ASMR_audio_stream"))
            Log.d(TAG, "Local raw ASMR audio track created and added to PeerConnection.")
        } else {
            Log.e(TAG, "Failed to create local ASMR audio track.")
        }
    }

    /** Initiates SDP negotiation by creating an offer */
    fun startNegotiation() {
        val constraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(d: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set successfully for Offer.")
                        onSendSdp(desc)
                    }
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?) {
                        Log.e(TAG, "Failed to set local description for Offer: $e")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String?) {
                Log.e(TAG, "Failed to create Offer: $e")
            }
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    /** Receives a remote SDP offer and responds with an SDP answer */
    fun handleOffer(sdpText: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, sdpText)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully for Offer. Creating Answer...")
                createAnswer()
            }
            override fun onCreateFailure(e: String?) {}
            override fun onSetFailure(e: String?) {
                Log.e(TAG, "Failed to set remote description for Offer: $e")
            }
        }, sessionDescription)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints()
        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(d: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Local description set successfully for Answer.")
                        onSendSdp(desc)
                    }
                    override fun onCreateFailure(e: String?) {}
                    override fun onSetFailure(e: String?) {
                        Log.e(TAG, "Failed to set local description for Answer: $e")
                    }
                }, desc)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(e: String?) {
                Log.e(TAG, "Failed to create Answer: $e")
            }
            override fun onSetFailure(e: String?) {}
        }, constraints)
    }

    /** Processes the remote negotiation answer */
    fun handleAnswer(sdpText: String) {
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, sdpText)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(d: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.d(TAG, "Remote description set successfully for Answer.")
            }
            override fun onCreateFailure(e: String?) {}
            override fun onSetFailure(e: String?) {
                Log.e(TAG, "Failed to set remote description for Answer: $e")
            }
        }, sessionDescription)
    }

    /** Injects remote network candidates into WebRTC engine */
    fun handleRemoteCandidate(sdpMid: String, sdpMLineIndex: Int, sdp: String) {
        val candidate = IceCandidate(sdpMid, sdpMLineIndex, sdp)
        peerConnection?.addIceCandidate(candidate)
    }

    /** Enables or disables local microphone capture */
    fun setMicrophoneMuted(muted: Boolean) {
        localAudioTrack?.setEnabled(!muted)
        Log.d(TAG, "Microphone enabled state: ${!muted}")
    }

    /** Dynamically adjusts the volume of the incoming remote ASMR audio track */
    fun setRemotePlayoutVolume(volume: Double) {
        remoteAudioTrack?.setVolume(volume.coerceIn(0.0, 1.0))
        Log.d(TAG, "Remote playout volume adjusted to: $volume")
    }

    /** Routes the live WebRTC audio stream playout to the phone's physical Earpiece */
    fun routeToEarpiece() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val earpiece = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE }
                if (earpiece != null) {
                    val success = audioManager.setCommunicationDevice(earpiece)
                    Log.d(TAG, "Routing to earpiece. Success = $success")
                } else {
                    Log.w(TAG, "No built-in earpiece device found for communication handover.")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                Log.d(TAG, "Legacy routing to earpiece set (isSpeakerphoneOn = false).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to route audio playout to earpiece", e)
        }
    }

    /** Routes the live WebRTC audio stream playout to the phone's Speakerphone */
    fun routeToSpeaker() {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val devices = audioManager.availableCommunicationDevices
                val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    val success = audioManager.setCommunicationDevice(speaker)
                    Log.d(TAG, "Routing to speaker. Success = $success")
                } else {
                    Log.w(TAG, "No built-in speaker device found for communication handover.")
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = true
                Log.d(TAG, "Legacy routing to speaker set (isSpeakerphoneOn = true).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to route audio playout to speaker", e)
        }
    }

    /** Resets the global AudioManager communications routing mode to prevent background leak */
    fun resetAudioRouting() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            }
            audioManager.mode = AudioManager.MODE_NORMAL
            Log.d(TAG, "AudioManager communication routing successfully reset.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset audio routing during teardown", e)
        }
    }

    /** Terminates the active media capture and closes all connection bindings */
    fun close() {
        try {
            activeObservers.remove(peerConnectionObserver)
            resetAudioRouting()
            localAudioTrack?.dispose()
            peerConnection?.close()
            peerConnectionFactory?.dispose()
            Log.d(TAG, "IntimateAudioStreamer connection successfully closed and disposed.")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing audio streamer resources", e)
        }
    }

}

@kotlinx.serialization.Serializable
data class KissIceCandidatePayload(
    val sdpMid: String,
    val sdpMLineIndex: Int,
    val sdp: String
)
