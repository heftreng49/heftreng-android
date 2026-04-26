package com.heftreng.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════════════════════════
//  HEFTRENG — Tema
//  Orijinal dark renk isimleri aynen korundu.
//  Light karşılıkları themedXxx() fonksiyonlarından gelir.
// ═══════════════════════════════════════════════════════════

// ── Dark renkler (orijinal isimler) ──────────────────────
val Background  = Color(0xFF09090B)
val Surface     = Color(0xFF18181B)
val SurfaceVar  = Color(0xFF27272A)
val OnBackground= Color(0xFFFAFAFA)
val OnSurface   = Color(0xFFE4E4E7)
val Amber       = Color(0xFFF59E0B)
val Muted       = Color(0xFF71717A)
val Divider     = Color(0xFF3F3F46)
val Error       = Color(0xFFEF4444)
val Success     = Color(0xFF10B981)

// ── Light renkler ─────────────────────────────────────────
val BackgroundL = Color(0xFFFAFAFA)
val SurfaceL    = Color(0xFFFFFFFF)
val SurfaceVarL = Color(0xFFF4F4F5)
val OnBgL       = Color(0xFF09090B)
val OnSurfL     = Color(0xFF18181B)
val MutedL      = Color(0xFF71717A)
val DividerL    = Color(0xFFE4E4E7)

// ── Global uygulama ayarları ──────────────────────────────
var AppDark     by mutableStateOf(true)
var AppAccent   by mutableStateOf(Amber)
var AppFontSize by mutableStateOf(15)

// ── Renk şemaları ─────────────────────────────────────────
private fun darkScheme(accent: Color) = darkColorScheme(
    primary          = accent,
    onPrimary        = Color.Black,
    background       = Background,
    onBackground     = OnBackground,
    surface          = Surface,
    onSurface        = OnSurface,
    surfaceVariant   = SurfaceVar,
    onSurfaceVariant = Muted,
    error            = Error,
    outline          = Divider,
)
private fun lightScheme(accent: Color) = lightColorScheme(
    primary          = accent,
    onPrimary        = Color.White,
    background       = BackgroundL,
    onBackground     = OnBgL,
    surface          = SurfaceL,
    onSurface        = OnSurfL,
    surfaceVariant   = SurfaceVarL,
    onSurfaceVariant = MutedL,
    error            = Error,
    outline          = DividerL,
)

@Composable
fun HeftrangTheme(
    darkMode: Boolean = true,
    accent  : Color   = Amber,
    content : @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkMode) darkScheme(accent) else lightScheme(accent),
        content     = content,
    )
}

// ── Tema-duyarlı renk seçiciler ───────────────────────────
//  Tüm ekranlar bu composable fonksiyonları kullanır.
@Composable fun bg()       = if (AppDark) Background  else BackgroundL
@Composable fun surf()     = if (AppDark) Surface     else SurfaceL
@Composable fun surfVar()  = if (AppDark) SurfaceVar  else SurfaceVarL
@Composable fun onBg()     = if (AppDark) OnBackground else OnBgL
@Composable fun onSurf()   = if (AppDark) OnSurface   else OnSurfL
@Composable fun muted()    = if (AppDark) Muted       else MutedL
@Composable fun divider()  = if (AppDark) Divider     else DividerL
@Composable fun accent()   = AppAccent
