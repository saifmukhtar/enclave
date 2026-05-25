package com.enclave.app.ui.chat.session

import android.content.Context
import android.util.Base64
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.network.BundleRepository
import com.enclave.app.ui.chat.ChatUiState
import com.enclave.app.webrtc.EncryptedSignalPayload
import com.enclave.app.webrtc.LenientJson
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatSessionManager(
    private val context: Context,
    private val cryptoManager: CryptoManager,
    private val bundleRepository: BundleRepository,
    private val signalingClient: SignalingClient,
    private val database: EnclaveDatabase,
    private val partnerId: String,
    private val onMessagePayloadReceived: (EncryptedSignalPayload) -> Unit
) {

    private val _uiState = MutableStateFlow<ChatUiState>(ChatUiState.Connecting)
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _partnerTyping = MutableStateFlow(false)
    val partnerTyping: StateFlow<Boolean> = _partnerTyping.asStateFlow()

    private var typingExpireJob: Job? = null

    fun start(scope: CoroutineScope) {
        initializeSession(scope)
        observeWebSocket(scope)
    }

    private fun initializeSession(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                // Generates identity, pre-keys etc. locally
                var localKeyRetryDelay = 2_000L
                while (true) {
                    try {
                        cryptoManager.generateLocalKeysIfNecessary()
                        break
                    } catch (e: Exception) {
                        android.util.Log.e("ChatSessionManager", "Local keys generation failed, retrying in ${localKeyRetryDelay}ms", e)
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
                        android.util.Log.d("ChatSessionManager", "Local bundle uploaded successfully!")
                        break
                    } catch (e: Exception) {
                        android.util.Log.e("ChatSessionManager", "Upload local bundle failed, retrying in ${uploadRetryDelay}ms", e)
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
                        initializeSharedVaultKey(scope)
                        return@launch
                    } else {
                        val exception = result.exceptionOrNull()
                        val msg = exception?.message ?: ""
                        // If partner simply hasn't registered yet, wait and retry
                        _uiState.value = ChatUiState.WaitingForPartner
                        android.util.Log.e("ChatSessionManager", "Partner not ready, retrying in ${retryDelay}ms: $msg", exception)
                        delay(retryDelay)
                        retryDelay = minOf(retryDelay * 2, 30_000L) // cap at 30s
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatSessionManager", "Fatal session initialization error", e)
                _uiState.value = ChatUiState.Error(e.message ?: "Unknown initialization error")
            }
        }
    }

    private fun initializeSharedVaultKey(scope: CoroutineScope) {
        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        if (!prefs.contains("vault_key")) {
            val keyBytes = ByteArray(32)
            java.security.SecureRandom().nextBytes(keyBytes)
            val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
            prefs.edit().putString("vault_key", keyBase64).apply()
            
            // Sync it with partner via signaling (the encryption/sending happens here, but since this is infrastructure,
            // we will let the sender encrypt and send it)
            scope.launch(Dispatchers.IO) {
                val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                val encryptionResult = cryptoManager.encryptMessage(partnerAddress, keyBytes)
                if (encryptionResult.isSuccess) {
                    val ciphertext = encryptionResult.getOrThrow()
                    signalingClient.sendEncryptedMessage(partnerId, ciphertext, "VAULT_KEY_SYNC")
                }
            }
        }
    }

    private fun observeWebSocket(scope: CoroutineScope) {
        scope.launch {
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
                                typingExpireJob = scope.launch {
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
                        "PROFILE_UPDATE" -> {
                            if (_uiState.value is ChatUiState.WaitingForPartner ||
                                _uiState.value is ChatUiState.Error) {
                                retryHandshakeNow(scope)
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        scope.launch {
            signalingClient.incomingSignalPayloads.collect { payload ->
                onMessagePayloadReceived(payload)
            }
        }
    }

    fun retryHandshakeNow(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ChatSessionManager", "Retrying Signal session handshake...")
                val result = bundleRepository.fetchPartnerBundleAndBuildSession(partnerId)
                if (result.isSuccess) {
                    _uiState.value = ChatUiState.Secured
                    android.util.Log.d("ChatSessionManager", "Re-handshake successful — session rebuilt")
                } else {
                    android.util.Log.w("ChatSessionManager", "Re-handshake failed: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatSessionManager", "Re-handshake error", e)
            }
        }
    }
}
