package dev.saifmukhtar.enclave.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: CallLogEntity)

    /** Returns up to 200 most recent call log entries, newest first. */
    @Query("SELECT * FROM call_logs ORDER BY startedAt DESC LIMIT 200")
    fun getRecentLogs(): Flow<List<CallLogEntity>>

    @Query("DELETE FROM call_logs")
    suspend fun clearAll()
}
