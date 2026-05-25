package com.enclave.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(outboxEntity: OutboxEntity)

    @Query("SELECT * FROM outbox_messages ORDER BY timestamp ASC")
    suspend fun getAllPendingMessages(): List<OutboxEntity>

    @Delete
    suspend fun delete(outboxEntity: OutboxEntity)

    @Query("DELETE FROM outbox_messages WHERE id = :id")
    suspend fun deleteById(id: Int)
}
