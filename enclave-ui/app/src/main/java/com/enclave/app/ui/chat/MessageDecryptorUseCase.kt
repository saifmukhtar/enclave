package com.enclave.app.ui.chat

import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.webrtc.LenientJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.concurrent.ConcurrentHashMap

class MessageDecryptorUseCase(
    private val database: EnclaveDatabase,
    private val cryptoManager: CryptoManager,
    private val myId: String
) {
    private val decryptedMessagesCache = ConcurrentHashMap<String, String>()

    fun getDecryptedText(entity: MessageEntity): String {
        return decryptedMessagesCache.getOrPut(entity.id) {
            try {
                val decryptedBytes = cryptoManager.decryptLocal(entity.encryptedPayload)
                when (entity.messageType) {
                    "MEDIA", "MEDIA_IMAGE" -> "📸 Photo"
                    "MEDIA_VIDEO" -> "🎥 Video"
                    "MEDIA_AUDIO" -> "🎵 Audio File"
                    "VOICE" -> "🎤 Voice Memo"
                    "RECORDED_KISS" -> "Kiss Impression"
                    else -> String(decryptedBytes, Charsets.UTF_8)
                }
            } catch (e: Exception) {
                "🔒 Decryption failed"
            }
        }
    }

    fun getDecryptedMessagesFlow(): Flow<List<ChatMessage>> {
        return database.messageDao().getAllMessages()
            .map { entities ->
                entities.map { entity ->
                    val decryptedText = getDecryptedText(entity)

                    // High-performance background parsing of quoted JSON to prevent 60fps UI stuttering
                    var parsedText = decryptedText
                    var qId: String? = null
                    var qText: String? = null
                    var qSender: String? = null

                    if (decryptedText.startsWith("{\"body\":")) {
                        try {
                            val reply = LenientJson.decodeFromString<ReplyPayload>(decryptedText)
                            parsedText = reply.body
                            qId = reply.quotedMsgId
                            qText = reply.quotedMsgText
                            qSender = reply.quotedMsgSender
                        } catch (e: Exception) {
                            // Not a valid JSON payload, keep standard plaintext
                        }
                    }

                    ChatMessage(
                        id = entity.id,
                        text = parsedText,
                        isFromMe = entity.senderId == myId,
                        timestamp = entity.timestamp,
                        deliveryStatus = entity.deliveryStatus,
                        disappearingDuration = entity.disappearingDuration,
                        expiresAt = entity.expiresAt,
                        reaction = entity.reaction,
                        messageType = entity.messageType,
                        quotedMsgId = qId,
                        quotedMsgText = qText,
                        quotedMsgSender = qSender
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun getDecryptedMediaMessagesFlow(): Flow<List<ChatMessage>> {
        return database.messageDao().getMediaMessages()
            .map { entities ->
                entities.map { entity ->
                    ChatMessage(
                        id = entity.id,
                        text = "Media",
                        isFromMe = entity.senderId == myId,
                        timestamp = entity.timestamp,
                        deliveryStatus = entity.deliveryStatus,
                        disappearingDuration = entity.disappearingDuration,
                        expiresAt = entity.expiresAt,
                        reaction = entity.reaction,
                        messageType = entity.messageType,
                        quotedMsgId = null,
                        quotedMsgText = null,
                        quotedMsgSender = null
                    )
                }
            }
            .flowOn(Dispatchers.IO)
    }

    fun injectCache(messageId: String, plainText: String) {
        decryptedMessagesCache[messageId] = plainText
    }
}
