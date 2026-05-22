package com.enclave.app.data.vault

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import java.io.File
import java.io.InputStream
import java.io.OutputStream

/**
 * Handles all IO for The Vault using hardware-backed EncryptedFile.
 */
class EncryptedFileManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val vaultDirectory = File(context.filesDir, "vault").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Creates an EncryptedFile reference.
     */
    private fun getEncryptedFile(fileName: String): EncryptedFile {
        val file = File(vaultDirectory, fileName)
        return EncryptedFile.Builder(
            context,
            file,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    /**
     * Writes raw bytes securely to disk.
     */
    fun writeSecureFile(fileName: String, data: ByteArray) {
        val encryptedFile = getEncryptedFile(fileName)
        
        // If file already exists, delete it before writing new EncryptedFile
        val rawFile = File(vaultDirectory, fileName)
        if (rawFile.exists()) rawFile.delete()

        encryptedFile.openFileOutput().use { outputStream ->
            outputStream.write(data)
            outputStream.flush()
        }
    }

    /**
     * Reads decrypted bytes directly into memory.
     */
    fun readSecureFile(fileName: String): ByteArray {
        val encryptedFile = getEncryptedFile(fileName)
        return encryptedFile.openFileInput().use { inputStream ->
            inputStream.readBytes()
        }
    }
    
    /**
     * Returns an InputStream for the decrypted file (useful for large media).
     */
    fun getSecureInputStream(fileName: String): InputStream {
        return getEncryptedFile(fileName).openFileInput()
    }
    
    /**
     * Lists all secure files in the vault.
     */
    fun listSecureFiles(): List<String> {
        return vaultDirectory.listFiles()?.map { it.name } ?: emptyList()
    }
    
    /**
     * Returns the raw File reference for lower-level operations (like shredding).
     */
    fun getRawFile(fileName: String): File {
        return File(vaultDirectory, fileName)
    }
}
