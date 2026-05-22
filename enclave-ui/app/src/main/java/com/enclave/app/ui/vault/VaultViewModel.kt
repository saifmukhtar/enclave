package com.enclave.app.ui.vault

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.enclave.app.data.local.MediaMetadataEntity
import com.enclave.app.data.vault.VaultRepository
import com.enclave.app.data.vault.ImportResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(val vaultRepository: VaultRepository) : ViewModel() {

    private val _selectedFolder = MutableStateFlow("General")
    val selectedFolder: StateFlow<String> = _selectedFolder.asStateFlow()

    private val _pendingPermissionIntent = MutableStateFlow<IntentSender?>(null)
    val pendingPermissionIntent: StateFlow<IntentSender?> = _pendingPermissionIntent.asStateFlow()

    val folders: StateFlow<List<String>> = vaultRepository.mediaMetadataDao.getAllFolders()
        .map { list ->
            if (list.isEmpty() || !list.contains("General")) {
                listOf("General") + list.filter { it != "General" }
            } else {
                listOf("General") + list.filter { it != "General" }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("General"))

    val vaultItems: StateFlow<List<MediaMetadataEntity>> = _selectedFolder
        .flatMapLatest { folder ->
            vaultRepository.mediaMetadataDao.getMediaInFolder(folder)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectFolder(folder: String) {
        _selectedFolder.value = folder
    }

    fun createFolder(folderName: String) {
        // Folders are dynamically registered when an item is added or migrated.
        // We set the active folder to the new name so subsequent imports go there.
        _selectedFolder.value = folderName
    }

    fun moveItemsToFolder(mediaIds: List<String>, targetFolder: String) {
        viewModelScope.launch {
            mediaIds.forEach { mediaId ->
                vaultRepository.mediaMetadataDao.updateMediaFolder(mediaId, targetFolder)
            }
        }
    }

    fun shredItems(fileNames: List<String>) {
        viewModelScope.launch {
            fileNames.forEach { fileName ->
                vaultRepository.shredFile(fileName)
            }
        }
    }

    fun exportItems(fileNames: List<String>, context: Context) {
        viewModelScope.launch {
            fileNames.forEach { fileName ->
                vaultRepository.exportToPublic(fileName, context)
            }
        }
    }

    fun clearVaultItems() {
        vaultRepository.clearMemoryCache()
    }

    fun shredFile(fileName: String) {
        viewModelScope.launch {
            vaultRepository.shredFile(fileName)
        }
    }

    fun exportToPublic(fileName: String, context: Context) {
        viewModelScope.launch {
            vaultRepository.exportToPublic(fileName, context)
        }
    }

    fun importPublicMedia(uris: List<Uri>, context: Context) {
        viewModelScope.launch {
            when (val result = vaultRepository.importPublicMedia(uris, context, _selectedFolder.value)) {
                is ImportResult.Success -> {
                    // Reactive flows auto-refresh!
                }
                is ImportResult.RequiresPermission -> {
                    _pendingPermissionIntent.value = result.intentSender
                }
                is ImportResult.Error -> {
                    // Fail gracefully
                }
            }
        }
    }

    fun clearPendingPermissionIntent() {
        _pendingPermissionIntent.value = null
    }
}
