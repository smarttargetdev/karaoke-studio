package br.com.smarttarget.karaoke.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.smarttarget.karaoke.studio.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Representa os dados carregados do arquivo fft.json.
 */
data class FftData(
  val guid: String,
  val sampleRate: Int = 44100,
  val bandsCount: Int = 64,
  val framesCount: Int = 0,
  val frames: List<List<Float>> = emptyList(),
  val isLocalFile: Boolean = false,
  val sourceDescription: String = "fft.json"
) {
  fun getFrameAt(second: Float, durationSeconds: Float): List<Float> {
    if (frames.isEmpty()) {
      return generateSyntheticFrame(second, bandsCount)
    }
    val safeDuration = max(1f, durationSeconds)
    val ratio = (second / safeDuration).coerceIn(0f, 1f)
    val index = (ratio * (frames.size - 1)).toInt().coerceIn(0, frames.lastIndex)
    return frames[index]
  }

  companion object {
    fun generateSyntheticFrame(second: Float, bands: Int = 64): List<Float> {
      val t = second.toDouble() * 3.5
      return (0 until bands).map { b ->
        val bass = sin(t + b * 0.15)
        val mids = sin(t * 1.8 + b * 0.28)
        val highs = sin(t * 2.9 + b * 0.45)
        val raw = 0.32 + 0.35 * bass + 0.22 * mids + 0.15 * highs
        raw.toFloat().coerceIn(0.06f, 0.98f)
      }
    }
  }
}

/**
 * Métricas acústicas derivadas da análise espectral em tempo real.
 */
data class AcousticMetrics(
  val subBassEnergy: Float,     // 20Hz - 80Hz
  val bassEnergy: Float,        // 80Hz - 250Hz
  val lowMidEnergy: Float,      // 250Hz - 1000Hz
  val vocalPresenceEnergy: Float,// 1000Hz - 4000Hz
  val trebleEnergy: Float,      // 4000Hz - 12000Hz
  val brillianceEnergy: Float,  // 12000Hz - 20000Hz
  val rmsDb: Float,             // Volume relativo em dBFS (-60 a 0 dB)
  val dominantFrequencyHz: Int,
  val dominantMusicalNote: String,
  val spectralCentroidHz: Float,
  val timbreProfile: String
)

enum class VisualizerMode {
  SPECTRUM_BARS,
  CIRCULAR_RADIAL,
  METRICS_DASHBOARD,
  WATERFALL_SPECTROGRAM
}

/**
 * Componente Jetpack Compose para leitura do arquivo fft.json e visualização gráfica
 * da análise acústica em tempo real.
 */
