package dev.saifmukhtar.enclave.ui.lounge

import kotlinx.serialization.json.Json


import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.saifmukhtar.enclave.crypto.CryptoManager
import dev.saifmukhtar.enclave.network.BundleRepository
import dev.saifmukhtar.enclave.network.LoungeQueueItem
import dev.saifmukhtar.enclave.network.LoungeSong
import dev.saifmukhtar.enclave.webrtc.LenientJson
import dev.saifmukhtar.enclave.webrtc.SignalMessageWrapper
import dev.saifmukhtar.enclave.webrtc.SignalingClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoungeMusicViewModel(
    private val signalingClient: SignalingClient,
    private val cryptoManager: CryptoManager,
    private val bundleRepository: BundleRepository?,
    private val partnerId: String,
    val myId: String,
    private val loungeSyncUseCase: LoungeSyncUseCase
) : ViewModel() {

    // --- Music Library States ---
    private val _loungeSongs = MutableStateFlow<List<LoungeSong>>(emptyList())
    val loungeSongs: StateFlow<List<LoungeSong>> = _loungeSongs.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    // --- Playlist Queue States ---
    private val _playlistQueue = MutableStateFlow<List<LoungeQueueItem>>(emptyList())
    val playlistQueue: StateFlow<List<LoungeQueueItem>> = _playlistQueue.asStateFlow()

    init {
        refreshSongs()
        refreshQueue()

        viewModelScope.launch {
            loungeSyncUseCase.observeEvents().collect { event ->
                when (event) {
                    is LoungeIncomingEvent.PlaylistUpdate -> refreshSongs()
                    is LoungeIncomingEvent.QueueUpdate -> refreshQueue()
                    else -> {}
                }
            }
        }
    }

    private fun refreshSongs() {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _loungeSongs.value = repo.fetchLoungeSongs()
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to fetch songs", e)
            }
        }
    }

    fun uploadAndAddSong(title: String, bytes: ByteArray) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _isUploading.value = true
            try {
                val safeTitle = title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
                val url = repo.uploadMusicFile("${safeTitle}.mp3", bytes)
                repo.insertLoungeSong(title, url)
                refreshSongs()
                sendLoungeMessage("LOUNGE_PLAYLIST_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to upload song", e)
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun deleteSong(song: dev.saifmukhtar.enclave.network.LoungeSong) {
        if (song.uploaded_by != myId) return
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteLoungeSong(song.id.orEmpty())
                refreshSongs()
                sendLoungeMessage("LOUNGE_PLAYLIST_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to delete song", e)
            }
        }
    }

    private fun refreshQueue() {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _playlistQueue.value = repo.fetchLoungeQueue()
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to fetch queue", e)
            }
        }
    }

    fun addToQueue(songId: String) {
        if (bundleRepository == null) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val repo = bundleRepository ?: return@launch
                repo.insertQueueItem(songId)
                refreshQueue()
                sendLoungeMessage("LOUNGE_QUEUE_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to add to queue", e)
            }
        }
    }

    fun removeFromQueue(queueItemId: String) {
        val repo = bundleRepository ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repo.deleteQueueItem(queueItemId)
                refreshQueue()
                sendLoungeMessage("LOUNGE_QUEUE_UPDATE", "")
            } catch (e: Exception) {
                android.util.Log.e("LoungeMusicViewModel", "Failed to remove from queue", e)
            }
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _playlistQueue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _playlistQueue.value = list
            sendLoungeMessage("LOUNGE_QUEUE_UPDATE", "")
        }
    }

    private fun sendLoungeMessage(type: String, payload: String) {
        val msg = SignalMessageWrapper(
            senderId = myId,
            type = type,
            payload = payload
        )
        val jsonStr = Json.encodeToString(SignalMessageWrapper.serializer(), msg)
        val encryptedResult = cryptoManager.encryptMessage(
            org.signal.libsignal.protocol.SignalProtocolAddress(partnerId, 1),
            jsonStr.toByteArray(Charsets.UTF_8)
        )
        if (encryptedResult.isSuccess) {
            viewModelScope.launch {
                signalingClient.sendEncryptedMessage(partnerId, encryptedResult.getOrThrow(), "LOUNGE")
            }
        }
    }

    class Factory(
        private val signalingClient: SignalingClient,
        private val cryptoManager: CryptoManager,
        private val bundleRepository: BundleRepository?,
        private val partnerId: String,
        private val myId: String,
        private val loungeSyncUseCase: LoungeSyncUseCase
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LoungeMusicViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LoungeMusicViewModel(signalingClient, cryptoManager, bundleRepository, partnerId, myId, loungeSyncUseCase) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
