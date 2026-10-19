package br.com.smarttarget.karaoke.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.engine.KaraokeLine
import br.com.smarttarget.karaoke.studio.engine.KaraokeSynchronizationManager
import br.com.smarttarget.karaoke.studio.engine.KaraokeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Componente Compose de Letras Sincronizadas estilo Karaokê Profissional com
 * preenchimento dinâmico de cor palavra por palavra em tempo real.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SynchronizedKaraokeLyricsView(
  currentPositionMs: Long,
  subtitlesDirPath: String?,
  songTitle: String,
  artist: String,
  modifier: Modifier = Modifier,
  syncManager: KaraokeSynchronizationManager = remember { KaraokeSynchronizationManager() }
) {
  var selectedLanguage by remember { mutableStateOf("pt-BR") }
  val syncState by syncManager.syncState.collectAsState()

  // Carrega o arquivo da legenda do idioma selecionado
  LaunchedEffect(subtitlesDirPath, selectedLanguage) {
    withContext(Dispatchers.IO) {
      var content: String? = null
      if (!subtitlesDirPath.isNullOrEmpty()) {
        val srtFile = File(subtitlesDirPath, "$selectedLanguage.srt")
        if (srtFile.exists()) {
          content = srtFile.readText()
        } else {
          // Tenta arquivo direto
          val direct = File(subtitlesDirPath)
          if (direct.exists()) content = direct.readText()
        }
      }

      if (content.isNullOrEmpty()) {
        content = """
1
00:00:01,000 --> 00:00:05,000
♪ $songTitle ♪

2
00:00:05,500 --> 00:00:10,000
Artista: $artist [Karaoke Studio]

3
00:00:11,000 --> 00:00:16,000
♪ Quando a melodia toca em meu peito ♪

4
00:00:16,500 --> 00:00:22,000
♪ A voz ganha vida e o som se espalha ♪

5
00:00:22,500 --> 00:00:28,000
♪ Cante com a alma e sinta a harmonia ♪

6
00:00:28,500 --> 00:00:35,000
♪ Solte o seu canto e brilhe no palco ♪
        """.trimIndent()
      }

      syncManager.loadSubtitles(content)
    }
  }

  // Atualiza posição do sincronizador continuamente
  LaunchedEffect(currentPositionMs) {
    syncManager.updatePosition(currentPositionMs)
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 3.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("karaoke_lyrics_view")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      // 1. Cabeçalho com SVG Icon e Seletor de Idiomas
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_lyrics),
            contentDescription = "Letras Karaokê",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Letra Sincronizada em Tempo Real",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        // Seletor de Idiomas
        Row(
          horizontalArrangement = Arrangement.spacedBy(4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          LanguageChip(
            code = "pt-BR",
            label = "PT",
            isSelected = selectedLanguage == "pt-BR",
            onClick = { selectedLanguage = "pt-BR" }
          )
          LanguageChip(
            code = "en-US",
            label = "EN",
            isSelected = selectedLanguage == "en-US",
            onClick = { selectedLanguage = "en-US" }
          )
          LanguageChip(
            code = "es-ES",
            label = "ES",
            isSelected = selectedLanguage == "es-ES",
            onClick = { selectedLanguage = "es-ES" }
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Caixa de Letra Ativa estilo Teleprompter Karaokê
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0D0F18))
          .border(1.dp, Color(0xFF222638), RoundedCornerShape(12.dp))
          .padding(16.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier.fillMaxWidth()
        ) {
          if (syncState.activeLine != null) {
            val activeLine = syncState.activeLine!!

            // Barra de progresso da frase atual
            LinearProgressIndicator(
              progress = { syncState.lineProgressRatio },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
              color = Color(0xFFFFD600),
              trackColor = Color(0xFF222638)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Frase ativa com destaque palavra a palavra
            FlowRow(
              horizontalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth()
            ) {
              activeLine.words.forEachIndexed { idx, word ->
                val isSung = currentPositionMs >= word.endMs
                val isCurrent = currentPositionMs in word.startMs..word.endMs

                val wordColor = when {
                  isCurrent -> Color(0xFFFFD600) // Dourado brilhante em execução
                  isSung -> Color(0xFFFFF176)    // Amarelo suave já cantado
                  else -> Color(0xFFB0BEC5)      // Prata/Cinza aguardando
                }

                Text(
                  text = "${word.text} ",
                  fontSize = 17.sp,
                  fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                  color = wordColor,
                  modifier = Modifier
                    .padding(vertical = 2.dp)
                )
              }
            }
          } else {
            // Estado entre frases (contagem regressiva para a próxima estrofe)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              Text(
                text = "♪ Instrumental ♪",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
              )
              if (syncState.nextLine != null) {
                val secRemaining = (syncState.msUntilNextLine / 1000f).coerceAtLeast(0f)
                Text(
                  text = "Próxima frase em ${String.format("%.1fs", secRemaining)}",
                  fontSize = 11.sp,
                  fontFamily = FontFamily.Monospace,
                  color = Color(0xFF90A4AE)
                )
              }
            }
          }

          // Prévia da Próxima Linha
          if (syncState.nextLine != null && syncState.activeLine != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
              text = "A seguir: ${syncState.nextLine?.fullText}",
              fontSize = 11.sp,
              color = Color(0xFF78909C),
              maxLines = 1,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

@Composable
private fun LanguageChip(
  code: String,
  label: String,
  isSelected: Boolean,
  onClick: () -> Unit
) {
  Surface(
    shape = RoundedCornerShape(6.dp),
    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
    modifier = Modifier
      .clickable(onClick = onClick)
      .border(
        width = 1.dp,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        shape = RoundedCornerShape(6.dp)
      )
  ) {
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
    )
  }
}