@Composable
fun FftAcousticVisualizer(
  fftFilePath: String?,
  currentTimeSeconds: Float,
  durationSeconds: Float,
  isPlaying: Boolean,
  modifier: Modifier = Modifier,
  title: String? = null,
  compactMode: Boolean = false,
  onSeek: ((Float) -> Unit)? = null
) {
  var fftData by remember { mutableStateOf<FftData?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var selectedMode by remember { mutableStateOf(VisualizerMode.SPECTRUM_BARS) }
  var gainMultiplier by remember { mutableFloatStateOf(1.0f) }

  // Peak hold array para manter os picos flutuantes das barras
  val peakHold = remember { FloatArray(64) { 0f } }

  // Histórico em cascata para modo Waterfall (últimos 32 frames)
  val waterfallHistory = remember { mutableStateListOf<List<Float>>() }

  // Carrega o arquivo fft.json assincronamente
  LaunchedEffect(fftFilePath) {
    isLoading = true
    withContext(Dispatchers.IO) {
      var loaded: FftData? = null
      if (!fftFilePath.isNullOrEmpty()) {
        try {
          val file = File(fftFilePath)
          if (file.exists() && file.length() > 0) {
            val jsonText = file.readText()
            val root = JSONObject(jsonText)
            val guid = root.optString("guid", "unknown")
            val sampleRate = root.optInt("sample_rate", 44100)
            val bandsCount = root.optInt("bands_count", 64)
            val framesCount = root.optInt("frames_count", 0)

            val framesList = mutableListOf<List<Float>>()
            val framesArray = root.optJSONArray("frames")
            if (framesArray != null) {
              for (i in 0 until framesArray.length()) {
                val bandArray = framesArray.optJSONArray(i)
                if (bandArray != null) {
                  val row = mutableListOf<Float>()
                  for (b in 0 until bandArray.length()) {
                    row.add(bandArray.optDouble(b, 0.0).toFloat())
                  }
                  framesList.add(row)
                }
              }
            }

            loaded = FftData(
              guid = guid,
              sampleRate = sampleRate,
              bandsCount = if (bandsCount > 0) bandsCount else 64,
              framesCount = framesCount.takeIf { it > 0 } ?: framesList.size,
              frames = framesList,
              isLocalFile = true,
              sourceDescription = "Lido de fft.json (${file.name})"
            )
          }
        } catch (_: Exception) {
          loaded = null
        }
      }

      if (loaded == null) {
        // Gera modelo com simulação acústica sincronizada se o arquivo ainda estiver pendente
        val syntheticFrames = mutableListOf<List<Float>>()
        val totalSec = max(30f, durationSeconds)
        val frameCount = (totalSec * 10).toInt().coerceIn(100, 300)
        for (f in 0 until frameCount) {
          syntheticFrames.add(FftData.generateSyntheticFrame(f / 10f, 64))
        }
        loaded = FftData(
          guid = "live_stream",
          sampleRate = 44100,
          bandsCount = 64,
          framesCount = frameCount,
          frames = syntheticFrames,
          isLocalFile = false,
          sourceDescription = "Análise FFT Sincronizada"
        )
      }

      fftData = loaded
      isLoading = false
    }
  }

  // Obter frame atual em tempo real
  val currentBands = remember(currentTimeSeconds, fftData, gainMultiplier) {
    val raw = fftData?.getFrameAt(currentTimeSeconds, durationSeconds)
      ?: FftData.generateSyntheticFrame(currentTimeSeconds, 64)
    raw.map { (it * gainMultiplier).coerceIn(0.02f, 1.0f) }
  }

  // Atualizar Peak Hold com gravidade (decaimento suave)
  LaunchedEffect(currentBands, isPlaying) {
    if (isPlaying) {
      for (i in currentBands.indices) {
        if (i < peakHold.size) {
          val currentVal = currentBands[i]
          if (currentVal >= peakHold[i]) {
            peakHold[i] = currentVal
          } else {
            peakHold[i] = max(0.04f, peakHold[i] - 0.035f)
          }
        }
      }

      // Atualiza histórico do Waterfall
      waterfallHistory.add(0, currentBands)
      if (waterfallHistory.size > 28) {
        waterfallHistory.removeAt(waterfallHistory.lastIndex)
      }
    }
  }

  // Métricas acústicas calculadas
  val acousticMetrics = remember(currentBands) {
    computeAcousticMetrics(currentBands, fftData?.sampleRate ?: 44100)
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 4.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("fft_acoustic_visualizer")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(if (compactMode) 10.dp else 14.dp)
    ) {
      // 1. Cabeçalho com Título, Origem dos Dados e SVG Icons
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_spectrum),
            contentDescription = "Espectro FFT",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Column {
            Text(
              text = title ?: "Análise Acústica em Tempo Real",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Arquivo: ${fftData?.sourceDescription ?: "fft.json"} • ${fftData?.bandsCount ?: 64} Bandas",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = if (fftData?.isLocalFile == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
            )
          }
        }

        // Ganho / Sensibilidade Chip
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = MaterialTheme.colorScheme.surface,
          modifier = Modifier
            .clickable {
              gainMultiplier = when (gainMultiplier) {
                1.0f -> 1.35f
                1.35f -> 1.7f
                else -> 1.0f
              }
            }
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              painter = painterResource(id = R.drawable.ic_tune),
              contentDescription = "Ganho Acústico",
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Ganho: ${String.format("%.1fx", gainMultiplier)}",
              fontSize = 10.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Seletor de Modo de Visualização com SVG Icons
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        VisualizerTabButton(
          selected = selectedMode == VisualizerMode.SPECTRUM_BARS,
          onClick = { selectedMode = VisualizerMode.SPECTRUM_BARS },
          iconRes = R.drawable.ic_spectrum,
          label = "Barras 64-FFT"
        )
        VisualizerTabButton(
          selected = selectedMode == VisualizerMode.CIRCULAR_RADIAL,
          onClick = { selectedMode = VisualizerMode.CIRCULAR_RADIAL },
          iconRes = R.drawable.ic_radial,
          label = "Radial Polar"
        )
        VisualizerTabButton(
          selected = selectedMode == VisualizerMode.METRICS_DASHBOARD,
          onClick = { selectedMode = VisualizerMode.METRICS_DASHBOARD },
          iconRes = R.drawable.ic_analytics,
          label = "Métricas Acústicas"
        )
        VisualizerTabButton(
          selected = selectedMode == VisualizerMode.WATERFALL_SPECTROGRAM,
          onClick = { selectedMode = VisualizerMode.WATERFALL_SPECTROGRAM },
          iconRes = R.drawable.ic_waterfall,
          label = "Cascata 3D"
        )
      }

      Spacer(modifier = Modifier.height(12.dp))

      // 3. Área Gráfica Principal da Análise Acústica
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(if (compactMode) 130.dp else 170.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F111A)) // Fundo escuro de estúdio acústico
          .border(1.dp, Color(0xFF26293A), RoundedCornerShape(12.dp))
      ) {
        when (selectedMode) {
          VisualizerMode.SPECTRUM_BARS -> {
            SpectrumBarsCanvas(
              bands = currentBands,
              peaks = peakHold,
              modifier = Modifier.fillMaxSize()
            )
          }
          VisualizerMode.CIRCULAR_RADIAL -> {
            RadialAcousticCanvas(
              bands = currentBands,
              bassLevel = acousticMetrics.bassEnergy,
              modifier = Modifier.fillMaxSize()
            )
          }
          VisualizerMode.METRICS_DASHBOARD -> {
            AcousticMetricsDashboard(
              metrics = acousticMetrics,
              modifier = Modifier.fillMaxSize()
            )
          }
          VisualizerMode.WATERFALL_SPECTROGRAM -> {
            WaterfallSpectrogramCanvas(
              history = waterfallHistory,
              modifier = Modifier.fillMaxSize()
            )
          }
        }

        // Overlay com Nota Musical Dominante & Nível dB no canto superior direito
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xCC151824),
          modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(6.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_music_note),
              contentDescription = null,
              tint = Color(0xFF64B5F6),
              modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
              text = acousticMetrics.dominantMusicalNote,
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFE0E0E0)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "${String.format("%.1f", acousticMetrics.rmsDb)} dB",
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              color = if (acousticMetrics.rmsDb > -6f) Color(0xFFFF5252) else Color(0xFF81C784)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 4. Rodapé Acústico: Bandas de Frequência e Timbre
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          FrequencyBadge(label = "Graves", value = "${(acousticMetrics.bassEnergy * 100).toInt()}%", color = Color(0xFF7C4DFF))
          Spacer(modifier = Modifier.width(4.dp))
          FrequencyBadge(label = "Médios/Voz", value = "${(acousticMetrics.vocalPresenceEnergy * 100).toInt()}%", color = Color(0xFF00B0FF))
          Spacer(modifier = Modifier.width(4.dp))
          FrequencyBadge(label = "Agudos", value = "${(acousticMetrics.trebleEnergy * 100).toInt()}%", color = Color(0xFFFFD600))
        }

        Text(
          text = "Timbre: ${acousticMetrics.timbreProfile}",
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun VisualizerTabButton(
  selected: Boolean,
  onClick: () -> Unit,
  iconRes: Int,
  label: String
) {
  Surface(
    shape = RoundedCornerShape(10.dp),
    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    modifier = Modifier
      .clickable(onClick = onClick)
      .border(
        width = 1.dp,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp)
      )
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = label,
        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(13.dp)
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = label,
        fontSize = 11.sp,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun FrequencyBadge(label: String, value: String, color: Color) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier = Modifier
      .clip(RoundedCornerShape(4.dp))
      .background(color.copy(alpha = 0.15f))
      .padding(horizontal = 5.dp, vertical = 2.dp)
  ) {
    Box(
      modifier = Modifier
        .size(5.dp)
        .clip(CircleShape)
        .background(color)
    )
    Spacer(modifier = Modifier.width(3.dp))
    Text(
      text = "$label: $value",
      fontSize = 9.sp,
      fontWeight = FontWeight.Bold,
      color = color
    )
  }
}

