package com.heftreng.app.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

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

    Box(
        modifier           = modifier.fillMaxWidth(),
        contentAlignment   = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = unitId
                    loadAd(AdRequest.Builder().build())
                }
            },
            update  = { adView ->
                // unitId değişirse yeni istek gönder
                if (adView.adUnitId != unitId) {
                    adView.adUnitId = unitId
                    adView.loadAd(AdRequest.Builder().build())
                }
            },
        )
    }
}
