package com.enclave.app.ui.kiss

import android.app.Application
import android.content.Context
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.ui.kiss.transport.KissTransport
import com.enclave.app.ui.kiss.transport.KissTransportEnvelope
import com.enclave.app.ui.kiss.transport.KissTouchPayload
import com.enclave.app.ui.kiss.transport.SupabaseBroadcastTransport
import com.enclave.app.ui.kiss.transport.TransportState
import io.github.jan.supabase.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Expert Android 14+ intimate haptic communication ViewModel.
 * Manages low-latency real-time ephemeral data sync using the KissTransport strategy pattern
 * and coordinates high-precision synchronized playback of lips impression and vibration waveforms.
 */
class KissViewModel(
    application: Application,
    val transport: KissTransport,
    val myId: String,
    val partnerId: String
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Local touch state representing our current live lips impression
    private val _localPayload = MutableStateFlow<KissTouchPayload?>(null)
    val localPayload: StateFlow<KissTouchPayload?> = _localPayload.asStateFlow()

    // Remote touch state received from the partner
    private val _remotePayload = MutableStateFlow<KissTouchPayload?>(null)
    val remotePayload: StateFlow<KissTouchPayload?> = _remotePayload.asStateFlow()

    // Frozen visual impressions (to render the soft stamp floating/fading)
    private val _sentImpression = MutableStateFlow<KissTouchPayload?>(null)
    val sentImpression: StateFlow<KissTouchPayload?> = _sentImpression.asStateFlow()

    private val _receivedImpression = MutableStateFlow<KissTouchPayload?>(null)
    val receivedImpression: StateFlow<KissTouchPayload?> = _receivedImpression.asStateFlow()

    private val sessionId = UUID.randomUUID().toString()
    private var sequenceCounter = 0L

    init {
        viewModelScope.launch {
            Log.d("KissViewModel", "Connecting to KissTransport...")
            transport.connect()
        }
    }

    /**
     * Packs the raw touch metrics (centroid, normalized pressure, surface area)
     * into a latency-compensated envelope and broadcasts it instantly.
     */
    fun sendKissImpression(
        size: Float,
        pressure: Float,
        x: Float,
        y: Float,
        isTouching: Boolean = true,
        touchMajor: Float = 0f,
        touchMinor: Float = 0f,
        orientation: Float = 0f
    ) {
        val payload = KissTouchPayload(
            xPct = x,
            yPct = y,
            pressure = pressure,
            touchSize = size,
            isTouching = isTouching,
            touchMajor = touchMajor,
            touchMinor = touchMinor,
            orientation = orientation
        )

        _localPayload.value = if (isTouching) payload else null

        // Handle fading of visual sent impression on release
        if (!isTouching && _localPayload.value != null) {
            _sentImpression.value = _localPayload.value
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _sentImpression.value = null
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val envelope = KissTransportEnvelope(
                    eventType = "kiss_event",
                    senderId = myId,
                    targetId = partnerId,
                    sessionId = sessionId,
                    sequence = sequenceCounter++,
                    clientSentAtMs = System.currentTimeMillis(),
                    payload = payload
                )
                transport.send(envelope)
            } catch (e: Exception) {
                Log.e("KissViewModel", "Failed to broadcast kiss impression", e)
            }
        }
    }

    /**
     * Safe fallback helper to retrieve the system Vibrator.
     */
    val vibrator by lazy {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    }

    fun updateRemotePayload(payload: KissTouchPayload?) {
        val prev = _remotePayload.value
        _remotePayload.value = payload

        if (prev != null && prev.isTouching && (payload == null || !payload.isTouching)) {
            _receivedImpression.value = prev
            viewModelScope.launch {
                kotlinx.coroutines.delay(3000)
                _receivedImpression.value = null
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            Log.d("KissViewModel", "Disconnecting KissTransport...")
            transport.disconnect()
        }
    }

    companion object {
        /**
         * Factory method to default-construct KissViewModel with SupabaseBroadcastTransport.
         */
        fun createDefault(
            application: Application,
            supabase: SupabaseClient,
            myId: String,
            partnerId: String,
            roomId: String
        ): KissViewModel {
            val transport = SupabaseBroadcastTransport(
                supabase = supabase,
                myId = myId,
                partnerId = partnerId,
                roomId = roomId
            )
            return KissViewModel(application, transport, myId, partnerId)
        }
    }
}
