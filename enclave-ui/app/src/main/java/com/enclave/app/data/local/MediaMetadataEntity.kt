package com.enclave.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "media_metadata")
data class MediaMetadataEntity(
    @PrimaryKey val mediaId: String,
    val messageId: String,
    val localEncryptedPath: String, // Path in EncryptedFile system
    val mimeType: String,
    val sizeBytes: Long,
    val isEphemeral: Boolean,
    val expiresAt: Long?, // Null if saved to Vault, timestamp if Story/Ephemeral
    @ColumnInfo(defaultValue = "General") val folderName: String = "General",
    @ColumnInfo(defaultValue = "") val thumbnailPath: String = "",
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false
)

