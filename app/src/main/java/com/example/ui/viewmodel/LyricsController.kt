package com.example.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.lyrics.LyricLine
import com.example.data.lyrics.LyricsResult
import com.example.data.models.Song
import com.example.data.repository.LyricsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class LyricsUiState {
    object Idle : LyricsUiState()
    object Loading : LyricsUiState()
    data class Synced(val lines: List<LyricLine>) : LyricsUiState()
    data class PlainOnly(val text: String) : LyricsUiState()
    object NotFoundState : LyricsUiState()
    object OfflineUnavailable : LyricsUiState()
}

private const val FINE_TICKER_INTERVAL_MS = 120L

/**
 * Owns synced-lyrics resolution and the currently-active line, extracted out of MusicViewModel
 * as a facade delegate following the same pattern as [SettingsController]: MusicViewModel
 * forwards its public properties/functions for this domain here unchanged, so screen composables
 * never see this type.
 */
class LyricsController(
    private val repository: LyricsRepository,
    private val scope: CoroutineScope
) {
    var lyricsState by mutableStateOf<LyricsUiState>(LyricsUiState.Idle)
        private set

    var activeLineIndex by mutableStateOf(-1)
        private set

    private var currentLines: List<LyricLine> = emptyList()
    private var resolveJob: Job? = null
    private var fineTickerJob: Job? = null

    /** Call whenever the actually-playing song changes (including to null). Cancels any stale
     * in-flight resolution so a rapid song skip mid-fetch can't flash the previous song's lyrics
     * onto the new one. */
    fun onSongChanged(song: Song?) {
        resolveJob?.cancel()
        currentLines = emptyList()
        activeLineIndex = -1

        if (song == null) {
            lyricsState = LyricsUiState.Idle
            return
        }

        lyricsState = LyricsUiState.Loading
        resolveJob = scope.launch {
            val result = repository.resolveLyrics(song)
            lyricsState = when (result) {
                is LyricsResult.Synced -> {
                    currentLines = result.lines
                    LyricsUiState.Synced(result.lines)
                }
                is LyricsResult.Plain -> LyricsUiState.PlainOnly(result.text)
                LyricsResult.NotFound -> LyricsUiState.NotFoundState
                LyricsResult.Offline -> LyricsUiState.OfflineUnavailable
            }
        }
    }

    /**
     * Recomputes the active line from scratch via binary search rather than incrementing from
     * the previous index, so both normal forward playback and arbitrary seeks (backward or
     * forward) land on the correct line. Ties (lines sharing an identical timestamp) resolve to
     * the last one at that timestamp, since [com.example.data.lyrics.LrcParser] already
     * collapses duplicates that way.
     */
    fun onPositionChanged(positionMs: Long) {
        val lines = currentLines
        if (lines.isEmpty()) {
            activeLineIndex = -1
            return
        }
        var lo = 0
        var hi = lines.lastIndex
        var found = -1
        while (lo <= hi) {
            val mid = (lo + hi) / 2
            if (lines[mid].timestampMs <= positionMs) {
                found = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        activeLineIndex = found
    }

    /**
     * Starts (or stops) a fine-grained ticker driving [onPositionChanged] while a lyrics view is
     * visible, layered on top of MusicViewModel's existing coarser progress tracker rather than
     * replacing it, so other screens see no change in behavior or battery use.
     */
    fun setLyricsViewVisible(visible: Boolean, positionProvider: () -> Long) {
        fineTickerJob?.cancel()
        fineTickerJob = if (visible) {
            scope.launch {
                while (isActive) {
                    onPositionChanged(positionProvider())
                    delay(FINE_TICKER_INTERVAL_MS)
                }
            }
        } else {
            null
        }
    }
}
