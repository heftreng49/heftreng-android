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
    // Kullanıcının seçtiği yazı rengi override'ı (null = tema varsayılanı)
    val textColorOverride: Color? = null,
) {
    // Yazı rengi: override varsa onu, yoksa tema onBackground'unu kullan
    val effectiveTextColor: Color get() = textColorOverride ?: onBackground
}

// ─────────────────────────────────────────────────────────────────────────────
//  1. CHARCOAL INK — Varsayılan (mevcut tema)
// ─────────────────────────────────────────────────────────────────────────────
private val charcoalDark = HeftrangColors(
    background    = Color(0xFF13131A),   // biraz daha açık → daha ferah
    surface       = Color(0xFF1A1A22),
    surfaceVar    = Color(0xFF22222C),
    card          = Color(0xFF1E1E28),
    onBackground  = Color(0xFFF4F4FA),   // daha parlak beyaz
    onSurface     = Color(0xFFCCCCD8),
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
    background    = Color(0xFF221A10),   // biraz daha açık kahve → daha ferah
    surface       = Color(0xFF2C2418),
    surfaceVar    = Color(0xFF362C22),
    card          = Color(0xFF30281C),
    onBackground  = Color(0xFFF8F0DC),   // daha parlak krem
    onSurface     = Color(0xFFD8C8A8),   // daha okunabilir mürekkep
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
    background    = Color(0xFF111C15),   // biraz daha açık yeşil-siyah
    surface       = Color(0xFF18261C),
    surfaceVar    = Color(0xFF1F3023),
    card          = Color(0xFF1C2A20),
    onBackground  = Color(0xFFE8F5EC),   // daha parlak nane beyazı
    onSurface     = Color(0xFFAAC8AF),   // daha okunabilir yeşil-gri
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
    background    = Color(0xFF0C1828),   // biraz daha açık okyanus gecesi
    surface       = Color(0xFF122030),
    surfaceVar    = Color(0xFF192B3E),
    card          = Color(0xFF162436),
    onBackground  = Color(0xFFEAF4FC),   // daha parlak buz mavisi-beyaz
    onSurface     = Color(0xFFA0CCDF),
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
    background    = Color(0xFF1C1018),   // biraz daha açık gece-mor
    surface       = Color(0xFF261822),
    surfaceVar    = Color(0xFF301E2C),
    card          = Color(0xFF2A1C26),
    onBackground  = Color(0xFFFDF0F4),   // daha parlak pembe-beyaz
    onSurface     = Color(0xFFE0B0C5),
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
    background    = Color(0xFF0F0F0F),   // tam siyah yerine çok hafif gri → daha ferah
    surface       = Color(0xFF171717),
    surfaceVar    = Color(0xFF202020),
    card          = Color(0xFF1A1A1A),
    onBackground  = Color(0xFFFFFFFF),
    onSurface     = Color(0xFFC0C0C0),
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
// OnBackground artık kullanıcının seçtiği yazı rengini döndürür (override varsa).
// Tüm ekranlar zaten OnBackground kullandığından tek değişiklikle etki eder.
val OnBackground       @Composable get() = LocalHeftrangColors.current.effectiveTextColor
val EffectiveTextColor @Composable get() = LocalHeftrangColors.current.effectiveTextColor
val Background    @Composable get() = LocalHeftrangColors.current.background
val HeftSurface   @Composable get() = LocalHeftrangColors.current.surface
val SurfaceVar    @Composable get() = LocalHeftrangColors.current.surfaceVar
val HeftCard      @Composable get() = LocalHeftrangColors.current.card
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
    darkMode         : Boolean = true,
    variant          : HeftrangThemeVariant = HeftrangThemeVariant.CHARCOAL_INK,
    textColorOverride: Color? = null,
    content          : @Composable () -> Unit,
) {
    val base        = getColors(variant, darkMode)
    val colors      = if (textColorOverride != null) base.copy(textColorOverride = textColorOverride) else base
    val colorScheme = buildColorScheme(colors)

    CompositionLocalProvider(LocalHeftrangColors provides colors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content     = content,
        )
    }
}
