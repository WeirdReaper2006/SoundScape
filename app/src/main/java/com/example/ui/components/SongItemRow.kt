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
fun SongItemRow(
    song: Song,
    onClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onEditTags: () -> Unit,
    onRemoveFromPlaylist: (() -> Unit)? = null,
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
            .let { if (useCardStyle) it.mediumShadow(SoundScapeShapes.standard) else it }
            .clip(SoundScapeShapes.standard)
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
                    .clip(SoundScapeShapes.subtle)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = song.title,
                    color = ThemeWhite,
                    style = SoundScapeType.captionBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = song.artist,
                    color = SpotifyTextSecondary,
                    style = SoundScapeType.small,
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
                    modifier = Modifier.mediumShadow().background(SpotifyDark)
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
                    if (onRemoveFromPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Remove from Playlist", color = ThemeWhite) },
                            leadingIcon = { Icon(Icons.Default.PlaylistRemove, contentDescription = null, tint = ThemeWhite, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                onRemoveFromPlaylist()
                                showMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}

// ---------------- STAGE 2: PREMIUM AUDIO WAVEFORM VISUALIZER ----------------
