package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Sabit semantik renkler ────────────────────────────────────────────────────
val Amber   = Color(0xFFFFBF40)
val Success = Color(0xFF34D399)
val Error   = Color(0xFFF87171)
val Accent  = Color(0xFFE879F9)

// ── Gradient çift ─────────────────────────────────────────────────────────────
val GradientStart = Color(0xFF8B5CF6)
val GradientEnd   = Color(0xFFEC4899)

// ── Tema renk seti ────────────────────────────────────────────────────────────
data class HeftrangColors(
    val background  : Color,
    val surface     : Color,
    val surfaceVar  : Color,
    val card        : Color,   // post kartı arka planı
    val onBackground: Color,
    val onSurface   : Color,
    val primary     : Color,
    val primaryLight: Color,
    val muted       : Color,
    val divider     : Color,
    val shimmer     : Color,   // skeleton loader rengi
    val isDark      : Boolean,
)

private val darkColors = HeftrangColors(
    background   = Color(0xFF08071A),
    surface      = Color(0xFF100F26),
    surfaceVar   = Color(0xFF1C1A35),
    card         = Color(0xFF141228),
    onBackground = Color(0xFFF2EEFF),
    onSurface    = Color(0xFFD8D0F0),
    primary      = Color(0xFF9B72F5),
    primaryLight = Color(0xFFB49BFA),
    muted        = Color(0xFF7B6FA8),
    divider      = Color(0xFF23204A),
    shimmer      = Color(0xFF1E1C3A),
    isDark       = true,
)

private val lightColors = HeftrangColors(
    background   = Color(0xFFF6F4FC),
    surface      = Color(0xFFFFFFFF),
    surfaceVar   = Color(0xFFEEEAF8),
    card         = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0E0B22),
    onSurface    = Color(0xFF1A1535),
    primary      = Color(0xFF7C3AED),
    primaryLight = Color(0xFF9B72F5),
    muted        = Color(0xFF9181B8),
    divider      = Color(0xFFE4DFF5),
    shimmer      = Color(0xFFEDE9F8),
    isDark       = false,
)

val LocalHeftrangColors = staticCompositionLocalOf { darkColors }

val Background    @Composable get() = LocalHeftrangColors.current.background
val HeftSurface   @Composable get() = LocalHeftrangColors.current.surface
val SurfaceVar    @Composable get() = LocalHeftrangColors.current.surfaceVar
val HeftCard      @Composable get() = LocalHeftrangColors.current.card
val OnBackground  @Composable get() = LocalHeftrangColors.current.onBackground
val OnSurface     @Composable get() = LocalHeftrangColors.current.onSurface
val Primary       @Composable get() = LocalHeftrangColors.current.primary
val PrimaryLight  @Composable get() = LocalHeftrangColors.current.primaryLight
val Muted         @Composable get() = LocalHeftrangColors.current.muted
val Divider       @Composable get() = LocalHeftrangColors.current.divider
val Shimmer       @Composable get() = LocalHeftrangColors.current.shimmer

private fun darkScheme() = darkColorScheme(
    primary          = darkColors.primary,
    onPrimary        = Color.White,
    secondary        = Accent,
    onSecondary      = Color.White,
    background       = darkColors.background,
    onBackground     = darkColors.onBackground,
    surface          = darkColors.surface,
    onSurface        = darkColors.onSurface,
    surfaceVariant   = darkColors.surfaceVar,
    onSurfaceVariant = darkColors.muted,
    error            = Error,
    outline          = darkColors.divider,
)

private fun lightScheme() = lightColorScheme(
    primary          = lightColors.primary,
    onPrimary        = Color.White,
    secondary        = Accent,
    onSecondary      = Color.White,
    background       = lightColors.background,
    onBackground     = lightColors.onBackground,
    surface          = lightColors.surface,
    onSurface        = lightColors.onSurface,
    surfaceVariant   = lightColors.surfaceVar,
    onSurfaceVariant = lightColors.muted,
    error            = Error,
    outline          = lightColors.divider,
)

@Composable
fun HeftrangTheme(
    darkMode: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors      = if (darkMode) darkColors else lightColors
    val colorScheme = if (darkMode) darkScheme() else lightScheme()

    CompositionLocalProvider(LocalHeftrangColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content,
        )
    }
}
