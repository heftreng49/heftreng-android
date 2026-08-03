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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.ads.AdView
import com.heftreng.app.ads.AdPlacement
import com.heftreng.app.ui.theme.HeftCard
import com.heftreng.app.viewmodel.AdsViewModel

/**
 * Banner ve native reklamlar için tek render noktası.
 * placement türüne (Banner/Native) göre içeriği dallandırır.
 *
 * Kullanım:
 *   val plan = adsVm.planFor("feed", items.size, nativeKey = KEY_NATIVE_FEED, bannerKey = KEY_BANNER_FEED)
 *   plan[index]?.let { placement -> AdSlotView(placement, adsVm) }
 */
@Composable
fun AdSlotView(
    placement: AdPlacement,
    adsVm    : AdsViewModel,
    modifier : Modifier = Modifier,
) {
    // İstek warmVisiblePositions tarafından zaten atıldı.
    // Burada tekrar request* çağırmak her recompose'da çift istek yaratır.
    // Sadece native için dispose (banner lifecycle farklı yönetilir).
    DisposableEffect(placement.slotKey) {
        onDispose {
            if (placement is AdPlacement.Native) adsVm.releaseNative(placement.slotKey)
        }
    }

    when (placement) {
        is AdPlacement.Banner -> BannerSlotContent(placement, adsVm, modifier)
        is AdPlacement.Native -> NativeSlotContent(placement, adsVm, modifier)
    }
}

@Composable
private fun BannerSlotContent(
    placement: AdPlacement.Banner,
    adsVm    : AdsViewModel,
    modifier : Modifier,
) {
    val isLoaded    by adsVm.bannerLoadedFlow(placement.slotKey).collectAsState()
    val isExhausted by adsVm.bannerExhaustedFlow(placement.slotKey).collectAsState()
    val adView   = if (isLoaded) adsVm.cachedBanner(placement.slotKey) else null
    var showDialog by remember { mutableStateOf(false) }

    // Reklam kesin olarak gelmediyse (no-fill, tekrar denenmiyor) shimmer'ı
    // sonsuza kadar döndürmek yerine alanı tamamen kaldırıyoruz — "boş/kırık
    // alan" görünümü yerine sayfa sanki o reklam hiç planlanmamış gibi akıyor.
    if (isExhausted && adView == null) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(bottom = 6.dp),
    ) {
        AdLabel(onInfoClick = { showDialog = true })
        if (adView != null) {
            BannerAndroidView(adView = adView)
        } else {
            AdShimmer(modifier = Modifier.fillMaxWidth().height(60.dp))
        }
    }

    if (showDialog) AdInfoDialog(onDismiss = { showDialog = false })
}

@Composable
private fun NativeSlotContent(
    placement: AdPlacement.Native,
    adsVm    : AdsViewModel,
    modifier : Modifier,
) {
    val isLoaded    by adsVm.nativeLoadedFlow(placement.slotKey).collectAsState()
    val isExhausted by adsVm.nativeExhaustedFlow(placement.slotKey).collectAsState()
    val nativeAd = if (isLoaded) adsVm.cachedNative(placement.slotKey) else null

    if (isExhausted && nativeAd == null) return

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (nativeAd != null) {
            val adSize = when (placement.size.lowercase()) {
                "medium" -> NativeAdSize.MEDIUM
                "large"  -> NativeAdSize.LARGE
                else     -> NativeAdSize.SMALL
            }
            // fillMaxWidth KALDIRILDI — XML'deki layout_marginHorizontal="12dp" artık geçerli.
            // NativeAdView kendi margin ve corner radius'unu taşıyor.
            NativeAdViewCompose(nativeAd = nativeAd, modifier = Modifier.wrapContentSize(), adSize = adSize)
        } else {
            // Shimmer: reklam yüklenirken placeholder — sabit yükseklik burada uygun.
            AdShimmerCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
}

// ── AndroidView sarmalayıcı (banner lifecycle) ─────────────────────────────
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

// ── Ortak UI parçaları ──────────────────────────────────────────────────────

@Composable
private fun AdLabel(onInfoClick: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
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
fun AdShimmerCard(modifier: Modifier = Modifier) {
    val brush = shimmerBrush()
    Box(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(HeftCard),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
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
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(12.dp)).background(brush))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.9f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Spacer(Modifier.height(5.dp))
            Box(Modifier.fillMaxWidth(0.7f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(brush))
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(34.dp).clip(RoundedCornerShape(20.dp)).background(brush))
        }
    }
}

@Composable
fun AdShimmer(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "adShimmer")
    val translateX by transition.animateFloat(
        initialValue  = -300f,
        targetValue   = 600f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
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
