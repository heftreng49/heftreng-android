package com.heftreng.app.ui.component

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import android.graphics.Color
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedCard(videoId: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(HeftSurface)
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    // Hardware acceleration — siyah ekranı önler
                    setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                    setBackgroundColor(Color.TRANSPARENT)

                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        domStorageEnabled = true
                        allowFileAccess = false
                        cacheMode = WebSettings.LOAD_DEFAULT
                    }

                    webChromeClient = WebChromeClient()
                    webViewClient  = WebViewClient()

                    // HTML içinde iframe — en güvenilir yöntem
                    // origin parametresi yok, embed kısıtlamasını tetiklemiyor
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                          <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
                          <style>
                            html,body{margin:0;padding:0;background:#000;width:100%;height:100%;}
                            iframe{width:100%;height:100%;border:none;display:block;}
                          </style>
                        </head>
                        <body>
                          <iframe
                            src="https://www.youtube.com/embed/$videoId?playsinline=1&rel=0&autoplay=0&enablejsapi=1"
                            allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                            allowfullscreen>
                          </iframe>
                        </body>
                        </html>
                    """.trimIndent()

                    // baseUrl null — WebView kendi origin'ini kullanır, kısıtlama yok
                    loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LinkPreviewCard(
    url       : String,
    title     : String,
    desc      : String,
    image     : String,
    type      : String,
    youtubeId : String = "",
    modifier  : Modifier = Modifier,
) {
    val context = LocalContext.current

    if (type == "youtube" && youtubeId.isNotBlank()) {
        YouTubeEmbedCard(videoId = youtubeId, modifier = modifier)
        return
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, Divider, RoundedCornerShape(12.dp))
            .background(HeftSurface)
            .clickable {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                )
            }
    ) {
        Column {
            if (image.isNotBlank()) {
                AsyncImage(
                    model              = image,
                    contentDescription = null,
                    modifier           = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale       = ContentScale.Crop,
                )
            }
            Column(Modifier.padding(12.dp)) {
                if (type == "instagram") {
                    Text(
                        "📷 Instagram Reels",
                        fontSize   = 11.sp,
                        color      = Muted,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                if (title.isNotBlank()) {
                    Text(
                        title,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color      = OnBackground,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }
                if (desc.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        desc,
                        fontSize = 12.sp,
                        color    = Muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    Uri.parse(url).host ?: url,
                    fontSize = 11.sp,
                    color    = Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
