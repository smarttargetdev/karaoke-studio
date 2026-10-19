package br.com.smarttarget.karaoke.studio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import br.com.smarttarget.karaoke.studio.ui.components.FftData
import org.junit.Assert.assertEquals
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
}
