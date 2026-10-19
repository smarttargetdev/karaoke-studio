package br.com.smarttarget.karaoke.studio

import android.app.Application
import br.com.smarttarget.karaoke.studio.data.local.AppDatabase
import br.com.smarttarget.karaoke.studio.data.local.KaraokeLibraryRepository
import br.com.smarttarget.karaoke.studio.data.local.UserPreferencesRepository
import br.com.smarttarget.karaoke.studio.engine.DownloadManager
import br.com.smarttarget.karaoke.studio.engine.YtDlpExtractorEngine

class KaraokeStudioApplication : Application() {

  lateinit var database: AppDatabase
    private set

  lateinit var preferencesRepo: UserPreferencesRepository
    private set

  lateinit var libraryRepo: KaraokeLibraryRepository
    private set

  lateinit var downloadManager: DownloadManager
    private set

  lateinit var ytDlpEngine: YtDlpExtractorEngine
    private set

  override fun onCreate() {
    super.onCreate()
    instance = this
    database = AppDatabase.getDatabase(this)
    preferencesRepo = UserPreferencesRepository(this)
    libraryRepo = KaraokeLibraryRepository(database.karaokeAssetDao())
    downloadManager = DownloadManager(this, database.downloadDao(), database.karaokeAssetDao(), preferencesRepo)
    ytDlpEngine = YtDlpExtractorEngine(this)
  }

  companion object {
    lateinit var instance: KaraokeStudioApplication
      private set
  }
}
