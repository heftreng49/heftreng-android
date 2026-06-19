package com.heftreng.app.ui.component

import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.IconButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.*
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdsViewModel

// ── Preloaded banner'ı kullanan ana bileşen ───────────────────────────────────
// adsVm null ise factory'den yeni AdView üretir (fallback)
@Composable
fun AdBannerView(
    unitId     : String?,
    modifier   : Modifier = Modifier,
    adsVm      : AdsViewModel? = null,
    slot       : AdsViewModel.BannerSlot = AdsViewModel.BannerSlot.FEED,
    bannerSize : String = "adaptive",   // CMS'den gelen boyut: adaptive/banner/medium_rectangle/large_banner
) {
    if (unitId.isNullOrBlank()) return

    val isLoaded = when (slot) {
        AdsViewModel.BannerSlot.FEED  -> adsVm?.bannerFeedLoaded?.collectAsState()?.value  ?: false
        AdsViewModel.BannerSlot.LIB   -> adsVm?.bannerLibLoaded?.collectAsState()?.value   ?: false
        AdsViewModel.BannerSlot.KURDI -> adsVm?.bannerKurdiLoaded?.collectAsState()?.value ?: false
        AdsViewModel.BannerSlot.BLOG  -> adsVm?.bannerBlogLoaded?.collectAsState()?.value  ?: false
    }

    val cachedView = when (slot) {
        AdsViewModel.BannerSlot.FEED  -> adsVm?.cachedFeedBanner
        AdsViewModel.BannerSlot.LIB   -> adsVm?.cachedLibBanner
        AdsViewModel.BannerSlot.KURDI -> adsVm?.cachedKurdiBanner
        AdsViewModel.BannerSlot.BLOG  -> adsVm?.cachedBlogBanner
    }

    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            )
            .padding(bottom = 6.dp),
    ) {
        // Reklam etiketi + bilgi butonu
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text  = "Reklam",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style = androidx.compose.ui.text.TextStyle(letterSpacing = 0.6.sp),
            )
            IconButton(
                onClick  = { showDialog = true },
                modifier = Modifier.size(20.dp),
            ) {
                Icon(
                    imageVector        = Icons.Default.Info,
                    contentDescription = "Reklam hakkında",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier           = Modifier.size(14.dp),
                )
            }
        }

        // Preloaded cache varsa ve boyut uyuşuyorsa kullan, yoksa inline yükle
        // Boyut uyuşmazsa adsVm'e haber ver → yeniden preload başlatılır
        val cachedSizeMatches = cachedView?.adSize?.let { adSize ->
            val ctx = LocalContext.current
            val expected = adsVm?.getAdSize(bannerSize)
            expected == null || adSize == expected
        } ?: false

        if (cachedView != null && cachedSizeMatches) {
            AndroidView(
                factory  = { _ ->
                    (cachedView.parent as? ViewGroup)?.removeView(cachedView)
                    cachedView
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
        } else {
            // Boyut değiştiyse önbelleği temizle ve yeni boyutla yükle
            LaunchedEffect(bannerSize) {
                if (cachedView != null && !cachedSizeMatches) {
                    val uid = unitId
                    adsVm?.preloadBanner(uid, slot, bannerSize)
                }
            }
            AndroidView(
                factory = { ctx ->
                    val dm    = ctx.resources.displayMetrics
                    val width = (dm.widthPixels / dm.density).toInt()
                    val size  = when (bannerSize) {
                        "banner"           -> AdSize.BANNER
                        "medium_rectangle" -> AdSize.MEDIUM_RECTANGLE
                        "large_banner"     -> AdSize.LARGE_BANNER
                        else               -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width)
                    }
                    AdView(ctx).apply {
                        setAdSize(size)
                        adUnitId = unitId
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        adListener = object : AdListener() {
                            override fun onAdLoaded() {
                                Log.d("AdBanner", "Yüklendi: slot=$slot size=$bannerSize")
                            }
                            override fun onAdFailedToLoad(e: LoadAdError) {
                                Log.e("AdBanner", "Hata: ${e.code} ${e.message}")
                            }
                        }
                        loadAd(AdRequest.Builder()
                            .setContentUrl("https://heftreng.app")
                            .build())
                    }
                },
                update = { adView ->
                    if (adView.adUnitId != unitId) {
                        adView.adUnitId = unitId
                        adView.loadAd(AdRequest.Builder()
                            .setContentUrl("https://heftreng.app").build())
                    }
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("Bu bir reklamdır") },
            text             = { Text("""Heftreng tamamen ücretsiz bir uygulamadır. Uygulamayı sürdürebilmek ve sunucu maliyetlerini karşılayabilmek için Google AdMob aracılığıyla reklam gösteriyoruz.

Gördüğünüz içerik, Google tarafından belirlenen bir reklamdır.""") },
            confirmButton    = { TextButton(onClick = { showDialog = false }) { Text("Tamam") } },
        )
    }
}

