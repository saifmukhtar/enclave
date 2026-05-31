package dev.saifmukhtar.enclave.domain

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import dev.saifmukhtar.enclave.data.local.MediaMetadataEntity
import dev.saifmukhtar.enclave.data.local.MessageDao
import dev.saifmukhtar.enclave.data.vault.EncryptedFileManager

/**
 * Handles the Universal Download Pipeline.
 */
class MediaExportUseCase(
    private val context: Context,
    private val encryptedFileManager: EncryptedFileManager,
    private val messageDao: MessageDao
) {

    /**
     * Extracts an ephemeral image, strips its expiry, and keeps it E2EE locked in The Vault.
     */
    suspend fun saveToVault(@Suppress("UNUSED_PARAMETER") mediaMetadata: MediaMetadataEntity) {
        // 1. Decrypt from Double Ratchet (if it's an incoming network payload) or read from cache
        // 2. Write to EncryptedFile using EncryptedFileManager
        // 3. Update Room DB to remove the ephemeral timestamp
        
        // Mock Implementation for Scaffold
    }

    /**
     * Completely decrypts a file and writes it to the public Android MediaStore.
     * WARNING: This permanently removes E2EE protections for this file.
     */
    suspend fun exportToPhone(mediaMetadata: MediaMetadataEntity, plaintextData: ByteArray) {
        val resolver = context.contentResolver
        val extension = when {
            mediaMetadata.mimeType.startsWith("image/") -> ".jpg"
            mediaMetadata.mimeType.startsWith("video/") -> ".mp4"
            mediaMetadata.mimeType.startsWith("audio/") -> ".m4a"
            mediaMetadata.mimeType == "application/pdf" -> ".pdf"
            else -> ".bin"
        }
        
        val directory = when {
            mediaMetadata.mimeType.startsWith("image/") -> Environment.DIRECTORY_PICTURES
            mediaMetadata.mimeType.startsWith("video/") -> Environment.DIRECTORY_MOVIES
            mediaMetadata.mimeType.startsWith("audio/") -> Environment.DIRECTORY_MUSIC
            mediaMetadata.mimeType == "application/pdf" -> Environment.DIRECTORY_DOCUMENTS
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "EnclaveExport_${System.currentTimeMillis()}$extension")
            put(MediaStore.MediaColumns.MIME_TYPE, mediaMetadata.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directory/Enclave")
        }

        val uri = resolver.insert(
            if (mediaMetadata.mimeType.startsWith("video/")) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else if (mediaMetadata.mimeType.startsWith("audio/")) MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else if (mediaMetadata.mimeType.startsWith("image/")) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            contentValues
        )
        uri?.let {
            resolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(plaintextData)
                outputStream.flush()
            }
        }
    }
}
