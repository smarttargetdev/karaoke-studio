package br.com.smarttarget.karaoke.studio.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KaraokeAssetDao {

  @Query("SELECT * FROM karaoke_library ORDER BY createdAt DESC")
  fun getAllAssets(): Flow<List<KaraokeAssetEntity>>

  @Query("""
    SELECT * FROM karaoke_library 
    WHERE songTitle LIKE '%' || :query || '%' 
       OR originalTitle LIKE '%' || :query || '%'
       OR singers LIKE '%' || :query || '%' 
       OR composers LIKE '%' || :query || '%' 
       OR genre LIKE '%' || :query || '%' 
       OR album LIKE '%' || :query || '%' 
       OR workTitle LIKE '%' || :query || '%' 
       OR guid LIKE '%' || :query || '%'
    ORDER BY createdAt DESC
  """)
  fun searchAssets(query: String): Flow<List<KaraokeAssetEntity>>

  @Query("SELECT * FROM karaoke_library WHERE isFavorite = 1 ORDER BY createdAt DESC")
  fun getFavorites(): Flow<List<KaraokeAssetEntity>>

  @Query("SELECT * FROM karaoke_library WHERE genre = :genre ORDER BY createdAt DESC")
  fun getAssetsByGenre(genre: String): Flow<List<KaraokeAssetEntity>>

  @Query("SELECT * FROM karaoke_library WHERE guid = :guid")
  suspend fun getAssetByGuid(guid: String): KaraokeAssetEntity?

  @Query("SELECT COUNT(*) FROM karaoke_library")
  fun getAssetsCount(): Flow<Int>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(asset: KaraokeAssetEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(assets: List<KaraokeAssetEntity>)

  @Update
  suspend fun update(asset: KaraokeAssetEntity)

  @Query("UPDATE karaoke_library SET isFavorite = :isFav WHERE guid = :guid")
  suspend fun setFavorite(guid: String, isFav: Boolean)

  @Delete
  suspend fun delete(asset: KaraokeAssetEntity)

  @Query("DELETE FROM karaoke_library WHERE guid = :guid")
  suspend fun deleteByGuid(guid: String)
}
