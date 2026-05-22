package com.example.data.models

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val durationMs: Long,
    val albumArtUri: String? = null,
    val isLocal: Boolean = true,
    val mimeType: String? = null,
    val dateAdded: Long = 0L
)
