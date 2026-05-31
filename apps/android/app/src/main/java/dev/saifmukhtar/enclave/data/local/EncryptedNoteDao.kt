package dev.saifmukhtar.enclave.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EncryptedNoteDao {
    @Query("SELECT * FROM encrypted_notes ORDER BY updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<EncryptedNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: EncryptedNoteEntity)

    @Query("DELETE FROM encrypted_notes WHERE id = :id")
    suspend fun deleteNote(id: String)
}
