package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.view.View
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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

    // ── Thumbnail kartı ──────────────────────────────────────────────────────
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
    ) {
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

        // YouTube'da izle — fallback
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

    // ── Tam ekran oynatıcı ───────────────────────────────────────────────────
    if (showPlayer) {
        YoutubeFullscreenPlayer(
            videoId = videoId,
            onClose = { showPlayer = false },
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun YoutubeFullscreenPlayer(
    videoId : String,
    onClose : () -> Unit,
) {
    var customView     by remember { mutableStateOf<View?>(null) }
    var customCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    BackHandler {
        if (customView != null) {
            customCallback?.onCustomViewHidden()
            customView     = null
            customCallback = null
        } else {
            onClose()
        }
    }

    // loadDataWithBaseURL kullanıyoruz — baseUrl=https://www.youtube.com
    // olunca tarayıcı origin'i YouTube olarak görüyor, Hata 153 ortadan kalkıyor
    val html = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1">
            <style>
                * { margin:0; padding:0; box-sizing:border-box; }
                body { background:#000; width:100vw; height:100vh; overflow:hidden; }
                iframe { width:100%; height:100%; border:none; display:block; }
            </style>
        </head>
        <body>
            <iframe
                src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                allow="autoplay; fullscreen; encrypted-media"
                allowfullscreen>
            </iframe>
        </body>
        </html>
    """.trimIndent()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside   = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .background(Color.Black)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
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
                            allowContentAccess               = true
                            mixedContentMode                 = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(
                                view: View, callback: CustomViewCallback
                            ) {
                                customView     = view
                                customCallback = callback
                            }
                            override fun onHideCustomView() {
                                customCallback?.onCustomViewHidden()
                                customView     = null
                                customCallback = null
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView, request: WebResourceRequest
                            ): Boolean {
                                val url = request.url.toString()
                                val isYt = url.contains("youtube.com") ||
                                           url.contains("youtu.be") ||
                                           url.contains("google.com") ||
                                           url.contains("ytimg.com")
                                return if (!isYt) {
                                    try {
                                        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    } catch (_: Exception) {}
                                    true
                                } else false
                            }
                        }
                        // loadUrl yerine loadDataWithBaseURL — origin sorunu bu şekilde çözülüyor
                        loadDataWithBaseURL(
                            "https://www.youtube.com",
                            html,
                            "text/html",
                            "utf-8",
                            null,
                        )
                    }
                },
            )

            // WebChromeClient custom view (tam ekran)
            customView?.let { cv ->
                AndroidView(
                    factory  = { cv },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Kapat butonu
            IconButton(
                onClick  = onClose,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(0.6f), CircleShape),
            ) {
                Icon(Icons.Default.Close, "Kapat", tint = Color.White)
            }
        }
    }
}
