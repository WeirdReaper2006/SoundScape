package com.example.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.MusicService
import com.example.data.db.AppDatabase
import com.example.data.db.PlaylistEntity
import com.example.data.models.Song
import com.example.data.repository.MusicRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SortCriteria {
    TITLE,
    ARTIST,
    DURATION,
    DATE_ADDED
}

enum class SortOrder {
    ASCENDING,
    DESCENDING
}

@OptIn(UnstableApi::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val musicDao = AppDatabase.getDatabase(application).musicDao()
    private val repository = MusicRepository(application, musicDao)

    // MediaController related
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    // UI States
    var allSongs by mutableStateOf<List<Song>>(emptyList())
        private set

    var searchResults by mutableStateOf<List<Song>>(emptyList())
        private set

    var searchQuery by mutableStateOf("")
        private set

    var isLoadingSongs by mutableStateOf(true)
        private set

    // Current State Flow bindings from DB
    val favoriteSongs: StateFlow<List<Song>> = repository.favoriteSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = repository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Tracks current song list playing in player to handle back/next cues
    var currentQueue by mutableStateOf<List<Song>>(emptyList())
        private set

    var originalQueue by mutableStateOf<List<Song>>(emptyList())
        private set

    var currentQueueIndex by mutableStateOf(-1)

    var activeSortCriteria by mutableStateOf(SortCriteria.TITLE)
        private set

    var activeSortOrder by mutableStateOf(SortOrder.ASCENDING)
        private set

    // Active playback properties
    var currentPlayingSong by mutableStateOf<Song?>(null)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    var currentPlaybackPosition by mutableLongStateOf(0L)
        private set

    var currentTrackDuration by mutableLongStateOf(0L)
        private set

    var isShuffleEnabled by mutableStateOf(false)
        private set

    var repeatMode by mutableStateOf(Player.REPEAT_MODE_OFF)
        private set

    // Playback Speed and Sleep Timer attributes (Stage 2)
    var playbackSpeed by mutableStateOf(1.0f)
        private set

    var sleepTimerMinsLeft by mutableStateOf(0)
        private set

    var sleepTimerSecondsLeft by mutableStateOf(0)
        private set

    private var sleepTimerJob: Job? = null

    // Navigation and active screens
    var activeTabIndex by mutableStateOf(0) // 0=Home, 1=Search, 2=Library

    // Selected items for playlists editing
    var showAddToPlaylistDialog by mutableStateOf<Song?>(null)

    // Onboarding & User Profile States
    var userName by mutableStateOf("New Listener")
        private set

    var musicPath by mutableStateOf("Music")
        private set

    var isOnboardingCompleted by mutableStateOf(false)
        private set

    // Dynamic Theme Settings
    var themePreset by mutableStateOf("green")
        private set

    var themeIsDark by mutableStateOf(true)
        private set

    var themeCustomColor by mutableStateOf("#00E5FF")
        private set

    // Background progress tracker job
    private var progressTrackingJob: Job? = null

    init {
        loadProfile()
        initMediaController()
        refreshLibrary()
    }

    fun loadProfile() {
        val prefs = getApplication<Application>().getSharedPreferences("spotify_clone_prefs", android.content.Context.MODE_PRIVATE)
        userName = prefs.getString("user_name", "Listener") ?: "Listener"
        musicPath = prefs.getString("music_path", "Music") ?: "Music"
        isOnboardingCompleted = prefs.getBoolean("onboarding_completed", false)
        themePreset = prefs.getString("theme_preset", "green") ?: "green"
        themeIsDark = prefs.getBoolean("theme_is_dark", true)
        themeCustomColor = prefs.getString("theme_custom_color", "#00E5FF") ?: "#00E5FF"
    }

    fun updateProfile(name: String, path: String) {
        val prefs = getApplication<Application>().getSharedPreferences("spotify_clone_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_name", name.trim())
            .putString("music_path", path.trim())
            .putBoolean("onboarding_completed", true)
            .apply()
        userName = name.trim()
        musicPath = path.trim()
        isOnboardingCompleted = true
        refreshLibrary()
    }

    fun updateTheme(preset: String, isDark: Boolean, customColorHex: String) {
        val prefs = getApplication<Application>().getSharedPreferences("spotify_clone_prefs", android.content.Context.MODE_PRIVATE)
        prefs.edit()
            .putString("theme_preset", preset)
            .putBoolean("theme_is_dark", isDark)
            .putString("theme_custom_color", customColorHex)
            .apply()
        themePreset = preset
        themeIsDark = isDark
        themeCustomColor = customColorHex
    }

    fun previewTheme(preset: String, isDark: Boolean, customColorHex: String) {
        themePreset = preset
        themeIsDark = isDark
        themeCustomColor = customColorHex
    }

    private fun initMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), MusicService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                setupControllerListener()
                updatePlaybackStateFromController()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(getApplication()))
    }

    private fun setupControllerListener() {
        mediaController?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)
                updateActiveSong()
            }

            override fun onIsPlayingChanged(isPlayingChanged: Boolean) {
                super.onIsPlayingChanged(isPlayingChanged)
                isPlaying = isPlayingChanged
                if (isPlayingChanged) {
                    startProgressTracker()
                } else {
                    stopProgressTracker()
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                super.onPlaybackStateChanged(state)
                updatePlaybackStateFromController()
            }

            override fun onRepeatModeChanged(mode: Int) {
                super.onRepeatModeChanged(mode)
                repeatMode = mode
            }

            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                super.onPlaybackParametersChanged(playbackParameters)
                playbackSpeed = playbackParameters.speed
            }
        })
    }

    private fun updatePlaybackStateFromController() {
        mediaController?.let { controller ->
            isPlaying = controller.isPlaying
            repeatMode = controller.repeatMode
            playbackSpeed = controller.playbackParameters.speed
            updateActiveSong()
            if (isPlaying) {
                startProgressTracker()
            }
        }
    }

    private fun updateActiveSong() {
        mediaController?.let { controller ->
            val activeItem = controller.currentMediaItem
            if (activeItem != null) {
                val activeId = activeItem.mediaId
                val idx = currentQueue.indexOfFirst { it.id == activeId }
                currentQueueIndex = idx
                currentPlayingSong = currentQueue.find { it.id == activeId }
                    ?: allSongs.find { it.id == activeId }
                    ?: Song(
                        id = activeItem.mediaId,
                        title = activeItem.mediaMetadata.title?.toString() ?: "Unnamed Track",
                        artist = activeItem.mediaMetadata.artist?.toString() ?: "Unknown artist",
                        album = activeItem.mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
                        path = activeItem.mediaMetadata.artworkUri?.toString() ?: "", // Backing
                        durationMs = controller.duration.coerceAtLeast(0L),
                        albumArtUri = activeItem.mediaMetadata.artworkUri?.toString(),
                        isLocal = false
                    )
                currentTrackDuration = controller.duration.coerceAtLeast(0L)
                currentPlaybackPosition = controller.currentPosition.coerceAtLeast(0L)
            } else {
                currentPlayingSong = null
                currentQueueIndex = -1
                currentTrackDuration = 0L
                currentPlaybackPosition = 0L
            }
        }
    }

    fun updateSort(criteria: SortCriteria, order: SortOrder) {
        activeSortCriteria = criteria
        activeSortOrder = order
        applySortingAndFiltering()
    }

    private fun sortSongsList(list: List<Song>, criteria: SortCriteria, order: SortOrder): List<Song> {
        val sorted = when (criteria) {
            SortCriteria.TITLE -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            SortCriteria.ARTIST -> list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.artist })
            SortCriteria.DURATION -> list.sortedBy { it.durationMs }
            SortCriteria.DATE_ADDED -> list.sortedBy { it.dateAdded }
        }
        return if (order == SortOrder.DESCENDING) sorted.reversed() else sorted
    }

    private fun applySortingAndFiltering() {
        allSongs = sortSongsList(allSongs, activeSortCriteria, activeSortOrder)
        onSearchQueryChanged(searchQuery)
    }

    fun refreshLibrary() {
        viewModelScope.launch {
            isLoadingSongs = true
            try {
                val foundLocal = repository.getLocalSongs()
                allSongs = sortSongsList(foundLocal, activeSortCriteria, activeSortOrder)
                searchResults = allSongs
            } catch (e: Exception) {
                allSongs = emptyList()
                searchResults = allSongs
            } finally {
                applySortingAndFiltering()
                isLoadingSongs = false
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        searchResults = if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
    }

    // Playback control functions
    fun playSong(song: Song, queue: List<Song> = allSongs) {
        originalQueue = queue
        mediaController?.let { controller ->
            var finalQueue = queue
            val songIndex = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            
            if (isShuffleEnabled) {
                val remaining = queue.filter { it.id != song.id }.toMutableList()
                remaining.shuffle()
                finalQueue = listOf(song) + remaining
            }
            
            currentQueue = finalQueue
            controller.clearMediaItems()
            
            finalQueue.forEach { qSong ->
                val mediaMetadata = MediaMetadata.Builder()
                    .setTitle(qSong.title)
                    .setArtist(qSong.artist)
                    .setAlbumTitle(qSong.album)
                    .setArtworkUri(qSong.albumArtUri?.let { android.net.Uri.parse(it) })
                    .build()

                val item = MediaItem.Builder()
                    .setMediaId(qSong.id)
                    .setUri(qSong.path)
                    .setMimeType(qSong.mimeType ?: "audio/*")
                    .setMediaMetadata(mediaMetadata)
                    .build()
                controller.addMediaItem(item)
            }

            val targetIndex = finalQueue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            controller.seekTo(targetIndex, 0L)
            controller.prepare()
            controller.play()
            currentPlayingSong = song
            updatePlaybackStateFromController()
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                if (controller.playbackState == Player.STATE_IDLE) {
                    controller.prepare()
                }
                controller.play()
            }
            isPlaying = controller.isPlaying
        }
    }

    fun playNext() {
        mediaController?.let { controller ->
            if (controller.hasNextMediaItem()) {
                controller.seekToNextMediaItem()
            } else if (repeatMode == Player.REPEAT_MODE_ALL && currentQueue.isNotEmpty()) {
                controller.seekTo(0, 0L)
            }
            updateActiveSong()
        }
    }

    fun playPrevious() {
        mediaController?.let { controller ->
            if (controller.hasPreviousMediaItem() && controller.currentPosition < 5000L) {
                controller.seekToPreviousMediaItem()
            } else {
                controller.seekTo(0L)
            }
            updateActiveSong()
        }
    }

    fun toggleShuffle() {
        isShuffleEnabled = !isShuffleEnabled
        if (isShuffleEnabled) {
            shuffleRemainingQueue()
        } else {
            restoreOriginalQueue()
        }
    }

    private fun shuffleRemainingQueue() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= currentQueue.size) return
        
        val played = currentQueue.subList(0, currentIndex + 1)
        val remaining = currentQueue.subList(currentIndex + 1, currentQueue.size).toMutableList()
        remaining.shuffle()
        
        val newQueue = played + remaining
        currentQueue = newQueue
        
        val itemsToRemove = controller.mediaItemCount - (currentIndex + 1)
        if (itemsToRemove > 0) {
            controller.removeMediaItems(currentIndex + 1, controller.mediaItemCount)
        }
        
        remaining.forEach { qSong ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(qSong.title)
                .setArtist(qSong.artist)
                .setAlbumTitle(qSong.album)
                .setArtworkUri(qSong.albumArtUri?.let { android.net.Uri.parse(it) })
                .build()

            val item = MediaItem.Builder()
                .setMediaId(qSong.id)
                .setUri(qSong.path)
                .setMimeType(qSong.mimeType ?: "audio/*")
                .setMediaMetadata(mediaMetadata)
                .build()
            controller.addMediaItem(item)
        }
    }

    private fun restoreOriginalQueue() {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex < 0 || currentIndex >= currentQueue.size) return
        
        val currentSong = currentQueue[currentIndex]
        val origIndex = originalQueue.indexOfFirst { it.id == currentSong.id }
        
        val remainingOriginalSongs = if (origIndex != -1 && origIndex + 1 < originalQueue.size) {
            originalQueue.subList(origIndex + 1, originalQueue.size)
        } else {
            emptyList()
        }
        
        val newQueue = currentQueue.subList(0, currentIndex + 1) + remainingOriginalSongs
        currentQueue = newQueue
        
        val itemsToRemove = controller.mediaItemCount - (currentIndex + 1)
        if (itemsToRemove > 0) {
            controller.removeMediaItems(currentIndex + 1, controller.mediaItemCount)
        }
        
        remainingOriginalSongs.forEach { qSong ->
            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(qSong.title)
                .setArtist(qSong.artist)
                .setAlbumTitle(qSong.album)
                .setArtworkUri(qSong.albumArtUri?.let { android.net.Uri.parse(it) })
                .build()

            val item = MediaItem.Builder()
                .setMediaId(qSong.id)
                .setUri(qSong.path)
                .setMimeType(qSong.mimeType ?: "audio/*")
                .setMediaMetadata(mediaMetadata)
                .build()
            controller.addMediaItem(item)
        }
    }

    fun toggleRepeat() {
        mediaController?.let { controller ->
            val nextMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            controller.repeatMode = nextMode
            repeatMode = nextMode
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.let { controller ->
            controller.seekTo(positionMs)
            currentPlaybackPosition = positionMs
        }
    }

    // Playback Speed & Sleep Timer controls (Stage 2 implementation)
    fun changePlaybackSpeed(speed: Float) {
        mediaController?.let { controller ->
            controller.setPlaybackSpeed(speed)
            playbackSpeed = speed
        }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        if (minutes <= 0) return
        sleepTimerMinsLeft = minutes
        sleepTimerSecondsLeft = minutes * 60
        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (sleepTimerSecondsLeft > 0) {
                delay(1000)
                sleepTimerSecondsLeft--
                sleepTimerMinsLeft = (sleepTimerSecondsLeft + 59) / 60
            }
            // Timer concluded: gently pause media controller
            mediaController?.pause()
            isPlaying = false
            sleepTimerMinsLeft = 0
            sleepTimerSecondsLeft = 0
            android.widget.Toast.makeText(getApplication(), "Sleep timer finished. Playback paused.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerMinsLeft = 0
        sleepTimerSecondsLeft = 0
    }

    // Favorites & Playlists management
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFav = favoriteSongs.value.any { it.id == song.id }
            if (isFav) {
                repository.removeFavorite(song.id)
            } else {
                repository.addFavorite(song)
            }
        }
    }

    fun isFavorite(songId: String): Flow<Boolean> {
        return repository.isFavorite(songId)
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            if (name.isNotBlank()) {
                repository.createPlaylist(name)
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
        }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            repository.addSongToPlaylist(playlistId, song)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            repository.removeSongFromPlaylist(playlistId, songId)
        }
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<Song>> {
        return repository.getPlaylistSongs(playlistId)
    }

    fun addToQueue(song: Song) {
        val origUpdated = originalQueue.toMutableList()
        if (!origUpdated.any { it.id == song.id }) {
            origUpdated.add(song)
            originalQueue = origUpdated
        }
        mediaController?.let { controller ->
            val updated = currentQueue.toMutableList()
            updated.add(song)
            currentQueue = updated

            val mediaMetadata = MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.albumArtUri?.let { android.net.Uri.parse(it) })
                .build()

            val item = MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(song.path)
                .setMimeType(song.mimeType ?: "audio/*")
                .setMediaMetadata(mediaMetadata)
                .build()

            controller.addMediaItem(item)
            android.widget.Toast.makeText(getApplication(), "Added to queue: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
        } ?: run {
            // Fallback if media controller is not initialized
            val updated = currentQueue.toMutableList()
            updated.add(song)
            currentQueue = updated
            android.widget.Toast.makeText(getApplication(), "Added to queue state: ${song.title}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun removeFromQueue(songId: String) {
        val index = currentQueue.indexOfFirst { it.id == songId }
        if (index != -1) {
            val updated = currentQueue.toMutableList()
            updated.removeAt(index)
            currentQueue = updated
            
            val origUpdated = originalQueue.toMutableList().apply { removeAll { it.id == songId } }
            originalQueue = origUpdated
            
            mediaController?.let { controller ->
                try {
                    controller.removeMediaItem(index)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            android.widget.Toast.makeText(getApplication(), "Removed from queue", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices) {
            val updated = currentQueue.toMutableList()
            val item = updated.removeAt(fromIndex)
            updated.add(toIndex, item)
            currentQueue = updated
            mediaController?.let { controller ->
                try {
                    controller.moveMediaItem(fromIndex, toIndex)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Periodic progress tracker implementation
    private fun startProgressTracker() {
        stopProgressTracker()
        progressTrackingJob = viewModelScope.launch(Dispatchers.Main) {
            while (isPlaying) {
                mediaController?.let { controller ->
                    currentPlaybackPosition = controller.currentPosition.coerceAtLeast(0L)
                    currentTrackDuration = controller.duration.coerceAtLeast(0L)
                }
                delay(1000)
            }
        }
    }

    private fun stopProgressTracker() {
        progressTrackingJob?.cancel()
        progressTrackingJob = null
    }

    override fun onCleared() {
        stopProgressTracker()
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        super.onCleared()
    }
}
