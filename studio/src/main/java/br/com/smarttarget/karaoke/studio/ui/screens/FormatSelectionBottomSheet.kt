package br.com.smarttarget.karaoke.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.data.model.PersonRole
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatSelectionBottomSheet(viewModel: MainViewModel) {
  val showSheet by viewModel.showFormatSheet.collectAsState()
  val info by viewModel.currentVideoInfo.collectAsState()
  val guid by viewModel.currentGuid.collectAsState()
  val songTitle by viewModel.songTitle.collectAsState()
  val genre by viewModel.genre.collectAsState()
  val persons by viewModel.persons.collectAsState()
  val album by viewModel.album.collectAsState()
  val workTitle by viewModel.workTitle.collectAsState()
  val recordingDate by viewModel.recordingDate.collectAsState()
  val compositionDate by viewModel.compositionDate.collectAsState()
  val classicalEra by viewModel.classicalEra.collectAsState()
  val classicalWorkType by viewModel.classicalWorkType.collectAsState()
  val audioRecognition by viewModel.audioRecognition.collectAsState()

  val baseAssetsFolder by viewModel.baseAssetsFolder.collectAsState()
  val videoRes by viewModel.videoResolution.collectAsState()
  val videoFps by viewModel.videoFps.collectAsState()
  val videoCodec by viewModel.videoCodec.collectAsState()
  val videoContainer by viewModel.videoContainer.collectAsState()
  val audioBitrate by viewModel.audioBitrate.collectAsState()
  val audioCodec by viewModel.audioCodec.collectAsState()
  val audioContainer by viewModel.audioContainer.collectAsState()

  var newPersonName by remember { mutableStateOf("") }
  var newPersonRole by remember { mutableStateOf(PersonRole.CANTOR) }
  var showTechSpecs by remember { mutableStateOf(false) }

  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

  val popularGenres = listOf("Sertanejo", "Rock", "Pop", "MPB", "Música Clássica", "Samba", "Gospel", "Forró")
  val classicalEras = listOf("Barroca", "Clássica", "Romântica", "Moderna", "Contemporânea")
  val classicalTypes = listOf("Ópera", "Cantata", "Ballet", "Recital", "Concerto", "Sonata", "Sinfonia", "Prelúdio")

  val hasSingers = persons.any { it.role == PersonRole.CANTOR }
  val hasComposers = persons.any { it.role == PersonRole.COMPOSITOR }
  val isClassical = genre.equals("Música Clássica", ignoreCase = true)

  if (showSheet && info != null) {
    val currentInfo = info!!

    ModalBottomSheet(
      onDismissRequest = { viewModel.dismissFormatSheet() },
      sheetState = sheetState,
      containerColor = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
          .verticalScroll(rememberScrollState())
      ) {
        // Video Header Info
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(width = 100.dp, height = 62.dp)
              .clip(RoundedCornerShape(10.dp))
              .background(MaterialTheme.colorScheme.surfaceVariant)
          ) {
            AsyncImage(
              model = currentInfo.thumbnailUrl,
              contentDescription = "Thumbnail",
              contentScale = ContentScale.Crop,
              modifier = Modifier.matchParentSize()
            )
            Box(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(3.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              val min = currentInfo.durationSeconds / 60
              val sec = currentInfo.durationSeconds % 60
              Text(
                text = String.format("%02d:%02d", min, sec),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
          }

          Spacer(modifier = Modifier.width(12.dp))

          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = currentInfo.title,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = currentInfo.channelTitle,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.primary
            )
          }

          // Auto Infer Button (Audio & Metadata)
          IconButton(
            onClick = {
              viewModel.inferFromAudioAndMetadata(guid, currentInfo.title, currentInfo.channelTitle, currentInfo.description)
            },
            modifier = Modifier.testTag("infer_metadata_button")
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_auto_awesome),
              contentDescription = "Reanalisar Áudio e Metadados",
              tint = MaterialTheme.colorScheme.secondary,
              modifier = Modifier.size(24.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // AUDIO RECOGNITION & ACOUSTIC INFERENCE BADGE
        if (audioRecognition != null) {
          val recog = audioRecognition!!
          Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 12.dp)
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
                    text = "Música Identificada via Análise de Áudio",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.secondary
                  )
                }
                Surface(
                  shape = RoundedCornerShape(6.dp),
                  color = MaterialTheme.colorScheme.primary
                ) {
                  Text(
                    text = "${recog.confidencePercent}% Confiança",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  )
                }
              }

              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = "Fonte: ${recog.source} • Tom: ${recog.musicalKey} • BPM Estimado: ${recog.estimatedBpm}",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSecondaryContainer
              )
            }
          }
        }

        // REQUIRED FIELD 1: Identificador (GUID Somente Leitura)
        Text(
          text = "Identificador (GUID Único do Asset - Pasta):",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = guid,
          onValueChange = {},
          readOnly = true,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("guid_readonly_input"),
          singleLine = true,
          textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
          ),
          shape = RoundedCornerShape(12.dp),
          leadingIcon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_folder),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
        )
        Text(
          text = "Pasta no dispositivo: Downloads/$baseAssetsFolder/$guid/",
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
          modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // REQUIRED FIELD 2: Nome da Música
        Text(
          text = "Nome da Música:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
          value = songTitle,
          onValueChange = { viewModel.setSongTitle(it) },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("song_title_input"),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          placeholder = { Text("Informe o nome da música") },
          leadingIcon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_music_note),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // REQUIRED FIELD 3: Gênero Musical
        Text(
          text = "Gênero Musical:",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          popularGenres.forEach { g ->
            FilterChip(
              selected = genre == g,
              onClick = { viewModel.setGenre(g) },
              label = { Text(g, fontSize = 11.sp, fontWeight = if (genre == g) FontWeight.Bold else FontWeight.Normal) }
            )
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // REQUIRED FIELD 4: Cantores e Compositores
        Text(
          text = "Artistas (Cantores e Compositores):",
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        // List of added persons
        if (persons.isNotEmpty()) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .horizontalScroll(rememberScrollState())
              .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            persons.forEach { person ->
              Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (person.role == PersonRole.CANTOR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                  Icon(
                    painter = painterResource(id = if (person.role == PersonRole.CANTOR) R.drawable.ic_mic else R.drawable.ic_person),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (person.role == PersonRole.CANTOR) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "${person.name} (${if (person.role == PersonRole.CANTOR) "Cantor" else "Compositor"})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (person.role == PersonRole.CANTOR) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  IconButton(
                    onClick = { viewModel.removePerson(person) },
                    modifier = Modifier.size(16.dp)
                  ) {
                    Icon(
                      painter = painterResource(id = R.drawable.ic_close),
                      contentDescription = "Remover",
                      modifier = Modifier.size(10.dp)
                    )
                  }
                }
              }
            }
          }
        }

        // Add Person Row
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedTextField(
            value = newPersonName,
            onValueChange = { newPersonName = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Nome do artista", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp)
          )

          Spacer(modifier = Modifier.width(6.dp))

          FilterChip(
            selected = newPersonRole == PersonRole.CANTOR,
            onClick = { newPersonRole = PersonRole.CANTOR },
            label = { Text("Cantor", fontSize = 10.sp) }
          )

          Spacer(modifier = Modifier.width(4.dp))

          FilterChip(
            selected = newPersonRole == PersonRole.COMPOSITOR,
            onClick = { newPersonRole = PersonRole.COMPOSITOR },
            label = { Text("Compositor", fontSize = 10.sp) }
          )

          Spacer(modifier = Modifier.width(6.dp))

          Button(
            onClick = {
              if (newPersonName.isNotBlank()) {
                viewModel.addPerson(newPersonName, newPersonRole)
                newPersonName = ""
              }
            },
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold)
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // CONDITIONAL FIELD: Álbum (se houverem cantores)
        if (hasSingers) {
          Text(
            text = "Álbum (Cantores):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = album,
            onValueChange = { viewModel.setAlbum(it) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("album_input"),
            placeholder = { Text("Ex: Cowboy do Asfalto") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
              Icon(
                painter = painterResource(id = R.drawable.ic_album),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        // CONDITIONAL FIELD: Obra (se houverem compositores)
        if (hasComposers) {
          Text(
            text = "Obra Musical (Compositores):",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = workTitle,
            onValueChange = { viewModel.setWorkTitle(it) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("work_title_input"),
            placeholder = { Text("Ex: Sinfonia Nº 5 em Dó Menor, Op. 67") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
              Icon(
                painter = painterResource(id = R.drawable.ic_music_note),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
              )
            }
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        // CONDITIONAL FIELD: Data de Gravação (se só houverem cantores) vs Data de Composição (se houver compositores)
        if (hasSingers && !hasComposers) {
          Text(
            text = "Data de Gravação:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = recordingDate,
            onValueChange = { viewModel.setRecordingDate(it) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("recording_date_input"),
            placeholder = { Text("Ex: 1990 ou 15/05/1990") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
              Icon(
                painter = painterResource(id = R.drawable.ic_calendar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
            }
          )
          Spacer(modifier = Modifier.height(12.dp))
        } else if (hasComposers) {
          Text(
            text = "Data de Composição:",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
          Spacer(modifier = Modifier.height(4.dp))
          OutlinedTextField(
            value = compositionDate,
            onValueChange = { viewModel.setCompositionDate(it) },
            modifier = Modifier
              .fillMaxWidth()
              .testTag("composition_date_input"),
            placeholder = { Text("Ex: 1808 ou 1723") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            leadingIcon = {
              Icon(
                painter = painterResource(id = R.drawable.ic_calendar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(18.dp)
              )
            }
          )
          Spacer(modifier = Modifier.height(12.dp))
        }

        // CONDITIONAL SECTION: Música Clássica (Época e Tipo de Obra)
        if (isClassical) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Características de Música Clássica:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )

              Spacer(modifier = Modifier.height(8.dp))

              // Época
              Text("Época:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                classicalEras.forEach { era ->
                  FilterChip(
                    selected = classicalEra == era,
                    onClick = { viewModel.setClassicalEra(era) },
                    label = { Text(era, fontSize = 11.sp) }
                  )
                }
              }

              Spacer(modifier = Modifier.height(8.dp))

              // Tipo de Obra
              Text("Tipo de Obra:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .horizontalScroll(rememberScrollState())
                  .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                classicalTypes.forEach { type ->
                  FilterChip(
                    selected = classicalWorkType == type,
                    onClick = { viewModel.setClassicalWorkType(type) },
                    label = { Text(type, fontSize = 11.sp) }
                  )
                }
              }
            }
          }
          Spacer(modifier = Modifier.height(12.dp))
        }

        // ASSET PROCESSING SPECS SUMMARY
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showTechSpecs = !showTechSpecs }
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_tune),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Ver Destino dos Arquivos e Configurações",
              style = MaterialTheme.typography.labelMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.primary
            )
          }
          Text(
            text = if (showTechSpecs) "▲ Ocultar" else "▼ Ver",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary
          )
        }

        AnimatedVisibility(visible = showTechSpecs) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 6.dp)
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text("• Áudio: audio.$audioContainer ($audioCodec, $audioBitrate)", fontSize = 11.sp)
              Text("• Vídeo: preview.$videoContainer ($videoCodec, $videoRes, $videoFps)", fontSize = 11.sp)
              Text("• FFT: fft.json (Análise matemática 64 bandas salva como fft.json)", fontSize = 11.sp)
              Text("• Legendas: subtitles/pt-BR.srt (Formato pt-BR e outros idiomas)", fontSize = 11.sp)
              Text("• Metadados: metadata.json (Informações completas do asset)", fontSize = 11.sp)
            }
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Primary CTA Button
        Button(
          onClick = { viewModel.confirmDownload() },
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("confirm_asset_download_button"),
          shape = RoundedCornerShape(16.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_download),
            contentDescription = null,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Extrair & Criar Asset (GUID)",
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp
          )
        }

        Spacer(modifier = Modifier.height(28.dp))
      }
    }
  }
}
