package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.soundscape.R

// Poppins stands in for the proprietary SpotifyMixUI/CircularSp family (DESIGN.md §3) —
// closest free geometric-sans analog, bundled as static font files (no network dependency).
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

// Type scale from DESIGN.md §3, mapped onto Material3's Typography slots.
val Typography = Typography(
    titleLarge = TextStyle( // Section Title
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle( // Feature Heading
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 23.4.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle( // Body Bold
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle( // Body
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle( // Button Uppercase
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.5.sp,
    ),
    labelMedium = TextStyle( // Nav Link Bold
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle( // Nav Link (inactive)
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle( // Caption
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
)

// DESIGN.md rows without a Material3 slot (Caption Bold, Small, Small Bold, Badge, Micro).
object SoundScapeType {
    val captionBold = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    )
    val small = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.sp,
    )
    val smallBold = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    )
    val badge = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.5.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.sp,
    )
    val micro = TextStyle(
        fontFamily = Poppins,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        lineHeight = 10.sp,
        letterSpacing = 0.sp,
    )
}
