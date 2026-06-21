package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ── Semantik renkler ──────────────────────────────────────────────────────────
val Amber   = Color(0xFFF59E0B)
val Success = Color(0xFF34D399)
val Error   = Color(0xFFF87171)

// ─────────────────────────────────────────────────────────────────────────────
//  YENİ KOYU TEMA: Siyah-gri arası, "Charcoal Ink"
//  Eski mor (#08071A) → Yeni sıcak antrasit (#0D0D0F, #131316, #1A1A1F)
//  Accent: keskin beyaz + ince mavi-gümüş vurgu
// ─────────────────────────────────────────────────────────────────────────────
val Accent       = Color(0xFF6C8EFF)   // indigo-mavi vurgu (eski pembe-mor yerine)
val GradientStart = Color(0xFF6C8EFF)
val GradientEnd   = Color(0xFF38BDF8)  // gökyüzü mavisi

data class HeftrangColors(
    val background  : Color,
    val surface     : Color,
    val surfaceVar  : Color,
    val card        : Color,
    val onBackground: Color,
    val onSurface   : Color,
    val primary     : Color,
    val primaryLight: Color,
    val muted       : Color,
    val divider     : Color,
    val shimmer     : Color,
    val isDark      : Boolean,
)

private val darkColors = HeftrangColors(
    background   = Color(0xFF0D0D0F),   // neredeyse siyah, hafif gri ton
    surface      = Color(0xFF131316),   // kart arkaplanı — siyahtan biraz açık
    surfaceVar   = Color(0xFF1A1A1F),   // hover / vurgulu yüzey
    card         = Color(0xFF17171C),   // post kartı — surface'ten hafif farklı
    onBackground = Color(0xFFF0F0F5),   // kırık beyaz — göze daha az batar
    onSurface    = Color(0xFFBDBDC8),   // ikincil metin
    primary      = Color(0xFF6C8EFF),   // indigo-mavi
    primaryLight = Color(0xFF8FAAFF),
    muted        = Color(0xFF5C5C6E),   // placeholder, ikon
    divider      = Color(0xFF222228),   // çok ince ayırıcı
    shimmer      = Color(0xFF1C1C22),
    isDark       = true,
)

private val lightColors = HeftrangColors(
    background   = Color(0xFFF5F5F7),
    surface      = Color(0xFFFFFFFF),
    surfaceVar   = Color(0xFFEEEEF3),
    card         = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111114),
    onSurface    = Color(0xFF3A3A45),
    primary      = Color(0xFF4A6FFF),
    primaryLight = Color(0xFF6C8EFF),
    muted        = Color(0xFF8E8E9E),
    divider      = Color(0xFFE5E5EA),
    shimmer      = Color(0xFFEEEEF3),
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
    content : @Composable () -> Unit,
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
