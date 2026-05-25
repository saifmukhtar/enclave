package com.enclave.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "outbox_messages")
data class OutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val targetId: String,
    val type: String, // e.g. "SIGNAL_PAYLOAD"
    val payload: String, // Base64 ciphertext or JSON
    val contentType: String?,
    val messageId: String?,
    val timestamp: Long = System.currentTimeMillis()
)
