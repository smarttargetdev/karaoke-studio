package br.com.smarttarget.karaoke.studio.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Resultado da comparação de afinação entre a voz do usuário e a referência.
 */
data class PitchComparisonResult(
  val micFrequencyHz: Float,
  val referenceFrequencyHz: Float,
  val micNoteName: String,
  val referenceNoteName: String,
  val centsOffset: Float, // -100 cents (bemol) a +100 cents (sustenido)
  val tuningStatus: TuningStatus,
  val scorePoints: Int,
  val isSinging: Boolean
)

enum class TuningStatus {
  SILENT,
  PERFECT_IN_TUNE, // Dentro de +/- 20 cents
  SLIGHTLY_SHARP,  // +20 a +50 cents
  SLIGHTLY_FLAT,   // -20 a -50 cents
  OUT_OF_TUNE      // > 50 cents
}

/**
 * Motor de detecção de Pitch em tempo real via microfone usando Autocorrelação com
 * interpolação parabólica sub-cent.
 */
class MicrophonePitchDetectorEngine {

  private val _pitchResult = MutableStateFlow(
    PitchComparisonResult(
      micFrequencyHz = 0f,
      referenceFrequencyHz = 440f,
      micNoteName = "--",
      referenceNoteName = "A4",
      centsOffset = 0f,
      tuningStatus = TuningStatus.SILENT,
      scorePoints = 0,
      isSinging = false
    )
  )
  val pitchResult: StateFlow<PitchComparisonResult> = _pitchResult.asStateFlow()

  private val _isRecording = MutableStateFlow(false)
  val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

  private val _totalScore = MutableStateFlow(0)
  val totalScore: StateFlow<Int> = _totalScore.asStateFlow()

  private val _streakCount = MutableStateFlow(0)
  val streakCount: StateFlow<Int> = _streakCount.asStateFlow()

  private var recordingJob: Job? = null
  private var audioRecord: AudioRecord? = null

  private val sampleRate = 44100
  private val bufferSize = 2048

  @SuppressLint("MissingPermission")
  fun startListening(
    scope: CoroutineScope,
    referencePitchProvider: () -> Float
  ) {
    if (_isRecording.value) return

    val minBuf = AudioRecord.getMinBufferSize(
      sampleRate,
      AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT
    )
    val actualBufSize = max(bufferSize * 2, minBuf)

    try {
      val record = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        actualBufSize
      )

      if (record.state == AudioRecord.STATE_INITIALIZED) {
        audioRecord = record
        record.startRecording()
        _isRecording.value = true

        recordingJob = scope.launch(Dispatchers.Default) {
          val audioBuffer = ShortArray(bufferSize)

          while (isActive && _isRecording.value) {
            val readCount = record.read(audioBuffer, 0, bufferSize)
            if (readCount > 0) {
              val refPitch = referencePitchProvider()
              processAudioBuffer(audioBuffer, readCount, refPitch)
            }
          }
        }
        return
      }
    } catch (_: Exception) {
      // Falha ao abrir AudioRecord (ex: permissão ou emulador sem mic físico)
    }

