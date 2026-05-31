package dev.saifmukhtar.enclave.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StatusStoryDao {
    @Upsert
    suspend fun upsertStory(entity: StatusStoryEntity)

    /** All non-expired stories, newest first. */
    @Query("SELECT * FROM status_stories WHERE expiresAt > :now ORDER BY createdAt DESC")
    fun getActiveStories(now: Long): Flow<List<StatusStoryEntity>>

    /** Stories from a specific author */
    @Query("SELECT * FROM status_stories WHERE authorId = :authorId AND expiresAt > :now ORDER BY createdAt DESC")
    fun getStoriesByAuthor(authorId: String, now: Long): Flow<List<StatusStoryEntity>>

    /** Partner's unviewed stories */
    @Query("SELECT COUNT(*) FROM status_stories WHERE authorId = :partnerId AND expiresAt > :now AND viewedAt = 0")
    fun getUnviewedPartnerStoryCount(partnerId: String, now: Long): Flow<Int>

    @Query("UPDATE status_stories SET viewedAt = :viewedAt WHERE id = :id")
    suspend fun markViewed(id: String, viewedAt: Long)

    @Query("DELETE FROM status_stories WHERE expiresAt < :now")
    suspend fun deleteExpiredStories(now: Long)

    @Query("DELETE FROM status_stories WHERE id = :id")
    suspend fun deleteStory(id: String)
}
