package com.enclave.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Upsert
    suspend fun upsertProfile(entity: UserProfileEntity)

    @Query("SELECT * FROM user_profiles WHERE userId = :userId LIMIT 1")
    suspend fun getProfileSync(userId: String): UserProfileEntity?

    @androidx.room.Transaction
    suspend fun upsertProfilePreservingLocal(entity: UserProfileEntity) {
        val existing = getProfileSync(entity.userId) ?: return upsertProfile(entity)
        // Merge: never overwrite locally-set fields with blank/zero values from server
        val merged = entity.copy(
            username        = entity.username.ifBlank        { existing.username },
            displayName     = entity.displayName.ifBlank     { existing.displayName },
            bio             = entity.bio.ifBlank             { existing.bio },
            avatarUrl       = entity.avatarUrl.ifBlank       { existing.avatarUrl },
            avatarLocalPath = entity.avatarLocalPath.ifBlank { existing.avatarLocalPath },
            lastSeen        = if (entity.lastSeen == 0L) existing.lastSeen else entity.lastSeen
        )
        upsertProfile(merged)
    }

    @Query("SELECT * FROM user_profiles WHERE userId = :userId")
    fun getProfile(userId: String): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles")
    fun getAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isMe = 1 LIMIT 1")
    fun getMyProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isMe = 0 LIMIT 1")
    fun getPartnerProfile(): Flow<UserProfileEntity?>

    @Query("UPDATE user_profiles SET lastSeen = :timestamp, isOnline = 0 WHERE userId = :userId")
    suspend fun updateLastSeen(userId: String, timestamp: Long)

    @Query("UPDATE user_profiles SET isOnline = :isOnline WHERE userId = :userId")
    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean)

    @Query("UPDATE user_profiles SET avatarUrl = :url WHERE userId = :userId")
    suspend fun updateAvatarUrl(userId: String, url: String)

    @Query("DELETE FROM user_profiles WHERE userId = :userId")
    suspend fun deleteProfile(userId: String)
}
