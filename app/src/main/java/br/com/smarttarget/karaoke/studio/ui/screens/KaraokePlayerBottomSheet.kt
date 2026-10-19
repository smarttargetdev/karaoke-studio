package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.ui.MainViewModel
import br.com.smarttarget.karaoke.studio.ui.components.SynchronizedVideoAudioPlayer
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokePlayerBottomSheet(viewModel: MainViewModel) {
  val playingItem by viewModel.playingItem.collectAsState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  if (playingItem != null) {
    val item = playingItem!!

    // Parse musical metadata if present
    var parsedSongTitle = item.songTitle.ifEmpty { item.title }
    var parsedGenre = "Sertanejo"
    var parsedSingers = listOf<String>()
    var parsedComposers = listOf<String>()
    var parsedAlbum: String? = null
    var parsedWork: String? = null
    var parsedDate: String? = null
    var parsedClassicalEra: String? = null
    var parsedClassicalType: String? = null

    if (!item.metadataJson.isNullOrEmpty()) {
      try {
        val json = JSONObject(item.metadataJson)
        parsedSongTitle = json.optString("song_title", parsedSongTitle)
        parsedGenre = json.optString("genre", parsedGenre)
        parsedAlbum = json.optString("album").takeIf { it.isNotEmpty() }
        parsedWork = json.optString("work_title").takeIf { it.isNotEmpty() }
        parsedDate = json.optString("recording_date").ifEmpty { json.optString("composition_date") }.takeIf { it.isNotEmpty() }
        parsedClassicalEra = json.optString("classical_era").takeIf { it.isNotEmpty() }
        parsedClassicalType = json.optString("work_type").takeIf { it.isNotEmpty() }

        val pArray = json.optJSONArray("persons")
        if (pArray != null) {
          val sList = mutableListOf<String>()
          val cList = mutableListOf<String>()
          for (i in 0 until pArray.length()) {
            val pObj = pArray.getJSONObject(i)
            val name = pObj.optString("name")
            val role = pObj.optString("role")
            if (role == "CANTOR") sList.add(name) else cList.add(name)
          }
          parsedSingers = sList
          parsedComposers = cList
        }
      } catch (_: Exception) {}
    }

    ModalBottomSheet(
      onDismissRequest = { viewModel.closePlayer() },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface,
      tonalElevation = 8.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // COMPONENTE SINCRONIZADO DE VÍDEO, ÁUDIO, FFT E LEGENDAS
        SynchronizedVideoAudioPlayer(
          videoPath = item.previewVideoPath,
          audioPath = item.audioPath,
          fftDataPath = item.fftDataPath,
          subtitlesDirPath = item.subtitlesDirPath ?: item.subtitlePath,
          thumbnailUrl = item.thumbnailUrl,
          durationSeconds = item.durationSeconds,
          songTitle = parsedSongTitle,
          artist = if (parsedSingers.isNotEmpty()) parsedSingers.joinToString(", ") else item.channelTitle,
          onClose = { viewModel.closePlayer() }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // METADADOS MUSICAIS COMPLETOS DO ASSET
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Metadados do Asset & Especificações Técnicas:",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text("• Identificador GUID: ${item.guid}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("• Gênero: $parsedGenre", fontSize = 11.sp)
            if (parsedAlbum != null) Text("• Álbum: $parsedAlbum", fontSize = 11.sp)
            if (parsedWork != null) Text("• Obra: $parsedWork", fontSize = 11.sp)
            if (parsedDate != null) Text("• Data: $parsedDate", fontSize = 11.sp)
            if (parsedClassicalEra != null) Text("• Época Clássica: $parsedClassicalEra", fontSize = 11.sp)
            if (parsedClassicalType != null) Text("• Tipo de Obra: $parsedClassicalType", fontSize = 11.sp)
            Text("• Legendas: subpasta subtitles/ (pt-BR e outros idiomas sincronizados)", fontSize = 11.sp)
            Text("• Análise Acústica FFT: fft.json (64 bandas em tempo real)", fontSize = 11.sp)
            val audioExt = if (item.isMp3Conversion) "mp3" else "m4a"
            val videoExt = "m4v"
            Text("• Container de Áudio: audio.$audioExt", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("• Container de Vídeo: preview.$videoExt", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
          }
        }

        Spacer(modifier = Modifier.height(28.dp))
      }
    }
  }
}
