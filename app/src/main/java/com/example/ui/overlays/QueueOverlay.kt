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
import com.example.ui.theme.heavyShadow
import com.example.ui.theme.SoundScapeType
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

    // Guards against rapid repeated taps on the same row's move/remove buttons: the queue
    // mutates (and rows shift) between the first tap landing and recomposition, so a fast
    // second tap can otherwise act on a stale realIndex pointing at a different song. See
    // rememberActionGuard for why this locks on the action settling rather than a fixed time
    // window.
    val guardedQueueAction = rememberActionGuard(queue, currentIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .heavyShadow(SoundScapeShapes.comfortable)
            .background(SpotifyBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { guardedQueueAction(onDismiss) }) {
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
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (currentSong != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(SoundScapeShapes.comfortable)
                    .background(SpotifyMediumGray)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SoundScapeArtwork(
                    song = currentSong,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(SoundScapeShapes.subtle)
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
                style = MaterialTheme.typography.bodyLarge
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
                itemsIndexed(upcomingQueue, key = { index, song -> "$index-${song.id}" }) { index, song ->
                    val realIndex = index + currentIndex + 1
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(SoundScapeShapes.standard)
                            .background(SpotifyMediumGray)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}",
                                color = SpotifyTextSecondary,
                                style = SoundScapeType.small,
                                modifier = Modifier.width(20.dp)
                            )
                            SoundScapeArtwork(
                                song = song,
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(SoundScapeShapes.subtle)
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
                                IconButton(onClick = { guardedQueueAction { viewModel.reorderQueue(realIndex, realIndex - 1) } }) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = ThemeWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < upcomingQueue.size - 1) {
                                IconButton(onClick = { guardedQueueAction { viewModel.reorderQueue(realIndex, realIndex + 1) } }) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = ThemeWhite, modifier = Modifier.size(16.dp))
                                }
                            }
                            IconButton(onClick = { guardedQueueAction { viewModel.removeFromQueueAt(realIndex) } }) {
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
