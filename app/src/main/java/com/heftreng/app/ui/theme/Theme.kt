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
//  TEMA TANIMLARI
//  Her tema: koyu + açık renk çifti içerir
// ─────────────────────────────────────────────────────────────────────────────

enum class HeftrangThemeVariant {
    CHARCOAL_INK,   // Varsayılan: siyah-gri antrasit (mevcut tema)
    BOOK,           // Kitap teması: sıcak krem + kahverengi
    FOREST,         // Orman teması: koyu yeşil + doğa tonları
    OCEAN,          // Okyanus teması: derin mavi + deniz tonları
    SUNSET,         // Gün batımı: turuncu-pembe gradyanlar
    MONOCHROME,     // Tek renk: saf siyah-beyaz
}

data class HeftrangColors(
    val background  : Color,
    val surface     : Color,
    val surfaceVar  : Color,
    val card        : Color,
    val onBackground: Color,
    val onSurface   : Color,
    val primary     : Color,
    val primaryLight: Color,
    val accent      : Color,
    val gradientStart: Color,
    val gradientEnd  : Color,
    val muted       : Color,
    val divider     : Color,
    val shimmer     : Color,
    val isDark      : Boolean,
)

// ─────────────────────────────────────────────────────────────────────────────
//  1. CHARCOAL INK — Varsayılan (mevcut tema)
// ─────────────────────────────────────────────────────────────────────────────
private val charcoalDark = HeftrangColors(
    background    = Color(0xFF0D0D0F),
    surface       = Color(0xFF131316),
    surfaceVar    = Color(0xFF1A1A1F),
    card          = Color(0xFF17171C),
    onBackground  = Color(0xFFF0F0F5),
    onSurface     = Color(0xFFBDBDC8),
    primary       = Color(0xFF6C8EFF),
    primaryLight  = Color(0xFF8FAAFF),
    accent        = Color(0xFF6C8EFF),
    gradientStart = Color(0xFF6C8EFF),
    gradientEnd   = Color(0xFF38BDF8),
    muted         = Color(0xFF5C5C6E),
    divider       = Color(0xFF222228),
    shimmer       = Color(0xFF1C1C22),
    isDark        = true,
)

private val charcoalLight = HeftrangColors(
    background    = Color(0xFFF5F5F7),
    surface       = Color(0xFFFFFFFF),
    surfaceVar    = Color(0xFFEEEEF3),
    card          = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF111114),
    onSurface     = Color(0xFF3A3A45),
    primary       = Color(0xFF4A6FFF),
    primaryLight  = Color(0xFF6C8EFF),
    accent        = Color(0xFF4A6FFF),
    gradientStart = Color(0xFF4A6FFF),
    gradientEnd   = Color(0xFF38BDF8),
    muted         = Color(0xFF8E8E9E),
    divider       = Color(0xFFE5E5EA),
    shimmer       = Color(0xFFEEEEF3),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  2. BOOK — Kitap Teması: Krem, Kahverengi, Sepia
//  Eski kütüphane havası: sararmış sayfa, mürekkep kahverengisi, antika vurgu
// ─────────────────────────────────────────────────────────────────────────────
private val bookDark = HeftrangColors(
    background    = Color(0xFF1C1610),   // koyu kahverengi-siyah (eski deri kapak)
    surface       = Color(0xFF241E16),   // biraz daha açık, kitap sayfası gölgesi
    surfaceVar    = Color(0xFF2E2620),   // hover yüzey
    card          = Color(0xFF2A2218),   // kart arkaplanı — sıcak koyu kahve
    onBackground  = Color(0xFFF2E8D5),   // krem/sepia beyazı — göze daha az batar
    onSurface     = Color(0xFFC9B99A),   // ikincil metin — sararmış mürekkep
    primary       = Color(0xFFD4A853),   // altın-amber vurgu (kitap klipsi)
    primaryLight  = Color(0xFFE8C57A),   // daha açık altın
    accent        = Color(0xFFD4A853),
    gradientStart = Color(0xFFD4A853),
    gradientEnd   = Color(0xFFE8846A),   // bakır-turuncu
    muted         = Color(0xFF6B5D48),   // solmuş mürekkep
    divider       = Color(0xFF352C20),   // çok ince ayırıcı
    shimmer       = Color(0xFF2C2418),
    isDark        = true,
)

