package com.enclave.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.enclave.app.BuildConfig
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MIGRATION_7_8
import com.enclave.app.data.local.MIGRATION_8_9
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.data.vault.EncryptedFileManager
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.withTimeoutOrNull
import org.signal.libsignal.protocol.SignalProtocolAddress

class EnclaveSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val CHANNEL_ID = "enclave_secure_sync"
        private const val NOTIFICATION_ID = 9999
    }

    override suspend fun doWork(): Result {
        Log.d("EnclaveSyncWorker", "Expedited background sync worker started!")

        // Set to foreground to comply with Android background runtime boundaries
        try {
            setForeground(getForegroundInfo())
        } catch (e: Exception) {
            Log.e("EnclaveSyncWorker", "Failed to set worker to foreground", e)
        }

        val cryptoManager = CryptoManager(applicationContext)
        val prefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val myId = prefs.getString("my_id", "11111111-1111-1111-1111-111111111111") ?: "11111111-1111-1111-1111-111111111111"
        val partnerId = prefs.getString("partner_id", "")?.let { if (it.isBlank()) null else it } ?: if (myId == "11111111-1111-1111-1111-111111111111") {
            "00000000-0000-0000-0000-000000000000"
        } else {
            "11111111-1111-1111-1111-111111111111"
        }
        val partnerAddress = SignalProtocolAddress(partnerId, 1)

        val database = Room.databaseBuilder(
            applicationContext,
            EnclaveDatabase::class.java,
            "enclave_db"
        )
        .addMigrations(MIGRATION_7_8, MIGRATION_8_9)
        .fallbackToDestructiveMigration()
        .build()

        val encryptedFileManager = EncryptedFileManager(applicationContext)
        val vaultRepository = VaultRepository(encryptedFileManager, database.mediaMetadataDao())

        val signalingClient = SignalingClient(BuildConfig.SIGNALING_SERVER_URL, myId)
        signalingClient.connect()

        try {
            withTimeoutOrNull<Unit>(5000) {
                signalingClient.incomingSignalPayloads.collect { payload ->
                    val decryptionResult = cryptoManager.decryptMessage(partnerAddress, payload.ciphertext)
                    if (decryptionResult.isSuccess) {
                        val decryptedBytes = decryptionResult.getOrThrow()
                        val messageId = payload.messageId ?: java.util.UUID.randomUUID().toString()

                        var parsedDuration = 0L
                        val parts = payload.contentType.split(";")
                        val baseType = parts.firstOrNull() ?: "TEXT"
                        parts.forEach { part ->
                            if (part.startsWith("expire=")) {
                                parsedDuration = part.substringAfter("expire=").toLongOrNull() ?: 0L
                            }
                        }

                        if (baseType == "REACTION") {
                            try {
                                val reactionJson = org.json.JSONObject(String(decryptedBytes, Charsets.UTF_8))
                                val targetMsgId = reactionJson.optString("messageId")
                                val emoji = reactionJson.optString("emoji")
                                if (targetMsgId.isNotEmpty()) {
                                    database.messageDao().updateMessageReaction(targetMsgId, emoji)
                                }
                            } catch (e: Exception) {
                                Log.e("EnclaveSyncWorker", "Failed to parse reaction JSON", e)
                            }
                            return@collect
                        }

                        val localEncrypted = if (baseType == "MEDIA" || baseType == "VOICE") {
                            val fileName = if (baseType == "MEDIA") {
                                "received_${System.currentTimeMillis()}.jpg"
                            } else {
                                "received_${System.currentTimeMillis()}.m4a"
                            }
                            vaultRepository.saveSecureFile(fileName, decryptedBytes)
                            cryptoManager.encryptLocal(fileName.toByteArray(Charsets.UTF_8))
                        } else {
                            cryptoManager.encryptLocal(decryptedBytes)
                        }

                        val plaintext = when (baseType) {
                            "MEDIA" -> "🔒 Encrypted Image Received"
                            "VOICE" -> "🎤 Voice Memo"
                            else -> String(decryptedBytes, Charsets.UTF_8)
                        }

                        val entity = MessageEntity(
                            id = messageId,
                            senderId = partnerId,
                            receiverId = myId,
                            encryptedPayload = localEncrypted,
                            timestamp = System.currentTimeMillis(),
                            isRead = false,
                            messageType = baseType,
                            deliveryStatus = "SENT",
                            disappearingDuration = parsedDuration,
                            expiresAt = 0L
                        )
                        database.messageDao().insertMessage(entity)
                        signalingClient.sendDeliveryReceipt(partnerId, messageId)

                        showDecryptedNotification(plaintext)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("EnclaveSyncWorker", "Sync failed", e)
            return Result.retry()
        } finally {
            signalingClient.close()
            database.close()
        }

        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Enclave Secure Sync")
            .setContentText("Fetching secure updates in background...")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setColor(0xFFFCE2E6.toInt()) // Blush Soft Pink Accent Styling
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    private fun showDecryptedNotification(text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("🔒 Secure Message Received")
            .setContentText(if (text.contains("🔒 Encrypted Image")) "Encrypted Image Received" else text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(0xFFFCE2E6.toInt()) // Blush Soft Pink Accent Styling
            .setAutoCancel(true)
            .build()

        manager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Enclave Secure Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Zero-Knowledge Enclave background fetch channel"
            }
            manager.createNotificationChannel(channel)
        }
    }
}
