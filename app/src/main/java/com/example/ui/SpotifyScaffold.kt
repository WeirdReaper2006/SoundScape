package com.example

import android.Manifest
import com.soundscape.BuildConfig
import com.soundscape.R
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.db.PlaylistEntity
import com.example.data.models.Song
import androidx.media3.common.Player
import com.example.ui.theme.SoundScapeTheme
import com.example.ui.viewmodel.MusicViewModel
import com.example.util.InputValidator
import com.example.util.PrefsKeys
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
import com.example.ui.theme.mediumShadow
@Composable
fun SpotifyScaffold(viewModel: MusicViewModel) {
    val context = LocalContext.current
    var isExpandedPlayerVisible by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }

    var showProfileSettingsDialog by remember { mutableStateOf(false) }
    var showQueueOverlay by remember { mutableStateOf(false) }
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // Detail-style destinations (playlist detail, virtual playlists, recents) that overlay the
    // three peer bottom tabs. The tabs themselves stay index-driven below - they're peers, not a
    // stack, so modeling them as NavHost routes would add nothing but risk.
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val onNonTabsRoute = currentRoute != null && currentRoute != "tabs"

    // Guards against rapid repeated taps across navigate/pop/drawer-toggle/tab-switch actions.
    // Without this, spamming taps across the sidebar, bottom tabs, and playlist navigation can
    // fire overlapping mutations to the nav back stack, activeTabIndex, and drawerState at once,
    // leaving them out of sync and the screen stuck blank.
    var lastNavActionTime by remember { mutableStateOf(0L) }
    val navGuardMs = 400L
    fun guardedNav(action: () -> Unit) {
        val now = System.currentTimeMillis()
        if (now - lastNavActionTime >= navGuardMs) {
            lastNavActionTime = now
            action()
        }
    }

    // Bridges the legacy showRecentsPage flag (still flipped by HomeScreen's "Recently Played"
    // row) onto the nav back stack without needing to touch HomeScreen.
    LaunchedEffect(viewModel.showRecentsPage) {
        if (viewModel.showRecentsPage) {
            navController.navigate("recents") { launchSingleTop = true }
            viewModel.showRecentsPage = false
        }
    }

    // Real-time cohesive back and navigation routing interceptor
    BackHandler(enabled = drawerState.isOpen || isExpandedPlayerVisible || showQueueOverlay || showProfileSettingsDialog || onNonTabsRoute || viewModel.activeTabIndex != 0) {
        when {
            drawerState.isOpen -> coroutineScope.launch { drawerState.close() }
            showQueueOverlay -> showQueueOverlay = false
            showProfileSettingsDialog -> {
                viewModel.previewTheme(viewModel.themePreset, viewModel.themeIsDark, viewModel.themeCustomColor)
                showProfileSettingsDialog = false
            }
            isExpandedPlayerVisible -> isExpandedPlayerVisible = false
            onNonTabsRoute -> navController.popBackStack()
            viewModel.activeTabIndex != 0 -> viewModel.activeTabIndex = 0
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = false,
        drawerContent = {
            SpotifySidebar(
                viewModel = viewModel,
                onClose = { guardedNav { coroutineScope.launch { drawerState.close() } } },
                onNavigateToSettings = {
                    guardedNav {
                        coroutineScope.launch { drawerState.close() }
                        showProfileSettingsDialog = true
                    }
                },
                onNavigateToRecents = {
                    guardedNav {
                        coroutineScope.launch { drawerState.close() }
                        viewModel.showRecentsPage = true
                    }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier
                        .mediumShadow()
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
                                guardedNav {
                                    viewModel.activeTabIndex = 0
                                    navController.popBackStack("tabs", inclusive = false)
                                    viewModel.showRecentsPage = false
                                }
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
                                guardedNav {
                                    viewModel.activeTabIndex = 1
                                    navController.popBackStack("tabs", inclusive = false)
                                    viewModel.showRecentsPage = false
                                }
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
                                guardedNav {
                                    viewModel.activeTabIndex = 2
                                    navController.popBackStack("tabs", inclusive = false)
                                    viewModel.showRecentsPage = false
                                }
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
                NavHost(navController = navController, startDestination = "tabs") {
                    composable("tabs") {
                        when (viewModel.activeTabIndex) {
                            0 -> HomeScreen(
                                viewModel = viewModel,
                                onPlaylistSelect = { guardedNav { navController.navigate("playlist_detail/${it.id}") { launchSingleTop = true } } },
                                onProfileClick = { guardedNav { coroutineScope.launch { drawerState.open() } } }
                            )
                            1 -> SearchScreen(
                                viewModel = viewModel,
                                onProfileClick = { guardedNav { coroutineScope.launch { drawerState.open() } } }
                            )
                            2 -> LibraryScreen(
                                viewModel = viewModel,
                                onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                onPlaylistClick = { guardedNav { navController.navigate("playlist_detail/${it.id}") { launchSingleTop = true } } },
                                onVirtualPlaylistClick = { guardedNav { navController.navigate("virtual_playlist/$it") { launchSingleTop = true } } },
                                onProfileClick = { guardedNav { coroutineScope.launch { drawerState.open() } } }
                            )
                        }
                    }
                    composable(
                        "playlist_detail/{playlistId}",
                        arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                    ) { backStackEntry ->
                        val playlistId = backStackEntry.arguments!!.getLong("playlistId")
                        val playlist = playlists.find { it.id == playlistId }
                        val playlistSongs by viewModel.getPlaylistSongs(playlistId).collectAsStateWithLifecycle(emptyList())
                        PlaylistDetailScreen(
                            title = playlist?.name ?: "",
                            songs = playlistSongs,
                            viewModel = viewModel,
                            onBack = { guardedNav { navController.popBackStack() } },
                            playlistId = playlistId
                        )
                    }
                    composable(
                        "virtual_playlist/{type}",
                        arguments = listOf(navArgument("type") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val virtualPlaylistType = backStackEntry.arguments!!.getString("type")
                        val title = if (virtualPlaylistType == "liked_songs") "Liked Songs" else {
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
                            songs = if (virtualPlaylistType == "liked_songs") favoriteList else viewModel.allSongs,
                            viewModel = viewModel,
                            onBack = { guardedNav { navController.popBackStack() } },
                            playlistId = null,
                            isLikedSongs = (virtualPlaylistType == "liked_songs"),
                            isFolderSongs = (virtualPlaylistType == "folder_songs")
                        )
                    }
                    composable("recents") {
                        RecentsScreen(
                            viewModel = viewModel,
                            onBack = { guardedNav { navController.popBackStack() } }
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
