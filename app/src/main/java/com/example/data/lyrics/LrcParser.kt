package com.example.data.lyrics

/**
 * Hand-rolled .lrc parser shared by the local sidecar-file path and the LRCLIB-fetched-text
 * path. LRC in the wild is inconsistent (out-of-order lines, duplicate timestamps for repeated
 * choruses, stray metadata tags, malformed lines) so this is deliberately lenient: unparsable
 * lines are skipped rather than aborting the whole file.
 */
object LrcParser {

    private val TIMESTAMP_TAG_REGEX = Regex("^\\[(\\d{1,2}):(\\d{2})(?:[.:](\\d{1,3}))?\\]")
    private val OFFSET_REGEX = Regex("\\[offset:\\s*(-?\\d+)\\]", RegexOption.IGNORE_CASE)
    private val META_TAG_REGEX = Regex("^\\[(ar|ti|al|by|offset|length|re|ve):[^\\]]*\\]$", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<LyricLine> {
        val offsetMs = OFFSET_REGEX.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val entries = mutableListOf<LyricLine>()
        raw.lineSequence().forEach { rawLine ->
            val line = rawLine.trimEnd('\r').trim()
            if (line.isEmpty() || META_TAG_REGEX.matches(line)) return@forEach

            val timestampsMs = mutableListOf<Long>()
            var remaining = line
            while (true) {
                val match = TIMESTAMP_TAG_REGEX.find(remaining) ?: break
                val minutes = match.groupValues[1].toLongOrNull() ?: break
                val seconds = match.groupValues[2].toLongOrNull() ?: break
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100
                    2 -> fraction.toLong() * 10
                    else -> fraction.take(3).toLong()
                }
                timestampsMs.add(minutes * 60_000L + seconds * 1000L + fractionMs)
                remaining = remaining.substring(match.value.length)
            }
            if (timestampsMs.isEmpty()) return@forEach

            val text = remaining.trim()
            timestampsMs.forEach { ts ->
                entries.add(LyricLine((ts + offsetMs).coerceAtLeast(0L), text))
            }
        }

        if (entries.isEmpty()) return emptyList()

        // Stable sort preserves original order among equal timestamps; folding left-to-right
        // then means the last line at a given timestamp (in file order) wins, which is the
        // deterministic "last wins" rule for duplicate-timestamp lines.
        val sorted = entries.sortedBy { it.timestampMs }
        val deduped = mutableListOf<LyricLine>()
        for (entry in sorted) {
            if (deduped.isNotEmpty() && deduped.last().timestampMs == entry.timestampMs) {
                deduped[deduped.lastIndex] = entry
            } else {
                deduped.add(entry)
            }
        }
        return deduped
    }
}
