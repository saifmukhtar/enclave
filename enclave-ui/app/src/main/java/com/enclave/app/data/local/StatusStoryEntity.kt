package com.enclave.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A 24-hour ephemeral status story (text, image, or emoji).
 * Content is stored encrypted; expiresAt = createdAt + 24h.
 */
@Entity(tableName = "status_stories")
data class StatusStoryEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    @ColumnInfo(defaultValue = "TEXT") val contentType: String = "TEXT", // TEXT | IMAGE | EMOJI
    val encryptedPayload: String = "",   // AES encrypted text content or vault image path
    @ColumnInfo(defaultValue = "#FCE2E6") val backgroundColor: String = "#FCE2E6",
    val expiresAt: Long,                // System.currentTimeMillis() + 86_400_000
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val viewedAt: Long = 0L, // 0 = not yet viewed
    @ColumnInfo(defaultValue = "0") val isFromMe: Boolean = false
)
