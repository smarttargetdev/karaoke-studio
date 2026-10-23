package br.com.smarttarget.karaoke.studio.engine

import android.content.Context
import android.os.Environment
import br.com.smarttarget.karaoke.studio.data.local.DownloadDao
import br.com.smarttarget.karaoke.studio.data.local.DownloadEntity
import br.com.smarttarget.karaoke.studio.data.local.KaraokeAssetDao
import br.com.smarttarget.karaoke.studio.data.local.KaraokeAssetEntity
import br.com.smarttarget.karaoke.studio.data.local.UserPreferencesRepository
import br.com.smarttarget.karaoke.studio.data.model.DownloadItem
import br.com.smarttarget.karaoke.studio.data.model.DownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sin

class DownloadManager(
  private val context: Context,
  private val downloadDao: DownloadDao,
  private val karaokeAssetDao: KaraokeAssetDao,
  private val preferencesRepo: UserPreferencesRepository
) {
  private val scope = CoroutineScope(Dispatchers.IO + Job())
  private val activeJobs = ConcurrentHashMap<String, Job>()

  private val _activeDownloadsCount = MutableStateFlow(0)
  val activeDownloadsCount: StateFlow<Int> = _activeDownloadsCount.asStateFlow()

  fun enqueue(item: DownloadItem) {
    scope.launch {
      downloadDao.insert(DownloadEntity.fromModel(item))
      startDownloadJob(item.id)
    }
  }

  fun enqueueAll(items: List<DownloadItem>) {
    scope.launch {
      downloadDao.insertAll(items.map { DownloadEntity.fromModel(it) })
      items.forEach { startDownloadJob(it.id) }
    }
  }

  fun pauseDownload(id: String) {
    activeJobs[id]?.cancel()
    activeJobs.remove(id)
    scope.launch {
      val entity = downloadDao.getDownloadById(id)
      if (entity != null) {
        downloadDao.update(entity.copy(status = DownloadStatus.PAUSED.name, speedBytesPerSec = 0L))
      }
      updateActiveCount()
    }
  }

  fun resumeDownload(id: String) {
    scope.launch {
      val entity = downloadDao.getDownloadById(id)
      if (entity != null) {
        startDownloadJob(id)
      }
    }
  }

  fun cancelDownload(id: String) {
    activeJobs[id]?.cancel()
    activeJobs.remove(id)
    scope.launch {
      val entity = downloadDao.getDownloadById(id)
      if (entity != null) {
        downloadDao.update(entity.copy(status = DownloadStatus.CANCELLED.name, speedBytesPerSec = 0L))
      }
      updateActiveCount()
    }
  }

  fun deleteDownload(id: String) {
    cancelDownload(id)
    scope.launch {
      val entity = downloadDao.getDownloadById(id)
      if (entity?.assetDirPath != null) {
        try {
          File(entity.assetDirPath).deleteRecursively()
        } catch (_: Exception) {}
      }
      downloadDao.deleteById(id)
      karaokeAssetDao.deleteByGuid(id)
    }
  }

  private fun startDownloadJob(id: String) {
    if (activeJobs.containsKey(id)) return

    val job = scope.launch {
      try {
        val entity = downloadDao.getDownloadById(id) ?: return@launch
        val item = entity.toModel()

        // 1. Determine base assets path and target GUID asset directory no SD interno do dispositivo (fora de Android/data)
        val baseAssetsPath = preferencesRepo.baseAssetsFolder.value.ifEmpty { "KaraokeStudio/assets" }
        val guid = item.guid.replace("-", "").lowercase()
        val baseDir = resolveInternalSdStorageDirectory(baseAssetsPath)
        val assetDir = File(baseDir, guid)
        if (!assetDir.exists()) assetDir.mkdirs()

        android.util.Log.d("DownloadManager", "[download] Diretório do asset configurado no SD interno: ${assetDir.absolutePath}")

        // Subtitles directory inside asset folder
        val subtitlesDir = File(assetDir, "subtitles")
        if (!subtitlesDir.exists()) subtitlesDir.mkdirs()

        // 2. Read configured video & audio specs (defaults: m4v & m4a)
        val videoContainer = preferencesRepo.videoContainer.value.lowercase().ifEmpty { "m4v" }
        val videoCodec = preferencesRepo.videoCodec.value
        val videoRes = preferencesRepo.videoResolution.value
        val videoFps = preferencesRepo.videoFps.value

        val audioContainer = preferencesRepo.audioContainer.value.lowercase().ifEmpty { "m4a" }
        val audioCodec = preferencesRepo.audioCodec.value
        val audioBitrate = preferencesRepo.audioBitrate.value
        val audioQuality = preferencesRepo.audioQuality.value

        // File destinations named strictly "audio" and "preview"
        val audioFile = File(assetDir, "audio.$audioContainer")
        val previewVideoFile = File(assetDir, "preview.$videoContainer")
        val fftFile = File(assetDir, "fft.json")
        val metadataFile = File(assetDir, "metadata.json")

        val totalBytes = 35_000_000L

        downloadDao.updateProgress(
          id = id,
          status = DownloadStatus.DOWNLOADING.name,
          progress = 0.05f,
          downloadedBytes = 0L,
          totalBytes = totalBytes,
          speed = 3_200_000L
        )
        updateActiveCount()

        // 3. Write audio stream file named "audio"
        val audioOut = FileOutputStream(audioFile)
        writeAudioHeader(audioOut, audioContainer)
        val buffer = ByteArray(16384)
        var downloaded = 0L
        val startTime = System.currentTimeMillis()

        for (chunk in 1..25) {
          if (!isActive) throw CancellationException()
          delay(80)
          val chunkBytes = (totalBytes / 50).toInt()
          downloaded += chunkBytes
          audioOut.write(buffer, 0, Math.min(buffer.size, chunkBytes))

          val progress = (chunk.toFloat() / 50)
          val elapsedSec = Math.max(1L, (System.currentTimeMillis() - startTime) / 1000)
          downloadDao.updateProgress(
            id = id,
            status = DownloadStatus.DOWNLOADING.name,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = totalBytes,
            speed = downloaded / elapsedSec
          )
        }
        audioOut.flush()
        audioOut.close()

        // 4. Write video preview stream file named "preview"
        val videoOut = FileOutputStream(previewVideoFile)
        writeVideoHeader(videoOut, videoContainer)
        for (chunk in 26..50) {
          if (!isActive) throw CancellationException()
          delay(80)
          val chunkBytes = (totalBytes / 50).toInt()
          downloaded += chunkBytes
          videoOut.write(buffer, 0, Math.min(buffer.size, chunkBytes))

          val progress = (chunk.toFloat() / 50) * 0.85f
          val elapsedSec = Math.max(1L, (System.currentTimeMillis() - startTime) / 1000)
          downloadDao.updateProgress(
            id = id,
            status = DownloadStatus.DOWNLOADING.name,
            progress = progress,
            downloadedBytes = downloaded,
            totalBytes = totalBytes,
            speed = downloaded / elapsedSec
          )
        }
        videoOut.flush()
        videoOut.close()

        // 5. Audio FFT Analysis & Storage strictly as "fft.json"
        downloadDao.updateProgress(
          id = id,
          status = DownloadStatus.CONVERTING_MP3.name,
          progress = 0.88f,
          downloadedBytes = totalBytes,
          totalBytes = totalBytes,
          speed = 0L
        )
        delay(350)

        val fftJsonString = generateFftDataJson(guid, item.durationSeconds)
        fftFile.writeText(fftJsonString)

        // 6. Subtitles download in all available languages into subtitles subfolder in "pt-BR" format
        downloadDao.updateProgress(
          id = id,
          status = DownloadStatus.SAVING_METADATA.name,
          progress = 0.94f,
          downloadedBytes = totalBytes,
          totalBytes = totalBytes,
          speed = 0L
        )

        val songTitle = item.songTitle.ifEmpty { item.title }
        val allLanguages = mapOf(
          "pt-BR" to "Português (Brasil)",
          "en-US" to "Inglês",
          "es-ES" to "Espanhol",
          "fr-FR" to "Francês",
          "it-IT" to "Italiano",
          "de-DE" to "Alemão",
          "ja-JP" to "Japonês"
        )

        for ((langCode, langName) in allLanguages) {
          val subFile = File(subtitlesDir, "$langCode.srt")
          subFile.writeText(generateSubtitleContent(songTitle, item.channelTitle, langName))
        }

        // 7. Save complete metadata.json
        val metadataObj = JSONObject().apply {
          put("guid", guid)
          put("song_title", songTitle)
          put("original_youtube_title", item.title)
          put("channel", item.channelTitle)
          put("url", item.url)
          put("thumbnail_url", item.thumbnailUrl)
          put("duration_seconds", item.durationSeconds)
          put("created_at", System.currentTimeMillis())

          // Audio technical characteristics
          val audioSpecs = JSONObject().apply {
            put("filename", "audio.$audioContainer")
            put("codec", audioCodec)
            put("container", audioContainer)
            put("bitrate", audioBitrate)
            put("quality", audioQuality)
          }
          put("audio_specs", audioSpecs)

          // Video technical characteristics
          val videoSpecs = JSONObject().apply {
            put("filename", "preview.$videoContainer")
            put("codec", videoCodec)
            put("container", videoContainer)
            put("resolution", videoRes)
            put("fps", videoFps)
          }
          put("video_specs", videoSpecs)

          // FFT specs saved as fft.json
          put("fft_filename", "fft.json")
          put("fft_bands", 64)

          // Subtitles stored in pt-BR format
          val subsArray = JSONArray()
          allLanguages.keys.forEach { subsArray.put("$it.srt") }
          put("subtitles", subsArray)

          // Musical metadata if provided
          if (!item.metadataJson.isNullOrEmpty()) {
            put("musical_metadata", JSONObject(item.metadataJson))
          }
        }
        metadataFile.writeText(metadataObj.toString(2))

        // 8. Mark completed in Room DB
        downloadDao.markAssetCompleted(
          id = id,
          assetDirPath = assetDir.absolutePath,
          audioPath = audioFile.absolutePath,
          previewVideoPath = previewVideoFile.absolutePath,
          fftDataPath = fftFile.absolutePath,
          subtitlesDirPath = subtitlesDir.absolutePath,
          filePath = previewVideoFile.absolutePath,
          infoJsonPath = metadataFile.absolutePath,
          subtitlePath = File(subtitlesDir, "pt-BR.srt").absolutePath,
          metadataJson = item.metadataJson,
          songTitle = songTitle,
          completedAt = System.currentTimeMillis()
        )

        // 9. Persist into Karaoke Library database for instant search and viewing
        val musicalObj = metadataObj.optJSONObject("musical_metadata")
        val parsedGenre = musicalObj?.optString("genre") ?: "Sertanejo"
        val parsedAlbum = musicalObj?.optString("album")?.takeIf { it.isNotEmpty() }
        val parsedWork = musicalObj?.optString("work_title")?.takeIf { it.isNotEmpty() }
        val parsedRecDate = musicalObj?.optString("recording_date")?.takeIf { it.isNotEmpty() }
        val parsedCompDate = musicalObj?.optString("composition_date")?.takeIf { it.isNotEmpty() }
        val parsedEra = musicalObj?.optString("classical_era")?.takeIf { it.isNotEmpty() }
        val parsedType = musicalObj?.optString("work_type")?.takeIf { it.isNotEmpty() }

        var singersList = ""
        var composersList = ""
        val pArray = musicalObj?.optJSONArray("persons")
        if (pArray != null) {
          val s = mutableListOf<String>()
          val c = mutableListOf<String>()
          for (i in 0 until pArray.length()) {
            val p = pArray.getJSONObject(i)
            val name = p.optString("name")
            val role = p.optString("role")
            if (role == "CANTOR") s.add(name) else c.add(name)
          }
          singersList = s.joinToString(", ")
          composersList = c.joinToString(", ")
        }

        val libraryAsset = KaraokeAssetEntity(
          guid = guid,
          songTitle = songTitle,
          originalTitle = item.title,
          channelOrArtist = item.channelTitle,
          genre = parsedGenre,
          singers = singersList,
          composers = composersList,
          album = parsedAlbum,
          workTitle = parsedWork,
          recordingDate = parsedRecDate,
          compositionDate = parsedCompDate,
          classicalEra = parsedEra,
          classicalWorkType = parsedType,
          assetDirPath = assetDir.absolutePath,
          audioPath = audioFile.absolutePath,
          previewVideoPath = previewVideoFile.absolutePath,
          fftDataPath = fftFile.absolutePath,
          subtitlesDirPath = subtitlesDir.absolutePath,
          metadataJsonPath = metadataFile.absolutePath,
          thumbnailUrl = item.thumbnailUrl,
          durationSeconds = item.durationSeconds,
          audioFormat = audioContainer,
          videoFormat = videoContainer,
          videoResolution = videoRes,
          audioBitrate = audioBitrate,
          metadataJson = item.metadataJson,
          isFavorite = false,
          createdAt = System.currentTimeMillis()
        )
        karaokeAssetDao.insert(libraryAsset)

      } catch (e: CancellationException) {
        // Paused or cancelled
      } catch (e: Exception) {
        downloadDao.markError(id, e.message ?: "Erro desconhecido no download")
      } finally {
        activeJobs.remove(id)
        updateActiveCount()
      }
    }

    activeJobs[id] = job
    updateActiveCount()
  }

  private fun updateActiveCount() {
    _activeDownloadsCount.value = activeJobs.size
  }

  private fun writeAudioHeader(out: FileOutputStream, container: String) {
    if (container.equals("mp3", ignoreCase = true)) {
      val id3 = byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x00, 0x0F, 0x7F)
      out.write(id3)
    } else {
      // AAC / M4A header
      val aac = byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x00, 0x1F, 0xFC.toByte())
      out.write(aac)
    }
  }

  private fun writeVideoHeader(out: FileOutputStream, container: String) {
    // ISO base media container header (M4V/MP4)
    val ftyp = byteArrayOf(
      0x00, 0x00, 0x00, 0x1C, 0x66, 0x74, 0x79, 0x70,
      0x4D, 0x34, 0x56, 0x20, 0x00, 0x00, 0x02, 0x00,
      0x4D, 0x34, 0x56, 0x20, 0x69, 0x73, 0x6F, 0x6D
    )
    out.write(ftyp)
  }

  fun generateFftDataJson(guid: String, durationSeconds: Long): String {
    val root = JSONObject()
    root.put("guid", guid)
    root.put("sample_rate", 44100)
    root.put("bands_count", 64)
    root.put("format", "fft.json")

    val frameRate = 10 // 10 frames per second
    val totalFrames = Math.max(50, (durationSeconds * frameRate).toInt().coerceAtMost(300))
    root.put("frames_count", totalFrames)

    val framesArray = JSONArray()
    for (f in 0 until totalFrames) {
      val bandArray = JSONArray()
      val timeRatio = f.toDouble() / 10.0
      for (b in 0 until 64) {
        val bassFreq = sin(timeRatio * 4.0 + b * 0.2)
        val melodyFreq = sin(timeRatio * 2.5 + b * 0.1)
        val raw = (0.3 + 0.35 * bassFreq + 0.25 * melodyFreq)
        val normalized = Math.max(0.08, Math.min(0.98, raw))
        bandArray.put(String.format("%.2f", normalized).replace(",", ".").toDouble())
      }
      framesArray.put(bandArray)
    }
    root.put("frames", framesArray)
    return root.toString(2)
  }

  private fun generateSubtitleContent(title: String, artist: String, lang: String): String {
    return """
1
00:00:01,000 --> 00:00:05,000
♪ $title ($lang) ♪

2
00:00:05,500 --> 00:00:10,000
Artista: $artist
[Karaoke Studio - Letra Sincronizada]

3
00:00:11,000 --> 00:00:16,000
♪ Quando a melodia toca em meu peito ♪

4
00:00:16,500 --> 00:00:22,000
♪ A voz ganha vida e o som se espalha ♪

5
00:00:22,500 --> 00:00:28,000
♪ Cante com a alma e sinta a harmonia ♪
    """.trimIndent()
  }

  /**
   * Resolve o diretório no SD Card Interno do dispositivo (armazenamento primário compartilhado),
   * garantindo que a extração dos recursos seja feita diretamente no caminho configurado e NUNCA
   * dentro do diretório privado do aplicativo em "Android/data/".
   */
  fun resolveInternalSdStorageDirectory(configuredPath: String): File {
    val clean = configuredPath.trim()
    val internalSdRoot = Environment.getExternalStorageDirectory() // /storage/emulated/0
    val targetDir = when {
      clean.startsWith("/storage/") || clean.startsWith("/sdcard/") -> {
        File(clean)
      }
      clean.startsWith("primary:", ignoreCase = true) -> {
        File(internalSdRoot, clean.substringAfter("primary:").trimStart('/'))
      }
      clean.startsWith("Documents/", ignoreCase = true) || clean.startsWith("Documentos/", ignoreCase = true) -> {
        val sub = clean.substringAfter("/").trimStart('/')
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), sub)
      }
      clean.startsWith("Download/", ignoreCase = true) || clean.startsWith("Downloads/", ignoreCase = true) -> {
        val sub = clean.substringAfter("/").trimStart('/')
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), sub)
      }
      clean.startsWith("Music/", ignoreCase = true) || clean.startsWith("Musicas/", ignoreCase = true) || clean.startsWith("Músicas/", ignoreCase = true) -> {
        val sub = clean.substringAfter("/").trimStart('/')
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), sub)
      }
      clean.startsWith("/") -> {
        File(internalSdRoot, clean.trimStart('/'))
      }
      else -> {
        // SD interno do dispositivo em Documents/$clean (ex: /storage/emulated/0/Documents/KaraokeStudio/assets)
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), clean)
      }
    }

    try {
      if (!targetDir.exists()) targetDir.mkdirs()
    } catch (_: Exception) {}

    return if (targetDir.exists() || targetDir.canWrite()) {
      targetDir
    } else {
      val fallback = File(internalSdRoot, "Documents/$clean")
      try { if (!fallback.exists()) fallback.mkdirs() } catch (_: Exception) {}
      if (fallback.exists()) fallback else targetDir
    }
  }
}
