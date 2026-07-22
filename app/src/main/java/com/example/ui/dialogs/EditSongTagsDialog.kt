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
import com.example.ui.components.DarkPillButton
@Composable
fun EditSongTagsDialog(
    song: Song,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }

    val titleError = (InputValidator.validateMetadataField(title, "Title", required = true) as? InputValidator.ValidationResult.Invalid)?.reason
    val artistError = (InputValidator.validateMetadataField(artist, "Artist", required = false) as? InputValidator.ValidationResult.Invalid)?.reason
    val albumError = (InputValidator.validateMetadataField(album, "Album", required = false) as? InputValidator.ValidationResult.Invalid)?.reason
    val isMetadataValid = titleError == null && artistError == null && albumError == null

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heavyShadow(SoundScapeShapes.panel),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = SoundScapeShapes.panel
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
                    isError = titleError != null,
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
                if (titleError != null) {
                    Text(titleError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist", color = SpotifyTextSecondary) },
                    isError = artistError != null,
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
                if (artistError != null) {
                    Text(artistError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album", color = SpotifyTextSecondary) },
                    isError = albumError != null,
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
                if (albumError != null) {
                    Text(albumError, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = ThemeWhite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    DarkPillButton(
                        text = "Save",
                        onClick = { onConfirm(title, artist, album) },
                        enabled = isMetadataValid,
                        contentColor = SpotifyDark
                    )
                }
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: IMPORT M3U DIALOG ----------------
