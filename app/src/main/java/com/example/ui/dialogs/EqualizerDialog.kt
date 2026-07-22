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
import com.example.ui.components.SelectablePillButton
@Composable
fun EqualizerDialog(
    viewModel: MusicViewModel,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .heavyShadow(SoundScapeShapes.panel),
            colors = CardDefaults.cardColors(containerColor = SpotifyDark),
            shape = SoundScapeShapes.panel
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Audio Equaliser",
                        color = ThemeWhite,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = ThemeWhite)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Equalizer State Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Equalizer State", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Switch(
                        checked = viewModel.eqEnabled,
                        onCheckedChange = { viewModel.toggleEqualizer() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SpotifyBlack,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = ThemeWhite,
                            uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Main Sliders Card (matches Equalizer.jpeg card styling)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                    shape = SoundScapeShapes.panel
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val bandsCount = viewModel.eqBands.size
                            for (index in 0 until bandsCount) {
                                val bandLevel = viewModel.eqBands.getOrElse(index) { 0 }
                                val freqLabel = viewModel.getBandFrequencyLabel(index)
                                
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val dbVal = bandLevel / 100
                                    Text(
                                        text = if (dbVal > 0) "+$dbVal" else "$dbVal",
                                        color = if (viewModel.eqEnabled) ThemeWhite else SpotifyTextSecondary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    EqualizerVerticalSlider(
                                        value = bandLevel.toFloat(),
                                        onValueChange = { value ->
                                            viewModel.updateEqualizerBand(index, value.toInt(), isManual = true)
                                        },
                                        valueRange = -1500f..1500f,
                                        modifier = Modifier.weight(1f),
                                        isEnabled = viewModel.eqEnabled
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = freqLabel,
                                        color = SpotifyTextSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2-Column Preset Grid
                val presetsList = listOf(
                    "Balanced" to "Balanced",
                    "Bass boost" to "Bass boost",
                    "Smooth" to "Smooth",
                    "Dynamic" to "Dynamic",
                    "Clear" to "Clear",
                    "Treble boost" to "Treble boost",
                    "Custom" to "Custom"
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetsList.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { (label, presetKey) ->
                                val isSelected = viewModel.eqActivePreset.equals(presetKey, ignoreCase = true)
                                SelectablePillButton(
                                    text = label,
                                    selected = isSelected,
                                    onClick = {
                                        viewModel.applyEqualizerPreset(presetKey)
                                    },
                                    modifier = Modifier.weight(1f),
                                    height = 40.dp,
                                    unselectedContentColor = ThemeWhite
                                )
                            }
                            if (rowPresets.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Preset Description Text
                val presetDesc = when (viewModel.eqActivePreset.lowercase()) {
                    "balanced" -> "A natural sound with well-balanced frequencies."
                    "bass boost" -> "Enhance lower frequencies for deeper, punchier bass."
                    "smooth" -> "A smooth, warm sound signature that is comfortable to listen to."
                    "dynamic" -> "Vibrant, high-energy profile with rich bass and bright treble."
                    "clear" -> "Crisp vocals and clear midrange for enhanced dialogue and acoustics."
                    "treble boost" -> "Crisp, sparkling highs for enhanced detail and clarity in treble."
                    "custom" -> "Manually adjust the equalizer bands to suit your personal preference."
                    else -> "Adjust the frequency bands to customize your sound signature."
                }

                Text(
                    text = presetDesc,
                    color = SpotifyTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

// ---------------- PREMIUM COMPOSABLE: EDIT METADATA TAGS DIALOG ----------------
