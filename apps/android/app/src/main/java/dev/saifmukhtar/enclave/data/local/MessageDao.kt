package dev.saifmukhtar.enclave.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType IN ('MEDIA', 'MEDIA_IMAGE', 'MEDIA_VIDEO') ORDER BY timestamp DESC")
    fun getMediaMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageType IN ('MEDIA', 'MEDIA_IMAGE', 'MEDIA_VIDEO') ORDER BY timestamp DESC")
    suspend fun getLastMediaMessagesOnly(): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Query("UPDATE messages SET isRead = 1 WHERE id = :messageId")
    suspend fun markAsRead(messageId: String)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: String)

    @Query("UPDATE messages SET isRead = :isRead, deliveryStatus = 'READ' WHERE id = :messageId")
    suspend fun updateMessageReadStatus(messageId: String, isRead: Boolean)

    @Query("UPDATE messages SET expiresAt = :expiresAt WHERE id = :messageId")
    suspend fun updateExpirationTime(messageId: String, expiresAt: Long)

    @Query("UPDATE messages SET reaction = :reaction WHERE id = :messageId")
    suspend fun updateMessageReaction(messageId: String, reaction: String)

    @Query("DELETE FROM messages WHERE expiresAt > 0 AND expiresAt <= :currentTime")
    suspend fun deleteExpiredMessages(currentTime: Long)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages")
    suspend fun clearEntireChat()

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    /** Called when a READ_RECEIPT arrives — records when the partner read our message */
    @Query("UPDATE messages SET readAt = :readAt, deliveryStatus = 'READ' WHERE id = :messageId")
    suspend fun markReadAt(messageId: String, readAt: Long)

    /** Get all unread received messages (for sending batch read receipts) */
    @Query("SELECT * FROM messages WHERE senderId != :myId AND isRead = 0")
    suspend fun getUnreadReceivedMessages(myId: String): List<MessageEntity>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLastMessages(limit: Int): List<MessageEntity>
}
