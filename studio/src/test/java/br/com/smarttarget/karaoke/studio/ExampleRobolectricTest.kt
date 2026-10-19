package br.com.smarttarget.karaoke.studio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.smarttarget.karaoke.studio.engine.KaraokeSynchronizationManager
import br.com.smarttarget.karaoke.studio.engine.MicrophonePitchDetectorEngine
import br.com.smarttarget.karaoke.studio.engine.TuningStatus
import br.com.smarttarget.karaoke.studio.ui.components.FftData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Karaoke Studio", appName)
  }

  @Test
  fun `verify synthetic fft bands generation`() {
    val bands = FftData.generateSyntheticFrame(second = 5.0f, bands = 64)
    assertEquals(64, bands.size)
    bands.forEach { energy ->
      assertTrue("A energia FFT deve estar normalizada entre 0.0 e 1.0", energy in 0.0f..1.0f)
    }
  }

  @Test
  fun `verify fft data frame bounds`() {
    val data = FftData(
      guid = "test_guid",
      sampleRate = 44100,
      bandsCount = 64,
      framesCount = 2,
      frames = listOf(
        List(64) { 0.2f },
        List(64) { 0.8f }
      )
    )
    val frameStart = data.getFrameAt(0f, 100f)
    assertEquals(0.2f, frameStart[0], 0.01f)

    val frameEnd = data.getFrameAt(100f, 100f)
    assertEquals(0.8f, frameEnd[0], 0.01f)
  }

  @Test
  fun `verify pitch detector note conversion and tuning comparison`() {
    // 440 Hz = A4
    val noteA4 = MicrophonePitchDetectorEngine.hzToNoteName(440f)
    assertEquals("A4", noteA4)

    // 261.63 Hz = C4
    val noteC4 = MicrophonePitchDetectorEngine.hzToNoteName(261.63f)
    assertEquals("C4", noteC4)

    // Perfeito em afinação (440Hz vs 440Hz)
    val perfectResult = MicrophonePitchDetectorEngine.compareFrequencies(440f, 440f, isSinging = true)
    assertEquals(TuningStatus.PERFECT_IN_TUNE, perfectResult.tuningStatus)
    assertTrue("Cents offset deve ser 0", perfectResult.centsOffset in -1f..1f)

    // Fora do tom (440Hz vs 550Hz)
    val outResult = MicrophonePitchDetectorEngine.compareFrequencies(550f, 440f, isSinging = true)
    assertEquals(TuningStatus.OUT_OF_TUNE, outResult.tuningStatus)
  }

  @Test
  fun `verify karaoke lyrics synchronization manager parses and updates progress`() {
    val syncManager = KaraokeSynchronizationManager()
    val testSrt = """
1
00:00:01,000 --> 00:00:05,000
♪ Evidências ♪

2
00:00:06,000 --> 00:00:10,000
Quando eu digo que deixei de te amar
    """.trimIndent()

    syncManager.loadSubtitles(testSrt)
    assertEquals(2, syncManager.lines.size)

    // Posição no meio da primeira frase (3000ms = 3s)
    syncManager.updatePosition(3000L)
    val state = syncManager.syncState.value
    assertNotNull(state.activeLine)
    assertEquals("♪ Evidências ♪", state.activeLine?.fullText)
    assertTrue("Progresso deve estar próximo da metade", state.lineProgressRatio in 0.4f..0.6f)
  }
}
