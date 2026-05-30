package com.enclave.app.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.StatusStoryEntity
import com.enclave.app.webrtc.SignalingClient
import com.enclave.app.webrtc.SignalMessageWrapper
import com.enclave.app.webrtc.LenientJson
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class StoryViewModel(
    application: Application,
    private val database: EnclaveDatabase,
    private val cryptoManager: CryptoManager,
    private val signalingClient: SignalingClient,
    val myId: String,
    private val partnerId: String
) : AndroidViewModel(application) {

    private val storyDao = database.statusStoryDao()

    val myStories: StateFlow<List<StatusStoryEntity>> = storyDao
        .getStoriesByAuthor(myId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val partnerStories: StateFlow<List<StatusStoryEntity>> = storyDao
        .getStoriesByAuthor(partnerId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val unviewedCount: StateFlow<Int> = storyDao
        .getUnviewedPartnerStoryCount(partnerId, System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch { storyDao.deleteExpiredStories(System.currentTimeMillis()) }
        observeIncoming()
    }

    private fun observeIncoming() {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { raw ->
                try {
                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(raw)
                    if (msg.senderId != partnerId) return@collect
                    when (msg.type) {
                        "STORY_SHARE" -> {
                            msg.payload?.let { payload ->
                                try {
                                    val story = LenientJson.decodeFromString<StorySharePayload>(payload)
                                    val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                                    val encryptedBytes = android.util.Base64.decode(story.encryptedContent, android.util.Base64.NO_WRAP)
                                    val decryptedResult = cryptoManager.decryptMessage(partnerAddress, encryptedBytes)
                                    if (decryptedResult.isSuccess) {
                                        val decryptedBytes = decryptedResult.getOrThrow()
                                        val reEncrypted = cryptoManager.encryptLocal(decryptedBytes)
                                        storyDao.upsertStory(
                                            StatusStoryEntity(
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
                                    } else {
                                        android.util.Log.e("StoryVM", "Failed to decrypt story message: ${decryptedResult.exceptionOrNull()?.message}")
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("StoryVM", "Error parsing incoming story share", e)
                                }
                            }
                        }
                        "STORY_VIEWED" -> {
                            msg.payload?.let { payload ->
                                android.util.Log.d("StoryVM", "Partner viewed story: $payload")
                                storyDao.markViewed(payload, System.currentTimeMillis())
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun postStory(text: String, bgColor: String) {
        viewModelScope.launch {
            try {
                val storyId = UUID.randomUUID().toString()
                val textBytes = text.toByteArray(Charsets.UTF_8)
                val localEncrypted = cryptoManager.encryptLocal(textBytes)
                val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                val partnerEncryptedResult = cryptoManager.encryptMessage(partnerAddress, textBytes)
                if (partnerEncryptedResult.isFailure) {
                    android.util.Log.e("StoryVM", "Failed to encrypt story for partner using Double Ratchet")
                    return@launch
                }
                val partnerEncryptedB64 = android.util.Base64.encodeToString(partnerEncryptedResult.getOrThrow(), android.util.Base64.NO_WRAP)
                val expiresAt = System.currentTimeMillis() + 86_400_000L

                val entity = StatusStoryEntity(
                    id = storyId,
                    authorId = myId,
                    contentType = "TEXT",
                    encryptedPayload = localEncrypted,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    isFromMe = true
                )
                storyDao.upsertStory(entity)

                val sharePayload = StorySharePayload(
                    storyId = storyId,
                    contentType = "TEXT",
                    encryptedContent = partnerEncryptedB64,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    createdAt = entity.createdAt
                )
                val msg = SignalMessageWrapper(
                    type = "STORY_SHARE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(sharePayload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
            } catch (e: Exception) {
                android.util.Log.e("StoryVM", "postStory failed", e)
            }
        }
    }

    fun postMediaStory(mediaBytes: ByteArray, contentType: String, bgColor: String = "#000000") {
        viewModelScope.launch {
            try {
                val storyId = UUID.randomUUID().toString()
                val localEncrypted = cryptoManager.encryptLocal(mediaBytes)
                val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                val partnerEncryptedResult = cryptoManager.encryptMessage(partnerAddress, mediaBytes)
                if (partnerEncryptedResult.isFailure) {
                    android.util.Log.e("StoryVM", "Failed to encrypt media story for partner")
                    return@launch
                }
                val partnerEncryptedB64 = android.util.Base64.encodeToString(partnerEncryptedResult.getOrThrow(), android.util.Base64.NO_WRAP)
                val expiresAt = System.currentTimeMillis() + 86_400_000L

                val entity = StatusStoryEntity(
                    id = storyId,
                    authorId = myId,
                    contentType = contentType,
                    encryptedPayload = localEncrypted,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    isFromMe = true
                )
                storyDao.upsertStory(entity)

                val sharePayload = StorySharePayload(
                    storyId = storyId,
                    contentType = contentType,
                    encryptedContent = partnerEncryptedB64,
                    backgroundColor = bgColor,
                    expiresAt = expiresAt,
                    createdAt = entity.createdAt
                )
                val msg = SignalMessageWrapper(
                    type = "STORY_SHARE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(sharePayload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
            } catch (e: Exception) {
                android.util.Log.e("StoryVM", "postMediaStory failed", e)
            }
        }
    }

    fun decryptStory(entity: StatusStoryEntity): String {
        return try {
            String(cryptoManager.decryptLocal(entity.encryptedPayload), Charsets.UTF_8)
        } catch (_: Exception) {
            "🔒 Encrypted Story"
        }
    }

    fun decryptMedia(entity: StatusStoryEntity): ByteArray? {
        return try {
            cryptoManager.decryptLocal(entity.encryptedPayload)
        } catch (_: Exception) {
            null
        }
    }

    fun markViewed(storyId: String) {
        viewModelScope.launch {
            storyDao.markViewed(storyId, System.currentTimeMillis())
            // Notify partner
            val msg = SignalMessageWrapper(
                type = "STORY_VIEWED",
                senderId = myId,
                targetId = partnerId,
                payload = storyId
            )
            signalingClient.sendRawMessage(Json.encodeToString(msg))
        }
    }

    fun deleteMyStory(storyId: String) {
        viewModelScope.launch { storyDao.deleteStory(storyId) }
    }
}
