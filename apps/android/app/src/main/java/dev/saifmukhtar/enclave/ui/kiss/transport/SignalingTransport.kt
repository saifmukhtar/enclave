package dev.saifmukhtar.enclave.ui.kiss.transport

import android.util.Log
import dev.saifmukhtar.enclave.webrtc.LenientJson
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString

/**
 * Scaffold transport that tunnels kiss payload through existing signaling websocket.
 */
class SignalingTransport(
    private val signalingClient: SignalingClient,
    private val myId: String,
    private val partnerId: String,
    private val kissEventType: String = "KISS_TRANSPORT_EVENT"
) : KissTransport {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<KissTransportEnvelope>(
        replay = 0,
        extraBufferCapacity = 128
    )
    override val incomingEvents: Flow<KissTransportEnvelope> = _incomingEvents.asSharedFlow()

    init {
        scope.launch {
            signalingClient.connectionState.collectLatest { connection ->
                _state.value = when (connection) {
                    SignalingClient.ConnectionState.CONNECTING -> TransportState.CONNECTING
                    SignalingClient.ConnectionState.CONNECTED -> TransportState.CONNECTED
                    SignalingClient.ConnectionState.DISCONNECTED -> TransportState.DISCONNECTED
                }
            }
        }

        scope.launch {
            signalingClient.incomingRawMessages.collectLatest { raw ->
                runCatching {
                    LenientJson.decodeFromString<SignalMessageWrapper>(raw)
                }.onSuccess { wrapper ->
                    if (wrapper.type != kissEventType) return@onSuccess
                    if (wrapper.senderId != partnerId) return@onSuccess
                    val payload = wrapper.payload ?: return@onSuccess

                    runCatching {
                        LenientJson.decodeFromString<KissTransportEnvelope>(payload)
                    }.onSuccess { envelope ->
                        _incomingEvents.tryEmit(envelope)
                    }.onFailure { error ->
                        Log.w("SignalingTransport", "Failed to decode kiss transport payload", error)
                    }
                }
            }
        }
    }

    override suspend fun connect() {
        signalingClient.connect()
    }

    override suspend fun disconnect() {
        signalingClient.close()
    }

    override suspend fun send(envelope: KissTransportEnvelope) {
        val wrapper = SignalMessageWrapper(
            type = kissEventType,
            senderId = myId,
            targetId = partnerId,
            payload = LenientJson.encodeToString(envelope)
        )
        signalingClient.sendRawMessage(LenientJson.encodeToString(wrapper))
    }
}
