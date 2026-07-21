package com.example.ui.viewmodel

import com.example.data.models.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueriesTest {

    private fun song(
        id: String,
        title: String,
        artist: String = "Artist",
        album: String = "Album",
        durationMs: Long = 0L,
        dateAdded: Long = 0L
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        path = "/music/$id.mp3",
        durationMs = durationMs,
        dateAdded = dateAdded
    )

    @Test
    fun `sortSongs orders by title case-insensitively`() {
        val songs = listOf(song("1", "banana"), song("2", "Apple"), song("3", "cherry"))
        val sorted = sortSongs(songs, SortCriteria.TITLE, SortOrder.ASCENDING)
        assertEquals(listOf("Apple", "banana", "cherry"), sorted.map { it.title })
    }

    @Test
    fun `sortSongs descending reverses order`() {
        val songs = listOf(song("1", "A"), song("2", "B"), song("3", "C"))
        val sorted = sortSongs(songs, SortCriteria.TITLE, SortOrder.DESCENDING)
        assertEquals(listOf("C", "B", "A"), sorted.map { it.title })
    }

    @Test
    fun `sortSongs by duration and date added`() {
        val songs = listOf(
            song("1", "X", durationMs = 300, dateAdded = 3),
            song("2", "Y", durationMs = 100, dateAdded = 1),
            song("3", "Z", durationMs = 200, dateAdded = 2)
        )
        assertEquals(listOf("2", "3", "1"), sortSongs(songs, SortCriteria.DURATION, SortOrder.ASCENDING).map { it.id })
        assertEquals(listOf("2", "3", "1"), sortSongs(songs, SortCriteria.DATE_ADDED, SortOrder.ASCENDING).map { it.id })
    }

    @Test
    fun `filterSongsByQuery blank query returns everything`() {
        val songs = listOf(song("1", "A"), song("2", "B"))
        assertEquals(songs, filterSongsByQuery(songs, ""))
        assertEquals(songs, filterSongsByQuery(songs, "   "))
    }

    @Test
    fun `filterSongsByQuery matches title artist or album case-insensitively`() {
        val songs = listOf(
            song("1", "Bohemian Rhapsody", artist = "Queen", album = "A Night at the Opera"),
            song("2", "Yesterday", artist = "The Beatles", album = "Help!")
        )
        assertEquals(listOf("1"), filterSongsByQuery(songs, "queen").map { it.id })
        assertEquals(listOf("1"), filterSongsByQuery(songs, "RHAPSODY").map { it.id })
        assertEquals(listOf("2"), filterSongsByQuery(songs, "help").map { it.id })
        assertEquals(emptyList<String>(), filterSongsByQuery(songs, "nonexistent").map { it.id })
    }

    @Test
    fun `parseExtinfArtistAndTitle splits artist and title on dash`() {
        val result = parseExtinfArtistAndTitle("#EXTINF:355,Queen - Bohemian Rhapsody")
        assertEquals("Queen" to "Bohemian Rhapsody", result)
    }

    @Test
    fun `parseExtinfArtistAndTitle falls back to Unknown Artist without a dash`() {
        val result = parseExtinfArtistAndTitle("#EXTINF:180,Some Track Name")
        assertEquals("Unknown Artist" to "Some Track Name", result)
    }

    @Test
    fun `parseExtinfArtistAndTitle returns null for non-EXTINF or malformed lines`() {
        assertNull(parseExtinfArtistAndTitle("/storage/emulated/0/Music/song.mp3"))
        assertNull(parseExtinfArtistAndTitle("#EXTINF:no-comma-here"))
        assertNull(parseExtinfArtistAndTitle("#PLAYLIST:My Playlist"))
    }
}
