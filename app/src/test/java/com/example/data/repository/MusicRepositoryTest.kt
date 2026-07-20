package com.example.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicRepositoryTest {

    @Test
    fun `blank folder matches everything`() {
        assertTrue(pathMatchesFolder("/storage/emulated/0/Music/song.mp3", ""))
        assertTrue(pathMatchesFolder("/storage/emulated/0/Music/song.mp3", "   "))
    }

    @Test
    fun `exact folder match`() {
        assertTrue(pathMatchesFolder("/storage/emulated/0/Music/song.mp3", "/storage/emulated/0/Music"))
    }

    @Test
    fun `subfolder matches`() {
        assertTrue(pathMatchesFolder("/storage/emulated/0/Music/Rock/song.mp3", "/storage/emulated/0/Music"))
    }

    @Test
    fun `sibling folder sharing a name prefix does not match`() {
        // Regression test: "/Music" must not match "/MyMusicArchive" via a bare substring check.
        assertFalse(pathMatchesFolder("/storage/emulated/0/MyMusicArchive/song.mp3", "/storage/emulated/0/Music"))
    }

    @Test
    fun `trailing slash on folder is ignored`() {
        assertTrue(pathMatchesFolder("/storage/emulated/0/Music/song.mp3", "/storage/emulated/0/Music/"))
    }

    @Test
    fun `comparison is case-insensitive`() {
        assertTrue(pathMatchesFolder("/storage/emulated/0/MUSIC/song.mp3", "/storage/emulated/0/music"))
    }

    @Test
    fun `path outside folder does not match`() {
        assertFalse(pathMatchesFolder("/storage/emulated/0/Podcasts/ep.mp3", "/storage/emulated/0/Music"))
    }
}
