package com.example.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// Material3 shape slots mapped onto DESIGN.md's radius scale (§5).
val SoundScapeMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), // badges, explicit tags
    small = RoundedCornerShape(4.dp), // inputs, small elements
    medium = RoundedCornerShape(6.dp), // album art containers, cards
    large = RoundedCornerShape(8.dp), // sections, dialogs
    extraLarge = RoundedCornerShape(16.dp), // panels, overlay elements
)

// DESIGN.md needs shapes beyond Material3's 5-slot Shapes (pills, full pills, circles),
// so the shape tokens screens/components migrate to are exposed here directly.
object SoundScapeShapes {
    val badge = RoundedCornerShape(2.dp)
    val subtle = RoundedCornerShape(4.dp)
    val standard = RoundedCornerShape(6.dp)
    val comfortable = RoundedCornerShape(8.dp)
    val panel = RoundedCornerShape(16.dp)
    val largePill = RoundedCornerShape(100.dp)
    val pill = RoundedCornerShape(500.dp)
    val fullPill = RoundedCornerShape(9999.dp)
    val circle = CircleShape
}
