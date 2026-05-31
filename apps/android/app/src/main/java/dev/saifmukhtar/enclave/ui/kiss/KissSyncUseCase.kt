package dev.saifmukhtar.enclave.ui.kiss

import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import dev.saifmukhtar.enclave.webrtc.LenientJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.ArrayDeque

class KissSyncUseCase(
    private val signalingClient: SignalingClient,
    private val coroutineScope: CoroutineScope
) {
    private val outboundQueue = ArrayDeque<SignalMessageWrapper>()
    private val maxQueueSize = 100

    init {
        coroutineScope.launch {
            signalingClient.connectionState.collect { state ->
                if (state == SignalingClient.ConnectionState.CONNECTED) {
                    flushQueuedMessages()
                }
            }
        }
    }

    val incomingMessages: Flow<SignalMessageWrapper> = signalingClient.incomingRawMessages.mapNotNull { message ->
        try {
            LenientJson.decodeFromString<SignalMessageWrapper>(message)
        } catch (_: Exception) {
            null
        }
    }

    suspend fun sendKissMessageReliably(message: SignalMessageWrapper) {
        if (!signalingClient.isConnected()) {
            enqueueMessage(message)
            return
        }
        try {
            signalingClient.sendRawMessage(Json.encodeToString(message))
        } catch (_: Exception) {
            enqueueMessage(message)
        }
    }

    private fun enqueueMessage(message: SignalMessageWrapper) {
        synchronized(outboundQueue) {
            // Coalesce high-frequency frame updates: keep only latest live impression snapshot.
            if (message.type == "KISS_LIP_IMPRESSION") {
                outboundQueue.removeAll { it.type == "KISS_LIP_IMPRESSION" }
            }
            if (outboundQueue.size >= maxQueueSize) {
                outboundQueue.removeFirst()
            }
            outboundQueue.addLast(message)
        }
    }

    private suspend fun flushQueuedMessages() {
        while (signalingClient.isConnected()) {
            val next = synchronized(outboundQueue) {
                if (outboundQueue.isEmpty()) null else outboundQueue.removeFirst()
            } ?: break
            try {
                signalingClient.sendRawMessage(Json.encodeToString(next))
            } catch (_: Exception) {
                enqueueMessage(next)
                break
            }
        }
    }
}
