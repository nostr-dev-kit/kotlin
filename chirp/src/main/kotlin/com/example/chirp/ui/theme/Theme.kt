package com.example.chirp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Light & Airy Color Scheme
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF5B5FC7),           // accent
    onPrimary = Color.White,
    primaryContainer = Color(0x145B5FC7),   // accentSoft (8% opacity)

    background = Color.White,               // bgPrimary
    surface = Color(0xFFF8F9FA),           // bgSecondary
    surfaceVariant = Color(0xFFF1F3F5),    // bgTertiary
    surfaceContainer = Color(0xFFF5F6F7),  // bgHover

    onBackground = Color(0xFF1A1A1A),      // textPrimary
    onSurface = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF5C5C5C),  // textSecondary
    outline = Color(0x0F000000),            // border (6% black)
    outlineVariant = Color(0x1A000000),     // borderStrong (10% black)

    error = Color(0xFFEF4444),
    tertiary = Color(0xFF9A9A9A)            // textTertiary
)

// Dark Color Scheme
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF8B8FE8),           // accent (lighter for dark bg)
    onPrimary = Color(0xFF1A1A1A),
    primaryContainer = Color(0x1F8B8FE8),   // accentSoft (12% opacity)

    background = Color(0xFF0F0F0F),         // bgPrimary
    surface = Color(0xFF1A1A1A),            // bgSecondary
    surfaceVariant = Color(0xFF242424),     // bgTertiary
    surfaceContainer = Color(0xFF2A2A2A),   // bgHover

    onBackground = Color(0xFFF5F5F5),       // textPrimary
    onSurface = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFFA0A0A0),   // textSecondary
    outline = Color(0x1FFFFFFF),             // border (12% white)
    outlineVariant = Color(0x33FFFFFF),      // borderStrong (20% white)

    error = Color(0xFFFF6B6B),
    tertiary = Color(0xFF6B6B6B)             // textTertiary
)

// Semantic colors
object SemanticColors {
    val zapGold = Color(0xFFF59E0B)
    val zapGoldSoft = Color(0x14F59E0B)     // 8% opacity
    val heartRed = Color(0xFFE11D48)
    val heartRedSoft = Color(0x14E11D48)    // 8% opacity
    val success = Color(0xFF22C55E)
}

// Spacing
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
}

// Corner radius
object CornerRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

// Avatar sizes
object AvatarSizes {
    val xs = 32.dp
    val sm = 36.dp
    val md = 40.dp
    val lg = 68.dp
}

@Composable
fun ChirpTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
