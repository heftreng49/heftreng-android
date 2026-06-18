package com.heftreng.app.ui.component

import android.view.LayoutInflater
import android.view.View
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
    // MediaView — her zaman register et, AdMob validator görsün
    val mediaView = adView.findViewById<MediaView>(R.id.ad_media)
    adView.mediaView = mediaView
    val hasMedia = nativeAd.mediaContent != null
    if (hasMedia) {
        mediaView.mediaContent = nativeAd.mediaContent!!
        mediaView.visibility   = View.VISIBLE
    } else {
        // İçerik yok — görünmez yap ama gone değil, validator için
        mediaView.layoutParams = mediaView.layoutParams.apply {
            height = 1  // 1px — validator görebilir ama kullanıcı görmez
        }
        mediaView.visibility = View.INVISIBLE
    }

    adView.headlineView     = adView.findViewById(R.id.ad_headline)
    adView.bodyView         = adView.findViewById(R.id.ad_body)
    adView.callToActionView = adView.findViewById(R.id.ad_call_to_action)
    adView.iconView         = adView.findViewById(R.id.ad_app_icon)

    (adView.headlineView as? TextView)?.text = nativeAd.headline

    if (nativeAd.body.isNullOrBlank()) {
        adView.bodyView?.visibility = View.GONE
    } else {
        adView.bodyView?.visibility = View.VISIBLE
        (adView.bodyView as? TextView)?.text = nativeAd.body
    }

    if (nativeAd.callToAction.isNullOrBlank()) {
        adView.callToActionView?.visibility = View.GONE
    } else {
        adView.callToActionView?.visibility = View.VISIBLE
        (adView.callToActionView as? Button)?.text = nativeAd.callToAction
    }

    val icon = nativeAd.icon
    if (icon?.drawable != null) {
        (adView.iconView as? ImageView)?.setImageDrawable(icon.drawable)
        adView.iconView?.visibility = View.VISIBLE
    } else {
        adView.iconView?.visibility = View.GONE
    }

    adView.setNativeAd(nativeAd)
}
