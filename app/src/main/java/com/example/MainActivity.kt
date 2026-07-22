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
import androidx.compose.ui.graphics.luminance
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

val SpotifyGreen: Color @Composable get() = MaterialTheme.colorScheme.primary
val SpotifyBlack: Color @Composable get() = MaterialTheme.colorScheme.background
val SpotifyDark: Color @Composable get() = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) Color(0xFFE9ECEF) else MaterialTheme.colorScheme.background
val SpotifyMediumGray: Color @Composable get() = MaterialTheme.colorScheme.surface
val SpotifyLightGray: Color @Composable get() = MaterialTheme.colorScheme.tertiary
val SpotifyTextSecondary: Color @Composable get() = MaterialTheme.colorScheme.secondary
val ThemeWhite: Color @Composable get() = MaterialTheme.colorScheme.onBackground

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PrefsKeys.migrateLegacyPrefsIfNeeded(this)
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            SoundScapeTheme(
                themePreset = viewModel.themePreset,
                isDark = viewModel.themeIsDark,
                customColorHex = viewModel.themeCustomColor
            ) {
                val context = LocalContext.current

                // Request Storage and Notification Permissions
                val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    listOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                } else {
                    listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }

                val permissionState = rememberMultiplePermissionsState(permissions = permissionsToRequest) { results ->
                    viewModel.refreshLibrary()
                }

                LaunchedEffect(Unit) {
                    if (!permissionState.allPermissionsGranted) {
                        permissionState.launchMultiplePermissionRequest()
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (viewModel.isOnboardingCompleted) {
                        SpotifyScaffold(viewModel)
                    } else {
                        OnboardingScreen(
                            onComplete = { name, path ->
                                viewModel.updateProfile(name, path)
                            }
                        )
                    }
                }
            }
        }
    }
}

