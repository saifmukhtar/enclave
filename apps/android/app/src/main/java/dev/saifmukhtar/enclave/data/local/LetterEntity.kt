package dev.saifmukhtar.enclave.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_letters")
data class LetterEntity(
    @PrimaryKey val id: String,
    val senderId: String,
    val ciphertext: String,
    val createdAt: Long,
    val isRead: Boolean
)
