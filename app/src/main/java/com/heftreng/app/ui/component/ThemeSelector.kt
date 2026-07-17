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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.heftreng.app.ui.i18n.Strings
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Yazı rengi seçenekleri
// ─────────────────────────────────────────────────────────────────────────────
data class TextColorOption(
    val color: Color?,   // null = tema varsayılanı
    val label: String,
)

fun textColorOptions(language: String) = listOf(
    TextColorOption(null,                  Strings.textColorDefault(language)),
    TextColorOption(Color(0xFFF8F8F8),     if (language == "ku") "Spî Geş"  else "Parlak Beyaz"),
    TextColorOption(Color(0xFFE8E8E8),     if (language == "ku") "Spî Nerm" else "Yumuşak Beyaz"),
    TextColorOption(Color(0xFFCCCCCC),     if (language == "ku") "Gewr Sivik" else "Açık Gri"),
    TextColorOption(Color(0xFFFFE4B5),     if (language == "ku") "Zerê Nerm" else "Krem Sarısı"),
    TextColorOption(Color(0xFFB0E0E6),     if (language == "ku") "Şîna Sivik" else "Açık Mavi"),
    TextColorOption(Color(0xFFB8F0C8),     if (language == "ku") "Keska Sivik" else "Açık Yeşil"),
    TextColorOption(Color(0xFFFFD6E0),     if (language == "ku") "Pembe Sivik" else "Açık Pembe"),
)

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
    selectedVariant      : HeftrangThemeVariant,
    isDarkMode           : Boolean,
    language             : String,
    onVariantChange      : (HeftrangThemeVariant) -> Unit,
    onDarkModeChange     : (Boolean) -> Unit,   // imza korunuyor, kullanılmıyor
    textColorOverride    : Color? = null,
    onTextColorChange    : (Color?) -> Unit = {},
    modifier             : Modifier = Modifier,
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

        // ── Yazı Rengi Seçici ─────────────────────────────────────────────────
        Spacer(Modifier.height(14.dp))

        Text(
            text       = Strings.textColorTitle(language),
            fontSize   = 13.sp,
            fontWeight = FontWeight.Medium,
            color      = Muted,
            modifier   = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            textColorOptions(language).forEach { option ->
                val isSelected = textColorOverride == option.color
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            // null (varsayılan) seçeneği için tema primary rengi göster
                            option.color ?: preview.primary
                        )
                        .border(
                            width = if (isSelected) 2.5.dp else 1.dp,
                            color = if (isSelected) preview.primary else Divider,
                            shape = CircleShape,
                        )
                        .clickable { onTextColorChange(option.color) },
                    contentAlignment = Alignment.Center,
                ) {
                    // Varsayılan seçenek için küçük "A" harfi göster
                    if (option.color == null) {
                        Text(
                            text     = "A",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color    = preview.bg,
                        )
                    }
                    // Seçili ise tik işareti
                    if (isSelected && option.color != null) {
                        Icon(
                            imageVector        = Icons.Default.Check,
                            contentDescription = null,
                            tint               = if (option.color.luminance() > 0.5f) Color.Black else Color.White,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Seçili renk etiketi
        val selectedOption = textColorOptions(language).find { it.color == textColorOverride }
        if (selectedOption != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text     = selectedOption.label,
                fontSize = 11.sp,
                color    = Muted,
            )
        }
    }
}
