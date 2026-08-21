package com.heftreng.app.ui.component

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R
import com.heftreng.app.ui.theme.Divider
import com.heftreng.app.ui.theme.HeftCard

enum class NativeAdSize { SMALL, MEDIUM, LARGE }

@Composable
fun NativeAdViewCompose(
    nativeAd : NativeAd,
    modifier : Modifier = Modifier,
    adSize   : NativeAdSize = NativeAdSize.LARGE,
) {
    val showMedia = adSize != NativeAdSize.SMALL
    val cardBg    = HeftCard
    val cardBorder = Divider

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ad_native_template, null) as NativeAdView
            applyCardStyle(view, cardBg, cardBorder)
            populateAd(nativeAd, view, showMedia)
            view
        },
        update = { view ->
            applyCardStyle(view, cardBg, cardBorder)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun applyCardStyle(view: NativeAdView, bg: Color, border: Color) {
    val dp = view.resources.displayMetrics.density
    view.background = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = 16f * dp
        setColor(bg.toArgb())
        setStroke((1f * dp).toInt(), border.toArgb())
    }
}

private fun populateAd(
    nativeAd : NativeAd,
    adView   : NativeAdView,
    showMedia: Boolean,
) {
    val context = adView.context
    val dm      = context.resources.displayMetrics

    // ── MediaView ─────────────────────────────────────────────────────────────
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView

    if (showMedia && nativeAd.mediaContent != null) {
        val mc = nativeAd.mediaContent!!
        mediaView.mediaContent  = mc
        mediaView.clipToOutline = true
        mediaView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        mediaView.visibility    = View.VISIBLE

        val hasVideo = mc.hasVideoContent()

        if (hasVideo) {
            // ── Video reklam: MediaView'un tam ekran genişliğinde, doğru oranda görünmesi ──
            // Sorun: Google'ın video player'ı MediaView içinde kendi letterbox'ını ekliyor.
            // Çözüm 1: aspectRatio bilinirse tam boyutu hemen set et.
            // Çözüm 2: onVideoStart'ta gerçek ratio ile güncelle.
            // Çözüm 3: İç TextureView'u yakalayıp scaleType'ı CENTER_CROP yap.

            val ratio = if (mc.aspectRatio > 0f) mc.aspectRatio else 16f / 9f
            applyVideoSize(mediaView, ratio, dm)

            // Video başlayınca gerçek ratio ile güncelle
            mc.videoController?.videoLifecycleCallbacks =
                object : com.google.android.gms.ads.VideoController.VideoLifecycleCallbacks() {
                    override fun onVideoStart() {
                        val r = mc.aspectRatio.takeIf { it > 0f } ?: ratio
                        mediaView.post { applyVideoSize(mediaView, r, dm) }
                        // İç video view'u yakala ve letterbox'ı kaldır
                        mediaView.post { removeLetterbox(mediaView) }
                    }
                    override fun onVideoPlay() {
                        val r = mc.aspectRatio.takeIf { it > 0f } ?: ratio
                        mediaView.postDelayed({ applyVideoSize(mediaView, r, dm) }, 100)
                        mediaView.postDelayed({ removeLetterbox(mediaView) }, 100)
                    }
                }

            // Hierarchy değişimini izle — video view eklenince letterbox kaldır
            mediaView.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View?, child: View?) {
                    mediaView.post { removeLetterbox(mediaView) }
                }
                override fun onChildViewRemoved(parent: View?, child: View?) {}
            })

        } else {
            // ── Görsel reklam: aspect ratio bilinir, direkt uygula ───────────
            val ratio = if (mc.aspectRatio > 0f) mc.aspectRatio else 1.91f
            applyImageSize(mediaView, ratio, dm)
        }

    } else {
        mediaView.visibility = View.GONE
    }

    // ── Başlık ────────────────────────────────────────────────────────────────
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

    // ── Sponsorlu ─────────────────────────────────────────────────────────────
    adView.findViewById<TextView>(R.id.ad_sponsored_label).visibility = View.VISIBLE

    // ── Açıklama ──────────────────────────────────────────────────────────────
    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body).also {
        it.text       = nativeAd.body ?: ""
        it.visibility = if (nativeAd.body.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ── CTA ───────────────────────────────────────────────────────────────────
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

    // ── Reklamcı ──────────────────────────────────────────────────────────────
    adView.advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser).also {
        it.text       = nativeAd.advertiser ?: ""
        it.visibility = if (nativeAd.advertiser.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    // ── Yıldız ────────────────────────────────────────────────────────────────
    adView.starRatingView = adView.findViewById<RatingBar>(R.id.ad_stars).also {
        val rating = nativeAd.starRating
        it.visibility = if (rating != null && rating > 0) View.VISIBLE else View.GONE
        if (rating != null && rating > 0) it.rating = rating.toFloat()
    }

    adView.setNativeAd(nativeAd)
}

/**
 * Video reklam boyutlandırma — yatay ve dikey için ayrı strateji.
 * Yatay (ratio ≥ 1): tam genişlik, yükseklik = genişlik / ratio. Siyah bant YOK.
 * Dikey (ratio < 1): max ekran yüksekliğinin %65'i, yatayda ortalı.
 */
private fun applyVideoSize(
    mediaView: MediaView,
    ratio    : Float,
    dm       : android.util.DisplayMetrics,
) {
    val dp      = dm.density
    val padding = (24 * dp).toInt()   // sol+sağ 12dp padding
    val screenW = dm.widthPixels - padding
    val screenH = dm.heightPixels

    if (ratio >= 1.0f) {
        // Yatay video — tam genişlik, doğru yükseklik
        val targetH = (screenW / ratio).toInt()
        val params  = mediaView.layoutParams ?: ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        params.width  = ViewGroup.LayoutParams.MATCH_PARENT
        params.height = targetH
        mediaView.layoutParams = params

    } else {
        // Dikey video — sınırlı yükseklik, genişlik orantılı
        val maxH    = (screenH * 0.65f).toInt()
        val targetH = minOf((screenW / ratio).toInt(), maxH)
        val targetW = (targetH * ratio).toInt()
        val params  = mediaView.layoutParams ?: ViewGroup.LayoutParams(targetW, targetH)
        params.width  = targetW
        params.height = targetH
        mediaView.layoutParams = params
    }
    mediaView.requestLayout()
}

/**
 * Görsel reklam boyutlandırma — aspect ratio bilinir.
 */
private fun applyImageSize(
    mediaView: MediaView,
    ratio    : Float,
    dm       : android.util.DisplayMetrics,
) {
    val dp      = dm.density
    val screenW = dm.widthPixels - (24 * dp).toInt()
    val targetH = (screenW / ratio).toInt()
    val params  = mediaView.layoutParams ?: ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, targetH
    )
    params.width  = ViewGroup.LayoutParams.MATCH_PARENT
    params.height = targetH
    mediaView.layoutParams = params
    mediaView.requestLayout()
}

/**
 * MediaView içindeki Google video player'ın eklediği siyah letterbox bantlarını kaldırır.
 *
 * Google'ın native video player'ı MediaView → ExoPlayerView → TextureView hiyerarşisinde
 * çalışır. TextureView'un parent'ı olan FrameLayout arka planı siyah olabilir ve
 * scaleType CENTER yerine FIT_CENTER kullanılabilir. Bu fonksiyon:
 * 1. Tüm alt View'ların arka planını şeffaf yapar.
 * 2. ImageView varsa scaleType CENTER_CROP yapar (görsel reklamlar için).
 * 3. TextureView varsa parent'ının gravity'sini CENTER yapar.
 */
private fun removeLetterbox(view: View) {
    view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            when (child) {
                is ImageView -> {
                    child.scaleType = ImageView.ScaleType.CENTER_CROP
                    child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
                is android.widget.FrameLayout -> {
                    child.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    (child.layoutParams as? android.widget.FrameLayout.LayoutParams)?.gravity =
                        android.view.Gravity.CENTER
                    removeLetterbox(child)
                }
                is ViewGroup -> removeLetterbox(child)
            }
        }
    }
}
