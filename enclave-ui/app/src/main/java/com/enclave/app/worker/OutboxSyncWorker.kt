package com.enclave.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.enclave.app.BuildConfig
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.webrtc.SignalingClient
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

        // BUG-8 Fix: Use BuildConfig server URL (not a SharedPreferences fallback that was never written)
        // BUG-15 pattern: initialize Supabase and wait for session to provide auth token
        val supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth)
            install(Postgrest)
        }
        supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }
        val token = supabase.auth.currentSessionOrNull()?.accessToken

        val signalingClient = SignalingClient(
            url = BuildConfig.SIGNALING_SERVER_URL,
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
            signalingClient.destroy()
            return Result.retry()
        }

        for (msg in pending) {
            try {
                if (msg.type == "SIGNAL_PAYLOAD") {
                    val ciphertext = Base64.decode(msg.payload, Base64.NO_WRAP)
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
