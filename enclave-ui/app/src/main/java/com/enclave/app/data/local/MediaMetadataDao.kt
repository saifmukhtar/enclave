package com.enclave.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaMetadataDao {
    @Query("SELECT * FROM media_metadata ORDER BY mediaId DESC")
    fun getAllMedia(): Flow<List<MediaMetadataEntity>>

    @Query("SELECT * FROM media_metadata WHERE folderName = :folderName ORDER BY mediaId DESC")
    fun getMediaInFolder(folderName: String): Flow<List<MediaMetadataEntity>>

    @Query("SELECT DISTINCT folderName FROM media_metadata")
    fun getAllFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedia(media: MediaMetadataEntity)

    @Query("DELETE FROM media_metadata WHERE localEncryptedPath = :path")
    suspend fun deleteMediaByPath(path: String)

    @Query("UPDATE media_metadata SET folderName = :folderName WHERE mediaId = :mediaId")
    suspend fun updateMediaFolder(mediaId: String, folderName: String)

    /** Toggle favorite status for a vault item. */
    @Query("UPDATE media_metadata SET isFavorite = :isFavorite WHERE mediaId = :mediaId")
    suspend fun setFavorite(mediaId: String, isFavorite: Boolean)

    /** Retrieve only favorited items. */
    @Query("SELECT * FROM media_metadata WHERE isFavorite = 1 ORDER BY mediaId DESC")
    fun getFavorites(): Flow<List<MediaMetadataEntity>>

    /** Filter by MIME type prefix (e.g. 'image/%', 'video/%', 'audio/%'). */
    @Query("SELECT * FROM media_metadata WHERE mimeType LIKE :mimePrefix ORDER BY mediaId DESC")
    fun getMediaByMimeType(mimePrefix: String): Flow<List<MediaMetadataEntity>>

    /** Search vault items by a portion of their path/name. */
    @Query("SELECT * FROM media_metadata WHERE localEncryptedPath LIKE '%' || :query || '%' ORDER BY mediaId DESC")
    fun searchMedia(query: String): Flow<List<MediaMetadataEntity>>

    @Query("DELETE FROM media_metadata WHERE mediaId = :mediaId")
    suspend fun deleteById(mediaId: String)
}

