package com.heftreng.app.ui.component

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R
import com.heftreng.app.ui.theme.HeftCard
import com.heftreng.app.ui.theme.Divider

/**
 * NativeAdViewCompose — sadeleştirilmiş, tek XML layout kullanan versiyon.
 *
 * ESKİ DURUM:
 *   when (adSize) { "medium" -> R.layout.ad_medium_template; "large" -> R.layout.ad_large_template; else -> R.layout.ad_small_template }
 *   → 3 ayrı XML dosyası inflate ediliyordu, içerik identikti.
 *
 * YENİ DURUM:
 *   • TEK XML: R.layout.ad_native_template
 *   • mediaHeightDp parametresi → MediaView yüksekliği compose katmanında ayarlanır
 *   • adSize → Enum'a çevrildi (String "small"/"medium"/"large" yerine)
 *     Neden? String hataya açık. Enum → compile-time güvenlik, IDE autocomplete
 *
 * BOYUTLAR:
 *   NativeAdSize.SMALL  → mediaHeightDp=0  → MediaView gizlenir (küçük kart)
 *   NativeAdSize.MEDIUM → mediaHeightDp=120 → küçük medya önizleme
 *   NativeAdSize.LARGE  → mediaHeightDp=200 → tam medya görünümü
 */
enum class NativeAdSize { SMALL, MEDIUM, LARGE }

@Composable
fun NativeAdViewCompose(
    nativeAd : NativeAd,
    modifier : Modifier = Modifier,
    adSize   : NativeAdSize = NativeAdSize.SMALL,
) {
    // Boyuta göre MediaView yüksekliği (dp cinsinden). 0 = MediaView GONE.
    val mediaHeightDp = when (adSize) {
        NativeAdSize.SMALL  -> 0
        NativeAdSize.MEDIUM -> 120
        NativeAdSize.LARGE  -> 200
    }

    val cardBg     = HeftCard
    val cardBorder = Divider

    AndroidView(
        factory = { context ->
            // TEK layout inflate — boyut farkı mediaHeightDp ile çözülüyor
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ad_native_template, null) as NativeAdView
            applyTheme(view, cardBg, cardBorder)
            populateAd(nativeAd, view, mediaHeightDp, context)
            view
        },
        update = { view ->
            // Tema değişiminde (dark/light) arka plan güncelle
            applyTheme(view, cardBg, cardBorder)
            populateAd(nativeAd, view, mediaHeightDp, view.context)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// ── Private yardımcılar ───────────────────────────────────────────────────────

private fun applyTheme(view: NativeAdView, bg: Color, border: Color) {
    val dp = view.resources.displayMetrics.density
    view.background = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = 16f * dp
        setColor(bg.toArgb())
        setStroke((1f * dp).toInt(), border.toArgb())
    }
}

private fun populateAd(nativeAd: NativeAd, adView: NativeAdView, mediaHeightDp: Int, context: android.content.Context) {
    val dp = context.resources.displayMetrics.density

    // MediaView: yüksekliği dinamik olarak set et
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView
    if (mediaHeightDp > 0 && nativeAd.mediaContent != null) {
        mediaView.mediaContent = nativeAd.mediaContent!!
        // layoutParams null gelebilir (inflate sonrası ölçüm henüz olmadıysa).
        // Her durumda yeni ConstraintLayout.LayoutParams üret — width MATCH_PARENT garantili.
        mediaView.layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            (mediaHeightDp * dp).toInt()
        ).also {
            it.startToStart = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
            it.endToEnd     = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.PARENT_ID
        }
        // İçeriği alana sığdır — zoom değil, letterbox (siyah kenar önlenir)
        mediaView.setImageScaleType(com.google.android.gms.ads.nativead.MediaView.IMAGE_SCALE_TYPE_FIT_CENTER)
        mediaView.visibility = View.VISIBLE
    } else {
        // mediaHeightDp==0 (SMALL) veya medya içerik yok → gizle
        mediaView.visibility = View.GONE
        mediaView.layoutParams = androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0
        )
    }

    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

    // AdMob politikası: "Reklam" ibaresi net, okunabilir ve HER zaman görünür olmalı.
    // Önceden bu view'a kod hiç dokunmuyordu — statik XML default'una (visible) güveniliyordu,
    // yani biri layout'u değiştirip visibility ekleyecek olsa etiket sessizce kaybolabilirdi.
    // Artık her populateAd() çağrısında (ilk yükleme + tema/update) açıkça VISIBLE garanti ediliyor.
    adView.findViewById<TextView>(R.id.ad_sponsored_label).visibility = View.VISIBLE

    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body).also {
        it.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
        it.text = nativeAd.body ?: ""
    }

    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action).also {
        it.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        it.text = nativeAd.callToAction ?: ""
    }

    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon).also {
        val icon = nativeAd.icon
        it.visibility = if (icon?.drawable != null) View.VISIBLE else View.GONE
        if (icon?.drawable != null) it.setImageDrawable(icon.drawable)
    }

    adView.setNativeAd(nativeAd)
}