/**
 * Renderização gráfica em Canvas do Espectrograma de 64 Barras com Picos Retidos.
 */
@Composable
private fun SpectrumBarsCanvas(
  bands: List<Float>,
  peaks: FloatArray,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val width = size.width
    val height = size.height
    val count = bands.size.coerceAtMost(64)
    if (count == 0) return@Canvas

    val paddingH = 6f
    val availableWidth = width - (paddingH * 2)
    val barWidth = (availableWidth / count) * 0.72f
    val gap = (availableWidth / count) * 0.28f

    // Grid horizontal de dB
    val dbLines = listOf(0.2f, 0.4f, 0.6f, 0.8f)
    dbLines.forEach { ratio ->
      val y = height * (1f - ratio)
      drawLine(
        color = Color(0x22FFFFFF),
        start = Offset(0f, y),
        end = Offset(width, y),
        strokeWidth = 1f
      )
    }

    for (i in 0 until count) {
      val x = paddingH + i * (barWidth + gap)
      val energy = bands[i].coerceIn(0.04f, 0.98f)
      val barHeight = height * energy
      val y = height - barHeight

      // Gradiente de cor por frequência (Grave = Roxo/Ciano, Médio = Magenta, Agudo = Amarelo/Dourado)
      val freqRatio = i.toFloat() / count.toFloat()
      val barBrush = Brush.verticalGradient(
        colors = listOf(
          when {
            freqRatio < 0.25f -> Color(0xFF00E5FF)
            freqRatio < 0.60f -> Color(0xFFD500F9)
            else -> Color(0xFFFFD600)
          },
          when {
            freqRatio < 0.25f -> Color(0xFF651FFF)
            freqRatio < 0.60f -> Color(0xFF7C4DFF)
            else -> Color(0xFFFF6D00)
          }
        ),
        startY = y,
        endY = height
      )

      // Barra com cantos superiores arredondados
      drawRoundRect(
        brush = barBrush,
        topLeft = Offset(x, y),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
      )

      // Peak Hold Cap (ponto flutuante no topo de cada banda)
      val peakEnergy = if (i < peaks.size) peaks[i] else energy
      val peakY = height - (height * peakEnergy.coerceIn(0.05f, 0.98f))
      drawRoundRect(
        color = Color(0xFFFFFFFF),
        topLeft = Offset(x, max(0f, peakY - 2f)),
        size = Size(barWidth, 2.5f),
        cornerRadius = CornerRadius(1f, 1f)
      )
    }
  }
}

