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
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

@Composable
fun YouTubePlayerCard(
    videoId  : String,
    modifier : Modifier = Modifier,
) {
    if (videoId.isBlank()) return

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context   = LocalContext.current

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
        factory  = { ctx ->
            YouTubePlayerView(ctx).apply {
                enableAutomaticInitialization = false

                val options = IFramePlayerOptions.Builder()
                    .controls(1)
                    .rel(0)
                    .build()

                lifecycle.addObserver(this)

                initialize(object : AbstractYouTubePlayerListener() {
                    override fun onReady(youTubePlayer: YouTubePlayer) {
                        youTubePlayer.cueVideo(videoId, 0f)
                    }
                    override fun onError(
                        youTubePlayer: YouTubePlayer,
                        error: com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants.PlayerError,
                    ) {
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
                }, options)
            }
        },
        onRelease = { it.release() },
    )
}
