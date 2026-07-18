package com.heftreng.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─────────────────────────────────────────────────────────────────────────────
//  TEMA TİPOGRAFİSİ
//
//  Tema başına letterSpacing · fontWeight · lineHeight · fontSize farklılaşır.
//  Font dosyası gerektirmez — sisteme dahil fontlarla çalışır.
//  İleride res/font/ eklenirse fontFamily alanı genişletmeye hazır.
// ─────────────────────────────────────────────────────────────────────────────

fun getTypography(variant: HeftrangThemeVariant): Typography = when (variant) {

    // ── CHARCOAL INK — Modern, dengeli, nötr ─────────────────────────────────
    HeftrangThemeVariant.CHARCOAL_INK -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.5.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )

    // ── BOOK — Klasik, sıcak, kütüphane havası ───────────────────────────────
    //  Serif hissi: dar letterSpacing, ağır fontWeight, geniş lineHeight
    HeftrangThemeVariant.BOOK -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,         fontSize = 57.sp, lineHeight = 68.sp, letterSpacing = (-0.5).sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,         fontSize = 45.sp, lineHeight = 54.sp, letterSpacing = (-0.25).sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,     fontSize = 36.sp, lineHeight = 46.sp, letterSpacing = (-0.25).sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,         fontSize = 32.sp, lineHeight = 42.sp, letterSpacing = (-0.25).sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,         fontSize = 28.sp, lineHeight = 38.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,     fontSize = 24.sp, lineHeight = 34.sp, letterSpacing = (-0.15).sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,         fontSize = 22.sp, lineHeight = 30.sp, letterSpacing = (-0.15).sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,     fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,     fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,       fontSize = 17.sp, lineHeight = 30.sp, letterSpacing = 0.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,       fontSize = 15.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal,       fontSize = 13.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    )

    // ── FOREST — Organik, yumuşak, nefes alan ────────────────────────────────
    //  Geniş lineHeight, hafif letterSpacing, normal ağırlık
    HeftrangThemeVariant.FOREST -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 57.sp, lineHeight = 70.sp, letterSpacing = 0.sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 45.sp, lineHeight = 56.sp, letterSpacing = 0.sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 36.sp, lineHeight = 48.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 32.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 28.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 24.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 22.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 16.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 30.sp, letterSpacing = 0.3.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 26.sp, letterSpacing = 0.2.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.2.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.4.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    )

    // ── OCEAN — Ferah, temiz, geniş ─────────────────────────────────────────
    //  Geniş letterSpacing, hafif fontWeight, clean hissi
    HeftrangThemeVariant.OCEAN -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,      fontSize = 57.sp, lineHeight = 66.sp, letterSpacing = (-0.25).sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light,      fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = 0.sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 0.sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 0.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.15.sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 28.sp, letterSpacing = 0.6.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 20.sp, letterSpacing = 0.5.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.6.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp),
    )

    // ── SUNSET — Dinamik, enerjik, vurgulu ───────────────────────────────────
    //  Kalın başlıklar, dar letterSpacing, yüksek kontrast ağırlık atlayışı
    HeftrangThemeVariant.SUNSET -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold,  fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.5).sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.ExtraBold,  fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.25).sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.15).sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.1).sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,       fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.1).sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 16.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 14.sp, lineHeight = 22.sp, letterSpacing = 0.25.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,     fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.3.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,     fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    )

    // ── MONOCHROME — Editorial, keskin, minimal ───────────────────────────────
    //  Monospace font, sıfır letterSpacing, maksimum kontrast
    HeftrangThemeVariant.MONOCHROME -> Typography(
        displayLarge  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,     fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-1).sp),
        displayMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,     fontSize = 45.sp, lineHeight = 52.sp, letterSpacing = (-0.5).sp),
        displaySmall  = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,     fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.5).sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,     fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
        headlineMedium= TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,     fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.25).sp),
        titleLarge    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.25).sp),
        titleMedium   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
        titleSmall    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        bodyLarge     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,   fontSize = 15.sp, lineHeight = 26.sp, letterSpacing = 0.sp),
        bodyMedium    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,   fontSize = 13.sp, lineHeight = 22.sp, letterSpacing = 0.sp),
        bodySmall     = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 18.sp, letterSpacing = 0.sp),
        labelLarge    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
        labelMedium   = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
        labelSmall    = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Normal,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.sp),
    )
}
