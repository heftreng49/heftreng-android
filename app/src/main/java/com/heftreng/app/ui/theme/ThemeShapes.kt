package com.heftreng.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// ─────────────────────────────────────────────────────────────────────────────
//  TEMA ŞEKİLLERİ
//
//  Material3 shape scale:
//    extraSmall → küçük ikonlar, chip'ler
//    small      → butonlar, metin alanları
//    medium     → kartlar, diyaloglar
//    large      → bottom sheet'ler, büyük kartlar
//    extraLarge → modal bottom sheet
// ─────────────────────────────────────────────────────────────────────────────

fun getShapes(variant: HeftrangThemeVariant): Shapes = when (variant) {

    // ── CHARCOAL INK — Modern, dengeli (12dp orta yuvarlak) ──────────────────
    HeftrangThemeVariant.CHARCOAL_INK -> Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small      = RoundedCornerShape(8.dp),
        medium     = RoundedCornerShape(12.dp),
        large      = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp),
    )

    // ── BOOK — Klasik, minimal yuvarlama (4dp sert köşe) ─────────────────────
    //  Sayfa köşeleri gibi: hemen hemen köşeli, ama kaba değil
    HeftrangThemeVariant.BOOK -> Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small      = RoundedCornerShape(4.dp),
        medium     = RoundedCornerShape(6.dp),
        large      = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(10.dp),
    )

    // ── FOREST — Organik, çok yumuşak (20dp büyük yuvarlak) ─────────────────
    //  Doğal formlar gibi: oval, akışkan
    HeftrangThemeVariant.FOREST -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small      = RoundedCornerShape(14.dp),
        medium     = RoundedCornerShape(20.dp),
        large      = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )

    // ── OCEAN — Akışkan, orta-büyük (16dp) ──────────────────────────────────
    //  Dalga gibi: yumuşak ama aşırı değil
    HeftrangThemeVariant.OCEAN -> Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small      = RoundedCornerShape(10.dp),
        medium     = RoundedCornerShape(16.dp),
        large      = RoundedCornerShape(20.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

    // ── SUNSET — Dinamik, tam yuvarlak (24dp pill etkisi) ───────────────────
    //  Butonlar pill gibi görünür, kartlar büyük oval köşeli
    HeftrangThemeVariant.SUNSET -> Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small      = RoundedCornerShape(16.dp),
        medium     = RoundedCornerShape(24.dp),
        large      = RoundedCornerShape(30.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

    // ── MONOCHROME — Editorial, sert köşe (2dp neredeyse kare) ──────────────
    //  Baskı/dergi estetiği: köşeler keskin, grid hissi baskın
    HeftrangThemeVariant.MONOCHROME -> Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small      = RoundedCornerShape(2.dp),
        medium     = RoundedCornerShape(4.dp),
        large      = RoundedCornerShape(6.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
}
