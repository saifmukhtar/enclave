@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
package dev.saifmukhtar.enclave.webrtc

import android.util.Base64
import android.util.Log
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.min

/**
 * Lenient Json parser — ignores any extra fields the server adds (e.g. `serverTs`).
 * Using strict Json.decodeFromString would throw on every incoming message
 * because the server stamps all messages with fields not in our data classes.
 */
val LenientJson = Json {
    ignoreUnknownKeys = true
    isLenient         = true
    coerceInputValues = true
}

object PayloadSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Payload", PrimitiveKind.STRING).nullable

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return when {
                element is JsonNull -> null
                element is JsonPrimitive && element.isString -> element.content
                else -> element.toString()
            }
        }
        return if (decoder.decodeNotNullMark()) decoder.decodeString() else null
    }
}

@Serializable
data class SignalMessageWrapper(
    val type: String,
    val senderId: String? = null,
    val targetId: String? = null,
    @Serializable(with = PayloadSerializer::class)
    val payload: String? = null,
    val contentType: String? = null,
    val messageId: String? = null
)

data class EncryptedSignalPayload(
    val ciphertext: ByteArray,
    val contentType: String,
    val messageId: String? = null
)

class SignalingClient(
    private val url: String, 
    private val myId: String,
    private val tokenProvider: (suspend () -> String?)? = null
) {

    // The HttpClient is long-lived and NOT closed on pause — only on full destroy.
    // Closing it on every app background caused reconnection failures (1006 disconnect).
    private val client = HttpClient(OkHttp) {
        install(WebSockets) {
            pingInterval = 20_000L // 20 seconds keep-alive
        }
        engine {
            config {
                val parsedHost = try {
                    java.net.URI(url).host
                } catch (e: Exception) {
                    null
                }
                if (parsedHost != null && !parsedHost.replace(".", "").all { it.isDigit() } && parsedHost != "localhost") {
                    val pinner = okhttp3.CertificatePinner.Builder()
                        .add("*.$parsedHost", "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                        .add(parsedHost, "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=")
                        .build()
                    certificatePinner(pinner)
                }
            }
        }
    }

    private val pendingMessagesQueue = java.util.concurrent.ConcurrentLinkedQueue<SignalMessageWrapper>()
 
    private fun queuePendingMessage(msg: SignalMessageWrapper) {
        if (pendingMessagesQueue.size >= 200) {
            val dropped = pendingMessagesQueue.poll()
            Log.w("SignalingClient", "Pending queue capacity exceeded (200 limit). Dropping oldest message of type: ${dropped?.type}")
        }
        pendingMessagesQueue.add(msg)
    }

    private val _incomingSignalPayloads = MutableSharedFlow<EncryptedSignalPayload>(replay = 100, extraBufferCapacity = 100)
    val incomingSignalPayloads: SharedFlow<EncryptedSignalPayload> = _incomingSignalPayloads.asSharedFlow()

    private val _incomingWebRtcMessages = MutableSharedFlow<SignalMessageWrapper>(replay = 100, extraBufferCapacity = 100)
    val incomingWebRtcMessages: SharedFlow<SignalMessageWrapper> = _incomingWebRtcMessages.asSharedFlow()

    private val _incomingRawMessages = MutableSharedFlow<String>(replay = 100, extraBufferCapacity = 100)
    val incomingRawMessages: SharedFlow<String> = _incomingRawMessages.asSharedFlow()

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var job: Job? = null

    enum class ConnectionState {
        CONNECTING, CONNECTED, DISCONNECTED
    }

    fun connect() {
        if (job?.isActive == true) return
        job = scope.launch {
            var currentBackoff = 1000L
            val maxBackoff = 30_000L

            while (isActive) {
                try {
                    _connectionState.value = ConnectionState.CONNECTING
                    Log.d("SignalingClient", "Connecting to WebSocket: $url with myId=$myId")
                    
                    val token = tokenProvider?.invoke()
                    client.webSocket(
                        urlString = url,
                        request = {
                            if (token != null) {
                                headers.append(HttpHeaders.Authorization, "Bearer $token")
                            }
                        }
                    ) {
                        session = this
                        _connectionState.value = ConnectionState.CONNECTED
                        Log.d("SignalingClient", "WebSocket Connected for myId=$myId")
                        currentBackoff = 1000L // Reset backoff on success

                        // Register immediately
                        val registerMsg = SignalMessageWrapper(type = "REGISTER", senderId = myId)
                        Log.d("SignalingClient", "Sending REGISTER: $registerMsg")
                        send(Frame.Text(Json.encodeToString(registerMsg)))

                        // Flush client-side pending queue!
                        while (isActive && _connectionState.value == ConnectionState.CONNECTED) {
                            val msg = pendingMessagesQueue.poll() ?: break
                            try {
                                Log.d("SignalingClient", "Flushing queued pending message: ${msg.type}")
                                send(Frame.Text(Json.encodeToString(msg)))
                            } catch (e: Exception) {
                                pendingMessagesQueue.add(msg)
                                break
                            }
                        }

                        // Listen for incoming messages
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                _incomingRawMessages.tryEmit(text)
                                try {
                                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(text)
                                    if (msg.type == "PING") {
                                        val pongMsg = SignalMessageWrapper(
                                            type = "PONG",
                                            senderId = myId,
                                            payload = msg.payload
                                        )
                                        sendRawMessage(Json.encodeToString(pongMsg))
                                    } else if (msg.type == "SIGNAL_PAYLOAD" && msg.payload != null) {
                                        val ciphertextBytes = Base64.decode(msg.payload, Base64.NO_WRAP)
                                        val contentType = msg.contentType ?: "TEXT"
                                        _incomingSignalPayloads.tryEmit(
                                            EncryptedSignalPayload(ciphertextBytes, contentType, msg.messageId)
                                        )
                                    } else if (
                                         msg.type == "WEBRTC_OFFER"   || msg.type == "OFFER"   ||
                                         msg.type == "WEBRTC_ANSWER"  || msg.type == "ANSWER"  ||
                                         msg.type == "ICE_CANDIDATE"  ||
                                         msg.type == "WEBRTC_HANGUP"  ||
                                         msg.type == "DELIVERY_STATUS"||
                                         msg.type == "WEBRTC_RINGING"
                                     ) {
                                         // Route all WebRTC signaling messages (including HANGUP, DELIVERY_STATUS and RINGING)
                                         _incomingWebRtcMessages.tryEmit(msg)
                                     }
                                } catch (e: Exception) {
                                    Log.e("SignalingClient", "Failed to parse message", e)
                                }
                            }
                        }
                        val reason = closeReason.await()
                        Log.d("SignalingClient", "WebSocket session closed by server for myId=$myId. Reason: $reason")
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Log.e("SignalingClient", "WebSocket connection error", e)
                } finally {
                    session = null
                    _connectionState.value = ConnectionState.DISCONNECTED
                    Log.d("SignalingClient", "WebSocket Disconnected. Reconnecting in ${currentBackoff}ms...")
                    delay(currentBackoff)
                    currentBackoff = min(currentBackoff * 2, maxBackoff) // Exponential backoff
                }
            }
        }
    }

    suspend fun sendEncryptedMessage(targetId: String, ciphertext: ByteArray, contentType: String = "TEXT", messageId: String? = null) {
        val payloadBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        val msg = SignalMessageWrapper(
            type = "SIGNAL_PAYLOAD",
            senderId = myId,
            targetId = targetId,
            payload = payloadBase64,
            contentType = contentType,
            messageId = messageId
        )
        if (isConnected() && session != null) {
            try {
                session?.send(Frame.Text(Json.encodeToString(msg)))
            } catch (e: Exception) {
                Log.w("SignalingClient", "Failed to send, queueing message: ${e.message}")
                queuePendingMessage(msg)
            }
        } else {
            Log.d("SignalingClient", "Session is offline, queueing encrypted message")
            queuePendingMessage(msg)
        }
    }

    suspend fun sendWebRtcMessage(targetId: String, type: String, payload: String) {
        val msg = SignalMessageWrapper(
            type = type,
            senderId = myId,
            targetId = targetId,
            payload = payload
        )
        val frameText = Json.encodeToString(msg)
        if (isConnected() && session != null) {
            try {
                session?.send(Frame.Text(frameText))
            } catch (e: Exception) {
                Log.w("SignalingClient", "Failed to send WebRTC message $type, queueing: ${e.message}")
                queuePendingMessage(msg)
            }
        } else {
            Log.d("SignalingClient", "Session offline, queueing WebRTC message: $type")
            queuePendingMessage(msg)
        }
    }

    suspend fun sendRawMessage(message: String) {
        session?.send(Frame.Text(message)) ?: Log.w("SignalingClient", "Cannot send raw, session is null")
    }

    suspend fun sendTypingStatus(targetId: String, isTyping: Boolean) {
        val msg = SignalMessageWrapper(
            type = "TYPING_STATUS",
            senderId = myId,
            targetId = targetId,
            payload = isTyping.toString()
        )
        sendRawMessage(Json.encodeToString(msg))
    }

    suspend fun sendReadReceipt(targetId: String, messageId: String) {
        val msg = SignalMessageWrapper(
            type = "READ_RECEIPT",
            senderId = myId,
            targetId = targetId,
            payload = messageId
        )
        sendRawMessage(Json.encodeToString(msg))
    }

    suspend fun sendDeliveryReceipt(targetId: String, messageId: String) {
        val msg = SignalMessageWrapper(
            type = "DELIVERY_RECEIPT",
            senderId = myId,
            targetId = targetId,
            payload = messageId
        )
        sendRawMessage(Json.encodeToString(msg))
    }

    fun isConnected(): Boolean = _connectionState.value == ConnectionState.CONNECTED

    /**
     * Pauses the WebSocket connection (e.g. app goes to background).
     * Does NOT close the underlying HttpClient so reconnection works on foreground return.
     */
    fun close() {
        job?.cancel()
        job = null
        scope.launch {
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "App backgrounded"))
            } catch (_: Exception) {}
            session = null
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    /**
     * Full teardown — call only when the SignalingClient will never be used again
     * (e.g. composable permanently disposed). Closes the underlying HttpClient.
     */
    fun destroy() {
        job?.cancel()
        job = null
        scope.launch {
            try {
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Client destroyed"))
            } catch (_: Exception) {}
            session = null
            _connectionState.value = ConnectionState.DISCONNECTED
            try { client.close() } catch (_: Exception) {}
        }
    }

    fun emitDecryptedRawMessage(text: String) {
        _incomingRawMessages.tryEmit(text)
    }
}
