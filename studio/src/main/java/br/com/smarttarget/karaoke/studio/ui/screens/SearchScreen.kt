package br.com.smarttarget.karaoke.studio.ui.screens

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@Composable
fun SearchScreen(viewModel: MainViewModel) {
  var urlInput by remember { mutableStateOf("") }
  var validationError by remember { mutableStateOf<String?>(null) }
  val isAnalyzing by viewModel.isAnalyzing.collectAsState()
  val analysisError by viewModel.analysisError.collectAsState()
  val baseAssetsFolder by viewModel.baseAssetsFolder.collectAsState()

  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current

  // Photo / Video Picker for selecting a local video from the device
  val videoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      var displayName = "video_karaoke_${System.currentTimeMillis()}.mp4"
      try {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
          if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
              displayName = cursor.getString(nameIndex) ?: displayName
            }
          }
        }
      } catch (_: Exception) {}
      viewModel.processLocalVideo(displayName, uri.toString())
    }
  }

  fun validateUrl(url: String): Boolean {
    val clean = url.trim()
    if (clean.isEmpty()) {
      validationError = "Por favor, informe a URL do vídeo do YouTube."
      return false
    }

    // Playlists are strictly forbidden
    if (clean.contains("list=") || clean.contains("/playlist") || clean.contains("playlist?")) {
      validationError = "Playlists não são permitidas. Por favor, informe a URL de um único vídeo."
      return false
    }

    val hasValidDomain = clean.contains("youtube.com") || clean.contains("youtu.be")
    val hasId = clean.contains("v=") || clean.contains("youtu.be/") || clean.contains("/shorts/")
    if (!hasValidDomain || !hasId) {
      validationError = "URL inválida. O link deve ser de um vídeo do YouTube (ex: https://www.youtube.com/watch?v=... ou https://youtu.be/...)"
      return false
    }
    validationError = null
    return true
  }

  val sampleUrls = listOf(
    Pair("Evidências (Chitãozinho & Xororó)", "https://www.youtube.com/watch?v=ePjtnSPFWK8"),
    Pair("Boate Azul (PlayBack Karaokê)", "https://www.youtube.com/watch?v=sample2_boate_azul"),
    Pair("Vivaldi - As Quatro Estações", "https://www.youtube.com/watch?v=sample_vivaldi_4_seasons"),
    Pair("Beethoven - Sinfonia Nº 5", "https://www.youtube.com/watch?v=sample_beethoven_sym5"),
    Pair("Bohemian Rhapsody (Queen)", "https://www.youtube.com/watch?v=fJ9rUzIMcZQ")
  )

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp)
      .verticalScroll(rememberScrollState())
  ) {
    // Header
    Text(
      text = "Processar Vídeo / Criar Asset",
      style = MaterialTheme.typography.titleLarge,
      fontWeight = FontWeight.Bold
    )
    Text(
      text = "Insira uma URL do YouTube ou selecione um vídeo do dispositivo. Playlists não são permitidas.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Main URL Input Card
    ElevatedCard(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("search_card")
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_link),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "URL do YouTube (Vídeo Único)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }

          // Paste from clipboard button
          OutlinedButton(
            onClick = {
              val clip = clipboardManager.getText()?.text
              if (!clip.isNullOrEmpty()) {
                urlInput = clip.trim()
                validateUrl(urlInput)
              }
            },
            shape = RoundedCornerShape(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp)
          ) {
            Text("Colar", fontSize = 11.sp)
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Text Input for URL
        OutlinedTextField(
          value = urlInput,
          onValueChange = {
            urlInput = it
            if (validationError != null) {
              validateUrl(it)
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("youtube_url_input"),
          placeholder = { Text("https://www.youtube.com/watch?v=...") },
          singleLine = true,
          isError = validationError != null,
          shape = RoundedCornerShape(12.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
          ),
          trailingIcon = {
            if (urlInput.isNotEmpty()) {
              IconButton(onClick = {
                urlInput = ""
                validationError = null
              }) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_close),
                  contentDescription = "Limpar",
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        )

        // Validation Error Message
        if (validationError != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = validationError ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
          )
        }

        // Analysis Error Message
        if (analysisError != null) {
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = analysisError ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontSize = 12.sp
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 'Fetch' Button
        Button(
          onClick = {
            if (validateUrl(urlInput)) {
              viewModel.analyzeUrl(urlInput.trim())
            }
          },
          enabled = !isAnalyzing,
          modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("fetch_button"),
          shape = RoundedCornerShape(12.dp),
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
          if (isAnalyzing) {
            CircularProgressIndicator(
              color = MaterialTheme.colorScheme.onPrimary,
              modifier = Modifier.size(20.dp),
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Extraindo dados com yt-dlp...")
          } else {
            Icon(
              painter = painterResource(id = R.drawable.ic_download),
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Fetch / Processar Vídeo",
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Option 2: Select Local Video Button
        OutlinedButton(
          onClick = {
            videoPickerLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
            )
          },
          modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .testTag("select_local_video_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_video),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Selecionar Vídeo Local do Dispositivo",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Quick Sample YouTube Links
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_auto_awesome),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Vídeos Sugeridos para Testar:",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
          )
        }

        Spacer(modifier = Modifier.height(8.dp))

        sampleUrls.forEach { (label, url) ->
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp)
              .clickable {
                urlInput = url
                validateUrl(url)
              }
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_youtube),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(text = label, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                Text(text = url, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1)
              }
              Icon(
                painter = painterResource(id = R.drawable.ic_arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // Information Card regarding Asset Processing Flow
    Surface(
      shape = RoundedCornerShape(14.dp),
      color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Text(
          text = "Fluxo de Criação do Asset (Karaoke Studio):",
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text("1. Validação da URL (apenas vídeos individuais, playlists rejeitadas)", fontSize = 11.sp)
        Text("2. Geração do identificador GUID único em minúsculas (sem hifens)", fontSize = 11.sp)
        Text("3. Criação da pasta do asset em Documentos/$baseAssetsFolder/{guid}/", fontSize = 11.sp)
        Text("4. Extração do áudio (m4a padrão) salvo com o nome 'audio'", fontSize = 11.sp)
        Text("5. Extração do vídeo preview (m4v padrão) salvo com o nome 'preview'", fontSize = 11.sp)
        Text("6. Análise matemática FFT do áudio armazenada como 'fft.json'", fontSize = 11.sp)
        Text("7. Download de todas as legendas em formato 'pt-BR' na subpasta subtitles/", fontSize = 11.sp)
        Text("8. Inferência da música via análise do áudio e metadados", fontSize = 11.sp)
      }
    }

    Spacer(modifier = Modifier.height(24.dp))
  }
}