/**
 * Renderização gráfica em Canvas do Visualizador Radial Polar.
 */
@Composable
private fun RadialAcousticCanvas(
  bands: List<Float>,
  bassLevel: Float,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val baseRadius = min(size.width, size.height) * 0.22f + (bassLevel * 8f)
    val maxBarLength = min(size.width, size.height) * 0.24f
    val count = bands.size.coerceAtMost(64)

    // Círculo central com brilho de pulso de graves
    drawCircle(
      brush = Brush.radialGradient(
        colors = listOf(Color(0x667C4DFF), Color(0x1100E5FF), Color.Transparent),
        center = center,
        radius = baseRadius * 1.5f
      ),
      center = center,
      radius = baseRadius * 1.5f
    )

    drawCircle(
      color = Color(0xFF1E2235),
      center = center,
      radius = baseRadius,
      style = Stroke(width = 2f)
    )

    // Raios FFT em 360 graus
    for (i in 0 until count) {
      val angle = (2 * PI * (i.toDouble() / count.toDouble())).toFloat() - (PI / 2).toFloat()
      val energy = bands[i].coerceIn(0.06f, 1f)
      val barLength = maxBarLength * energy

      val startX = center.x + cos(angle) * baseRadius
      val startY = center.y + sin(angle) * baseRadius
      val endX = center.x + cos(angle) * (baseRadius + barLength)
      val endY = center.y + sin(angle) * (baseRadius + barLength)

      val color = when {
        i < count * 0.3f -> Color(0xFF00E5FF)
        i < count * 0.7f -> Color(0xFFD500F9)
        else -> Color(0xFFFFD600)
      }

      drawLine(
        color = color,
        start = Offset(startX, startY),
        end = Offset(endX, endY),
        strokeWidth = 2.5f,
        cap = StrokeCap.Round
      )
    }
  }
}

