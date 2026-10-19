package br.com.smarttarget.karaoke.studio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

/**
 * Modelo de linha de legenda com decomposição em palavras/sílabas para sincronismo de Karaokê.
 */
data class KaraokeLine(
  val id: Int,
  val startMs: Long,
  val endMs: Long,
  val fullText: String,
  val words: List<KaraokeWord>
) {
  val durationMs: Long get() = max(1L, endMs - startMs)
}

data class KaraokeWord(
  val text: String,
  val startMs: Long,
  val endMs: Long
)

data class KaraokeSyncState(
  val currentPositionMs: Long = 0L,
  val currentLineIndex: Int = -1,
  val activeLine: KaraokeLine? = null,
  val nextLine: KaraokeLine? = null,
  val lineProgressRatio: Float = 0f, // 0.0f a 1.0f
  val currentWordIndex: Int = -1,
  val msUntilNextLine: Long = 0L
)

/**
 * Gerenciador de sincronização em tempo real para legendas de karaokê.
 */
class KaraokeSynchronizationManager {

  private val _lines = mutableListOf<KaraokeLine>()
  val lines: List<KaraokeLine> get() = _lines

  private val _syncState = MutableStateFlow(KaraokeSyncState())
  val syncState: StateFlow<KaraokeSyncState> = _syncState.asStateFlow()

  /**
   * Carrega e decompõe o arquivo SRT em linhas e palavras com marcação temporal.
   */
  fun loadSubtitles(srtContent: String) {
    _lines.clear()
    val rawBlocks = srtContent.trim().split(Regex("\r?\n\r?\n"))
    var index = 1

    for (block in rawBlocks) {
      val linesInBlock = block.lines().filter { it.isNotBlank() }
      if (linesInBlock.size >= 2) {
        val timeLine = linesInBlock.firstOrNull { it.contains("-->") } ?: continue
        val parts = timeLine.split("-->")
        if (parts.size == 2) {
          val startMs = parseTimestamp(parts[0].trim())
          val endMs = parseTimestamp(parts[1].trim())
          val textLines = linesInBlock.filter { !it.contains("-->") && it.toIntOrNull() == null }
          val fullText = textLines.joinToString(" ").trim()

          if (fullText.isNotEmpty() && endMs > startMs) {
            val words = splitIntoKaraokeWords(fullText, startMs, endMs)
            _lines.add(KaraokeLine(index++, startMs, endMs, fullText, words))
          }
        }
      }
    }
    updatePosition(0L)
  }

  /**
   * Atualiza a posição atual de reprodução (em milissegundos) e calcula o estado de sincronismo.
   */
  fun updatePosition(positionMs: Long) {
    if (_lines.isEmpty()) {
      _syncState.value = KaraokeSyncState(currentPositionMs = positionMs)
      return
    }

    val activeIndex = _lines.indexOfFirst { positionMs in it.startMs..it.endMs }
    val activeLine = if (activeIndex >= 0) _lines[activeIndex] else null
    val nextLine = _lines.firstOrNull { it.startMs > positionMs }

    val lineProgress = if (activeLine != null) {
      val elapsed = positionMs - activeLine.startMs
      (elapsed.toFloat() / activeLine.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val wordIndex = if (activeLine != null) {
      activeLine.words.indexOfFirst { positionMs in it.startMs..it.endMs }
    } else -1

    val countdown = if (nextLine != null && activeLine == null) {
      max(0L, nextLine.startMs - positionMs)
    } else 0L

    _syncState.value = KaraokeSyncState(
      currentPositionMs = positionMs,
      currentLineIndex = activeIndex,
      activeLine = activeLine,
      nextLine = nextLine,
      lineProgressRatio = lineProgress,
      currentWordIndex = wordIndex,
      msUntilNextLine = countdown
    )
  }

  private fun splitIntoKaraokeWords(fullText: String, startMs: Long, endMs: Long): List<KaraokeWord> {
    val tokens = fullText.split(Regex("\\s+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    val totalDuration = endMs - startMs
    val wordDuration = totalDuration / tokens.size
    val result = mutableListOf<KaraokeWord>()

    for (i in tokens.indices) {
      val wStart = startMs + (i * wordDuration)
      val wEnd = if (i == tokens.lastIndex) endMs else wStart + wordDuration
      result.add(KaraokeWord(tokens[i], wStart, wEnd))
    }
    return result
  }

  private fun parseTimestamp(ts: String): Long {
    val clean = ts.replace(",", ".")
    val parts = clean.split(":")
    if (parts.size == 3) {
      val h = parts[0].toLongOrNull() ?: 0L
      val m = parts[1].toLongOrNull() ?: 0L
      val s = parts[2].toDoubleOrNull() ?: 0.0
      return ((h * 3600 + m * 60 + s) * 1000).toLong()
    }
    return 0L
  }
}