// ── Pozisyon bazlı banner — liste içinde her konuma özel AdView ───────────────
// Kullanım: LazyColumn items { } içinde her banner satırı için
//   PositionedAdBannerView(positionKey = "feed_banner_$index", unitId = unitId, adsVm = adsVm)
@Composable
fun PositionedAdBannerView(
    positionKey : String,
    unitId      : String?,
    adsVm       : AdsViewModel,
    modifier    : Modifier = Modifier,
    bannerSize  : String   = "adaptive",
) {
    if (unitId.isNullOrBlank()) return

    // İlk göründüğünde yükle
    LaunchedEffect(positionKey, unitId, bannerSize) {
        adsVm.preloadPositionedBanner(positionKey, unitId, bannerSize)
    }

    val isLoaded by adsVm.positionedBannerLoadedFlow(positionKey).collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                color  = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape  = RoundedCornerShape(10.dp),
            )
            .padding(bottom = 6.dp),
    ) {
        Row(
            modifier              = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Text(
                text     = "Reklam",
                fontSize = 10.sp,
                color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                style    = androidx.compose.ui.text.TextStyle(letterSpacing = 0.6.sp),
            )
            IconButton(onClick = { showDialog = true }, modifier = Modifier.size(20.dp)) {
                Icon(
                    imageVector        = Icons.Default.Info,
                    contentDescription = "Reklam hakkında",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier           = Modifier.size(14.dp),
                )
            }
        }

        if (isLoaded) {
            val adView = adsVm.cachedPositionedBanner(positionKey)
            if (adView != null) {
                AndroidView(
                    factory  = { _ ->
                        // Bu View yalnızca bu key'e ait — başka hiçbir konumla paylaşılmıyor.
                        // removeView gerekmez; parent zaten bu tek Composable.
                        adView
                    },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                )
            }
        } else {
            // Yüklenene kadar yer tutucu
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                    )
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("Bu bir reklamdır") },
            text             = { Text("Heftreng tamamen ücretsiz bir uygulamadır. Uygulamayı sürdürebilmek ve sunucu maliyetlerini karşılayabilmek için Google AdMob aracılığıyla reklam gösteriyoruz.\n\nGördüğünüz içerik, Google tarafından belirlenen bir reklamdır.") },
            confirmButton    = { TextButton(onClick = { showDialog = false }) { Text("Tamam") } },
        )
    }
}

// ── Pozisyon bazlı native reklam — liste içinde her konuma özel NativeAd ──────
// Kullanım: LazyColumn items { } içinde her native satırı için
//   PositionedNativeAdView(positionKey = "feed_native_$index", unitId = unitId, adsVm = adsVm)
@Composable
fun PositionedNativeAdView(
    positionKey    : String,
    unitId         : String?,
    adsVm          : AdsViewModel,
    modifier       : Modifier = Modifier,
    nativeAdContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit,
) {
    if (unitId.isNullOrBlank()) return

    LaunchedEffect(positionKey, unitId) {
        adsVm.preloadPositionedNative(positionKey, unitId)
    }

    val isLoaded by adsVm.positionedNativeLoadedFlow(positionKey).collectAsState()
    val nativeAd = if (isLoaded) adsVm.cachedPositionedNative(positionKey) else null

    if (nativeAd != null) {
        nativeAdContent(nativeAd)
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                )
        )
    }
}

// ── Shimmer animasyonu ────────────────────────────────────────────────────────
@Composable
private fun AdShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateX by transition.animateFloat(
        initialValue   = -300f,
        targetValue    = 600f,
        animationSpec  = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        SurfaceVar,
                        SurfaceVar.copy(alpha = 0.4f),
                        SurfaceVar,
                    ),
                    start = Offset(translateX, 0f),
                    end   = Offset(translateX + 300f, 300f),
                )
            ),
    )
}
