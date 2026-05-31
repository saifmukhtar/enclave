package dev.saifmukhtar.enclave.ui.profile

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.data.local.EnclaveDatabase
import dev.saifmukhtar.enclave.data.local.UserProfileEntity
import dev.saifmukhtar.enclave.network.BundleRepository
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import dev.saifmukhtar.enclave.webrtc.LenientJson

@Serializable
data class ProfileUpdatePayload(
    val username: String = "",
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val isOnline: Boolean,
    val lastSeen: Long
)

class ProfileViewModel(
    application: Application,
    private val database: EnclaveDatabase,
    private val bundleRepository: BundleRepository,
    private val signalingClient: SignalingClient,
    private val cryptoManager: dev.saifmukhtar.enclave.crypto.CryptoManager,
    private val myId: String,
    private val partnerId: String
) : AndroidViewModel(application) {

    private val profileDao = database.userProfileDao()

    val myProfile: StateFlow<UserProfileEntity?> = profileDao
        .getMyProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val partnerProfile: StateFlow<UserProfileEntity?> = profileDao
        .getPartnerProfile()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    init {
        // Load profiles from Supabase on startup
        viewModelScope.launch {
            refreshProfiles()
        }
        // Listen for partner profile updates over WebSocket
        observeProfileUpdates()
    }

    private fun observeProfileUpdates() {
        viewModelScope.launch {
            signalingClient.incomingRawMessages.collect { raw ->
                try {
                    val msg = LenientJson.decodeFromString<SignalMessageWrapper>(raw)
                    if (msg.senderId != partnerId) return@collect
                    when (msg.type) {
                        "PROFILE_UPDATE" -> {
                            msg.payload?.let { payload ->
                                val update = LenientJson.decodeFromString<ProfileUpdatePayload>(payload)
                                val existing = profileDao.getProfileSync(partnerId)
                                profileDao.upsertProfilePreservingLocal(
                                    UserProfileEntity(
                                        userId = partnerId,
                                        username = update.username.ifBlank { existing?.username ?: "" },
                                        displayName = update.displayName.ifBlank { existing?.displayName ?: "" },
                                        bio = update.bio.ifBlank { existing?.bio ?: "" },
                                        avatarUrl = update.avatarUrl.ifBlank { existing?.avatarUrl ?: "" },
                                        isOnline = update.isOnline,
                                        lastSeen = if (!update.isOnline) update.lastSeen else existing?.lastSeen ?: 0L,
                                        isMe = false
                                    )
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun decryptLocalAvatar(base64Str: String): ByteArray? {
        return try {
            cryptoManager.decryptLocal(base64Str)
        } catch (e: Exception) {
            android.util.Log.e("ProfileViewModel", "decryptLocalAvatar failed", e)
            null
        }
    }

    fun decryptPartnerAvatar(base64Str: String): ByteArray? {
        return try {
            val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
            val ciphertext = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
            cryptoManager.decryptMessage(partnerAddress, ciphertext).getOrNull()
        } catch (e: Exception) {
            android.util.Log.e("ProfileViewModel", "decryptPartnerAvatar failed", e)
            null
        }
    }

    fun saveProfile(username: String, displayName: String, bio: String, avatarBytes: ByteArray? = null) {
        if (_isSaving.value) return
        viewModelScope.launch {
            _isSaving.value = true
            try {
                val existing = profileDao.getProfileSync(myId)
                var newAvatarUrl = existing?.avatarUrl ?: ""
                var newAvatarLocalPath = existing?.avatarLocalPath ?: ""

                if (avatarBytes != null) {
                    // 1. Encrypt for partner (Signal E2EE)
                    val partnerAddress = org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1)
                    val encryptedBytes = cryptoManager.encryptMessage(partnerAddress, avatarBytes).getOrThrow()
                    newAvatarUrl = android.util.Base64.encodeToString(encryptedBytes, android.util.Base64.NO_WRAP)

                    // 2. Encrypt locally (Room local cache)
                    newAvatarLocalPath = cryptoManager.encryptLocal(avatarBytes)
                }

                // Persist locally
                val entity = UserProfileEntity(
                    userId = myId,
                    username = username,
                    displayName = displayName,
                    bio = bio,
                    avatarUrl = newAvatarUrl,
                    avatarLocalPath = newAvatarLocalPath,
                    isOnline = true,
                    isMe = true
                )
                profileDao.upsertProfile(entity)

                // Push to Supabase
                bundleRepository.uploadMyProfile(username, displayName, bio, newAvatarUrl)

                // Broadcast over WebSocket so partner gets it instantly
                val payload = ProfileUpdatePayload(
                    username = username,
                    displayName = displayName,
                    bio = bio,
                    avatarUrl = newAvatarUrl,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                val msg = SignalMessageWrapper(
                    type = "PROFILE_UPDATE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(payload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
            } catch (e: Exception) {
                android.util.Log.e("ProfileViewModel", "saveProfile failed", e)
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun broadcastOnline() {
        viewModelScope.launch {
            try {
                val me = myProfile.value ?: return@launch
                val payload = ProfileUpdatePayload(
                    username = me.username,
                    displayName = me.displayName,
                    bio = me.bio,
                    avatarUrl = me.avatarUrl,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                val msg = SignalMessageWrapper(
                    type = "PROFILE_UPDATE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(payload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
                bundleRepository.setOnline()
            } catch (_: Exception) {}
        }
    }

    fun broadcastOffline() {
        viewModelScope.launch {
            try {
                val me = myProfile.value
                val payload = ProfileUpdatePayload(
                    username = me?.username ?: "",
                    displayName = me?.displayName ?: "",
                    bio = me?.bio ?: "",
                    avatarUrl = me?.avatarUrl ?: "",
                    isOnline = false,
                    lastSeen = System.currentTimeMillis()
                )
                val msg = SignalMessageWrapper(
                    type = "PROFILE_UPDATE",
                    senderId = myId,
                    targetId = partnerId,
                    payload = Json.encodeToString(payload)
                )
                signalingClient.sendRawMessage(Json.encodeToString(msg))
                bundleRepository.setOffline()
                profileDao.updateLastSeen(myId, System.currentTimeMillis())
            } catch (_: Exception) {}
        }
    }

    private suspend fun refreshProfiles() {
        try {
            if (profileDao.getProfileSync(myId) == null) {
                profileDao.upsertProfile(UserProfileEntity(
                    userId = myId,
                    username = "me_${myId.take(5)}",
                    displayName = "Me",
                    bio = "Secure Enclave User",
                    isMe = true
                ))
            }
            val existingPartner = profileDao.getProfileSync(partnerId)
            if (existingPartner == null) {
                // Insert a temporary stub until the real profile is fetched from Supabase below
                profileDao.upsertProfile(UserProfileEntity(
                    userId = partnerId,
                    username = "partner_${partnerId.take(5)}",
                    displayName = "Partner",
                    bio = "End-to-End Encrypted Partner",
                    isMe = false
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("ProfileViewModel", "Error creating stub profiles", e)
        }

        try {
            // My profile — merge to preserve locally-set username, displayName, bio, avatarLocalPath
            bundleRepository.fetchMyProfile()?.let { profileDao.upsertProfilePreservingLocal(it) }
            // Partner profile — guard against blank partnerId (can be blank before auto-resolve completes)
            if (partnerId.isNotBlank()) {
                bundleRepository.fetchPartnerProfile(partnerId)?.let { profileDao.upsertProfilePreservingLocal(it) }
            } else {
                android.util.Log.d("ProfileViewModel", "Skipping fetchPartnerProfile: partnerId is blank")
            }
        } catch (e: Exception) {
            android.util.Log.w("ProfileViewModel", "refreshProfiles failed (non-critical)", e)
        }
    }
}
