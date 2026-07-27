package com.example.data.repository

import android.content.Context
import com.example.data.db.LyricsEntity
import com.example.data.db.MusicDao
import com.example.data.lyrics.EmbeddedLyricsReader
import com.example.data.lyrics.LrcLibClient
import com.example.data.lyrics.LrcParser
import com.example.data.lyrics.LrcSidecarReader
import com.example.data.lyrics.LyricsNetworkException
import com.example.data.lyrics.LyricsOfflineException
import com.example.data.lyrics.LyricsResult
import com.example.data.lyrics.LyricsSource
import com.example.data.models.Song
import com.example.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "LyricsRepository"

// How long a confirmed "no lyrics on LRCLIB" cache entry is trusted before retrying the network,
// in case LRCLIB's own database grows the entry later.
private const val NOT_FOUND_CACHE_TTL_MS = 30L * 24 * 60 * 60 * 1000 // 30 days

/**
 * Resolves lyrics for a song in priority order - embedded tag, local .lrc sidecar, cached DB
 * entry, LRCLIB network fetch (cached after first success) - mirroring [MusicRepository]'s shape.
 */
class LyricsRepository(
    private val context: Context,
    private val musicDao: MusicDao,
    private val lrcLibClient: LrcLibClient = LrcLibClient()
) {

    suspend fun resolveLyrics(song: Song): LyricsResult = withContext(Dispatchers.IO) {
        try {
            val embedded = EmbeddedLyricsReader.read(song.path, song.mimeType)
            if (embedded is LyricsResult.Synced || (embedded is LyricsResult.Plain && embedded.text.isNotBlank())) {
                return@withContext embedded
            }

            val sidecarLines = LrcSidecarReader.read(song.path)
            if (sidecarLines.isNotEmpty()) {
                return@withContext LyricsResult.Synced(sidecarLines, LyricsSource.LOCAL_LRC)
            }

            val cached = musicDao.getLyrics(song.id)
            if (cached != null && !isStaleNotFound(cached)) {
                return@withContext cached.toLyricsResult()
            }

            fetchFromLrcLibAndCache(song)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to resolve lyrics for song ${song.id}", e)
            LyricsResult.NotFound
        }
    }

    private fun isStaleNotFound(entity: LyricsEntity): Boolean {
        if (entity.source != "NOT_FOUND") return false
        return System.currentTimeMillis() - entity.fetchedAt > NOT_FOUND_CACHE_TTL_MS
    }

    private suspend fun fetchFromLrcLibAndCache(song: Song): LyricsResult {
        val result = try {
            lrcLibClient.fetch(
                context = context,
                artist = song.artist,
                title = song.title,
                album = song.album,
                durationSec = (song.durationMs / 1000).toInt()
            )
        } catch (e: LyricsOfflineException) {
            return LyricsResult.Offline
        } catch (e: LyricsNetworkException) {
            AppLogger.w(TAG, "LRCLIB fetch failed for song ${song.id}, not caching a negative result", e)
            return LyricsResult.NotFound
        }

        val entity = when (result) {
            is LyricsResult.Synced -> LyricsEntity(
                songId = song.id,
                source = "LRCLIB",
                syncedLrcText = lyricsToLrcText(result),
                plainText = null,
                fetchedAt = System.currentTimeMillis()
            )
            is LyricsResult.Plain -> LyricsEntity(
                songId = song.id,
                source = "LRCLIB",
                syncedLrcText = null,
                plainText = result.text,
                fetchedAt = System.currentTimeMillis()
            )
            LyricsResult.NotFound -> LyricsEntity(
                songId = song.id,
                source = "NOT_FOUND",
                syncedLrcText = null,
                plainText = null,
                fetchedAt = System.currentTimeMillis()
            )
            LyricsResult.Offline -> null // shouldn't happen (offline is thrown, not returned), but never cache it
        }
        entity?.let { musicDao.upsertLyrics(it) }
        return result
    }

    // LRCLIB's synced lines are already produced by LrcParser from raw LRC text; re-serialize
    // them back to `[mm:ss.xx]text` lines so the cache can store (and later re-parse) raw LRC
    // text the same way the local-sidecar path does, keeping LrcParser the single source of
    // truth for LRC<->lines conversion.
    private fun lyricsToLrcText(result: LyricsResult.Synced): String {
        return result.lines.joinToString("\n") { line ->
            val totalMs = line.timestampMs
            val minutes = totalMs / 60_000
            val seconds = (totalMs % 60_000) / 1000
            val centis = (totalMs % 1000) / 10
            "[%02d:%02d.%02d]%s".format(minutes, seconds, centis, line.text)
        }
    }

    private fun LyricsEntity.toLyricsResult(): LyricsResult {
        return when (source) {
            "NOT_FOUND" -> LyricsResult.NotFound
            "LRCLIB" -> {
                val synced = syncedLrcText?.let { LrcParser.parse(it) }
                when {
                    !synced.isNullOrEmpty() -> LyricsResult.Synced(synced, LyricsSource.CACHED)
                    !plainText.isNullOrBlank() -> LyricsResult.Plain(plainText, LyricsSource.CACHED)
                    else -> LyricsResult.NotFound
                }
            }
            else -> LyricsResult.NotFound
        }
    }
}
