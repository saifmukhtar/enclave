package com.enclave.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Locally cached profile for both "me" and partner.
 * Avatar is stored as an encrypted local path in the Vault;
 * avatarUrl is the Supabase Storage URL for syncing.
 */
@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val userId: String,
    @ColumnInfo(defaultValue = "") val username: String = "",
    @ColumnInfo(defaultValue = "") val displayName: String = "",
    @ColumnInfo(defaultValue = "") val bio: String = "",
    @ColumnInfo(defaultValue = "") val avatarUrl: String = "",          // Supabase Storage signed URL
    @ColumnInfo(defaultValue = "") val avatarLocalPath: String = "",    // Local encrypted vault path
    @ColumnInfo(defaultValue = "0") val lastSeen: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isOnline: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isMe: Boolean = false           // true = my own profile
)
