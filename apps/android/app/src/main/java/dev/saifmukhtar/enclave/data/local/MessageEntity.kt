package dev.saifmukhtar.enclave.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["senderId"])
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val receiverId: String,
    val encryptedPayload: String, // Double Ratchet ciphertext
    val timestamp: Long,
    val isRead: Boolean,
    val messageType: String, // TEXT, IMAGE, VIDEO, VOICE
    @ColumnInfo(defaultValue = "SENT") val deliveryStatus: String = "SENT",
    @ColumnInfo(defaultValue = "0") val disappearingDuration: Long = 0L,
    @ColumnInfo(defaultValue = "0") val expiresAt: Long = 0L,
    @ColumnInfo(defaultValue = "") val reaction: String = "",
    @ColumnInfo(defaultValue = "0") val readAt: Long = 0L  // Timestamp when partner read this, 0 = unread
)
