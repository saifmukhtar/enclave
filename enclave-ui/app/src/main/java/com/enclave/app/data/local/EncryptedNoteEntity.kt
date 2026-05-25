package com.enclave.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "encrypted_notes")
data class EncryptedNoteEntity(
    @PrimaryKey val id: String,
    val titlePayload: ByteArray,
    val contentPayload: ByteArray,
    val createdAt: Long,
    val updatedAt: Long,
    val authorId: String,
    val isSynced: Boolean = false
)
