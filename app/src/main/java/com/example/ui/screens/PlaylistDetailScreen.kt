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
import com.example.ui.theme.SoundScapeShapes
import com.example.ui.theme.mediumShadow
import com.example.ui.components.CircularPlayButton
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
                    shape = SoundScapeShapes.comfortable,
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
                                .shadow(12.dp, SoundScapeShapes.comfortable),
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
                            style = MaterialTheme.typography.titleLarge
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
                                modifier = Modifier.mediumShadow(SoundScapeShapes.standard).background(SpotifyDark)
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

                        CircularPlayButton(
                            onClick = {
                                if (sortedSongs.isNotEmpty()) {
                                    if (viewModel.isShuffleEnabled) {
                                        val shuffledSongs = sortedSongs.shuffled()
                                        viewModel.playSong(shuffledSongs.first(), sortedSongs)
                                    } else {
                                        viewModel.playSong(sortedSongs.first(), sortedSongs)
                                    }
                                }
                            },
                            size = 56.dp,
                            iconSize = 32.dp
                        )
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
                        SongItemRow(
                            song = song,
                            onClick = { viewModel.playSong(song, sortedSongs) },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog = song },
                            onAddToQueue = { viewModel.addToQueue(song) },
                            onEditTags = { viewModel.showEditTagsDialog = song },
                            onRemoveFromPlaylist = if (playlistId != null) {
                                { viewModel.removeSongFromPlaylist(playlistId, song.id) }
                            } else null
                        )
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
