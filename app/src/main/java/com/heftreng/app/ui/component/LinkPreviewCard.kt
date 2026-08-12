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
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeEmbedCard(videoId: String, modifier: Modifier = Modifier) {
    // YouTube embed URL'ini direkt WebView'a yükle.
    // loadDataWithBaseURL ile farklı origin belirtmek 152-4 hatasına yol açıyor.
    // Direkt loadUrl kullanınca WebView kendi origin'i geçerli olur, YouTube izin verir.
    val embedUrl = "https://www.youtube-nocookie.com/embed/$videoId" +
        "?playsinline=1&rel=0&autoplay=0"

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
                    settings.javaScriptEnabled            = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode         = true
                    settings.useWideViewPort              = true
                    settings.domStorageEnabled            = true
                    webChromeClient                       = WebChromeClient()
                    loadUrl(embedUrl)
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

    // YouTube — WebView embed
    if (type == "youtube" && youtubeId.isNotBlank()) {
        YouTubeEmbedCard(videoId = youtubeId, modifier = modifier)
        return
    }

    // Instagram + genel link — önizleme kartı
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
                // Instagram / Reels için ikon + etiket
                if (type == "instagram") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📷 ", fontSize = 13.sp)
                        Text(
                            "Instagram Reels",
                            fontSize   = 11.sp,
                            color      = Muted,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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
