package com.enclave.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface TimeCapsuleDao {
    @Insert
    suspend fun insert(timeCapsule: TimeCapsuleEntity)

    @Query("SELECT * FROM time_capsules ORDER BY sendAt ASC")
    fun getAllCapsulesFlow(): Flow<List<TimeCapsuleEntity>>

    @Query("SELECT * FROM time_capsules WHERE id = :id")
    suspend fun getCapsuleById(id: String): TimeCapsuleEntity?

    @Query("DELETE FROM time_capsules WHERE id = :id")
    suspend fun deleteById(id: String)
}
