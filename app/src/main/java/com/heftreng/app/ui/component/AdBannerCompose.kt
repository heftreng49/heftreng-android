package com.heftreng.app.ui.component

import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.heftreng.app.ui.theme.HeftCard
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
 *
 * NOT: prefetchKeys parametresi kaldırıldı. Önceden her banner kendi bir sonraki
 * konumu önceden ısıtıyordu — bu FeedScreen'deki scroll-tabanlı ön-yükleme
 * (snapshotFlow { firstVisibleItemIndex }) ile çakışıp aynı pozisyona çift
 * istek atılmasına yol açıyordu. Artık ön-yükleme tamamen FeedScreen'de
 * (ve diğer ekranlarda) yönetilir; bu composable sadece gösterimden sorumludur.
 */
@Composable
fun PositionedAdBannerView(
    positionKey  : String,
    unitId       : String?,
    adsVm        : AdsViewModel,
    modifier     : Modifier = Modifier,
    bannerSize   : String   = "adaptive",
) {
    if (unitId.isNullOrBlank()) return

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
 * PositionedNativeAdView — AdMob politikasına uygun model:
 * Pozisyon ekrana girince TEK istek atar (per-position lazy-load).
 * LazyColumn dışına çıkıp dispose olunca hem isteği iptal eder
 * hem de gösterilmemiş reklamı imha eder (boşa istek = fill rate düşer).
 *
 * NEDEN POOL YOK: Önceden yüklenen reklamlar makul sürede gösterilmezse
 * AdMob politika ihlali (guideline: yüklenen ad ~1 saatte gösterilmeli).
 * Havuz yaklaşımı ayrıca istek/gösterim oranını düşürür.
 */
@Composable
fun PositionedNativeAdView(
    positionKey    : String,
    unitId         : String?,
    adsVm          : AdsViewModel,
    modifier       : Modifier = Modifier,
    nativeAdContent: @Composable (com.google.android.gms.ads.nativead.NativeAd) -> Unit,
) {
    val adCardHeight = 250.dp

    // unitId null/boş: RC henüz gelmedi — shimmer göster, layout kaymasin.
    // DisposableEffect yalnızca geçerli unitId ile çalış.
    val isLoaded by adsVm.positionedNativeLoadedFlow(positionKey).collectAsState()
    val nativeAd = if (isLoaded) adsVm.cachedPositionedNative(positionKey) else null

    DisposableEffect(positionKey, unitId) {
        if (!unitId.isNullOrBlank()) {
            adsVm.preloadPositionedNative(positionKey, unitId)
        }
        onDispose {
            adsVm.releasePositionedNative(positionKey)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = adCardHeight)
            .animateContentSize(
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ),
    ) {
        if (nativeAd != null) {
            nativeAdContent(nativeAd)
        } else {
            // Henüz yüklenmedi veya RC bekleniyor — shimmer göster
            AdShimmerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(adCardHeight)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
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

// Gerçek native reklam kartını taklit eden shimmer iskelet
// Yükseklik ve yapı ad_native_template.xml ile eşleşiyor → sıfır layout shift
@Composable
fun AdShimmerCard(modifier: Modifier = Modifier) {
    val brush = com.heftreng.app.ui.component.shimmerBrush()
    Box(
        modifier = modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
            .background(HeftCard),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            // Üst satır: küçük ikon + başlık
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(brush))
                Spacer(Modifier.width(10.dp))
                Column {
                    Box(Modifier.width(140.dp).height(13.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.width(90.dp).height(10.dp).clip(RoundedCornerShape(6.dp)).background(brush))
                }
            }
            Spacer(Modifier.height(12.dp))
            // Medya alanı (büyük dikdörtgen)
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(brush),
            )
            Spacer(Modifier.height(10.dp))
            // Açıklama satırları
            Box(Modifier.fillMaxWidth(0.9f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Spacer(Modifier.height(10.dp))
            // CTA butonu
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(brush),
            )
        }
    }
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
