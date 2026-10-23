package br.com.smarttarget.karaoke.studio.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel) {
  val themeMode by viewModel.themeMode.collectAsState()
  val baseAssetsFolder by viewModel.baseAssetsFolder.collectAsState()
  val videoRes by viewModel.videoResolution.collectAsState()
  val videoFps by viewModel.videoFps.collectAsState()
  val videoCodec by viewModel.videoCodec.collectAsState()
  val videoContainer by viewModel.videoContainer.collectAsState()

  val audioBitrate by viewModel.audioBitrate.collectAsState()
  val audioQuality by viewModel.audioQuality.collectAsState()
  val audioCodec by viewModel.audioCodec.collectAsState()
  val audioContainer by viewModel.audioContainer.collectAsState()

  val backgroundDownload by viewModel.backgroundDownload.collectAsState()
  val consoleLogs by viewModel.consoleLogs.collectAsState()

  var assetsFolderInput by remember(baseAssetsFolder) { mutableStateOf(baseAssetsFolder) }
  var showTerminalLogs by remember { mutableStateOf(false) }

  // Directory Picker usando Storage Access Framework (SAF)
  val directoryPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocumentTree()
  ) { uri: Uri? ->
    uri?.let {
      val decoded = Uri.decode(it.toString())
      val selectedFolder = when {
        decoded.contains("primary:") -> decoded.substringAfter("primary:")
        decoded.contains("Documents/") -> "Documentos/" + decoded.substringAfter("Documents/")
        decoded.contains("Documentos/") -> "Documentos/" + decoded.substringAfter("Documentos/")
        it.path != null && it.path!!.contains(":") -> it.path!!.substringAfter(":")
        it.lastPathSegment != null -> it.lastPathSegment!!.replace("primary:", "")
        else -> "KaraokeStudio/assets"
      }.trimStart('/').ifEmpty { "KaraokeStudio/assets" }

      assetsFolderInput = selectedFolder
      viewModel.setBaseAssetsFolder(selectedFolder)
    }
  }

  val videoResolutions = listOf("1080p", "720p", "480p", "360p")
  val videoFpsOptions = listOf("60 fps", "30 fps", "24 fps")
  val videoCodecs = listOf("H.264 (AVC)", "H.265 (HEVC)", "VP9", "AV1")
  val videoContainers = listOf("m4v", "mp4", "webm", "mkv")

  val audioBitrates = listOf("320 kbps", "256 kbps", "192 kbps", "128 kbps")
  val audioQualities = listOf("Estúdio (High)", "Padrão", "Econômica")
  val audioCodecs = listOf("AAC", "MP3", "Opus", "FLAC")
  val audioContainers = listOf("m4a", "mp3", "ogg", "wav")

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState())
  ) {
    Text(
      text = "Configurações",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Configure a pasta de assets em Documentos, características de vídeo e áudio usadas no processamento.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // SECTION 1: PASTA DE ARMAZENAMENTO DOS ASSETS (DOCUMENTOS + DIRECTORY PICKER)
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("assets_storage_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_folder),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
              Text(
                text = "Pasta da Biblioteca",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "Armazenamento no diretório Documentos",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Botão Principal do Seletor de Pasta (Directory Picker)
        Button(
          onClick = { directoryPickerLauncher.launch(null) },
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("directory_picker_button")
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_folder),
            contentDescription = "Directory Picker",
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Selecionar Pasta (Directory Picker)",
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedTextField(
          value = assetsFolderInput,
          onValueChange = {
            assetsFolderInput = it
            viewModel.setBaseAssetsFolder(it)
          },
          modifier = Modifier.fillMaxWidth(),
          singleLine = true,
          shape = RoundedCornerShape(12.dp),
          placeholder = { Text("Ex: KaraokeStudio/assets") },
          leadingIcon = {
            Icon(
              painter = painterResource(id = R.drawable.ic_folder),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
          },
          trailingIcon = {
            IconButton(onClick = { directoryPickerLauncher.launch(null) }) {
              Icon(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = "Abrir Seletor",
                tint = MaterialTheme.colorScheme.primary
              )
            }
          }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Exibição do Caminho Completo em Documentos
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Text(
              text = "📁 Caminho no SD Interno (fora de Android/data):",
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "/storage/emulated/0/Documents/$assetsFolderInput/{guid}/",
              fontSize = 11.sp,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Os recursos (audio, preview, fft.json e subtitles/) são extraídos diretamente no SD interno compartilhado do dispositivo.",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
          text = "Atalhos de Pastas em Documentos:",
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp)
        ) {
          FilterChip(
            selected = assetsFolderInput == "KaraokeStudio/assets",
            onClick = {
              assetsFolderInput = "KaraokeStudio/assets"
              viewModel.setBaseAssetsFolder("KaraokeStudio/assets")
            },
            label = { Text("KaraokeStudio/assets (Padrão)") }
          )
          FilterChip(
            selected = assetsFolderInput == "Karaoke/Musicas",
            onClick = {
              assetsFolderInput = "Karaoke/Musicas"
              viewModel.setBaseAssetsFolder("Karaoke/Musicas")
            },
            label = { Text("Karaoke/Musicas") }
          )
          FilterChip(
            selected = assetsFolderInput == "Estudio/Projetos",
            onClick = {
              assetsFolderInput = "Estudio/Projetos"
              viewModel.setBaseAssetsFolder("Estudio/Projetos")
            },
            label = { Text("Estudio/Projetos") }
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // SECTION 2: CARACTERÍSTICAS PADRÃO DE VÍDEO
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("video_config_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_video),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Padrão de Vídeo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Text(
          text = "Configura a resolução, taxa de reprodução (FPS), codificação e container",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2.1 Resolução
        Text("Resolução Padrão:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          videoResolutions.forEach { res ->
            FilterChip(
              selected = videoRes == res,
              onClick = { viewModel.setVideoResolution(res) },
              label = { Text(res, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2.2 FPS
        Text("Taxa de Reprodução (FPS):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          videoFpsOptions.forEach { fps ->
            FilterChip(
              selected = videoFps == fps,
              onClick = { viewModel.setVideoFps(fps) },
              label = { Text(fps, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2.3 Formato de Codificação (Codec)
        Text("Formato de Codificação (Codec):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          videoCodecs.forEach { codec ->
            FilterChip(
              selected = videoCodec == codec,
              onClick = { viewModel.setVideoCodec(codec) },
              label = { Text(codec, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 2.4 Container de Vídeo
        Text("Formato do Container:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          videoContainers.forEach { cont ->
            FilterChip(
              selected = videoContainer == cont,
              onClick = { viewModel.setVideoContainer(cont) },
              label = { Text(".$cont", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // SECTION 3: CARACTERÍSTICAS PADRÃO DE ÁUDIO
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("audio_config_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Padrão de Áudio",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        Text(
          text = "Configura o bitrate, qualidade, formato de codificação e container",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 3.1 Bitrate
        Text("Bitrate de Áudio:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          audioBitrates.forEach { bit ->
            FilterChip(
              selected = audioBitrate == bit,
              onClick = { viewModel.setAudioBitrate(bit) },
              label = { Text(bit, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3.2 Qualidade
        Text("Qualidade do Áudio:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          audioQualities.forEach { qual ->
            FilterChip(
              selected = audioQuality == qual,
              onClick = { viewModel.setAudioQuality(qual) },
              label = { Text(qual, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3.3 Codificação / Codec
        Text("Formato de Codificação (Codec):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          audioCodecs.forEach { codec ->
            FilterChip(
              selected = audioCodec == codec,
              onClick = { viewModel.setAudioCodec(codec) },
              label = { Text(codec, fontSize = 11.sp) }
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3.4 Container de Áudio
        Text("Formato do Container:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          audioContainers.forEach { cont ->
            FilterChip(
              selected = audioContainer == cont,
              onClick = { viewModel.setAudioContainer(cont) },
              label = { Text(".$cont", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // SECTION 4: TEMA DO APLICATIVO
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("theme_settings_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = if (themeMode == "dark") R.drawable.ic_moon else R.drawable.ic_sun),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Tema do Aplicativo",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.setThemeMode("dark") }
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = themeMode == "dark", onClick = { viewModel.setThemeMode("dark") })
          Spacer(modifier = Modifier.width(8.dp))
          Text("Modo Escuro (Studio Dark)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.setThemeMode("light") }
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = themeMode == "light", onClick = { viewModel.setThemeMode("light") })
          Spacer(modifier = Modifier.width(8.dp))
          Text("Modo Claro (Clean Light)", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }

        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { viewModel.setThemeMode("system") }
            .padding(vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          RadioButton(selected = themeMode == "system", onClick = { viewModel.setThemeMode("system") })
          Spacer(modifier = Modifier.width(8.dp))
          Text("Seguir Sistema", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // SECTION 5: DOWNLOAD EM SEGUNDO PLANO
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Download em Segundo Plano",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Continua baixando com tela desligada e emite notificação ao concluir",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Switch(
          checked = backgroundDownload,
          onCheckedChange = { viewModel.setBackgroundDownload(it) }
        )
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // SECTION 6: CONSOLE YT-DLP
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clickable { showTerminalLogs = !showTerminalLogs },
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_terminal),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Console e Log",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Text(
            text = if (showTerminalLogs) "▲ Fechar" else "▼ Ver",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
          )
        }

        AnimatedVisibility(visible = showTerminalLogs) {
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .padding(top = 10.dp)
          ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
              items(consoleLogs) { logLine ->
                Text(
                  text = logLine,
                  fontFamily = FontFamily.Monospace,
                  fontSize = 10.sp,
                  color = if (logLine.contains("[error]")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
