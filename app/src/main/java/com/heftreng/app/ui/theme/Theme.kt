package com.heftreng.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Heftreng Renk Paleti (XML tema: --bg:#060612, --pr:#8b5cf6, --ac:#f472b6) ──

// Dark palette
val Background    = Color(0xFF060612)
val Surface       = Color(0xFF12102A)
val SurfaceVar    = Color(0xFF1E1C38)
val OnBackground  = Color(0xFFF0EEFF)
val Primary       = Color(0xFF8B5CF6)   // --pr
val PrimaryLight  = Color(0xFFA78BFA)   // --pr lighter
val Accent        = Color(0xFFF472B6)   // --ac
val Amber         = Color(0xFFFBBF24)   // --warn / gold accent
val Muted         = Color(0xFF7467A0)   // --mut
val Divider       = Color(0xFF2A2850)   // --dim
val Success       = Color(0xFF10D9A0)   // --ok
val Error         = Color(0xFFF87171)   // --err

// Light palette (settings'ten "light mode" seçilirse)
val BackgroundLight   = Color(0xFFF5F3FF)
val SurfaceLight      = Color(0xFFFFFFFF)
val SurfaceVarLight   = Color(0xFFEDE9FE)
val OnBackgroundLight = Color(0xFF1A1040)
val MutedLight        = Color(0xFF8878B8)
val DividerLight      = Color(0xFFD0C8F0)

private val DarkColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = Color.White,
    secondary        = Accent,
    onSecondary      = Color.White,
    background       = Background,
    onBackground     = OnBackground,
    surface          = Surface,
    onSurface        = OnBackground,
    surfaceVariant   = SurfaceVar,
    onSurfaceVariant = Muted,
    error            = Error,
    outline          = Divider,
)

private val LightColorScheme = lightColorScheme(
    primary          = Primary,
    onPrimary        = Color.White,
    secondary        = Accent,
    onSecondary      = Color.White,
    background       = BackgroundLight,
    onBackground     = OnBackgroundLight,
    surface          = SurfaceLight,
    onSurface        = OnBackgroundLight,
    surfaceVariant   = SurfaceVarLight,
    onSurfaceVariant = MutedLight,
    error            = Error,
    outline          = DividerLight,
)

/**
 * [darkMode]: SettingsViewModel'den collectAsState() ile gelmeli.
 *
 * Kullanım (NavHost / MainActivity):
 *   val darkMode by settingsVm.darkMode.collectAsState()
 *   HeftRengTheme(darkMode = darkMode) { ... }
 */
@Composable
fun HeftRengTheme(
    darkMode: Boolean = true,   // ✅ AppPrefs kaldırıldı — dışarıdan flow ile gelir
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkMode) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content     = content,
    )
}
