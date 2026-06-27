package com.heftreng.app.ui.component

import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.*
import com.google.android.gms.ads.nativead.NativeAd
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.AdsViewModel

// ── Tekil (sabit) banner bileşeni ─────────────────────────────────────────────
// Sadece preloaded cache kullanır. Cache yoksa AdEngine'e yükleme tetikler ve
// yüklenene kadar shimmer placeholder gösterir — inline loadAd() ÇAĞRILMAZ
// (Compose recomposition'da birden fazla kez çağrılma tehlikesi vardır).
@Composable
fun AdBannerView(
    unitId     : String?,
    modifier   : Modifier = Modifier,
    adsVm      : AdsViewModel? = null,
    slot       : AdsViewModel.BannerSlot = AdsViewModel.BannerSlot.FEED,
    bannerSize : String = "adaptive",
) {
    if (unitId.isNullOrBlank()) return

    // Yükleme durumu ve cache
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

    // Cache yoksa (ilk açılış) AdEngine'e yüklemeyi tetikle — BİR KEZ.
    LaunchedEffect(unitId, slot, bannerSize) {
        if (adsVm != null && !isLoaded) {
            adsVm.preloadBanner(unitId, slot, bannerSize)
        }
    }

    // Boyut değişimini algıla → yeniden yükle
    LaunchedEffect(bannerSize) {
        val ctx = cachedView
        if (ctx != null && adsVm != null) {
            val expected = adsVm.getAdSize(bannerSize)
            if (cachedView.adSize != expected) {
                adsVm.preloadBanner(unitId, slot, bannerSize)
            }
        }
    }

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
        AdLabel(onInfoClick = { showDialog = true })

        if (isLoaded && cachedView != null) {
            val adView: AdView = cachedView
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, adView) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_RESUME  -> adView.resume()
                        Lifecycle.Event.ON_PAUSE   -> adView.pause()
                        Lifecycle.Event.ON_DESTROY -> adView.destroy()
                        else -> {}
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }
            AndroidView(
                factory = { _ ->
                    (adView.parent as? ViewGroup)?.removeView(adView)
                    adView.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                    adView
                },
                update = { view ->
                    view.layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )
                },
                modifier = Modifier.fillMaxWidth().wrapContentHeight(),
            )
        } else {
            AdShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )
        }
    }

    if (showDialog) {
        AdInfoDialog(onDismiss = { showDialog = false })
    }
}

// ── Pozisyon bazlı banner — liste içinde her konuma özel AdView ───────────────
@Composable
fun PositionedAdBannerView(
    positionKey  : String,
    unitId       : String?,
    adsVm        : AdsViewModel,
    modifier     : Modifier = Modifier,
    bannerSize   : String   = "adaptive",
    prefetchKeys : List<Pair<String, String>> = emptyList(),
) {
    if (unitId.isNullOrBlank()) return

    LaunchedEffect(positionKey, unitId, bannerSize) {
        adsVm.preloadPositionedBanner(positionKey, unitId, bannerSize)
        prefetchKeys.forEach { (key, nextUnitId) ->
            if (nextUnitId.isNotBlank()) adsVm.preloadPositionedBanner(key, nextUnitId, bannerSize)
        }
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
        AdLabel(onInfoClick = { showDialog = true })

        if (isLoaded) {
            val adView: AdView? = adsVm.cachedPositionedBanner(positionKey)
            if (adView != null) {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner, adView) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME  -> adView.resume()
                            Lifecycle.Event.ON_PAUSE   -> adView.pause()
                            Lifecycle.Event.ON_DESTROY -> adView.destroy()
                            else -> {}
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }
                AndroidView(
                    factory = { _ ->
                        adView.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                        adView
                    },
                    update = { view ->
                        view.layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        )
                    },
                    modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                )
            }
        } else {
            AdShimmer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            )
        }
    }

    if (showDialog) {
        AdInfoDialog(onDismiss = { showDialog = false })
    }
}

// ── Pozisyon bazlı native reklam ──────────────────────────────────────────────
// CACHE-FIRST: PositionedBannerAdView ile aynı desen.
// DisposableEffect içinde doğrudan AdLoader ÇAĞRILMAZ — her scroll'da yeni
// istek atılmasını engeller. Yükleme ViewModel'deki cache üzerinden yapılır;
// composable sadece cache'i okur. Bu sayede istek sayısı = gerçek gösterim sayısı.
@Composable
fun PositionedNativeAdView(
    positionKey    : String,
    unitId         : String?,
    adsVm          : AdsViewModel,
    modifier       : Modifier = Modifier,
    prefetchKeys   : List<Pair<String, String>> = emptyList(),
    nativeAdContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit,
) {
    if (unitId.isNullOrBlank()) return

    LaunchedEffect(positionKey, unitId) {
        // 1) Bu slotu yükle — havuzda reklam varsa 0ms gecikme
        adsVm.preloadPositionedNative(positionKey, unitId)
        // 2) Sonraki slotları önceden yükle
        prefetchKeys.forEach { (key, nextUnitId) ->
            if (nextUnitId.isNotBlank()) adsVm.preloadPositionedNative(key, nextUnitId)
        }
        // 3) Havuzu yeniden doldur — çekilen yer boş kalmasın.
        //    warmUpNativePool idempotent: havuz zaten doluysa istek atmaz.
        adsVm.warmUpNativePool(unitId)
    }

    val isLoaded by adsVm.positionedNativeLoadedFlow(positionKey).collectAsState()
    val nativeAd = if (isLoaded) adsVm.cachedPositionedNative(positionKey) else null

    if (nativeAd != null) {
        nativeAdContent(nativeAd)
    } else {
        AdShimmer(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

// ── Ortak yardımcı bileşenler ─────────────────────────────────────────────────

@Composable
private fun AdLabel(onInfoClick: () -> Unit) {
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
        IconButton(onClick = onInfoClick, modifier = Modifier.size(20.dp)) {
            Icon(
                imageVector        = Icons.Default.Info,
                contentDescription = "Reklam hakkında",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier           = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun AdInfoDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Bu bir reklamdır") },
        text             = {
            Text(
                "Heftreng tamamen ücretsiz bir uygulamadır. Uygulamayı " +
                "sürdürebilmek için Google AdMob aracılığıyla reklam " +
                "gösteriyoruz.\n\nGördüğünüz içerik Google tarafından " +
                "belirlenmektedir."
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tamam") } },
    )
}

@Composable
fun AdShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "adShimmer")
    val translateX by transition.animateFloat(
        initialValue  = -300f,
        targetValue   = 600f,
        animationSpec = infiniteRepeatable(
            animation  = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerX",
    )
    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                    start = Offset(translateX, 0f),
                    end   = Offset(translateX + 300f, 300f),
                ),
                shape = RoundedCornerShape(8.dp),
            ),
    )
}
