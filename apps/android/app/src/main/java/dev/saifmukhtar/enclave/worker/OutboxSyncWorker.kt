package dev.saifmukhtar.enclave.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.saifmukhtar.enclave.data.config.ConfigManager
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.delay
import android.util.Base64
import kotlinx.coroutines.flow.first

class OutboxSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = EnclaveDatabase.getInstance(applicationContext)
        val pending = database.outboxDao().getAllPendingMessages()
        if (pending.isEmpty()) return Result.success()

        val prefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val myId = prefs.getString("my_id", null) ?: return Result.failure()

        val configManager = ConfigManager.getInstance(applicationContext)
        val sUrl = configManager.getSupabaseUrl()
        val sKey = configManager.getSupabaseKey()
        if (sUrl.isNullOrBlank() || sKey.isNullOrBlank()) {
            return Result.failure()
        }

        // BUG-8 Fix: Use BuildConfig server URL (not a SharedPreferences fallback that was never written)
        // BUG-15 pattern: initialize Supabase and wait for session to provide auth token
        val supabase = createSupabaseClient(
            supabaseUrl = sUrl,
            supabaseKey = sKey
        ) {
            install(Auth)
            install(Postgrest)
        }
        supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        val token = supabase.auth.currentSessionOrNull()?.accessToken
        if (token == null) {
            android.util.Log.e("OutboxSyncWorker", "Authentication token is missing or expired. Cannot proceed with sync.")
            return Result.failure()
        }

        val sigUrl = configManager.getSignalingServerUrl()
        if (sigUrl.isNullOrBlank()) {
            android.util.Log.e("OutboxSyncWorker", "Signaling server URL is missing.")
            return Result.failure()
        }
        val signalingClient = SignalingClient(
            url = sigUrl,
            myId = myId,
            tokenProvider = { token }
        )

        signalingClient.connect()
        // Wait up to 10 seconds for connection
        var retries = 0
        while (!signalingClient.isConnected() && retries < 20) {
            delay(500)
            retries++
        }

        if (!signalingClient.isConnected()) {
            android.util.Log.w("OutboxSyncWorker", "Signaling client failed to connect within timeout. Scheduling retry.")
            signalingClient.destroy()
            return Result.retry()
        }

        for (msg in pending) {
            try {
                if (msg.type == "SIGNAL_PAYLOAD") {
                    val ciphertext = try {
                        Base64.decode(msg.payload, Base64.NO_WRAP)
                    } catch (e: IllegalArgumentException) {
                        android.util.Log.e("OutboxSyncWorker", "Corrupted base64 payload in message ${msg.id}. Skipping to prevent retry loop.", e)
                        database.outboxDao().deleteById(msg.id)
                        continue
                    }
                    signalingClient.sendEncryptedMessage(
                        targetId = msg.targetId,
                        ciphertext = ciphertext,
                        contentType = msg.contentType ?: "TEXT",
                        messageId = msg.messageId
                    )
                } else {
                    signalingClient.sendWebRtcMessage(
                        targetId = msg.targetId,
                        type = msg.type,
                        payload = msg.payload
                    )
                }
                
                // Once sent, delete from outbox and update message delivery status
                database.outboxDao().deleteById(msg.id)
                if (msg.messageId != null) {
                    val existing = database.messageDao().getMessageById(msg.messageId)
                    if (existing != null && existing.deliveryStatus == "QUEUED") {
                        database.messageDao().updateDeliveryStatus(msg.messageId, "SENT")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("OutboxSyncWorker", "Transport error sending message ID ${msg.id}. Scheduling worker retry.", e)
                signalingClient.destroy()
                return Result.retry()
            }
        }

        // Keep connection open slightly longer to ensure flush
        delay(2000)
        signalingClient.destroy()
        return Result.success()
    }
}
