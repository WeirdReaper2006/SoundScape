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
