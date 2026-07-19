package com.example.data.repository

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.*
import com.example.data.models.Song
import com.example.util.AppLogger
import com.example.util.UserFacingException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private const val TAG = "MusicRepository"

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {

    suspend fun getLocalSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songList = mutableListOf<Song>()
        
        // Retrieve scan location filter from SharedPreferences securely
        val prefs = context.getSharedPreferences("spotify_clone_prefs", Context.MODE_PRIVATE)
        val selectedLocation = prefs.getString("music_path", "")?.trim() ?: ""
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.DATE_ADDED
        )

        val selection = "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} LIKE 'audio/%' OR " +
                "${MediaStore.Audio.Media.MIME_TYPE} = 'application/ogg' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.opus' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.ogg' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.m4a' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.wav' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.mp3' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.flac' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.aac' OR " +
                "${MediaStore.Audio.Media.DATA} LIKE '%.webm') AND " +
                "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND " +
                "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
                "${MediaStore.Audio.Media.IS_ALARM} = 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val title = cursor.getString(titleColumn) ?: "Unknown Track"
                    val artist = cursor.getString(artistColumn) ?: "Unknown Artist"
                    val album = cursor.getString(albumColumn) ?: "Unknown Album"
                    val duration = cursor.getLong(durationColumn)
                    val path = cursor.getString(pathColumn) ?: ""
                    val mime = cursor.getString(mimeColumn)
                    val dateAdded = cursor.getLong(dateAddedColumn)

                    // Secure backend validation of paths: Ignore if does not match selected scan folder
                    if (selectedLocation.isNotBlank() && !path.contains(selectedLocation, ignoreCase = true)) {
                        continue
                    }

                    val contentUri: Uri = ContentUris.withAppendedId(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    // Get embedded artwork Uri via media store album ID or fallback
                    val albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                    val artworkUri = if (albumIdColumn != -1) {
                        val albumId = cursor.getLong(albumIdColumn)
                        Uri.parse("content://media/external/audio/albumart/$albumId").toString()
                    } else {
                        null
                    }

                    songList.add(
                        Song(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            path = path.ifEmpty { contentUri.toString() },
                            durationMs = duration,
                            albumArtUri = artworkUri,
                            isLocal = true,
                            mimeType = mime,
                            dateAdded = dateAdded,
                            genre = null
                        )
                    )
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query local songs from MediaStore", e)
        }

        songList
    }

    // Room DB integrations
    val favoriteSongs: Flow<List<Song>> = musicDao.getFavorites().map { entities ->
        entities.map { entity ->
            Song(
                id = entity.songId,
                title = entity.title,
                artist = entity.artist,
                album = entity.album,
                path = entity.path,
                durationMs = entity.durationMs,
                albumArtUri = entity.albumArtUri,
                isLocal = entity.isLocal
            )
        }
    }

    suspend fun addFavorite(song: Song) = withContext(Dispatchers.IO) {
        try {
            musicDao.insertFavorite(
                FavoriteEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    path = song.path,
                    durationMs = song.durationMs,
                    albumArtUri = song.albumArtUri,
                    isLocal = song.isLocal
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to add favorite for song ${song.id}", e)
            throw UserFacingException("Could not add this song to favorites. Please try again.")
        }
    }

    suspend fun removeFavorite(songId: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.deleteFavorite(songId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to remove favorite for song $songId", e)
            throw UserFacingException("Could not remove this song from favorites. Please try again.")
        }
    }

    fun isFavorite(songId: String): Flow<Boolean> {
        return musicDao.isFavorite(songId)
    }

    val playlists: Flow<List<PlaylistEntity>> = musicDao.getPlaylists()

    suspend fun createPlaylist(name: String): Long = withContext(Dispatchers.IO) {
        try {
            musicDao.insertPlaylist(PlaylistEntity(name = name))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create playlist '$name'", e)
            throw UserFacingException("Could not create the playlist. Please try again.")
        }
    }

    suspend fun deletePlaylist(playlistId: Long) = withContext(Dispatchers.IO) {
        try {
            musicDao.deletePlaylist(playlistId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete playlist $playlistId", e)
            throw UserFacingException("Could not delete the playlist. Please try again.")
        }
    }

    suspend fun addSongToPlaylist(playlistId: Long, song: Song) = withContext(Dispatchers.IO) {
        try {
            musicDao.insertPlaylistSong(
                PlaylistSongCrossRef(
                    playlistId = playlistId,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    path = song.path,
                    durationMs = song.durationMs,
                    albumArtUri = song.albumArtUri,
                    isLocal = song.isLocal
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to add song ${song.id} to playlist $playlistId", e)
            throw UserFacingException("Could not add this song to the playlist. Please try again.")
        }
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.deletePlaylistSong(playlistId, songId)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to remove song $songId from playlist $playlistId", e)
            throw UserFacingException("Could not remove this song from the playlist. Please try again.")
        }
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return musicDao.getPlaylistSongs(playlistId).map { items ->
            items.map { entity ->
                Song(
                    id = entity.songId,
                    title = entity.title,
                    artist = entity.artist,
                    album = entity.album,
                    path = entity.path,
                    durationMs = entity.durationMs,
                    albumArtUri = entity.albumArtUri,
                    isLocal = entity.isLocal
                )
            }
        }
    }

    val songOverrides: Flow<List<SongOverrideEntity>> = musicDao.getOverrides()

    suspend fun saveSongOverride(songId: String, title: String, artist: String, album: String) = withContext(Dispatchers.IO) {
        try {
            musicDao.insertOverride(SongOverrideEntity(songId, title, artist, album))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save song override for $songId", e)
            throw UserFacingException("Could not save your changes. Please try again.")
        }
    }

    val recentPlays: Flow<List<RecentPlayEntity>> = musicDao.getRecentPlays()

    suspend fun addRecentPlay(song: Song) = withContext(Dispatchers.IO) {
        try {
            val cutoff = System.currentTimeMillis() - 15552000000L // 6 months in ms
            musicDao.deleteOldRecentPlays(cutoff)

            // Remove previous play log of the same song on the current calendar day
            val cal = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            musicDao.deleteRecentPlayForSongOnDay(song.id, cal.timeInMillis)

            musicDao.insertRecentPlay(
                RecentPlayEntity(
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    path = song.path,
                    durationMs = song.durationMs,
                    albumArtUri = song.albumArtUri,
                    isLocal = song.isLocal,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to record recent play for song ${song.id}", e)
        }
    }
}

