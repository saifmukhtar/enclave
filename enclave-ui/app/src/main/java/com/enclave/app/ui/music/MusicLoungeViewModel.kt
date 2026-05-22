package com.enclave.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class MusicConsentState {
    IDLE,
    WAITING_FOR_PARTNER,
    RECEIVING_REQUEST,
    PLAYING
}

class MusicLoungeViewModel(
    private val signalingClient: SignalingClient
) : ViewModel() {

    private val _consentState = MutableStateFlow(MusicConsentState.IDLE)
    val consentState: StateFlow<MusicConsentState> = _consentState.asStateFlow()

    private var pendingTrackUrl: String? = null
    var requestedTrackName: String = ""

    init {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { message ->
                handleIncomingSignal(message)
            }
        }
    }

    private fun handleIncomingSignal(message: String) {
        if (!message.contains("MUSIC_")) return
        val json = JSONObject(message)
        
        when (json.getString("type")) {
            "MUSIC_REQUEST" -> {
                pendingTrackUrl = json.getString("url")
                requestedTrackName = json.getString("name")
                _consentState.value = MusicConsentState.RECEIVING_REQUEST
            }
            "MUSIC_ACCEPT" -> {
                if (_consentState.value == MusicConsentState.WAITING_FOR_PARTNER) {
                    _consentState.value = MusicConsentState.PLAYING
                    // Inform the UI to initialize MediaController and begin playback using pendingTrackUrl
                }
            }
            "MUSIC_REJECT" -> {
                _consentState.value = MusicConsentState.IDLE
                pendingTrackUrl = null
            }
        }
    }

    fun selectTrack(url: String, name: String) {
        pendingTrackUrl = url
        requestedTrackName = name
        _consentState.value = MusicConsentState.WAITING_FOR_PARTNER
        
        val payload = JSONObject().apply {
            put("type", "MUSIC_REQUEST")
            put("url", url)
            put("name", name)
        }
        viewModelScope.launch {
            signalingClient.sendRawMessage(payload.toString())
        }
    }

    fun acceptRequest() {
        _consentState.value = MusicConsentState.PLAYING
        val payload = JSONObject().apply {
            put("type", "MUSIC_ACCEPT")
        }
        viewModelScope.launch {
            signalingClient.sendRawMessage(payload.toString())
        }
        // Inform UI to initialize MediaController with pendingTrackUrl
    }

    fun rejectRequest() {
        _consentState.value = MusicConsentState.IDLE
        pendingTrackUrl = null
        val payload = JSONObject().apply {
            put("type", "MUSIC_REJECT")
        }
        viewModelScope.launch {
            signalingClient.sendRawMessage(payload.toString())
        }
    }

    fun getPendingUrl() = pendingTrackUrl
}
