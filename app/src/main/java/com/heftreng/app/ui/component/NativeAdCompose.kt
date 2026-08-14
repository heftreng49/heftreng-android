package com.heftreng.app.ui.component

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R
import com.heftreng.app.ui.theme.Divider
import com.heftreng.app.ui.theme.HeftCard

// ─────────────────────────────────────────────────────────────────────────────
//  NativeAdViewCompose
//
//  Google NativeAd'i XML şablona (ad_native_template.xml) bağlar.
//
//  Boyutlandırma stratejisi:
//  • XML'de MediaView 0dp/0dp + varsayılan 16:9 ratio tanımlı.
//  • Reklam yüklenince gerçek aspectRatio okunur, ConstraintSet ile güncellenir.
//  • Yatay (ratio ≥ 1): tam genişlik, yükseklik = genişlik / ratio
//  • Dikey (ratio < 1): ekran yüksekliğinin max %60'ı, genişlik = yükseklik × ratio
//  • ratio = 0 (henüz bilinmiyor): 16:9 fallback
// ─────────────────────────────────────────────────────────────────────────────

enum class NativeAdSize { SMALL, MEDIUM, LARGE }

@Composable
fun NativeAdViewCompose(
    nativeAd : NativeAd,
    modifier : Modifier = Modifier,
    adSize   : NativeAdSize = NativeAdSize.LARGE,
) {
    val showMedia  = adSize != NativeAdSize.SMALL
    val cardBg     = HeftCard
    val cardBorder = Divider
    val populated  = remember { androidx.compose.runtime.mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ad_native_template, null) as NativeAdView
            applyCardStyle(view, cardBg, cardBorder)
            populateAd(nativeAd, view, showMedia, context)
            populated.value = true
            view
        },
        update = { view ->
            // Sadece tema — populateAd tekrar çağrılırsa video sıfırlanır
            applyCardStyle(view, cardBg, cardBorder)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// ─────────────────────────────────────────────────────────────────────────────
//  applyCardStyle — kart arka plan ve kenarlık
// ─────────────────────────────────────────────────────────────────────────────

private fun applyCardStyle(view: NativeAdView, bg: Color, border: Color) {
    val dp = view.resources.displayMetrics.density
    view.background = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = 16f * dp
        setColor(bg.toArgb())
        setStroke((1f * dp).toInt(), border.toArgb())
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  populateAd — reklam verilerini XML view'larına bağlar
// ─────────────────────────────────────────────────────────────────────────────

private fun populateAd(
    nativeAd  : NativeAd,
    adView    : NativeAdView,
    showMedia : Boolean,
    context   : android.content.Context,
) {
    val dm = context.resources.displayMetrics

    // ── MediaView ─────────────────────────────────────────────────────────────
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView

    if (showMedia && nativeAd.mediaContent != null) {
        val mc = nativeAd.mediaContent!!
        if (mediaView.mediaContent == null) {
            mediaView.mediaContent = mc
        }
        mediaView.clipToOutline = true
        mediaView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        mediaView.visibility = View.VISIBLE
        // aspectRatio: yüklenmeden önce 0 gelebilir → updateMediaSize içinde fallback var
        updateMediaSize(mediaView, mc.aspectRatio, dm)
    } else {
        mediaView.visibility = View.GONE
    }

    // ── Başlık ────────────────────────────────────────────────────────────────
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

    // ── Sponsorlu etiketi ─────────────────────────────────────────────────────
    adView.findViewById<TextView>(R.id.ad_sponsored_label).visibility = View.VISIBLE

    // ── Açıklama ──────────────────────────────────────────────────────────────
    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body).also {
        it.text       = nativeAd.body ?: ""
        it.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ── CTA butonu ────────────────────────────────────────────────────────────
    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action).also {
        it.text       = nativeAd.callToAction ?: ""
        it.visibility = if (nativeAd.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ── İkon ──────────────────────────────────────────────────────────────────
    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon).also {
        val icon = nativeAd.icon
        it.visibility = if (icon?.drawable != null) View.VISIBLE else View.GONE
        if (icon?.drawable != null) it.setImageDrawable(icon.drawable)
    }

    // ── Reklamcı adı ──────────────────────────────────────────────────────────
    adView.advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser).also {
        it.text       = nativeAd.advertiser ?: ""
        it.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ── Yıldız değerlendirmesi ────────────────────────────────────────────────
    adView.starRatingView = adView.findViewById<RatingBar>(R.id.ad_stars).also {
        val rating = nativeAd.starRating
        it.visibility = if (rating != null && rating > 0) View.VISIBLE else View.GONE
        if (rating != null && rating > 0) it.rating = rating.toFloat()
    }

    // setNativeAd her zaman en sonda çağrılmalı
    adView.setNativeAd(nativeAd)
}

// ─────────────────────────────────────────────────────────────────────────────
//  updateMediaSize — MediaView boyutunu reklamın aspect ratio'suna göre ayarlar
//
//  Yatay (ratio ≥ 1.0):
//    genişlik = container genişliği (MATCH_CONSTRAINT)
//    yükseklik = genişlik / ratio  → ConstraintSet "W,ratio:1"
//
//  Dikey (ratio < 1.0):
//    yükseklik = min(ekran yüksekliği × 0.60, ham yükseklik)
//    genişlik  = yükseklik × ratio  → sabit px
//    MediaView ortaya hizalanır
//
//  ratio = 0 → 16:9 fallback (reklam henüz yüklenmiyor)
// ─────────────────────────────────────────────────────────────────────────────

private fun updateMediaSize(
    mediaView : MediaView,
    rawRatio  : Float,
    dm        : android.util.DisplayMetrics,
) {
    val ratio   = if (rawRatio > 0f) rawRatio else 16f / 9f
    val dp      = dm.density
    val screenW = dm.widthPixels - (24 * dp).toInt()   // 12dp × 2 padding
    val screenH = dm.heightPixels
    val minH    = (120 * dp).toInt()
    val maxH    = (screenH * 0.60f).toInt()

    val parent = mediaView.parent as? ConstraintLayout ?: return
    val cs     = ConstraintSet()
    cs.clone(parent)

    if (ratio >= 1.0f) {
        // ── Yatay reklam ─────────────────────────────────────────────────────
        // MATCH_CONSTRAINT genişlik + dimensionRatio → ConstraintLayout hesaplar
        cs.constrainWidth(R.id.ad_media, ConstraintSet.MATCH_CONSTRAINT)
        cs.constrainHeight(R.id.ad_media, ConstraintSet.MATCH_CONSTRAINT)
        cs.connect(R.id.ad_media, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START, 0)
        cs.connect(R.id.ad_media, ConstraintSet.END,   ConstraintSet.PARENT_ID, ConstraintSet.END,   0)
        cs.setDimensionRatio(R.id.ad_media, "W,${ratio}:1")
        cs.constrainMinHeight(R.id.ad_media, minH)
        cs.constrainMaxHeight(R.id.ad_media, ConstraintSet.WRAP_CONTENT)
    } else {
        // ── Dikey reklam ─────────────────────────────────────────────────────
        // Sabit px boyut + yatay ortalama
        val rawH    = (screenW / ratio).toInt()
        val targetH = rawH.coerceIn(minH, maxH)
        val targetW = (targetH * ratio).toInt().coerceAtMost(screenW)
        cs.constrainWidth(R.id.ad_media, targetW)
        cs.constrainHeight(R.id.ad_media, targetH)
        cs.setDimensionRatio(R.id.ad_media, "")
        cs.centerHorizontally(R.id.ad_media, ConstraintSet.PARENT_ID)
    }

    cs.applyTo(parent)
}
