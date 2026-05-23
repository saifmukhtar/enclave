package com.enclave.app.network

import android.util.Base64
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.crypto.EnclaveSignalStore
import com.enclave.app.data.local.UserProfileEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.ecc.Curve

@Serializable
data class SignedPreKeyUpload(val id: Int, val key: String, val signature: String)

@Serializable
data class OneTimePreKeyUpload(val id: Int, val key: String)

@Serializable
data class KeyBundleUpload(
    val user_id: String,
    val identity_key: String,
    val signed_pre_key: SignedPreKeyUpload,
    val one_time_pre_keys: List<OneTimePreKeyUpload>
)

@Serializable
data class UserProfile(
    val id: String,
    val push_token: String = "",
    val fcm_token: String = "",
    val username: String? = null,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val last_seen: String? = null,
    val is_online: Boolean? = null,
    val love_language: String? = null,
    val location_city: String? = null
)

@Serializable
data class ProfileUpdate(
    val id: String,
    val username: String? = null,
    val display_name: String? = null,
    val bio: String? = null,
    val avatar_url: String? = null,
    val is_online: Boolean? = null,
    val love_language: String? = null,
    val location_city: String? = null
)

/**
 * Represents a shared lounge song entry in the Supabase lounge_songs table.
 * url is a public storage URL that never expires (public bucket).
 */
@Serializable
data class LoungeSong(
    val id: String? = null,
    val title: String = "",
    val url: String = "",
    val uploaded_by: String = "",
    val created_at: String? = null
)

@Serializable
data class LoungeDrawing(
    val id: String? = null,
    val title: String = "",
    val url: String = "",
    val uploaded_by: String = "",
    val created_at: String? = null
)

@Serializable
data class ScrapbookEntry(
    val id: String? = null,
    val caption: String = "",
    val photo_url: String = "",
    val event_date: String = "",
    val uploaded_by: String = "",
    val created_at: String? = null
)

@Serializable
data class LoungeQueueItem(
    val id: String? = null,
    val song_id: String = "",
    val queued_by: String = "",
    val created_at: String? = null
)


/**
 * Manages Signal crypto Key Bundles and User Profile syncing via Supabase.
 */
