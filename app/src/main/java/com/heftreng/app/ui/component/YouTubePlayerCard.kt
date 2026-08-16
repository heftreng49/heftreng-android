package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.heftreng.app.R

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerCard(
    videoId  : String,
    modifier : Modifier = Modifier,
) {
    val context  = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (!isPlaying) {
            // Thumbnail
            AsyncImage(
                model              = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                contentDescription = "YouTube video",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))

            // Play butonu
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color.Red)
                    .clickable { isPlaying = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_play),
                    contentDescription = "Oynat",
                    tint               = Color.White,
                    modifier           = Modifier.size(30.dp).offset(x = 2.dp),
                )
            }

            // YouTube'da izle butonu
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId")))
                        } catch (_: Exception) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId")))
                        }
                    }
            ) {
                Text("▶ YouTube'da izle", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        } else {
            // YouTube IFrame API — youtube.com üzerinden yükle, hata 152 olmaz
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled             = true
                            domStorageEnabled             = true
                            mediaPlaybackRequiresUserGesture = false
                            loadWithOverviewMode          = true
                            useWideViewPort               = true
                            cacheMode                     = WebSettings.LOAD_NO_CACHE
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient   = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                // YouTube dışı linkleri tarayıcıya aç
                                val url = request.url.toString()
                                return if (!url.contains("youtube") && !url.contains("youtu.be")) {
                                    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    true
                                } else false
                            }
                        }
                        // YouTube IFrame API — youtube.com'dan yükleniyor, origin youtube.com
                        // Bu sayede embed kısıtlaması uygulanmıyor
                        loadUrl("https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1&origin=https://www.youtube.com")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
