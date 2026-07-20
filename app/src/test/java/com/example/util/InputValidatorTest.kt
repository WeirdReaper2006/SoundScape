package com.example.util

import com.example.util.InputValidator.ValidationResult
import org.junit.Assert.assertTrue
import org.junit.Test

class InputValidatorTest {

    private fun assertValid(result: ValidationResult) =
        assertTrue("expected Valid but was $result", result is ValidationResult.Valid)

    private fun assertInvalid(result: ValidationResult) =
        assertTrue("expected Invalid but was $result", result is ValidationResult.Invalid)

    @Test
    fun `validateName accepts normal names`() {
        assertValid(InputValidator.validateName("Jane Doe"))
        assertValid(InputValidator.validateName("O'Brien-Smith"))
    }

    @Test
    fun `validateName rejects too short or too long`() {
        assertInvalid(InputValidator.validateName("A"))
        assertInvalid(InputValidator.validateName("A".repeat(51)))
    }

    @Test
    fun `validateName rejects disallowed characters`() {
        assertInvalid(InputValidator.validateName("Robert<script>"))
        assertInvalid(InputValidator.validateName("../../etc/passwd"))
    }

    @Test
    fun `validateFolderSuffix rejects path traversal`() {
        assertInvalid(InputValidator.validateFolderSuffix("../secret"))
        assertInvalid(InputValidator.validateFolderSuffix("Music/../etc"))
        assertInvalid(InputValidator.validateFolderSuffix(".."))
    }

    @Test
    fun `validateFolderSuffix accepts nested folders`() {
        assertValid(InputValidator.validateFolderSuffix("my-folder_1"))
        assertValid(InputValidator.validateFolderSuffix("Music/Rock"))
        assertValid(InputValidator.validateFolderSuffix("Music/Rock/Deep"))
    }

    @Test
    fun `validateFolderSuffix rejects empty or malformed path segments`() {
        assertInvalid(InputValidator.validateFolderSuffix("Music//Rock"))
        assertInvalid(InputValidator.validateFolderSuffix("/Music"))
        assertInvalid(InputValidator.validateFolderSuffix("Music/"))
    }

    @Test
    fun `validateFolderSuffix accepts common special characters in folder names`() {
        assertValid(InputValidator.validateFolderSuffix("My Music"))
        assertValid(InputValidator.validateFolderSuffix("90's Hits"))
        assertValid(InputValidator.validateFolderSuffix("Podcasts & Audiobooks"))
        assertValid(InputValidator.validateFolderSuffix("Rock (2020s)"))
        assertValid(InputValidator.validateFolderSuffix("Café Playlist"))
    }

    @Test
    fun `validateFolderSuffix rejects filesystem-reserved characters`() {
        assertInvalid(InputValidator.validateFolderSuffix("Movies\\Backup"))
        assertInvalid(InputValidator.validateFolderSuffix("a:b"))
        assertInvalid(InputValidator.validateFolderSuffix("a*b"))
        assertInvalid(InputValidator.validateFolderSuffix("a?b"))
        assertInvalid(InputValidator.validateFolderSuffix("a\"b"))
        assertInvalid(InputValidator.validateFolderSuffix("a<b>"))
        assertInvalid(InputValidator.validateFolderSuffix("a|b"))
    }

    @Test
    fun `validatePlaylistName rejects filesystem-unsafe characters`() {
        assertInvalid(InputValidator.validatePlaylistName("My:Playlist"))
        assertInvalid(InputValidator.validatePlaylistName("a".repeat(101)))
        assertValid(InputValidator.validatePlaylistName("Road Trip 2026"))
    }

    @Test
    fun `validateMetadataField honors required flag`() {
        assertInvalid(InputValidator.validateMetadataField("", "Title", required = true))
        assertValid(InputValidator.validateMetadataField("", "Title", required = false))
        assertValid(InputValidator.validateMetadataField("Bohemian Rhapsody", "Title"))
    }

    @Test
    fun `validateSearchQuery accepts normal text and rejects control characters`() {
        assertValid(InputValidator.validateSearchQuery("bohemian rhapsody"))
        assertInvalid(InputValidator.validateSearchQuery("badquery"))
    }

    @Test
    fun `validateImportedMediaPath requires a recognized audio extension`() {
        assertValid(InputValidator.validateImportedMediaPath("/Music/song.mp3"))
        assertValid(InputValidator.validateImportedMediaPath("/Music/song.flac"))
        assertInvalid(InputValidator.validateImportedMediaPath("/Music/malware.exe"))
        assertInvalid(InputValidator.validateImportedMediaPath("/Music/noextension"))
    }

    @Test
    fun `validateImportedMediaPath rejects control characters and oversized paths`() {
        assertInvalid(InputValidator.validateImportedMediaPath("/Music/badpath.mp3"))
        assertInvalid(InputValidator.validateImportedMediaPath("/" + "a".repeat(1025) + ".mp3"))
    }
}
