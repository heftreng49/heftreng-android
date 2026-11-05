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
import androidx.compose.material3.*
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
    if (videoId.isBlank()) return

    val context    = LocalContext.current
    var showPlayer by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (showPlayer) {
            InlineYouTubePlayer(
                videoId  = videoId,
                modifier = Modifier.fillMaxSize(),
                onFallback = {
                    showPlayer = false
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                                .apply { setPackage("com.google.android.youtube") }
                        )
                    } catch (_: Exception) {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId"))
                        )
                    }
                },
            )
        } else {
            AsyncImage(
                model              = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                contentDescription = "YouTube video",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .align(Alignment.Center)
                    .clip(CircleShape)
                    .background(Color(0xFFCC0000))
                    .clickable { showPlayer = true },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter            = painterResource(R.drawable.ic_play),
                    contentDescription = "Oynat",
                    tint               = Color.White,
                    modifier           = Modifier.size(28.dp).offset(x = 2.dp),
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                                    .apply { setPackage("com.google.android.youtube") }
                            )
                        } catch (_: Exception) {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId"))
                            )
                        }
                    }
            ) {
                Text("▶ YouTube", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun InlineYouTubePlayer(
    videoId    : String,
    modifier   : Modifier = Modifier,
    onFallback : () -> Unit,
) {
    // youtube-nocookie.com embed — daha az kısıtlama, aynı player
    val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId" +
        "?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=0"

    AndroidView(
        modifier = modifier,
        factory  = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                settings.apply {
                    javaScriptEnabled                = true
                    domStorageEnabled                = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode             = true
                    useWideViewPort                  = true
                    cacheMode                        = WebSettings.LOAD_NO_CACHE
                    mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    // Chrome UA — zorunlu, aksi hâlde YouTube blokluyor
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 12; Pixel 6) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0.6367.82 Mobile Safari/537.36"
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest,
                    ): Boolean {
                        val url = request.url.toString()
                        // embed izni yoksa "watch" URL'sine yönlendiriyor — yakala ve YouTube'a aç
                        if (url.contains("youtube.com/watch") || url.contains("youtu.be/")) {
                            onFallback()
                            return true
                        }
                        val isMedia = url.contains("youtube") || url.contains("googlevideo") ||
                                      url.contains("ytimg") || url.contains("google.com")
                        return if (!isMedia) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            catch (_: Exception) {}
                            true
                        } else false
                    }

                    override fun onReceivedHttpError(
                        view: WebView,
                        request: WebResourceRequest,
                        errorResponse: WebResourceResponse,
                    ) {
                        // 403 = embed yasak → fallback
                        if (request.isForMainFrame && errorResponse.statusCode == 403) {
                            onFallback()
                        }
                    }
                }
                loadUrl(embedUrl)
            }
        },
    )
}
