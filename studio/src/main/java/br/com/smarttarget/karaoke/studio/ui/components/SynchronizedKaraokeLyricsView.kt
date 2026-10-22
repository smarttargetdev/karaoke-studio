package br.com.smarttarget.karaoke.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.engine.KaraokeLine
import br.com.smarttarget.karaoke.studio.engine.KaraokeSyllable
import br.com.smarttarget.karaoke.studio.engine.KaraokeSynchronizationManager
import br.com.smarttarget.karaoke.studio.engine.KaraokeWord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Componente Compose de Letras Sincronizadas estilo Karaokê com animação contínua
 * de preenchimento de cor (Color Wipe) por sílaba em tempo real.
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
            text = "Letra Sincronizada (Color Wipe Silábico)",
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

      // 2. Caixa de Letra Ativa estilo Teleprompter Karaokê Profissional
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

            // Barra de progresso da frase atual com gradiente de wipe
            LinearProgressIndicator(
              progress = { syncState.lineProgressRatio },
              modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
              color = Color(0xFFFFD600),
              trackColor = Color(0xFF222638)
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Linha Ativa com preenchimento contínuo de cor (syllable color wipe)
            FlowRow(
              horizontalArrangement = Arrangement.Center,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.fillMaxWidth()
            ) {
              activeLine.words.forEach { word ->
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                ) {
                  word.syllables.forEach { syllable ->
                    KaraokeSyllableItem(
                      syllable = syllable,
                      currentPositionMs = currentPositionMs
                    )
                  }
                  Spacer(modifier = Modifier.width(4.dp))
                }
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
            Spacer(modifier = Modifier.height(12.dp))
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

/**
 * Renderizador de Sílaba com animação suave de preenchimento horizontal de cor (Wipe).
 */
@Composable
private fun KaraokeSyllableItem(
  syllable: KaraokeSyllable,
  currentPositionMs: Long
) {
  val isFinished = currentPositionMs >= syllable.endMs
  val isUpcoming = currentPositionMs < syllable.startMs

  // Fração exata de preenchimento da sílaba
  val rawProgress = when {
    isFinished -> 1.0f
    isUpcoming -> 0.0f
    else -> ((currentPositionMs - syllable.startMs).toFloat() / syllable.durationMs.toFloat()).coerceIn(0f, 1f)
  }

  // Animação suave para evitar qualquer saltinho entre sílabas
  val animatedProgress = remember { Animatable(rawProgress) }
  LaunchedEffect(rawProgress) {
    if (rawProgress == 1.0f || rawProgress == 0.0f) {
      animatedProgress.snapTo(rawProgress)
    } else {
      animatedProgress.animateTo(
        targetValue = rawProgress,
        animationSpec = tween(durationMillis = 45, easing = LinearEasing)
      )
    }
  }

  val density = LocalDensity.current
  val fontSizeSp = 18.sp
  val fontSizePx = with(density) { fontSizeSp.toPx() }

  Box(
    contentAlignment = Alignment.CenterStart,
    modifier = Modifier.height(28.dp)
  ) {
    // 1. Camada Base (Texto ainda não cantado em prata/cinza com leve sombra)
    Text(
      text = syllable.text,
      fontSize = fontSizeSp,
      fontWeight = FontWeight.Bold,
      color = Color(0xFFB0BEC5),
      fontFamily = FontFamily.Default
    )

    // 2. Camada de Sobreposição Preenchida (Dourado Brilhante) com recorte horizontal suave
    if (animatedProgress.value > 0.0f) {
      Canvas(
        modifier = Modifier
          .matchParentSize()
      ) {
        val w = size.width
        val h = size.height
        val fillWidth = w * animatedProgress.value

        clipRect(left = 0f, top = 0f, right = fillWidth, bottom = h) {
          val paint = android.graphics.Paint().apply {
            textSize = fontSizePx
            color = android.graphics.Color.parseColor("#FFD600")
            isAntiAlias = true
            isFakeBoldText = true
            // Efeito de brilho de palco no karaokê
            setShadowLayer(8f, 0f, 0f, android.graphics.Color.parseColor("#FFAB00"))
          }

          // Centralização vertical consistente
          val fontMetrics = paint.fontMetrics
          val baseline = (h / 2f) - ((fontMetrics.ascent + fontMetrics.descent) / 2f)

          drawContext.canvas.nativeCanvas.drawText(
            syllable.text,
            0f,
            baseline,
            paint
          )
        }

        // Ponto luminoso de destaque no cursor da sílaba ativa
        if (animatedProgress.value in 0.05f..0.98f) {
          drawCircle(
            brush = Brush.radialGradient(
              colors = listOf(Color(0xFFFFF59D), Color.Transparent),
              center = Offset(fillWidth, h * 0.2f),
              radius = 8f
            ),
            center = Offset(fillWidth, h * 0.2f),
            radius = 8f
          )
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