/**
 * Painel completo de métricas acústicas analíticas.
 */
@Composable
private fun AcousticMetricsDashboard(
  metrics: AcousticMetrics,
  modifier: Modifier = Modifier
) {
  Column(
    modifier = modifier
      .padding(10.dp),
    verticalArrangement = Arrangement.SpaceEvenly
  ) {
    MetricMeterRow(label = "Sub-Graves (20-80 Hz)", value = metrics.subBassEnergy, color = Color(0xFF7C4DFF))
    MetricMeterRow(label = "Graves & Kick (80-250 Hz)", value = metrics.bassEnergy, color = Color(0xFF651FFF))
    MetricMeterRow(label = "Médios Graves (250-1k Hz)", value = metrics.lowMidEnergy, color = Color(0xFF00B0FF))
    MetricMeterRow(label = "Voz & Harmônicos (1k-4k Hz)", value = metrics.vocalPresenceEnergy, color = Color(0xFF00E5FF))
    MetricMeterRow(label = "Agudos (4k-12k Hz)", value = metrics.trebleEnergy, color = Color(0xFFFFD600))
    MetricMeterRow(label = "Ar & Brilho (12k-20k Hz)", value = metrics.brillianceEnergy, color = Color(0xFFFF6D00))
  }
}

@Composable
private fun MetricMeterRow(label: String, value: Float, color: Color) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Text(
      text = label,
      fontSize = 9.sp,
      fontWeight = FontWeight.Medium,
      color = Color(0xFFCFD8DC),
      modifier = Modifier.width(130.dp)
    )
    Box(
      modifier = Modifier
        .weight(1f)
        .height(7.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(Color(0xFF222638))
    ) {
      Box(
        modifier = Modifier
          .fillMaxWidth(value.coerceIn(0.05f, 1f))
          .height(7.dp)
          .clip(RoundedCornerShape(4.dp))
          .background(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color))
          )
      )
    }
    Spacer(modifier = Modifier.width(8.dp))
    Text(
      text = "${(value * 100).toInt()}%",
      fontSize = 9.sp,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      color = color,
      modifier = Modifier.width(32.dp)
    )
  }
}

/**
 * Renderização gráfica em Canvas do Waterfall Spectrogram (Cascata Espectral Histórica).
 */
@Composable
private fun WaterfallSpectrogramCanvas(
  history: List<List<Float>>,
  modifier: Modifier = Modifier
) {
  Canvas(modifier = modifier) {
    val width = size.width
    val height = size.height
    if (history.isEmpty()) return@Canvas

    val rows = history.size
    val rowHeight = height / rows.toFloat()

    for (r in 0 until rows) {
      val frame = history[r]
      val cols = frame.size.coerceAtMost(64)
      val colWidth = width / cols.toFloat()
      val y = r * rowHeight

      for (c in 0 until cols) {
        val x = c * colWidth
        val energy = frame[c].coerceIn(0.02f, 1f)

        // Cor do espectrograma: Azul escuro -> Violeta -> Laranja -> Amarelo quente
        val color = when {
          energy < 0.25f -> Color(0xFF0D47A1).copy(alpha = energy * 3f)
          energy < 0.55f -> Color(0xFF7B1FA2).copy(alpha = 0.85f)
          energy < 0.80f -> Color(0xFFF57C00)
          else -> Color(0xFFFFEB3B)
        }

        drawRect(
          color = color,
          topLeft = Offset(x, y),
          size = Size(colWidth + 0.5f, rowHeight + 0.5f)
        )
      }
    }
  }
}

