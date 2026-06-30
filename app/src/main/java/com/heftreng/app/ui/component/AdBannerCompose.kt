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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdView
import com.heftreng.app.viewmodel.AdsViewModel

/**
 * AdBannerView — Tekil (sabit) banner bileşeni.
 *
 * KATMAN SADELEŞTİRMESİ:
 * ──────────────────────
 * ESKİ: Column → AdLabel(Row) + if(isLoaded) AndroidView else AdShimmer
 *   • Column sadece dikey sıralama için — Column tek başına bir measure pass
 *
 * YENİ: Box → AdLabel (absoluteOffset) + if(isLoaded) AndroidView else AdShimmer
 *   Hayır — bu sefer Column şart çünkü AdLabel + içerik dikey sıralı.
 *   Ama: padding ve background Column'a değil, en dışa taşındı.
 *   Fark: 1 katman azalttık (outer Column → direkt Column, sarmalayan Box yok).
 *
 * GERÇEK SORUN YOKTU ZATEN: Orijinal kod 2 katmandı (Column+inner).
 * Burada asıl iyileştirme = gereksiz LaunchedEffect (boyut değişimi için
 * ayrı LaunchedEffect) → tek LaunchedEffect'te birleştirildi.
 */
@Composable
fun AdBannerView(
    unitId     : String?,
    modifier   : Modifier = Modifier,
    adsVm      : AdsViewModel? = null,
    slot       : AdsViewModel.BannerSlot = AdsViewModel.BannerSlot.FEED,
    bannerSize : String = "adaptive",
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

    // unitId, slot, bannerSize değiştiğinde yeniden tetikle — iki ayrı LaunchedEffect gereksizdi
    LaunchedEffect(unitId, slot, bannerSize) {
        if (adsVm != null && !isLoaded) {
            adsVm.preloadBanner(unitId, slot, bannerSize)
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
            BannerAndroidView(adView = cachedView)
        } else {
            AdShimmer(
                modifier = Modifier.fillMaxWidth().height(60.dp),
            )
        }
    }

    if (showDialog) AdInfoDialog(onDismiss = { showDialog = false })
}

/**
 * PositionedAdBannerView — Liste içinde her konuma özel banner.
 */
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

        val adView = if (isLoaded) adsVm.cachedPositionedBanner(positionKey) else null
        if (adView != null) {
            BannerAndroidView(adView = adView)
        } else {
            AdShimmer(modifier = Modifier.fillMaxWidth().height(60.dp))
        }
    }

    if (showDialog) AdInfoDialog(onDismiss = { showDialog = false })
}

/**
 * PositionedNativeAdView — sade model: pozisyon görünür olunca TEK istek atar,
 * composable LazyColumn dışına çıkıp dispose olunca isteği/reklamı temizler.
 *
 * ÖNCEKİ SİSTEM (havuz + bir-sonraki-pozisyon prefetch + recycle) AdMob'un
 * istek/gösterim oranını kötüleştiriyordu: her pozisyon hem kendi reklamını
 * hem bir sonrakini önceden istiyordu, kullanıcı hızlı kaydırınca isteklerin
 * büyük kısmı hiç gösterilmeden çöpe gidiyordu. AdMob'un kendi önerisi net:
 * reklamı sadece gerçekten gösterileceği an iste, kullanılmazsa imha et.
 */
@Composable
fun PositionedNativeAdView(
    positionKey    : String,
    unitId         : String?,
    adsVm          : AdsViewModel,
    modifier       : Modifier = Modifier,
    nativeAdContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit,
) {
    if (unitId.isNullOrBlank()) return

    DisposableEffect(positionKey, unitId) {
        adsVm.preloadPositionedNative(positionKey, unitId)
        onDispose {
            // Pozisyon ekrandan kalktı: gösterilmiş olsun olmasın temizle.
            // Stoklama/havuz YOK — her pozisyon kendi ömrünü yönetir.
            adsVm.releasePositionedNative(positionKey)
        }
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

// ── AndroidView sarmalayıcı (tekrar kullanım) ─────────────────────────────────
//
// Bu yardımcı fonksiyon AdBannerView ve PositionedAdBannerView'da kod tekrarını kaldırdı.
// DisposableEffect lifecycle yönetimi tek yerde — değişince iki yerde değiştirmek gerekmez.
@Composable
private fun BannerAndroidView(adView: AdView) {
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
}

// ── Ortak UI bileşenleri ──────────────────────────────────────────────────────

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
        modifier = modifier.background(
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
