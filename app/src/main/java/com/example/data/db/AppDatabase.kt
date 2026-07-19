package com.example.data.db

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val isLocal: Boolean
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val songCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_songs")
data class PlaylistSongCrossRef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val isLocal: Boolean
)

@Entity(tableName = "song_overrides")
data class SongOverrideEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val album: String
)

@Entity(tableName = "recent_plays")
data class RecentPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val album: String,
    val path: String,
    val durationMs: Long,
    val albumArtUri: String?,
    val isLocal: Boolean,
    val timestamp: Long
)

@Dao
interface MusicDao {
    @Query("SELECT * FROM favorites")
    fun getFavorites(): Flow<List<FavoriteEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    fun deleteFavorite(songId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    fun isFavorite(songId: String): Flow<Boolean>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun getPlaylists(): Flow<List<PlaylistEntity>>

    @Insert
    fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    fun deletePlaylist(playlistId: Long)

    @Insert
    fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    fun deletePlaylistSong(playlistId: Long, songId: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId")
    fun getPlaylistSongs(playlistId: Long): Flow<List<PlaylistSongCrossRef>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOverride(override: SongOverrideEntity)

    @Query("SELECT * FROM song_overrides")
    fun getOverrides(): Flow<List<SongOverrideEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertRecentPlay(recentPlay: RecentPlayEntity)

    @Query("SELECT * FROM recent_plays ORDER BY timestamp DESC")
    fun getRecentPlays(): Flow<List<RecentPlayEntity>>

    @Query("DELETE FROM recent_plays WHERE timestamp < :cutoffTime")
    fun deleteOldRecentPlays(cutoffTime: Long)

    @Query("DELETE FROM recent_plays WHERE songId = :songId AND timestamp >= :startOfDay")
    fun deleteRecentPlayForSongOnDay(songId: String, startOfDay: Long)
}

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SongOverrideEntity::class,
        RecentPlayEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_database"
                )
                // Only the pre-existing, un-migrated 1 -> 2 jump is allowed to wipe data.
                // Any future version bump without a real Migration now fails loudly
                // during development instead of silently deleting user data.
                .fallbackToDestructiveMigrationFrom(1)
                .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
