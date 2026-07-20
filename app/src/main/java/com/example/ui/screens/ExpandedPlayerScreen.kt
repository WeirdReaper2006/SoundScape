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
