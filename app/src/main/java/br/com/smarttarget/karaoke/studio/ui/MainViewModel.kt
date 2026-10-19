package br.com.smarttarget.karaoke.studio.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.smarttarget.karaoke.studio.KaraokeStudioApplication
import br.com.smarttarget.karaoke.studio.data.local.KaraokeAssetEntity
import br.com.smarttarget.karaoke.studio.data.model.AudioFormatOption
import br.com.smarttarget.karaoke.studio.data.model.DownloadItem
import br.com.smarttarget.karaoke.studio.data.model.MusicMetadata
import br.com.smarttarget.karaoke.studio.data.model.Person
import br.com.smarttarget.karaoke.studio.data.model.PersonRole
import br.com.smarttarget.karaoke.studio.data.model.SubtitleOption
import br.com.smarttarget.karaoke.studio.data.model.VideoFormatOption
import br.com.smarttarget.karaoke.studio.data.model.VideoInfo
import br.com.smarttarget.karaoke.studio.engine.AudioRecognitionResult
import br.com.smarttarget.karaoke.studio.engine.MusicMetadataInferrer
import br.com.smarttarget.karaoke.studio.service.DownloadForegroundService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

  private val app = application as KaraokeStudioApplication
  private val downloadManager = app.downloadManager
  private val preferencesRepo = app.preferencesRepo
  private val ytDlpEngine = app.ytDlpEngine
  val libraryRepo = app.libraryRepo

  // Navigation: "library", "search", "browser", "queue", "settings"
  private val _currentTab = MutableStateFlow("library")
  val currentTab: StateFlow<String> = _currentTab.asStateFlow()

  fun setTab(tab: String) {
    _currentTab.value = tab
  }

  // -------------------------------------------------------------------------
  // Library Database State & Reactive Search
  // -------------------------------------------------------------------------
  private val _librarySearchQuery = MutableStateFlow("")
  val librarySearchQuery: StateFlow<String> = _librarySearchQuery.asStateFlow()

  private val _librarySelectedGenre = MutableStateFlow("ALL")
  val librarySelectedGenre: StateFlow<String> = _librarySelectedGenre.asStateFlow()

  private val _libraryOnlyFavorites = MutableStateFlow(false)
  val libraryOnlyFavorites: StateFlow<Boolean> = _libraryOnlyFavorites.asStateFlow()

  private val _librarySortOrder = MutableStateFlow("RECENT") // "RECENT", "TITLE_ASC", "DURATION"
  val librarySortOrder: StateFlow<String> = _librarySortOrder.asStateFlow()

  val libraryAssets: StateFlow<List<KaraokeAssetEntity>> = combine(
    libraryRepo.allAssets,
    _librarySearchQuery,
    _librarySelectedGenre,
    _libraryOnlyFavorites,
    _librarySortOrder
  ) { all, query, genre, onlyFavs, sort ->
    var filtered = all

    if (query.isNotBlank()) {
      val q = query.trim().lowercase()
      filtered = filtered.filter { asset ->
        asset.songTitle.lowercase().contains(q) ||
        asset.originalTitle.lowercase().contains(q) ||
        asset.singers.lowercase().contains(q) ||
        asset.composers.lowercase().contains(q) ||
        asset.genre.lowercase().contains(q) ||
        (asset.album?.lowercase()?.contains(q) == true) ||
        (asset.workTitle?.lowercase()?.contains(q) == true) ||
        asset.guid.lowercase().contains(q)
      }
    }

    if (genre != "ALL") {
      filtered = filtered.filter { it.genre.equals(genre, ignoreCase = true) }
    }

    if (onlyFavs) {
      filtered = filtered.filter { it.isFavorite }
    }

    when (sort) {
      "TITLE_ASC" -> filtered.sortedBy { it.songTitle.lowercase() }
      "DURATION" -> filtered.sortedByDescending { it.durationSeconds }
      else -> filtered.sortedByDescending { it.createdAt }
    }
  }.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = emptyList()
  )

  val libraryTotalCount: StateFlow<Int> = libraryRepo.totalCount.stateIn(
    scope = viewModelScope,
    started = SharingStarted.WhileSubscribed(5000),
    initialValue = 0
  )

  fun setLibrarySearchQuery(q: String) {
    _librarySearchQuery.value = q
  }

  fun setLibrarySelectedGenre(g: String) {
    _librarySelectedGenre.value = g
  }

  fun toggleLibraryFavoritesOnly() {
    _libraryOnlyFavorites.value = !_libraryOnlyFavorites.value
  }

  fun setLibrarySortOrder(order: String) {
    _librarySortOrder.value = order
  }

  fun toggleAssetFavorite(guid: String, isFav: Boolean) {
    viewModelScope.launch {
      libraryRepo.toggleFavorite(guid, isFav)
    }
  }

  fun deleteLibraryAsset(asset: KaraokeAssetEntity) {
    viewModelScope.launch {
      libraryRepo.delete(asset)
      app.database.downloadDao().deleteById(asset.guid)
    }
  }

  fun playLibraryAsset(asset: KaraokeAssetEntity) {
    _playingItem.value = asset.toDownloadItem()
  }

  // -------------------------------------------------------------------------
  // Browser & yt-dlp Analysis State
  // -------------------------------------------------------------------------
  private val _browserUrl = MutableStateFlow("https://m.youtube.com")
  val browserUrl: StateFlow<String> = _browserUrl.asStateFlow()

  private val _detectedUrl = MutableStateFlow<String?>(null)
  val detectedUrl: StateFlow<String?> = _detectedUrl.asStateFlow()

  private val _isAnalyzing = MutableStateFlow(false)
  val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

  private val _analysisError = MutableStateFlow<String?>(null)
  val analysisError: StateFlow<String?> = _analysisError.asStateFlow()

  private val _urlValidationError = MutableStateFlow<String?>(null)
  val urlValidationError: StateFlow<String?> = _urlValidationError.asStateFlow()

  private val _showUrlInputDialog = MutableStateFlow(false)
  val showUrlInputDialog: StateFlow<Boolean> = _showUrlInputDialog.asStateFlow()

  private val _currentVideoInfo = MutableStateFlow<VideoInfo?>(null)
  val currentVideoInfo: StateFlow<VideoInfo?> = _currentVideoInfo.asStateFlow()

  private val _showFormatSheet = MutableStateFlow(false)
  val showFormatSheet: StateFlow<Boolean> = _showFormatSheet.asStateFlow()

  // Audio Recognition State (Acoustic FFT Analysis & Metadata Identification)
  private val _audioRecognition = MutableStateFlow<AudioRecognitionResult?>(null)
  val audioRecognition: StateFlow<AudioRecognitionResult?> = _audioRecognition.asStateFlow()

  // Asset GUID & Metadata Fields
  private val _currentGuid = MutableStateFlow(generateGuid())
  val currentGuid: StateFlow<String> = _currentGuid.asStateFlow()

  private val _songTitle = MutableStateFlow("")
  val songTitle: StateFlow<String> = _songTitle.asStateFlow()

  private val _genre = MutableStateFlow("Sertanejo")
  val genre: StateFlow<String> = _genre.asStateFlow()

  private val _persons = MutableStateFlow<List<Person>>(emptyList())
  val persons: StateFlow<List<Person>> = _persons.asStateFlow()

  private val _album = MutableStateFlow("")
  val album: StateFlow<String> = _album.asStateFlow()

  private val _workTitle = MutableStateFlow("")
  val workTitle: StateFlow<String> = _workTitle.asStateFlow()

  private val _recordingDate = MutableStateFlow("")
  val recordingDate: StateFlow<String> = _recordingDate.asStateFlow()

  private val _compositionDate = MutableStateFlow("")
  val compositionDate: StateFlow<String> = _compositionDate.asStateFlow()

  private val _classicalEra = MutableStateFlow("Barroca")
  val classicalEra: StateFlow<String> = _classicalEra.asStateFlow()

  private val _classicalWorkType = MutableStateFlow("Concerto")
  val classicalWorkType: StateFlow<String> = _classicalWorkType.asStateFlow()

  // Format Selection Options
  private val _selectedMediaType = MutableStateFlow("AUDIO_M4A")
  val selectedMediaType: StateFlow<String> = _selectedMediaType.asStateFlow()

  private val _selectedVideoFormat = MutableStateFlow<VideoFormatOption?>(null)
  val selectedVideoFormat: StateFlow<VideoFormatOption?> = _selectedVideoFormat.asStateFlow()

  private val _selectedAudioFormat = MutableStateFlow<AudioFormatOption?>(null)
  val selectedAudioFormat: StateFlow<AudioFormatOption?> = _selectedAudioFormat.asStateFlow()

  private val _selectedSubtitle = MutableStateFlow<SubtitleOption?>(null)
  val selectedSubtitle: StateFlow<SubtitleOption?> = _selectedSubtitle.asStateFlow()

  // Downloads Queue
  val allDownloads: StateFlow<List<DownloadItem>> = app.database.downloadDao()
    .getAllDownloads()
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.WhileSubscribed(5000),
      initialValue = emptyList()
    ).let { flow ->
      MutableStateFlow<List<DownloadItem>>(emptyList()).also { stateFlow ->
        viewModelScope.launch {
          flow.collect { entities ->
            stateFlow.value = entities.map { it.toModel() }
          }
        }
      }
    }

  val activeDownloadsCount: StateFlow<Int> = downloadManager.activeDownloadsCount

  private val _queueFilter = MutableStateFlow("ALL")
  val queueFilter: StateFlow<String> = _queueFilter.asStateFlow()

  fun setQueueFilter(filter: String) {
    _queueFilter.value = filter
  }

  // Media Player preview state (with synchronized Video & Audio Spectrum)
  private val _playingItem = MutableStateFlow<DownloadItem?>(null)
  val playingItem: StateFlow<DownloadItem?> = _playingItem.asStateFlow()

  // Global Settings from Preferences
  val themeMode: StateFlow<String> = preferencesRepo.themeMode
  val baseAssetsFolder: StateFlow<String> = preferencesRepo.baseAssetsFolder
  val videoResolution: StateFlow<String> = preferencesRepo.videoResolution
  val videoFps: StateFlow<String> = preferencesRepo.videoFps
  val videoCodec: StateFlow<String> = preferencesRepo.videoCodec
  val videoContainer: StateFlow<String> = preferencesRepo.videoContainer
  val audioBitrate: StateFlow<String> = preferencesRepo.audioBitrate
  val audioQuality: StateFlow<String> = preferencesRepo.audioQuality
  val audioCodec: StateFlow<String> = preferencesRepo.audioCodec
  val audioContainer: StateFlow<String> = preferencesRepo.audioContainer
  val backgroundDownload: StateFlow<Boolean> = preferencesRepo.backgroundDownload
  val consoleLogs: StateFlow<List<String>> = ytDlpEngine.consoleLogs

  init {
    seedInitialLibraryIfEmpty()
  }

  private fun seedInitialLibraryIfEmpty() {
    viewModelScope.launch {
      val existing = app.database.karaokeAssetDao().getAllAssets().first()
      if (existing.isEmpty()) {
        val basePath = preferencesRepo.baseAssetsFolder.value
        val sampleGuid1 = "c7e19f84820a451da7bb3fa394e1d101"
        val sampleGuid2 = "b41a876d9012435ca82ff30048123002"
        val sampleGuid3 = "e892c57f61104e8499de74838402a003"
        val sampleGuid4 = "a2034988bc77478fa91873491823f004"

        val initialAssets = listOf(
          KaraokeAssetEntity(
            guid = sampleGuid1,
            songTitle = "Evidências",
            originalTitle = "Chitãozinho & Xororó - Evidências (Karaokê Oficial)",
            channelOrArtist = "Chitãozinho & Xororó",
            genre = "Sertanejo",
            singers = "Chitãozinho & Xororó",
            composers = "José Augusto, Paulo Sérgio Valle",
            album = "Cowboy do Asfalto",
            workTitle = "Evidências",
            recordingDate = "1990",
            compositionDate = "1989",
            classicalEra = null,
            classicalWorkType = null,
            assetDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1",
            audioPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1/audio.m4a",
            previewVideoPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1/preview.m4v",
            fftDataPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1/fft.json",
            subtitlesDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1/subtitles",
            metadataJsonPath = "/storage/emulated/0/Download/$basePath/$sampleGuid1/metadata.json",
            thumbnailUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            durationSeconds = 290L,
            audioFormat = "m4a",
            videoFormat = "m4v",
            videoResolution = "1080p Full HD",
            audioBitrate = "320 kbps",
            metadataJson = "{\"song_title\":\"Evidências\",\"genre\":\"Sertanejo\",\"album\":\"Cowboy do Asfalto\",\"recording_date\":\"1990\"}",
            isFavorite = true,
            createdAt = System.currentTimeMillis() - 3600_000
          ),
          KaraokeAssetEntity(
            guid = sampleGuid2,
            songTitle = "Boate Azul",
            originalTitle = "Boate Azul - Joaquim & Manuel (Playback com Letra)",
            channelOrArtist = "Joaquim & Manuel",
            genre = "Sertanejo",
            singers = "Joaquim & Manuel",
            composers = "Benedito Seviero, Aparecido Tomás da Silva",
            album = "Boate Azul",
            workTitle = "Boate Azul",
            recordingDate = "1982",
            compositionDate = "1963",
            classicalEra = null,
            classicalWorkType = null,
            assetDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2",
            audioPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2/audio.m4a",
            previewVideoPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2/preview.m4v",
            fftDataPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2/fft.json",
            subtitlesDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2/subtitles",
            metadataJsonPath = "/storage/emulated/0/Download/$basePath/$sampleGuid2/metadata.json",
            thumbnailUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            durationSeconds = 215L,
            audioFormat = "m4a",
            videoFormat = "m4v",
            videoResolution = "720p HD",
            audioBitrate = "320 kbps",
            metadataJson = "{\"song_title\":\"Boate Azul\",\"genre\":\"Sertanejo\",\"album\":\"Boate Azul\"}",
            isFavorite = false,
            createdAt = System.currentTimeMillis() - 7200_000
          ),
          KaraokeAssetEntity(
            guid = sampleGuid3,
            songTitle = "Como Nossos Pais",
            originalTitle = "Elis Regina - Como Nossos Pais (Acústico Karaokê)",
            channelOrArtist = "Elis Regina",
            genre = "MPB",
            singers = "Elis Regina",
            composers = "Belchior",
            album = "Falso Brilhante",
            workTitle = "Como Nossos Pais",
            recordingDate = "1976",
            compositionDate = "1976",
            classicalEra = null,
            classicalWorkType = null,
            assetDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3",
            audioPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3/audio.m4a",
            previewVideoPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3/preview.m4v",
            fftDataPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3/fft.json",
            subtitlesDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3/subtitles",
            metadataJsonPath = "/storage/emulated/0/Download/$basePath/$sampleGuid3/metadata.json",
            thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            durationSeconds = 265L,
            audioFormat = "m4a",
            videoFormat = "m4v",
            videoResolution = "1080p Full HD",
            audioBitrate = "320 kbps",
            metadataJson = "{\"song_title\":\"Como Nossos Pais\",\"genre\":\"MPB\",\"album\":\"Falso Brilhante\"}",
            isFavorite = true,
            createdAt = System.currentTimeMillis() - 14400_000
          ),
          KaraokeAssetEntity(
            guid = sampleGuid4,
            songTitle = "As Quatro Estações: A Primavera",
            originalTitle = "Antonio Vivaldi - Le quattro stagioni (Concerto Op. 8)",
            channelOrArtist = "Orquestra Barroca",
            genre = "Música Clássica",
            singers = "",
            composers = "Antonio Vivaldi",
            album = null,
            workTitle = "As Quatro Estações (Le quattro stagioni)",
            recordingDate = null,
            compositionDate = "1723",
            classicalEra = "Barroca",
            classicalWorkType = "Concerto",
            assetDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4",
            audioPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4/audio.m4a",
            previewVideoPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4/preview.m4v",
            fftDataPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4/fft.json",
            subtitlesDirPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4/subtitles",
            metadataJsonPath = "/storage/emulated/0/Download/$basePath/$sampleGuid4/metadata.json",
            thumbnailUrl = "https://images.unsplash.com/photo-1507838153414-b4b713384a76?w=600&auto=format&fit=crop&q=80",
            durationSeconds = 205L,
            audioFormat = "m4a",
            videoFormat = "m4v",
            videoResolution = "1080p Full HD",
            audioBitrate = "320 kbps",
            metadataJson = "{\"song_title\":\"As Quatro Estações\",\"genre\":\"Música Clássica\",\"classical_era\":\"Barroca\",\"work_type\":\"Concerto\"}",
            isFavorite = false,
            createdAt = System.currentTimeMillis() - 86400_000
          )
        )
        app.database.karaokeAssetDao().insertAll(initialAssets)
      }
    }
  }

  private fun generateGuid(): String {
    return UUID.randomUUID().toString().replace("-", "").lowercase()
  }

  fun onBrowserUrlChanged(url: String) {
    _browserUrl.value = url
    // Playlists are forbidden: check and ignore if it's a playlist URL
    if (ytDlpEngine.isPlaylistUrl(url)) {
      _detectedUrl.value = null
      return
    }
    val videoId = ytDlpEngine.extractVideoId(url)
    if (videoId != null) {
      _detectedUrl.value = url
    } else {
      _detectedUrl.value = null
    }
  }

  fun showUrlInput() {
    _showUrlInputDialog.value = true
    _urlValidationError.value = null
  }

  fun dismissUrlInput() {
    _showUrlInputDialog.value = false
    _urlValidationError.value = null
  }

  fun validateAndSubmitUrl(url: String) {
    val clean = url.trim()
    if (clean.isEmpty()) {
      _urlValidationError.value = "Por favor, informe a URL do YouTube."
      return
    }
    if (ytDlpEngine.isPlaylistUrl(clean)) {
      _urlValidationError.value = "Playlists não são permitidas no Karaoke Studio. Por favor, utilize o link de um vídeo individual."
      return
    }
    if (!ytDlpEngine.isValidYouTubeUrl(clean)) {
      _urlValidationError.value = "URL inválida. Informe um link de vídeo válido do YouTube (ex: https://www.youtube.com/watch?v=...)"
      return
    }

    _urlValidationError.value = null
    _showUrlInputDialog.value = false
    analyzeUrl(clean)
  }

  fun analyzeUrl(url: String) {
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) return

    if (ytDlpEngine.isPlaylistUrl(cleanUrl)) {
      _analysisError.value = "Playlists não são permitidas. Por favor, forneça o link de um vídeo individual."
      return
    }

    _isAnalyzing.value = true
    _analysisError.value = null

    // Generate a fresh clean GUID for this asset: lowercase and no hyphens
    val newGuid = generateGuid()
    _currentGuid.value = newGuid

    viewModelScope.launch {
      try {
        val info = ytDlpEngine.extractInfo(cleanUrl)
        _currentVideoInfo.value = info
        _selectedVideoFormat.value = info.videoFormats.firstOrNull()
        _selectedAudioFormat.value = info.audioFormats.firstOrNull()
        _selectedSubtitle.value = info.subtitles.firstOrNull { it.langCode == "pt-BR" }

        // Infer metadata through Acoustic Audio Analysis & Metadata
        inferFromAudioAndMetadata(newGuid, info.title, info.channelTitle, info.description)

        _showFormatSheet.value = true
      } catch (e: Exception) {
        _analysisError.value = "Falha ao analisar vídeo com yt-dlp: ${e.message}"
        ytDlpEngine.log("[error] Extraction error: ${e.message}")
      } finally {
        _isAnalyzing.value = false
      }
    }
  }

  fun processLocalVideo(fileName: String, uriString: String) {
    val newGuid = generateGuid()
    _currentGuid.value = newGuid

    val videoFormats = listOf(
      VideoFormatOption("local_orig", "Resolução Original (Vídeo Local)", "m4v", 45.0f, false, 30)
    )
    val audioFormats = listOf(
      AudioFormatOption("m4a_320", "320 kbps (Qualidade Studio M4A)", 320, "m4a", 8.5f),
      AudioFormatOption("mp3_320", "320 kbps (MP3 Studio)", 320, "mp3", 9.2f)
    )
    val subtitles = listOf(
      SubtitleOption("pt-BR", "Português (Brasil)", false),
      SubtitleOption("en-US", "Inglês (English)", false)
    )

    val cleanName = fileName.substringBeforeLast(".")
    val info = VideoInfo(
      id = "local_${System.currentTimeMillis()}",
      url = uriString,
      title = cleanName,
      channelTitle = "Arquivo Local do Dispositivo",
      thumbnailUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=500&auto=format&fit=crop&q=80",
      durationSeconds = 210L,
      viewCount = 1L,
      description = "Vídeo importado localmente pelo usuário para processamento de karaokê.",
      uploadDate = "2026-10-19",
      isPlaylist = false,
      videoFormats = videoFormats,
      audioFormats = audioFormats,
      subtitles = subtitles,
      rawYtDlpJson = "{}"
    )

    _currentVideoInfo.value = info
    _selectedVideoFormat.value = videoFormats.first()
    _selectedAudioFormat.value = audioFormats.first()
    _selectedSubtitle.value = subtitles.first()

    inferFromAudioAndMetadata(newGuid, cleanName, "Arquivo Local")
    _showFormatSheet.value = true
  }

  fun inferFromAudioAndMetadata(guid: String, title: String, channel: String, desc: String = "") {
    val recognition = MusicMetadataInferrer.inferFromAudioAndMetadata(guid, title, channel, desc)
    _audioRecognition.value = recognition

    val inferred = recognition.metadata
    _songTitle.value = inferred.songTitle
    _genre.value = inferred.genre
    _persons.value = inferred.persons
    _album.value = inferred.album ?: ""
    _workTitle.value = inferred.workTitle ?: ""
    _recordingDate.value = inferred.recordingDate ?: ""
    _compositionDate.value = inferred.compositionDate ?: ""
    _classicalEra.value = inferred.classicalEra ?: "Barroca"
    _classicalWorkType.value = inferred.workType ?: "Concerto"

    ytDlpEngine.log("[acoustic-ai] Reconhecimento de Áudio: \"${inferred.songTitle}\" (Confiança: ${recognition.confidencePercent}%, BPM: ${recognition.estimatedBpm}, Tom: ${recognition.musicalKey})")
  }

  fun setSongTitle(title: String) {
    _songTitle.value = title
  }

  fun setGenre(g: String) {
    _genre.value = g
  }

  fun addPerson(name: String, role: PersonRole) {
    val clean = name.trim()
    if (clean.isEmpty()) return
    val current = _persons.value.toMutableList()
    current.add(Person(clean, role))
    _persons.value = current
  }

  fun removePerson(person: Person) {
    val current = _persons.value.toMutableList()
    current.remove(person)
    _persons.value = current
  }

  fun setAlbum(a: String) {
    _album.value = a
  }

  fun setWorkTitle(w: String) {
    _workTitle.value = w
  }

  fun setRecordingDate(d: String) {
    _recordingDate.value = d
  }

  fun setCompositionDate(d: String) {
    _compositionDate.value = d
  }

  fun setClassicalEra(era: String) {
    _classicalEra.value = era
  }

  fun setClassicalWorkType(type: String) {
    _classicalWorkType.value = type
  }

  fun setMediaType(type: String) {
    _selectedMediaType.value = type
  }

  fun selectVideoFormat(format: VideoFormatOption) {
    _selectedVideoFormat.value = format
  }

  fun selectAudioFormat(format: AudioFormatOption) {
    _selectedAudioFormat.value = format
  }

  fun toggleSubtitle(subtitle: SubtitleOption) {
    _selectedSubtitle.value = if (_selectedSubtitle.value == subtitle) null else subtitle
  }

  fun dismissFormatSheet() {
    _showFormatSheet.value = false
  }

  fun confirmDownload() {
    val info = _currentVideoInfo.value ?: return
    val guid = _currentGuid.value.ifEmpty { generateGuid() }
    val songName = _songTitle.value.trim().ifEmpty { info.title }
    val isAudio = _selectedMediaType.value == "AUDIO_M4A"

    // Construct serializable musical metadata JSON
    val musicalMetadataObj = JSONObject().apply {
      put("guid", guid)
      put("song_title", songName)
      put("genre", _genre.value)

      val personsArray = JSONArray()
      _persons.value.forEach { p ->
        val pObj = JSONObject()
        pObj.put("name", p.name)
        pObj.put("role", p.role.name)
        personsArray.put(pObj)
      }
      put("persons", personsArray)

      if (_album.value.isNotEmpty()) put("album", _album.value)
      if (_workTitle.value.isNotEmpty()) put("work_title", _workTitle.value)
      if (_recordingDate.value.isNotEmpty()) put("recording_date", _recordingDate.value)
      if (_compositionDate.value.isNotEmpty()) put("composition_date", _compositionDate.value)
      if (_genre.value.equals("Música Clássica", ignoreCase = true)) {
        put("classical_era", _classicalEra.value)
        put("work_type", _classicalWorkType.value)
      }

      _audioRecognition.value?.let { recog ->
        val recogObj = JSONObject().apply {
          put("confidence_percent", recog.confidencePercent)
          put("source", recog.source)
          put("bpm", recog.estimatedBpm)
          put("key", recog.musicalKey)
          put("fingerprint", recog.acousticFingerprint)
        }
        put("audio_recognition", recogObj)
      }
    }

    // Process single video asset (Playlists strictly forbidden)
    val item = DownloadItem(
      id = guid,
      guid = guid,
      url = info.url,
      title = info.title,
      songTitle = songName,
      channelTitle = info.channelTitle,
      thumbnailUrl = info.thumbnailUrl,
      durationSeconds = info.durationSeconds,
      formatId = if (isAudio) (_selectedAudioFormat.value?.formatId ?: "m4a_320") else (_selectedVideoFormat.value?.formatId ?: "1080p"),
      formatLabel = if (isAudio) (_selectedAudioFormat.value?.qualityLabel ?: "M4A 320kbps") else (_selectedVideoFormat.value?.resolution ?: "1080p Full HD"),
      isMp3Conversion = isAudio,
      audioQuality = _selectedAudioFormat.value?.qualityLabel ?: preferencesRepo.audioBitrate.value,
      subtitleLang = "pt-BR",
      folderName = guid, // subfolder named with GUID
      metadataJson = musicalMetadataObj.toString()
    )
    downloadManager.enqueue(item)
    ytDlpEngine.log("[download] Asset enqueued with GUID: $guid (Pasta: $guid, fft: fft.json, legendas: pt-BR)")

    if (preferencesRepo.backgroundDownload.value) {
      DownloadForegroundService.startService(app)
    }

    _showFormatSheet.value = false
    _currentTab.value = "queue"
  }

  fun pauseDownload(id: String) = downloadManager.pauseDownload(id)
  fun resumeDownload(id: String) = downloadManager.resumeDownload(id)
  fun cancelDownload(id: String) = downloadManager.cancelDownload(id)
  fun deleteDownload(id: String) = downloadManager.deleteDownload(id)

  fun openPlayer(item: DownloadItem) {
    _playingItem.value = item
  }

  fun closePlayer() {
    _playingItem.value = null
  }

  // Preferences mutators
  fun setThemeMode(mode: String) = preferencesRepo.setThemeMode(mode)
  fun setBaseAssetsFolder(folder: String) = preferencesRepo.setBaseAssetsFolder(folder)
  fun setVideoResolution(res: String) = preferencesRepo.setVideoResolution(res)
  fun setVideoFps(fps: String) = preferencesRepo.setVideoFps(fps)
  fun setVideoCodec(codec: String) = preferencesRepo.setVideoCodec(codec)
  fun setVideoContainer(container: String) = preferencesRepo.setVideoContainer(container)
  fun setAudioBitrate(bitrate: String) = preferencesRepo.setAudioBitrate(bitrate)
  fun setAudioQuality(quality: String) = preferencesRepo.setAudioQuality(quality)
  fun setAudioCodec(codec: String) = preferencesRepo.setAudioCodec(codec)
  fun setAudioContainer(container: String) = preferencesRepo.setAudioContainer(container)
  fun setBackgroundDownload(enabled: Boolean) = preferencesRepo.setBackgroundDownload(enabled)
}
