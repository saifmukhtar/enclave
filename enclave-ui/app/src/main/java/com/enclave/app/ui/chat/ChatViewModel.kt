package com.enclave.app.ui.chat

import android.content.Context
import android.util.Base64
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.crypto.EnclaveSignalStore
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.MessageEntity
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.webrtc.EncryptedSignalPayload
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.worker.DisappearingMessagesWorker
import com.enclave.app.network.BundleRepository
import com.enclave.app.media.VoiceMemoController
import com.enclave.app.models.RecordedKissPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.enclave.app.webrtc.LenientJson
import org.json.JSONObject
import org.signal.libsignal.protocol.SignalProtocolAddress
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit


class ChatViewModel(
    private val context: Context,
    private val bundleRepository: BundleRepository,
    private val cryptoManager: CryptoManager,
    private val signalStore: EnclaveSignalStore,
    private val signalingClient: SignalingClient,
    private val vaultRepository: VaultRepository,
    private val database: EnclaveDatabase,
    private val partnerId: String,
    private val myId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Connecting)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _partnerTyping = MutableStateFlow(false)
    val partnerTyping: StateFlow<Boolean> = _partnerTyping.asStateFlow()

    private val _activePlayingVoiceMessageId = MutableStateFlow<String?>(null)
    val activePlayingVoiceMessageId: StateFlow<String?> = _activePlayingVoiceMessageId.asStateFlow()

    private val _disappearingMode = MutableStateFlow(0L) // 0 = Off, otherwise duration in seconds
    val disappearingMode: StateFlow<Long> = _disappearingMode.asStateFlow()

    // Active Quote/Reply target message state
    private val _replyToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyToMessage: StateFlow<ChatMessage?> = _replyToMessage.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _recordingAmplitude = MutableStateFlow(0f)
    val recordingAmplitude: StateFlow<Float> = _recordingAmplitude.asStateFlow()

    private val _navigateToVault = MutableSharedFlow<Unit>()
    val navigateToVault = _navigateToVault.asSharedFlow()

    private val voiceMemoController = VoiceMemoController(context, cryptoManager)

    private val _activePlaybackKiss = MutableStateFlow<RecordedKissPayload?>(null)
    val activePlaybackKiss: StateFlow<RecordedKissPayload?> = _activePlaybackKiss.asStateFlow()

    fun clearPlaybackKiss() {
        _activePlaybackKiss.value = null
    }

    fun triggerNavigateToVault() {
        viewModelScope.launch {
            _navigateToVault.emit(Unit)
        }
    }

    private val partnerAddress = SignalProtocolAddress(partnerId, 1)

    private val messageDecryptorUseCase = MessageDecryptorUseCase(database, cryptoManager, myId)

    private var typingExpireJob: Job? = null
    private var recordingJob: Job? = null
    private var isChatActive = false

    // Decrypt messages strictly on background thread, reusing local cache hits for 60fps performance
    val messages: StateFlow<List<ChatMessage>> = messageDecryptorUseCase.getDecryptedMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val mediaMessages: StateFlow<List<ChatMessage>> = messageDecryptorUseCase.getDecryptedMediaMessagesFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeSession()
        observeWebSocket()
        startLiveDisappearingTicker()
    }

    fun setReplyToMessage(msg: ChatMessage?) {
        _replyToMessage.value = msg
    }

    private fun startLiveDisappearingTicker() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                database.messageDao().deleteExpiredMessages(System.currentTimeMillis())
            }
        }
    }

    private fun initializeSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Generates identity, pre-keys etc. locally
                var localKeyRetryDelay = 2_000L
                while (true) {
                    try {
                        cryptoManager.generateLocalKeysIfNecessary()
                        break
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Local keys generation failed, retrying in ${localKeyRetryDelay}ms", e)
                        delay(localKeyRetryDelay)
                        localKeyRetryDelay = minOf(localKeyRetryDelay * 2, 10_000L)
                    }
                }

                // Publish local Signal cryptographic key bundle to Supabase registry with retry backoff
                var uploadRetryDelay = 5_000L
                while (true) {
                    try {
                        _uiState.value = ChatUiState.Connecting
                        bundleRepository.uploadLocalBundle()
                        android.util.Log.d("ChatViewModel", "Local bundle uploaded successfully!")
                        break
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Upload local bundle failed, retrying in ${uploadRetryDelay}ms", e)
                        _uiState.value = ChatUiState.Connecting
                        delay(uploadRetryDelay)
                        uploadRetryDelay = minOf(uploadRetryDelay * 2, 30_000L)
                    }
                }

                // Retry fetching partner bundle with backoff until they register
                var retryDelay = 5_000L
                while (true) {
                    _uiState.value = ChatUiState.Handshaking
                    val result = bundleRepository.fetchPartnerBundleAndBuildSession(partnerId)
                    if (result.isSuccess) {
                        _uiState.value = ChatUiState.Secured
                        initializeSharedVaultKey()
                        return@launch
                    } else {
                        val exception = result.exceptionOrNull()
                        val msg = exception?.message ?: ""
                        // If partner simply hasn't registered yet, wait and retry
                        _uiState.value = ChatUiState.WaitingForPartner
                        android.util.Log.e("ChatViewModel", "Partner not ready, retrying in ${retryDelay}ms: $msg", exception)
                        delay(retryDelay)
                        retryDelay = minOf(retryDelay * 2, 30_000L) // cap at 30s
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Fatal session initialization error", e)
                _uiState.value = ChatUiState.Error(e.message ?: "Unknown initialization error")
            }
        }
    }

    private fun observeWebSocket() {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { text ->
                try {
                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(text)
                    if (msg.senderId != partnerId) return@collect

                    when (msg.type) {
                        "TYPING_STATUS" -> {
                            val isTyping = msg.payload?.toBoolean() ?: false
                            _partnerTyping.value = isTyping

                            typingExpireJob?.cancel()
                            if (isTyping) {
                                typingExpireJob = viewModelScope.launch {
                                    delay(5000)
                                    _partnerTyping.value = false
                                }
                            }
                        }
                        "READ_RECEIPT" -> {
                            val messageId = msg.payload
                            if (messageId != null) {
                                database.messageDao().updateDeliveryStatus(messageId, "READ")
                            }
                        }
                        "DELIVERY_RECEIPT" -> {
                            val messageId = msg.payload
                            if (messageId != null) {
                                val existing = database.messageDao().getMessageById(messageId)
                                if (existing != null && existing.deliveryStatus != "READ") {
                                    database.messageDao().updateDeliveryStatus(messageId, "DELIVERED")
                                }
                            }
                        }
                        // When partner sends a PROFILE_UPDATE after reconnecting,
                        // try re-building the Signal session in case their keys rotated.
                        "PROFILE_UPDATE" -> {
                            if (_uiState.value is ChatUiState.WaitingForPartner ||
                                _uiState.value is ChatUiState.Error) {
                                retryHandshakeNow()
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        viewModelScope.launch {
            signalingClient.incomingSignalPayloads.collect { payload ->
                receiveMessagePayload(payload)
            }
        }
    }

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.messageDao().updateDeliveryStatus(messageId, "READ")
            database.messageDao().markAsRead(messageId)
            signalingClient.sendReadReceipt(partnerId, messageId)
            
            // Set dynamic OS-resilient disappearing countdown timer
            val matchingMessage = messages.value.find { it.id == messageId }
            if (matchingMessage != null && matchingMessage.disappearingDuration > 0 && matchingMessage.expiresAt == 0L) {
                val expiresAt = System.currentTimeMillis() + (matchingMessage.disappearingDuration * 1000)
                database.messageDao().updateExpirationTime(messageId, expiresAt)

                val request = OneTimeWorkRequestBuilder<DisappearingMessagesWorker>()
                    .setInputData(workDataOf("MESSAGE_ID" to messageId))
                    .setInitialDelay(matchingMessage.disappearingDuration, TimeUnit.SECONDS)
                    .build()
                WorkManager.getInstance(context).enqueue(request)
            }
        }
    }

    fun setDisappearingMode(duration: Long) {
        _disappearingMode.value = duration
    }

    private val messageSenderUseCase = MessageSenderUseCase(
        context, cryptoManager, signalStore, signalingClient, database, myId, partnerId
    )

    fun sendMessage(text: String) {
        viewModelScope.launch {
            val replyTarget = _replyToMessage.value
            _replyToMessage.value = null // Clear active reply state
            
            val isSecured = _uiState.value is ChatUiState.Secured
            
            val result = messageSenderUseCase.sendMessage(text, replyTarget, disappearingMode.value, isSecured)
            if (result != null) {
                messageDecryptorUseCase.injectCache(result.first, result.second)
            }
        }
    }

    fun sendTimeCapsuleMessage(text: String, sendAt: Long) {
        viewModelScope.launch {
            messageSenderUseCase.sendTimeCapsuleMessage(text, sendAt)
        }
    }

    fun receiveMessagePayload(payload: EncryptedSignalPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            val decryptionResult = cryptoManager.decryptMessage(partnerAddress, payload.ciphertext)
            if (decryptionResult.isSuccess) {
                val decryptedBytes = decryptionResult.getOrThrow()
                val messageId = payload.messageId ?: UUID.randomUUID().toString()
                
                var parsedDuration = 0L
                var parsedExt = "bin"
                val parts = payload.contentType.split(";")
                val baseType = parts.firstOrNull() ?: "TEXT"
                parts.forEach { part ->
                    if (part.startsWith("expire=")) {
                        parsedDuration = part.substringAfter("expire=").toLongOrNull() ?: 0L
                    }
                    if (part.startsWith("ext=")) {
                        parsedExt = part.substringAfter("ext=")
                    }
                }

                if (baseType == "VAULT_KEY_SYNC") {
                    val keyBase64 = Base64.encodeToString(decryptedBytes, Base64.NO_WRAP)
                    context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
                        .edit().putString("vault_key", keyBase64).apply()
                    android.util.Log.d("ChatViewModel", "Successfully received and saved shared E2EE vault key from partner.")
                    return@launch
                }

                if (baseType == "REACTION") {
                    val reactionJson = JSONObject(String(decryptedBytes, Charsets.UTF_8))
                    val targetMsgId = reactionJson.optString("messageId")
                    val emoji = reactionJson.optString("emoji")
                    if (targetMsgId.isNotEmpty()) {
                        database.messageDao().updateMessageReaction(targetMsgId, emoji)
                    }
                    return@launch
                }

                val localEncrypted = if (baseType == "MEDIA" || baseType == "MEDIA_IMAGE" || baseType == "MEDIA_VIDEO" || baseType == "MEDIA_AUDIO" || baseType == "MEDIA_FILE" || baseType == "VOICE") {
                    val ext = when (baseType) {
                        "MEDIA", "MEDIA_IMAGE" -> "jpg"
                        "MEDIA_VIDEO" -> "mp4"
                        "MEDIA_AUDIO" -> "mp3"
                        "MEDIA_FILE" -> parsedExt
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
                    "MEDIA_FILE" -> "📄 Document ($parsedExt)"
                    "VOICE" -> "🎤 Voice Memo"
                    "RECORDED_KISS" -> "Kiss Impression"
                    "HAPTIC" -> {
                        val patternName = String(decryptedBytes, Charsets.UTF_8)
                        triggerHapticPattern(patternName)
                        "📳 Haptic: $patternName"
                    }
                    else -> String(decryptedBytes, Charsets.UTF_8)
                }

                messageDecryptorUseCase.injectCache(messageId, plaintext)

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
            } else {
                // Decryption failed — this usually means the partner's Signal session was
                // reset (e.g. app reinstall). Trigger a re-handshake to rebuild the session
                // so future messages decrypt correctly.
                val err = decryptionResult.exceptionOrNull()
                android.util.Log.w("ChatViewModel", "Decryption failed, will attempt re-handshake: ${err?.message}")
                if (_uiState.value is ChatUiState.Secured) {
                    retryHandshakeNow()
                }
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val matchingMessage = messages.value.find { it.id == messageId } ?: return@launch
            val newEmoji = if (matchingMessage.reaction == emoji) "" else emoji
            database.messageDao().updateMessageReaction(messageId, newEmoji)

            val reactionPayload = JSONObject().apply {
                put("messageId", messageId)
                put("emoji", newEmoji)
            }.toString().toByteArray(Charsets.UTF_8)

            val encryptResult = cryptoManager.encryptMessage(partnerAddress, reactionPayload)
            if (encryptResult.isSuccess) {
                signalingClient.sendEncryptedMessage(partnerId, encryptResult.getOrThrow(), "REACTION")
            }
        }
    }

    /**
     * Re-initiates the X3DH key bundle fetch and Signal session build.
     * Called when: (a) decryption fails (partner may have re-keyed), or
     * (b) partner comes online after being in WaitingForPartner/Error state.
     */
    private fun retryHandshakeNow() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ChatViewModel", "Retrying Signal session handshake...")
                val result = bundleRepository.fetchPartnerBundleAndBuildSession(partnerId)
                if (result.isSuccess) {
                    _uiState.value = ChatUiState.Secured
                    android.util.Log.d("ChatViewModel", "Re-handshake successful — session rebuilt")
                } else {
                    android.util.Log.w("ChatViewModel", "Re-handshake failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Re-handshake error", e)
            }
        }
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.messageDao().deleteMessage(messageId)
        }
    }

    fun sendAudioMemo(memoBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptionResult = cryptoManager.encryptMessage(partnerAddress, memoBytes)
            if (encryptionResult.isSuccess) {
                val ciphertext = encryptionResult.getOrThrow()
                val messageId = UUID.randomUUID().toString()
                
                val isConnected = signalingClient.isConnected()
                if (isConnected) {
                    signalingClient.sendEncryptedMessage(partnerId, ciphertext, "VOICE", messageId = messageId)
                } else {
                    val outbox = com.enclave.app.data.local.OutboxEntity(
                        targetId = partnerId,
                        type = "SIGNAL_PAYLOAD",
                        payload = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                        contentType = "VOICE",
                        messageId = messageId
                    )
                    database.outboxDao().insert(outbox)
                    val req = androidx.work.OneTimeWorkRequestBuilder<com.enclave.app.worker.OutboxSyncWorker>()
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("outbox_sync", androidx.work.ExistingWorkPolicy.REPLACE, req)
                }
                
                messageDecryptorUseCase.injectCache(messageId, "🎤 Voice Memo")
                
                val fileName = "sent_${System.currentTimeMillis()}.m4a"
                vaultRepository.saveSecureFile(fileName, memoBytes)
                val localEncrypted = cryptoManager.encryptLocal(fileName.toByteArray(Charsets.UTF_8))
                
                val entity = MessageEntity(
                    id = messageId,
                    senderId = myId,
                    receiverId = partnerId,
                    encryptedPayload = localEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    messageType = "VOICE",
                    deliveryStatus = if (isConnected) "SENT" else "QUEUED",
                    disappearingDuration = 0L,
                    expiresAt = 0L
                )
                database.messageDao().insertMessage(entity)
            }
        }
    }

    fun sendTypingStatus(isTyping: Boolean) {
        viewModelScope.launch {
            signalingClient.sendTypingStatus(partnerId, isTyping)
        }
    }

    fun sendMediaMessage(mediaBytes: ByteArray, mimeType: String = "image/jpeg") {
        viewModelScope.launch(Dispatchers.IO) {
            val baseType = if (mimeType.startsWith("video/")) {
                "MEDIA_VIDEO"
            } else if (mimeType.startsWith("audio/") || mimeType.startsWith("music/")) {
                "MEDIA_AUDIO"
            } else if (mimeType.startsWith("image/")) {
                "MEDIA_IMAGE"
            } else {
                "MEDIA_FILE"
            }
            
            val ext = when (baseType) {
                "MEDIA_VIDEO" -> "mp4"
                "MEDIA_AUDIO" -> "mp3"
                "MEDIA_IMAGE" -> "jpg"
                else -> {
                    when (mimeType) {
                        "application/pdf" -> "pdf"
                        "text/markdown" -> "md"
                        "application/msword" -> "doc"
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
                        else -> "bin"
                    }
                }
            }

            val contentTypeHeader = if (baseType == "MEDIA_FILE") "$baseType;ext=$ext" else baseType

            val encryptionResult = cryptoManager.encryptMessage(partnerAddress, mediaBytes)
            if (encryptionResult.isSuccess) {
                val ciphertext = encryptionResult.getOrThrow()
                val messageId = UUID.randomUUID().toString()
                
                val isConnected = signalingClient.isConnected()
                if (isConnected) {
                    signalingClient.sendEncryptedMessage(partnerId, ciphertext, contentTypeHeader, messageId = messageId)
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
                
                val displayMsg = when (baseType) {
                    "MEDIA_VIDEO" -> "🎥 Video"
                    "MEDIA_AUDIO" -> "🎵 Audio File"
                    "MEDIA_FILE" -> "📄 Document ($ext)"
                    else -> "📸 Photo"
                }
                messageDecryptorUseCase.injectCache(messageId, displayMsg)
                
                val fileName = "sent_${System.currentTimeMillis()}.$ext"
                vaultRepository.saveSecureFile(fileName, mediaBytes)
                val localEncrypted = cryptoManager.encryptLocal(fileName.toByteArray(Charsets.UTF_8))
                
                val entity = MessageEntity(
                    id = messageId,
                    senderId = myId,
                    receiverId = partnerId,
                    encryptedPayload = localEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    messageType = baseType,
                    deliveryStatus = if (isConnected) "SENT" else "QUEUED",
                    disappearingDuration = 0L,
                    expiresAt = 0L
                )
                database.messageDao().insertMessage(entity)
            }
        }
    }

    fun sendRecordedKiss(jsonPayload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val messageBytes = jsonPayload.toByteArray(Charsets.UTF_8)
            val encryptionResult = cryptoManager.encryptMessage(partnerAddress, messageBytes)
            if (encryptionResult.isSuccess) {
                val ciphertext = encryptionResult.getOrThrow()
                val messageId = UUID.randomUUID().toString()
                val isConnected = signalingClient.isConnected()
                if (isConnected) {
                    signalingClient.sendEncryptedMessage(partnerId, ciphertext, "RECORDED_KISS", messageId = messageId)
                } else {
                    val outbox = com.enclave.app.data.local.OutboxEntity(
                        targetId = partnerId,
                        type = "SIGNAL_PAYLOAD",
                        payload = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                        contentType = "RECORDED_KISS",
                        messageId = messageId
                    )
                    database.outboxDao().insert(outbox)
                    val req = androidx.work.OneTimeWorkRequestBuilder<com.enclave.app.worker.OutboxSyncWorker>()
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("outbox_sync", androidx.work.ExistingWorkPolicy.REPLACE, req)
                }
                
                messageDecryptorUseCase.injectCache(messageId, "Kiss Impression")
                val localEncrypted = cryptoManager.encryptLocal(messageBytes)
                val entity = MessageEntity(
                    id = messageId,
                    senderId = myId,
                    receiverId = partnerId,
                    encryptedPayload = localEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    messageType = "RECORDED_KISS",
                    deliveryStatus = if (isConnected) "SENT" else "QUEUED",
                    disappearingDuration = 0L,
                    expiresAt = 0L
                )
                database.messageDao().insertMessage(entity)
            }
        }
    }

    fun playRecordedKiss(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val msg = database.messageDao().getMessageById(messageId) ?: return@launch
            try {
                val decryptedBytes = cryptoManager.decryptLocal(msg.encryptedPayload)
                val jsonStr = String(decryptedBytes, Charsets.UTF_8)
                val payload = LenientJson.decodeFromString<RecordedKissPayload>(jsonStr)
                _activePlaybackKiss.value = payload
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playVoiceMessage(messageId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentActive = _activePlayingVoiceMessageId.value
            if (currentActive == messageId) {
                voiceMemoController.stopPlayback()
                _activePlayingVoiceMessageId.value = null
                return@launch
            }
            
            voiceMemoController.stopPlayback()
            _activePlayingVoiceMessageId.value = null
            
            val entity = database.messageDao().getMessageById(messageId) ?: return@launch
            try {
                val fileNameBytes = cryptoManager.decryptLocal(entity.encryptedPayload)
                val fileName = String(fileNameBytes, Charsets.UTF_8)
                val fileBytes = vaultRepository.encryptedFileManager.readSecureFile(fileName)
                
                launch(Dispatchers.Main) {
                    _activePlayingVoiceMessageId.value = messageId
                    voiceMemoController.playVoiceMemo(fileBytes) {
                        _activePlayingVoiceMessageId.value = null
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startAudioRecording() {
        val started = voiceMemoController.startRecording()
        if (!started) return
        _isRecording.value = true
        _recordingDurationSeconds.value = 0
        _recordingAmplitude.value = 0f
        recordingJob = viewModelScope.launch {
            var ticks = 0
            while (_isRecording.value) {
                delay(100)
                ticks++
                if (ticks % 10 == 0) {
                    _recordingDurationSeconds.value = ticks / 10
                }
                val rawAmp = voiceMemoController.getAmplitude()
                _recordingAmplitude.value = (rawAmp.toFloat() / 32768f).coerceIn(0f, 1f)
            }
        }
    }

    fun stopAudioRecording() {
        recordingJob?.cancel()
        _isRecording.value = false
        val audioBytes = voiceMemoController.stopRecording()
        if (audioBytes != null && audioBytes.isNotEmpty()) {
            sendAudioMemo(audioBytes)
        }
    }

    fun cancelAudioRecording() {
        recordingJob?.cancel()
        _isRecording.value = false
        _recordingDurationSeconds.value = 0
        _recordingAmplitude.value = 0f
        voiceMemoController.cancelRecording()
    }

    suspend fun getMediaBytes(messageId: String): ByteArray? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val entity = database.messageDao().getMessageById(messageId) ?: return@withContext null
                val fileNameBytes = cryptoManager.decryptLocal(entity.encryptedPayload)
                val fileName = String(fileNameBytes, Charsets.UTF_8)
                vaultRepository.encryptedFileManager.readSecureFile(fileName)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    // --- Secure Encrypted Message Search ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ChatMessage>>(emptyList())
    val searchResults: StateFlow<List<ChatMessage>> = _searchResults.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch last 1000 messages
                val entities = database.messageDao().getLastMessages(1000)
                val matches = entities.mapNotNull { entity ->
                    val decryptedText = messageDecryptorUseCase.getDecryptedText(entity)

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
                            // ignore
                        }
                    }

                    if (parsedText.contains(query, ignoreCase = true)) {
                        val isFromMe = entity.senderId == myId
                        ChatMessage(
                            id = entity.id,
                            text = parsedText,
                            isFromMe = isFromMe,
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
                    } else null
                }
                _searchResults.value = matches
            } catch (e: Exception) {
                android.util.Log.e("ChatViewModel", "Secure search failed", e)
            }
        }
    }

    private fun initializeSharedVaultKey() {
        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        var vaultKeyBase64 = prefs.getString("vault_key", null)
        
        if (vaultKeyBase64 == null) {
            if (myId < partnerId) {
                val newKey = com.enclave.app.crypto.VaultCipher.generateKey()
                vaultKeyBase64 = Base64.encodeToString(newKey, Base64.NO_WRAP)
                prefs.edit().putString("vault_key", vaultKeyBase64).apply()
                
                viewModelScope.launch {
                    try {
                        sendVaultKeyToPartner(newKey)
                    } catch (e: Exception) {
                        android.util.Log.e("ChatViewModel", "Failed to send generated vault key", e)
                    }
                }
            }
        } else {
            val keyBytes = Base64.decode(vaultKeyBase64, Base64.NO_WRAP)
            viewModelScope.launch {
                try {
                    sendVaultKeyToPartner(keyBytes)
                } catch (e: Exception) {
                    android.util.Log.e("ChatViewModel", "Failed to sync existing vault key", e)
                }
            }
        }
    }

    private suspend fun sendVaultKeyToPartner(keyBytes: ByteArray) {
        val encryptedResult = cryptoManager.encryptMessage(partnerAddress, keyBytes)
        if (encryptedResult.isSuccess) {
            signalingClient.sendEncryptedMessage(
                targetId = partnerId,
                ciphertext = encryptedResult.getOrThrow(),
                contentType = "VAULT_KEY_SYNC",
                messageId = UUID.randomUUID().toString()
            )
            android.util.Log.d("ChatViewModel", "Successfully sent shared E2EE vault key to partner.")
        } else {
            android.util.Log.e("ChatViewModel", "Failed to encrypt vault key for partner", encryptedResult.exceptionOrNull())
        }
    }

    fun sendHapticMessage(patternName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val messageBytes = patternName.toByteArray(Charsets.UTF_8)
            val encryptionResult = cryptoManager.encryptMessage(partnerAddress, messageBytes)
            
            if (encryptionResult.isSuccess) {
                val ciphertext = encryptionResult.getOrThrow()
                val messageId = UUID.randomUUID().toString()
                
                val isConnected = signalingClient.isConnected()
                if (isConnected) {
                    signalingClient.sendEncryptedMessage(partnerId, ciphertext, contentType = "HAPTIC", messageId = messageId)
                } else {
                    val outbox = com.enclave.app.data.local.OutboxEntity(
                        targetId = partnerId,
                        type = "SIGNAL_PAYLOAD",
                        payload = android.util.Base64.encodeToString(ciphertext, android.util.Base64.NO_WRAP),
                        contentType = "HAPTIC",
                        messageId = messageId
                    )
                    database.outboxDao().insert(outbox)
                    val req = androidx.work.OneTimeWorkRequestBuilder<com.enclave.app.worker.OutboxSyncWorker>()
                        .setConstraints(androidx.work.Constraints.Builder().setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build())
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork("outbox_sync", androidx.work.ExistingWorkPolicy.REPLACE, req)
                }
                
                messageDecryptorUseCase.injectCache(messageId, "📳 Haptic: $patternName")
                
                val localEncrypted = cryptoManager.encryptLocal(messageBytes)
                val entity = MessageEntity(
                    id = messageId,
                    senderId = myId,
                    receiverId = partnerId,
                    encryptedPayload = localEncrypted,
                    timestamp = System.currentTimeMillis(),
                    isRead = true,
                    messageType = "HAPTIC",
                    deliveryStatus = if (isConnected) "SENT" else "QUEUED",
                    disappearingDuration = 0L,
                    expiresAt = 0L
                )
                database.messageDao().insertMessage(entity)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun triggerHapticPattern(patternName: String) {
        val vibrator = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
            ?: return
        val defaultVibrator = vibrator.defaultVibrator
        if (!defaultVibrator.hasVibrator()) return

        val timings = when (patternName) {
            "Heartbeat" -> longArrayOf(0, 150, 100, 150, 600, 150, 100, 150)
            "Purr" -> longArrayOf(0, 50, 50, 50, 50, 50, 50, 50, 50, 50, 50)
            "Rapid" -> longArrayOf(0, 20, 20, 20, 20, 20, 20, 20, 20, 20)
            else -> longArrayOf(0, 500)
        }
        val amplitudes = when (patternName) {
            "Heartbeat" -> intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)
            "Purr" -> intArrayOf(0, 100, 0, 100, 0, 100, 0, 100, 0, 100, 0)
            "Rapid" -> intArrayOf(0, 255, 0, 255, 0, 255, 0, 255, 0, 255)
            else -> intArrayOf(0, 255)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val effect = android.os.VibrationEffect.createWaveform(timings, amplitudes, -1)
            defaultVibrator.vibrate(effect)
        } else {
            defaultVibrator.vibrate(timings, -1)
        }
    }
}
