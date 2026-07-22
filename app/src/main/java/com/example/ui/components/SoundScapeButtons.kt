package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SoundScapeShapes
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.SpotifyMediumGray

/**
 * DESIGN.md §4 "Dark Pill" — compact CTA pill (e.g. "Go Premium").
 */
@Composable
fun DarkPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = SpotifyGreen,
    disabledContainerColor: Color = containerColor.copy(alpha = 0.3f),
    contentColor: Color = Color.Black,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = SoundScapeShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
        contentPadding = contentPadding,
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/**
 * DESIGN.md §4 "Dark Large Pill" — full-width primary app navigation/CTA button,
 * with an optional leading icon.
 */
@Composable
fun DarkLargePillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 50.dp,
    containerColor: Color = SpotifyGreen,
    disabledContainerColor: Color = SpotifyMediumGray,
    contentColor: Color = Color.Black,
    leadingIcon: ImageVector? = null,
    testTag: String? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .size(width = Dp.Unspecified, height = height)
            .let { if (testTag != null) it.testTag(testTag) else it },
        enabled = enabled,
        shape = SoundScapeShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = disabledContainerColor,
            disabledContentColor = contentColor.copy(alpha = 0.5f),
        ),
    ) {
        if (leadingIcon != null) {
            Icon(imageVector = leadingIcon, contentDescription = null, tint = SpotifyGreen)
            Spacer(Modifier.width(10.dp))
        }
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/**
 * DESIGN.md §4 "Light Pill" — light-mode CTA (rare; marketing/consent style surfaces).
 */
@Composable
fun LightPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFFEEEEEE),
    contentColor: Color = Color(0xFF181818),
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = SoundScapeShapes.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/**
 * DESIGN.md §4 "Outlined Pill" — follow buttons, secondary actions.
 */
@Composable
fun OutlinedPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = Color(0xFF7C7C7C),
    contentColor: Color = Color.White,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = SoundScapeShapes.fullPill,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = contentColor),
        contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 4.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}

/**
 * DESIGN.md §4 "Circular Play" — play/pause controls. Pass [isPlaying] to toggle
 * between play/pause icons, or leave it null for a play-only trigger (e.g. "play this
 * playlist"/"play top result") that never shows a pause state.
 */
@Composable
fun CircularPlayButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPlaying: Boolean? = null,
    size: Dp = 56.dp,
    iconSize: Dp = 32.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    iconColor: Color = Color.Black,
    contentDescription: String? = "Play",
    testTag: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .let { if (testTag != null) it.testTag(testTag) else it }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isPlaying == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Selectable pill used for toggleable option rows (e.g. equalizer presets) — not a
 * DESIGN.md §4 button variant itself, but shares its pill geometry/typography so
 * ad hoc `RoundedCornerShape(24.dp)` toggle buttons have a single home.
 */
@Composable
fun SelectablePillButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContainerColor: Color = Color(0xFF2C2C2E),
    selectedContentColor: Color = Color.Black,
    unselectedContentColor: Color = Color.White,
) {
    Button(
        onClick = onClick,
        modifier = modifier.size(width = Dp.Unspecified, height = height),
        shape = SoundScapeShapes.pill,
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) selectedContainerColor else unselectedContainerColor,
            contentColor = if (selected) selectedContentColor else unselectedContentColor,
        ),
    ) {
        Text(text, fontWeight = FontWeight.Bold)
    }
}
