package com.example.data.db

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

@Entity(
    tableName = "playlist_songs",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlistId"]),
        Index(value = ["playlistId", "songId"], unique = true)
    ]
)
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
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

/**
 * playlist_songs previously had no foreign key back to playlists and no uniqueness constraint,
 * so deleting a playlist left its songs orphaned forever (and, since PlaylistEntity's id isn't
 * declared AUTOINCREMENT, SQLite could reuse a deleted playlist's rowid for a new one, making the
 * new playlist appear to inherit the old one's orphaned songs). SQLite can't add a foreign key to
 * an existing table, so the table is rebuilt: only rows whose playlistId still exists are kept,
 * and duplicate (playlistId, songId) rows are collapsed to the earliest one before the new unique
 * index is created.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE playlist_songs_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                playlistId INTEGER NOT NULL,
                songId TEXT NOT NULL,
                title TEXT NOT NULL,
                artist TEXT NOT NULL,
                album TEXT NOT NULL,
                path TEXT NOT NULL,
                durationMs INTEGER NOT NULL,
                albumArtUri TEXT,
                isLocal INTEGER NOT NULL,
                FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO playlist_songs_new (id, playlistId, songId, title, artist, album, path, durationMs, albumArtUri, isLocal)
            SELECT id, playlistId, songId, title, artist, album, path, durationMs, albumArtUri, isLocal
            FROM playlist_songs
            WHERE playlistId IN (SELECT id FROM playlists)
            AND id IN (
                SELECT MIN(id) FROM playlist_songs GROUP BY playlistId, songId
            )
            """.trimIndent()
        )
        db.execSQL("DROP TABLE playlist_songs")
        db.execSQL("ALTER TABLE playlist_songs_new RENAME TO playlist_songs")
        db.execSQL("CREATE INDEX index_playlist_songs_playlistId ON playlist_songs(playlistId)")
        db.execSQL("CREATE UNIQUE INDEX index_playlist_songs_playlistId_songId ON playlist_songs(playlistId, songId)")
    }
}

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SongOverrideEntity::class,
        RecentPlayEntity::class
    ],
    version = 3,
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
                .addMigrations(MIGRATION_2_3)
                .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
