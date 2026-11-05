package com.heftreng.app.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubePlayerCard(
    videoId  : String,
    modifier : Modifier = Modifier,
) {
    if (videoId.isBlank()) return

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context   = LocalContext.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory  = { ctx ->
                YouTubePlayerView(ctx).apply {
                    // Lifecycle'a bağla — bellek sızıntısı olmaz
                    lifecycle.addObserver(this)

                    addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                        override fun onReady(youTubePlayer: YouTubePlayer) {
                            // cueVideo: yükler ama otomatik başlatmaz, kullanıcı basınca oynar
                            youTubePlayer.cueVideo(videoId, 0f)
                        }

                        override fun onError(
                            youTubePlayer: YouTubePlayer,
                            error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError,
                        ) {
                            // Oynatılamayan videolarda YouTube uygulamasına aç
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                                        .apply { setPackage("com.google.android.youtube") }
                                )
                            } catch (_: Exception) {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse("https://youtu.be/$videoId"))
                                )
                            }
                        }
                    })
                }
            },
            onRelease = { view ->
                view.release()
            },
        )
    }
}
