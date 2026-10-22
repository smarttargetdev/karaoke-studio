package br.com.smarttarget.karaoke.studio.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.ui.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeBrowserScreen(viewModel: MainViewModel) {
  val detectedUrl by viewModel.detectedUrl.collectAsState()
  val isAnalyzing by viewModel.isAnalyzing.collectAsState()
  val analysisError by viewModel.analysisError.collectAsState()
  val showUrlDialog by viewModel.showUrlInputDialog.collectAsState()
  val urlValidationError by viewModel.urlValidationError.collectAsState()

  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var inputUrl by remember { mutableStateOf("https://m.youtube.com") }
  var manualUrlInput by remember { mutableStateOf("") }
  var webProgress by remember { mutableIntStateOf(100) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }

  BackHandler(enabled = canGoBack) {
    webViewInstance?.goBack()
  }

  val quickSearches = listOf(
    "Karaokê Sertanejo",
    "Karaokê Rock",
    "Karaokê Anos 80",
    "Playback Gospel",
    "Karaokê Pop Hits",
    "Clássica",
    "Karaokê MPB"
  )

  Column(modifier = Modifier.fillMaxSize()) {
    // Top Bar with Nav Controls & URL input
    Surface(
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 2.dp,
      modifier = Modifier.fillMaxWidth()
    ) {
      Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { webViewInstance?.goBack() },
            enabled = canGoBack,
            modifier = Modifier.testTag("nav_back_button")
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_arrow_back),
              contentDescription = "Voltar",
              tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
          }

          IconButton(
            onClick = { webViewInstance?.goForward() },
            enabled = canGoForward,
            modifier = Modifier.testTag("nav_forward_button")
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_arrow_forward),
              contentDescription = "Avançar",
              tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
          }

          IconButton(
            onClick = { webViewInstance?.reload() },
            modifier = Modifier.testTag("nav_refresh_button")
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_refresh),
              contentDescription = "Recarregar",
              tint = MaterialTheme.colorScheme.primary
            )
          }

          // Search / URL input field
          OutlinedTextField(
            value = inputUrl,
            onValueChange = { inputUrl = it },
            modifier = Modifier
              .weight(1f)
              .height(48.dp)
              .testTag("browser_url_input"),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            colors = OutlinedTextFieldDefaults.colors(
              focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
              unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(24.dp),
            placeholder = { Text("Pesquisar ou colar URL", style = MaterialTheme.typography.bodySmall) },
            trailingIcon = {
              IconButton(onClick = {
                val clean = inputUrl.trim()
                if (clean.startsWith("http://") || clean.startsWith("https://")) {
                  webViewInstance?.loadUrl(clean)
                } else {
                  webViewInstance?.loadUrl("https://m.youtube.com/results?search_query=${clean.replace(" ", "+")}")
                }
              }) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_search),
                  contentDescription = "Pesquisar",
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(20.dp)
                )
              }
            }
          )

          Spacer(modifier = Modifier.width(4.dp))

          // Direct Paste & Validate URL Button
          IconButton(
            onClick = {
              manualUrlInput = if (inputUrl.startsWith("http")) inputUrl else ""
              viewModel.showUrlInput()
            },
            modifier = Modifier.testTag("open_url_dialog_button")
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_link),
              contentDescription = "Inserir URL",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(22.dp)
            )
          }

          // Top Bar Direct Download Button (Ativo quando um vídeo for definido)
          val targetVideoUrl = detectedUrl ?: inputUrl.takeIf {
            it.contains("watch?v=") || it.contains("youtu.be/") || it.contains("/shorts/")
          }
          if (targetVideoUrl != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Button(
              onClick = { viewModel.analyzeUrl(targetVideoUrl) },
              enabled = !isAnalyzing,
              shape = RoundedCornerShape(20.dp),
              colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
              contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
              modifier = Modifier.height(36.dp).testTag("top_bar_download_button")
            ) {
              Icon(
                painter = painterResource(id = R.drawable.ic_download),
                contentDescription = "Baixar",
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(4.dp))
              Text("Baixar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
          }
        }

        // Quick Search Chips
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 4.dp, bottom = 2.dp),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          quickSearches.forEach { query ->
            FilterChip(
              selected = false,
              onClick = {
                inputUrl = query
                webViewInstance?.loadUrl("https://m.youtube.com/results?search_query=${query.replace(" ", "+")}")
              },
              label = { Text(query, fontSize = 11.sp, fontWeight = FontWeight.Medium) },
              leadingIcon = {
                Icon(
                  painter = painterResource(id = R.drawable.ic_mic),
                  contentDescription = null,
                  modifier = Modifier.size(14.dp),
                  tint = MaterialTheme.colorScheme.primary
                )
              }
            )
          }
        }

        if (webProgress in 1..99) {
          LinearProgressIndicator(
            progress = { webProgress / 100f },
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp),
            color = MaterialTheme.colorScheme.primary
          )
        }
      }
    }

    // Main WebView Container
    Box(modifier = Modifier.fillMaxSize()) {
      AndroidView(
        factory = { ctx ->
          WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

            webViewClient = object : WebViewClient() {
              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                url?.let {
                  inputUrl = it
                  viewModel.onBrowserUrlChanged(it)
                }
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                url?.let {
                  inputUrl = it
                  viewModel.onBrowserUrlChanged(it)
                }
                canGoBack = view?.canGoBack() == true
                canGoForward = view?.canGoForward() == true
              }

              override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                inputUrl = url
                viewModel.onBrowserUrlChanged(url)
                return false
              }
            }

            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                webProgress = newProgress
              }
            }

            loadUrl("https://m.youtube.com")
            webViewInstance = this
          }
        },
        modifier = Modifier.fillMaxSize()
      )

      // Floating Banner when Video is detected
      Column(
        modifier = Modifier
          .align(Alignment.BottomCenter)
          .padding(16.dp)
      ) {
        androidx.compose.animation.AnimatedVisibility(
          visible = detectedUrl != null,
          enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
          exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
          modifier = Modifier.fillMaxWidth()
        ) {
          ElevatedCard(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.elevatedCardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("video_detected_card")
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
              ) {
                Icon(
                  painter = painterResource(id = R.drawable.ic_youtube),
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.primary,
                  modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Vídeo do YouTube Detectado!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = detectedUrl ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              Button(
                onClick = { detectedUrl?.let { viewModel.analyzeUrl(it) } },
                enabled = !isAnalyzing,
                modifier = Modifier
                  .fillMaxWidth()
                  .height(48.dp)
                  .testTag("analyze_ytdlp_button"),
                colors = ButtonDefaults.buttonColors(
                  containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
              ) {
                if (isAnalyzing) {
                  CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text("Processando yt-dlp & Gerando GUID...")
                } else {
                  Icon(
                    painter = painterResource(id = R.drawable.ic_download),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Baixar",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                  )
                }
              }

              if (analysisError != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = analysisError ?: "",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.error
                )
              }
            }
          }
        }
      }
    }

    // Modal Dialog to enter and validate YouTube URL directly
    if (showUrlDialog) {
      AlertDialog(
        onDismissRequest = { viewModel.dismissUrlInput() },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_link),
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Inserir URL do YouTube", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column {
            Text(
              text = "Cole o link completo de um vídeo individual do YouTube para validação e extração (playlists não são permitidas):",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = manualUrlInput,
              onValueChange = { manualUrlInput = it },
              modifier = Modifier
                .fillMaxWidth()
                .testTag("manual_url_input_field"),
              placeholder = { Text("https://www.youtube.com/watch?v=...") },
              singleLine = true,
              isError = urlValidationError != null,
              shape = RoundedCornerShape(12.dp)
            )
            if (urlValidationError != null) {
              Spacer(modifier = Modifier.height(6.dp))
              Text(
                text = urlValidationError ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = { viewModel.validateAndSubmitUrl(manualUrlInput) },
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Baixar")
          }
        },
        dismissButton = {
          OutlinedButton(
            onClick = { viewModel.dismissUrlInput() },
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Cancelar")
          }
        }
      )
    }
  }
}
