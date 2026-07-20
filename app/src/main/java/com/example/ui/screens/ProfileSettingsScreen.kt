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
fun ProfileSettingsScreen(
    viewModel: MusicViewModel,
    onDismiss: (saved: Boolean) -> Unit
) {
    val context = LocalContext.current
    var newName by remember { mutableStateOf(viewModel.userName) }
    
    // Scan Path choices: Mapping path stored (Music, Download, "", Custom) back to type choice
    val initialTypeChoice = remember(viewModel.musicPath) {
        when (viewModel.musicPath) {
            "Music" -> "Music Presets"
            "Download" -> "Downloads Folder"
            "" -> "Entire Storage"
            else -> "Custom Folder"
        }
    }
    var pathType by remember { mutableStateOf(initialTypeChoice) }
    var customPathInput by remember { mutableStateOf(if (initialTypeChoice == "Custom Folder") viewModel.musicPath else "") }
    
    var selectedThemePreset by remember { mutableStateOf(viewModel.themePreset) }
    var selectedIsDark by remember { mutableStateOf(viewModel.themeIsDark) }
    var selectedCustomColor by remember { mutableStateOf(viewModel.themeCustomColor) }

    // Live preview theme as choices change
    LaunchedEffect(selectedThemePreset, selectedIsDark, selectedCustomColor) {
        viewModel.previewTheme(selectedThemePreset, selectedIsDark, selectedCustomColor)
    }

    var nameError by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf<String?>(null) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            val filter = getFolderSearchFilterFromUri(context, uri)
            if (filter.isNotEmpty()) {
                customPathInput = filter
                pathType = "Custom Folder"
            }
        }
    }
    
    val isFormValid = remember(newName, pathType, customPathInput) {
        val nameOk = InputValidator.validateName(newName) is InputValidator.ValidationResult.Valid
        val pathOk = if (pathType == "Custom Folder") {
            InputValidator.validateFolderSuffix(customPathInput) is InputValidator.ValidationResult.Valid
        } else {
            true
        }
        nameOk && pathOk
    }

    // Multi-page settings navigation state
    var activeSubPage by remember { mutableStateOf<String?>(null) }

    // Intercept system back gestures on sub-pages
    BackHandler(enabled = activeSubPage != null) {
        activeSubPage = null
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        color = SpotifyBlack
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (activeSubPage == null) {
                // Header Bar for Main Settings
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { onDismiss(false) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Settings",
                        color = ThemeWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Main Settings Scrollable List
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // "Free account" center text + "Go Premium" button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = "Free account",
                            color = SpotifyTextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                Toast.makeText(context, "SoundScape Premium is currently in beta! Enjoy all free features.", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SpotifyGreen),
                            shape = RoundedCornerShape(24.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = "GO PREMIUM",
                                color = Color.Black,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Account Menu Card
                    SettingsMenuItem(
                        title = "Account",
                        subtitle = "Profile name • Theme presets • Folder scanning",
                        icon = Icons.Default.Person,
                        onClick = { activeSubPage = "account" }
                    )

                    // Playback Menu Card
                    SettingsMenuItem(
                        title = "Playback",
                        subtitle = "Gapless • Automix • Crossfade • Mono audio",
                        icon = Icons.AutoMirrored.Filled.VolumeUp,
                        onClick = { activeSubPage = "playback" }
                    )

                    // Equalizer Menu Card
                    SettingsMenuItem(
                        title = "Equalizer",
                        subtitle = "Adjust frequencies • Bass boost",
                        icon = Icons.Default.GraphicEq,
                        onClick = { activeSubPage = "equalizer" }
                    )

                    // About & Support Menu Card
                    SettingsMenuItem(
                        title = "About and support",
                        subtitle = "Version • Licenses • Terms of Use • Support",
                        icon = Icons.Default.Info,
                        onClick = { activeSubPage = "about" }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Persistent bottom action bar (visible only on main menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { onDismiss(false) }) {
                        Text("Cancel", color = ThemeWhite, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (isFormValid) {
                                val finalPath = when (pathType) {
                                    "Music Presets" -> "Music"
                                    "Downloads Folder" -> "Download"
                                    "Entire Storage" -> ""
                                    else -> customPathInput.trim()
                                }
                                viewModel.updateProfile(newName.trim(), finalPath)
                                viewModel.updateTheme(selectedThemePreset, selectedIsDark, selectedCustomColor)
                                onDismiss(true)
                            }
                        },
                        enabled = isFormValid,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpotifyGreen,
                            disabledContainerColor = SpotifyLightGray.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(
                            text = "Save",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                // Header Bar for Sub-Pages
                val subPageTitle = when (activeSubPage) {
                    "account" -> "Account"
                    "playback" -> "Playback"
                    "equalizer" -> "Equaliser"
                    "about" -> "About and support"
                    else -> ""
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { activeSubPage = null }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = ThemeWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = subPageTitle,
                        color = ThemeWhite,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrollable Content for sub-pages
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (activeSubPage) {
                        "account" -> {
                            Text(
                                text = "SoundScape v${BuildConfig.VERSION_NAME}",
                                color = SpotifyGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )

                            // Editable Name
                            OutlinedTextField(
                                value = newName,
                                onValueChange = {
                                    newName = it
                                    nameError = (InputValidator.validateName(it) as? InputValidator.ValidationResult.Invalid)?.reason
                                },
                                label = { Text("Profile Name", color = SpotifyTextSecondary) },
                                isError = nameError != null,
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = SpotifyGreen,
                                    unfocusedBorderColor = SpotifyLightGray.copy(alpha = 0.5f),
                                    focusedLabelColor = SpotifyGreen,
                                    unfocusedLabelColor = SpotifyTextSecondary,
                                    focusedTextColor = ThemeWhite,
                                    unfocusedTextColor = ThemeWhite
                                ),
                                modifier = Modifier.fillMaxWidth().testTag("settings_name_input")
                            )
                            if (nameError != null) {
                                Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Theme Settings Header
                            Text(
                                text = "App Theme Preset",
                                color = ThemeWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Theme Presets Grid / Rows
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val presets = listOf(
                                    Triple("green", "Emerald Green", Color(0xFF1DB954)),
                                    Triple("sunset", "Sunset Glow", Color(0xFFFF9800)),
                                    Triple("blue", "Electric Blue", Color(0xFF2979FF)),
                                    Triple("violet", "Amethyst violet", Color(0xFF9C27B0)),
                                    Triple("crimson", "Crimson Pulse", Color(0xFFE91E63))
                                )
                                
                                presets.chunked(2).forEach { chunk ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        chunk.forEach { (key, title, color) ->
                                            val isSelected = selectedThemePreset == key
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) color.copy(alpha = 0.25f) else SpotifyBlack)
                                                    .border(
                                                        width = 1.5.dp,
                                                        color = if (isSelected) color else Color.Transparent,
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { selectedThemePreset = key }
                                                    .padding(10.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(14.dp)
                                                            .clip(CircleShape)
                                                            .background(color)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = title,
                                                        color = ThemeWhite,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
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

                            // Custom theme option
                            val isCustomSelected = selectedThemePreset == "custom"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCustomSelected) SpotifyGreen.copy(alpha = 0.1f) else SpotifyBlack)
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isCustomSelected) SpotifyGreen else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { selectedThemePreset = "custom" }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isCustomSelected,
                                    onClick = { selectedThemePreset = "custom" },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = SpotifyGreen,
                                        unselectedColor = ThemeWhite
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Customize Theme Color",
                                    color = ThemeWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (isCustomSelected) {
                                Text(
                                    text = "Pick Custom Accent Splay",
                                    color = ThemeWhite,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val colorSwatches = listOf(
                                        "#00FFCC" to Color(0xFF00FFCC), // Mint Teal
                                        "#FF007F" to Color(0xFFFF007F), // Neon Pink
                                        "#00E5FF" to Color(0xFF00E5FF), // Aqua Cyan
                                        "#FFE082" to Color(0xFFFFE082), // Soft Amber
                                        "#00E676" to Color(0xFF00E676), // Emerald
                                        "#BF5AF2" to Color(0xFFBF5AF2)  // Purple Bloom
                                    )
                                    colorSwatches.forEach { (hex, tintColor) ->
                                        val isColorSelected = selectedCustomColor.equals(hex, ignoreCase = true)
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .clip(CircleShape)
                                                .background(tintColor)
                                                .border(
                                                    width = 3.dp,
                                                    color = if (isColorSelected) ThemeWhite else Color.Transparent,
                                                    shape = CircleShape
                                                )
                                                .clickable { selectedCustomColor = hex },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isColorSelected) {
                                                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.Black, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            }

                            // Appearance Mode Toggle (Light / Dark Theme)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpotifyBlack)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (selectedIsDark) Icons.Filled.Brightness4 else Icons.Filled.Brightness7,
                                        contentDescription = "Appearance Mode",
                                        tint = ThemeWhite,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Appearance Mode",
                                            color = ThemeWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = if (selectedIsDark) "Dark Mode" else "Light Mode",
                                            color = SpotifyTextSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Switch(
                                    checked = selectedIsDark,
                                    onCheckedChange = { selectedIsDark = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Folder Scanning Portion
                            Text(
                                text = "Folder Scanning & Location",
                                color = ThemeWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SpotifyBlack)
                                    .padding(4.dp)
                            ) {
                                listOf(
                                    "Standard Music Folder" to "Music Presets",
                                    "Downloads Folder" to "Downloads Folder",
                                    "Custom Folder name" to "Custom Folder",
                                    "Scan Entire Storage (No filtering)" to "Entire Storage"
                                ).forEach { (display, type) ->
                                    val isSelected = pathType == type
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { pathType = type }
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = { pathType = type },
                                            colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = ThemeWhite)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(text = display, color = if (isSelected) SpotifyGreen else ThemeWhite, fontSize = 12.sp)
                                    }
                                }
                            }

                            // Document Tree File Explorer Action Button for Settings Screen
                            Button(
                                onClick = { pickerLauncher.launch(null) },
                                colors = ButtonDefaults.buttonColors(containerColor = SpotifyBlack),
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Filled.Folder, contentDescription = "Folder Picker", tint = SpotifyGreen)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Select via File Explorer", color = ThemeWhite, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            if (pathType == "Custom Folder") {
                                OutlinedTextField(
                                    value = customPathInput,
                                    onValueChange = {
                                        customPathInput = it
                                        pathError = (InputValidator.validateFolderSuffix(it) as? InputValidator.ValidationResult.Invalid)?.reason
                                    },
                                    label = { Text("Custom Folder Suffix", color = SpotifyTextSecondary) },
                                    isError = pathError != null,
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = SpotifyGreen,
                                        unfocusedBorderColor = SpotifyLightGray.copy(alpha = 0.5f),
                                        focusedLabelColor = SpotifyGreen,
                                        unfocusedLabelColor = SpotifyTextSecondary,
                                        focusedTextColor = ThemeWhite,
                                        unfocusedTextColor = ThemeWhite
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("settings_custom_path_input")
                                )
                                if (pathError != null) {
                                    Text(pathError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        }

                        "playback" -> {
                            // Header 1: Track transitions
                            Text("Track transitions", color = ThemeWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            // Gapless playback Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gapless playback", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Removes any gaps or pauses that may occur in between tracks.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.gaplessPlaybackEnabled,
                                    onCheckedChange = { viewModel.toggleGaplessPlayback() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            // Automix Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Automix", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Allows seamless transitions between songs on certain playlists.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.automixEnabled,
                                    onCheckedChange = { viewModel.toggleAutomix() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }

                            // Crossfade Slider
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Crossfade", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        text = if (viewModel.crossfadeDurationSec == 0) "Off" else "${viewModel.crossfadeDurationSec} s",
                                        color = SpotifyGreen,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text("Adjust the length of fading and overlap in between tracks.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("0 s", color = SpotifyTextSecondary, fontSize = 11.sp)
                                    Slider(
                                        value = viewModel.crossfadeDurationSec.toFloat(),
                                        onValueChange = { viewModel.updateCrossfadeDuration(it.toInt()) },
                                        valueRange = 0f..12f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = SpotifyGreen,
                                            activeTrackColor = SpotifyGreen,
                                            inactiveTrackColor = SpotifyBlack
                                        ),
                                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                                    )
                                    Text("12 s", color = SpotifyTextSecondary, fontSize = 11.sp)
                                }
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Header 2: Listening controls
                            Text("Listening controls", color = ThemeWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)

                            // Mono audio Switch
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mono audio", color = ThemeWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Left and right speakers play the same audio.", color = SpotifyTextSecondary, fontSize = 10.sp)
                                }
                                Switch(
                                    checked = viewModel.monoAudioEnabled,
                                    onCheckedChange = { viewModel.toggleMonoAudio() },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = SpotifyBlack,
                                        checkedTrackColor = SpotifyGreen,
                                        uncheckedThumbColor = ThemeWhite,
                                        uncheckedTrackColor = ThemeWhite.copy(alpha = 0.2f)
                                    )
                                )
                            }
                        }

                        "equalizer" -> {
                            // Equalizer Switch Row (State)
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
                                    .height(260.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(vertical = 16.dp, horizontal = 12.dp),
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
                                                // dB label at the top
                                                val dbVal = bandLevel / 100
                                                Text(
                                                    text = if (dbVal > 0) "+$dbVal" else "$dbVal",
                                                    color = if (viewModel.eqEnabled) ThemeWhite else SpotifyTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Slider
                                                EqualizerVerticalSlider(
                                                    value = bandLevel.toFloat(),
                                                    onValueChange = { value ->
                                                        viewModel.updateEqualizerBand(index, value.toInt(), isManual = true)
                                                    },
                                                    valueRange = -1500f..1500f,
                                                    modifier = Modifier.weight(1f),
                                                    isEnabled = viewModel.eqEnabled
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // Frequency label at the bottom
                                                Text(
                                                    text = freqLabel,
                                                    color = SpotifyTextSecondary,
                                                    fontSize = 10.sp,
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
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Chunk into rows of 2
                                presetsList.chunked(2).forEach { rowPresets ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        rowPresets.forEach { (label, presetKey) ->
                                            val isSelected = viewModel.eqActivePreset.equals(presetKey, ignoreCase = true)
                                            Button(
                                                onClick = {
                                                    viewModel.applyEqualizerPreset(presetKey)
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF2C2C2E),
                                                    contentColor = if (isSelected) Color.Black else ThemeWhite
                                                ),
                                                shape = RoundedCornerShape(24.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = label,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                        // If odd number, add spacer placeholder
                                        if (rowPresets.size < 2) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Preset Description Text at the bottom
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
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        "about" -> {
                            // Version Item
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Version", color = ThemeWhite, fontSize = 14.sp)
                                Text("SoundScape v${BuildConfig.VERSION_NAME}", color = SpotifyTextSecondary, fontSize = 14.sp)
                            }
                            
                            // Player Release
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Player Release", color = ThemeWhite, fontSize = 14.sp)
                                Text("2", color = SpotifyTextSecondary, fontSize = 14.sp)
                            }

                            HorizontalDivider(color = SpotifyLightGray.copy(alpha = 0.2f))

                            // Privacy Policy
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Privacy Policy...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Privacy Policy", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Third-party licences
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "SoundScape uses ExoPlayer, Room, and Coil under open source licenses.", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Third-party licences", color = ThemeWhite, fontSize = 14.sp)
                            }

                            // Terms of Use
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Terms of Use...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Terms of Use", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Platform Rules
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Opening Platform Rules...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Platform Rules", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }

                            // Support
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        Toast.makeText(context, "Contacting support at support@soundscape.com...", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Support", color = ThemeWhite, fontSize = 14.sp)
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = SpotifyTextSecondary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- GLOBAL SETTINGS MENU ITEM CARD ----------------
