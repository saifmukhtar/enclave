package com.enclave.app.ui.lounge

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.crypto.CryptoManager
import com.enclave.app.data.local.EnclaveDatabase
import com.enclave.app.data.local.LetterDao
import com.enclave.app.data.local.LetterEntity
import com.enclave.app.network.BundleRepository
import com.enclave.app.network.ScrapbookEntry
import com.enclave.app.webrtc.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class LoungeMediaViewModel(
    application: Application,
    private val signalingClient: SignalingClient,
    private val cryptoManager: CryptoManager,
    private val letterDao: LetterDao,
    private val database: EnclaveDatabase,
    private val partnerId: String,
    val myId: String,
    private val loungeSyncUseCase: com.enclave.app.ui.lounge.LoungeSyncUseCase,
    private val bundleRepository: BundleRepository? = null
) : AndroidViewModel(application) {

    class Factory(
        private val application: Application,
        private val signalingClient: SignalingClient,
        private val cryptoManager: CryptoManager,
        private val letterDao: LetterDao,
        private val database: EnclaveDatabase,
        private val partnerId: String,
        private val myId: String,
        private val loungeSyncUseCase: com.enclave.app.ui.lounge.LoungeSyncUseCase,
        private val bundleRepository: BundleRepository? = null
    ) : androidx.lifecycle.ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return LoungeMediaViewModel(
                application, signalingClient, cryptoManager, letterDao, database, partnerId, myId, loungeSyncUseCase, bundleRepository
            ) as T
        }
    }

    // --- Scrapbook States ---
    val scrapbookEntries = MutableStateFlow<List<ScrapbookEntry>>(emptyList())
    val isScrapbookUploading = MutableStateFlow(false)

    // --- Daily Letter Capsule States ---
    val decryptedLettersFlow = letterDao.getAllLettersFlow()

    // --- E2EE Shared Notes ---
    val encryptedNotesFlow = database.encryptedNoteDao().getAllNotesFlow()

    val myProfile: StateFlow<com.enclave.app.data.local.UserProfileEntity?> = database.userProfileDao()
        .getMyProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val partnerProfile: StateFlow<com.enclave.app.data.local.UserProfileEntity?> = database.userProfileDao()
        .getPartnerProfile()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        refreshScrapbook()
        observeSignaling()
    }

    private fun observeSignaling() {
        viewModelScope.launch {
            loungeSyncUseCase.observeEvents().collect { event ->
                when (event) {
                    is LoungeIncomingEvent.LetterSend -> handleIncomingLetter(event.payload)
                    is LoungeIncomingEvent.NoteSync -> handleIncomingNote(event.payload)
                    is LoungeIncomingEvent.NoteDelete -> handleIncomingNoteDelete(event.id)
                    is LoungeIncomingEvent.ScrapbookUpdate -> refreshScrapbook()
                    else -> {}
                }
            }
        }
    }

    private fun sendLoungeMessage(type: String, payload: String) {
        viewModelScope.launch {
            val wrapper = com.enclave.app.webrtc.SignalMessageWrapper(
                type = type,
                senderId = myId,
                targetId = partnerId,
                payload = payload
            )
            signalingClient.sendRawMessage(Json.encodeToString(wrapper))
        }
    }

    // --- Scrapbook ---
    fun refreshScrapbook() {
        val repo = bundleRepository ?: return
        viewModelScope.launch {
            try {
                scrapbookEntries.value = repo.fetchScrapbookEntries()
            } catch (e: Exception) {
                android.util.Log.e("LoungeMediaViewModel", "refreshScrapbook failed", e)
            }
        }
    }

    fun uploadAndAddScrapbook(caption: String, eventDate: String, bytes: ByteArray) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            isScrapbookUploading.value = true
            try {
                val fileName = "scrapbook_${System.currentTimeMillis()}.jpg"
                val url = repo.uploadScrapbookPhoto(fileName, bytes)
                repo.insertScrapbookEntry(caption, url, eventDate)
                refreshScrapbook()
                sendLoungeMessage("LOUNGE_SCRAPBOOK_UPDATE", "")
            } catch (t: Throwable) {
                android.util.Log.e("LoungeMediaViewModel", "uploadAndAddScrapbook failed", t)
            } finally {
                isScrapbookUploading.value = false
            }
        }
    }

    fun deleteScrapbookEntry(entry: ScrapbookEntry) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteScrapbookEntry(entry.id.orEmpty())
                refreshScrapbook()
                sendLoungeMessage("LOUNGE_SCRAPBOOK_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeMediaViewModel", "deleteScrapbookEntry failed", e)
            }
        }
    }

    // --- Encrypted Daily Love Letters Capsuling ---
    fun sendLetter(content: String) {
        viewModelScope.launch {
            val payload = SyncedLetterPayload(myId, content)
            sendLoungeMessage("LOUNGE_LETTER_SEND", Json.encodeToString(payload))

            val encryptedB64 = cryptoManager.encryptLocal(content.toByteArray(Charsets.UTF_8))
            val letterEntity = LetterEntity(
                id = UUID.randomUUID().toString(),
                senderId = myId,
                ciphertext = encryptedB64,
                createdAt = System.currentTimeMillis(),
                isRead = true
            )
            letterDao.insertLetter(letterEntity)
        }
    }

    fun decryptLetter(ciphertext: String): String {
        return try {
            val bytes = cryptoManager.decryptLocal(ciphertext)
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Decryption Error"
        }
    }

    fun markLetterAsRead(id: String) {
        viewModelScope.launch {
            letterDao.markAsRead(id)
        }
    }

    fun deleteLetter(id: String) {
        viewModelScope.launch {
            letterDao.deleteLetterById(id)
        }
    }
    
    fun handleIncomingLetter(payload: SyncedLetterPayload) {
        viewModelScope.launch(Dispatchers.IO) {
            val encryptedB64 = cryptoManager.encryptLocal(payload.plainContent.toByteArray(Charsets.UTF_8))
            val letterEntity = LetterEntity(
                id = UUID.randomUUID().toString(),
                senderId = payload.senderId,
                ciphertext = encryptedB64,
                createdAt = System.currentTimeMillis(),
                isRead = false
            )
            letterDao.insertLetter(letterEntity)
        }
    }

    // --- Encrypted Shared Notes ---
    fun saveEncryptedNote(id: String = UUID.randomUUID().toString(), title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val keyBase64 = cryptoManager.loadVaultKey()
                if (keyBase64 == null) return@launch
                val keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP)

                val titleEncrypted = com.enclave.app.crypto.VaultCipher.encrypt(title.toByteArray(Charsets.UTF_8), keyBytes)
                val contentEncrypted = com.enclave.app.crypto.VaultCipher.encrypt(content.toByteArray(Charsets.UTF_8), keyBytes)

                val entity = com.enclave.app.data.local.EncryptedNoteEntity(
                    id = id,
                    titlePayload = titleEncrypted,
                    contentPayload = contentEncrypted,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    authorId = myId,
                    isSynced = true
                )
                database.encryptedNoteDao().insertNote(entity)

                val payload = SyncedNotePayload(
                    id = id,
                    titlePayloadBase64 = android.util.Base64.encodeToString(titleEncrypted, android.util.Base64.NO_WRAP),
                    contentPayloadBase64 = android.util.Base64.encodeToString(contentEncrypted, android.util.Base64.NO_WRAP),
                    authorId = myId
                )
                sendLoungeMessage("LOUNGE_NOTE_SYNC", Json.encodeToString(payload))
            } catch (e: Exception) {
                android.util.Log.e("LoungeMediaViewModel", "Failed to save encrypted note", e)
            }
        }
    }

    fun deleteEncryptedNote(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.encryptedNoteDao().deleteNote(id)
            sendLoungeMessage("LOUNGE_NOTE_DELETE", id)
        }
    }

    fun decryptNoteField(encryptedBytes: ByteArray): String {
        return try {
            val keyBase64 = cryptoManager.loadVaultKey()
                ?: return "Locked"
            val keyBytes = android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP)
            val decryptedBytes = com.enclave.app.crypto.VaultCipher.decrypt(encryptedBytes, keyBytes)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            "Decryption failed"
        }
    }
    
    fun handleIncomingNote(payload: SyncedNotePayload) {
        viewModelScope.launch(Dispatchers.IO) {
            val titleBytes = android.util.Base64.decode(payload.titlePayloadBase64, android.util.Base64.NO_WRAP)
            val contentBytes = android.util.Base64.decode(payload.contentPayloadBase64, android.util.Base64.NO_WRAP)

            val entity = com.enclave.app.data.local.EncryptedNoteEntity(
                id = payload.id,
                titlePayload = titleBytes,
                contentPayload = contentBytes,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                authorId = payload.authorId,
                isSynced = true
            )
            database.encryptedNoteDao().insertNote(entity)
        }
    }
    
    fun handleIncomingNoteDelete(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            database.encryptedNoteDao().deleteNote(id)
        }
    }
}
