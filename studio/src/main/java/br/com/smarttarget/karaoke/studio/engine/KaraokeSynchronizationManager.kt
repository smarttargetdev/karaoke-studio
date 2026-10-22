package br.com.smarttarget.karaoke.studio.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

/**
 * Sílaba de uma palavra com marcação temporal precisa para preenchimento de cor estilo karaokê.
 */
data class KaraokeSyllable(
  val text: String,
  val startMs: Long,
  val endMs: Long
) {
  val durationMs: Long get() = max(1L, endMs - startMs)
}

/**
 * Modelo de palavra com decomposição silábica para animação de wipe contínuo.
 */
data class KaraokeWord(
  val text: String,
  val startMs: Long,
  val endMs: Long,
  val syllables: List<KaraokeSyllable> = emptyList()
) {
  val durationMs: Long get() = max(1L, endMs - startMs)
}

/**
 * Linha de estrofe de karaokê.
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
 * Gerenciador de sincronização em tempo real para legendas de karaokê com decomposição silábica.
 */
class KaraokeSynchronizationManager {

  private val _lines = mutableListOf<KaraokeLine>()
  val lines: List<KaraokeLine> get() = _lines

  private val _syncState = MutableStateFlow(KaraokeSyncState())
  val syncState: StateFlow<KaraokeSyncState> = _syncState.asStateFlow()

  /**
   * Carrega e decompõe o arquivo SRT em linhas, palavras e sílabas.
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
      val syllables = splitWordIntoSyllables(tokens[i], wStart, wEnd)
      result.add(KaraokeWord(tokens[i], wStart, wEnd, syllables))
    }
    return result
  }

  /**
   * Divide uma palavra em sílabas fonéticas para transição de cor fluida.
   */
  private fun splitWordIntoSyllables(word: String, startMs: Long, endMs: Long): List<KaraokeSyllable> {
    val clean = word.trim()
    if (clean.length <= 3) {
      return listOf(KaraokeSyllable(clean, startMs, endMs))
    }

    // Regras heurísticas simples de separação de sílabas por vogais
    val vowels = setOf('a', 'e', 'i', 'o', 'u', 'á', 'é', 'í', 'ó', 'ú', 'â', 'ê', 'ô', 'ã', 'õ')
    val parts = mutableListOf<String>()
    var currentChunk = StringBuilder()

    for (ch in clean) {
      currentChunk.append(ch)
      if (vowels.contains(ch.lowercaseChar()) && currentChunk.length >= 2) {
        parts.add(currentChunk.toString())
        currentChunk = StringBuilder()
      }
    }
    if (currentChunk.isNotEmpty()) {
      if (parts.isNotEmpty()) {
        parts[parts.lastIndex] = parts.last() + currentChunk.toString()
      } else {
        parts.add(currentChunk.toString())
      }
    }

    val totalDuration = endMs - startMs
    val step = totalDuration / parts.size
    val syllables = mutableListOf<KaraokeSyllable>()

    for (i in parts.indices) {
      val sStart = startMs + (i * step)
      val sEnd = if (i == parts.lastIndex) endMs else sStart + step
      syllables.add(KaraokeSyllable(parts[i], sStart, sEnd))
    }
    return syllables
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
