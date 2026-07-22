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
import com.example.ui.theme.SoundScapeType
import com.example.ui.theme.mediumShadow
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
                                    .clip(SoundScapeShapes.comfortable)
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
                                style = SoundScapeType.micro,
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
                                    .clip(SoundScapeShapes.standard)
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

                                var showQuickPlayMenu by remember { mutableStateOf(false) }
                                Box {
                                    IconButton(
                                        onClick = { showQuickPlayMenu = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More song options",
                                            tint = SpotifyTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = showQuickPlayMenu,
                                        onDismissRequest = { showQuickPlayMenu = false },
                                        modifier = Modifier
                                            .mediumShadow(SoundScapeShapes.standard)
                                            .background(SpotifyDark)
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Add to Queue", color = ThemeWhite) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = SpotifyGreen, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                viewModel.addToQueue(song)
                                                showQuickPlayMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Add to Playlist", color = ThemeWhite) },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                viewModel.showAddToPlaylistDialog = song
                                                showQuickPlayMenu = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Edit Metadata", color = ThemeWhite) },
                                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                                            onClick = {
                                                viewModel.showEditTagsDialog = song
                                                showQuickPlayMenu = false
                                            }
                                        )
                                    }
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
                        .clip(SoundScapeShapes.comfortable)
                        .background(SpotifyMediumGray),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No genres identified yet! Ensure your music files have genre tags.",
                        color = SpotifyTextSecondary,
                        style = SoundScapeType.small,
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
                                                .clip(SoundScapeShapes.comfortable)
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
