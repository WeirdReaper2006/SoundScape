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
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow

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
    var activeVirtualPlaylistType by remember { mutableStateOf<String?>(null) } // "liked_songs" or "folder_songs"
    
    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var showQueueOverlay by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Real-time cohesive back and navigation routing interceptor
    BackHandler(enabled = drawerState.isOpen || isExpandedPlayerVisible || showQueueOverlay || showProfileSettingsDialog || activePlaylistForDetail != null || activeVirtualPlaylistType != null || viewModel.showRecentsPage || viewModel.activeTabIndex != 0) {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            showQueueOverlay -> showQueueOverlay = false
            showProfileSettingsDialog -> {
                viewModel.previewTheme(viewModel.themePreset, viewModel.themeIsDark, viewModel.themeCustomColor)
                showProfileSettingsDialog = false
            }
            isExpandedPlayerVisible -> isExpandedPlayerVisible = false
            activeVirtualPlaylistType != null -> activeVirtualPlaylistType = null
            activePlaylistForDetail != null -> activePlaylistForDetail = null
            viewModel.showRecentsPage -> viewModel.showRecentsPage = false
            viewModel.activeTabIndex != 0 -> viewModel.activeTabIndex = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            SpotifySidebar(
                viewModel = viewModel,
                onClose = { coroutineScope.launch { drawerState.close() } },
                onNavigateToSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showProfileSettingsDialog = true
                },
                onNavigateToRecents = {
                    coroutineScope.launch { drawerState.close() }
                    viewModel.showRecentsPage = true
                }
            )
        }
    ) {
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
                                activeVirtualPlaylistType = null
                                viewModel.showRecentsPage = false
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
                                activeVirtualPlaylistType = null
                                viewModel.showRecentsPage = false
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
                                activePlaylistForDetail = null
                                activeVirtualPlaylistType = null
                                viewModel.showRecentsPage = false
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
                if (activeVirtualPlaylistType != null) {
                    val title = if (activeVirtualPlaylistType == "liked_songs") "Liked Songs" else {
                        when (viewModel.musicPath) {
                            "Music" -> "Music Folder"
                            "Download" -> "Downloads Folder"
                            "" -> "Entire Storage"
                            else -> viewModel.musicPath.substringAfterLast("/")
                        }
                    }
                    val favoriteList by viewModel.favoriteSongs.collectAsStateWithLifecycle()
                    PlaylistDetailScreen(
                        title = title,
                        songs = if (activeVirtualPlaylistType == "liked_songs") favoriteList else viewModel.allSongs,
                        viewModel = viewModel,
                        onBack = { activeVirtualPlaylistType = null },
                        playlistId = null,
                        isLikedSongs = (activeVirtualPlaylistType == "liked_songs"),
                        isFolderSongs = (activeVirtualPlaylistType == "folder_songs")
                    )
                } else if (activePlaylistForDetail != null) {
                    val playlistSongs by viewModel.getPlaylistSongs(activePlaylistForDetail!!.id).collectAsStateWithLifecycle(emptyList())
                    PlaylistDetailScreen(
                        title = activePlaylistForDetail!!.name,
                        songs = playlistSongs,
                        viewModel = viewModel,
                        onBack = { activePlaylistForDetail = null },
                        playlistId = activePlaylistForDetail!!.id
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
                            onProfileClick = { coroutineScope.launch { drawerState.open() } }
                        )
                        1 -> SearchScreen(
                            viewModel = viewModel,
                            onProfileClick = { coroutineScope.launch { drawerState.open() } }
                        )
                        2 -> LibraryScreen(
                            viewModel = viewModel,
                            onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                            onPlaylistClick = { activePlaylistForDetail = it },
                            onVirtualPlaylistClick = { activeVirtualPlaylistType = it },
                            onProfileClick = { coroutineScope.launch { drawerState.open() } }
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

        // Profile & Settings Screen (Animated Slide-up)
        AnimatedVisibility(
            visible = showProfileSettingsDialog,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(400, easing = EaseOutQuart)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(400, easing = EaseInQuart)
            )
        ) {
            val originalPreset = remember { viewModel.themePreset }
            val originalIsDark = remember { viewModel.themeIsDark }
            val originalCustomColor = remember { viewModel.themeCustomColor }

            ProfileSettingsScreen(
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

        // Curated playlists 2-column grid for 6 Genre Mixes (Uniquely Yours)
        item {
            Text(text = "Uniquely Yours", color = ThemeWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            val genres = viewModel.topGenres
            if (genres.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SpotifyMediumGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No genres identified yet! Ensure your music files have genre tags.",
                        color = SpotifyTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                // List of 6 curated beautiful daily gradients matching Spotify mixes
                val mixGradients = listOf(
                    listOf(Color(0xFFE91E63), Color(0xFFFFC107)), // Pop (Pink-Gold)
                    listOf(Color(0xFF9C27B0), Color(0xFFE91E63)), // R&B (Purple-Pink)
                    listOf(Color(0xFF3F51B5), Color(0xFF00BCD4)), // Rock (Blue-Cyan)
                    listOf(Color(0xFF4CAF50), Color(0xFF8BC34A)), // Hip-Hop (Green-Lime)
                    listOf(Color(0xFFFF9800), Color(0xFFFF5722)), // Electronic (Orange-Red)
                    listOf(Color(0xFF607D8B), Color(0xFF9E9E9E))  // Jazz / Other (Gray-Silver)
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    genres.chunked(2).forEachIndexed { rowIndex, chunk ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            chunk.forEachIndexed { colIndex, genreName ->
                                val gradientIndex = (rowIndex * 2 + colIndex) % mixGradients.size
                                val gradientColors = mixGradients[gradientIndex]
                                val matchingSongs = viewModel.allSongs.filter { it.genre == genreName }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable {
                                            viewModel.temporaryPlaylistSongs = matchingSongs
                                            onPlaylistSelect(
                                                PlaylistEntity(
                                                    id = -999L,
                                                    name = "$genreName Mix",
                                                    songCount = matchingSongs.size
                                                )
                                            )
                                        },
                                    colors = CardDefaults.cardColors(containerColor = SpotifyMediumGray)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(brush = Brush.linearGradient(colors = gradientColors)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "$genreName Mix",
                                            color = ThemeWhite,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${matchingSongs.size} tracks",
                                            color = SpotifyTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1
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

// ---------------- CUSTOM PLAYLIST LIBRARY ITEM ----------------
@Composable
fun PlaylistLibraryItem(
    name: String,
    typeLabel: String,
    playlist: PlaylistEntity,
    viewModel: MusicViewModel,
    onClick: () -> Unit
) {
    val songs by viewModel.getPlaylistSongs(playlist.id).collectAsStateWithLifecycle(emptyList())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlaylistCollage(
            songs = songs,
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp))
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                color = ThemeWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = typeLabel,
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ---------------- LIBRARY TAB SCREEN ----------------
@Composable
fun LibraryScreen(
    viewModel: MusicViewModel,
    onCreatePlaylistClick: () -> Unit,
    onPlaylistClick: (PlaylistEntity) -> Unit,
    onVirtualPlaylistClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    val favoriteList by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
 
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
 
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(36.dp))
 
        // Spotify-style Library Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Profile Avatar on top-left
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
 
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) searchQuery = "" }) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search library",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary else ThemeWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onCreatePlaylistClick) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create playlist",
                        tint = ThemeWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
 
        // Conditionally visible Search input
        if (showSearch) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search in library...", color = SpotifyTextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = SpotifyLightGray.copy(alpha = 0.4f),
                    focusedTextColor = ThemeWhite,
                    unfocusedTextColor = ThemeWhite
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            )
        }
 
        Spacer(modifier = Modifier.height(16.dp))
 
        // Get Local scan folder name
        val folderName = remember(viewModel.musicPath) {
            when (viewModel.musicPath) {
                "Music" -> "Music Folder"
                "Download" -> "Downloads Folder"
                "" -> "Entire Storage"
                else -> viewModel.musicPath.substringAfterLast("/")
            }
        }
 
        // Construct unified list items
        val likedSongsItem = Triple("Liked Songs", "Playlist • ${viewModel.userName}", "liked_songs")
        val folderItem = Triple(folderName, "Folder", "folder_songs")
        val playlistItems = playlists.map { Triple(it.name, "Playlist • ${viewModel.userName}", it) }
 
        // Filter based on search query
        val filteredVirtuals = listOf(likedSongsItem, folderItem).filter { (name, typeLabel, _) ->
            searchQuery.trim().isEmpty() || name.contains(searchQuery, ignoreCase = true) || typeLabel.contains(searchQuery, ignoreCase = true)
        }
        val filteredPlaylists = playlistItems.filter { (name, _, _) ->
            searchQuery.trim().isEmpty() || name.contains(searchQuery, ignoreCase = true)
        }
 
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Virtual items (Liked Songs, Folder)
            items(filteredVirtuals) { (name, typeLabel, key) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onVirtualPlaylistClick(key) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlaylistCollage(
                        songs = if (key == "liked_songs") favoriteList else viewModel.allSongs,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        isLikedSongs = (key == "liked_songs"),
                        isFolderSongs = (key == "folder_songs")
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = ThemeWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (key == "liked_songs") {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Pinned",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = typeLabel,
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
 
            // 2. Custom playlists
            items(filteredPlaylists) { (name, typeLabel, playlist) ->
                PlaylistLibraryItem(
                    name = name,
                    typeLabel = typeLabel,
                    playlist = playlist,
                    viewModel = viewModel,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
 
            if (filteredVirtuals.isEmpty() && filteredPlaylists.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No matching results found", color = SpotifyTextSecondary)
                    }
                }
            }
 
            item {
                Spacer(modifier = Modifier.height(64.dp))
            }
        }
    }
}

// ---------------- PLAYLIST COLLAGE GRAPHIC ----------------
@Composable
fun PlaylistCollage(
    songs: List<Song>,
    modifier: Modifier = Modifier,
    isLikedSongs: Boolean = false,
    isFolderSongs: Boolean = false
) {
    if (songs.size >= 4) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .aspectRatio(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SoundScapeArtwork(song = songs[0], modifier = Modifier.fillMaxSize())
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SoundScapeArtwork(song = songs[1], modifier = Modifier.fillMaxSize())
                    }
                }
                Row(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.weight(1f)) {
                        SoundScapeArtwork(song = songs[2], modifier = Modifier.fillMaxSize())
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        SoundScapeArtwork(song = songs[3], modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    } else if (songs.isNotEmpty()) {
        SoundScapeArtwork(
            song = songs[0],
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .aspectRatio(1f)
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(8.dp))
                .background(
                    brush = when {
                        isLikedSongs -> Brush.linearGradient(listOf(Color(0xFF4F2FE3), Color(0xFF8097E4)))
                        isFolderSongs -> Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)))
                        else -> Brush.verticalGradient(listOf(SpotifyMediumGray, SpotifyBlack))
                    }
                )
                .aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLikedSongs -> Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                isFolderSongs -> Icon(Icons.Filled.Folder, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                else -> Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

// ---------------- PLAYLIST DETAIL VIEW ----------------
@Composable
fun PlaylistDetailScreen(
    title: String,
    songs: List<Song>,
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    playlistId: Long? = null,
    isLikedSongs: Boolean = false,
    isFolderSongs: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchBarVisible by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var sortType by remember { mutableStateOf("default") } // "default", "title", "artist"

    val filteredSongs = remember(songs, searchQuery) {
        if (searchQuery.isBlank()) {
            songs
        } else {
            songs.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val sortedSongs = remember(filteredSongs, sortType) {
        when (sortType) {
            "title" -> filteredSongs.sortedBy { it.title.lowercase() }
            "artist" -> filteredSongs.sortedBy { it.artist.lowercase() }
            else -> filteredSongs
        }
    }

    val lazyListState = rememberLazyListState()

    val playlistColor = when {
        isLikedSongs -> Color(0xFF4F2FE3)
        isFolderSongs -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color(0xFF1E88E5)
    }

    val backgroundBrush = remember(playlistColor) {
        Brush.verticalGradient(
            colors = listOf(
                playlistColor,
                playlistColor.copy(alpha = 0.4f),
                Color.Black
            ),
            startY = 0f,
            endY = 1000f
        )
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 15f) {
                    searchBarVisible = true
                } else if (available.y < -15f && searchQuery.isEmpty()) {
                    searchBarVisible = false
                }
                return Offset.Zero
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .nestedScroll(nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(36.dp))

            // Back button and header title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ThemeWhite)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    color = ThemeWhite,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Animated Search Bar text field
            AnimatedVisibility(
                visible = searchBarVisible,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search in playlist", color = SpotifyTextSecondary, fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SpotifyTextSecondary) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = null, tint = ThemeWhite)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SpotifyMediumGray,
                        unfocusedContainerColor = SpotifyMediumGray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = ThemeWhite,
                        unfocusedTextColor = ThemeWhite
                    ),
                    shape = RoundedCornerShape(8.dp),
                    singleLine = true
                )
            }

            LazyColumn(
                state = lazyListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        PlaylistCollage(
                            songs = songs,
                            modifier = Modifier
                                .size(160.dp)
                                .shadow(12.dp, RoundedCornerShape(8.dp)),
                            isLikedSongs = isLikedSongs,
                            isFolderSongs = isFolderSongs
                        )
                    }
                }

                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                        Text(
                            text = title,
                            color = ThemeWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${songs.size} tracks",
                            color = SpotifyTextSecondary,
                            fontSize = 13.sp
                        )
                    }
                }

                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleShuffle() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (viewModel.isShuffleEnabled) MaterialTheme.colorScheme.primary else ThemeWhite,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Sort button next to Shuffle
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = "Sort tracks",
                                    tint = if (sortType != "default") MaterialTheme.colorScheme.primary else ThemeWhite,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                                modifier = Modifier.background(SpotifyDark)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Default Order", color = if (sortType == "default") MaterialTheme.colorScheme.primary else ThemeWhite) },
                                    onClick = { sortType = "default"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Title (A-Z)", color = if (sortType == "title") MaterialTheme.colorScheme.primary else ThemeWhite) },
                                    onClick = { sortType = "title"; showSortMenu = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Artist (A-Z)", color = if (sortType == "artist") MaterialTheme.colorScheme.primary else ThemeWhite) },
                                    onClick = { sortType = "artist"; showSortMenu = false }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    if (sortedSongs.isNotEmpty()) {
                                        if (viewModel.isShuffleEnabled) {
                                            val shuffledSongs = sortedSongs.shuffled()
                                            viewModel.playSong(shuffledSongs.first(), sortedSongs)
                                        } else {
                                            viewModel.playSong(sortedSongs.first(), sortedSongs)
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                if (sortedSongs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching tracks found" else "No songs here yet!",
                                color = SpotifyTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    items(sortedSongs) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.playSong(song, sortedSongs) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                SoundScapeArtwork(
                                    song = song,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(6.dp))
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
                                    Text(
                                        text = song.artist,
                                        color = SpotifyTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (playlistId != null) {
                                IconButton(onClick = { viewModel.removeSongFromPlaylist(playlistId, song.id) }) {
                                    Icon(Icons.Default.PlaylistRemove, contentDescription = "Remove", tint = SpotifyTextSecondary)
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
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
            .height(28.dp)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                // No-op to consume touch gestures and prevent fall-through / click propagation to the Slider scrub bar underneath it
            },
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

            Row(verticalAlignment = Alignment.CenterVertically) {
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
                
                Spacer(modifier = Modifier.width(8.dp))
                
                IconButton(
                    onClick = { viewModel.showAddToPlaylistDialog = song }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to Playlist",
                        tint = ThemeWhite,
                        modifier = Modifier.size(28.dp)
                    )
                }
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

// ---------------- HIGH-PRECISION EQUALIZER VERTICAL SLIDER ----------------
@Composable
fun EqualizerVerticalSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedRange<Float>,
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true
) {
    val density = LocalDensity.current
    var heightPx by remember { mutableStateOf(0f) }

    val activeColor = if (isEnabled) MaterialTheme.colorScheme.primary else Color(0xFF48484A)
    val trackBgColor = Color(0xFF2C2C2E)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(48.dp) // wide grab area for better precision
            .onGloballyPositioned { coordinates ->
                heightPx = coordinates.size.height.toFloat()
            }
            .pointerInput(valueRange, isEnabled) {
                if (isEnabled) {
                    detectTapGestures(
                        onPress = { offset ->
                            if (heightPx > 0) {
                                val fraction = (1f - (offset.y / heightPx)).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newValue)
                            }
                        }
                    )
                }
            }
            .pointerInput(valueRange, isEnabled) {
                if (isEnabled) {
                    detectDragGestures(
                        onDrag = { change, _ ->
                            change.consume()
                            if (heightPx > 0) {
                                val fraction = (1f - (change.position.y / heightPx)).coerceIn(0f, 1f)
                                val newValue = valueRange.start + fraction * (valueRange.endInclusive - valueRange.start)
                                onValueChange(newValue)
                            }
                        }
                    )
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        val rangeLen = valueRange.endInclusive - valueRange.start
        val fraction = if (rangeLen == 0f) 0.5f else ((value - valueRange.start) / rangeLen).coerceIn(0f, 1f)

        val thumbSize = 16.dp
        val thumbSizePx = with(density) { thumbSize.toPx() }

        // Track and thumb container
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Background track
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(trackBgColor)
                    .align(Alignment.Center)
            )

            // Active track
            if (heightPx > 0) {
                val activeTrackHeight = with(density) {
                    val maxTrackHeight = heightPx - thumbSizePx
                    (fraction * maxTrackHeight).toDp() + (thumbSize / 2)
                }
                Box(
                    modifier = Modifier
                        .height(activeTrackHeight)
                        .width(3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(activeColor)
                        .align(Alignment.BottomCenter)
                )
            }

            // Thumb
            if (heightPx > 0) {
                val thumbOffset = with(density) {
                    val maxOffsetPx = heightPx - thumbSizePx
                    val offsetPx = fraction * maxOffsetPx
                    -offsetPx.toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(y = thumbOffset)
                        .size(thumbSize)
                        .clip(CircleShape)
                        .background(Color(0xFF1C1C1E))
                        .border(2.5.dp, activeColor, CircleShape)
                        .align(Alignment.BottomCenter)
                )
            }
        }
    }
}

// ---------------- PROFILE & SETTINGS EDIT DIALOG ----------------
@Composable
fun ProfileSettingsScreen(
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

    // Multi-page settings navigation state
    var activeSubPage by remember { mutableStateOf<String?>(null) }

    // Intercept system back gestures on sub-pages
    BackHandler(enabled = activeSubPage != null) {
        activeSubPage = null
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = SpotifyBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (activeSubPage == null) {
                // Header Bar for Main Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismiss(false) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        color = ThemeWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Settings Scrollable List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // "Free account" center text + "Go Premium" button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Free account",
                            color = SpotifyTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "SoundScape Premium is currently in beta! Enjoy all free features.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "GO PREMIUM",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Account Menu Card
                    SettingsMenuItem(
                        title = "Account",
                        subtitle = "Profile name • Theme presets • Folder scanning",
                        icon = Icons.Default.Person,
                        onClick = { activeSubPage = "account" }
                    )

                    // Playback Menu Card
                    SettingsMenuItem(
                        title = "Playback",
                        subtitle = "Gapless • Automix • Crossfade • Mono audio",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = { activeSubPage = "playback" }
                    )

                    // Equalizer Menu Card
                    SettingsMenuItem(
                        title = "Equalizer",
                        subtitle = "Adjust frequencies • Bass boost",
                        icon = Icons.Default.GraphicEq,
                        onClick = { activeSubPage = "equalizer" }
                    )

                    // About & Support Menu Card
                    SettingsMenuItem(
                        title = "About and support",
                        subtitle = "Version • Licenses • Terms of Use • Support",
                        icon = Icons.Default.Info,
                        onClick = { activeSubPage = "about" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Persistent bottom action bar (visible only on main menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onDismiss(false) }) {
                        Text("Cancel", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
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
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            disabledContainerColor = SpotifyLightGray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Header Bar for Sub-Pages
                val subPageTitle = when (activeSubPage) {
                    "account" -> "Account"
                    "playback" -> "Playback"
                    "equalizer" -> "Equaliser"
                    "about" -> "About and support"
                    else -> ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeSubPage = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = subPageTitle,
                        color = ThemeWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrollable Content for sub-pages
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (activeSubPage) {
                        "account" -> {
                            Text(
                                text = "SoundScape v1.4",
                                color = SpotifyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
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
                                label = { Text("Profile Name", color = SpotifyTextSecondary) },
                                isError = nameError != null,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SpotifyGreen,
                                    unfocusedBorderColor = SpotifyLightGray.copy(alpha = 0.5f),
                                    focusedLabelColor = SpotifyGreen,
                                    unfocusedLabelColor = SpotifyTextSecondary,
                                    focusedTextColor = ThemeWhite,
                                    unfocusedTextColor = ThemeWhite
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("settings_name_input")
                            )
                            if (nameError != null) {
                                Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Theme Settings Header
                            Text(
                                text = "App Theme Preset",
                                color = ThemeWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
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
                                                        color = ThemeWhite,
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
                                    .background(if (isCustomSelected) SpotifyGreen.copy(alpha = 0.1f) else SpotifyBlack)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isCustomSelected) SpotifyGreen else Color.Transparent,
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
                                        selectedColor = SpotifyGreen,
                                        unselectedColor = ThemeWhite
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Customize Theme Color",
                                    color = ThemeWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isCustomSelected) {
                                Text(
                                    text = "Pick Custom Accent Splay",
                                    color = ThemeWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
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
                                                    color = if (isColorSelected) ThemeWhite else Color.Transparent,
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
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Folder Scanning Portion
                            Text(
                                text = "Folder Scanning & Location",
                                color = ThemeWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
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
                                            colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = ThemeWhite)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = display, color = if (isSelected) SpotifyGreen else ThemeWhite, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Document Tree File Explorer Action Button for Settings Screen
                            Button(
                                onClick = { pickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyBlack),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = "Folder Picker", tint = SpotifyGreen)
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
                                    label = { Text("Custom Folder Suffix", color = SpotifyTextSecondary) },
                                    isError = pathError != null,
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SpotifyGreen,
                                        unfocusedBorderColor = SpotifyLightGray.copy(alpha = 0.5f),
                                        focusedLabelColor = SpotifyGreen,
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
                        }

                        "playback" -> {
                            // Header 1: Track transitions
                            Text("Track transitions", color = ThemeWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            // Gapless playback Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gapless playback", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Removes any gaps or pauses that may occur in between tracks.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.gaplessPlaybackEnabled,
                                    onCheckedChange = { viewModel.toggleGaplessPlayback() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            // Automix Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Automix", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Allows seamless transitions between songs on certain playlists.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.automixEnabled,
                                    onCheckedChange = { viewModel.toggleAutomix() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            // Crossfade Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Crossfade", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (viewModel.crossfadeDurationSec == 0) "Off" else "${viewModel.crossfadeDurationSec} s",
                                        color = SpotifyGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Adjust the length of fading and overlap in between tracks.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("0 s", color = SpotifyTextSecondary, fontSize = 11.sp)
                                    Slider(
                                        value = viewModel.crossfadeDurationSec.toFloat(),
                                        onValueChange = { viewModel.updateCrossfadeDuration(it.toInt()) },
                                        valueRange = 0f..12f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = SpotifyGreen,
                                            activeTrackColor = SpotifyGreen,
                                            inactiveTrackColor = SpotifyBlack
                                        ),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    Text("12 s", color = SpotifyTextSecondary, fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Header 2: Listening controls
                            Text("Listening controls", color = ThemeWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            // Mono audio Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mono audio", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Left and right speakers play the same audio.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.monoAudioEnabled,
                                    onCheckedChange = { viewModel.toggleMonoAudio() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }

                        "equalizer" -> {
                            // Equalizer Switch Row (State)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Equalizer State", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                                }
                                Switch(
                                    checked = viewModel.eqEnabled,
                                    onCheckedChange = { viewModel.toggleEqualizer() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Main Sliders Card (matches Equalizer.jpeg card styling)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 16.dp, horizontal = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        val bandsCount = viewModel.eqBands.size
                                        for (index in 0 until bandsCount) {
                                            val bandLevel = viewModel.eqBands.getOrElse(index) { 0 }
                                            val freqLabel = viewModel.getBandFrequencyLabel(index)
                                            
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // dB label at the top
                                                val dbVal = bandLevel / 100
                                                Text(
                                                    text = if (dbVal > 0) "+$dbVal" else "$dbVal",
                                                    color = if (viewModel.eqEnabled) ThemeWhite else SpotifyTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Slider
                                                EqualizerVerticalSlider(
                                                    value = bandLevel.toFloat(),
                                                    onValueChange = { value ->
                                                        viewModel.updateEqualizerBand(index, value.toInt(), isManual = true)
                                                    },
                                                    valueRange = -1500f..1500f,
                                                    modifier = Modifier.weight(1f),
                                                    isEnabled = viewModel.eqEnabled
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Frequency label at the bottom
                                                Text(
                                                    text = freqLabel,
                                                    color = SpotifyTextSecondary,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 2-Column Preset Grid
                            val presetsList = listOf(
                                "Balanced" to "Balanced",
                                "Bass boost" to "Bass boost",
                                "Smooth" to "Smooth",
                                "Dynamic" to "Dynamic",
                                "Clear" to "Clear",
                                "Treble boost" to "Treble boost",
                                "Custom" to "Custom"
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Chunk into rows of 2
                                presetsList.chunked(2).forEach { rowPresets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowPresets.forEach { (label, presetKey) ->
                                            val isSelected = viewModel.eqActivePreset.equals(presetKey, ignoreCase = true)
                                            Button(
                                                onClick = {
                                                    viewModel.applyEqualizerPreset(presetKey)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2E),
                                                    contentColor = if (isSelected) Color.Black else ThemeWhite
                                                ),
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        // If odd number, add spacer placeholder
                                        if (rowPresets.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Preset Description Text at the bottom
                            val presetDesc = when (viewModel.eqActivePreset.lowercase()) {
                                "balanced" -> "A natural sound with well-balanced frequencies."
                                "bass boost" -> "Enhance lower frequencies for deeper, punchier bass."
                                "smooth" -> "A smooth, warm sound signature that is comfortable to listen to."
                                "dynamic" -> "Vibrant, high-energy profile with rich bass and bright treble."
                                "clear" -> "Crisp vocals and clear midrange for enhanced dialogue and acoustics."
                                "treble boost" -> "Crisp, sparkling highs for enhanced detail and clarity in treble."
                                "custom" -> "Manually adjust the equalizer bands to suit your personal preference."
                                else -> "Adjust the frequency bands to customize your sound signature."
                            }

                            Text(
                                text = presetDesc,
                                color = SpotifyTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        "about" -> {
                            // Version Item
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Version", color = ThemeWhite, fontSize = 14.sp)
                                Text("SoundScape v1.4", color = SpotifyTextSecondary, fontSize = 14.sp)
                            }
                            
                            // Player Release
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Player Release", color = ThemeWhite, fontSize = 14.sp)
                                Text("2", color = SpotifyTextSecondary, fontSize = 14.sp)
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Privacy Policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Privacy Policy", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Third-party licences
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "SoundScape uses ExoPlayer, Room, and Coil under open source licenses.", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Third-party licences", color = ThemeWhite, fontSize = 14.sp)
                            }

                            // Terms of Use
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Terms of Use...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Terms of Use", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Platform Rules
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Platform Rules...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Platform Rules", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Support
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Contacting support at support@soundscape.com...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Support", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- GLOBAL SETTINGS MENU ITEM CARD ----------------
@Composable
fun SettingsMenuItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SpotifyMediumGray)
            .border(
                width = 1.dp,
                color = SpotifyLightGray.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ThemeWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = SpotifyTextSecondary,
                fontSize = 12.sp
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = SpotifyTextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

// ---------------- LEFT SIDEBAR DRAWER COMPOSABLE ----------------
@Composable
fun SpotifySidebar(
    viewModel: MusicViewModel,
    onClose: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToRecents: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(320.dp)
            .background(SpotifyDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top row: Avatar + Profile Name & Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        onNavigateToSettings()
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(SpotifyGreen, Color(0xFF1DB954).copy(alpha = 0.5f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = viewModel.userName.uppercase().take(1),
                        color = Color.Black,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = viewModel.userName,
                        color = ThemeWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "View profile",
                        color = SpotifyGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Drawer",
                    tint = ThemeWhite
                )
            }
        }

        // Divider
        HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.3f))

        // Navigation list
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val navItems = listOf(
                Triple("Listening stats", Icons.Default.BarChart, {
                    Toast.makeText(context, "Stats are compiled after 24 hours of listening!", Toast.LENGTH_SHORT).show()
                }),
                Triple("Recents", Icons.Default.History, {
                    onNavigateToRecents()
                }),
                Triple("Settings and privacy", Icons.Default.Settings, {
                    onNavigateToSettings()
                })
            )

            navItems.forEach { (label, icon, onClickAction) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClickAction() }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = ThemeWhite.copy(alpha = 0.85f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        color = ThemeWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
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
                        text = "Audio Equaliser",
                        color = ThemeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ThemeWhite)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Equalizer State Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Equalizer State", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = viewModel.eqEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyBlack,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = ThemeWhite,
                            uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Sliders Card (matches Equalizer.jpeg card styling)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val bandsCount = viewModel.eqBands.size
                            for (index in 0 until bandsCount) {
                                val bandLevel = viewModel.eqBands.getOrElse(index) { 0 }
                                val freqLabel = viewModel.getBandFrequencyLabel(index)
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val dbVal = bandLevel / 100
                                    Text(
                                        text = if (dbVal > 0) "+$dbVal" else "$dbVal",
                                        color = if (viewModel.eqEnabled) ThemeWhite else SpotifyTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    EqualizerVerticalSlider(
                                        value = bandLevel.toFloat(),
                                        onValueChange = { value ->
                                            viewModel.updateEqualizerBand(index, value.toInt(), isManual = true)
                                        },
                                        valueRange = -1500f..1500f,
                                        modifier = Modifier.weight(1f),
                                        isEnabled = viewModel.eqEnabled
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = freqLabel,
                                        color = SpotifyTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2-Column Preset Grid
                val presetsList = listOf(
                    "Balanced" to "Balanced",
                    "Bass boost" to "Bass boost",
                    "Smooth" to "Smooth",
                    "Dynamic" to "Dynamic",
                    "Clear" to "Clear",
                    "Treble boost" to "Treble boost",
                    "Custom" to "Custom"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetsList.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (label, presetKey) ->
                                val isSelected = viewModel.eqActivePreset.equals(presetKey, ignoreCase = true)
                                Button(
                                    onClick = {
                                        viewModel.applyEqualizerPreset(presetKey)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2E),
                                        contentColor = if (isSelected) Color.Black else ThemeWhite
                                    ),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (rowPresets.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preset Description Text
                val presetDesc = when (viewModel.eqActivePreset.lowercase()) {
                    "balanced" -> "A natural sound with well-balanced frequencies."
                    "bass boost" -> "Enhance lower frequencies for deeper, punchier bass."
                    "smooth" -> "A smooth, warm sound signature that is comfortable to listen to."
                    "dynamic" -> "Vibrant, high-energy profile with rich bass and bright treble."
                    "clear" -> "Crisp vocals and clear midrange for enhanced dialogue and acoustics."
                    "treble boost" -> "Crisp, sparkling highs for enhanced detail and clarity in treble."
                    "custom" -> "Manually adjust the equalizer bands to suit your personal preference."
                    else -> "Adjust the frequency bands to customize your sound signature."
                }

                Text(
                    text = presetDesc,
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
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
