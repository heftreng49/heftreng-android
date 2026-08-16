package com.heftreng.app.ui.component

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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.heftreng.app.ui.theme.*
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * YouTube videosunu uygulama içinde oynatır.
 * android-youtube-player (PierfrancescoSoffritti) — IFrame Player API,
 * resmi API key gerektirmez.
 */
@Composable
fun YouTubeEmbedCard(videoId: String, modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(androidx.compose.ui.graphics.Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // Lifecycle'a bağla — ekrandan çıkınca durdurur, sızdırmaz
                    lifecycleOwner.lifecycle.addObserver(this)

                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            // cueVideo → otomatik başlatmaz, kullanıcı play'e basar
                            // loadVideo → otomatik başlatır
                            youTubePlayer.cueVideo(videoId, 0f)
                        }
                    })
                }
            },
            update = { view ->
                // videoId değişirse yeni videoyu yükle
                view.addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                })
            }
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
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
    ) {
        Column {
            if (image.isNotBlank()) {
                AsyncImage(
                    model              = image,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxWidth().height(180.dp),
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
