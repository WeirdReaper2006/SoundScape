package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.PlaylistEntity
import com.example.data.models.Song
import androidx.media3.common.Player
import com.example.ui.theme.SoundScapeTheme
import com.example.ui.viewmodel.MusicViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.*
import java.util.concurrent.TimeUnit

// SoundScape M3 Dynamic Theme Integration - Maps legacy styling to dynamic MaterialTheme
val SpotifyGreen: Color @Composable get() = MaterialTheme.colorScheme.primary
val SpotifyBlack: Color @Composable get() = MaterialTheme.colorScheme.background
val SpotifyDark: Color @Composable get() = if (MaterialTheme.colorScheme.background.red + MaterialTheme.colorScheme.background.green + MaterialTheme.colorScheme.background.blue > 1.5f) Color(0xFFE9ECEF) else MaterialTheme.colorScheme.background
val SpotifyMediumGray: Color @Composable get() = MaterialTheme.colorScheme.surface
val SpotifyLightGray: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val SpotifyTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
val ThemeWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground

// ---------------- EMBEDDED ALBUM ART & FOLDER HELPERS ----------------
fun getEmbeddedPicture(context: android.content.Context, path: String): ByteArray? {
    if (path.isEmpty()) return null
    val retriever = android.media.MediaMetadataRetriever()
    return try {
        if (path.startsWith("content://") || path.startsWith("file://")) {
            retriever.setDataSource(context, android.net.Uri.parse(path))
        } else {
            retriever.setDataSource(path)
        }
        retriever.embeddedPicture
    } catch (e: Exception) {
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}

@Composable
fun rememberAlbumArt(song: Song): Any? {
    val context = LocalContext.current
    return produceState<Any?>(initialValue = R.drawable.ic_launcher_foreground, key1 = song.id, key2 = song.path, key3 = song.albumArtUri) {
        if (!song.albumArtUri.isNullOrBlank()) {
            value = song.albumArtUri
        } else if (song.isLocal) {
            val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                getEmbeddedPicture(context, song.path)
            }
            if (bytes != null) {
                value = bytes
            } else {
                value = R.drawable.ic_launcher_foreground
            }
        } else {
            value = R.drawable.ic_launcher_foreground
        }
    }.value
}

@Composable
fun SoundScapeArtwork(
    song: Song?,
    modifier: Modifier
) {
    val context = LocalContext.current
    var isError by remember(song?.id) { mutableStateOf(false) }
    val albumArtData = song?.let { rememberAlbumArt(it) }

    if (song == null || isError || albumArtData == null || albumArtData == R.drawable.ic_launcher_foreground) {
        val title = song?.title ?: "Unknown"
        val artist = song?.artist ?: "Track"
        
        val gradientPresets = remember {
            listOf(
                listOf(Color(0xFF00C6FF), Color(0xFF0072FF)), // Neon Blue
                listOf(Color(0xFFF12711), Color(0xFFF5AF19)), // Sunset Orange
                listOf(Color(0xFF11998e), Color(0xFF38ef7d)), // Emerald Green
                listOf(Color(0xFF7F00FF), Color(0xFFFF007F)), // Purple to Pink
                listOf(Color(0xFF3A1C71), Color(0xFFD76D77)), // Deep Space Violet
                listOf(Color(0xFFED213A), Color(0xFF93291E)), // Crimson Red
                listOf(Color(0xFFFC466B), Color(0xFF3F5EFB)), // Pink to Blue
                listOf(Color(0xFF0F2027), Color(0xFF2C5364)), // Midnight Slate
                listOf(Color(0xFF8A2387), Color(0xFFE94057))  // Royal Sunset
            )
        }
        
        val brush = remember(title, artist) {
            val hash = (title + artist).hashCode()
            val index = Math.abs(hash % gradientPresets.size)
            Brush.linearGradient(gradientPresets[index])
        }

        Box(
            modifier = modifier
                .background(brush),
            contentAlignment = Alignment.Center
        ) {
            val firstLetter = remember(title) {
                title.trim().take(1).uppercase()
            }
            Text(
                text = if (firstLetter.all { it.isLetterOrDigit() }) firstLetter else "🎵",
                color = Color.White.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold,
                fontSize = (modifier.toString().hashCode().let { 16.sp }), // safe default font size
                textAlign = TextAlign.Center
            )
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(albumArtData)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop,
            onError = {
                isError = true
            }
        )
    }
}

