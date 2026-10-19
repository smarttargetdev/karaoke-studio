package br.com.smarttarget.karaoke.studio.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import br.com.smarttarget.karaoke.studio.R
import br.com.smarttarget.karaoke.studio.engine.MicrophonePitchDetectorEngine
import br.com.smarttarget.karaoke.studio.engine.PitchComparisonResult
import br.com.smarttarget.karaoke.studio.engine.TuningStatus
import kotlin.math.abs

/**
 * Componente Compose de Detecção de Pitch via Microfone com Comparação de Afinação
 * em Tempo Real contra a música de referência.
 */
@Composable
fun PitchDetectionVisualizer(
  referencePitchHz: Float,
  modifier: Modifier = Modifier,
  engine: MicrophonePitchDetectorEngine = remember { MicrophonePitchDetectorEngine() }
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()

  val isListening by engine.isRecording.collectAsState()
  val pitchResult by engine.pitchResult.collectAsState()
  val totalScore by engine.totalScore.collectAsState()
  val streak by engine.streakCount.collectAsState()

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO
      ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasMicPermission = granted
    if (granted) {
      engine.startListening(scope) { referencePitchHz }
    } else {
      // Inicia em modo simulação caso o usuário negue
      engine.startSimulation(scope) { referencePitchHz }
    }
  }

  // Histórico para a "Pista de Notas / Note Highway"
  val pitchTrail = remember { mutableStateListOf<Float>() }

  LaunchedEffect(pitchResult) {
    if (pitchResult.isSinging) {
      pitchTrail.add(pitchResult.centsOffset)
      if (pitchTrail.size > 24) pitchTrail.removeAt(0)
    } else {
      pitchTrail.add(0f)
      if (pitchTrail.size > 24) pitchTrail.removeAt(0)
    }
  }

  DisposableEffect(Unit) {
    onDispose {
      engine.stopListening()
    }
  }

  // Cor dinâmica de afinação
  val statusColor by animateColorAsState(
    targetValue = when (pitchResult.tuningStatus) {
      TuningStatus.PERFECT_IN_TUNE -> Color(0xFF00E676) // Verde esmeralda brilhante
      TuningStatus.SLIGHTLY_SHARP -> Color(0xFFFFB300)  // Amarelo âmbar
      TuningStatus.SLIGHTLY_FLAT -> Color(0xFF00B0FF)   // Ciano
      TuningStatus.OUT_OF_TUNE -> Color(0xFFFF1744)     // Vermelho
      TuningStatus.SILENT -> Color(0xFF78909C)          // Cinza neutro
    },
    label = "pitch_status_color"
  )

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 3.dp,
    modifier = modifier
      .fillMaxWidth()
      .testTag("pitch_detection_visualizer")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp)
    ) {
      // 1. Cabeçalho com SVG Icons, Pontuação de Karaokê e Ativação do Mic
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            painter = painterResource(id = R.drawable.ic_pitch_tune),
            contentDescription = "Detecção de Pitch",
            tint = statusColor,
            modifier = Modifier.size(20.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Column {
            Text(
              text = "Afinador de Voz (Pitch ao Vivo)",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "Nota da Música: ${pitchResult.referenceNoteName} (${referencePitchHz.toInt()} Hz)",
              fontSize = 10.sp,
              fontFamily = FontFamily.Monospace,
              color = MaterialTheme.colorScheme.primary
            )
          }
        }

        // Botão de Ativação do Microfone
        Button(
          onClick = {
            if (isListening) {
              engine.stopListening()
            } else {
              if (hasMicPermission) {
                engine.startListening(scope) { referencePitchHz }
              } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isListening) statusColor else MaterialTheme.colorScheme.primary
          ),
          shape = RoundedCornerShape(10.dp),
          contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.height(34.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_mic_active),
            contentDescription = null,
            tint = if (isListening) Color.Black else MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(14.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = if (isListening) "MIC ATIVO" else "LIGAR MIC",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isListening) Color.Black else MaterialTheme.colorScheme.onPrimary
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      // 2. Painel Gráfico de Afinação (Needle Gauge + Pista de Afinação)
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .height(115.dp)
          .clip(RoundedCornerShape(12.dp))
          .background(Color(0xFF0F111A))
          .border(1.dp, Color(0xFF26293A), RoundedCornerShape(12.dp))
      ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
          val w = size.width
          val h = size.height
          val centerY = h / 2f

          // Linhas de tolerância de afinação (+/- 20 cents = zona verde perfeita)
          val perfectZoneTop = centerY - (h * 0.20f)
          val perfectZoneBottom = centerY + (h * 0.20f)

          drawRect(
            color = Color(0x1F00E676),
            topLeft = Offset(0f, perfectZoneTop),
            size = Size(w, perfectZoneBottom - perfectZoneTop)
          )

          // Linha central guia (Afinação Exata 0 Cents)
          drawLine(
            color = Color(0x6600E676),
            start = Offset(0f, centerY),
            end = Offset(w, centerY),
            strokeWidth = 2f
          )

          // Graduações e marcas horizontais
          val steps = 7
          for (i in 0 until steps) {
            val x = (w / (steps - 1)) * i
            drawLine(
              color = Color(0x22FFFFFF),
              start = Offset(x, 10f),
              end = Offset(x, h - 10f),
              strokeWidth = 1f
            )
          }

          // Rastro histórico da voz do usuário
          if (pitchTrail.size > 1) {
            val segmentWidth = w / 24f
            for (i in 0 until pitchTrail.size - 1) {
              val x1 = i * segmentWidth
              val y1 = centerY - (pitchTrail[i] / 100f) * (h * 0.40f)
              val x2 = (i + 1) * segmentWidth
              val y2 = centerY - (pitchTrail[i + 1] / 100f) * (h * 0.40f)

              drawLine(
                color = statusColor.copy(alpha = (i + 1) / 24f),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 3f,
                cap = StrokeCap.Round
              )
            }
          }

          // Ponto atual da voz do usuário
          if (pitchResult.isSinging) {
            val currentY = centerY - (pitchResult.centsOffset / 100f) * (h * 0.40f)
            val currentX = w - 16f

            // Halo brilhante
            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(statusColor.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(currentX, currentY),
                radius = 18f
              ),
              center = Offset(currentX, currentY),
              radius = 18f
            )

            // Ponto central
            drawCircle(
              color = Color.White,
              center = Offset(currentX, currentY),
              radius = 5f
            )
          }
        }

        // Overlay com Nota Cantada e Cents no centro
        Column(
          modifier = Modifier
            .align(Alignment.Center)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xD9141724))
            .padding(horizontal = 12.dp, vertical = 4.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = if (pitchResult.isSinging) "Sua Voz: ${pitchResult.micNoteName}" else "Cante no Microfone",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor
          )
          Text(
            text = when (pitchResult.tuningStatus) {
              TuningStatus.PERFECT_IN_TUNE -> "AFINADO! (0 cents)"
              TuningStatus.SLIGHTLY_SHARP -> "ALTO (+${pitchResult.centsOffset.toInt()}c)"
              TuningStatus.SLIGHTLY_FLAT -> "BAIXO (${pitchResult.centsOffset.toInt()}c)"
              TuningStatus.OUT_OF_TUNE -> "FORA DO TOM (${pitchResult.centsOffset.toInt()}c)"
              TuningStatus.SILENT -> "Aguardando voz..."
            },
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFCFD8DC)
          )
        }
      }

      Spacer(modifier = Modifier.height(8.dp))

      // 3. Rodapé com Placar de Afinação Karaokê e Streak
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primaryContainer
          ) {
            Text(
              text = "Pontos: $totalScore",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }

          if (streak > 2) {
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
              shape = RoundedCornerShape(6.dp),
              color = Color(0xFFFFD600)
            ) {
              Text(
                text = "${streak}x COMBO 🔥",
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
              )
            }
          }
        }

        // Botão para resetar pontuação
        Text(
          text = "Zerar Pontos",
          fontSize = 10.sp,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier
            .clickable { engine.resetScore() }
            .padding(4.dp)
        )
      }
    }
  }
}
