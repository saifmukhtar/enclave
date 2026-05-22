package com.enclave.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a record of each call session (audio or video) for the Call Log screen.
 * - callType: "AUDIO" | "VIDEO"
 * - direction: "OUTGOING" | "INCOMING"
 * - status: "CONNECTED" | "MISSED" | "REJECTED"
 */
@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey val id: String,
    val callType: String,         // "AUDIO" | "VIDEO"
    val direction: String,        // "OUTGOING" | "INCOMING"
    val status: String,           // "CONNECTED" | "MISSED" | "REJECTED"
    val startedAt: Long,          // epoch millis
    val durationSeconds: Int      // 0 for missed/rejected calls
)
