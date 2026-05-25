package com.enclave.app.data.vault

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.util.LruCache
import android.media.MediaMetadataRetriever
import com.enclave.app.crypto.ExifStripper
import com.enclave.app.data.local.MediaMetadataDao
import com.enclave.app.data.local.MediaMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.UUID

sealed class ImportResult {
    object Success : ImportResult()
    data class RequiresPermission(val intentSender: IntentSender) : ImportResult()
    data class Error(val exception: Exception) : ImportResult()
}

class VaultRepository(
    private val context: Context,
    val encryptedFileManager: EncryptedFileManager,
    val mediaMetadataDao: MediaMetadataDao,
    val bundleRepository: com.enclave.app.network.BundleRepository
) {

    private fun getSharedVaultKey(): ByteArray? {
        val prefs = context.getSharedPreferences("enclave_prefs", Context.MODE_PRIVATE)
        val keyBase64 = prefs.getString("vault_key", null) ?: return null
        return try {
            android.util.Base64.decode(keyBase64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private val memoryCache: LruCache<String, Bitmap> by lazy {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 8 // Use 1/8th of JVM max memory
        object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }
    }

    fun clearMemoryCache() {
        memoryCache.evictAll()
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    suspend fun getVaultFiles(): List<String> = withContext(Dispatchers.IO) {
        encryptedFileManager.listSecureFiles()
    }

    suspend fun saveSecureFile(fileName: String, data: ByteArray) = withContext(Dispatchers.IO) {
        val vaultKey = getSharedVaultKey()
        if (vaultKey != null) {
            try {
                val encrypted = com.enclave.app.crypto.VaultCipher.encrypt(data, vaultKey)
                val rawFile = encryptedFileManager.getRawFile(fileName)
                if (rawFile.exists()) rawFile.delete()
                rawFile.writeBytes(encrypted)
            } catch (e: Exception) {
                android.util.Log.e("VaultRepository", "VaultCipher encryption failed, falling back to EncryptedFile", e)
                encryptedFileManager.writeSecureFile(fileName, data)
            }
        } else {
            encryptedFileManager.writeSecureFile(fileName, data)
        }
    }

    suspend fun getDecryptedImageBitmap(fileName: String, reqWidth: Int = 512, reqHeight: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val cacheKey = if (reqWidth <= 0 || reqHeight <= 0) "${fileName}_full" else "${fileName}_${reqWidth}_${reqHeight}"
            val cached = memoryCache.get(cacheKey)
            if (cached != null) {
                return@withContext cached
            }

            val vaultKey = getSharedVaultKey()
            val bytes = if (vaultKey != null) {
                try {
                    val rawFile = encryptedFileManager.getRawFile(fileName)
                    val encryptedBytes = rawFile.readBytes()
                    com.enclave.app.crypto.VaultCipher.decrypt(encryptedBytes, vaultKey)
                } catch (e: Exception) {
                    android.util.Log.w("VaultRepository", "VaultCipher decrypt failed, falling back to EncryptedFile", e)
                    val stream: InputStream = encryptedFileManager.getSecureInputStream(fileName)
                    stream.use { it.readBytes() }
                }
            } else {
                val stream: InputStream = encryptedFileManager.getSecureInputStream(fileName)
                stream.use { it.readBytes() }
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)

            if (reqWidth > 0 && reqHeight > 0) {
                options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            } else {
                options.inSampleSize = 1
            }
            options.inJustDecodeBounds = false

            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun shredFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val rawFile = encryptedFileManager.getRawFile(fileName)
            if (!rawFile.exists()) return@withContext false

            val length = rawFile.length()
            if (length > 0) {
                val random = SecureRandom()
                val buffer = ByteArray(4096)
                rawFile.outputStream().use { os ->
                    var bytesWritten: Long = 0
                    while (bytesWritten < length) {
                        random.nextBytes(buffer)
                        val toWrite = minOf(buffer.size.toLong(), length - bytesWritten).toInt()
                        os.write(buffer, 0, toWrite)
                        bytesWritten += toWrite
                    }
                    os.flush()
                }
            }
            rawFile.delete()
            mediaMetadataDao.deleteMediaByPath(fileName)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun exportToPublic(fileName: String, context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val vaultKey = getSharedVaultKey()
            val bytes = if (vaultKey != null) {
                try {
                    val rawFile = encryptedFileManager.getRawFile(fileName)
                    val encryptedBytes = rawFile.readBytes()
                    com.enclave.app.crypto.VaultCipher.decrypt(encryptedBytes, vaultKey)
                } catch (e: Exception) {
                    val stream: InputStream = encryptedFileManager.getSecureInputStream(fileName)
                    stream.use { it.readBytes() }
                }
            } else {
                val stream: InputStream = encryptedFileManager.getSecureInputStream(fileName)
                stream.use { it.readBytes() }
            }
            
            val isVideo = fileName.contains("video") || fileName.endsWith(".mp4") || fileName.endsWith(".mkv")
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "export_$fileName")
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }

            val uri = context.contentResolver.insert(collection, values)
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(bytes)
                }

                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                context.contentResolver.update(uri, values, null, null)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun importPublicMedia(uris: List<Uri>, context: Context, folderName: String = "General"): ImportResult = withContext(Dispatchers.IO) {
        try {
            val urisToDelete = mutableListOf<Uri>()
            uris.forEachIndexed { index, uri ->
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bytes = inputStream.readBytes()
                    inputStream.close()

                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val isVideo = mimeType.startsWith("video/")

                    // 1. Strip EXIF & Photoshop metadata for JPEGs
                    val finalBytes = if (!isVideo && (mimeType.contains("jpeg") || mimeType.contains("jpg"))) {
                        ExifStripper.stripJpegExif(bytes)
                    } else {
                        bytes
                    }

                    val prefix = if (isVideo) "video" else "image"
                    val ext = if (isVideo) "mp4" else "jpg"
                    val fileName = "${prefix}_${System.currentTimeMillis()}_$index.$ext"
                    saveSecureFile(fileName, finalBytes)

                    // 2. Generate Video Thumbnail cleanly via MediaMetadataRetriever
                    var thumbnailPath = ""
                    if (isVideo) {
                        val retriever = MediaMetadataRetriever()
                        try {
                            retriever.setDataSource(context, uri)
                            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            if (bitmap != null) {
                                val bos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bos)
                                val thumbBytes = bos.toByteArray()
                                val thumbName = "thumb_${System.currentTimeMillis()}_$index.jpg"
                                saveSecureFile(thumbName, thumbBytes)
                                thumbnailPath = thumbName
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        } finally {
                            retriever.release()
                        }
                    }

                    // 3. Insert Room Database Record
                    val mediaId = UUID.randomUUID().toString()
                    val entity = MediaMetadataEntity(
                        mediaId = mediaId,
                        messageId = "",
                        localEncryptedPath = fileName,
                        mimeType = mimeType,
                        sizeBytes = finalBytes.size.toLong(),
                        isEphemeral = false,
                        expiresAt = null,
                        folderName = folderName,
                        thumbnailPath = thumbnailPath
                    )
                    mediaMetadataDao.insertMedia(entity)
                    urisToDelete.add(uri)

                    // 4. E2EE Collaborative Cloud Vault sync
                    val vaultKey = getSharedVaultKey()
                    if (vaultKey != null) {
                        try {
                            val mainBytes = encryptedFileManager.getRawFile(fileName).readBytes()
                            bundleRepository.uploadVaultFile(fileName, mainBytes)

                            if (thumbnailPath.isNotEmpty()) {
                                val thumbBytes = encryptedFileManager.getRawFile(thumbnailPath).readBytes()
                                bundleRepository.uploadVaultFile(thumbnailPath, thumbBytes)
                            }

                            bundleRepository.insertVaultMetadata(
                                mediaId = mediaId,
                                localPath = fileName,
                                mimeType = mimeType,
                                sizeBytes = finalBytes.size.toLong(),
                                folderName = folderName,
                                thumbnailPath = thumbnailPath
                            )
                        } catch (ex: Exception) {
                            android.util.Log.e("VaultRepository", "Cooperative Cloud Vault sync failed for $fileName", ex)
                        }
                    }
                }
            }

            if (urisToDelete.isNotEmpty()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    try {
                        val pendingIntent = MediaStore.createDeleteRequest(context.contentResolver, urisToDelete)
                        return@withContext ImportResult.RequiresPermission(pendingIntent.intentSender)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Fallback for Android 10 and below or if createDeleteRequest fails
                urisToDelete.forEach { uri ->
                    try {
                        context.contentResolver.delete(uri, null, null)
                    } catch (e: Exception) {
                        if (e is RecoverableSecurityException) {
                            return@withContext ImportResult.RequiresPermission(
                                e.userAction.actionIntent.intentSender
                            )
                        } else {
                            e.printStackTrace()
                        }
                    }
                }
            }

            ImportResult.Success
        } catch (e: Exception) {
            e.printStackTrace()
            ImportResult.Error(e)
        }
    }

    suspend fun syncSharedVault() = withContext(Dispatchers.IO) {
        getSharedVaultKey() ?: return@withContext
        try {
            val remoteMetaList = bundleRepository.fetchRemoteVaultMetadata()
            for (meta in remoteMetaList) {
                val localEntity = mediaMetadataDao.getMediaByIdSync(meta.media_id)
                if (localEntity == null) {
                    // Download and register locally
                    try {
                        val mainPath = "${meta.uploaded_by}/${meta.local_encrypted_path}"
                        val mainBytes = bundleRepository.downloadVaultFile(mainPath)

                        // Save main file raw encrypted bytes directly to disk
                        val rawFile = encryptedFileManager.getRawFile(meta.local_encrypted_path)
                        if (rawFile.exists()) rawFile.delete()
                        rawFile.writeBytes(mainBytes)

                        // Download thumbnail if present
                        if (meta.thumbnail_path.isNotEmpty()) {
                            val thumbPath = "${meta.uploaded_by}/${meta.thumbnail_path}"
                            val thumbBytes = bundleRepository.downloadVaultFile(thumbPath)
                            val rawThumbFile = encryptedFileManager.getRawFile(meta.thumbnail_path)
                            if (rawThumbFile.exists()) rawThumbFile.delete()
                            rawThumbFile.writeBytes(thumbBytes)
                        }

                        // Insert local Room Database Record
                        val entity = MediaMetadataEntity(
                            mediaId = meta.media_id,
                            messageId = "",
                            localEncryptedPath = meta.local_encrypted_path,
                            mimeType = meta.mime_type,
                            sizeBytes = meta.size_bytes,
                            isEphemeral = false,
                            expiresAt = null,
                            folderName = meta.folder_name,
                            thumbnailPath = meta.thumbnail_path
                        )
                        mediaMetadataDao.insertMedia(entity)
                        android.util.Log.d("VaultRepository", "Synced remote vault file: ${meta.local_encrypted_path}")
                    } catch (e: Exception) {
                        android.util.Log.e("VaultRepository", "Failed to download remote file: ${meta.local_encrypted_path}", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("VaultRepository", "syncSharedVault failed", e)
        }
    }
}
