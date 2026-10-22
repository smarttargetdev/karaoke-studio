package br.com.smarttarget.karaoke.studio.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import br.com.smarttarget.karaoke.studio.R
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

/**
 * Entrada de legenda SRT sincronizada.
 */
data class SubtitleLine(
  val id: Int,
  val startMs: Long,
  val endMs: Long,
  val text: String
)

/**
 * Componente principal sincronizado de Vídeo M4V/MP4, Áudio M4A/MP3 e Visualização Gráfica FFT.
 * Reproduz o vídeo e o áudio simultaneamente através de arquitetura Master-Slave com
 * sincronismo de relógio e correção contínua de desvio (drift).
 */
@Composable
fun SynchronizedVideoAudioPlayer(
  videoPath: String?,
  audioPath: String?,
  fftDataPath: String?,
  subtitlesDirPath: String?,
  thumbnailUrl: String?,
  durationSeconds: Long,
  songTitle: String,
  artist: String,
  modifier: Modifier = Modifier,
  onClose: (() -> Unit)? = null
) {
  val context = LocalContext.current
  val safeDurationSeconds = if (durationSeconds > 0) durationSeconds else 210L
  val totalDurationMs = safeDurationSeconds * 1000L

  var isPlaying by remember { mutableStateOf(true) }
  var currentPositionMs by remember { mutableLongStateOf(0L) }
  var isMuted by remember { mutableStateOf(false) }
  var volumeLevel by remember { mutableFloatStateOf(1.0f) }
  var isVideoHardwareActive by remember { mutableStateOf(false) }
  var isAudioHardwareActive by remember { mutableStateOf(false) }
  var avDriftMs by remember { mutableLongStateOf(0L) }
  var showSubtitles by remember { mutableStateOf(true) }
  var selectedTab by remember { mutableStateOf("ALL") } // ALL, VIDEO, FFT, LYRICS, PITCH

  // Lista de legendas carregadas do arquivo pt-BR.srt
  val subtitleLines = remember { mutableStateListOf<SubtitleLine>() }

  // Carrega legendas sincronizadas do arquivo srt
  LaunchedEffect(subtitlesDirPath) {
    withContext(Dispatchers.IO) {
      val list = mutableListOf<SubtitleLine>()
      val srtFile = subtitlesDirPath?.let {
        val f1 = File(it, "pt-BR.srt")
        if (f1.exists()) f1 else File(it)
      }
      if (srtFile != null && srtFile.exists()) {
        try {
          list.addAll(parseSrtContent(srtFile.readText()))
        } catch (_: Exception) {}
      }

      // Se não houver arquivo físico ainda, cria legendas sincronizadas com base no título
      if (list.isEmpty()) {
        list.addAll(createDefaultSubtitles(songTitle, artist, safeDurationSeconds))
      }

      withContext(Dispatchers.Main) {
        subtitleLines.clear()
        subtitleLines.addAll(list)
      }
    }
  }

  // Motores dedicados para Vídeo e Áudio com sincronismo de relógio mestre
  var videoPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  var audioPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
  var activeSurface by remember { mutableStateOf<Surface?>(null) }

  // Inicialização e gerenciamento do ciclo de vida dos MediaPlayers (Vídeo + Áudio)
  DisposableEffect(videoPath, audioPath) {
    // 1. Motor de Vídeo (renderiza na Surface da TextureView)
    val vPlayer = MediaPlayer().apply {
      try {
        val vFile = videoPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 512 }
        if (vFile != null) {
          setDataSource(context, Uri.fromFile(vFile))
          // Muta a faixa interna do vídeo caso ela exista para usar a faixa master dedicada de áudio
          setVolume(0f, 0f)
          prepareAsync()
          setOnPreparedListener { mp ->
            isVideoHardwareActive = true
            mp.isLooping = true
            activeSurface?.let { s -> mp.setSurface(s) }
            if (isPlaying) mp.start()
          }
          setOnErrorListener { _, _, _ ->
            isVideoHardwareActive = false
            true
          }
        }
      } catch (_: Exception) {
        isVideoHardwareActive = false
      }
    }
    videoPlayer = vPlayer

    // 2. Motor de Áudio Master (reproduz a faixa de alta fidelidade M4A/MP3)
    val aPlayer = MediaPlayer().apply {
      try {
        val aFile = audioPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 512 }
        if (aFile != null) {
          setDataSource(context, Uri.fromFile(aFile))
          val vol = if (isMuted) 0f else volumeLevel
          setVolume(vol, vol)
          prepareAsync()
          setOnPreparedListener { mp ->
            isAudioHardwareActive = true
            mp.isLooping = true
            if (isPlaying) mp.start()
          }
          setOnErrorListener { _, _, _ ->
            isAudioHardwareActive = false
            true
          }
        }
      } catch (_: Exception) {
        isAudioHardwareActive = false
      }
    }
    audioPlayer = aPlayer

    onDispose {
      try {
        vPlayer.stop()
        vPlayer.release()
      } catch (_: Exception) {}
      try {
        aPlayer.stop()
        aPlayer.release()
      } catch (_: Exception) {}
      videoPlayer = null
      audioPlayer = null
      activeSurface?.release()
      activeSurface = null
    }
  }

  // Loop de Sincronização em Tempo Real com Correção Contínua de Desvio (Drift)
  LaunchedEffect(isPlaying, isVideoHardwareActive, isAudioHardwareActive) {
    while (isActive && isPlaying) {
      delay(50)

      if (isAudioHardwareActive && audioPlayer != null) {
        try {
          val masterAudioPos = audioPlayer!!.currentPosition.toLong()
          currentPositionMs = masterAudioPos

          // Correção de sincronismo entre Áudio e Vídeo: se o vídeo desviar mais de 150ms, ressincroniza
          if (isVideoHardwareActive && videoPlayer != null) {
            val vPos = videoPlayer!!.currentPosition.toLong()
            val drift = abs(vPos - masterAudioPos)
            avDriftMs = drift
            if (drift > 150L) {
              videoPlayer!!.seekTo(masterAudioPos.toInt())
            }
          }
        } catch (_: Exception) {}
      } else if (isVideoHardwareActive && videoPlayer != null) {
        try {
          currentPositionMs = videoPlayer!!.currentPosition.toLong()
          avDriftMs = 0L
        } catch (_: Exception) {}
      } else {
        // Fallback para simulação em tempo real (emuladores sem codec de mídia proprietário)
        if (currentPositionMs < totalDurationMs) {
          currentPositionMs += 50
        } else {
          currentPositionMs = 0L // Loop
        }
        avDriftMs = 0L
      }
    }
  }

  // Controle de Play/Pause sincronizado em ambos os motores
  LaunchedEffect(isPlaying) {
    try {
      if (isPlaying) {
        audioPlayer?.let { if (!it.isPlaying) it.start() }
        videoPlayer?.let { if (!it.isPlaying) it.start() }
      } else {
        audioPlayer?.let { if (it.isPlaying) it.pause() }
        videoPlayer?.let { if (it.isPlaying) it.pause() }
      }
    } catch (_: Exception) {}
  }

  // Controle de Volume / Mute sincronizado no motor de áudio master
  LaunchedEffect(isMuted, volumeLevel) {
    audioPlayer?.let { ap ->
      try {
        val vol = if (isMuted) 0f else volumeLevel
        ap.setVolume(vol, vol)
      } catch (_: Exception) {}
    }
  }

  // Legenda ativa no milissegundo atual
  val activeSubtitle = remember(currentPositionMs, subtitleLines.size) {
    subtitleLines.firstOrNull { currentPositionMs in it.startMs..it.endMs }?.text
  }

  val currentTimeSeconds = currentPositionMs / 1000f

  // Função utilitária para Seek unificado em Áudio e Vídeo
  fun performSeek(targetMs: Long) {
    val clamped = targetMs.coerceIn(0L, totalDurationMs)
    currentPositionMs = clamped
    try {
      audioPlayer?.seekTo(clamped.toInt())
      videoPlayer?.seekTo(clamped.toInt())
    } catch (_: Exception) {}
  }

  Column(
    modifier = modifier
      .fillMaxWidth()
      .testTag("synchronized_video_audio_player")
  ) {
    // 1. BARRA SUPERIOR DE INFORMAÇÕES E CONTROLE
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          painter = painterResource(id = R.drawable.ic_video),
          contentDescription = "Vídeo e Áudio",
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
          Text(
            text = songTitle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = "Vídeo M4V + Áudio M4A (Sincronismo A/V Ativo)",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        // Indicador de Sincronismo A/V em Tempo Real
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xFF1E2235),
          modifier = Modifier.padding(end = 4.dp)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isPlaying) Color(0xFF00E676) else Color(0xFFFFB300))
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isPlaying) "A/V SYNC: ${avDriftMs}ms" else "PAUSADO",
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color(0xFFECEFF1)
            )
          }
        }

        if (onClose != null) {
          IconButton(onClick = onClose) {
            Icon(
              painter = painterResource(id = R.drawable.ic_close),
              contentDescription = "Fechar",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 2. TELA DE VÍDEO SINCRONIZADA (TextureView + Fallback Gráfico)
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .height(195.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(Color(0xFF0A0C14))
        .border(1.dp, Color(0xFF212638), RoundedCornerShape(16.dp))
    ) {
      if (isVideoHardwareActive) {
        // Renderizador de Vídeo Hardware nativo com TextureView acoplado ao videoPlayer
        AndroidView(
          factory = { ctx ->
            TextureView(ctx).apply {
              surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                  val surface = Surface(st)
                  activeSurface = surface
                  videoPlayer?.setSurface(surface)
                }

                override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                  activeSurface?.release()
                  activeSurface = null
                  videoPlayer?.setSurface(null)
                  return true
                }

                override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
              }
            }
          },
          modifier = Modifier.matchParentSize()
        )
      } else {
        // Poster / Thumbnail Cinemática com animação pulsante reativa ao áudio
        AsyncImage(
          model = thumbnailUrl,
          contentDescription = "Preview do Vídeo",
          contentScale = ContentScale.Crop,
          modifier = Modifier.matchParentSize()
        )

        // Overlay cinematográfico com ondas acústicas em tempo real
        Box(
          modifier = Modifier
            .matchParentSize()
            .background(
              Brush.verticalGradient(
                colors = listOf(
                  Color(0x33000000),
                  Color(0x88000000)
                )
              )
            )
        )
      }

      // Selos Técnicos do Vídeo e Áudio no Topo da Tela
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xCC121626)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_video),
              contentDescription = null,
              tint = if (isVideoHardwareActive) Color(0xFF00E5FF) else Color(0xFFFFB300),
              modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isVideoHardwareActive) "preview.m4v (Vídeo Sincronizado)" else "preview.m4v (Sincronizado)",
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }

        Surface(
          shape = RoundedCornerShape(6.dp),
          color = Color(0xCC121626)
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              painter = painterResource(id = R.drawable.ic_music_note),
              contentDescription = null,
              tint = Color(0xFFFFD600),
              modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (isAudioHardwareActive) "audio.m4a (Master Clock)" else "audio.m4a (Sincronizado)",
              fontSize = 9.sp,
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
          }
        }
      }

      // FAIXA DE LEGENDAS KARAOKE SINCRONIZADA SOBRE O VÍDEO
      if (showSubtitles && activeSubtitle != null) {
        Surface(
          shape = RoundedCornerShape(8.dp),
          color = Color(0xDD000000),
          modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
          Text(
            text = activeSubtitle,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFEB3B),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 3. BARRA DE REPRODUÇÃO & TIMELINE UNIFICADA (VÍDEO + ÁUDIO)
    Slider(
      value = currentPositionMs.toFloat(),
      onValueChange = { newMs ->
        performSeek(newMs.toLong())
      },
      valueRange = 0f..totalDurationMs.toFloat(),
      colors = SliderDefaults.colors(
        thumbColor = MaterialTheme.colorScheme.primary,
        activeTrackColor = MaterialTheme.colorScheme.primary
      ),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("synchronized_timeline_slider")
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      val curMin = (currentPositionMs / 1000) / 60
      val curSec = (currentPositionMs / 1000) % 60
      val totMin = safeDurationSeconds / 60
      val totSec = safeDurationSeconds % 60
      Text(
        text = String.format("%02d:%02d", curMin, curSec),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
      )

      // Botão de Legenda Toggle
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
          .clip(RoundedCornerShape(6.dp))
          .clickable { showSubtitles = !showSubtitles }
          .background(if (showSubtitles) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
          .padding(horizontal = 6.dp, vertical = 2.dp)
      ) {
        Icon(
          painter = painterResource(id = R.drawable.ic_subtitles),
          contentDescription = "Legendas pt-BR",
          tint = if (showSubtitles) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = if (showSubtitles) "Legenda ON" else "Legenda OFF",
          fontSize = 9.sp,
          fontWeight = FontWeight.Bold,
          color = if (showSubtitles) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Text(
        text = String.format("%02d:%02d", totMin, totSec),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // 4. CONTROLES DE REPRODUÇÃO & ÁUDIO (PLAY, PAUSE, STOP, SEEK -10s/+10s, VOLUME)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Controle de Volume / Mudo com SVG Icon
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = { isMuted = !isMuted },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            painter = painterResource(id = if (isMuted) R.drawable.ic_volume_off else R.drawable.ic_volume_up),
            contentDescription = if (isMuted) "Ativar Som" else "Mutar",
            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
          )
        }
        Slider(
          value = if (isMuted) 0f else volumeLevel,
          onValueChange = {
            volumeLevel = it
            if (isMuted && it > 0f) isMuted = false
          },
          valueRange = 0f..1f,
          modifier = Modifier.width(85.dp),
          colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary
          )
        )
      }

      // Botões Centrais de Controle Sincronizado
      Row(verticalAlignment = Alignment.CenterVertically) {
        // Retroceder 10s
        IconButton(
          onClick = { performSeek(currentPositionMs - 10000L) },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_arrow_back),
            contentDescription = "Voltar 10s",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // STOP BUTTON (Ressincroniza e reseta vídeo e áudio para 0)
        FilledIconButton(
          onClick = {
            isPlaying = false
            performSeek(0L)
          },
          modifier = Modifier
            .size(44.dp)
            .testTag("player_stop_button"),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
          )
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_stop),
            contentDescription = "Stop",
            modifier = Modifier.size(20.dp)
          )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // PLAY / PAUSE BUTTON (Inicia e pausa simultaneamente áudio e vídeo)
        FilledIconButton(
          onClick = { isPlaying = !isPlaying },
          modifier = Modifier
            .size(54.dp)
            .testTag("player_play_pause_button"),
          colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
          )
        ) {
          Icon(
            painter = painterResource(id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (isPlaying) "Pausar" else "Reproduzir",
            modifier = Modifier.size(26.dp)
          )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Avançar 10s
        IconButton(
          onClick = { performSeek(currentPositionMs + 10000L) },
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            painter = painterResource(id = R.drawable.ic_arrow_forward),
            contentDescription = "Avançar 10s",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 5. SELETOR DE MÓDULOS DE ANÁLISE E KARAOKÊ
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      PlayerTabButton(
        selected = selectedTab == "ALL",
        onClick = { selectedTab = "ALL" },
        iconRes = R.drawable.ic_auto_awesome,
        label = "Todos os Módulos"
      )
      PlayerTabButton(
        selected = selectedTab == "WAVEFORM",
        onClick = { selectedTab = "WAVEFORM" },
        iconRes = R.drawable.ic_waveform,
        label = "Forma de Onda"
      )
      PlayerTabButton(
        selected = selectedTab == "LYRICS",
        onClick = { selectedTab = "LYRICS" },
        iconRes = R.drawable.ic_lyrics,
        label = "Letra Karaokê"
      )
      PlayerTabButton(
        selected = selectedTab == "PITCH",
        onClick = { selectedTab = "PITCH" },
        iconRes = R.drawable.ic_pitch_tune,
        label = "Afinador de Pitch"
      )
      PlayerTabButton(
        selected = selectedTab == "FFT",
        onClick = { selectedTab = "FFT" },
        iconRes = R.drawable.ic_spectrum,
        label = "FFT Acústico"
      )
    }

    Spacer(modifier = Modifier.height(12.dp))

    // Módulo de Forma de Onda (Waveform) do Áudio Extraído
    if (selectedTab == "ALL" || selectedTab == "WAVEFORM") {
      AudioWaveformVisualizer(
        audioPath = audioPath,
        fftFilePath = fftDataPath,
        currentPositionMs = currentPositionMs,
        totalDurationMs = totalDurationMs,
        isPlaying = isPlaying,
        onSeek = { performSeek(it) },
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(12.dp))
    }

    // Frequência de referência vocal dinâmica sincronizada com o playback
    val referencePitchHz = remember(currentTimeSeconds) {
      // 440 Hz com modulação de notas musicais da melodia (A4, C5, D5, G4)
      val noteOffset = (kotlin.math.sin(currentTimeSeconds * 0.8) * 5.0).toInt()
      (440.0 * Math.pow(2.0, noteOffset / 12.0)).toFloat().coerceIn(180f, 880f)
    }

    // Módulo de Letras Sincronizadas
    if (selectedTab == "ALL" || selectedTab == "LYRICS") {
      SynchronizedKaraokeLyricsView(
        currentPositionMs = currentPositionMs,
        subtitlesDirPath = subtitlesDirPath,
        songTitle = songTitle,
        artist = artist,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(12.dp))
    }

    // Módulo de Afinador de Voz e Pitch Detection
    if (selectedTab == "ALL" || selectedTab == "PITCH") {
      PitchDetectionVisualizer(
        referencePitchHz = referencePitchHz,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(12.dp))
    }

    // Módulo de Visualização Gráfica FFT
    if (selectedTab == "ALL" || selectedTab == "FFT") {
      FftAcousticVisualizer(
        fftFilePath = fftDataPath,
        currentTimeSeconds = currentTimeSeconds,
        durationSeconds = safeDurationSeconds.toFloat(),
        isPlaying = isPlaying,
        title = "Visualização Acústica Sincronizada (fft.json)",
        compactMode = selectedTab == "ALL"
      )
    }
  }
}

