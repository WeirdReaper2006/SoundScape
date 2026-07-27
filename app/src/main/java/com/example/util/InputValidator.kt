package com.example.util

/**
 * Centralized, allowlist-style input validation. Every schema here rejects
 * anything that doesn't match rather than sanitizing/escaping it - callers
 * must stop processing on [ValidationResult.Invalid] instead of stripping
 * offending characters and continuing.
 */
object InputValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    private val NAME_REGEX = Regex("^[\\p{L}\\p{N} '_-]+$")
    private val CONTROL_CHAR_REGEX = Regex("[\\p{Cntrl}]")
    // Same as CONTROL_CHAR_REGEX but keeps newlines - lyric text is legitimately multi-line,
    // unlike the single-line metadata fields CONTROL_CHAR_REGEX is designed for.
    private val LYRICS_CONTROL_CHAR_REGEX = Regex("[\\p{Cntrl}&&[^\n]]")
    private val FILESYSTEM_UNSAFE_REGEX = Regex("[\\\\/:*?\"<>|]")
    private val FOLDER_SEGMENT_UNSAFE_REGEX = Regex("[\\\\:*?\"<>|]")
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "m4b", "flac", "wav", "ogg", "oga", "aac", "wma", "opus"
    )

    private const val NAME_MIN_LENGTH = 2
    private const val NAME_MAX_LENGTH = 50
    private const val FOLDER_SUFFIX_MAX_LENGTH = 50
    private const val PLAYLIST_NAME_MAX_LENGTH = 100
    private const val METADATA_FIELD_MAX_LENGTH = 200
    private const val SEARCH_QUERY_MAX_LENGTH = 200
    private const val MEDIA_PATH_MAX_LENGTH = 1024
    private const val LYRICS_TEXT_MAX_LENGTH = 50_000

    /** Listener / profile display name. */
    fun validateName(value: String): ValidationResult {
        val trimmed = value.trim()
        if (trimmed.length < NAME_MIN_LENGTH || trimmed.length > NAME_MAX_LENGTH) {
            return ValidationResult.Invalid("Name must be between $NAME_MIN_LENGTH and $NAME_MAX_LENGTH characters.")
        }
        if (!NAME_REGEX.matches(trimmed)) {
            return ValidationResult.Invalid("Name may only contain letters, numbers, spaces, hyphens and underscores.")
        }
        return ValidationResult.Valid
    }

    /**
     * Custom scan-folder suffix, possibly nested (e.g. "Music/Rock"). Must never allow path
     * traversal - enforced explicitly via the "..' check below rather than by restricting the
     * character set, so real-world folder names (spaces, apostrophes, accents, etc.) still work.
     */
    fun validateFolderSuffix(value: String): ValidationResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > FOLDER_SUFFIX_MAX_LENGTH) {
            return ValidationResult.Invalid("Folder suffix must be between 1 and $FOLDER_SUFFIX_MAX_LENGTH characters.")
        }
        if (CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("Folder suffix contains unsupported control characters.")
        }
        if (trimmed.contains("..")) {
            return ValidationResult.Invalid("Folder suffix may not contain '..'.")
        }
        val segments = trimmed.split("/")
        if (segments.any { it.isBlank() }) {
            return ValidationResult.Invalid("Folder suffix may not contain empty path segments.")
        }
        if (segments.any { FOLDER_SEGMENT_UNSAFE_REGEX.containsMatchIn(it) }) {
            return ValidationResult.Invalid("Folder suffix may not contain \\ : * ? \" < > |")
        }
        return ValidationResult.Valid
    }

    /** Playlist name, used both as the DB record name and as a candidate export filename. */
    fun validatePlaylistName(value: String): ValidationResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > PLAYLIST_NAME_MAX_LENGTH) {
            return ValidationResult.Invalid("Playlist name must be between 1 and $PLAYLIST_NAME_MAX_LENGTH characters.")
        }
        if (CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("Playlist name contains unsupported control characters.")
        }
        if (FILESYSTEM_UNSAFE_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("Playlist name may not contain \\ / : * ? \" < > |")
        }
        return ValidationResult.Valid
    }

    /** Song title/artist/album metadata fields. [required] = false allows an empty value. */
    fun validateMetadataField(value: String, fieldLabel: String, required: Boolean = true): ValidationResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            return if (required) {
                ValidationResult.Invalid("$fieldLabel cannot be empty.")
            } else {
                ValidationResult.Valid
            }
        }
        if (trimmed.length > METADATA_FIELD_MAX_LENGTH) {
            return ValidationResult.Invalid("$fieldLabel must be $METADATA_FIELD_MAX_LENGTH characters or fewer.")
        }
        if (CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("$fieldLabel contains unsupported control characters.")
        }
        return ValidationResult.Valid
    }

    /**
     * Strips control characters (notably newlines) from metadata read from an untrusted source
     * (a song file's own embedded tags, indexed verbatim by MediaStore) and caps its length.
     * Unlike [validateMetadataField], this never rejects the song outright - it must still show
     * up in the library - but the raw value can't be trusted as a single line of plain text: a
     * crafted tag containing an embedded newline could otherwise inject a bogus extra line into
     * an exported M3U file (forging a fake additional playlist entry on re-import).
     */
    fun sanitizeUntrustedMetadataField(value: String): String {
        return CONTROL_CHAR_REGEX.replace(value, " ").trim().take(METADATA_FIELD_MAX_LENGTH)
    }

    /**
     * Same treatment as [sanitizeUntrustedMetadataField] but for lyric text pulled from an
     * embedded tag, a local .lrc sidecar file, or the LRCLIB API - all untrusted external input
     * rendered as UI text. Unlike metadata fields, lyrics are legitimately multi-line, so
     * newlines are preserved while other control characters are still stripped, and the length
     * cap is far larger to fit a full song's worth of text.
     */
    fun sanitizeUntrustedLyricsText(value: String): String {
        return LYRICS_CONTROL_CHAR_REGEX.replace(value, " ").trim().take(LYRICS_TEXT_MAX_LENGTH)
    }

    /** Free-text library/search query. */
    fun validateSearchQuery(value: String): ValidationResult {
        if (value.length > SEARCH_QUERY_MAX_LENGTH) {
            return ValidationResult.Invalid("Search query must be $SEARCH_QUERY_MAX_LENGTH characters or fewer.")
        }
        if (CONTROL_CHAR_REGEX.containsMatchIn(value)) {
            return ValidationResult.Invalid("Search query contains unsupported control characters.")
        }
        return ValidationResult.Valid
    }

    /**
     * A file path line imported from an untrusted M3U playlist file. Must look like a
     * plausible audio file path before it's allowed to be persisted and later opened as media.
     * Must never allow path traversal or relative paths - a crafted ".." entry or a bare
     * relative path could otherwise reference files entirely outside the music library.
     */
    fun validateImportedMediaPath(value: String): ValidationResult {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || trimmed.length > MEDIA_PATH_MAX_LENGTH) {
            return ValidationResult.Invalid("Media path must be between 1 and $MEDIA_PATH_MAX_LENGTH characters.")
        }
        if (CONTROL_CHAR_REGEX.containsMatchIn(trimmed)) {
            return ValidationResult.Invalid("Media path contains unsupported control characters.")
        }
        if (!trimmed.startsWith("/")) {
            return ValidationResult.Invalid("Media path must be an absolute path.")
        }
        if (trimmed.contains("..")) {
            return ValidationResult.Invalid("Media path may not contain '..'.")
        }
        val extension = trimmed.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension !in AUDIO_EXTENSIONS) {
            return ValidationResult.Invalid("Media path does not have a recognized audio file extension.")
        }
        return ValidationResult.Valid
    }
}
