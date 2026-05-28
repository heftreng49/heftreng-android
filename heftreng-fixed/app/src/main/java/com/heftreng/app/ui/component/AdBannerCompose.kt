package com.heftreng.app.ui.component

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

/**
 * AdMob Banner — Compose wrapper.
 * unitId boş veya null gelirse hiçbir şey render edilmez.
 */
@Composable
fun AdBannerView(
    unitId  : String?,
    modifier: Modifier = Modifier,
) {
    if (unitId.isNullOrBlank()) return

    val context = LocalContext.current

    Box(
        modifier         = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory  = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = unitId
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            Log.d("AdBanner", "Banner yüklendi: $unitId")
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            Log.e("AdBanner", "Banner yüklenemedi: ${error.message} (code=${error.code})")
                        }
                        override fun onAdClicked() {
                            Log.d("AdBanner", "Banner tıklandı")
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            },
            update = { adView ->
                if (adView.adUnitId != unitId) {
                    adView.adUnitId = unitId
                    adView.loadAd(AdRequest.Builder().build())
                }
            },
        )
    }
}
