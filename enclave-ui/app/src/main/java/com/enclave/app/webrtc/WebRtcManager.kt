package com.enclave.app.webrtc

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import com.enclave.app.data.config.ConfigManager

class WebRtcManager(
    private val context: Context,
    private val eglBaseContext: EglBase.Context,
    private val isAudioOnly: Boolean = false
) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    var peerConnection: PeerConnection? = null

    var localVideoTrack: VideoTrack? = null
    var localAudioTrack: AudioTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioDeviceCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                handleAudioDeviceChange()
            }
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                handleAudioDeviceChange()
            }
        }
    } else {
        null
    }

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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        }

        if (isExternalAudioDeviceConnected()) {
            routeToExternalDevice()
        } else {
            routeToEarpiece()
        }
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

    private fun handleAudioDeviceChange() {
        if (isExternalAudioDeviceConnected()) {
            routeToExternalDevice()
        } else {
            routeToSpeaker()
        }
    }

    fun isExternalAudioDeviceConnected(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            for (device in devices) {
                if (device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    device.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    device.type == AudioDeviceInfo.TYPE_USB_HEADSET
                ) {
                    return true
                }
            }
        } else {
            @Suppress("DEPRECATION")
            if (audioManager.isWiredHeadsetOn || audioManager.isBluetoothScoOn) {
                return true
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun routeToExternalDevice() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val devices = audioManager.availableCommunicationDevices
            val externalDevice = devices.firstOrNull {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            if (externalDevice != null) {
                audioManager.setCommunicationDevice(externalDevice)
            }
        } else {
            audioManager.isSpeakerphoneOn = false
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        }
    }

    @Suppress("DEPRECATION")
    fun close() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && audioDeviceCallback != null) {
            try {
                audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Failed to unregister AudioDeviceCallback", e)
            }
        }

        val capturer = videoCapturer
        val helper = surfaceTextureHelper
        val pc = peerConnection
        val factory = peerConnectionFactory

        videoCapturer = null
        surfaceTextureHelper = null
        peerConnection = null
        peerConnectionFactory = null

        // Safe background thread native resource teardown to avoid blocking UI thread (ANR crash)
        Thread {
            try {
                capturer?.stopCapture()
                capturer?.dispose()
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Failed to dispose capturer", e)
            }
            try {
                helper?.dispose()
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Failed to dispose helper", e)
            }
            try {
                pc?.close()
                pc?.dispose()
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Failed to dispose peer connection", e)
            }
            try {
                factory?.dispose()
            } catch (e: Exception) {
                Log.e("WebRtcManager", "Failed to dispose factory", e)
            }
        }.start()

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
