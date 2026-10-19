package br.com.smarttarget.karaoke.studio.data.local

import kotlinx.coroutines.flow.Flow
import java.io.File

class KaraokeLibraryRepository(
  private val assetDao: KaraokeAssetDao
) {
  val allAssets: Flow<List<KaraokeAssetEntity>> = assetDao.getAllAssets()
  val favorites: Flow<List<KaraokeAssetEntity>> = assetDao.getFavorites()
  val totalCount: Flow<Int> = assetDao.getAssetsCount()

  fun searchAssets(query: String): Flow<List<KaraokeAssetEntity>> {
    return if (query.isBlank()) {
      assetDao.getAllAssets()
    } else {
      assetDao.searchAssets(query.trim())
    }
  }

  fun getByGenre(genre: String): Flow<List<KaraokeAssetEntity>> {
    return assetDao.getAssetsByGenre(genre)
  }

  suspend fun getByGuid(guid: String): KaraokeAssetEntity? {
    return assetDao.getAssetByGuid(guid)
  }

  suspend fun insert(asset: KaraokeAssetEntity) {
    assetDao.insert(asset)
  }

  suspend fun toggleFavorite(guid: String, isFavorite: Boolean) {
    assetDao.setFavorite(guid, isFavorite)
  }

  suspend fun delete(asset: KaraokeAssetEntity) {
    assetDao.deleteByGuid(asset.guid)
    // Clean up local disk files if present
    try {
      if (asset.assetDirPath.isNotEmpty()) {
        File(asset.assetDirPath).deleteRecursively()
      }
    } catch (_: Exception) {}
  }
}
