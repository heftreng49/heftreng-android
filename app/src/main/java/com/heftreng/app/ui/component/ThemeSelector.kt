package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Tema renk önizleme verileri
// ─────────────────────────────────────────────────────────────────────────────
data class ThemePreviewColors(
    val bg: Color,
    val primary: Color,
    val secondary: Color,
)

fun HeftrangThemeVariant.previewColors(isDark: Boolean): ThemePreviewColors {
    val c = getColors(this, isDark)
    return ThemePreviewColors(
        bg        = c.background,
        primary   = c.primary,
        secondary = c.gradientEnd,
    )
}

fun HeftrangThemeVariant.localizedName(language: String): String = when (this) {
    HeftrangThemeVariant.CHARCOAL_INK -> Strings.themeCharcoal(language)
    HeftrangThemeVariant.BOOK         -> Strings.themeBook(language)
    HeftrangThemeVariant.FOREST       -> Strings.themeForest(language)
    HeftrangThemeVariant.OCEAN        -> Strings.themeOcean(language)
    HeftrangThemeVariant.SUNSET       -> Strings.themeSunset(language)
    HeftrangThemeVariant.MONOCHROME   -> Strings.themeMonochrome(language)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tek tema kartı
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ThemeCard(
    variant    : HeftrangThemeVariant,
    isDark     : Boolean,
    isSelected : Boolean,
    language   : String,
    modifier   : Modifier = Modifier,
    onClick    : () -> Unit,
) {
    val preview     = variant.previewColors(isDark)
    val borderColor = if (isSelected) Primary else Divider
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Mini önizleme kutusu
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(preview.bg),
        ) {
            // Gradyan şerit
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(listOf(preview.primary, preview.secondary))
                    )
            )

            // Seçili işareti
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                        .background(preview.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = Icons.Default.Check,
                        contentDescription = null,
                        tint               = Color.White,
                        modifier           = Modifier.size(12.dp),
                    )
                }
            }

            // Sahte içerik çizgileri
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(2) { i ->
                    Box(
                        modifier = Modifier
                            .width(if (i == 0) 36.dp else 24.dp)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(preview.primary.copy(alpha = if (i == 0) 0.9f else 0.5f))
                    )
                }
            }
        }

        // Tema adı — dile göre
        Text(
            text       = "${variant.emoji()} ${variant.localizedName(language)}",
            fontSize   = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (isSelected) Primary else OnSurface,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tema Seçici Ana Bileşeni
//  NOT: LazyVerticalGrid KULLANILMAZ — LazyColumn içinde sonsuz yükseklik
//  kısıtlamasına yol açar (crash). Bunun yerine sabit Row × 2 düzeni.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ThemeSelector(
    selectedVariant : HeftrangThemeVariant,
    isDarkMode      : Boolean,
    language        : String,
    onVariantChange : (HeftrangThemeVariant) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    modifier        : Modifier = Modifier,
) {
    // 6 tema → 2 satır × 3 sütun (sabit, Lazy değil)
    val rows = HeftrangThemeVariant.entries.chunked(3)

    Column(
        modifier            = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Başlık
        Text(
            text       = Strings.themeTitle(language),
            fontSize   = 18.sp,
            fontWeight = FontWeight.Bold,
            color      = OnBackground,
        )

        // Koyu/Açık mod geçişi
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HeftSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text       = Strings.themeDarkMode(language),
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = OnBackground,
                )
                Text(
                    text     = if (isDarkMode)
                        Strings.themeDarkModeToLight(language)
                    else
                        Strings.themeDarkModeToDark(language),
                    fontSize = 12.sp,
                    color    = Muted,
                )
            }
            Switch(
                checked         = isDarkMode,
                onCheckedChange = onDarkModeChange,
            )
        }

        // Tema ızgarası — Row × 2, Lazy yok
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { rowVariants ->
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    rowVariants.forEach { variant ->
                        ThemeCard(
                            variant    = variant,
                            isDark     = isDarkMode,
                            isSelected = variant == selectedVariant,
                            language   = language,
                            modifier   = Modifier.weight(1f),
                            onClick    = { onVariantChange(variant) },
                        )
                    }
                    // Satırda 3'ten az tema varsa boşluk doldur
                    repeat(3 - rowVariants.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
