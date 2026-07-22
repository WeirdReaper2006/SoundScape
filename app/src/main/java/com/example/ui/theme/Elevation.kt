package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Approximates DESIGN.md §6's box-shadows (Compose has no offset+blur shadow primitive,
// so a colored elevation shadow is the closest faithful mapping).

/** rgba(0,0,0,0.5) 0px 8px 24px — dialogs, menus, elevated panels. */
fun Modifier.heavyShadow(shape: Shape = RoundedCornerShape(8.dp)): Modifier = shadow(
    elevation = 24.dp,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.5f),
    spotColor = Color.Black.copy(alpha = 0.5f),
)

/** rgba(0,0,0,0.3) 0px 8px 8px — cards, dropdowns. */
fun Modifier.mediumShadow(shape: Shape = RoundedCornerShape(6.dp)): Modifier = shadow(
    elevation = 8.dp,
    shape = shape,
    ambientColor = Color.Black.copy(alpha = 0.3f),
    spotColor = Color.Black.copy(alpha = 0.3f),
)
