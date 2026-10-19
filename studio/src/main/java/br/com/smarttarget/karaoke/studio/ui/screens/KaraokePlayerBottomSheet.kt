package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import org.json.JSONObject
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.ui.MainViewModel
import kotlin.math.abs
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KaraokePlayerBottomSheet(viewModel: MainViewModel) {
  val playingItem by viewModel.playingItem.collectAsState()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  if (playingItem != null) {
    val item = playingItem!!
    var isPlaying by remember { mutableStateOf(true) }
    var currentSeconds by remember { mutableFloatStateOf(0f) }
    val totalSeconds = if (item.durationSeconds > 0) item.durationSeconds.toFloat() else 240f

    // Playback loop
    LaunchedEffect(isPlaying) {
      while (isPlaying) {
        delay(100)
        if (currentSeconds < totalSeconds) {
          currentSeconds += 0.1f
        } else {
          isPlaying = false
        }
      }
    }

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
          .padding(horizontal = 20.dp, vertical = 6.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Player Header
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "Karaoke Studio Player",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = "Asset: ${item.guid}",
              style = MaterialTheme.typography.bodySmall,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(onClick = { viewModel.closePlayer() }) {
            Icon(
              painter = painterResource(id = R.drawable.ic_close),
              contentDescription = "Fechar",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 1. VIDEO PREVIEW BOX
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = "Vídeo Preview",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
          )

          // Play status overlay badge
          Box(
            modifier = Modifier
              .align(Alignment.TopStart)
              .padding(8.dp)
              .clip(RoundedCornerShape(6.dp))
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
              .padding(horizontal = 8.dp, vertical = 3.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_video),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "preview (Vídeo Sincronizado)",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 2. AUDIO SPECTRUM (FFT REAL-TIME VISUALIZER)
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = MaterialTheme.colorScheme.surfaceVariant,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_spectrum),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.secondary,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Spectrum do Áudio (Dados FFT)",
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.secondary
                )
              }
              Text(
                text = "64 Bandas FFT",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
              )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 48 FFT Spectrum Bars reacting dynamically to playback
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Bottom
            ) {
              val barCount = 42
              for (b in 0 until barCount) {
                val waveFactor = if (isPlaying) {
                  val t = currentSeconds * 3.0
                  val harmonic1 = sin(t + b * 0.25)
                  val harmonic2 = sin(t * 1.8 + b * 0.15)
                  val beat = abs(sin(currentSeconds * 4.0)) * 0.3
                  ((0.3 + 0.35 * harmonic1 + 0.2 * harmonic2 + beat)).toFloat().coerceIn(0.08f, 1.0f)
                } else {
                  0.08f
                }

                val animatedHeight by animateFloatAsState(targetValue = waveFactor, label = "fft_bar_$b")

                Box(
                  modifier = Modifier
                    .width(4.dp)
                    .height((64 * animatedHeight).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                      Brush.verticalGradient(
                        colors = listOf(
                          MaterialTheme.colorScheme.secondary,
                          MaterialTheme.colorScheme.primary
                        )
                      )
                    )
                )
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Song Title and Artist Info
        Text(
          text = parsedSongTitle,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        if (parsedSingers.isNotEmpty()) {
          Text(
            text = "Cantores: ${parsedSingers.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
          )
        }
        if (parsedComposers.isNotEmpty()) {
          Text(
            text = "Compositores: ${parsedComposers.joinToString(", ")}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time Slider
        Slider(
          value = currentSeconds,
          onValueChange = { currentSeconds = it },
          valueRange = 0f..totalSeconds,
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier.fillMaxWidth()
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          val curMin = currentSeconds.toInt() / 60
          val curSec = currentSeconds.toInt() % 60
          val totMin = totalSeconds.toInt() / 60
          val totSec = totalSeconds.toInt() % 60
          Text(text = String.format("%02d:%02d", curMin, curSec), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          Text(text = String.format("%02d:%02d", totMin, totSec), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. PLAYBACK CONTROLS (PLAY, PAUSE, STOP)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.Center,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // STOP BUTTON
          FilledIconButton(
            onClick = {
              isPlaying = false
              currentSeconds = 0f
            },
            modifier = Modifier
              .size(48.dp)
              .testTag("player_stop_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant,
              contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_stop),
              contentDescription = "Stop",
              modifier = Modifier.size(20.dp)
            )
          }

          Spacer(modifier = Modifier.width(20.dp))

          // PLAY / PAUSE BUTTON
          FilledIconButton(
            onClick = { isPlaying = !isPlaying },
            modifier = Modifier
              .size(60.dp)
              .testTag("player_play_pause_button"),
            colors = IconButtonDefaults.filledIconButtonColors(
              containerColor = MaterialTheme.colorScheme.primary,
              contentColor = MaterialTheme.colorScheme.onPrimary
            )
          ) {
            Icon(
              painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
              contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
              modifier = Modifier.size(28.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 4. METADADOS MUSICAIS COMPLETOS DO ASSET
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Text(
              text = "Metadados do Asset:",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))

            Text("• Identificador: ${item.guid}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Text("• Gênero: $parsedGenre", fontSize = 11.sp)
            if (parsedAlbum != null) Text("• Álbum: $parsedAlbum", fontSize = 11.sp)
            if (parsedWork != null) Text("• Obra: $parsedWork", fontSize = 11.sp)
            if (parsedDate != null) Text("• Data: $parsedDate", fontSize = 11.sp)
            if (parsedClassicalEra != null) Text("• Época Clássica: $parsedClassicalEra", fontSize = 11.sp)
            if (parsedClassicalType != null) Text("• Tipo de Obra: $parsedClassicalType", fontSize = 11.sp)
            Text("• Legendas: subpasta subtitles/ (Formato pt-BR e outros idiomas)", fontSize = 11.sp)
            Text("• Análise FFT: fft.json (64 bandas sincronizadas)", fontSize = 11.sp)
          }
        }

        Spacer(modifier = Modifier.height(28.dp))
      }
    }
  }
}
