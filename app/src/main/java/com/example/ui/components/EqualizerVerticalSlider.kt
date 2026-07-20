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
