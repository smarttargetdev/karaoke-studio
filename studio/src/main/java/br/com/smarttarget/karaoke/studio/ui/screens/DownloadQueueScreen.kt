package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.data.model.DownloadItem
import br.com.smarttarget.karaoke.studio.data.model.DownloadStatus
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@Composable
fun DownloadQueueScreen(viewModel: MainViewModel) {
  val downloads by viewModel.allDownloads.collectAsState()
  val filter by viewModel.queueFilter.collectAsState()
  val activeCount by viewModel.activeDownloadsCount.collectAsState()

  val filteredDownloads = when (filter) {
    "ACTIVE" -> downloads.filter {
      it.status in listOf(
        DownloadStatus.PENDING,
        DownloadStatus.EXTRACTING,
        DownloadStatus.DOWNLOADING,
        DownloadStatus.CONVERTING_MP3,
        DownloadStatus.SAVING_METADATA
      )
    }
    "COMPLETED" -> downloads.filter { it.status == DownloadStatus.COMPLETED }
    "PAUSED" -> downloads.filter { it.status == DownloadStatus.PAUSED }
    else -> downloads
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 16.dp, vertical = 8.dp)
  ) {
    // Top Header & Stats Summary
    Surface(
      color = MaterialTheme.colorScheme.surfaceVariant,
      shape = RoundedCornerShape(16.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Fila de Downloads yt-dlp",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "${downloads.size} faixas registradas | $activeCount ativas",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.primaryContainer)
              .padding(horizontal = 10.dp, vertical = 6.dp)
          ) {
            Text(
              text = "$activeCount Baixando",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Filter Chips
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      FilterChip(
        selected = filter == "ALL",
        onClick = { viewModel.setQueueFilter("ALL") },
        label = { Text("Todos (${downloads.size})") }
      )
      FilterChip(
        selected = filter == "ACTIVE",
        onClick = { viewModel.setQueueFilter("ACTIVE") },
        label = { Text("Baixando ($activeCount)") },
        leadingIcon = {
          if (activeCount > 0) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
              initialValue = 0.3f,
              targetValue = 1f,
              animationSpec = infiniteRepeatable(
                animation = tween(600),
                repeatMode = RepeatMode.Reverse
              ),
              label = "alpha"
            )
            Box(
              modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
          }
        }
      )
      FilterChip(
        selected = filter == "COMPLETED",
        onClick = { viewModel.setQueueFilter("COMPLETED") },
        label = { Text("Concluídos (${downloads.count { it.status == DownloadStatus.COMPLETED }})") }
      )
      FilterChip(
        selected = filter == "PAUSED",
        onClick = { viewModel.setQueueFilter("PAUSED") },
        label = { Text("Pausados (${downloads.count { it.status == DownloadStatus.PAUSED }})") }
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // List of Downloads
    if (filteredDownloads.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_download_queue),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Nenhum download nesta lista",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "Navegue pelo YouTube e selecione músicas ou vídeos para baixar.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = { viewModel.setTab("browser") },
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_youtube),
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Ir para o YouTube")
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(filteredDownloads, key = { it.id }) { item ->
          DownloadItemCard(
            item = item,
            onPause = { viewModel.pauseDownload(item.id) },
            onResume = { viewModel.resumeDownload(item.id) },
            onCancel = { viewModel.cancelDownload(item.id) },
            onDelete = { viewModel.deleteDownload(item.id) },
            onPlay = { viewModel.openPlayer(item) }
          )
        }
      }
    }
  }
}

@Composable
fun DownloadItemCard(
  item: DownloadItem,
  onPause: () -> Unit,
  onResume: () -> Unit,
  onCancel: () -> Unit,
  onDelete: () -> Unit,
  onPlay: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("download_card_${item.id}"),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Thumbnail
        Box(
          modifier = Modifier
            .size(width = 80.dp, height = 50.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
          )
          // Badge: MP3 or Video
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .background(
                if (item.isMp3Conversion) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                RoundedCornerShape(topStart = 4.dp)
              )
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            Text(
              text = if (item.isMp3Conversion) "MP3" else "MP4",
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimary
            )
          }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = item.songTitle.ifEmpty { item.title },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = item.channelTitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
          )

          // Asset GUID & Folder indicator
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 2.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_folder),
              contentDescription = null,
              modifier = Modifier.size(12.dp),
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = item.guid.take(12) + "...",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
            Text(
              text = " • audio/preview/fft",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
          }
        }
      }

      // Progress bar if active or paused
      if (item.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.CONVERTING_MP3, DownloadStatus.SAVING_METADATA, DownloadStatus.PAUSED)) {
        Spacer(modifier = Modifier.height(10.dp))

        val progressPercent = (item.progress * 100).toInt()
        val downloadedMb = item.downloadedBytes / (1024f * 1024f)
        val totalMb = item.totalBytes / (1024f * 1024f)
        val speedMb = item.speedBytesPerSec / (1024f * 1024f)

        LinearProgressIndicator(
          progress = { item.progress },
          modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp)),
          color = when (item.status) {
            DownloadStatus.CONVERTING_MP3 -> MaterialTheme.colorScheme.secondary
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.outline
            else -> MaterialTheme.colorScheme.primary
          }
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = when (item.status) {
              DownloadStatus.CONVERTING_MP3 -> "Transcodificando para MP3 (ID3 tags)..."
              DownloadStatus.SAVING_METADATA -> "Gravando metadados .info.json..."
              DownloadStatus.PAUSED -> "Download pausado"
              else -> String.format("%.1f MB / %.1f MB (%.1f MB/s)", downloadedMb, totalMb, speedMb)
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "$progressPercent%",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
        }
      }

      // Bottom Action row
      Spacer(modifier = Modifier.height(8.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Status Badge
        Surface(
          color = when (item.status) {
            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primaryContainer
            DownloadStatus.ERROR -> MaterialTheme.colorScheme.errorContainer
            DownloadStatus.CONVERTING_MP3 -> MaterialTheme.colorScheme.secondaryContainer
            DownloadStatus.PAUSED -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.surfaceVariant
          },
          shape = RoundedCornerShape(6.dp)
        ) {
          Text(
            text = when (item.status) {
              DownloadStatus.COMPLETED -> "✓ Concluído"
              DownloadStatus.CONVERTING_MP3 -> "MP3 Conversão"
              DownloadStatus.SAVING_METADATA -> "Metadados"
              DownloadStatus.DOWNLOADING -> "Baixando"
              DownloadStatus.PAUSED -> "Pausado"
              DownloadStatus.ERROR -> "Erro"
              DownloadStatus.CANCELLED -> "Cancelado"
              else -> "Pendente"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            color = when (item.status) {
              DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.onPrimaryContainer
              DownloadStatus.ERROR -> MaterialTheme.colorScheme.onErrorContainer
              DownloadStatus.CONVERTING_MP3 -> MaterialTheme.colorScheme.onSecondaryContainer
              else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
          )
        }

        // Action Buttons
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (item.status == DownloadStatus.COMPLETED) {
            Button(
              onClick = onPlay,
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.height(34.dp)
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = null,
                modifier = Modifier.size(14.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Cantar / Tocar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Excluir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          } else if (item.status == DownloadStatus.DOWNLOADING) {
            IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_pause),
                contentDescription = "Pausar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Cancelar",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp)
              )
            }
          } else if (item.status == DownloadStatus.PAUSED) {
            IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_play),
                contentDescription = "Retomar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Excluir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          } else {
            IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
              Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Excluir",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
              )
            }
          }
        }
      }
    }
  }
}
