package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun SoundScapeTheme(
    themePreset: String = "green",
    isDark: Boolean = true,
    customColorHex: String = "#00E5FF",
    content: @Composable () -> Unit
) {
    // Get the primary color of the selected theme
    val primaryColor = when (themePreset) {
        "green" -> Color(0xFF1DB954)
        "sunset" -> Color(0xFFFF9800)
        "blue" -> Color(0xFF2979FF)
        "violet" -> Color(0xFF9C27B0)
        "crimson" -> Color(0xFFE91E63)
        "custom" -> {
            try {
                Color(android.graphics.Color.parseColor(customColorHex))
            } catch (e: Exception) {
                Color(0xFF00E5FF) // default slate teal
            }
        }
        else -> Color(0xFF1DB954)
    }

    val colorScheme = if (isDark) {
        val darkBg = when (themePreset) {
            "green" -> Color(0xFF0B0E0C)
            "sunset" -> Color(0xFF100E0A)
            "blue" -> Color(0xFF0B0C11)
            "violet" -> Color(0xFF0D0A11)
            "crimson" -> Color(0xFF110A0C)
            else -> Color(0xFF121212)
        }
        val darkSurface = when (themePreset) {
            "green" -> Color(0xFF141A16)
            "sunset" -> Color(0xFF1C1812)
            "blue" -> Color(0xFF131520)
            "violet" -> Color(0xFF181320)
            "crimson" -> Color(0xFF1C1315)
            else -> Color(0xFF1E1E1E)
        }
        darkColorScheme(
            primary = primaryColor,
            secondary = Color(0xFFB3B3B3),
            tertiary = Color(0xFF2A2A2A),
            background = darkBg,
            surface = darkSurface,
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onTertiary = Color(0xFFB3B3B3),
            onBackground = Color.White,
            onSurface = Color.White
        )
    } else {
        val lightBg = when (themePreset) {
            "green" -> Color(0xFFF1FDF5)
            "sunset" -> Color(0xFFFDF8F3)
            "blue" -> Color(0xFFF1F3FE)
            "violet" -> Color(0xFFF9F1FD)
            "crimson" -> Color(0xFFFCF0F3)
            else -> Color(0xFFF8F9FA)
        }
        val lightSurface = Color.White
        lightColorScheme(
            primary = primaryColor,
            secondary = Color(0xFF333333),
            tertiary = Color(0xFFE5E5E5),
            background = lightBg,
            surface = lightSurface,
            onPrimary = Color.White,
            onSecondary = Color.Black,
            onTertiary = Color(0xFF666666),
            onBackground = Color.Black,
            onSurface = Color.Black
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
