package br.com.smarttarget.karaoke.studio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.smarttarget.karaoke.studio.data.model.DownloadItem
import br.com.smarttarget.karaoke.studio.data.model.DownloadStatus

@Entity(tableName = "downloads")
data class DownloadEntity(
  @PrimaryKey val id: String,
  val guid: String,
  val url: String,
  val title: String,
  val songTitle: String,
  val channelTitle: String,
  val thumbnailUrl: String,
  val durationSeconds: Long,
  val formatId: String,
  val formatLabel: String,
  val isMp3Conversion: Boolean,
  val audioQuality: String,
  val subtitleLang: String?,
  val folderName: String,
  val assetDirPath: String?,
  val audioPath: String?,
  val previewVideoPath: String?,
  val fftDataPath: String?,
  val subtitlesDirPath: String?,
  val filePath: String?,
  val infoJsonPath: String?,
  val subtitlePath: String?,
  val metadataJson: String?,
  val status: String,
  val progress: Float,
  val downloadedBytes: Long,
  val totalBytes: Long,
  val speedBytesPerSec: Long,
  val errorMessage: String?,
  val playlistId: String?,
  val playlistTitle: String?,
  val createdAt: Long,
  val completedAt: Long?
) {
  fun toModel(): DownloadItem {
    return DownloadItem(
      id = id,
      guid = guid,
      url = url,
      title = title,
      songTitle = songTitle,
      channelTitle = channelTitle,
      thumbnailUrl = thumbnailUrl,
      durationSeconds = durationSeconds,
      formatId = formatId,
      formatLabel = formatLabel,
      isMp3Conversion = isMp3Conversion,
      audioQuality = audioQuality,
      subtitleLang = subtitleLang,
      folderName = folderName,
      assetDirPath = assetDirPath,
      audioPath = audioPath,
      previewVideoPath = previewVideoPath,
      fftDataPath = fftDataPath,
      subtitlesDirPath = subtitlesDirPath,
      filePath = filePath,
      infoJsonPath = infoJsonPath,
      subtitlePath = subtitlePath,
      metadataJson = metadataJson,
      status = try { DownloadStatus.valueOf(status) } catch (e: Exception) { DownloadStatus.PENDING },
      progress = progress,
      downloadedBytes = downloadedBytes,
      totalBytes = totalBytes,
      speedBytesPerSec = speedBytesPerSec,
      errorMessage = errorMessage,
      playlistId = playlistId,
      playlistTitle = playlistTitle,
      createdAt = createdAt,
      completedAt = completedAt
    )
  }

  companion object {
    fun fromModel(model: DownloadItem): DownloadEntity {
      return DownloadEntity(
        id = model.id,
        guid = model.guid,
        url = model.url,
        title = model.title,
        songTitle = model.songTitle,
        channelTitle = model.channelTitle,
        thumbnailUrl = model.thumbnailUrl,
        durationSeconds = model.durationSeconds,
        formatId = model.formatId,
        formatLabel = model.formatLabel,
        isMp3Conversion = model.isMp3Conversion,
        audioQuality = model.audioQuality,
        subtitleLang = model.subtitleLang,
        folderName = model.folderName,
        assetDirPath = model.assetDirPath,
        audioPath = model.audioPath,
        previewVideoPath = model.previewVideoPath,
        fftDataPath = model.fftDataPath,
        subtitlesDirPath = model.subtitlesDirPath,
        filePath = model.filePath,
        infoJsonPath = model.infoJsonPath,
        subtitlePath = model.subtitlePath,
        metadataJson = model.metadataJson,
        status = model.status.name,
        progress = model.progress,
        downloadedBytes = model.downloadedBytes,
        totalBytes = model.totalBytes,
        speedBytesPerSec = model.speedBytesPerSec,
        errorMessage = model.errorMessage,
        playlistId = model.playlistId,
        playlistTitle = model.playlistTitle,
        createdAt = model.createdAt,
        completedAt = model.completedAt
      )
    }
  }
}
