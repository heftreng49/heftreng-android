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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R
import com.heftreng.app.ui.theme.HeftCard
import com.heftreng.app.ui.theme.Divider

@Composable
fun NativeAdViewCompose(
    nativeAd : NativeAd,
    modifier : Modifier = Modifier,
    adSize   : String   = "small",
) {
    val layoutId = when (adSize) {
        "medium" -> R.layout.ad_medium_template
        "large"  -> R.layout.ad_large_template
        else     -> R.layout.ad_small_template
    }

    // Compose tema renklerini al — LocalHeftrangColors'tan geliyor, light/dark otomatik
    val cardBg     = HeftCard
    val cardBorder = Divider

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context).inflate(layoutId, null) as NativeAdView
            applyThemeBackground(view, cardBg, cardBorder)
            populateNativeAdView(nativeAd, view)
            view
        },
        update = { view ->
            applyThemeBackground(view, cardBg, cardBorder)
            populateNativeAdView(nativeAd, view)
        },
        modifier = modifier.fillMaxWidth(), // reserved ad container
    )
}

private fun applyThemeBackground(view: NativeAdView, cardBg: Color, cardBorder: Color) {
    val drawable = GradientDrawable().apply {
        shape         = GradientDrawable.RECTANGLE
        cornerRadius  = 16f * view.resources.displayMetrics.density
        setColor(cardBg.toArgb())
        setStroke(
            (1f * view.resources.displayMetrics.density).toInt(),
            cardBorder.toArgb(),
        )
    }
    view.background = drawable
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView
    if (nativeAd.mediaContent != null) {
        mediaView.mediaContent = nativeAd.mediaContent!!
        mediaView.visibility   = View.VISIBLE
    } else {
        mediaView.visibility = View.GONE
    }

    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body).also {
        if (nativeAd.body.isNullOrBlank()) {
            it.visibility = View.GONE
        } else {
            it.visibility = View.VISIBLE
            it.text = nativeAd.body
        }
    }

    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action).also {
        if (nativeAd.callToAction.isNullOrBlank()) {
            it.visibility = View.GONE
        } else {
            it.visibility = View.VISIBLE
            it.text = nativeAd.callToAction
        }
    }

    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon).also {
        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            it.setImageDrawable(icon.drawable)
            it.visibility = View.VISIBLE
        } else {
            it.visibility = View.GONE
        }
    }

    adView.setNativeAd(nativeAd)
}