class BundleRepository(
    private val supabase: SupabaseClient,
    private val signalStore: EnclaveSignalStore,
    private val cryptoManager: CryptoManager
) {

    private suspend fun awaitAuth() {
        while (supabase.auth.currentUserOrNull() == null) {
            android.util.Log.d("BundleRepository", "Waiting for Supabase auth to be ready...")
            delay(1000)
        }
        android.util.Log.d("BundleRepository", "Supabase auth is ready!")
    }

    /**
     * Uploads the locally generated keys to the public registry.
     */
    suspend fun uploadLocalBundle() = withContext(Dispatchers.IO) {
        awaitAuth()
        val currentUser = supabase.auth.currentUserOrNull() ?: throw IllegalStateException("Not logged in to Supabase")
        
        val identityKeyPair = signalStore.identityKeyPair
        val identityKeyB64 = Base64.encodeToString(identityKeyPair.publicKey.serialize(), Base64.NO_WRAP)
        
        val signedPreKeys = signalStore.loadSignedPreKeys()
        if (signedPreKeys.isEmpty()) throw IllegalStateException("No signed pre-keys generated")
        val signedPreKey = signedPreKeys.first()
        val spkUpload = SignedPreKeyUpload(
            id = signedPreKey.id,
            key = Base64.encodeToString(signedPreKey.keyPair.publicKey.serialize(), Base64.NO_WRAP),
            signature = Base64.encodeToString(signedPreKey.signature, Base64.NO_WRAP)
        )
        
        val preKeys = signalStore.loadPreKeys()
        val opkUploads = preKeys.map {
            OneTimePreKeyUpload(
                id = it.id,
                key = Base64.encodeToString(it.keyPair.publicKey.serialize(), Base64.NO_WRAP)
            )
        }
        
        val bundle = KeyBundleUpload(
            user_id = currentUser.id,
            identity_key = identityKeyB64,
            signed_pre_key = spkUpload,
            one_time_pre_keys = opkUploads
        )
        
        supabase.postgrest["pre_key_bundles"].upsert(bundle)
    }

    /**
     * Fetches the partner's Key Bundle and triggers X3DH Handshake.
     */
    suspend fun fetchPartnerBundleAndBuildSession(partnerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            awaitAuth()
            val bundle = supabase.postgrest["pre_key_bundles"]
                .select { filter { eq("user_id", partnerId) } }
                .decodeSingle<KeyBundleUpload>()
                
            val identityKey = IdentityKey(Base64.decode(bundle.identity_key, Base64.NO_WRAP), 0)
            
            val spkRecord = bundle.signed_pre_key
            val spkPubKey = Curve.decodePoint(Base64.decode(spkRecord.key, Base64.NO_WRAP), 0)
            val spkSignature = Base64.decode(spkRecord.signature, Base64.NO_WRAP)
            
            val opkRecord = bundle.one_time_pre_keys.firstOrNull()
            
            val preKeyBundle = if (opkRecord != null) {
                val opkPubKey = Curve.decodePoint(Base64.decode(opkRecord.key, Base64.NO_WRAP), 0)
                PreKeyBundle(0, 1, opkRecord.id, opkPubKey, spkRecord.id, spkPubKey, spkSignature, identityKey)
            } else {
                PreKeyBundle(0, 1, -1, null, spkRecord.id, spkPubKey, spkSignature, identityKey)
            }
            
            val partnerAddress = SignalProtocolAddress(partnerId, 1)
            cryptoManager.buildSession(partnerAddress, preKeyBundle)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Synchronizes the dynamic Firebase Cloud Messaging token into the Profiles table.
     */
    suspend fun syncFcmToken(fcmToken: String) = withContext(Dispatchers.IO) {
        val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
        val profile = UserProfile(
            id = currentUser.id,
            push_token = fcmToken,
            fcm_token = fcmToken
        )
        supabase.postgrest["profiles"].upsert(profile)
    }

    // ─── Profile Management ───────────────────────────────────────────────────

    /**
     * Upload / update my profile (username, display name, bio, avatar URL) to Supabase.
     */
    suspend fun uploadMyProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String
    ) = withContext(Dispatchers.IO) {
        val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
        val update = ProfileUpdate(
            id = currentUser.id,
            username = username.lowercase().trim(),
            display_name = displayName.trim(),
            bio = bio.trim(),
            avatar_url = avatarUrl,
            is_online = true
        )
        supabase.postgrest["profiles"].upsert(update)
    }

    /**
     * Fetch the partner's profile from Supabase.
     */
    suspend fun fetchPartnerProfile(partnerId: String): UserProfileEntity? = withContext(Dispatchers.IO) {
        try {
            awaitAuth()
            val profile = supabase.postgrest["profiles"]
                .select { filter { eq("id", partnerId) } }
                .decodeSingleOrNull<UserProfile>()
            profile?.let {
                UserProfileEntity(
                    userId = it.id,
                    username = it.username ?: "",
                    displayName = it.display_name ?: "",
                    bio = it.bio ?: "",
                    avatarUrl = it.avatar_url ?: "",
                    isOnline = it.is_online ?: false,
                    isMe = false
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchPartnerProfile failed", e)
            null
        }
    }

    /**
     * Fetch my own profile from Supabase.
     */
    suspend fun fetchMyProfile(): UserProfileEntity? = withContext(Dispatchers.IO) {
        try {
            val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext null
            val profile = supabase.postgrest["profiles"]
                .select { filter { eq("id", currentUser.id) } }
                .decodeSingleOrNull<UserProfile>()
            profile?.let {
                UserProfileEntity(
                    userId = it.id,
                    username = it.username ?: "",
                    displayName = it.display_name ?: currentUser.email?.substringBefore("@") ?: "",
                    bio = it.bio ?: "",
                    avatarUrl = it.avatar_url ?: "",
                    isOnline = true,
                    isMe = true
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchMyProfile failed", e)
            null
        }
    }

    /**
     * Mark myself as offline and update last_seen timestamp.
     */
    suspend fun setOffline() = withContext(Dispatchers.IO) {
        try {
            val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
            supabase.postgrest["profiles"].update(
                { set("is_online", false) }
            ) { filter { eq("id", currentUser.id) } }
        } catch (e: Exception) {
            android.util.Log.w("BundleRepository", "setOffline failed (non-critical)", e)
        }
    }

    /**
     * Mark myself as online.
     */
    suspend fun setOnline() = withContext(Dispatchers.IO) {
        try {
            val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
            supabase.postgrest["profiles"].update(
                { set("is_online", true) }
            ) { filter { eq("id", currentUser.id) } }
        } catch (e: Exception) {
            android.util.Log.w("BundleRepository", "setOnline failed (non-critical)", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Music Lounge API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Fetch all shared lounge songs from Supabase, ordered by upload date.
     */
    suspend fun fetchLoungeSongs(): List<LoungeSong> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_songs"]
                .select {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<LoungeSong>()
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchLoungeSongs failed", e)
            emptyList()
        }
    }

    /**
     * Insert a new song record into the lounge_songs index table.
     */
    suspend fun insertLoungeSong(title: String, url: String) = withContext(Dispatchers.IO) {
        try {
            val myId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            supabase.postgrest["lounge_songs"].insert(
                LoungeSong(title = title, url = url, uploaded_by = myId)
            )
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "insertLoungeSong failed", e)
        }
    }

    /**
     * Delete a lounge song record (RLS ensures only uploader can delete).
     */
    suspend fun deleteLoungeSong(id: String) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_songs"].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "deleteLoungeSong failed", e)
        }
    }

    /**
     * Upload a music file to the public Supabase 'music' storage bucket.
     * Returns the public URL for streaming (no token expiry).
     */
    suspend fun uploadMusicFile(fileName: String, fileBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val myId = supabase.auth.currentUserOrNull()?.id
            ?: error("Not authenticated — cannot upload music")
        val path = "$myId/$fileName"
        supabase.storage.from("music").upload(path, fileBytes, upsert = true)
        supabase.storage.from("music").publicUrl(path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawings Board API
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchLoungeDrawings(): List<LoungeDrawing> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_drawings"]
                .select {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<LoungeDrawing>()
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchLoungeDrawings failed", e)
            emptyList()
        }
    }

    suspend fun insertLoungeDrawing(title: String, url: String) = withContext(Dispatchers.IO) {
        try {
            val myId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            supabase.postgrest["lounge_drawings"].insert(
                LoungeDrawing(title = title, url = url, uploaded_by = myId)
            )
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "insertLoungeDrawing failed", e)
        }
    }

    suspend fun deleteLoungeDrawing(id: String) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_drawings"].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "deleteLoungeDrawing failed", e)
        }
    }

    suspend fun uploadDrawingFile(fileName: String, fileBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val myId = supabase.auth.currentUserOrNull()?.id
            ?: error("Not authenticated — cannot upload drawing")
        val path = "$myId/$fileName"
        supabase.storage.from("drawings").upload(path, fileBytes, upsert = true)
        supabase.storage.from("drawings").publicUrl(path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Scrapbook API
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchScrapbookEntries(): List<ScrapbookEntry> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_scrapbook"]
                .select {
                    order("event_date", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                }
                .decodeList<ScrapbookEntry>()
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchScrapbookEntries failed", e)
            emptyList()
        }
    }

    suspend fun insertScrapbookEntry(caption: String, photoUrl: String, eventDate: String) = withContext(Dispatchers.IO) {
        try {
            val myId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            supabase.postgrest["lounge_scrapbook"].insert(
                ScrapbookEntry(caption = caption, photo_url = photoUrl, event_date = eventDate, uploaded_by = myId)
            )
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "insertScrapbookEntry failed", e)
        }
    }

    suspend fun deleteScrapbookEntry(id: String) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_scrapbook"].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "deleteScrapbookEntry failed", e)
        }
    }

    suspend fun uploadScrapbookPhoto(fileName: String, fileBytes: ByteArray): String = withContext(Dispatchers.IO) {
        val myId = supabase.auth.currentUserOrNull()?.id
            ?: error("Not authenticated — cannot upload photo")
        val path = "$myId/$fileName"
        supabase.storage.from("scrapbook").upload(path, fileBytes, upsert = true)
        supabase.storage.from("scrapbook").publicUrl(path)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Music Queue API
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun fetchLoungeQueue(): List<LoungeQueueItem> = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_music_queue"]
                .select {
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                }
                .decodeList<LoungeQueueItem>()
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "fetchLoungeQueue failed", e)
            emptyList()
        }
    }

    suspend fun insertQueueItem(songId: String) = withContext(Dispatchers.IO) {
        try {
            val myId = supabase.auth.currentUserOrNull()?.id ?: return@withContext
            val item = LoungeQueueItem(song_id = songId, queued_by = myId)
            supabase.postgrest["lounge_music_queue"].insert(item)
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "insertQueueItem failed", e)
        }
    }

    suspend fun deleteQueueItem(id: String) = withContext(Dispatchers.IO) {
        try {
            supabase.postgrest["lounge_music_queue"].delete {
                filter { eq("id", id) }
            }
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "deleteQueueItem failed", e)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Love Language & Location City Profiles updates
    // ─────────────────────────────────────────────────────────────────────────

    suspend fun updateLoveLanguage(loveLanguage: String) = withContext(Dispatchers.IO) {
        try {
            val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
            val update = ProfileUpdate(
                id = currentUser.id,
                love_language = loveLanguage
            )
            supabase.postgrest["profiles"].upsert(update)
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "updateLoveLanguage failed", e)
        }
    }

    suspend fun updateLocationCity(locationCity: String) = withContext(Dispatchers.IO) {
        try {
            val currentUser = supabase.auth.currentUserOrNull() ?: return@withContext
            val update = ProfileUpdate(
                id = currentUser.id,
                location_city = locationCity
            )
            supabase.postgrest["profiles"].upsert(update)
        } catch (e: Exception) {
            android.util.Log.e("BundleRepository", "updateLocationCity failed", e)
        }
    }
}

