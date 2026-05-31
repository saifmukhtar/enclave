package dev.saifmukhtar.enclave.ui.kiss.transport

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

/**
 * Supabase Realtime Broadcast transport.
 *
 * Uses ephemeral low-latency channel messages (no DB writes) and carries
 * timestamp compensation fields inside the payload envelope.
 */
class SupabaseBroadcastTransport(
    private val supabase: SupabaseClient,
    private val myId: String,
    private val partnerId: String,
    private val roomId: String,
    private val eventName: String = "kiss_event"
) : KissTransport {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _state = MutableStateFlow(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _incomingEvents = MutableSharedFlow<KissTransportEnvelope>(
        replay = 0,
        extraBufferCapacity = 256
    )
    override val incomingEvents: Flow<KissTransportEnvelope> = _incomingEvents.asSharedFlow()

    private val channelName: String = "room_lovers_sync:$roomId"
    private var subscribed = false

    private val channel = supabase.channel(channelName)

    override suspend fun connect() {
        if (subscribed) return
        _state.value = TransportState.CONNECTING

        // Register listener before subscribe so we don't miss early frames.
        scope.launch {
            channel.broadcastFlow<BroadcastEnvelope>(event = eventName).collectLatest { msg ->
                val envelope = msg.envelope
                if (envelope.senderId == myId) return@collectLatest
                if (envelope.targetId != myId && envelope.targetId != partnerId) return@collectLatest
                if (envelope.isExpired()) return@collectLatest
                _incomingEvents.tryEmit(envelope)
            }
        }

        channel.subscribe(blockUntilSubscribed = true)
        subscribed = true
        _state.value = TransportState.CONNECTED
    }

    override suspend fun disconnect() {
        runCatching {
            supabase.realtime.removeChannel(channel)
        }
        subscribed = false
        _state.value = TransportState.DISCONNECTED
        scope.cancel()
    }

    override suspend fun send(envelope: KissTransportEnvelope) {
        // Ensure scheduled playback target exists so receivers can compensate uniformly.
        val normalized = if (envelope.scheduledStartAtMs == null) {
            envelope.copy(scheduledStartAtMs = envelope.clientSentAtMs + envelope.compensationWindowMs)
        } else {
            envelope
        }

        channel.broadcast(
            event = eventName,
            message = BroadcastEnvelope(normalized)
        )
    }
}

@Serializable
private data class BroadcastEnvelope(
    val envelope: KissTransportEnvelope
)