    // Se o hardware não puder ser inicializado, inicia em modo de simulação acústica
    startSimulation(scope, referencePitchProvider)
  }

  fun startSimulation(
    scope: CoroutineScope,
    referencePitchProvider: () -> Float
  ) {
    if (_isRecording.value) return
    _isRecording.value = true

    recordingJob = scope.launch(Dispatchers.Default) {
      var phase = 0.0
      while (isActive && _isRecording.value) {
        kotlinx.coroutines.delay(60)
        phase += 0.2
        val refPitch = referencePitchProvider()
        // Variação suave ao redor da referência simulando um cantor afinando
        val simMicHz = refPitch + (sin(phase) * 14.0).toFloat()
        val result = compareFrequencies(simMicHz, refPitch, true)
        updateScore(result)
        _pitchResult.value = result
      }
    }
  }

  fun stopListening() {
    _isRecording.value = false
    recordingJob?.cancel()
    recordingJob = null

    try {
      audioRecord?.stop()
      audioRecord?.release()
    } catch (_: Exception) {}
    audioRecord = null

    _pitchResult.value = _pitchResult.value.copy(
      tuningStatus = TuningStatus.SILENT,
      isSinging = false
    )
  }

  fun resetScore() {
    _totalScore.value = 0
    _streakCount.value = 0
  }

  private fun processAudioBuffer(buffer: ShortArray, size: Int, referencePitchHz: Float) {
    // 1. Cálculo de RMS (Energia / Volume do microfone)
    var sumSquares = 0.0
    for (i in 0 until size) {
      val norm = buffer[i] / 32768.0
      sumSquares += norm * norm
    }
    val rms = sqrt(sumSquares / size).toFloat()

    // Limiar de silêncio (elimina ruído ambiente)
    if (rms < 0.02f) {
      _pitchResult.value = PitchComparisonResult(
        micFrequencyHz = 0f,
        referenceFrequencyHz = referencePitchHz,
        micNoteName = "--",
        referenceNoteName = hzToNoteName(referencePitchHz),
        centsOffset = 0f,
        tuningStatus = TuningStatus.SILENT,
        scorePoints = 0,
        isSinging = false
      )
      return
    }

    // 2. Detecção de Pitch por Autocorrelação com Interpolação Parabólica
    val micPitchHz = detectPitchAutocorrelation(buffer, size, sampleRate)

    if (micPitchHz <= 50f || micPitchHz > 1200f) {
      // Fora do alcance vocal humano
      _pitchResult.value = _pitchResult.value.copy(
        tuningStatus = TuningStatus.SILENT,
        isSinging = false
      )
      return
    }

    val result = compareFrequencies(micPitchHz, referencePitchHz, true)
    updateScore(result)
    _pitchResult.value = result
  }

  private fun updateScore(result: PitchComparisonResult) {
    if (!result.isSinging) return
    when (result.tuningStatus) {
      TuningStatus.PERFECT_IN_TUNE -> {
        _streakCount.value += 1
        val multiplier = min(4, 1 + (_streakCount.value / 10))
        _totalScore.value += 100 * multiplier
      }
      TuningStatus.SLIGHTLY_SHARP, TuningStatus.SLIGHTLY_FLAT -> {
        _totalScore.value += 50
      }
      TuningStatus.OUT_OF_TUNE -> {
        _streakCount.value = 0
        _totalScore.value += 10
      }
      TuningStatus.SILENT -> {}
    }
  }

  companion object {
    /**
     * Calcula o Pitch usando o algoritmo de autocorrelação com interpolação de Lagrange/parábola.
     */
    fun detectPitchAutocorrelation(buffer: ShortArray, size: Int, sRate: Int): Float {
      val minHz = 65 // C2
      val maxHz = 1050 // C6
      val minLag = sRate / maxHz
      val maxLag = min(size / 2, sRate / minHz)

      val floatBuffer = FloatArray(size) { buffer[it].toFloat() }

      var bestLag = -1
      var maxCorr = -1f

      // Pula a crista zero
      var lag = minLag
      while (lag < maxLag && floatBuffer[lag] > 0) {
        lag++
      }

      while (lag < maxLag) {
        var sum = 0f
        for (i in 0 until (size - lag)) {
          sum += floatBuffer[i] * floatBuffer[i + lag]
        }
        if (sum > maxCorr) {
          maxCorr = sum
          bestLag = lag
        }
        lag++
      }

      if (bestLag <= 0 || bestLag >= maxLag - 1) return 0f

      // Interpolação parabólica sub-amostral para precisão extrema de afinação
      val s1 = getAutocorrValue(floatBuffer, size, bestLag - 1)
      val s2 = maxCorr
      val s3 = getAutocorrValue(floatBuffer, size, bestLag + 1)

      val delta = (s1 - s3) / (2f * (s1 - 2f * s2 + s3 + 0.00001f))
      val refinedLag = bestLag.toFloat() + delta.coerceIn(-1f, 1f)

      return if (refinedLag > 0f) sRate / refinedLag else 0f
    }

    private fun getAutocorrValue(buffer: FloatArray, size: Int, lag: Int): Float {
      var sum = 0f
      for (i in 0 until (size - lag)) {
        sum += buffer[i] * buffer[i + lag]
      }
      return sum
    }

    /**
     * Compara a frequência do microfone com a referência de frequência da música.
     */
    fun compareFrequencies(micHz: Float, refHz: Float, isSinging: Boolean): PitchComparisonResult {
      if (!isSinging || micHz <= 0f) {
        return PitchComparisonResult(
          micFrequencyHz = 0f,
          referenceFrequencyHz = refHz,
          micNoteName = "--",
          referenceNoteName = hzToNoteName(refHz),
          centsOffset = 0f,
          tuningStatus = TuningStatus.SILENT,
          scorePoints = 0,
          isSinging = false
        )
      }

      // Calcula diferença de afinação em cents: 1200 * log2(f1 / f2)
      // Ajusta oitavas para permitir canto em falsete ou uma oitava abaixo sem penalização
      var normalizedRef = refHz
      while (micHz > normalizedRef * 1.5f && normalizedRef < 1000f) normalizedRef *= 2f
      while (micHz < normalizedRef * 0.75f && normalizedRef > 80f) normalizedRef /= 2f

      val centsDiff = (1200.0 * log2(micHz.toDouble() / normalizedRef.toDouble())).toFloat()
        .coerceIn(-100f, 100f)

      val status = when {
        abs(centsDiff) <= 20f -> TuningStatus.PERFECT_IN_TUNE
        centsDiff > 20f && centsDiff <= 50f -> TuningStatus.SLIGHTLY_SHARP
        centsDiff < -20f && centsDiff >= -50f -> TuningStatus.SLIGHTLY_FLAT
        else -> TuningStatus.OUT_OF_TUNE
      }

      return PitchComparisonResult(
        micFrequencyHz = micHz,
        referenceFrequencyHz = refHz,
        micNoteName = hzToNoteName(micHz),
        referenceNoteName = hzToNoteName(refHz),
        centsOffset = centsDiff,
        tuningStatus = status,
        scorePoints = if (status == TuningStatus.PERFECT_IN_TUNE) 100 else 50,
        isSinging = true
      )
    }

    fun hzToNoteName(freqHz: Float): String {
      if (freqHz <= 20f) return "--"
      val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
      val midi = (69.0 + 12.0 * log2(freqHz.toDouble() / 440.0)).roundToInt()
      val noteIndex = ((midi % 12) + 12) % 12
      val octave = (midi / 12) - 1
      return "${noteNames[noteIndex]}$octave"
    }
  }
}
