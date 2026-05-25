package com.enclave.app.ui.chat

import android.content.Context
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.crypto.EnclaveSignalStore
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.SignalProtocolAddress
import java.util.UUID

class MessageSenderUseCase(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val signalStore: EnclaveSignalStore,
    private val signalingClient: SignalingClient,
    private val database: EnclaveDatabase,
    private val myId: String,
    private val partnerId: String
) {
    private val partnerAddress = SignalProtocolAddress(partnerId, 1)

    suspend fun sendMessage(
        text: String,
        replyTarget: ChatMessage?,
        disappearingMode: Long,
        isSecured: Boolean
    ): Pair<String, String>? = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext null
        val hasSession = try { signalStore.containsSession(partnerAddress) } catch (e: Exception) { false }
        if (!hasSession && !isSecured) return@withContext null

        val plainTextToSend = if (replyTarget != null) {
            val payload = ReplyPayload(
                body = text,
                quotedMsgId = replyTarget.id,
                quotedMsgText = replyTarget.text,
                quotedMsgSender = if (replyTarget.isFromMe) "You" else "Partner"
            )
            Json.encodeToString(payload)
        } else {
            text
        }

        val messageBytes = plainTextToSend.toByteArray(Charsets.UTF_8)
        val encryptionResult = cryptoManager.encryptMessage(partnerAddress, messageBytes)

        if (encryptionResult.isSuccess) {
            val ciphertext = encryptionResult.getOrThrow()
            val messageId = UUID.randomUUID().toString()
            
            val currentExpire = disappearingMode
            val contentTypeHeader = if (currentExpire > 0) "TEXT;expire=$currentExpire" else "TEXT"
            
            val isConnected = signalingClient.isConnected()
            if (isConnected) {
                signalingClient.sendEncryptedMessage(partnerId, ciphertext, contentType = contentTypeHeader, messageId = messageId)
            } else {
                val outbox = com.enclave.app.data.local.OutboxEntity(
                    targetId = partnerId,
                    type = "SIGNAL_PAYLOAD",
                    payload = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                    contentType = contentTypeHeader,
                    messageId = messageId
                )
                database.outboxDao().insert(outbox)
                val req = androidx.work.OneTimeWorkRequestBuilder<com.enclave.app.worker.OutboxSyncWorker>()
                    .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                    .build()
                androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("outbox_sync", androidx.work.ExistingWorkPolicy.REPLACE, req)
            }
            
            val localEncrypted = cryptoManager.encryptLocal(messageBytes)
            val entity = MessageEntity(
                id = messageId,
                senderId = myId,
                receiverId = partnerId,
                encryptedPayload = localEncrypted,
                timestamp = System.currentTimeMillis(),
                isRead = true,
                messageType = "TEXT",
                deliveryStatus = if (isConnected) "SENT" else "QUEUED",
                disappearingDuration = currentExpire,
                expiresAt = 0L
            )
            database.messageDao().insertMessage(entity)

            // Return the cached plain text for local cache injection
            Pair(messageId, plainTextToSend)
        } else {
            null
        }
    }

    suspend fun sendTimeCapsuleMessage(text: String, sendAt: Long) = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext
        val messageId = UUID.randomUUID().toString()
        val messageBytes = text.toByteArray(Charsets.UTF_8)
        val localEncrypted = cryptoManager.encryptLocal(messageBytes)
        
        val capsule = com.enclave.app.data.local.TimeCapsuleEntity(
            id = messageId,
            targetId = partnerId,
            payloadText = localEncrypted,
            sendAt = sendAt,
            createdAt = System.currentTimeMillis()
        )
        
        database.timeCapsuleDao().insert(capsule)
        
        val delayMs = sendAt - System.currentTimeMillis()
        val req = androidx.work.OneTimeWorkRequestBuilder<com.enclave.app.worker.TimeCapsuleWorker>()
            .setInputData(androidx.work.workDataOf("CAPSULE_ID" to messageId))
            .setInitialDelay(java.util.concurrent.TimeUnit.MILLISECONDS.toMillis(delayMs), java.util.concurrent.TimeUnit.MILLISECONDS)
            .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
            .build()
        
        androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("capsule_$messageId", androidx.work.ExistingWorkPolicy.REPLACE, req)
    }
}
