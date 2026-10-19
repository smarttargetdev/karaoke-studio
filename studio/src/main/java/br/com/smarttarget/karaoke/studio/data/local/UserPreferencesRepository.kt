package br.com.smarttarget.karaoke.studio.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class UserPreferencesRepository(context: Context) {
  private val prefs: SharedPreferences = context.getSharedPreferences("karaoke_studio_prefs", Context.MODE_PRIVATE)

  // Appearance
  private val _themeMode = MutableStateFlow(prefs.getString(KEY_THEME_MODE, "dark") ?: "dark")
  val themeMode: StateFlow<String> = _themeMode.asStateFlow()

  // Base assets storage folder
  private val _baseAssetsFolder = MutableStateFlow(prefs.getString(KEY_BASE_ASSETS_FOLDER, "KaraokeStudio/assets") ?: "KaraokeStudio/assets")
  val baseAssetsFolder: StateFlow<String> = _baseAssetsFolder.asStateFlow()

  // Video Default Settings
  private val _videoResolution = MutableStateFlow(prefs.getString(KEY_VIDEO_RESOLUTION, "1080p") ?: "1080p")
  val videoResolution: StateFlow<String> = _videoResolution.asStateFlow()

  private val _videoFps = MutableStateFlow(prefs.getString(KEY_VIDEO_FPS, "60 fps") ?: "60 fps")
  val videoFps: StateFlow<String> = _videoFps.asStateFlow()

  private val _videoCodec = MutableStateFlow(prefs.getString(KEY_VIDEO_CODEC, "H.264 (AVC)") ?: "H.264 (AVC)")
  val videoCodec: StateFlow<String> = _videoCodec.asStateFlow()

  private val _videoContainer = MutableStateFlow(prefs.getString(KEY_VIDEO_CONTAINER, "m4v") ?: "m4v")
  val videoContainer: StateFlow<String> = _videoContainer.asStateFlow()

  // Audio Default Settings
  private val _audioBitrate = MutableStateFlow(prefs.getString(KEY_AUDIO_BITRATE, "320 kbps") ?: "320 kbps")
  val audioBitrate: StateFlow<String> = _audioBitrate.asStateFlow()

  private val _audioQuality = MutableStateFlow(prefs.getString(KEY_AUDIO_QUALITY, "Estúdio (High)") ?: "Estúdio (High)")
  val audioQuality: StateFlow<String> = _audioQuality.asStateFlow()

  private val _audioCodec = MutableStateFlow(prefs.getString(KEY_AUDIO_CODEC, "AAC") ?: "AAC")
  val audioCodec: StateFlow<String> = _audioCodec.asStateFlow()

  private val _audioContainer = MutableStateFlow(prefs.getString(KEY_AUDIO_CONTAINER, "m4a") ?: "m4a")
  val audioContainer: StateFlow<String> = _audioContainer.asStateFlow()

  // Background download toggle
  private val _backgroundDownload = MutableStateFlow(prefs.getBoolean(KEY_BACKGROUND_DOWNLOAD, true))
  val backgroundDownload: StateFlow<Boolean> = _backgroundDownload.asStateFlow()

  fun setThemeMode(mode: String) {
    prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    _themeMode.value = mode
  }

  fun setBaseAssetsFolder(folder: String) {
    val clean = folder.trim().ifEmpty { "KaraokeStudio/assets" }
    prefs.edit().putString(KEY_BASE_ASSETS_FOLDER, clean).apply()
    _baseAssetsFolder.value = clean
  }

  fun setVideoResolution(res: String) {
    prefs.edit().putString(KEY_VIDEO_RESOLUTION, res).apply()
    _videoResolution.value = res
  }

  fun setVideoFps(fps: String) {
    prefs.edit().putString(KEY_VIDEO_FPS, fps).apply()
    _videoFps.value = fps
  }

  fun setVideoCodec(codec: String) {
    prefs.edit().putString(KEY_VIDEO_CODEC, codec).apply()
    _videoCodec.value = codec
  }

  fun setVideoContainer(container: String) {
    prefs.edit().putString(KEY_VIDEO_CONTAINER, container).apply()
    _videoContainer.value = container
  }

  fun setAudioBitrate(bitrate: String) {
    prefs.edit().putString(KEY_AUDIO_BITRATE, bitrate).apply()
    _audioBitrate.value = bitrate
  }

  fun setAudioQuality(quality: String) {
    prefs.edit().putString(KEY_AUDIO_QUALITY, quality).apply()
    _audioQuality.value = quality
  }

  fun setAudioCodec(codec: String) {
    prefs.edit().putString(KEY_AUDIO_CODEC, codec).apply()
    _audioCodec.value = codec
  }

  fun setAudioContainer(container: String) {
    prefs.edit().putString(KEY_AUDIO_CONTAINER, container).apply()
    _audioContainer.value = container
  }

  fun setBackgroundDownload(enabled: Boolean) {
    prefs.edit().putBoolean(KEY_BACKGROUND_DOWNLOAD, enabled).apply()
    _backgroundDownload.value = enabled
  }

  companion object {
    private const val KEY_THEME_MODE = "pref_theme_mode"
    private const val KEY_BASE_ASSETS_FOLDER = "pref_base_assets_folder"
    private const val KEY_VIDEO_RESOLUTION = "pref_video_resolution"
    private const val KEY_VIDEO_FPS = "pref_video_fps"
    private const val KEY_VIDEO_CODEC = "pref_video_codec"
    private const val KEY_VIDEO_CONTAINER = "pref_video_container"
    private const val KEY_AUDIO_BITRATE = "pref_audio_bitrate"
    private const val KEY_AUDIO_QUALITY = "pref_audio_quality"
    private const val KEY_AUDIO_CODEC = "pref_audio_codec"
    private const val KEY_AUDIO_CONTAINER = "pref_audio_container"
    private const val KEY_BACKGROUND_DOWNLOAD = "pref_background_download"
  }
}