private val bookLight = HeftrangColors(
    background    = Color(0xFFFAF3E8),   // krem/sepia kağıt rengi
    surface       = Color(0xFFFFF8EE),   // neredeyse beyaz, sıcak ton
    surfaceVar    = Color(0xFFF0E8D8),   // hafif koyu krem
    card          = Color(0xFFFFF8EE),   // kart — parlak krem
    onBackground  = Color(0xFF2C1F0E),   // koyu kahverengi mürekkep
    onSurface     = Color(0xFF5C4A30),   // ikincil metin — orta kahve
    primary       = Color(0xFF8B5E2C),   // derin kahverengi-amber
    primaryLight  = Color(0xFFB07A40),
    accent        = Color(0xFF8B5E2C),
    gradientStart = Color(0xFF8B5E2C),
    gradientEnd   = Color(0xFFD4853A),
    muted         = Color(0xFF9E8A70),   // solmuş metin
    divider       = Color(0xFFDFD0BC),   // açık krem çizgi
    shimmer       = Color(0xFFF0E8D8),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  3. FOREST — Orman Teması: Koyu Yeşil, Toprak, Doğa
// ─────────────────────────────────────────────────────────────────────────────
private val forestDark = HeftrangColors(
    background    = Color(0xFF0A120E),   // neredeyse siyah-yeşil
    surface       = Color(0xFF111A14),
    surfaceVar    = Color(0xFF182218),
    card          = Color(0xFF141D16),
    onBackground  = Color(0xFFDFF0E4),   // açık nane beyazı
    onSurface     = Color(0xFF9AB89F),   // orta yeşil-gri
    primary       = Color(0xFF4CAF6F),   // canlı orman yeşili
    primaryLight  = Color(0xFF6FCB8A),
    accent        = Color(0xFF4CAF6F),
    gradientStart = Color(0xFF4CAF6F),
    gradientEnd   = Color(0xFF88C96A),   // lime-yeşil
    muted         = Color(0xFF4A6650),
    divider       = Color(0xFF1E2E20),
    shimmer       = Color(0xFF162019),
    isDark        = true,
)

private val forestLight = HeftrangColors(
    background    = Color(0xFFF2F9F2),
    surface       = Color(0xFFFFFFFF),
    surfaceVar    = Color(0xFFE8F4E8),
    card          = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF0D2010),
    onSurface     = Color(0xFF2E5030),
    primary       = Color(0xFF2E7D32),   // koyu orman yeşili
    primaryLight  = Color(0xFF4CAF50),
    accent        = Color(0xFF2E7D32),
    gradientStart = Color(0xFF2E7D32),
    gradientEnd   = Color(0xFF66BB6A),
    muted         = Color(0xFF7C9E7E),
    divider       = Color(0xFFD0E8D0),
    shimmer       = Color(0xFFE8F4E8),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  4. OCEAN — Okyanus Teması: Derin Mavi, Lacivert, Turkuaz
// ─────────────────────────────────────────────────────────────────────────────
private val oceanDark = HeftrangColors(
    background    = Color(0xFF050D1A),   // derin okyanus gecesi
    surface       = Color(0xFF0A1525),
    surfaceVar    = Color(0xFF0F1F32),
    card          = Color(0xFF0D1B2E),
    onBackground  = Color(0xFFDFEEFA),   // buz mavisi-beyaz
    onSurface     = Color(0xFF8FBCD4),
    primary       = Color(0xFF00B4D8),   // parlak turkuaz
    primaryLight  = Color(0xFF48CAE4),
    accent        = Color(0xFF00B4D8),
    gradientStart = Color(0xFF0077B6),
    gradientEnd   = Color(0xFF00B4D8),
    muted         = Color(0xFF3A6080),
    divider       = Color(0xFF112030),
    shimmer       = Color(0xFF0C1A2A),
    isDark        = true,
)

private val oceanLight = HeftrangColors(
    background    = Color(0xFFEDF6FB),
    surface       = Color(0xFFFFFFFF),
    surfaceVar    = Color(0xFFDBEEF7),
    card          = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF0A2035),
    onSurface     = Color(0xFF1A4A6A),
    primary       = Color(0xFF0077B6),
    primaryLight  = Color(0xFF00B4D8),
    accent        = Color(0xFF0077B6),
    gradientStart = Color(0xFF0077B6),
    gradientEnd   = Color(0xFF48CAE4),
    muted         = Color(0xFF6A9AB8),
    divider       = Color(0xFFBFDEED),
    shimmer       = Color(0xFFDBEEF7),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  5. SUNSET — Gün Batımı: Turuncu, Pembe, Mor
// ─────────────────────────────────────────────────────────────────────────────
private val sunsetDark = HeftrangColors(
    background    = Color(0xFF150A0F),   // koyu gece-mor
    surface       = Color(0xFF1E1018),
    surfaceVar    = Color(0xFF281520),
    card          = Color(0xFF22131C),
    onBackground  = Color(0xFFFAE8EE),   // açık pembe-beyaz
    onSurface     = Color(0xFFD4A0B5),
    primary       = Color(0xFFFF6B8A),   // sıcak pembe-kırmızı
    primaryLight  = Color(0xFFFF8FA8),
    accent        = Color(0xFFFF6B8A),
    gradientStart = Color(0xFFFF6B35),   // turuncu
    gradientEnd   = Color(0xFFFF6B8A),   // pembe
    muted         = Color(0xFF7A3D55),
    divider       = Color(0xFF2E1825),
    shimmer       = Color(0xFF25141E),
    isDark        = true,
)

private val sunsetLight = HeftrangColors(
    background    = Color(0xFFFFF5F7),
    surface       = Color(0xFFFFFFFF),
    surfaceVar    = Color(0xFFFFE8EE),
    card          = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF330818),
    onSurface     = Color(0xFF6B2040),
    primary       = Color(0xFFE91E63),
    primaryLight  = Color(0xFFFF5C8D),
    accent        = Color(0xFFE91E63),
    gradientStart = Color(0xFFFF6B35),
    gradientEnd   = Color(0xFFE91E63),
    muted         = Color(0xFFB87090),
    divider       = Color(0xFFFFCCDD),
    shimmer       = Color(0xFFFFE8EE),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  6. MONOCHROME — Saf Siyah-Beyaz
// ─────────────────────────────────────────────────────────────────────────────
private val monoDark = HeftrangColors(
    background    = Color(0xFF000000),
    surface       = Color(0xFF0A0A0A),
    surfaceVar    = Color(0xFF141414),
    card          = Color(0xFF0F0F0F),
    onBackground  = Color(0xFFFFFFFF),
    onSurface     = Color(0xFFB0B0B0),
    primary       = Color(0xFFFFFFFF),
    primaryLight  = Color(0xFFE0E0E0),
    accent        = Color(0xFFFFFFFF),
    gradientStart = Color(0xFFB0B0B0),
    gradientEnd   = Color(0xFFFFFFFF),
    muted         = Color(0xFF505050),
    divider       = Color(0xFF1E1E1E),
    shimmer       = Color(0xFF141414),
    isDark        = true,
)

private val monoLight = HeftrangColors(
    background    = Color(0xFFFFFFFF),
    surface       = Color(0xFFFFFFFF),
    surfaceVar    = Color(0xFFF0F0F0),
    card          = Color(0xFFFFFFFF),
    onBackground  = Color(0xFF000000),
    onSurface     = Color(0xFF404040),
    primary       = Color(0xFF000000),
    primaryLight  = Color(0xFF404040),
    accent        = Color(0xFF000000),
    gradientStart = Color(0xFF404040),
    gradientEnd   = Color(0xFF000000),
    muted         = Color(0xFF909090),
    divider       = Color(0xFFE0E0E0),
    shimmer       = Color(0xFFF0F0F0),
    isDark        = false,
)

// ─────────────────────────────────────────────────────────────────────────────
//  Eski alan adları (geriye dönük uyumluluk)
// ─────────────────────────────────────────────────────────────────────────────
val Accent        = Color(0xFF6C8EFF)
val GradientStart = Color(0xFF6C8EFF)
val GradientEnd   = Color(0xFF38BDF8)

// ─────────────────────────────────────────────────────────────────────────────
//  CompositionLocal & Yardımcı Fonksiyonlar
// ─────────────────────────────────────────────────────────────────────────────

val LocalHeftrangColors = staticCompositionLocalOf { charcoalDark }

fun getColors(variant: HeftrangThemeVariant, isDark: Boolean): HeftrangColors =
    when (variant) {
        HeftrangThemeVariant.CHARCOAL_INK -> if (isDark) charcoalDark else charcoalLight
        HeftrangThemeVariant.BOOK         -> if (isDark) bookDark     else bookLight
        HeftrangThemeVariant.FOREST       -> if (isDark) forestDark   else forestLight
        HeftrangThemeVariant.OCEAN        -> if (isDark) oceanDark    else oceanLight
        HeftrangThemeVariant.SUNSET       -> if (isDark) sunsetDark   else sunsetLight
        HeftrangThemeVariant.MONOCHROME   -> if (isDark) monoDark     else monoLight
    }

fun HeftrangThemeVariant.displayName(): String = when (this) {
    HeftrangThemeVariant.CHARCOAL_INK -> "Charcoal Ink"
    HeftrangThemeVariant.BOOK         -> "Kitap"
    HeftrangThemeVariant.FOREST       -> "Orman"
    HeftrangThemeVariant.OCEAN        -> "Okyanus"
    HeftrangThemeVariant.SUNSET       -> "Gün Batımı"
    HeftrangThemeVariant.MONOCHROME   -> "Tek Renk"
}

fun HeftrangThemeVariant.emoji(): String = when (this) {
    HeftrangThemeVariant.CHARCOAL_INK -> "🖤"
    HeftrangThemeVariant.BOOK         -> "📚"
    HeftrangThemeVariant.FOREST       -> "🌿"
    HeftrangThemeVariant.OCEAN        -> "🌊"
    HeftrangThemeVariant.SUNSET       -> "🌅"
    HeftrangThemeVariant.MONOCHROME   -> "⚪"
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kolay erişim Composable getter'ları
// ─────────────────────────────────────────────────────────────────────────────
val Background    @Composable get() = LocalHeftrangColors.current.background
val HeftSurface   @Composable get() = LocalHeftrangColors.current.surface
val SurfaceVar    @Composable get() = LocalHeftrangColors.current.surfaceVar
val HeftCard      @Composable get() = LocalHeftrangColors.current.card
val OnBackground  @Composable get() = LocalHeftrangColors.current.onBackground
val OnSurface     @Composable get() = LocalHeftrangColors.current.onSurface
val Primary       @Composable get() = LocalHeftrangColors.current.primary
val PrimaryLight  @Composable get() = LocalHeftrangColors.current.primaryLight
val HeftAccent    @Composable get() = LocalHeftrangColors.current.accent
val HeftGradientStart @Composable get() = LocalHeftrangColors.current.gradientStart
val HeftGradientEnd   @Composable get() = LocalHeftrangColors.current.gradientEnd
val Muted         @Composable get() = LocalHeftrangColors.current.muted
val Divider       @Composable get() = LocalHeftrangColors.current.divider
val Shimmer       @Composable get() = LocalHeftrangColors.current.shimmer

// ─────────────────────────────────────────────────────────────────────────────
//  Material3 Scheme Üreteci
// ─────────────────────────────────────────────────────────────────────────────
private fun buildColorScheme(colors: HeftrangColors) =
    if (colors.isDark) darkColorScheme(
        primary          = colors.primary,
        onPrimary        = Color.White,
        secondary        = colors.accent,
        onSecondary      = Color.White,
        background       = colors.background,
        onBackground     = colors.onBackground,
        surface          = colors.surface,
        onSurface        = colors.onSurface,
        surfaceVariant   = colors.surfaceVar,
        onSurfaceVariant = colors.muted,
        error            = Error,
        outline          = colors.divider,
    ) else lightColorScheme(
        primary          = colors.primary,
        onPrimary        = Color.White,
        secondary        = colors.accent,
        onSecondary      = Color.White,
        background       = colors.background,
        onBackground     = colors.onBackground,
        surface          = colors.surface,
        onSurface        = colors.onSurface,
        surfaceVariant   = colors.surfaceVar,
        onSurfaceVariant = colors.muted,
        error            = Error,
        outline          = colors.divider,
    )

// ─────────────────────────────────────────────────────────────────────────────
//  Ana Tema Composable
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun HeftrangTheme(
    darkMode : Boolean = true,
    variant  : HeftrangThemeVariant = HeftrangThemeVariant.CHARCOAL_INK,
    content  : @Composable () -> Unit,
) {
    val colors      = getColors(variant, darkMode)
    val colorScheme = buildColorScheme(colors)

    CompositionLocalProvider(LocalHeftrangColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content,
        )
    }
}
