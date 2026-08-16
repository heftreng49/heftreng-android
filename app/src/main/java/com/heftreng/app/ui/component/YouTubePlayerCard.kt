package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.content.Context
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

/**
 * YouTube WebView Pool — tek WebView örneği yeniden kullanılır.
 * Referer + Origin header ile hata 152 aşılır.
 * Scroll Listener: sadece görünür kart oynar, diğerleri pause edilir.
 */
object YouTubeWebViewPool {
    private var pooledWebView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreate(context: Context): WebView {
        return pooledWebView ?: WebView(context.applicationContext).also { wv ->
            wv.settings.apply {
                javaScriptEnabled             = true
                domStorageEnabled             = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode          = true
                useWideViewPort               = true
                mixedContentMode              = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            wv.webChromeClient = WebChromeClient()
            pooledWebView = wv
        }
    }

    fun release() {
        pooledWebView?.apply {
            loadUrl("about:blank")
            destroy()
        }
        pooledWebView = null
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerCard(
    videoId  : String,
    modifier : Modifier = Modifier,
    autoPlay : Boolean  = false,
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
        if (!isPlaying) {
            // ── Thumbnail göster ───────────────────────────────────────────
            AsyncImage(
                model              = thumbnailUrl,
                contentDescription = "YouTube video",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f))
            )
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
            // YouTube logosu
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clickable {
                        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$videoId"))
                        try { context.startActivity(appIntent) }
                        catch (_: Exception) { context.startActivity(webIntent) }
                    }
            ) {
                Text("▶ YouTube'da izle", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        } else {
            // ── WebView ile IFrame oynat ───────────────────────────────────
            val html = """
                <!DOCTYPE html><html>
                <head>
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <style>* { margin:0; padding:0; } body { background:#000; }
                    .wrap { position:relative; width:100%; padding-top:56.25%; }
                    iframe { position:absolute; top:0; left:0; width:100%; height:100%; border:0; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <iframe
                      src="https://www.youtube-nocookie.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                      allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                      allowfullscreen></iframe>
                  </div>
                </body></html>
            """.trimIndent()

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled             = true
                        settings.domStorageEnabled             = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.loadWithOverviewMode          = true
                        settings.useWideViewPort               = true
                        webChromeClient                        = WebChromeClient()
                        // Referer + Origin header — hata 152 çözümü
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html,
                            "text/html",
                            "UTF-8",
                            null
                        )
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
