package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

// ── Renkler ──────────────────────────────────────────────────────────────────
val Background   = Color(0xFF09090B)
val Surface      = Color(0xFF18181B)
val SurfaceVar   = Color(0xFF27272A)
val Amber        = Color(0xFFF59E0B)
val AmberDim     = Color(0xFF92400E)
val OnBackground = Color(0xFFFAFAFA)
val OnSurface    = Color(0xFFE4E4E7)
val Muted        = Color(0xFF71717A)
val Divider      = Color(0xFF3F3F46)

private val DarkColors = darkColorScheme(
    primary          = Amber,
    onPrimary        = Color(0xFF000000),
    primaryContainer = AmberDim,
    background       = Background,
    onBackground     = OnBackground,
    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVar,
    outline          = Divider,
    error            = Color(0xFFEF4444),
)

// Acik tema renkleri
private val LightColors = androidx.compose.material3.lightColorScheme(
    primary          = Amber,
    onPrimary        = Color(0xFF000000),
    background       = Color(0xFFFAFAFA),
    onBackground     = Color(0xFF09090B),
    surface          = Color(0xFFFFFFFF),
    onSurface        = Color(0xFF18181B),
    surfaceVariant   = Color(0xFFE4E4E7),
    outline          = Color(0xFFD4D4D8),
    error            = Color(0xFFEF4444),
)

@Composable
fun HeftrangTheme(content: @Composable () -> Unit) {
    val colorScheme = if (com.heftreng.app.navigation.AppPrefs.darkMode) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
