package br.com.smarttarget.karaoke.studio.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import br.com.smarttarget.karaoke.studio.data.model.DownloadItem
import br.com.smarttarget.karaoke.studio.data.model.DownloadStatus
import org.json.JSONObject

@Entity(tableName = "karaoke_library")
data class KaraokeAssetEntity(
  @PrimaryKey val guid: String,
  val songTitle: String,
  val originalTitle: String,
  val channelOrArtist: String,
  val genre: String,
  val singers: String,
  val composers: String,
  val album: String?,
  val workTitle: String?,
  val recordingDate: String?,
  val compositionDate: String?,
  val classicalEra: String?,
  val classicalWorkType: String?,
  val assetDirPath: String,
  val audioPath: String,
  val previewVideoPath: String,
  val fftDataPath: String,
  val subtitlesDirPath: String,
  val metadataJsonPath: String,
  val thumbnailUrl: String,
  val durationSeconds: Long,
  val audioFormat: String,
  val videoFormat: String,
  val videoResolution: String,
  val audioBitrate: String,
  val metadataJson: String?,
  val isFavorite: Boolean = false,
  val createdAt: Long = System.currentTimeMillis()
) {
  fun toDownloadItem(): DownloadItem {
    return DownloadItem(
      id = guid,
      guid = guid,
      url = "",
      title = originalTitle,
      songTitle = songTitle,
      channelTitle = channelOrArtist,
      thumbnailUrl = thumbnailUrl,
      durationSeconds = durationSeconds,
      formatId = videoResolution,
      formatLabel = "$videoResolution ($videoFormat)",
      isMp3Conversion = audioFormat == "mp3",
      audioQuality = audioBitrate,
      subtitleLang = "pt-BR",
      folderName = guid,
      assetDirPath = assetDirPath,
      audioPath = audioPath,
      previewVideoPath = previewVideoPath,
      fftDataPath = fftDataPath,
      subtitlesDirPath = subtitlesDirPath,
      filePath = previewVideoPath,
      infoJsonPath = metadataJsonPath,
      subtitlePath = "$subtitlesDirPath/pt-BR.srt",
      metadataJson = metadataJson,
      status = DownloadStatus.COMPLETED,
      progress = 1.0f,
      downloadedBytes = 35_000_000L,
      totalBytes = 35_000_000L,
      speedBytesPerSec = 0L,
      createdAt = createdAt,
      completedAt = createdAt
    )
  }

  companion object {
    fun fromDownloadEntity(d: DownloadEntity): KaraokeAssetEntity {
      var parsedGenre = "Sertanejo"
      var parsedSingers = ""
      var parsedComposers = ""
      var parsedAlbum: String? = null
      var parsedWork: String? = null
      var parsedRecDate: String? = null
      var parsedCompDate: String? = null
      var parsedEra: String? = null
      var parsedType: String? = null

      if (!d.metadataJson.isNullOrEmpty()) {
        try {
          val json = JSONObject(d.metadataJson)
          parsedGenre = json.optString("genre", "Sertanejo")
          parsedAlbum = json.optString("album").takeIf { it.isNotEmpty() }
          parsedWork = json.optString("work_title").takeIf { it.isNotEmpty() }
          parsedRecDate = json.optString("recording_date").takeIf { it.isNotEmpty() }
          parsedCompDate = json.optString("composition_date").takeIf { it.isNotEmpty() }
          parsedEra = json.optString("classical_era").takeIf { it.isNotEmpty() }
          parsedType = json.optString("work_type").takeIf { it.isNotEmpty() }

          val pArray = json.optJSONArray("persons")
          if (pArray != null) {
            val sList = mutableListOf<String>()
            val cList = mutableListOf<String>()
            for (i in 0 until pArray.length()) {
              val p = pArray.getJSONObject(i)
              val name = p.optString("name")
              val role = p.optString("role")
              if (role == "CANTOR") sList.add(name) else cList.add(name)
            }
            parsedSingers = sList.joinToString(", ")
            parsedComposers = cList.joinToString(", ")
          }
        } catch (_: Exception) {}
      }

      val aPath = d.audioPath ?: ""
      val aFormat = if (aPath.endsWith(".mp3", ignoreCase = true)) "mp3" else "m4a"

      val vPath = d.previewVideoPath ?: ""
      val vFormat = if (vPath.endsWith(".mp4", ignoreCase = true)) "mp4" else "m4v"

      return KaraokeAssetEntity(
        guid = d.guid.replace("-", "").lowercase(),
        songTitle = d.songTitle.ifEmpty { d.title },
        originalTitle = d.title,
        channelOrArtist = d.channelTitle,
        genre = parsedGenre,
        singers = parsedSingers,
        composers = parsedComposers,
        album = parsedAlbum,
        workTitle = parsedWork,
        recordingDate = parsedRecDate,
        compositionDate = parsedCompDate,
        classicalEra = parsedEra,
        classicalWorkType = parsedType,
        assetDirPath = d.assetDirPath ?: "",
        audioPath = d.audioPath ?: "",
        previewVideoPath = d.previewVideoPath ?: "",
        fftDataPath = d.fftDataPath ?: "",
        subtitlesDirPath = d.subtitlesDirPath ?: "",
        metadataJsonPath = d.infoJsonPath ?: "",
        thumbnailUrl = d.thumbnailUrl,
        durationSeconds = d.durationSeconds,
        audioFormat = aFormat,
        videoFormat = vFormat,
        videoResolution = d.formatLabel,
        audioBitrate = d.audioQuality,
        metadataJson = d.metadataJson,
        isFavorite = false,
        createdAt = d.completedAt ?: d.createdAt
      )
    }
  }
}
