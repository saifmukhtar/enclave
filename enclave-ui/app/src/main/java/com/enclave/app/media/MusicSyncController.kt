package com.enclave.app.media

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.LenientJson
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Wraps MediaController and coordinates real-time synchronization between peers
 * with built-in network latency drift interpolation and state echo deflection.
 */
class MusicSyncController(
    private val mediaController: MediaController,
    private val signalingClient: SignalingClient,
    private val myId: String,
    private val partnerId: String
) {
    // Stable, cancellable scope for all coroutines in this controller.
    // Cancelled via destroy() when the controller is no longer needed.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var ignoreNextPlayPause = false
    private var ignoreNextSeek = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentTrackName = MutableStateFlow("")
    val currentTrackName: StateFlow<String> = _currentTrackName.asStateFlow()

    private val _currentTrackUrl = MutableStateFlow("")
    val currentTrackUrl: StateFlow<String> = _currentTrackUrl.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _sleepTimerRemaining = MutableStateFlow(0)  // seconds remaining
    val sleepTimerRemaining: StateFlow<Int> = _sleepTimerRemaining.asStateFlow()

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var playlist: List<Pair<String, String>> = emptyList() // (url, name)
    private var currentIndex: Int = -1

    init {
        // Expose starting states
        _isPlaying.value = mediaController.isPlaying
        _currentPosition.value = mediaController.currentPosition.coerceAtLeast(0L)
        _duration.value = mediaController.duration.coerceAtLeast(0L)

        mediaController.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                _duration.value = mediaController.duration.coerceAtLeast(0L)
                
                if (ignoreNextPlayPause) {
                    ignoreNextPlayPause = false
                    return
                }

                // Genuine user action: broadcast state change
                val action = if (isPlaying) "MUSIC_PLAY" else "MUSIC_PAUSE"
                broadcastSyncEvent(action, mediaController.currentPosition)
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    if (ignoreNextSeek) {
                        ignoreNextSeek = false
                        return
                    }
                    // Genuine seek action
                    broadcastSyncEvent("MUSIC_SEEK", newPosition.positionMs)
                }
            }

            override fun onEvents(player: Player, events: Player.Events) {
                _currentPosition.value = player.currentPosition.coerceAtLeast(0L)
                _duration.value = player.duration.coerceAtLeast(0L)
            }
        })

        // Periodically refresh track timeline progress
        scope.launch {
            while (true) {
                if (mediaController.isPlaying) {
                    _currentPosition.value = mediaController.currentPosition.coerceAtLeast(0L)
                }
                kotlinx.coroutines.delay(500)
            }
        }

        // Gather real-time synchronization packets
        scope.launch {
            signalingClient.incomingRawMessages.collect { message ->
                handleRemoteSyncEvent(message)
            }
        }
    }

    private fun broadcastSyncEvent(action: String, position: Long, extraData: String? = null) {
        val payloadObj = JSONObject().apply {
            put("action", action)
            put("position", position)
            put("timestamp", System.currentTimeMillis())
            put("trackName", _currentTrackName.value)
            put("trackUrl", _currentTrackUrl.value)
            if (extraData != null) {
                put("extraData", extraData)
            }
        }
        scope.launch(Dispatchers.IO) {
            val wrap = SignalMessageWrapper(
                type = "MUSIC_SYNC",
                senderId = myId,
                targetId = partnerId,
                payload = payloadObj.toString()
            )
            signalingClient.sendRawMessage(Json.encodeToString(wrap))
        }
    }

    private fun handleRemoteSyncEvent(message: String) {
        try {
            val wrap = LenientJson.decodeFromString<SignalMessageWrapper>(message)
            if (wrap.type != "MUSIC_SYNC" || wrap.payload == null) return

            val json = JSONObject(wrap.payload)
            val action = json.getString("action")
            val remotePosition = json.getLong("position")
            val remoteTimestamp = json.getLong("timestamp")
            if (json.has("trackName")) {
                _currentTrackName.value = json.getString("trackName")
            }
            if (json.has("trackUrl")) {
                val remoteUrl = json.getString("trackUrl")
                if (remoteUrl.isNotEmpty() && remoteUrl != _currentTrackUrl.value) {
                    _currentTrackUrl.value = remoteUrl
                    ignoreNextPlayPause = true
                    ignoreNextSeek = true
                    mediaController.setMediaItem(MediaItem.fromUri(remoteUrl))
                    mediaController.prepare()
                }
            }

            // Drift Compensation Interpolation
            val latency = (System.currentTimeMillis() - remoteTimestamp).coerceIn(0L, 2500L)
            val truePosition = remotePosition + latency

            when (action) {
                "MUSIC_SHUFFLE" -> {
                    val extraData = json.optString("extraData")
                    val shuffleOn = extraData.contains("shuffle=true")
                    _shuffleEnabled.value = shuffleOn
                }
                "MUSIC_PLAY" -> {
                    val alreadyPlaying = mediaController.isPlaying
                    val positionClose = Math.abs(mediaController.currentPosition - truePosition) < 1500
                    if (alreadyPlaying && positionClose) {
                        // Echo deflection: already running matching state
                        return
                    }
                    ignoreNextSeek = true
                    if (!alreadyPlaying) {
                        ignoreNextPlayPause = true
                    }
                    mediaController.seekTo(truePosition.coerceAtLeast(0L))
                    if (!alreadyPlaying) {
                        mediaController.play()
                    }
                }
                "MUSIC_PAUSE" -> {
                    if (!mediaController.isPlaying) {
                        // Echo deflection: already paused
                        return
                    }
                    ignoreNextPlayPause = true
                    mediaController.pause()
                }
                "MUSIC_SEEK" -> {
                    val positionClose = Math.abs(mediaController.currentPosition - truePosition) < 1500
                    if (positionClose) {
                        // Echo deflection: already seeked to similar playhead position
                        return
                    }
                    ignoreNextSeek = true
                    mediaController.seekTo(truePosition.coerceAtLeast(0L))
                }
            }
        } catch (e: Exception) {
            // Safe ignore serialization misses from WebRTC or standard text signaling frames
        }
    }

    fun setPlaylist(songs: List<Pair<String, String>>) {
        playlist = songs
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        currentIndex = if (shuffleEnabled.value) {
            (playlist.indices - currentIndex).randomOrNull() ?: 0
        } else {
            if (currentIndex == -1) 0 else (currentIndex + 1) % playlist.size
        }
        if (currentIndex in playlist.indices) {
            val (url, name) = playlist[currentIndex]
            playTrack(url, name)
        }
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        currentIndex = if (currentIndex <= 0) playlist.size - 1 else currentIndex - 1
        if (currentIndex in playlist.indices) {
            val (url, name) = playlist[currentIndex]
            playTrack(url, name)
        }
    }

    fun toggleShuffle() {
        val next = !_shuffleEnabled.value
        _shuffleEnabled.value = next
        broadcastSyncEvent("MUSIC_SHUFFLE", mediaController.currentPosition, extraData = "shuffle=$next")
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes == 0) {
            _sleepTimerRemaining.value = 0
            return
        }
        _sleepTimerRemaining.value = minutes * 60
        sleepTimerJob = scope.launch {
            while (_sleepTimerRemaining.value > 0) {
                kotlinx.coroutines.delay(1000)
                _sleepTimerRemaining.value -= 1
            }
            mediaController.pause()
        }
    }

    /** Cancels all coroutines and releases resources. Call when the controller is discarded. */
    fun destroy() {
        scope.cancel()
    }

    fun playTrack(url: String, name: String) {
        _currentTrackUrl.value = url
        _currentTrackName.value = name
        // Try to update current index based on url
        currentIndex = playlist.indexOfFirst { it.first == url }
        ignoreNextPlayPause = true
        ignoreNextSeek = true
        mediaController.setMediaItem(MediaItem.fromUri(url))
        mediaController.prepare()
        mediaController.play()
        broadcastSyncEvent("MUSIC_PLAY", 0L)
    }

    fun togglePlayPause() {
        if (mediaController.isPlaying) {
            mediaController.pause()
        } else {
            mediaController.play()
        }
    }

    fun seekTo(position: Long) {
        mediaController.seekTo(position)
    }
}
