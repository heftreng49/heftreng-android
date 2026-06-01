package com.heftreng.app.ui.component

import android.util.Log
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError

@Composable
fun AdBannerView(
    unitId: String?,
    modifier: Modifier = Modifier,
    isMediumRectangle: Boolean = true // Varsayılan olarak true yaparak tam görseldeki boyutu hedefliyoruz
) {
    if (unitId.isNullOrBlank()) return

    val context = LocalContext.current
    // 1000Kitap'taki büyük görselli reklam için AdSize.MEDIUM_RECTANGLE şarttır.
    val selectedAdSize = AdSize.MEDIUM_RECTANGLE
    val requiredHeight = 250.dp // Reklamın kendi resmi ve butonu için gereken net yükseklik

    var menuExpanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp), // Akıştaki diğer postlarla tam aynı hizada dış boşluk
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) // Hafif mat, şık arka plan
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 12.dp), // Üst bilgi satırı ve alt kısım için kompakt iç boşluk
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Üst Bilgi Satırı (Reklam İkonu, Yazısı ve Üç Nokta Menüsü)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Announcement,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Reklam",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        style = androidx.compose.ui.text.TextStyle(letterSpacing = 0.4.sp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menü",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Bu reklam neden görünüyor?") },
                            onClick = {
                                menuExpanded = false
                                showDialog = true
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. Reklam Alanı (Görseldeki gibi kenarları yumuşatılmış ve tam oturan kalıp)
            Box(
                modifier = Modifier
                    .width(300.dp) // Medium Rectangle reklamların standart genişliği 300dp'dir
                    .height(requiredHeight)
                    .clip(RoundedCornerShape(8.dp)) 
                    .border(
                        BorderStroke(0.5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                        RoundedCornerShape(8.dp)
                    )
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    modifier = Modifier.wrapContentSize(),
                    factory = { ctx ->
                        AdView(ctx).apply {
                            setAdSize(selectedAdSize)
                            adUnitId = unitId
                            setBackgroundColor(android.graphics.Color.TRANSPARENT) 
                            adListener = object : AdListener() {
                                override fun onAdLoaded() { Log.d("AdBanner", "Medium Rectangle Yüklendi") }
                                override fun onAdFailedToLoad(error: LoadAdError) { Log.e("AdBanner", error.message) }
                            }
                            loadAd(AdRequest.Builder().build())
                        }
                    },
                    update = { adView ->
                        if (adView.adUnitId != unitId || adView.adSize != selectedAdSize) {
                            adView.adUnitId = unitId
                            adView.setAdSize(selectedAdSize)
                            adView.loadAd(AdRequest.Builder().build())
                        }
                    }
                )
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = "Reklamlar Hakkında") },
            text = { Text(text = "Heftreng uygulamasını sizlere tamamen ücretsiz sunabilmek ve sunucu giderlerini karşılayabilmek için Google AdMob reklamlarını kullanıyoruz. Anlayışınız ve desteğiniz için teşekkür ederiz.") },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(text = "Anladım")
                }
            }
        )
    }
}
