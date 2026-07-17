package com.heftreng.app.ui.component

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
//  Renk önizleme verisi
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
//  Tek satır — liste öğesi
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ThemeListItem(
    variant    : HeftrangThemeVariant,
    isDark     : Boolean,
    isSelected : Boolean,
    language   : String,
    onClick    : () -> Unit,
) {
    val preview = variant.previewColors(isDark)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) preview.primary.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Küçük gradyan renk çipi
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(listOf(preview.primary, preview.secondary))
                )
        )

        // Emoji + isim
        Text(
            text       = "${variant.emoji()} ${variant.localizedName(language)}",
            fontSize   = 15.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (isSelected) preview.primary else OnBackground,
            modifier   = Modifier.weight(1f),
        )

        // Seçili işareti
        if (isSelected) {
            Icon(
                imageVector        = Icons.Default.Check,
                contentDescription = null,
                tint               = preview.primary,
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tema Seçici — dropdown liste
//  - Koyu mod switch'i YOK (yukarıda zaten Görünüm bölümünde var)
//  - LazyVerticalGrid YOK (LazyColumn içinde crash yapar)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ThemeSelector(
    selectedVariant : HeftrangThemeVariant,
    isDarkMode      : Boolean,
    language        : String,
    onVariantChange : (HeftrangThemeVariant) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,   // imza korunuyor, kullanılmıyor
    modifier        : Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = selectedVariant
    val preview  = selected.previewColors(isDarkMode)

    Column(modifier = modifier) {

        // ── Başlık ────────────────────────────────────────────────────────────
        Text(
            text       = Strings.themeTitle(language),
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = Muted,
            modifier   = Modifier.padding(bottom = 6.dp),
        )

        // ── Seçili tema — tıklayınca liste açılır ────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.5.dp,
                    color = if (expanded) preview.primary else Divider,
                    shape = RoundedCornerShape(12.dp),
                )
                .background(HeftSurface)
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Renk chip'i
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(preview.primary, preview.secondary))
                    )
            )

            Text(
                text       = "${selected.emoji()} ${selected.localizedName(language)}",
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                color      = OnBackground,
                modifier   = Modifier.weight(1f),
            )

            Icon(
                imageVector        = if (expanded) Icons.Default.KeyboardArrowUp
                                     else          Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint               = Muted,
                modifier           = Modifier.size(22.dp),
            )
        }

        // ── Açılır liste ──────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = fadeIn() + expandVertically(),
            exit    = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Divider, RoundedCornerShape(12.dp))
                    .background(HeftSurface)
                    .padding(vertical = 4.dp),
            ) {
                HeftrangThemeVariant.entries.forEach { variant ->
                    ThemeListItem(
                        variant    = variant,
                        isDark     = isDarkMode,
                        isSelected = variant == selected,
                        language   = language,
                        onClick    = {
                            onVariantChange(variant)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}
