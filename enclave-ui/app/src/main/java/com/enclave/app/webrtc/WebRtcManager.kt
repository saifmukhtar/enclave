package com.enclave.app.webrtc

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule

class WebRtcManager(
    private val context: Context,
    private val eglBaseContext: EglBase.Context
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null

    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        initWebRTC()
    }

    private fun initWebRTC() {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        // 1. Explicitly configure WebRTC's JavaAudioDeviceModule to enable hardware AEC and Noise Suppression (NS)
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioRecord init error: $errorMessage")
                }
                override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode?, errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioRecord start error: $errorCode - $errorMessage")
                }
                override fun onWebRtcAudioRecordError(errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioRecord run error: $errorMessage")
                }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioTrack init error: $errorMessage")
                }
                override fun onWebRtcAudioTrackStartError(errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode?, errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioTrack start error: $errorCode - $errorMessage")
                }
                override fun onWebRtcAudioTrackError(errorMessage: String?) {
                    Log.e("WebRtcManager", "AudioTrack run error: $errorMessage")
                }
            })
            .createAudioDeviceModule()

        val options = PeerConnectionFactory.Options()
        val defaultVideoEncoderFactory = DefaultVideoEncoderFactory(eglBaseContext, true, true)
        val defaultVideoDecoderFactory = DefaultVideoDecoderFactory(eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audioDeviceModule)
            .setOptions(options)
            .setVideoEncoderFactory(defaultVideoEncoderFactory)
            .setVideoDecoderFactory(defaultVideoDecoderFactory)
            .createPeerConnectionFactory()

        // Set default routing to speakerphone on launch
        routeToSpeaker()
    }

    fun startLocalCapture() {
        val enumerator = Camera2Enumerator(context)
        val deviceNames = enumerator.deviceNames

        val selectedDevice = deviceNames.firstOrNull { enumerator.isFrontFacing(it) } ?: deviceNames.firstOrNull()
        if (selectedDevice != null) {
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            videoCapturer = enumerator.createCapturer(selectedDevice, null)
            val videoSource = peerConnectionFactory?.createVideoSource(videoCapturer!!.isScreencast)

            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(640, 480, 30)

            localVideoTrack = peerConnectionFactory?.createVideoTrack("local_video", videoSource)
        }

        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
    }

    /** For audio-only calls — initializes microphone track only, no camera. */
    fun startAudioOnlyCapture() {
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("local_audio", audioSource)
        localVideoTrack = null // Explicitly no video
    }

    fun toggleCamera() {
        val capturer = videoCapturer as? CameraVideoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {}
            override fun onCameraSwitchError(error: String?) {}
        })
    }

    fun startScreenCapture(mediaProjectionIntent: Intent) {
        try {
            // 1. Stop active camera capture
            try {
                videoCapturer?.stopCapture()
                videoCapturer?.dispose()
            } catch (e: Exception) {
                android.util.Log.e("Enclave", "Exception caught", e)
            }

            // Ensure surfaceTextureHelper is initialized (e.g. if call started as audio-only)
            if (surfaceTextureHelper == null) {
                surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBaseContext)
            }

            // 2. Create ScreenCapturerAndroid
            val screenCapturer = ScreenCapturerAndroid(mediaProjectionIntent, object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                }
            })
            videoCapturer = screenCapturer

            // 3. Re-initialize capturing under local_video track
            val videoSource = peerConnectionFactory?.createVideoSource(true)
            screenCapturer.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            screenCapturer.startCapture(1280, 720, 15) // Screen Capture: optimized 720p 15fps

            // 4. Update the local video track references
            localVideoTrack = peerConnectionFactory?.createVideoTrack("local_video", videoSource)

            // 5. Hot replace track in active peer connection senders
            peerConnection?.senders?.forEach { sender ->
                if (sender.track()?.id() == "local_video") {
                    sender.setTrack(localVideoTrack, true)
                }
            }
        } catch (e: Exception) {
            Log.e("WebRtcManager", "Screen capture failed, falling back to camera", e)
            startLocalCapture()
        }
    }

    fun stopScreenCapture() {
        // 1. Stop screen capture
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
        }

        // 2. Restore camera capture
        startLocalCapture()

        // 3. Hot restore track in active peer connection senders
        peerConnection?.senders?.forEach { sender ->
            if (sender.track()?.id() == "local_video") {
                sender.setTrack(localVideoTrack, true)
            }
        }
    }

    fun createPeerConnection(observer: PeerConnection.Observer) {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder(com.enclave.app.BuildConfig.TURN_SERVER_URL)
                .setUsername(com.enclave.app.BuildConfig.TURN_USERNAME)
                .setPassword(com.enclave.app.BuildConfig.TURN_PASSWORD)
                .createIceServer(),
            PeerConnection.IceServer.builder(com.enclave.app.BuildConfig.TURN_SERVER_URL + "?transport=tcp")
                .setUsername(com.enclave.app.BuildConfig.TURN_USERNAME)
                .setPassword(com.enclave.app.BuildConfig.TURN_PASSWORD)
                .createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, observer)

        localVideoTrack?.let { peerConnection?.addTrack(it, listOf("stream_val")) }
        localAudioTrack?.let { peerConnection?.addTrack(it, listOf("stream_val")) }
    }

    // Modern AudioManager routing via AudioManager.setCommunicationDevice API (Android 14+)
    // Modern & Legacy compatible AudioManager routing
    @Suppress("DEPRECATION")
    fun routeToSpeaker() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val speakerDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            }
            if (speakerDevice != null) {
                audioManager.setCommunicationDevice(speakerDevice)
            }
        } else {
            audioManager.isSpeakerphoneOn = true
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    @Suppress("DEPRECATION")
    fun routeToEarpiece() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val earpieceDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            }
            if (earpieceDevice != null) {
                audioManager.setCommunicationDevice(earpieceDevice)
            }
        } else {
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    @Suppress("DEPRECATION")
    fun routeToBluetooth() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val bluetoothDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET
            }
            if (bluetoothDevice != null) {
                audioManager.setCommunicationDevice(bluetoothDevice)
            } else {
                routeToSpeaker()
            }
        } else {
            audioManager.isSpeakerphoneOn = false
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
        }
    }

    @Suppress("DEPRECATION")
    fun close() {
        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (e: Exception) {
            android.util.Log.e("Enclave", "Exception caught", e)
        }
        surfaceTextureHelper?.dispose()
        peerConnection?.close()
        peerConnectionFactory?.dispose()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        } else {
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
}
