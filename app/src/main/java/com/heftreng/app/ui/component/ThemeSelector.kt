package com.heftreng.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.heftreng.app.ui.theme.*

// ─────────────────────────────────────────────────────────────────────────────
//  Tema renk önizleme verileri (Tema seçici için)
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

// ─────────────────────────────────────────────────────────────────────────────
//  Tek tema kartı
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ThemeCard(
    variant    : HeftrangThemeVariant,
    isDark     : Boolean,
    isSelected : Boolean,
    onClick    : () -> Unit,
) {
    val preview = variant.previewColors(isDark)
    val borderColor = if (isSelected) Primary else Divider
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Column(
        modifier = Modifier
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
            // Gradyan şerit (accent rengi temsili)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(preview.primary, preview.secondary)
                        )
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
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint  = Color.White,
                        modifier = Modifier.size(12.dp)
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

        // Tema adı
        Text(
            text       = "${variant.emoji()} ${variant.displayName()}",
            fontSize   = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color      = if (isSelected) Primary else OnSurface,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tema Seçici Ana Bileşeni  — Ayarlar ekranına yerleştirin
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ThemeSelector(
    selectedVariant : HeftrangThemeVariant,
    isDarkMode      : Boolean,
    onVariantChange : (HeftrangThemeVariant) -> Unit,
    onDarkModeChange: (Boolean) -> Unit,
    modifier        : Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Başlık
        Text(
            text       = "Tema",
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
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text       = "Koyu Mod",
                    fontSize   = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color      = OnBackground,
                )
                Text(
                    text     = if (isDarkMode) "Açık moda geç" else "Koyu moda geç",
                    fontSize = 12.sp,
                    color    = Muted,
                )
            }
            Switch(
                checked         = isDarkMode,
                onCheckedChange = onDarkModeChange,
            )
        }

        // Tema ızgarası
        LazyVerticalGrid(
            columns             = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement   = Arrangement.spacedBy(10.dp),
            modifier            = Modifier.fillMaxWidth(),
            // İç kaydırma devre dışı — ana kaydırma alanına bırak
            userScrollEnabled   = false,
        ) {
            items(HeftrangThemeVariant.entries) { variant ->
                ThemeCard(
                    variant    = variant,
                    isDark     = isDarkMode,
                    isSelected = variant == selectedVariant,
                    onClick    = { onVariantChange(variant) },
                )
            }
        }
    }
}
