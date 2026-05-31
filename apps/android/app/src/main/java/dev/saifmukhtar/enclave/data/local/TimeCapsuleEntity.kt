package dev.saifmukhtar.enclave.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_capsules")
data class TimeCapsuleEntity(
    @PrimaryKey
    val id: String,
    val targetId: String,
    val payloadText: String, // Stored locally encrypted with local vault key
    val sendAt: Long,
    val createdAt: Long = System.currentTimeMillis()
)
