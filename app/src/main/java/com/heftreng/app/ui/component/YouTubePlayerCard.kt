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
            // ── Inline WebView oynatıcı ──────────────────────────────────────
            InlineYouTubePlayer(
                videoId = videoId,
                modifier = Modifier.fillMaxSize(),
                onError = {
                    // embed izni yoksa YouTube uygulamasına aç
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
            // ── Thumbnail ────────────────────────────────────────────────────
            AsyncImage(
                model              = "https://img.youtube.com/vi/$videoId/hqdefault.jpg",
                contentDescription = "YouTube video",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.25f)))

            // Oynat butonu
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

            // YouTube'da izle etiketi
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
    videoId  : String,
    modifier : Modifier = Modifier,
    onError  : () -> Unit,
) {
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0">
        <style>
          * { margin:0; padding:0; box-sizing:border-box; background:#000; }
          html, body { width:100%; height:100%; overflow:hidden; }
          #player { width:100%; height:100%; }
        </style>
        </head>
        <body>
        <div id="player"></div>
        <script>
          var tag = document.createElement('script');
          tag.src = "https://www.youtube.com/iframe_api";
          document.head.appendChild(tag);
          function onYouTubeIframeAPIReady() {
            new YT.Player('player', {
              videoId: '$videoId',
              playerVars: {
                autoplay: 1,
                playsinline: 1,
                rel: 0,
                modestbranding: 1,
                origin: 'https://www.youtube.com'
              },
              events: {
                onReady: function(e) { e.target.playVideo(); },
                onError: function(e) {
                  if (e.data === 100 || e.data === 101 || e.data === 150) {
                    Android.onEmbedError();
                  }
                }
              }
            });
          }
        </script>
        </body>
        </html>
    """.trimIndent()

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
                    cacheMode                        = WebSettings.LOAD_DEFAULT
                    mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun onEmbedError() { onError() }
                }, "Android")
                webChromeClient = WebChromeClient()
                webViewClient   = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView, request: WebResourceRequest
                    ): Boolean {
                        val url = request.url.toString()
                        val isYt = url.contains("youtube.com") || url.contains("youtu.be") ||
                                   url.contains("googlevideo.com") || url.contains("ytimg.com")
                        return if (!isYt) {
                            try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            catch (_: Exception) {}
                            true
                        } else false
                    }
                }
                loadDataWithBaseURL(
                    "https://www.youtube.com", html, "text/html", "utf-8", null
                )
            }
        },
    )
}
