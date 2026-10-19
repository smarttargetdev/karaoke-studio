package br.com.smarttarget.karaoke.studio.data.model

enum class DownloadStatus {
  PENDING,
  EXTRACTING,
  DOWNLOADING,
  CONVERTING_MP3,
  SAVING_METADATA,
  COMPLETED,
  PAUSED,
  ERROR,
  CANCELLED
}

data class DownloadItem(
  val id: String,
  val guid: String = id.replace("-", "").lowercase(),
  val url: String,
  val title: String,
  val songTitle: String = title,
  val channelTitle: String,
  val thumbnailUrl: String,
  val durationSeconds: Long,
  val formatId: String,
  val formatLabel: String,
  val isMp3Conversion: Boolean,
  val audioQuality: String,
  val subtitleLang: String? = "all",
  val folderName: String,
  val assetDirPath: String? = null,
  val audioPath: String? = null,
  val previewVideoPath: String? = null,
  val fftDataPath: String? = null,
  val subtitlesDirPath: String? = null,
  val filePath: String? = null,
  val infoJsonPath: String? = null,
  val subtitlePath: String? = null,
  val metadataJson: String? = null,
  val status: DownloadStatus = DownloadStatus.PENDING,
  val progress: Float = 0f,
  val downloadedBytes: Long = 0L,
  val totalBytes: Long = 0L,
  val speedBytesPerSec: Long = 0L,
  val errorMessage: String? = null,
  val playlistId: String? = null,
  val playlistTitle: String? = null,
  val createdAt: Long = System.currentTimeMillis(),
  val completedAt: Long? = null
)
