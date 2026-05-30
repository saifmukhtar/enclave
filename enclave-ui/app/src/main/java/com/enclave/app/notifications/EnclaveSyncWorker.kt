package com.enclave.app.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.room.Room
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.flow.first
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.enclave.app.data.config.ConfigManager
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MIGRATION_7_8
import com.enclave.app.data.local.MIGRATION_8_9
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.data.vault.EncryptedFileManager
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.network.BundleRepository
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
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
        val myId = prefs.getString("my_id", null)?.takeIf { it.isNotBlank() }
        val partnerId = prefs.getString("partner_id", null)?.takeIf { it.isNotBlank() }

        // Abort sync gracefully if the app has not been fully paired yet.
        // Do NOT substitute fake UUIDs — that causes ghost-user decryption attempts.
        if (myId == null || partnerId == null) {
            Log.d("EnclaveSyncWorker", "App not paired yet (myId=$myId, partnerId=$partnerId). Skipping sync.")
            return Result.success()
        }
        val partnerAddress = SignalProtocolAddress(partnerId, 1)

        val database = EnclaveDatabase.getInstance(applicationContext)

        val encryptedFileManager = EncryptedFileManager(applicationContext)

        val configManager = ConfigManager.getInstance(applicationContext)
        val sUrl = configManager.getSupabaseUrl()
        val sKey = configManager.getSupabaseKey()
        if (sUrl.isNullOrBlank() || sKey.isNullOrBlank()) {
            return Result.failure()
        }

        // Initialize Supabase Auth to fetch cached user session tokens for signaling authentication
        val supabase = createSupabaseClient(
            supabaseUrl = sUrl,
            supabaseKey = sKey
        ) {
            httpEngine = io.ktor.client.engine.okhttp.OkHttp.create {
                 config {
                    connectTimeout(java.time.Duration.ofMinutes(5))
                    readTimeout(java.time.Duration.ofMinutes(5))
                    writeTimeout(java.time.Duration.ofMinutes(5))
                    val parsedHost = try {
                        java.net.URI(sUrl).host
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
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
            
            defaultSerializer = io.github.jan.supabase.serializer.KotlinXSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    isLenient         = true
                    coerceInputValues = true
                }
            )
        }

        // Wait for session status flow to finish initial load of cached session
        supabase.auth.sessionStatus.first { it is SessionStatus.Authenticated || it is SessionStatus.NotAuthenticated }

        val bundleRepository = BundleRepository(supabase, cryptoManager.signalStore, cryptoManager)
        val vaultRepository = VaultRepository(applicationContext, encryptedFileManager, database.mediaMetadataDao(), bundleRepository)

        val sigUrl = configManager.getSignalingServerUrl()
        if (sigUrl.isNullOrBlank()) {
            Log.e("EnclaveSyncWorker", "Sync failed: Signaling server URL is missing.")
            return Result.failure()
        }
        val token = supabase.auth.currentSessionOrNull()?.accessToken
        if (token == null) {
            Log.w("EnclaveSyncWorker", "Sync aborted: Supabase token is null or expired.")
            return Result.failure()
        }
        val signalingClient = SignalingClient(
            url = sigUrl,
            myId = myId,
            tokenProvider = { token }
        )

        try {
            Log.d("EnclaveSyncWorker", "Connecting to signaling server...")
            signalingClient.connect()
            withTimeoutOrNull<Unit>(5000) {
                coroutineScope {
                    launch {
                    signalingClient.incomingRawMessages.collect { raw ->
                        try {
                            val msg = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.webrtc.SignalMessageWrapper>(raw)
                            if (msg.senderId != partnerId) return@collect
                            when (msg.type) {
                                "STORY_SHARE" -> {
                                    msg.payload?.let { payload ->
                                        try {
                                            val story = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.ui.profile.StorySharePayload>(payload)
                                            val encryptedBytes = android.util.Base64.decode(story.encryptedContent, android.util.Base64.NO_WRAP)
                                            val decryptedResult = cryptoManager.decryptMessage(partnerAddress, encryptedBytes)
                                            if (decryptedResult.isSuccess) {
                                                val decryptedBytes = decryptedResult.getOrThrow()
                                                val reEncrypted = cryptoManager.encryptLocal(decryptedBytes)
                                                database.statusStoryDao().upsertStory(
                                                    com.enclave.app.data.local.StatusStoryEntity(
                                                        id = story.storyId,
                                                        authorId = partnerId,
                                                        contentType = story.contentType,
                                                        encryptedPayload = reEncrypted,
                                                        backgroundColor = story.backgroundColor,
                                                        expiresAt = story.expiresAt,
                                                        createdAt = story.createdAt,
                                                        isFromMe = false
                                                    )
                                                )
                                                showDecryptedNotification("New Status Story from Partner")
                                            }
                                            Unit
                                        } catch (e: Exception) {
                                            Log.e("EnclaveSyncWorker", "Error parsing incoming story share", e)
                                        }
                                    }
                                }
                                "STORY_VIEWED" -> {
                                    msg.payload?.let { payload ->
                                        database.statusStoryDao().markViewed(payload, System.currentTimeMillis())
                                    }
                                }
                                "MESSAGE_REVOKE" -> {
                                    msg.payload?.let { payload ->
                                        database.messageDao().deleteMessage(payload)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EnclaveSyncWorker", "Failed parsing raw message", e)
                        }
                    }
                }
                
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

                        if (baseType == "LOUNGE") {
                            val loungeJson = String(decryptedBytes, Charsets.UTF_8)
                            signalingClient.emitDecryptedRawMessage(loungeJson)
                            return@collect
                        }

                        if (baseType == "VAULT_KEY_SYNC") {
                            val keyBase64 = android.util.Base64.encodeToString(decryptedBytes, android.util.Base64.NO_WRAP)
                            cryptoManager.storeVaultKey(keyBase64)
                            Log.d("EnclaveSyncWorker", "Successfully received and saved shared E2EE vault key from partner securely.")
                            return@collect
                        }

                        if (baseType.startsWith("LOUNGE_")) {
                            if (baseType == "LOUNGE_NOTE_SYNC") {
                                try {
                                    val text = String(decryptedBytes, Charsets.UTF_8)
                                    val payloadObj = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.ui.lounge.SyncedNotePayload>(text)
                                    val entity = com.enclave.app.data.local.EncryptedNoteEntity(
                                        id = payloadObj.id,
                                        titlePayload = android.util.Base64.decode(payloadObj.titlePayloadBase64, android.util.Base64.NO_WRAP),
                                        contentPayload = android.util.Base64.decode(payloadObj.contentPayloadBase64, android.util.Base64.NO_WRAP),
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        authorId = partnerId,
                                        isSynced = true
                                    )
                                    database.encryptedNoteDao().insertNote(entity)
                                    showDecryptedNotification("New Shared Note Received")
                                } catch (e: Exception) {
                                    Log.e("EnclaveSyncWorker", "Failed to parse LOUNGE_NOTE_SYNC", e)
                                }
                            } else if (baseType == "LOUNGE_NOTE_DELETE") {
                                val id = String(decryptedBytes, Charsets.UTF_8)
                                database.encryptedNoteDao().deleteNote(id)
                            } else if (baseType == "LOUNGE_DAILY_LETTER") {
                                try {
                                    val text = String(decryptedBytes, Charsets.UTF_8)
                                    val payloadObj = com.enclave.app.webrtc.LenientJson.decodeFromString<com.enclave.app.ui.lounge.SyncedLetterPayload>(text)
                                    val enc = cryptoManager.encryptLocal(payloadObj.plainContent.toByteArray(Charsets.UTF_8))
                                    val entity = com.enclave.app.data.local.LetterEntity(
                                        id = java.util.UUID.randomUUID().toString(),
                                        senderId = payloadObj.senderId,
                                        ciphertext = enc,
                                        createdAt = System.currentTimeMillis(),
                                        isRead = false
                                    )
                                    database.letterDao().insertLetter(entity)
                                    showDecryptedNotification("Daily Letter Received")
                                } catch (e: Exception) {
                                    Log.e("EnclaveSyncWorker", "Failed to parse LOUNGE_DAILY_LETTER", e)
                                }
                            }
                            return@collect
                        }

                        val localEncrypted = if (baseType == "MEDIA" || baseType == "MEDIA_IMAGE" || baseType == "MEDIA_VIDEO" || baseType == "MEDIA_AUDIO" || baseType == "MEDIA_FILE" || baseType == "VOICE") {
                            val ext = when (baseType) {
                                "MEDIA", "MEDIA_IMAGE" -> "jpg"
                                "MEDIA_VIDEO" -> "mp4"
                                "MEDIA_AUDIO" -> "mp3"
                                "MEDIA_FILE" -> {
                                    var parsedExt = "bin"
                                    parts.forEach { part ->
                                        if (part.startsWith("ext=")) {
                                            parsedExt = part.substringAfter("ext=")
                                        }
                                    }
                                    parsedExt
                                }
                                else -> "m4a"
                            }
                            val fileName = "received_${System.currentTimeMillis()}.$ext"
                            vaultRepository.saveSecureFile(fileName, decryptedBytes)
                            cryptoManager.encryptLocal(fileName.toByteArray(Charsets.UTF_8))
                        } else {
                            cryptoManager.encryptLocal(decryptedBytes)
                        }

                        val plaintext = when (baseType) {
                            "MEDIA", "MEDIA_IMAGE" -> "📸 Photo"
                            "MEDIA_VIDEO" -> "🎥 Video"
                            "MEDIA_AUDIO" -> "🎵 Audio File"
                            "MEDIA_FILE" -> "📄 Document"
                            "VOICE" -> "🎤 Voice Memo"
                            "RECORDED_KISS" -> "Kiss Impression"
                            "HAPTIC" -> "📳 Haptic"
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
            }
            // Sync the Collaborative Vault in the background
            try {
                vaultRepository.syncSharedVault()
                Log.d("EnclaveSyncWorker", "Collaborative Vault successfully synchronized in background sync worker.")
            } catch (e: Exception) {
                Log.e("EnclaveSyncWorker", "Collaborative Vault sync failed in background", e)
            }

            // Also sync partner profile for the Companion Widget
            try {
                if (partnerId.isNotBlank()) {
                    val partnerProfile = bundleRepository.fetchPartnerProfile(partnerId)
                    if (partnerProfile != null) {
                        database.userProfileDao().upsertProfilePreservingLocal(partnerProfile)
                        
                        // Force widget update
                        val intent = android.content.Intent(applicationContext, com.enclave.app.ui.widget.CompanionWidgetProvider::class.java)
                        intent.action = android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE
                        val ids = android.appwidget.AppWidgetManager.getInstance(applicationContext)
                            .getAppWidgetIds(android.content.ComponentName(applicationContext, com.enclave.app.ui.widget.CompanionWidgetProvider::class.java))
                        intent.putExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                        applicationContext.sendBroadcast(intent)
                    }
                } else {
                    Log.d("EnclaveSyncWorker", "Skipping partner profile sync: partnerId is blank")
                }
            } catch (e: Exception) {
                Log.e("EnclaveSyncWorker", "Failed to sync partner profile for widget", e)
            }
        } catch (e: java.net.ConnectException) {
            Log.e("EnclaveSyncWorker", "Sync failed: Network connection refused.", e)
            return Result.retry()
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Log.w("EnclaveSyncWorker", "Sync timed out during connection or message retrieval.", e)
            return Result.retry()
        } catch (e: Exception) {
            Log.e("EnclaveSyncWorker", "Sync failed with unhandled exception", e)
            return Result.failure()
        } finally {
            Log.d("EnclaveSyncWorker", "Closing signaling client connection.")
            signalingClient.close()
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
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private suspend fun showDecryptedNotification(text: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        val prefs = applicationContext.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val partnerId = prefs.getString("partner_id", null)?.takeIf { it.isNotBlank() }
        var partnerUsername = "Partner"
        if (partnerId != null) {
            try {
                val db = EnclaveDatabase.getInstance(applicationContext)
                val profile = db.userProfileDao().getProfileSync(partnerId)
                if (profile != null && !profile.username.isNullOrBlank()) {
                    partnerUsername = profile.username
                }
            } catch (e: Exception) {
                Log.e("EnclaveSyncWorker", "Error fetching partner profile for notification", e)
            }
        }

        val formattedText = text.replace("Partner", partnerUsername)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("🔒 $partnerUsername")
            .setContentText(if (formattedText.contains("🔒 Encrypted Image")) "Encrypted Image Received" else formattedText)
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
