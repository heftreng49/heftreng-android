package com.heftreng.app.ui.component

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R

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

    AndroidView(
        factory = { context ->
            val view = LayoutInflater.from(context).inflate(layoutId, null) as NativeAdView
            populateNativeAdView(nativeAd, view)
            view
        },
        update  = { view -> populateNativeAdView(nativeAd, view) },
        modifier = modifier.fillMaxWidth(),
    )
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    // 1. MediaView — sabit 200dp yükseklikte, içerik yoksa GONE
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView
    if (nativeAd.mediaContent != null) {
        mediaView.mediaContent = nativeAd.mediaContent!!
        mediaView.visibility   = View.VISIBLE
    } else {
        mediaView.visibility = View.GONE
    }

    // 2. Headline
    adView.headlineView = adView.findViewById<TextView>(R.id.ad_headline).also {
        it.text = nativeAd.headline ?: ""
    }

    // 3. Body
    adView.bodyView = adView.findViewById<TextView>(R.id.ad_body).also {
        if (nativeAd.body.isNullOrBlank()) {
            it.visibility = View.GONE
        } else {
            it.visibility = View.VISIBLE
            it.text = nativeAd.body
        }
    }

    // 4. CTA
    adView.callToActionView = adView.findViewById<Button>(R.id.ad_call_to_action).also {
        if (nativeAd.callToAction.isNullOrBlank()) {
            it.visibility = View.GONE
        } else {
            it.visibility = View.VISIBLE
            it.text = nativeAd.callToAction
        }
    }

    // 5. Icon
    adView.iconView = adView.findViewById<ImageView>(R.id.ad_app_icon).also {
        val icon = nativeAd.icon
        if (icon?.drawable != null) {
            it.setImageDrawable(icon.drawable)
            it.visibility = View.VISIBLE
        } else {
            it.visibility = View.GONE
        }
    }

    // En son setNativeAd — tüm view'lar set edildikten sonra
    adView.setNativeAd(nativeAd)
}
