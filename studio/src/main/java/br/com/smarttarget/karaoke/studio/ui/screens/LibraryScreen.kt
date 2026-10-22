package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.data.local.KaraokeAssetEntity
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@Composable
fun LibraryScreen(viewModel: MainViewModel) {
  val assets by viewModel.libraryAssets.collectAsState()
  val totalCount by viewModel.libraryTotalCount.collectAsState()
  val searchQuery by viewModel.librarySearchQuery.collectAsState()
  val selectedGenre by viewModel.librarySelectedGenre.collectAsState()
  val onlyFavorites by viewModel.libraryOnlyFavorites.collectAsState()
  val sortOrder by viewModel.librarySortOrder.collectAsState()

  var selectedAssetForDetails by remember { mutableStateOf<KaraokeAssetEntity?>(null) }
  var assetToDelete by remember { mutableStateOf<KaraokeAssetEntity?>(null) }
  val clipboardManager = LocalClipboardManager.current

  val genres = listOf("ALL", "Sertanejo", "MPB", "Rock", "Clássica", "Pop", "Samba", "Gospel", "Forró")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
  ) {
    // Top Title & Asset Counter
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text(
          text = "Biblioteca",
          style = MaterialTheme.typography.titleLarge,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = "$totalCount ${if (totalCount == 1) "faixa processada" else "faixas processadas"} no dispositivo",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_library),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "${assets.size} exibidas",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Search Box
    OutlinedTextField(
      value = searchQuery,
      onValueChange = { viewModel.setLibrarySearchQuery(it) },
      modifier = Modifier
        .fillMaxWidth()
        .testTag("library_search_input"),
      placeholder = { Text("Pesquisar por música, cantor, compositor, gênero, GUID...") },
      singleLine = true,
      shape = RoundedCornerShape(14.dp),
      leadingIcon = {
        Icon(
          painter = painterResource(id = R.drawable.ic_search),
          contentDescription = null,
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { viewModel.setLibrarySearchQuery("") }) {
            Icon(
              painter = painterResource(id = R.drawable.ic_close),
              contentDescription = "Limpar busca",
              modifier = Modifier.size(16.dp)
            )
          }
        }
      },
      colors = OutlinedTextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
      )
    )

    Spacer(modifier = Modifier.height(8.dp))

    // Genre Filter Chips & Favorites Toggle
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Favorites filter chip
      FilterChip(
        selected = onlyFavorites,
        onClick = { viewModel.toggleLibraryFavoritesOnly() },
        leadingIcon = {
          Icon(
            painter = painterResource(id = if (onlyFavorites) R.drawable.ic_star else R.drawable.ic_star_border),
            contentDescription = null,
            tint = if (onlyFavorites) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
          )
        },
        label = { Text("Favoritas", fontSize = 11.sp) }
      )

      genres.forEach { g ->
        val label = if (g == "ALL") "Todos" else g
        FilterChip(
          selected = selectedGenre == g,
          onClick = { viewModel.setLibrarySelectedGenre(g) },
          label = { Text(label, fontSize = 11.sp) }
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Sort order chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "Ordenar: ",
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(modifier = Modifier.width(4.dp))
      val sortOptions = listOf(
        Pair("RECENT", "Recentes"),
        Pair("TITLE_ASC", "Nome (A-Z)"),
        Pair("DURATION", "Duração")
      )
      sortOptions.forEach { (key, label) ->
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = if (sortOrder == key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          modifier = Modifier
            .padding(horizontal = 2.dp)
            .clickable { viewModel.setLibrarySortOrder(key) }
        ) {
          Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = if (sortOrder == key) FontWeight.Bold else FontWeight.Normal,
            color = if (sortOrder == key) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Asset List or Empty State
    if (assets.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.padding(24.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_library),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = if (searchQuery.isNotEmpty() || selectedGenre != "ALL" || onlyFavorites) "Nenhuma música encontrada" else "Sua biblioteca está vazia",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = if (searchQuery.isNotEmpty()) "Tente buscar por outro termo ou limpar os filtros." else "Processe um link do YouTube ou selecione um vídeo para extrair áudio, vídeo e legendas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )
          Spacer(modifier = Modifier.height(16.dp))
          Button(
            onClick = { viewModel.setTab("search") },
            shape = RoundedCornerShape(12.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_download),
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Baixar")
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        items(items = assets, key = { it.guid }) { asset ->
          KaraokeAssetCard(
            asset = asset,
            onPlay = { viewModel.playLibraryAsset(asset) },
            onToggleFavorite = { viewModel.toggleAssetFavorite(asset.guid, !asset.isFavorite) },
            onShowDetails = { selectedAssetForDetails = asset },
            onDelete = { assetToDelete = asset }
          )
        }
      }
    }
  }

  // File Details Dialog
  selectedAssetForDetails?.let { asset ->
    AlertDialog(
      onDismissRequest = { selectedAssetForDetails = null },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(text = "Detalhes e Caminhos do Asset", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
        ) {
          Text(
            text = asset.songTitle,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = "GUID: ${asset.guid}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(12.dp))

          Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("Caminhos Locais no Dispositivo:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("📁 Pasta: ${asset.assetDirPath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(2.dp))
              Text("🎵 Áudio: ${asset.audioPath} (${asset.audioFormat.uppercase()}, ${asset.audioBitrate})", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(2.dp))
              Text("🎬 Vídeo Preview: ${asset.previewVideoPath} (${asset.videoFormat.uppercase()}, ${asset.videoResolution})", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(2.dp))
              Text("📊 FFT: ${asset.fftDataPath} (fft.json)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(2.dp))
              Text("📝 Legendas: ${asset.subtitlesDirPath} (pt-BR e outros)", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
              Spacer(modifier = Modifier.height(2.dp))
              Text("📋 Metadados: ${asset.metadataJsonPath}", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text("Metadados Musicais:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
          Text("• Gênero: ${asset.genre}", fontSize = 11.sp)
          if (asset.singers.isNotEmpty()) Text("• Cantores: ${asset.singers}", fontSize = 11.sp)
          if (asset.composers.isNotEmpty()) Text("• Compositores: ${asset.composers}", fontSize = 11.sp)
          if (asset.album != null) Text("• Álbum: ${asset.album}", fontSize = 11.sp)
          if (asset.workTitle != null) Text("• Obra: ${asset.workTitle}", fontSize = 11.sp)
          if (asset.recordingDate != null) Text("• Data Gravação: ${asset.recordingDate}", fontSize = 11.sp)
          if (asset.compositionDate != null) Text("• Data Composição: ${asset.compositionDate}", fontSize = 11.sp)
          if (asset.classicalEra != null) Text("• Época: ${asset.classicalEra}", fontSize = 11.sp)
          if (asset.classicalWorkType != null) Text("• Tipo de Obra: ${asset.classicalWorkType}", fontSize = 11.sp)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            clipboardManager.setText(AnnotatedString(asset.assetDirPath))
            selectedAssetForDetails = null
          },
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Copiar Caminho")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { selectedAssetForDetails = null },
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Fechar")
        }
      }
    )
  }

  // Delete Confirmation Dialog
  assetToDelete?.let { asset ->
    AlertDialog(
      onDismissRequest = { assetToDelete = null },
      title = { Text("Excluir Asset", fontWeight = FontWeight.Bold) },
      text = {
        Text("Tem certeza que deseja excluir '${asset.songTitle}' da biblioteca e remover os arquivos locais do dispositivo?")
      },
      confirmButton = {
        Button(
          onClick = {
            viewModel.deleteLibraryAsset(asset)
            assetToDelete = null
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Excluir")
        }
      },
      dismissButton = {
        OutlinedButton(
          onClick = { assetToDelete = null },
          shape = RoundedCornerShape(10.dp)
        ) {
          Text("Cancelar")
        }
      }
    )
  }
}

@Composable
fun KaraokeAssetCard(
  asset: KaraokeAssetEntity,
  onPlay: () -> Unit,
  onToggleFavorite: () -> Unit,
  onShowDetails: () -> Unit,
  onDelete: () -> Unit
) {
  ElevatedCard(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("asset_card_${asset.guid}")
  ) {
    Column(modifier = Modifier.padding(12.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Thumbnail with duration badge
        Box(
          modifier = Modifier
            .size(width = 96.dp, height = 64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
          AsyncImage(
            model = asset.thumbnailUrl,
            contentDescription = "Thumbnail",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
          )

          // Duration badge
          Box(
            modifier = Modifier
              .align(Alignment.BottomEnd)
              .padding(3.dp)
              .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
              .padding(horizontal = 4.dp, vertical = 1.dp)
          ) {
            val min = asset.durationSeconds / 60
            val sec = asset.durationSeconds % 60
            Text(
              text = String.format("%02d:%02d", min, sec),
              fontSize = 9.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Info Column
        Column(modifier = Modifier.weight(1f)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = asset.songTitle,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
              modifier = Modifier.weight(1f)
            )

            // Favorite star button
            IconButton(
              onClick = onToggleFavorite,
              modifier = Modifier.size(28.dp)
            ) {
              Icon(
                painter = painterResource(id = if (asset.isFavorite) R.drawable.ic_star else R.drawable.ic_star_border),
                contentDescription = "Favorito",
                tint = if (asset.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
              )
            }
          }

          // Artists info
          if (asset.singers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_mic),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = asset.singers,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          if (asset.composers.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                painter = painterResource(id = R.drawable.ic_person),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "Comp: ${asset.composers}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }

          // Format chips (audio m4a / video m4v / fft.json)
          Row(
            modifier = Modifier.padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            Surface(
              shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = asset.genre,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }

            Surface(
              shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = ".${asset.audioFormat} + .${asset.videoFormat}",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
              )
            }

            Surface(
              shape = RoundedCornerShape(4.dp),
              color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
              modifier = Modifier.clickable { onPlay() }
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
              ) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_spectrum),
                  contentDescription = "FFT Acústico Sincronizado",
                  tint = MaterialTheme.colorScheme.onPrimaryContainer,
                  modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                  text = "fft.json",
                  fontSize = 9.sp,
                  fontFamily = FontFamily.Monospace,
                  fontWeight = FontWeight.Bold,
                  color = MaterialTheme.colorScheme.onPrimaryContainer
                )
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Action row: Play button + Info details + Delete
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // GUID tag
        Text(
          text = "GUID: ${asset.guid.take(12)}...",
          fontSize = 10.sp,
          fontFamily = FontFamily.Monospace,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = onShowDetails,
            modifier = Modifier.size(34.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_info),
              contentDescription = "Ver arquivos locais",
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(18.dp)
            )
          }

          IconButton(
            onClick = onDelete,
            modifier = Modifier.size(34.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_close),
              contentDescription = "Excluir asset",
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
          }

          Spacer(modifier = Modifier.width(4.dp))

          Button(
            onClick = onPlay,
            shape = RoundedCornerShape(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.height(34.dp)
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_play),
              contentDescription = null,
              modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Tocar Karaokê", fontSize = 11.sp, fontWeight = FontWeight.Bold)
          }
        }
      }
    }
  }
}
