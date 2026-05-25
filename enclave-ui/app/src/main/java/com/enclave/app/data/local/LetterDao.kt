package com.enclave.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LetterDao {
    @Query("SELECT * FROM daily_letters ORDER BY createdAt DESC")
    fun getAllLettersFlow(): Flow<List<LetterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLetter(letter: LetterEntity)

    @Query("UPDATE daily_letters SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: String)

    @Delete
    suspend fun deleteLetter(letter: LetterEntity)

    @Query("DELETE FROM daily_letters WHERE id = :id")
    suspend fun deleteLetterById(id: String)
}
