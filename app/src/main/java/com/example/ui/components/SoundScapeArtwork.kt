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
        com.example.util.AppLogger.w("MainActivity", "Failed to extract embedded picture for song", e)
        null
    } finally {
        try {
            retriever.release()
        } catch (e: Exception) {
            com.example.util.AppLogger.w("MainActivity", "Failed to release MediaMetadataRetriever", e)
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
                text = if (firstLetter.isNotEmpty() && firstLetter.all { it.isLetterOrDigit() }) firstLetter else "🎵",
                color = Color.White.copy(alpha = 0.9f),
                style = MaterialTheme.typography.bodyLarge,
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
        com.example.util.AppLogger.e("MainActivity", "Failed to resolve folder search filter from URI", e)
    }
    return uri.lastPathSegment ?: ""
}
