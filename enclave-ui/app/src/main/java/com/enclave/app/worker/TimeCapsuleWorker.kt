package com.enclave.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enclave.app.data.config.ConfigManager
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.webrtc.SignalingClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.delay
import android.util.Base64
import java.util.UUID
import kotlinx.coroutines.flow.first
import org.signal.libsignal.protocol.SignalProtocolAddress

class TimeCapsuleWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val capsuleId = inputData.getString("CAPSULE_ID") ?: return Result.failure()
        
        val database = EnclaveDatabase.getInstance(applicationContext)
        val capsule = database.timeCapsuleDao().getCapsuleById(capsuleId) ?: return Result.success() // Already sent or deleted

        val prefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val myId = prefs.getString("my_id", null) ?: return Result.failure()

        val configManager = ConfigManager.getInstance(applicationContext)
        val sUrl = configManager.getSupabaseUrl()
        val sKey = configManager.getSupabaseKey()
        if (sUrl.isNullOrBlank() || sKey.isNullOrBlank()) {
            return Result.failure()
        }

        // Initialize Supabase to fetch live auth token for signaling authentication
        val supabase = createSupabaseClient(
            supabaseUrl = sUrl,
            supabaseKey = sKey
        ) {
            install(Auth)
            install(Postgrest)
        }
        supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        val token = supabase.auth.currentSessionOrNull()?.accessToken

        // Wait up to 5 mins if offline? Actually, WorkManager handles constraints like NetworkType.CONNECTED.
        val sigUrl = configManager.getSignalingServerUrl()
        if (sigUrl.isNullOrBlank()) {
            return Result.failure()
        }
        val signalingClient = SignalingClient(
            url = sigUrl,
            myId = myId,
            tokenProvider = { token }
        )

        signalingClient.connect()
        var retries = 0
        while (!signalingClient.isConnected() && retries < 20) {
            delay(500)
            retries++
        }

        if (!signalingClient.isConnected()) {
            signalingClient.destroy()
            return Result.retry() // Retry later if still offline despite constraint
        }

        try {
            // Need crypto manager to encrypt for the recipient NOW
            val cryptoManager = CryptoManager(applicationContext)
            
            // Decrypt local payload
            val plaintextBytes = cryptoManager.decryptLocal(capsule.payloadText)
            
            // Encrypt for Signal Protocol
            val partnerAddress = SignalProtocolAddress(capsule.targetId, 1)
            val encryptionResult = cryptoManager.encryptMessage(partnerAddress, plaintextBytes)
            
            if (encryptionResult.isSuccess) {
                val ciphertext = encryptionResult.getOrThrow()
                
                signalingClient.sendEncryptedMessage(
                    targetId = capsule.targetId,
                    ciphertext = ciphertext,
                    contentType = "TEXT",
                    messageId = capsule.id
                )

                // Move from TimeCapsule to MessageEntity
                val localEncrypted = cryptoManager.encryptLocal(plaintextBytes)
                val entity = MessageEntity(
                    id = capsule.id,
                    senderId = myId,
                    receiverId = capsule.targetId,
                    encryptedPayload = localEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    messageType = "TEXT",
                    deliveryStatus = "SENT",
                    disappearingDuration = 0L,
                    expiresAt = 0L
                )
                database.messageDao().insertMessage(entity)
                
                // Delete the time capsule since it's sent
                database.timeCapsuleDao().deleteById(capsule.id)
            } else {
                // If signal encryption fails (e.g. no session), we can't send it right now.
                // It might need re-handshake, so retry later.
                signalingClient.destroy()
                return Result.retry()
            }
        } catch (e: Exception) {
            signalingClient.destroy()
            return Result.retry()
        }

        delay(1000)
        signalingClient.destroy()
        return Result.success()
    }
}