fun getFolderSearchFilterFromUri(context: android.content.Context, uri: android.net.Uri): String {
    try {
        val path = uri.path ?: ""
        if (path.contains(":")) {
            val split = path.split(":")
            if (split.size > 1) {
                return android.net.Uri.decode(split[1])
            }
        }
        val docId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        if (docId != null && docId.contains(":")) {
            val split = docId.split(":")
            if (split.size > 1) {
                return android.net.Uri.decode(split[1])
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return uri.lastPathSegment ?: ""
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            SoundScapeTheme(
                themePreset = viewModel.themePreset,
                isDark = viewModel.themeIsDark,
                customColorHex = viewModel.themeCustomColor
            ) {
                val context = LocalContext.current

                // Request Storage and Notification Permissions
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest) { results ->
                    viewModel.refreshLibrary()
                }

                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (viewModel.isOnboardingCompleted) {
                        SpotifyScaffold(viewModel)
                    } else {
                        OnboardingScreen(
                            onComplete = { name, path ->
                                viewModel.updateProfile(name, path)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpotifyScaffold(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var activePlaylistForDetail by remember { mutableStateOf<PlaylistEntity?>(null) }
    
    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var showQueueOverlay by remember { mutableStateOf(false) }

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    // Real-time cohesive back and navigation routing interceptor
    BackHandler(enabled = isExpandedPlayerVisible || showQueueOverlay || showProfileSettingsDialog || activePlaylistForDetail != null || viewModel.showRecentsPage || viewModel.activeTabIndex != 0) {
        when {
            showQueueOverlay -> showQueueOverlay = false
            showProfileSettingsDialog -> showProfileSettingsDialog = false
            isExpandedPlayerVisible -> isExpandedPlayerVisible = false
            activePlaylistForDetail != null -> activePlaylistForDetail = null
            viewModel.showRecentsPage -> viewModel.showRecentsPage = false
            viewModel.activeTabIndex != 0 -> viewModel.activeTabIndex = 0
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier
                        .background(SpotifyBlack)
                        .navigationBarsPadding()
                ) {
                    // Small persist bottom player bar
                    viewModel.currentPlayingSong?.let { song ->
                        MiniPlayerBar(
                            song = song,
                            isPlaying = viewModel.isPlaying,
                            positionMs = viewModel.currentPlaybackPosition,
                            durationMs = viewModel.currentTrackDuration,
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onSkipNext = { viewModel.playNext() },
                            onBarClick = { isExpandedPlayerVisible = true }
                        )
                    }

                    // Bottom Navigation Bar
                    NavigationBar(
                        containerColor = SpotifyMediumGray,
                        tonalElevation = 8.dp,
                        modifier = Modifier.testTag("system_bottom_nav")
                    ) {
                        NavigationBarItem(
                            selected = viewModel.activeTabIndex == 0,
                            onClick = { 
                                viewModel.activeTabIndex = 0
                                activePlaylistForDetail = null 
                            },
                            icon = { Icon(if (viewModel.activeTabIndex == 0) Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Home") },
                            label = { Text("Home") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = SpotifyTextSecondary,
                                unselectedTextColor = SpotifyTextSecondary,
                                indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                            )
                        )
                        NavigationBarItem(
                            selected = viewModel.activeTabIndex == 1,
                            onClick = { 
                                viewModel.activeTabIndex = 1
                                activePlaylistForDetail = null
                            },
                            icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                            label = { Text("Search") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = SpotifyTextSecondary,
                                unselectedTextColor = SpotifyTextSecondary,
                                indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.testTag("search_tab_item")
                        )
                        NavigationBarItem(
                            selected = viewModel.activeTabIndex == 2,
                            onClick = { 
                                viewModel.activeTabIndex = 2
                            },
                            icon = { Icon(if (viewModel.activeTabIndex == 2) Icons.Filled.LibraryMusic else Icons.Outlined.LibraryMusic, contentDescription = "Your Library") },
                            label = { Text("Your Library") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = SpotifyTextSecondary,
                                unselectedTextColor = SpotifyTextSecondary,
                                indicatorColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(SpotifyDark.copy(alpha = 0.8f), SpotifyBlack),
                            startY = 0f
                        )
                    )
            ) {
                // If a playlist is selected for detail, show detail screen instead of generic tabs
                if (activePlaylistForDetail != null) {
                    PlaylistDetailScreen(
                        playlist = activePlaylistForDetail!!,
                        viewModel = viewModel,
                        onBack = { activePlaylistForDetail = null }
                    )
                } else if (viewModel.showRecentsPage) {
                    RecentsScreen(
                        viewModel = viewModel,
                        onBack = { viewModel.showRecentsPage = false }
                    )
                } else {
                    when (viewModel.activeTabIndex) {
                        0 -> HomeScreen(
                            viewModel = viewModel,
                            onPlaylistSelect = { activePlaylistForDetail = it },
                            onProfileClick = { showProfileSettingsDialog = true }
                        )
                        1 -> SearchScreen(
                            viewModel = viewModel,
                            onProfileClick = { showProfileSettingsDialog = true }
                        )
                        2 -> LibraryScreen(
                            viewModel = viewModel,
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onPlaylistClick = { activePlaylistForDetail = it },
                            onProfileClick = { showProfileSettingsDialog = true }
                        )
                    }
                }
            }
        }

        // Fullscreen Overlay Player Sheet (Animated Slide-up)
        AnimatedVisibility(
            visible = isExpandedPlayerVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400, easing = EaseOutQuart)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(450, easing = EaseInQuart)
            )
        ) {
            viewModel.currentPlayingSong?.let { song ->
                ExpandedPlayerScreen(
                    song = song,
                    viewModel = viewModel,
                    onMinimize = { isExpandedPlayerVisible = false },
                    onViewQueueClick = { showQueueOverlay = true }
                )
            }
        }

        // Add to Playlist Dialog
        viewModel.showAddToPlaylistDialog?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = playlists,
                onDismiss = { viewModel.showAddToPlaylistDialog = null },
                onPlaylistSelected = { playlistId ->
                    viewModel.addSongToPlaylist(playlistId, song)
                    viewModel.showAddToPlaylistDialog = null
                    Toast.makeText(context, "Added to playlist!", Toast.LENGTH_SHORT).show()
                },
                onCreateNewPlaylist = {
                    showCreatePlaylistDialog = true
                }
            )
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onConfirm = { name ->
                    viewModel.createPlaylist(name)
                    showCreatePlaylistDialog = false
                    Toast.makeText(context, "Playlist created!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Profile & Settings Dialog
        if (showProfileSettingsDialog) {
            val originalPreset = remember { viewModel.themePreset }
            val originalIsDark = remember { viewModel.themeIsDark }
            val originalCustomColor = remember { viewModel.themeCustomColor }

            ProfileSettingsDialog(
                viewModel = viewModel,
                onDismiss = { saved ->
                    showProfileSettingsDialog = false
                    if (!saved) {
                        viewModel.previewTheme(originalPreset, originalIsDark, originalCustomColor)
                    }
                }
            )
        }

        // Queue Overlay View
        AnimatedVisibility(
            visible = showQueueOverlay,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(350, easing = EaseOutQuart)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(350, easing = EaseInQuart)
            )
        ) {
            QueueOverlay(
                viewModel = viewModel,
                onDismiss = { showQueueOverlay = false }
            )
        }

        // Edit Metadata Override Dialog
        viewModel.showEditTagsDialog?.let { song ->
            EditSongTagsDialog(
                song = song,
                onDismiss = { viewModel.showEditTagsDialog = null },
                onConfirm = { title, artist, album ->
                    viewModel.saveSongOverride(song.id, title, artist, album)
                    viewModel.showEditTagsDialog = null
                    Toast.makeText(context, "Metadata overridden successfully!", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Import M3U Playlist Dialog
        if (viewModel.showImportM3UDialog) {
            ImportM3UDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showImportM3UDialog = false }
            )
        }

        // Global Equalizer Dialog
        if (viewModel.showEqualizerDialogGlobally) {
            EqualizerDialog(
                viewModel = viewModel,
                onDismiss = { viewModel.showEqualizerDialogGlobally = false }
            )
        }
    }
}

@Composable
fun MiniPlayerBar(
    song: Song,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPauseToggle: () -> Unit,
    onSkipNext: () -> Unit,
    onBarClick: () -> Unit
) {
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs.toFloat() else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyMediumGray)
            .clickable { onBarClick() }
            .testTag("mini_player_bar")
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                SoundScapeArtwork(
                    song = song,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SpotifyLightGray)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = song.title,
                        color = ThemeWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song.artist,
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = ThemeWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier.testTag("mini_skip_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next song",
                        tint = ThemeWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Mini track slider line
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = SpotifyGreen,
            trackColor = ThemeWhite.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun AlbumFormatPill(path: String, mimeType: String?) {
    // Deprecated per user request: file types are hidden under song details
}

// ---------------- HOME TAB SCREEN ----------------
@Composable
fun HomeScreen(
    viewModel: MusicViewModel,
    onPlaylistSelect: (PlaylistEntity) -> Unit,
    onProfileClick: () -> Unit
) {
    val localContext = LocalContext.current
    val favoriteList by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val recentPlays by viewModel.recentPlays.collectAsStateWithLifecycle(initialValue = emptyList())

    val recentHomeList = remember(recentPlays) {
        recentPlays.distinctBy { it.songId }.take(5)
    }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Welcoming displays
        item {
            Spacer(modifier = Modifier.height(36.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Spotify-like round profile icon on top-left corner
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SpotifyGreen, Color(0xFF1DB954).copy(alpha = 0.5f))
                            )
                        )
                        .clickable { onProfileClick() }
                        .testTag("profile_icon_home"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.userName.uppercase().take(1),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = greeting,
                        color = SpotifyTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Hi, ${viewModel.userName}",
                        color = ThemeWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Horizontal scrollable Recents Section
        if (recentHomeList.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recents",
                        color = ThemeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Show All",
                        color = SpotifyGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.showRecentsPage = true }
                            .testTag("btn_show_all_recents")
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentHomeList) { play ->
                        val song = play.toSong()
                        Column(
                            modifier = Modifier
                                .width(110.dp)
                                .clickable { viewModel.playSong(song) }
                        ) {
                            SoundScapeArtwork(
                                song = song,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = song.title,
                                color = ThemeWhite,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artist,
                                color = SpotifyTextSecondary,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // Quick Grid section
        item {
            Text(text = "Quick play", color = ThemeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // Fast Grid layouts (2 columns representation)
            val gridItems = viewModel.allSongs.take(6)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val chunks = gridItems.chunked(2)
                chunks.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { song ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(60.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SpotifyMediumGray)
                                    .clickable { viewModel.playSong(song) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                SoundScapeArtwork(
                                    song = song,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .weight(1f)
                                ) {
                                    Text(
                                        text = song.title,
                                        color = ThemeWhite,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (rowItems.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Curated playlists horizontal loop
        item {
            Text(text = "Uniquely Yours", color = ThemeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Liked Songs block card
                    Card(
                        modifier = Modifier
                            .width(150.dp)
                            .clickable {
                                // Find or load all liked tracks
                                if (favoriteList.isNotEmpty()) {
                                    viewModel.playSong(favoriteList.first(), favoriteList)
                                } else {
                                    Toast
                                        .makeText(
                                            localContext,
                                            "No liked songs yet! Library tab to find songs.",
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = SpotifyMediumGray)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(126.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(Color(0xFF450E72), Color(0xFFC062EF))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Favorite, contentDescription = "Liked", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Liked Songs",
                                color = ThemeWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Text(
                                text = "${favoriteList.size} tracks",
                                color = SpotifyTextSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }

                    // Preloaded custom playlists
                    playlists.take(2).forEach { playlist ->
                        Card(
                            modifier = Modifier
                                .width(150.dp)
                                .clickable { onPlaylistSelect(playlist) },
                            colors = CardDefaults.cardColors(containerColor = SpotifyMediumGray)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Box(
                                    modifier = Modifier
                                        .size(126.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(SpotifyLightGray, SpotifyMediumGray)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Playlist", tint = SpotifyGreen, modifier = Modifier.size(48.dp))
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = playlist.name,
                                    color = ThemeWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Custom Playlist",
                                    color = SpotifyTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // Empty bottom clearance spacing
        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

// ---------------- SEARCH TAB SCREEN ----------------
@Composable
fun SearchScreen(viewModel: MusicViewModel, onProfileClick: () -> Unit) {
    val localContext = LocalContext.current
    val searchResults = viewModel.searchResults

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile icon on top-left of Search
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(SpotifyGreen, Color(0xFF1DB954).copy(alpha = 0.5f))
                        )
                    )
                    .clickable { onProfileClick() }
                    .testTag("profile_icon_search"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = viewModel.userName.uppercase().take(1),
                    color = Color.Black,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Search",
                color = ThemeWhite,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Bar
        TextField(
            value = viewModel.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            placeholder = { Text("What do you want to listen to?", color = SpotifyTextSecondary) },
            prefix = { Icon(Icons.Default.Search, contentDescription = null, tint = ThemeWhite, modifier = Modifier.padding(end = 6.dp)) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .testTag("search_text_input"),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SpotifyMediumGray,
                unfocusedContainerColor = SpotifyMediumGray,
                focusedTextColor = ThemeWhite,
                unfocusedTextColor = ThemeWhite,
                cursorColor = SpotifyGreen,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Display states: Search Placeholder or Query Search list
        if (viewModel.searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SoundScapeBrandLogo(modifier = Modifier.size(100.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Search SoundScape",
                        color = ThemeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Find your offline songs, artists, or albums",
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            if (searchResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(64.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No songs matching query found", color = SpotifyTextSecondary, fontSize = 14.sp)
                    }
                }
            } else {
                val topResult = searchResults.first()

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Spotified Top Result Card
                    item {
                        Text(
                            text = "Top Result",
                            color = ThemeWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyMediumGray)
                                .clickable { viewModel.playSong(topResult, searchResults) }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SoundScapeArtwork(
                                song = topResult,
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            
                            Spacer(modifier = Modifier.width(16.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = topResult.title,
                                    color = ThemeWhite,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = topResult.artist,
                                    color = SpotifyTextSecondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SpotifyDark)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SONG",
                                        color = ThemeWhite,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen)
                                    .clickable { viewModel.playSong(topResult, searchResults) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play top result",
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                    }

                    // Songs Matches
                    item {
                        Text(
                            text = "Songs",
                            color = ThemeWhite,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                        )
                    }

                    items(searchResults) { song ->
                        SongItemRow(
                            song = song,
                            onClick = { viewModel.playSong(song, searchResults) },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onEditTags = { viewModel.showEditTagsDialog = song }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
            }
        }
    }
}

// ---------------- LIBRARY TAB SCREEN ----------------
@Composable
fun LibrarySongGridItem(
    song: Song,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onEditTags: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SpotifyMediumGray)
            .clickable { onClick() }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(6.dp))
        ) {
            SoundScapeArtwork(
                song = song,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = ThemeWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Song options",
                        tint = SpotifyTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SpotifyDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onAddToQueue()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onAddToPlaylist()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Metadata Tags", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onEditTags()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

// ---------------- LIBRARY TAB SCREEN ----------------
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onProfileClick: () -> Unit
) {
    var activeLibraryTab by remember { mutableStateOf("All Songs") } // "All Songs", "Playlists", "Favorites"
    val context = LocalContext.current

    val favoriteList by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile icon on top-left
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SpotifyGreen, Color(0xFF1DB954).copy(alpha = 0.5f))
                            )
                        )
                        .clickable { onProfileClick() }
                        .testTag("profile_icon_library"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.userName.uppercase().take(1),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Text(
                    text = "Your Library",
                    color = ThemeWhite,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (activeLibraryTab == "Playlists") {
                IconButton(onClick = onCreatePlaylistClick) {
                    Icon(Icons.Default.Add, contentDescription = "Create playlist", tint = ThemeWhite, modifier = Modifier.size(28.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Library Sub-tabs pills
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("All Songs", "Playlists", "Favorites").forEach { tab ->
                val isSelected = activeLibraryTab == tab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) SpotifyGreen else SpotifyMediumGray)
                        .clickable { activeLibraryTab = tab }
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = tab,
                        color = if (isSelected) Color.Black else ThemeWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (activeLibraryTab) {
            "All Songs" -> {
                if (viewModel.isLoadingSongs) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = SpotifyGreen)
                    }
                } else if (viewModel.allSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No local music files found", color = SpotifyTextSecondary)
                    }
                } else {
                    if (viewModel.isLibraryGridView) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Sorting row header in grid
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        com.example.ui.viewmodel.SortCriteria.values().forEach { criteria ->
                                            val isSelected = viewModel.activeSortCriteria == criteria
                                            val label = when (criteria) {
                                                com.example.ui.viewmodel.SortCriteria.TITLE -> "Title"
                                                com.example.ui.viewmodel.SortCriteria.ARTIST -> "Artist"
                                                com.example.ui.viewmodel.SortCriteria.DURATION -> "Duration"
                                                com.example.ui.viewmodel.SortCriteria.DATE_ADDED -> "Date Added"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else Color.Transparent)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) SpotifyGreen else ThemeWhite.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.updateSort(criteria, viewModel.activeSortOrder) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) SpotifyGreen else ThemeWhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(
                                        imageVector = if (viewModel.activeSortOrder == com.example.ui.viewmodel.SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = "Toggle Sort Order",
                                        tint = SpotifyGreen,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyMediumGray)
                                            .clickable {
                                                val nextOrder = if (viewModel.activeSortOrder == com.example.ui.viewmodel.SortOrder.ASCENDING) {
                                                    com.example.ui.viewmodel.SortOrder.DESCENDING
                                                } else {
                                                    com.example.ui.viewmodel.SortOrder.ASCENDING
                                                }
                                                viewModel.updateSort(viewModel.activeSortCriteria, nextOrder)
                                            }
                                            .padding(8.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Icon(
                                        imageVector = if (viewModel.isLibraryGridView) Icons.Filled.GridView else Icons.Outlined.GridView,
                                        contentDescription = "Toggle Grid/List View",
                                        tint = SpotifyGreen,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyMediumGray)
                                            .clickable { viewModel.toggleLibraryLayout() }
                                            .padding(8.dp)
                                    )
                                }
                            }

                            // Songs in grid
                            items(viewModel.allSongs) { song ->
                                LibrarySongGridItem(
                                    song = song,
                                    onClick = { viewModel.playSong(song, viewModel.allSongs) },
                                    onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                                    onAddToQueue = { viewModel.addToQueue(song) },
                                    onEditTags = { viewModel.showEditTagsDialog = song }
                                )
                            }
                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
                                Spacer(modifier = Modifier.height(64.dp))
                            }
                        }
                    } else {
                        // Standard List view in LazyColumn
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        com.example.ui.viewmodel.SortCriteria.values().forEach { criteria ->
                                            val isSelected = viewModel.activeSortCriteria == criteria
                                            val label = when (criteria) {
                                                com.example.ui.viewmodel.SortCriteria.TITLE -> "Title"
                                                com.example.ui.viewmodel.SortCriteria.ARTIST -> "Artist"
                                                com.example.ui.viewmodel.SortCriteria.DURATION -> "Duration"
                                                com.example.ui.viewmodel.SortCriteria.DATE_ADDED -> "Date Added"
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) SpotifyGreen.copy(alpha = 0.15f) else Color.Transparent)
                                                    .border(
                                                        width = 1.dp,
                                                        color = if (isSelected) SpotifyGreen else ThemeWhite.copy(alpha = 0.15f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.updateSort(criteria, viewModel.activeSortOrder) }
                                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    color = if (isSelected) SpotifyGreen else ThemeWhite,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    Icon(
                                        imageVector = if (viewModel.activeSortOrder == com.example.ui.viewmodel.SortOrder.ASCENDING) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                        contentDescription = "Toggle Sort Order",
                                        tint = SpotifyGreen,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyMediumGray)
                                            .clickable {
                                                val nextOrder = if (viewModel.activeSortOrder == com.example.ui.viewmodel.SortOrder.ASCENDING) {
                                                    com.example.ui.viewmodel.SortOrder.DESCENDING
                                                } else {
                                                    com.example.ui.viewmodel.SortOrder.ASCENDING
                                                }
                                                viewModel.updateSort(viewModel.activeSortCriteria, nextOrder)
                                            }
                                            .padding(8.dp)
                                    )

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Icon(
                                        imageVector = if (viewModel.isLibraryGridView) Icons.Filled.GridView else Icons.Outlined.GridView,
                                        contentDescription = "Toggle Grid/List View",
                                        tint = SpotifyGreen,
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(SpotifyMediumGray)
                                            .clickable { viewModel.toggleLibraryLayout() }
                                            .padding(8.dp)
                                    )
                                }
                            }

                            items(viewModel.allSongs) { song ->
                                SongItemRow(
                                    song = song,
                                    onClick = { viewModel.playSong(song, viewModel.allSongs) },
                                    onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                                    onAddToQueue = { viewModel.addToQueue(song) },
                                    onEditTags = { viewModel.showEditTagsDialog = song },
                                    useCardStyle = false
                                )
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    color = ThemeWhite.copy(alpha = 0.08f),
                                    thickness = 1.dp
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(48.dp))
                            }
                        }
                    }
                }
            }

            "Playlists" -> {
                if (playlists.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Create custom offline playlists", color = SpotifyTextSecondary)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onCreatePlaylistClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                                ) {
                                    Text("Create Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { viewModel.showImportM3UDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray)
                                ) {
                                    Text("Import M3U", color = ThemeWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = onCreatePlaylistClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("New Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Button(
                                    onClick = { viewModel.showImportM3UDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import M3U", color = ThemeWhite, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SpotifyMediumGray)
                                    .clickable { onPlaylistClick(playlist) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(SpotifyLightGray),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen)
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = playlist.name, color = ThemeWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(text = "Custom offline playlist", color = SpotifyTextSecondary, fontSize = 11.sp)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { viewModel.exportPlaylistToM3U(playlist.id, playlist.name) }) {
                                        Icon(Icons.Default.Share, contentDescription = "Export to M3U", tint = SpotifyGreen, modifier = Modifier.size(20.dp))
                                    }
                                    IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = SpotifyTextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Favorites" -> {
                if (favoriteList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No favorite songs added yet!", color = SpotifyTextSecondary)
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(favoriteList) { song ->
                            SongItemRow(
                                song = song,
                                onClick = { viewModel.playSong(song, favoriteList) },
                                onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                                onAddToQueue = { viewModel.addToQueue(song) },
                                onEditTags = { viewModel.showEditTagsDialog = song }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(48.dp))
                        }
                    }
                }
            }
        }
    }
}

// ---------------- PLAYLIST DETAIL VIEW ----------------
@Composable
fun PlaylistDetailScreen(
    playlist: PlaylistEntity,
    viewModel: MusicViewModel,
    onBack: () -> Unit
) {
    val playlistSongs by viewModel.getPlaylistSongs(playlist.id).collectAsStateWithLifecycle(emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ThemeWhite)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = playlist.name,
                color = ThemeWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Large Banner image for Playlist
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(SpotifyMediumGray, SpotifyBlack)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(60.dp))
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "${playlistSongs.size} tracks", color = ThemeWhite, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (playlistSongs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No songs in this playlist. Use Library/Search to add songs!", color = SpotifyTextSecondary, fontSize = 13.sp)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Tracks", color = ThemeWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)

                // Large play-all fab button
                Button(
                    onClick = { viewModel.playSong(playlistSongs.first(), playlistSongs) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                    modifier = Modifier.testTag("playlist_play_all")
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Play All", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(playlistSongs) { song ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SpotifyMediumGray)
                            .clickable { viewModel.playSong(song, playlistSongs) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            SoundScapeArtwork(
                                song = song,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(text = song.title, color = ThemeWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(text = song.artist, color = SpotifyTextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        IconButton(onClick = { viewModel.removeSongFromPlaylist(playlist.id, song.id) }) {
                            Icon(Icons.Default.PlaylistRemove, contentDescription = "Remove", tint = SpotifyTextSecondary)
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

// ---------------- SINGLE SONG ITEM ROW ----------------
@Composable
fun SongItemRow(
    song: Song,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onEditTags: () -> Unit,
    useCardStyle: Boolean = true
) {
    val durationText = remember(song.durationMs) {
        val min = TimeUnit.MILLISECONDS.toMinutes(song.durationMs)
        val sec = TimeUnit.MILLISECONDS.toSeconds(song.durationMs) - TimeUnit.MINUTES.toSeconds(min)
        String.format("%02d:%02d", min, sec)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (useCardStyle) SpotifyMediumGray else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = if (useCardStyle) 8.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SoundScapeArtwork(
                song = song,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp))
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = song.title,
                    color = ThemeWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = durationText, color = SpotifyTextSecondary, fontSize = 11.sp)
            Spacer(modifier = Modifier.width(4.dp))
            
            var showMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More song options",
                        tint = SpotifyTextSecondary
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(SpotifyDark)
                ) {
                    DropdownMenuItem(
                        text = { Text("Add to Queue", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onAddToQueue()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add to Playlist", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onAddToPlaylist()
                            showMenu = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Edit Metadata", color = ThemeWhite) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                        onClick = {
                            onEditTags()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}

// ---------------- STAGE 2: PREMIUM AUDIO WAVEFORM VISUALIZER ----------------
@Composable
fun AudioWaveformVisualizer(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_anim")
    val barCount = 20
    
    val waveHeights = (0 until barCount).map { index ->
        if (isPlaying) {
            val duration = remember(index) { (500 + (index % 5) * 150) }
            val delayValue = remember(index) { (index % 4) * 100 }
            infiniteTransition.animateFloat(
                initialValue = 0.15f,
                targetValue = 0.85f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = duration, delayMillis = delayValue, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$index"
            )
        } else {
            remember { mutableStateOf(0.12f) }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        waveHeights.forEach { heightState ->
            val barHeight = 28.dp * heightState.value
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                SpotifyGreen,
                                SpotifyGreen.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }
    }
}

// ---------------- DETAILED PLAYER SCREEN ----------------
@Composable
fun ExpandedPlayerScreen(
    song: Song,
    viewModel: MusicViewModel,
    onMinimize: () -> Unit,
    onViewQueueClick: () -> Unit
) {
    val statePosition = viewModel.currentPlaybackPosition
    val stateDuration = viewModel.currentTrackDuration
    val isPlaying = viewModel.isPlaying

    var sliderTargetValue by remember { mutableStateOf<Float?>(null) }
    val progress = if (sliderTargetValue != null) {
        sliderTargetValue!!
    } else {
        if (stateDuration > 0) statePosition.toFloat() / stateDuration.toFloat() else 0f
    }

    val isFavorite by viewModel.isFavorite(song.id).collectAsStateWithLifecycle(initialValue = false)

    // Calculate elegant timestamps
    val currentSecs = if (sliderTargetValue != null) {
        (sliderTargetValue!! * stateDuration).toLong()
    } else {
        statePosition
    }
    val stampCurrent = remember(currentSecs) {
        val min = TimeUnit.MILLISECONDS.toMinutes(currentSecs)
        val sec = TimeUnit.MILLISECONDS.toSeconds(currentSecs) - TimeUnit.MINUTES.toSeconds(min)
        String.format("%02d:%02d", min, sec)
    }
    val stampTotal = remember(stateDuration) {
        val min = TimeUnit.MILLISECONDS.toMinutes(stateDuration)
        val sec = TimeUnit.MILLISECONDS.toSeconds(stateDuration) - TimeUnit.MINUTES.toSeconds(min)
        String.format("%02d:%02d", min, sec)
    }

    // Rotating Album Artwork Spec
    val infiniteTransition = rememberInfiniteTransition(label = "ArtworkRoll")
    val rotationAngle by if (isPlaying) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(14000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ArtworkRollAnimation"
        )
    } else {
        remember { mutableStateOf(0f) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SpotifyDark, SpotifyBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .testTag("expanded_player")
    ) {
        // Upper banner controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onMinimize) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Minimize",
                    tint = ThemeWhite,
                    modifier = Modifier.size(32.dp)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM QUEUE",
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = song.album,
                    color = ThemeWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onViewQueueClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "View Queue",
                        tint = SpotifyGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
                var showPlayerOptionsMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showPlayerOptionsMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = ThemeWhite
                        )
                    }
                    DropdownMenu(
                        expanded = showPlayerOptionsMenu,
                        onDismissRequest = { showPlayerOptionsMenu = false },
                        modifier = Modifier.background(SpotifyDark)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = ThemeWhite) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                viewModel.showAddToPlaylistDialog = song
                                showPlayerOptionsMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Metadata", color = ThemeWhite) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                viewModel.showEditTagsDialog = song
                                showPlayerOptionsMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Center Rotating Album Art with beautiful glowing drop-shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            SoundScapeArtwork(
                song = song,
                modifier = Modifier
                    .fillMaxSize()
                    .rotate(rotationAngle)
                    .clip(CircleShape) // Rotates perfectly like a vintage vinyl disc!
                    .background(SpotifyMediumGray)
            )
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Title and heart actions row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = ThemeWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = SpotifyTextSecondary,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            IconButton(
                onClick = { viewModel.toggleFavorite(song) },
                modifier = Modifier.testTag("btn_favorite")
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favorite",
                    tint = if (isFavorite) SpotifyGreen else ThemeWhite,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        AudioWaveformVisualizer(
            isPlaying = isPlaying,
            modifier = Modifier
                .padding(vertical = 4.dp)
                .testTag("audio_waveform")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Progressive Slider scrubbing interface
        Slider(
            value = progress,
            onValueChange = { sliderTargetValue = it },
            onValueChangeFinished = {
                sliderTargetValue?.let {
                    val positionMs = (it * stateDuration).toLong()
                    viewModel.seekTo(positionMs)
                }
                sliderTargetValue = null
            },
            colors = SliderDefaults.colors(
                thumbColor = ThemeWhite,
                activeTrackColor = SpotifyGreen,
                inactiveTrackColor = ThemeWhite.copy(alpha = 0.2f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("scrub_bar")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stampCurrent, color = SpotifyTextSecondary, fontSize = 12.sp)
            Text(text = stampTotal, color = SpotifyTextSecondary, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(0.1f))

        // Player controls line (shuffle, skip-prev, play/pause, skip-next, repeat)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.toggleShuffle() }) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle toggle",
                    tint = if (viewModel.isShuffleEnabled) SpotifyGreen else ThemeWhite
                )
            }

            IconButton(
                onClick = { viewModel.playPrevious() },
                modifier = Modifier.testTag("btn_skip_previous")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous Song",
                    tint = ThemeWhite,
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(ThemeWhite)
                    .clickable { viewModel.togglePlayPause() }
                    .testTag("btn_play_pause"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "PlayPause large trigger",
                    tint = SpotifyBlack,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = { viewModel.playNext() },
                modifier = Modifier.testTag("btn_skip_next")
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next Song",
                    tint = ThemeWhite,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = { viewModel.toggleRepeat() }) {
                Icon(
                    imageVector = when (viewModel.repeatMode) {
                        Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        Player.REPEAT_MODE_ALL -> Icons.Default.Repeat
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat toggle",
                    tint = if (viewModel.repeatMode != Player.REPEAT_MODE_OFF) SpotifyGreen else ThemeWhite
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Stage 2 Custom Accessory Row: Sleep Timer, Speed Controls & Equalizer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Speed Controller
            var showSpeedMenu by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { showSpeedMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyMediumGray.copy(alpha = 0.6f),
                        contentColor = ThemeWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("btn_speed_control")
                ) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = "Playback Speed",
                        modifier = Modifier.size(16.dp),
                        tint = SpotifyGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${viewModel.playbackSpeed}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showSpeedMenu,
                    onDismissRequest = { showSpeedMenu = false },
                    modifier = Modifier.background(SpotifyDark)
                ) {
                    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                    speeds.forEach { speed ->
                        DropdownMenuItem(
                            text = { Text("${speed}x", color = ThemeWhite) },
                            onClick = {
                                viewModel.changePlaybackSpeed(speed)
                                showSpeedMenu = false
                            }
                        )
                    }
                }
            }

            // Sleep Timer Controller
            var showSleepTimerMenu by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { showSleepTimerMenu = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyMediumGray.copy(alpha = 0.6f),
                        contentColor = if (viewModel.sleepTimerMinsLeft > 0) SpotifyGreen else ThemeWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("btn_sleep_timer")
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = "Sleep Timer",
                        modifier = Modifier.size(16.dp),
                        tint = if (viewModel.sleepTimerMinsLeft > 0) SpotifyGreen else ThemeWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (viewModel.sleepTimerMinsLeft > 0) {
                            val mins = viewModel.sleepTimerSecondsLeft / 60
                            val secs = viewModel.sleepTimerSecondsLeft % 60
                            String.format("%02d:%02d", mins, secs)
                        } else {
                            "Off"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                DropdownMenu(
                    expanded = showSleepTimerMenu,
                    onDismissRequest = { showSleepTimerMenu = false },
                    modifier = Modifier.background(SpotifyDark)
                ) {
                    val timerOptions = listOf(
                        0 to "Off",
                        5 to "5 min",
                        15 to "15 min",
                        30 to "30 min",
                        45 to "45 min",
                        60 to "60 min"
                    )
                    timerOptions.forEach { (mins, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = ThemeWhite) },
                            onClick = {
                                if (mins == 0) {
                                    viewModel.cancelSleepTimer()
                                } else {
                                    viewModel.startSleepTimer(mins)
                                }
                                showSleepTimerMenu = false
                            }
                        )
                    }
                }
            }

            // Equalizer (EQ) controller
            var showEqualizerDialog by remember { mutableStateOf(false) }
            Box {
                Button(
                    onClick = { showEqualizerDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyMediumGray.copy(alpha = 0.6f),
                        contentColor = if (viewModel.eqEnabled || viewModel.bbEnabled) SpotifyGreen else ThemeWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("btn_equalizer")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Equalizer",
                        modifier = Modifier.size(16.dp),
                        tint = if (viewModel.eqEnabled || viewModel.bbEnabled) SpotifyGreen else ThemeWhite
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "EQ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (showEqualizerDialog) {
                    EqualizerDialog(
                        viewModel = viewModel,
                        onDismiss = { showEqualizerDialog = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.15f))
    }
}

// ---------------- MODAL POPUP DIALOGS ----------------
@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Long) -> Unit,
    onCreateNewPlaylist: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Add to Playlist",
                    color = ThemeWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Select an existing offline playlist for \"${song.title}\":",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "No custom playlists found", color = SpotifyTextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPlaylistSelected(playlist.id) }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(text = playlist.name, color = ThemeWhite)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        onDismiss()
                        onCreateNewPlaylist()
                    }) {
                        Text(text = "+ Create Playlist", color = SpotifyGreen)
                    }

                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = ThemeWhite)
                    }
                }
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var textValue by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "New Playlist",
                    color = ThemeWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = textValue,
                    onValueChange = { textValue = it },
                    placeholder = { Text("Playlist name", color = SpotifyTextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifyMediumGray,
                        unfocusedContainerColor = SpotifyMediumGray,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite,
                        cursorColor = SpotifyGreen,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(text = "Cancel", color = ThemeWhite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(textValue) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                        enabled = textValue.isNotBlank()
                    ) {
                        Text(text = "Create", color = Color.Black)
                    }
                }
            }
        }
    }
}

// ---------------- USER ONBOARDING SCREEN ----------------
@Composable
fun OnboardingScreen(onComplete: (String, String) -> Unit) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("Offline Listener") }
    
    // Scan Path choices: "Music" (preset), "Download" (preset), "Custom" (user custom suffix), "" (All)
    var pathType by remember { mutableStateOf("Music Presets") } // "Music Presets", "Downloads Folder", "Entire Storage", "Custom Folder"
    var customPathInput by remember { mutableStateOf("") }
    
    var nameError by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf<String?>(null) }
    
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val filter = getFolderSearchFilterFromUri(context, uri)
            if (filter.isNotEmpty()) {
                customPathInput = filter
                pathType = "Custom Folder"
            }
        }
    }
    
    val isFormValid = remember(nameInput, pathType, customPathInput) {
        val nameOk = nameInput.trim().length >= 2 && nameInput.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        val pathOk = if (pathType == "Custom Folder") {
            customPathInput.trim().isNotEmpty()
        } else {
            true
        }
        nameOk && pathOk
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SpotifyDark, SpotifyBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SoundScapeBrandLogo(modifier = Modifier.size(80.dp))
            
            Text(
                text = "Welcome to SoundScape",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("onboarding_title")
            )
            
            Text(
                text = "Personalize your offline listening profile and configure where you want to scan for audio tracks safely.",
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Name Field
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    if (it.trim().length < 2) {
                        nameError = "Name must be at least 2 characters."
                    } else if (!it.all { char -> char.isLetterOrDigit() || char == ' ' || char == '-' || char == '_' }) {
                        nameError = "Only alphanumeric, space, hyphens, underscores are allowed."
                    } else {
                        nameError = null
                    }
                },
                label = { Text("What should we call you?") },
                placeholder = { Text("Enter listener name") },
                isError = nameError != null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = SpotifyLightGray,
                    focusedLabelColor = SpotifyGreen,
                    unfocusedLabelColor = SpotifyTextSecondary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_name_input")
            )
            if (nameError != null) {
                Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location Scan Selection Header
            Text(
                text = "Select Scanning Target (Security Filter)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyMediumGray)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Standard Music Folder" to "Music Presets",
                    "Downloads Folder" to "Downloads Folder",
                    "Custom Folder name" to "Custom Folder",
                    "Scan Entire Storage (No filter)" to "Entire Storage"
                ).forEach { (display, type) ->
                    val isSelected = pathType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pathType = type }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { pathType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = display, color = if (isSelected) SpotifyGreen else Color.White, fontSize = 13.sp)
                    }
                }
            }

            // Document Tree File Explorer Action Sizing Button
            Button(
                onClick = { pickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = "Folder Picker", tint = SpotifyGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Select via File Explorer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Custom Suffix Folder Text Input
            if (pathType == "Custom Folder") {
                OutlinedTextField(
                    value = customPathInput,
                    onValueChange = {
                        customPathInput = it
                        if (it.trim().isEmpty()) {
                            pathError = "Custom folder suffix cannot be empty."
                        } else if (!it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' }) {
                            pathError = "Alphabetic and digit folder suffixes only (no path traversal /)."
                        } else {
                            pathError = null
                        }
                    },
                    label = { Text("Folder Name Suffix to Match") },
                    placeholder = { Text("e.g. Beats, CustomMusic, Folk") },
                    isError = pathError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifyLightGray,
                        focusedLabelColor = SpotifyGreen,
                        unfocusedLabelColor = SpotifyTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_custom_path_input")
                )
                if (pathError != null) {
                    Text(pathError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val cleanedName = nameInput.trim()
                    val finalName = if (cleanedName.length >= 2) cleanedName else "Offline Listener"
                    val finalPath = when (pathType) {
                        "Music Presets" -> "Music"
                        "Downloads Folder" -> "Download"
                        "Entire Storage" -> ""
                        else -> if (customPathInput.trim().isNotEmpty()) customPathInput.trim() else "Music"
                    }
                    onComplete(finalName, finalPath)
                },
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    disabledContainerColor = SpotifyLightGray
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_start_button")
            ) {
                Text(
                    text = "Continue",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = {
                    onComplete("Offline Listener", "Music")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip & Continue with Defaults",
                    color = SpotifyGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ---------------- PROFILE & SETTINGS EDIT DIALOG ----------------
@Composable
fun ProfileSettingsDialog(
    viewModel: MusicViewModel,
    onDismiss: (saved: Boolean) -> Unit
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf(viewModel.userName) }
    
    // Scan Path choices: Mapping path stored (Music, Download, "", Custom) back to type choice
    val initialTypeChoice = remember(viewModel.musicPath) {
        when (viewModel.musicPath) {
            "Music" -> "Music Presets"
            "Download" -> "Downloads Folder"
            "" -> "Entire Storage"
            else -> "Custom Folder"
        }
    }
    var pathType by remember { mutableStateOf(initialTypeChoice) }
    var customPathInput by remember { mutableStateOf(if (initialTypeChoice == "Custom Folder") viewModel.musicPath else "") }
    
    var selectedThemePreset by remember { mutableStateOf(viewModel.themePreset) }
    var selectedIsDark by remember { mutableStateOf(viewModel.themeIsDark) }
    var selectedCustomColor by remember { mutableStateOf(viewModel.themeCustomColor) }

    // Live preview theme as choices change
    LaunchedEffect(selectedThemePreset, selectedIsDark, selectedCustomColor) {
        viewModel.previewTheme(selectedThemePreset, selectedIsDark, selectedCustomColor)
    }

    var nameError by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf<String?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val filter = getFolderSearchFilterFromUri(context, uri)
            if (filter.isNotEmpty()) {
                customPathInput = filter
                pathType = "Custom Folder"
            }
        }
    }
    
    val isFormValid = remember(newName, pathType, customPathInput) {
        val nameOk = newName.trim().length >= 2 && newName.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
        val pathOk = if (pathType == "Custom Folder") {
            customPathInput.trim().isNotEmpty()
        } else {
            true
        }
        nameOk && pathOk
    }

    Dialog(onDismissRequest = { onDismiss(false) }) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
                .clip(RoundedCornerShape(16.dp)),
            color = SpotifyMediumGray
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Profile & Settings",
                    color = ThemeWhite,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "SoundScape v1.3",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 2.dp)
                )

                // Editable Name
                OutlinedTextField(
                    value = newName,
                    onValueChange = {
                        newName = it
                        if (it.trim().length < 2) {
                            nameError = "Name must be at least 2 characters."
                        } else if (!it.all { char -> char.isLetterOrDigit() || char == ' ' || char == '-' || char == '_' }) {
                            nameError = "Only alphanumeric and spaces/hyphens allowed."
                        } else {
                            nameError = null
                        }
                    },
                    label = { Text("Profile Name") },
                    isError = nameError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = SpotifyLightGray,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        unfocusedLabelColor = SpotifyTextSecondary,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("settings_name_input")
                )
                if (nameError != null) {
                    Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }

                // Theme Settings Header
                Text(
                    text = "App Theme Preset",
                    color = ThemeWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                // Theme Presets Grid / Rows
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val presets = listOf(
                        Triple("green", "Emerald Green", Color(0xFF1DB954)),
                        Triple("sunset", "Sunset Glow", Color(0xFFFF9800)),
                        Triple("blue", "Electric Blue", Color(0xFF2979FF)),
                        Triple("violet", "Amethyst violet", Color(0xFF9C27B0)),
                        Triple("crimson", "Crimson Pulse", Color(0xFFE91E63))
                    )
                    
                    presets.chunked(2).forEach { chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            chunk.forEach { (key, title, color) ->
                                val isSelected = selectedThemePreset == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) color.copy(alpha = 0.25f) else SpotifyBlack)
                                        .border(
                                            width = 1.5.dp,
                                            color = if (isSelected) color else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedThemePreset = key }
                                        .padding(10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(color)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = title,
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (chunk.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Custom theme option
                val isCustomSelected = selectedThemePreset == "custom"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCustomSelected) Color.White.copy(alpha = 0.1f) else SpotifyBlack)
                        .border(
                            width = 1.5.dp,
                            color = if (isCustomSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedThemePreset = "custom" }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { selectedThemePreset = "custom" },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colorScheme.primary,
                            unselectedColor = Color.White
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Customize Theme Color",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (isCustomSelected) {
                    Text(
                        text = "Pick Custom Accent Splay",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val colorSwatches = listOf(
                            "#00FFCC" to Color(0xFF00FFCC), // Mint Teal
                            "#FF007F" to Color(0xFFFF007F), // Neon Pink
                            "#00E5FF" to Color(0xFF00E5FF), // Aqua Cyan
                            "#FFE082" to Color(0xFFFFE082), // Soft Amber
                            "#00E676" to Color(0xFF00E676), // Emerald
                            "#BF5AF2" to Color(0xFFBF5AF2)  // Purple Bloom
                        )
                        colorSwatches.forEach { (hex, tintColor) ->
                            val isColorSelected = selectedCustomColor.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(tintColor)
                                    .border(
                                        width = 3.dp,
                                        color = if (isColorSelected) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedCustomColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isColorSelected) {
                                    Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                // Appearance Mode Toggle (Light / Dark Theme)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyBlack)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (selectedIsDark) Icons.Filled.Brightness4 else Icons.Filled.Brightness7,
                            contentDescription = "Appearance Mode",
                            tint = ThemeWhite,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Appearance Mode",
                                color = ThemeWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedIsDark) "Dark Mode" else "Light Mode",
                                color = SpotifyTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Switch(
                        checked = selectedIsDark,
                        onCheckedChange = { selectedIsDark = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            uncheckedThumbColor = ThemeWhite,
                            uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                        )
                    )
                }

                // Path reselection list
                Text(
                    text = "Reselect Scan Folder Location",
                    color = ThemeWhite,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Start)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyBlack)
                        .padding(4.dp)
                ) {
                    listOf(
                        "Standard Music Folder" to "Music Presets",
                        "Downloads Folder" to "Downloads Folder",
                        "Custom Folder name" to "Custom Folder",
                        "Scan Entire Storage (No filtering)" to "Entire Storage"
                    ).forEach { (display, type) ->
                        val isSelected = pathType == type
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { pathType = type }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { pathType = type },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary, unselectedColor = ThemeWhite)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = display, color = if (isSelected) MaterialTheme.colorScheme.primary else ThemeWhite, fontSize = 12.sp)
                        }
                    }
                }

                // Document Tree File Explorer Action Button for Settings Dialog
                Button(
                    onClick = { pickerLauncher.launch(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = SpotifyBlack),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = "Folder Picker", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Select via File Explorer", color = ThemeWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (pathType == "Custom Folder") {
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = {
                            customPathInput = it
                            if (it.trim().isEmpty()) {
                                pathError = "Custom folder name suffix cannot be empty."
                            } else if (!it.all { char -> char.isLetterOrDigit() || char == '_' || char == '-' }) {
                                pathError = "Only alphanumeric, hyphens & underscores allowed."
                            } else {
                                pathError = null
                            }
                        },
                        label = { Text("Custom Folder Suffix") },
                        isError = pathError != null,
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = SpotifyLightGray,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = SpotifyTextSecondary,
                            focusedTextColor = ThemeWhite,
                            unfocusedTextColor = ThemeWhite
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("settings_custom_path_input")
                    )
                    if (pathError != null) {
                        Text(pathError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onDismiss(false) }) {
                        Text("Cancel", color = ThemeWhite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val finalPath = when (pathType) {
                                    "Music Presets" -> "Music"
                                    "Downloads Folder" -> "Download"
                                    "Entire Storage" -> ""
                                    else -> customPathInput.trim()
                                }
                                viewModel.updateProfile(newName.trim(), finalPath)
                                viewModel.updateTheme(selectedThemePreset, selectedIsDark, selectedCustomColor)
                                onDismiss(true)
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = SpotifyLightGray)
                    ) {
                        Text("Save & Rescan", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- QUEUE OVERLAY SCREEN ----------------
@Composable
fun QueueOverlay(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    val currentSong = viewModel.currentPlayingSong
    val queue = viewModel.currentQueue
    val currentIndex = viewModel.currentQueueIndex
    val upcomingQueue = remember(queue, currentIndex) {
        if (currentIndex != -1 && currentIndex + 1 < queue.size) {
            queue.subList(currentIndex + 1, queue.size)
        } else {
            emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close", tint = ThemeWhite)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Play Queue",
                color = ThemeWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Now Playing Section
        Text(
            text = "Now Playing",
            color = SpotifyTextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (currentSong != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyMediumGray)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundScapeArtwork(
                    song = currentSong,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentSong.title,
                        color = SpotifyGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = currentSong.artist,
                        color = SpotifyTextSecondary,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Playing indicator",
                    tint = SpotifyGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "Nothing playing right now", color = SpotifyTextSecondary)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Next In Queue Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Next In Queue (${upcomingQueue.size} songs)",
                color = ThemeWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (upcomingQueue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No upcoming tracks in play queue. Click tracks in Home or Library to begin listening!",
                    color = SpotifyTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(upcomingQueue) { index, song ->
                    val realIndex = index + currentIndex + 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(SpotifyMediumGray)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}",
                                color = SpotifyTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.width(20.dp)
                            )
                            SoundScapeArtwork(
                                song = song,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = song.title,
                                    color = ThemeWhite,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = SpotifyTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Up/down buttons based on upcomingQueue index bounds
                            if (index > 0) {
                                IconButton(onClick = { viewModel.reorderQueue(realIndex, realIndex - 1) }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = ThemeWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < upcomingQueue.size - 1) {
                                IconButton(onClick = { viewModel.reorderQueue(realIndex, realIndex + 1) }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = ThemeWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { viewModel.removeFromQueue(song.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove from Queue", tint = SpotifyTextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: SOUNDSCAPE BRAND LOGO ----------------
@Composable
fun SoundScapeBrandLogo(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFF1DB954), Color(0xFF00E5FF))
                )
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.fillMaxSize()
        )
    }
}

// ---------------- PREMIUM COMPOSABLE: EQUALIZER DIALOG ----------------
@Composable
fun EqualizerDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    var showPresetMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Equalizer & Bass",
                        color = ThemeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ThemeWhite)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // Equalizer Switch Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = SpotifyGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Equalizer State", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = viewModel.eqEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyDark,
                            checkedTrackColor = SpotifyGreen,
                            uncheckedThumbColor = SpotifyTextSecondary,
                            uncheckedTrackColor = SpotifyMediumGray
                        )
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Preset Dropdown (enabled only if EQ is enabled)
                if (viewModel.eqEnabled) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { showPresetMenu = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Select Preset", color = ThemeWhite)
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = ThemeWhite)
                        }
                        DropdownMenu(
                            expanded = showPresetMenu,
                            onDismissRequest = { showPresetMenu = false },
                            modifier = Modifier.background(SpotifyDark)
                        ) {
                            val presets = listOf("Flat", "Bass Booster", "Electronic", "Pop", "Rock", "Classical")
                            presets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset, color = ThemeWhite) },
                                    onClick = {
                                        viewModel.applyEqualizerPreset(preset)
                                        showPresetMenu = false
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    // 5 vertical sliders
                    Text("Frequency Bands", color = SpotifyTextSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val frequencies = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
                        frequencies.forEachIndexed { index, freq ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f)
                            ) {
                                val bandLevel = viewModel.eqBands.getOrElse(index) { 0 }
                                Text("${bandLevel / 100}dB", color = ThemeWhite, fontSize = 11.sp)
                                Slider(
                                    value = bandLevel.toFloat(),
                                    onValueChange = { value ->
                                        viewModel.updateEqualizerBand(index, value.toInt())
                                    },
                                    valueRange = -1500f..1500f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = SpotifyGreen,
                                        activeTrackColor = SpotifyGreen,
                                        inactiveTrackColor = SpotifyMediumGray
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .graphicsLayer {
                                            rotationZ = -90f
                                        }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(freq, color = SpotifyTextSecondary, fontSize = 10.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }

                // Bass Boost Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = SpotifyGreen)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Bass Boost State", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = viewModel.bbEnabled,
                        onCheckedChange = { viewModel.toggleBassBoost() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyDark,
                            checkedTrackColor = SpotifyGreen,
                            uncheckedThumbColor = SpotifyTextSecondary,
                            uncheckedTrackColor = SpotifyMediumGray
                        )
                    )
                }

                if (viewModel.bbEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Strength", color = ThemeWhite, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                        Slider(
                            value = viewModel.bbStrength.toFloat(),
                            onValueChange = { value ->
                                viewModel.updateBassBoostStrength(value.toInt())
                            },
                            valueRange = 0f..1000f,
                            colors = SliderDefaults.colors(
                                thumbColor = SpotifyGreen,
                                activeTrackColor = SpotifyGreen,
                                inactiveTrackColor = SpotifyMediumGray
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("${viewModel.bbStrength / 10}%", color = ThemeWhite, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: EDIT METADATA TAGS DIALOG ----------------
@Composable
fun EditSongTagsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Edit Song Metadata",
                    color = ThemeWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title", color = SpotifyTextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifyMediumGray,
                        unfocusedContainerColor = SpotifyMediumGray,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite,
                        cursorColor = SpotifyGreen,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist", color = SpotifyTextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifyMediumGray,
                        unfocusedContainerColor = SpotifyMediumGray,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite,
                        cursorColor = SpotifyGreen,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album", color = SpotifyTextSecondary) },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = SpotifyMediumGray,
                        unfocusedContainerColor = SpotifyMediumGray,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite,
                        cursorColor = SpotifyGreen,
                        focusedIndicatorColor = SpotifyGreen
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ThemeWhite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(title, artist, album) },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen)
                    ) {
                        Text("Save", color = SpotifyDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: IMPORT M3U DIALOG ----------------
@Composable
fun ImportM3UDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) {
        viewModel.scanForM3UPlaylists()
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Import M3U Playlist",
                    color = ThemeWhite,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scanning directory: ${viewModel.musicPath}",
                    color = SpotifyTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(16.dp))

                val m3uFiles = viewModel.availableM3UFiles
                if (m3uFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No .m3u or .m3u8 files found.", color = SpotifyTextSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                    ) {
                        items(m3uFiles) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.importPlaylistFromM3U(file)
                                        onDismiss()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                        contentDescription = null,
                                        tint = SpotifyGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = file.name,
                                        color = ThemeWhite,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ArrowForwardIos,
                                    contentDescription = "Import",
                                    tint = SpotifyTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            HorizontalDivider(color = SpotifyMediumGray.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ThemeWhite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.scanForM3UPlaylists() },
                        colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray)
                    ) {
                        Text("Rescan", color = ThemeWhite)
                    }
                }
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: RECENTS HISTORY SCREEN ----------------
@Composable
fun RecentsScreen(viewModel: MusicViewModel, onBack: () -> Unit) {
    val recentPlays by viewModel.recentPlays.collectAsStateWithLifecycle(initialValue = emptyList())

    val groupedRecents = remember(recentPlays) {
        recentPlays.groupBy { entity ->
            getFormattedDateHeader(entity.timestamp)
        }.mapValues { (_, plays) ->
            plays.distinctBy { it.songId }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ThemeWhite
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Recently Played",
                color = ThemeWhite,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (recentPlays.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "No play history recorded yet", color = SpotifyTextSecondary, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                groupedRecents.forEach { (dateHeader, plays) ->
                    item {
                        Text(
                            text = dateHeader,
                            color = SpotifyGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp, start = 4.dp)
                        )
                    }

                    items(plays) { play ->
                        val song = play.toSong()
                        SongItemRow(
                            song = song,
                            onClick = { viewModel.playSong(song, plays.map { it.toSong() }) },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onEditTags = { viewModel.showEditTagsDialog = song }
                        )
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }
        }
    }
}

fun com.example.data.db.RecentPlayEntity.toSong() = Song(
    id = songId,
    title = title,
    artist = artist,
    album = album,
    path = path,
    durationMs = durationMs,
    albumArtUri = albumArtUri,
    isLocal = isLocal,
    dateAdded = timestamp
)

fun getFormattedDateHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    return when {
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR) -> "Today"
        
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - time.get(Calendar.DAY_OF_YEAR) == 1 -> "Yesterday"
        
        else -> java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(timestamp))
    }
}
