package com.heftreng.app.ui.component

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import com.heftreng.app.R

@Composable
fun NativeAdViewCompose(
    nativeAd: NativeAd,
    modifier: Modifier = Modifier,
    adSize: String = "small" // CMS'den gelen boyut bilgisi
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
        update = { view ->
            populateNativeAdView(nativeAd, view)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
    )
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
    adView.headlineView = adView.findViewById(R.id.ad_headline)
    adView.bodyView = adView.findViewById(R.id.ad_body)
    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
    adView.iconView = adView.findViewById(R.id.ad_app_icon)
    adView.mediaView = adView.findViewById<com.google.android.gms.ads.nativead.MediaView>(R.id.ad_media).also { mv ->
        // MediaView en az 120x120dp olmalı — video içerik varsa göster, yoksa gizle
        if (nativeAd.mediaContent != null) {
            mv.visibility = View.VISIBLE
            mv.mediaContent = nativeAd.mediaContent!!
        } else {
            mv.visibility = View.GONE
        }
    }

    (adView.headlineView as? TextView)?.text = nativeAd.headline

    if (nativeAd.body == null) {
        adView.bodyView?.visibility = View.INVISIBLE
    } else {
        adView.bodyView?.visibility = View.VISIBLE
        (adView.bodyView as? TextView)?.text = nativeAd.body
    }

    if (nativeAd.callToAction == null) {
        adView.callToActionView?.visibility = View.INVISIBLE
    } else {
        adView.callToActionView?.visibility = View.VISIBLE
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction
    }

    if (nativeAd.icon == null) {
        adView.iconView?.visibility = View.GONE
    } else {
        (adView.iconView as? ImageView)?.setImageDrawable(nativeAd.icon?.drawable)
        adView.iconView?.visibility = View.VISIBLE
    }

    adView.setNativeAd(nativeAd)
}
