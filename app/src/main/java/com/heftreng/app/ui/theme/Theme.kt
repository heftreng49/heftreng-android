package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

val Amber   = Color(0xFFFBBF24)
val Success = Color(0xFF10D9A0)
val Error   = Color(0xFFF87171)
val Accent  = Color(0xFFF472B6)

data class HeftrangColors(
    val background  : Color,
    val surface     : Color,
    val surfaceVar  : Color,
    val onBackground: Color,
    val onSurface   : Color,
    val primary     : Color,
    val primaryLight: Color,
    val muted       : Color,
    val divider     : Color,
    val isDark      : Boolean,
)

private val darkColors = HeftrangColors(
    background   = Color(0xFF060612),
    surface      = Color(0xFF12102A),
    surfaceVar   = Color(0xFF1E1C38),
    onBackground = Color(0xFFF0EEFF),
    onSurface    = Color(0xFFD4CEEF),
    primary      = Color(0xFF8B5CF6),
    primaryLight = Color(0xFFA78BFA),
    muted        = Color(0xFF7467A0),
    divider      = Color(0xFF2A2850),
    isDark       = true,
)

private val lightColors = HeftrangColors(
    background   = Color(0xFFFFFFFF),
    surface      = Color(0xFFFFFFFF),
    surfaceVar   = Color(0xFFF4F4F6),
    onBackground = Color(0xFF110A26),
    onSurface    = Color(0xFF1F1640),
    primary      = Color(0xFF7C3AED), 
    primaryLight = Color(0xFF8B5CF6),
    muted        = Color(0xFF8E85A6),
    divider      = Color(0xFFEAE7F2),
    isDark       = false,
)

val LocalHeftrangColors = staticCompositionLocalOf { darkColors }

val Background    @Composable get() = LocalHeftrangColors.current.background
val HeftSurface   @Composable get() = LocalHeftrangColors.current.surface
val SurfaceVar    @Composable get() = LocalHeftrangColors.current.surfaceVar
val OnBackground  @Composable get() = LocalHeftrangColors.current.onBackground
val OnSurface     @Composable get() = LocalHeftrangColors.current.onSurface
val Primary       @Composable get() = LocalHeftrangColors.current.primary
val PrimaryLight  @Composable get() = LocalHeftrangColors.current.primaryLight
val Muted         @Composable get() = LocalHeftrangColors.current.muted
val Divider       @Composable get() = LocalHeftrangColors.current.divider

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
