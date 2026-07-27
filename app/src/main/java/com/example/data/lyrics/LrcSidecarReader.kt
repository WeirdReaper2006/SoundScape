package com.example.data.lyrics

import com.example.util.AppLogger
import com.example.util.InputValidator
import java.io.File

private const val TAG = "LrcSidecarReader"

/**
 * Looks for a same-basename `.lrc` file next to a song's audio file (e.g. `Song.mp3` ->
 * `Song.lrc` in the same directory). Only works for paths the app can already read - typically
 * pre-scoped-storage external paths or ones already granted via MediaStore access, same as
 * playback itself - so a missing/unreadable sidecar is treated as "not found", never a crash.
 */
object LrcSidecarReader {

    fun read(audioPath: String): List<LyricLine> {
        return try {
            val audioFile = File(audioPath)
            if (!audioFile.exists()) return emptyList()
            val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
            if (!lrcFile.exists() || !lrcFile.canRead()) return emptyList()
            val raw = InputValidator.sanitizeUntrustedLyricsText(lrcFile.readText())
            LrcParser.parse(raw)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to read local .lrc sidecar for $audioPath", e)
            emptyList()
        }
    }
}
