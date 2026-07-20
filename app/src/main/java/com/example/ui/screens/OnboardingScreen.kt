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
fun OnboardingScreen(onComplete: (String, String) -> Unit) {
    val context = LocalContext.current
    var nameInput by remember { mutableStateOf("Offline Listener") }
    
    // Scan Path choices: "Music" (preset), "Download" (preset), "Custom" (user custom suffix), "" (All)
    var pathType by remember { mutableStateOf("Music Presets") } // "Music Presets", "Downloads Folder", "Entire Storage", "Custom Folder"
    var customPathInput by remember { mutableStateOf("") }
    
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
    
    val isFormValid = remember(nameInput, pathType, customPathInput) {
        val nameOk = InputValidator.validateName(nameInput) is InputValidator.ValidationResult.Valid
        val pathOk = if (pathType == "Custom Folder") {
            InputValidator.validateFolderSuffix(customPathInput) is InputValidator.ValidationResult.Valid
        } else {
            true
        }
        nameOk && pathOk
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SpotifyDark, SpotifyBlack)
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SoundScapeBrandLogo(modifier = Modifier.size(80.dp))
            
            Text(
                text = "Welcome to SoundScape",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("onboarding_title")
            )
            
            Text(
                text = "Personalize your offline listening profile and configure where you want to scan for audio tracks safely.",
                color = SpotifyTextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Name Field
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it
                    nameError = (InputValidator.validateName(it) as? InputValidator.ValidationResult.Invalid)?.reason
                },
                label = { Text("What should we call you?") },
                placeholder = { Text("Enter listener name") },
                isError = nameError != null,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SpotifyGreen,
                    unfocusedBorderColor = SpotifyLightGray,
                    focusedLabelColor = SpotifyGreen,
                    unfocusedLabelColor = SpotifyTextSecondary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_name_input")
            )
            if (nameError != null) {
                Text(nameError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Location Scan Selection Header
            Text(
                text = "Select Scanning Target (Security Filter)",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Start)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(SpotifyMediumGray)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "Standard Music Folder" to "Music Presets",
                    "Downloads Folder" to "Downloads Folder",
                    "Custom Folder name" to "Custom Folder",
                    "Scan Entire Storage (No filter)" to "Entire Storage"
                ).forEach { (display, type) ->
                    val isSelected = pathType == type
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pathType = type }
                            .padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { pathType = type },
                            colors = RadioButtonDefaults.colors(selectedColor = SpotifyGreen, unselectedColor = Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = display, color = if (isSelected) SpotifyGreen else Color.White, fontSize = 13.sp)
                    }
                }
            }

            // Document Tree File Explorer Action Sizing Button
            Button(
                onClick = { pickerLauncher.launch(null) },
                colors = ButtonDefaults.buttonColors(containerColor = SpotifyMediumGray),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.Filled.Folder, contentDescription = "Folder Picker", tint = SpotifyGreen)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Select via File Explorer", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            // Custom Suffix Folder Text Input
            if (pathType == "Custom Folder") {
                OutlinedTextField(
                    value = customPathInput,
                    onValueChange = {
                        customPathInput = it
                        pathError = (InputValidator.validateFolderSuffix(it) as? InputValidator.ValidationResult.Invalid)?.reason
                    },
                    label = { Text("Folder Name Suffix to Match") },
                    placeholder = { Text("e.g. Beats, CustomMusic, Folk") },
                    isError = pathError != null,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SpotifyGreen,
                        unfocusedBorderColor = SpotifyLightGray,
                        focusedLabelColor = SpotifyGreen,
                        unfocusedLabelColor = SpotifyTextSecondary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_custom_path_input")
                )
                if (pathError != null) {
                    Text(pathError!!, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, modifier = Modifier.align(Alignment.Start))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val cleanedName = nameInput.trim()
                    val finalName = if (cleanedName.length >= 2) cleanedName else "Offline Listener"
                    val finalPath = when (pathType) {
                        "Music Presets" -> "Music"
                        "Downloads Folder" -> "Download"
                        "Entire Storage" -> ""
                        else -> if (customPathInput.trim().isNotEmpty()) customPathInput.trim() else "Music"
                    }
                    onComplete(finalName, finalPath)
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SpotifyGreen,
                    disabledContainerColor = SpotifyLightGray
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("onboarding_start_button")
            ) {
                Text(
                    text = "Continue",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            TextButton(
                onClick = {
                    onComplete("Offline Listener", "Music")
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Skip & Continue with Defaults",
                    color = SpotifyGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ---------------- HIGH-PRECISION EQUALIZER VERTICAL SLIDER ----------------