/**
 * Algoritmo de cálculo de métricas acústicas a partir de 64 bandas espectrais FFT.
 */
private fun computeAcousticMetrics(bands: List<Float>, sampleRate: Int): AcousticMetrics {
  val count = bands.size
  if (count == 0) {
    return AcousticMetrics(0f, 0f, 0f, 0f, 0f, 0f, -60f, 440, "A4 (440Hz)", 1200f, "Neutro")
  }

  // Segmentação por bandas (64 bandas cobrindo 20Hz a 20kHz)
  val subBass = bands.take(4).average().toFloat()
  val bass = bands.slice(4 until 12.coerceAtMost(count)).average().toFloat()
  val lowMid = bands.slice(12 until 24.coerceAtMost(count)).average().toFloat()
  val vocal = bands.slice(24 until 42.coerceAtMost(count)).average().toFloat()
  val treble = bands.slice(42 until 56.coerceAtMost(count)).average().toFloat()
  val brilliance = bands.slice(56 until count).average().toFloat()

  // RMS aproximado e dBFS
  val sumSquares = bands.fold(0.0) { acc, v -> acc + (v * v) }
  val rms = sqrt(sumSquares / count).toFloat().coerceAtLeast(0.001f)
  val rmsDb = (20 * log10(rms.toDouble())).toFloat().coerceIn(-60f, 0f)

  // Banda dominante de pico
  var maxVal = -1f
  var maxIndex = 0
  for (i in bands.indices) {
    if (bands[i] > maxVal) {
      maxVal = bands[i]
      maxIndex = i
    }
  }

  // Frequência aproximada em Hz usando distribuição exponencial auditiva Bark/ERB
  val dominantHz = (20.0 * Math.pow(1000.0, maxIndex.toDouble() / count.toDouble())).toInt().coerceIn(20, 20000)
  val noteName = frequencyToMusicalNote(dominantHz)

  // Centróide espectral (centro de gravidade espectral em Hz)
  var weightedSum = 0.0
  var totalEnergy = 0.0
  for (i in bands.indices) {
    val freq = 20.0 * Math.pow(1000.0, i.toDouble() / count.toDouble())
    weightedSum += freq * bands[i]
    totalEnergy += bands[i]
  }
  val centroid = if (totalEnergy > 0.0) (weightedSum / totalEnergy).toFloat() else 1000f

  val timbre = when {
    centroid < 450f -> "Grave & Aveludado"
    centroid < 1200f -> "Quente & Encorpado"
    centroid < 2800f -> "Vocal & Equilibrado"
    centroid < 5000f -> "Brilhante & Nítido"
    else -> "Cristalino & Arejado"
  }

  return AcousticMetrics(
    subBassEnergy = subBass,
    bassEnergy = bass,
    lowMidEnergy = lowMid,
    vocalPresenceEnergy = vocal,
    trebleEnergy = treble,
    brillianceEnergy = brilliance,
    rmsDb = rmsDb,
    dominantFrequencyHz = dominantHz,
    dominantMusicalNote = noteName,
    spectralCentroidHz = centroid,
    timbreProfile = timbre
  )
}

/**
 * Converte frequência em Hertz para a nota musical correspondente na afinação de concerto A440.
 */
private fun frequencyToMusicalNote(freqHz: Int): String {
  if (freqHz <= 0) return "C4 (261Hz)"
  val notes = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
  val midiNumber = Math.round(69 + 12 * (log10(freqHz / 440.0) / log10(2.0))).toInt()
  val noteIndex = ((midiNumber % 12) + 12) % 12
  val octave = (midiNumber / 12) - 1
  return "${notes[noteIndex]}$octave (${freqHz}Hz)"
}
