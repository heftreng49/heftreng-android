package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Koyu mod ─────────────────────────────────────────────────────────────────
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
    onPrimary        = Color.Black,
    primaryContainer = AmberDim,
    background       = Background,
    onBackground     = OnBackground,
    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVar,
    outline          = Divider,
    error            = Color(0xFFEF4444),
)

// ── Aydınlık mod ─────────────────────────────────────────────────────────────
val LightBackground   = Color(0xFFFAFAFA)
val LightSurface      = Color(0xFFFFFFFF)
val LightSurfaceVar   = Color(0xFFF4F4F5)
val LightOnBackground = Color(0xFF09090B)
val LightOnSurface    = Color(0xFF18181B)
val LightMuted        = Color(0xFF71717A)
val LightDivider      = Color(0xFFE4E4E7)

private val LightColors = lightColorScheme(
    primary          = Amber,
    onPrimary        = Color.Black,
    background       = LightBackground,
    onBackground     = LightOnBackground,
    surface          = LightSurface,
    onSurface        = LightOnSurface,
    surfaceVariant   = LightSurfaceVar,
    outline          = LightDivider,
    error            = Color(0xFFEF4444),
)

@Composable
fun HeftrangTheme(content: @Composable () -> Unit) {
    val colorScheme = if (com.heftreng.app.navigation.AppPrefs.darkMode) DarkColors else LightColors
    MaterialTheme(colorScheme = colorScheme, content = content)
}
