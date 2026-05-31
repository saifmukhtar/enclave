package dev.saifmukhtar.enclave.ui.chat.session

import android.content.Context
import android.util.Base64
import dev.saifmukhtar.enclave.crypto.CryptoManager
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase
import dev.saifmukhtar.enclave.network.BundleRepository
import dev.saifmukhtar.enclave.ui.chat.ChatUiState
import dev.saifmukhtar.enclave.webrtc.EncryptedSignalPayload
import dev.saifmukhtar.enclave.webrtc.LenientJson
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
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
        if (partnerId.isBlank()) {
            android.util.Log.d("ChatSessionManager", "Skipping initialization: partnerId is blank")
            _uiState.value = ChatUiState.Connecting
            return
        }
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
                        // Settling delay: when both devices boot simultaneously, each uploads a fresh bundle
                        // and immediately tries to handshake. Without this delay, early messages are
                        // encrypted against a session that the partner hasn't established yet, causing
                        // 'protobuf encoding was invalid' decryption failures on first exchange.
                        delay(1_500L)
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
        if (!cryptoManager.hasVaultKey()) {
            // BUG-11 Fix: Both devices find vault_key missing on first pair and each generate their
            // own key simultaneously. The last VAULT_KEY_SYNC to arrive silently overwrites the first,
            // making the earlier device's encrypted vault files permanently unreadable.
            //
            // Fix: Use lexicographic UUID ordering to deterministically assign key-generator role.
            // The device whose UUID sorts earlier is the "owner" and generates+sends the key.
            // The other device simply waits — it will receive VAULT_KEY_SYNC from its partner.
            val myId = prefs.getString("my_id", null) ?: return
            val amIKeyOwner = myId < partnerId  // lexicographic: exactly one side is always true
 
            if (amIKeyOwner) {
                val keyBytes = ByteArray(32)
                java.security.SecureRandom().nextBytes(keyBytes)
                val keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
                cryptoManager.storeVaultKey(keyBase64)
                android.util.Log.d("ChatSessionManager", "Vault key owner: generating and syncing key to partner")

                scope.launch(Dispatchers.IO) {
                    val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                    val encryptionResult = cryptoManager.encryptMessage(partnerAddress, keyBytes)
                    if (encryptionResult.isSuccess) {
                        val ciphertext = encryptionResult.getOrThrow()
                        signalingClient.sendEncryptedMessage(partnerId, ciphertext, "VAULT_KEY_SYNC")
                    }
                }
            } else {
                android.util.Log.d("ChatSessionManager", "Vault key non-owner: waiting for partner to send VAULT_KEY_SYNC")
                // Partner (key-owner) will send VAULT_KEY_SYNC — received and saved by ChatViewModel/EnclaveSyncWorker
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
                        "MESSAGE_REVOKE" -> {
                            val revokeId = msg.payload
                            if (revokeId != null) {
                                database.messageDao().deleteMessage(revokeId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("Enclave", "Exception caught", e)
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
        if (partnerId.isBlank()) return
        scope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("ChatSessionManager", "Retrying Signal session handshake...")
                // Re-upload our own bundle first — partner may have reinstalled and needs fresh keys
                try { bundleRepository.uploadLocalBundle() } catch (e: Exception) {
                    android.util.Log.w("ChatSessionManager", "Bundle re-upload failed (non-fatal): ${e.message}")
                }

                // Handle UntrustedIdentityException: when the partner rotates keys (reinstall / key rotation),
                // the cached identity no longer matches. Force-trust the new key so handshake can proceed.
                // This mirrors how Signal Protocol handles key rotation in official clients.
                try {
                    val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                    if (cryptoManager.signalStore.containsSession(partnerAddress)) {
                        // Delete stale session to allow fresh handshake with partner's new keys
                        android.util.Log.d("ChatSessionManager", "Clearing stale session for partner to allow fresh key trust")
                        cryptoManager.signalStore.deleteSession(partnerAddress)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ChatSessionManager", "Identity pre-clear failed (non-fatal): ${e.message}")
                }

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
