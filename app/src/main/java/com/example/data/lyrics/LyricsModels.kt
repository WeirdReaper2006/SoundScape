package com.example.data.lyrics

data class LyricLine(val timestampMs: Long, val text: String)

enum class LyricsSource { EMBEDDED, LOCAL_LRC, CACHED, LRCLIB }

sealed class LyricsResult {
    data class Synced(val lines: List<LyricLine>, val source: LyricsSource) : LyricsResult()
    data class Plain(val text: String, val source: LyricsSource) : LyricsResult()
    object NotFound : LyricsResult()
    /** LRCLIB was the only untried source and there's no network - distinct from a confirmed NotFound. */
    object Offline : LyricsResult()
}
