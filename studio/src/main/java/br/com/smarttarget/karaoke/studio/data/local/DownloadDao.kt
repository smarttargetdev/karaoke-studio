package br.com.smarttarget.karaoke.studio.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
  @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
  fun getAllDownloads(): Flow<List<DownloadEntity>>

  @Query("SELECT * FROM downloads WHERE id = :id OR guid = :id")
  suspend fun getDownloadById(id: String): DownloadEntity?

  @Query("SELECT * FROM downloads WHERE status IN ('PENDING', 'EXTRACTING', 'DOWNLOADING', 'CONVERTING_MP3', 'SAVING_METADATA') ORDER BY createdAt ASC")
  fun getActiveDownloads(): Flow<List<DownloadEntity>>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(download: DownloadEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertAll(downloads: List<DownloadEntity>)

  @Update
  suspend fun update(download: DownloadEntity)

  @Query("UPDATE downloads SET status = :status, progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speedBytesPerSec = :speed WHERE id = :id")
  suspend fun updateProgress(id: String, status: String, progress: Float, downloadedBytes: Long, totalBytes: Long, speed: Long)

  @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, assetDirPath = :assetDirPath, audioPath = :audioPath, previewVideoPath = :previewVideoPath, fftDataPath = :fftDataPath, subtitlesDirPath = :subtitlesDirPath, filePath = :filePath, infoJsonPath = :infoJsonPath, subtitlePath = :subtitlePath, metadataJson = :metadataJson, songTitle = :songTitle, completedAt = :completedAt WHERE id = :id")
  suspend fun markAssetCompleted(
    id: String,
    assetDirPath: String,
    audioPath: String,
    previewVideoPath: String,
    fftDataPath: String,
    subtitlesDirPath: String,
    filePath: String,
    infoJsonPath: String?,
    subtitlePath: String?,
    metadataJson: String?,
    songTitle: String,
    completedAt: Long
  )

  @Query("UPDATE downloads SET status = 'COMPLETED', progress = 1.0, filePath = :filePath, infoJsonPath = :infoJsonPath, subtitlePath = :subtitlePath, completedAt = :completedAt WHERE id = :id")
  suspend fun markCompleted(id: String, filePath: String, infoJsonPath: String?, subtitlePath: String?, completedAt: Long)

  @Query("UPDATE downloads SET status = 'ERROR', errorMessage = :error WHERE id = :id")
  suspend fun markError(id: String, error: String)

  @Delete
  suspend fun delete(download: DownloadEntity)

  @Query("DELETE FROM downloads WHERE id = :id")
  suspend fun deleteById(id: String)

  @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
  suspend fun clearCompleted()
}
