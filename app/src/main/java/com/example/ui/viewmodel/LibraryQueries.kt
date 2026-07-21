package com.example.ui.viewmodel

import com.example.data.models.Song

/** Pure library-query helpers extracted out of MusicViewModel purely so they're unit-testable. */

internal fun sortSongs(list: List<Song>, criteria: SortCriteria, order: SortOrder): List<Song> {
    val sorted = when (criteria) {
        SortCriteria.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        SortCriteria.ARTIST -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
        SortCriteria.DURATION -> list.sortedBy { it.durationMs }
        SortCriteria.DATE_ADDED -> list.sortedBy { it.dateAdded }
    }
    return if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
}

internal fun filterSongsByQuery(songs: List<Song>, query: String): List<Song> {
    if (query.isBlank()) return songs
    return songs.filter {
        it.title.contains(query, ignoreCase = true) ||
            it.artist.contains(query, ignoreCase = true) ||
            it.album.contains(query, ignoreCase = true)
    }
}

/**
 * Parses an M3U "#EXTINF:<duration>,<artist> - <title>" line into (artist, title). Returns null
 * for anything that isn't a well-formed EXTINF line, matching the "leave currentTitle/currentArtist
 * unchanged" behavior importPlaylistFromM3U previously implemented inline.
 */
internal fun parseExtinfArtistAndTitle(line: String): Pair<String, String>? {
    val trimmed = line.trim()
    if (!trimmed.startsWith("#EXTINF:")) return null
    val parts = trimmed.substringAfter("#EXTINF:").split(",", limit = 2)
    if (parts.size != 2) return null
    val info = parts[1]
    val dashIndex = info.indexOf(" - ")
    return if (dashIndex != -1) {
        val artist = info.substring(0, dashIndex).trim()
        val title = info.substring(dashIndex + 3).trim()
        artist to title
    } else {
        "Unknown Artist" to info.trim()
    }
}