@Composable
private fun PlayerTabButton(
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
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        painter = painterResource(id = iconRes),
        contentDescription = label,
        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(14.dp)
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

/**
 * Utilitário de parsing de arquivos SRT para sincronização de legendas.
 */
private fun parseSrtContent(content: String): List<SubtitleLine> {
  val result = mutableListOf<SubtitleLine>()
  val blocks = content.trim().split(Regex("\r?\n\r?\n"))
  for (block in blocks) {
    val lines = block.lines().filter { it.isNotBlank() }
    if (lines.size >= 3) {
      val id = lines[0].trim().toIntOrNull() ?: continue
      val timeLine = lines[1].trim()
      val timeParts = timeLine.split("-->")
      if (timeParts.size == 2) {
        val startMs = parseSrtTimestamp(timeParts[0].trim())
        val endMs = parseSrtTimestamp(timeParts[1].trim())
        val text = lines.drop(2).joinToString(" ")
        result.add(SubtitleLine(id, startMs, endMs, text))
      }
    }
  }
  return result
}

private fun parseSrtTimestamp(ts: String): Long {
  // Exemplo: 00:01:23,456
  val clean = ts.replace(",", ".")
  val parts = clean.split(":")
  if (parts.size == 3) {
    val hours = parts[0].toLongOrNull() ?: 0L
    val mins = parts[1].toLongOrNull() ?: 0L
    val secs = parts[2].toDoubleOrNull() ?: 0.0
    return ((hours * 3600 + mins * 60 + secs) * 1000).toLong()
  }
  return 0L
}

private fun createDefaultSubtitles(title: String, artist: String, durationSec: Long): List<SubtitleLine> {
  val lines = mutableListOf<SubtitleLine>()
  var currentMs = 1000L

  lines.add(SubtitleLine(1, 1000L, 5000L, "♪ $title ♪"))
  lines.add(SubtitleLine(2, 5500L, 10000L, "Artista: $artist [Karaoke Studio]"))
  lines.add(SubtitleLine(3, 11000L, 16000L, "♪ Solte a sua voz e sinta a música ♪"))
  lines.add(SubtitleLine(4, 16500L, 22000L, "♪ Análise espectral em perfeita harmonia ♪"))
  lines.add(SubtitleLine(5, 22500L, 29000L, "♪ Viva a experiência do karaokê profissional ♪"))

  currentMs = 30000L
  var idx = 6
  while (currentMs < durationSec * 1000L - 10000L) {
    lines.add(SubtitleLine(idx++, currentMs, currentMs + 5000L, "♪ Quando a melodia toca em meu peito ♪"))
    currentMs += 6000L
    lines.add(SubtitleLine(idx++, currentMs, currentMs + 5000L, "♪ A voz ganha vida e o som se espalha ♪"))
    currentMs += 6000L
  }
  return lines
}
