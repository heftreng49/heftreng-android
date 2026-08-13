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
import com.google.android.gms.ads.VideoController
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R
import com.heftreng.app.ui.theme.HeftCard
import com.heftreng.app.ui.theme.Divider

enum class NativeAdSize { SMALL, MEDIUM, LARGE }

@Composable
fun NativeAdViewCompose(
    nativeAd : NativeAd,
    modifier : Modifier = Modifier,
    adSize   : NativeAdSize = NativeAdSize.LARGE,
) {
    val showMedia = adSize != NativeAdSize.SMALL
    val cardBg     = HeftCard
    val cardBorder = Divider

    // inflated flag — update bloğunda populateAd tekrar çağrılmasın
    val inflated = remember { androidx.compose.runtime.mutableStateOf(false) }

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context)
                .inflate(R.layout.ad_native_template, null) as NativeAdView
            applyTheme(view, cardBg, cardBorder)
            populateAd(nativeAd, view, showMedia, context)
            inflated.value = true
            view
        },
        update = { view ->
            // Sadece tema güncelle — populateAd ÇAĞIRMA.
            // populateAd tekrar çağrılırsa mediaContent yeniden set edilir,
            // video controller sıfırlanır, video başlamaz/durur.
            applyTheme(view, cardBg, cardBorder)
        },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun applyTheme(view: NativeAdView, bg: Color, border: Color) {
    val dp = view.resources.displayMetrics.density
    view.background = GradientDrawable().apply {
        shape        = GradientDrawable.RECTANGLE
        cornerRadius = 16f * dp
        setColor(bg.toArgb())
        setStroke((1f * dp).toInt(), border.toArgb())
    }
}

private fun populateAd(
    nativeAd  : NativeAd,
    adView    : NativeAdView,
    showMedia : Boolean,
    context   : android.content.Context,
) {
    val dm      = context.resources.displayMetrics
    val dp      = dm.density
    val screenW = dm.widthPixels - (24 * dp).toInt() // 12dp*2 CardView margin
    val screenH = dm.heightPixels

    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView

    // ── MediaView ─────────────────────────────────────────────────────────────
    if (showMedia && nativeAd.mediaContent != null) {
        val mc = nativeAd.mediaContent!!

        // mediaContent sadece bir kez set et — tekrar set edilirse video sıfırlanır
        if (mediaView.mediaContent == null) {
            mediaView.mediaContent = mc
        }

        mediaView.clipToOutline = true
        mediaView.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        mediaView.visibility = View.VISIBLE

        // İlk ratio ataması
        val ratio = mc.aspectRatio
        applyRatio(mediaView, ratio, screenW, screenH, dp)

        // Video yüklenince gerçek ratio ile güncelle
        mc.videoController?.videoLifecycleCallbacks =
            object : VideoController.VideoLifecycleCallbacks() {
                override fun onVideoStart() {
                    mediaView.post {
                        applyRatio(mediaView, mc.aspectRatio, screenW, screenH, dp)
                    }
                }
            }
    } else {
        mediaView.visibility = View.GONE
    }

    // ── Diğer view'lar ────────────────────────────────────────────────────────
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

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

    adView.advertiserView = adView.findViewById<TextView>(R.id.ad_advertiser).also {
        val adv = nativeAd.advertiser
        it.visibility = if (!adv.isNullOrBlank()) View.VISIBLE else View.GONE
        it.text = adv ?: ""
    }

    adView.starRatingView = adView.findViewById<RatingBar>(R.id.ad_stars).also {
        val rating = nativeAd.starRating
        if (rating != null && rating > 0) {
            it.rating = rating.toFloat()
            it.visibility = View.VISIBLE
        } else {
            it.visibility = View.GONE
        }
    }

    // setNativeAd her zaman en sonda
    adView.setNativeAd(nativeAd)
}

/**
 * MediaView'u ConstraintSet ile yeniden boyutlandırır.
 * ratio=0 ise (video henüz yüklenmedi) 16:9 fallback kullanılır.
 * Video yüklenince onVideoStart() ile gerçek ratio ile tekrar çağrılır.
 */
private fun applyRatio(
    mediaView : MediaView,
    rawRatio  : Float,
    screenW   : Int,
    screenH   : Int,
    dp        : Float,
) {
    val ratio  = if (rawRatio > 0f) rawRatio else 16f / 9f
    val minH   = (120 * dp).toInt()
    val maxH   = (screenH * 0.55f).toInt()

    val parent = mediaView.parent as? ConstraintLayout ?: return
    val cs     = ConstraintSet()
    cs.clone(parent)

    if (ratio >= 1.0f) {
        // Yatay: tam genişlik, 16:9 oranı
        cs.constrainWidth(R.id.ad_media, ConstraintSet.MATCH_CONSTRAINT)
        cs.constrainHeight(R.id.ad_media, ConstraintSet.MATCH_CONSTRAINT)
        cs.setDimensionRatio(R.id.ad_media, "W,${ratio}:1")
        cs.connect(R.id.ad_media, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        cs.connect(R.id.ad_media, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        cs.constrainMinHeight(R.id.ad_media, minH)
    } else {
        // Dikey: yükseklik sınırlı, ortaya hizalı
        val targetH = ((screenW / ratio).toInt()).coerceIn(minH, maxH)
        val targetW = (targetH * ratio).toInt().coerceAtMost(screenW)
        cs.constrainWidth(R.id.ad_media, targetW)
        cs.constrainHeight(R.id.ad_media, targetH)
        cs.setDimensionRatio(R.id.ad_media, "")
        cs.centerHorizontally(R.id.ad_media, ConstraintSet.PARENT_ID)
    }

    cs.applyTo(parent)
}
