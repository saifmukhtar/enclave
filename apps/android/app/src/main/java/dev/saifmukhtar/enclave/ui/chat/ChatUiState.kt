package dev.saifmukhtar.enclave.ui.chat

import kotlinx.serialization.Serializable
import java.util.UUID

sealed class ChatUiState {
    object Connecting : ChatUiState()
    object Handshaking : ChatUiState()
    object WaitingForPartner : ChatUiState()
    object Secured : ChatUiState()
    data class Error(val message: String) : ChatUiState()
}

@Serializable
data class ReplyPayload(
    val body: String,
    val quotedMsgId: String,
    val quotedMsgText: String,
    val quotedMsgSender: String
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isFromMe: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: String = "SENT",
    val disappearingDuration: Long = 0L,
    val expiresAt: Long = 0L,
    val reaction: String = "",
    val messageType: String = "TEXT",
    val quotedMsgId: String? = null,
    val quotedMsgText: String? = null,
    val quotedMsgSender: String? = null
)
