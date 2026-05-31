package dev.saifmukhtar.enclave.ui.kiss.transport

import kotlinx.coroutines.flow.Flow

/**
 * Transport abstraction for live kiss/touch payload streaming.
 * Implementations may use existing signaling WS or Supabase Realtime Broadcast.
 */
interface KissTransport {
    val state: Flow<TransportState>
    val incomingEvents: Flow<KissTransportEnvelope>

    suspend fun connect()
    suspend fun disconnect()

    suspend fun send(envelope: KissTransportEnvelope)
}

enum class TransportState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED
}

/**
 * Payload envelope used across transports.
 *
 * Time sync fields:
 * - clientSentAtMs: sender wall-clock time when event was emitted.
 * - compensationWindowMs: allowed clock/latency adjustment window.
 * - scheduledStartAtMs: optional target wall-clock for synchronized playback.
 */
@kotlinx.serialization.Serializable
data class KissTransportEnvelope(
    val version: Int = 1,
    val eventType: String,
    val senderId: String,
    val targetId: String,
    val sessionId: String,
    val sequence: Long,
    val clientSentAtMs: Long,
    val compensationWindowMs: Long = DEFAULT_COMPENSATION_WINDOW_MS,
    val scheduledStartAtMs: Long? = null,
    val payload: KissTouchPayload
) {
    companion object {
        const val DEFAULT_COMPENSATION_WINDOW_MS: Long = 160L
    }

    /**
     * Computes how long receiver should wait before applying haptics.
     * Positive value means delay; 0 means apply immediately.
     */
    fun computePlaybackDelayMs(nowMs: Long = System.currentTimeMillis()): Long {
        val scheduled = scheduledStartAtMs ?: (clientSentAtMs + compensationWindowMs)
        return (scheduled - nowMs).coerceIn(0L, compensationWindowMs)
    }

    /**
     * True when event is too old and should be dropped.
     */
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean {
        val hardDeadline = (scheduledStartAtMs ?: clientSentAtMs) + (compensationWindowMs * 2)
        return nowMs > hardDeadline
    }
}

@kotlinx.serialization.Serializable
data class KissTouchPayload(
    val xPct: Float,
    val yPct: Float,
    val pressure: Float,
    val touchSize: Float,
    val isTouching: Boolean,
    val touchMajor: Float = 0f,
    val touchMinor: Float = 0f,
    val orientation: Float = 0f
)
