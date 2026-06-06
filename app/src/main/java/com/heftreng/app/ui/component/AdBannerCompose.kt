package com.heftreng.app.ui.component

import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Announcement
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    unitId   : String?,
    modifier : Modifier = Modifier,
    adsVm    : AdsViewModel? = null,
    slot     : AdsViewModel.BannerSlot = AdsViewModel.BannerSlot.FEED,
) {
    if (unitId.isNullOrBlank()) return

    val isLoaded = when (slot) {
        AdsViewModel.BannerSlot.FEED  -> adsVm?.bannerFeedLoaded?.collectAsState()?.value  ?: false
        AdsViewModel.BannerSlot.LIB   -> adsVm?.bannerLibLoaded?.collectAsState()?.value   ?: false
        AdsViewModel.BannerSlot.KURDI -> adsVm?.bannerKurdiLoaded?.collectAsState()?.value ?: false
    }

    val cachedView = when (slot) {
        AdsViewModel.BannerSlot.FEED  -> adsVm?.cachedFeedBanner
        AdsViewModel.BannerSlot.LIB   -> adsVm?.cachedLibBanner
        AdsViewModel.BannerSlot.KURDI -> adsVm?.cachedKurdiBanner
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showDialog   by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        shape  = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
    ) {
        Column(
            modifier            = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Üst bilgi satırı
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Announcement, null,
                        tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reklam",
                        color    = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        style    = androidx.compose.ui.text.TextStyle(letterSpacing = 0.4.sp))
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.MoreVert, "Menü",
                            tint     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text    = { Text("Bu reklam neden görünüyor?") },
                            onClick = { menuExpanded = false; showDialog = true },
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Sabit rezervasyon kutusu — No Layout Shift ────────────────────
            // Reklam yüklenene kadar bu alan iskelet animasyonuyla bekler,
            // reklam gelince sadece içi dolar, dışarıdaki kartlar hiç oynamaz.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()    // Adaptive banner yüksekliğe göre açılır
                    .clip(RoundedCornerShape(8.dp))
                    .border(BorderStroke(0.5.dp,
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                        RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (!isLoaded) {
                    // Shimmer skeleton — reklam yüklenene kadar gösterilir
                    Box(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                        AdShimmer()
                    }
                }

                // Preloaded view varsa kullan, yoksa factory'den yeni üret
                if (cachedView != null) {
                    AndroidView(
                        factory = {
                            (cachedView.parent as? ViewGroup)?.removeView(cachedView)
                            cachedView
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    // Fallback — preload olmadıysa normal yükle
                    AndroidView(
                        factory = { ctx ->
                            val dm    = ctx.resources.displayMetrics
                            val width = ((dm.widthPixels / dm.density).toInt() - 28) // padding çıkar
                            val size  = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width)
                            AdView(ctx).apply {
                                setAdSize(size)
                                adUnitId = unitId
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                adListener = object : AdListener() {
                                    override fun onAdLoaded() { Log.d("AdBanner", "Yüklendi") }
                                    override fun onAdFailedToLoad(e: LoadAdError) { Log.e("AdBanner", e.message) }
                                }
                                loadAd(
                                    AdRequest.Builder()
                                        .setContentUrl("https://heftreng.app")
                                        .build()
                                )
                            }
                        },
                        update = { adView ->
                            if (adView.adUnitId != unitId) {
                                adView.adUnitId = unitId
                                adView.loadAd(AdRequest.Builder()
                                    .setContentUrl("https://heftreng.app").build())
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title            = { Text("Reklamlar Hakkında") },
            text             = { Text("Heftreng uygulamasını sizlere tamamen ücretsiz sunabilmek ve sunucu giderlerini karşılayabilmek için Google AdMob reklamlarını kullanıyoruz. Anlayışınız ve desteğiniz için teşekkür ederiz.") },
            confirmButton    = { TextButton(onClick = { showDialog = false }) { Text("Anladım") } },
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
