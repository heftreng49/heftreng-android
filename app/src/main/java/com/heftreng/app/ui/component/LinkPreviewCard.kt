package com.heftreng.app.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.R
import com.heftreng.app.ui.theme.*

/**
 * YouTube: thumbnail + play butonu göster, tıklayınca YouTube'a yönlendir.
 * WebView embed YouTube tarafından engelleniyor (hata 152-4) — bu yüzden
 * in-app oynatma yerine dışa yönlendirme kullanıyoruz.
 */
@Composable
fun YouTubeEmbedCard(videoId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
    val youtubeUrl   = "https://www.youtube.com/watch?v=$videoId"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                // Önce YouTube uygulamasını dene, yoksa tarayıcıya aç
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(youtubeUrl))
                try {
                    context.startActivity(appIntent)
                } catch (e: Exception) {
                    context.startActivity(webIntent)
                }
            }
    ) {
        // Thumbnail
        AsyncImage(
            model              = thumbnailUrl,
            contentDescription = "YouTube video",
            modifier           = Modifier.fillMaxSize(),
            contentScale       = ContentScale.Crop,
        )

        // Karartma overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f))
        )

        // Play butonu ortada
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .background(Color.Red, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter           = painterResource(R.drawable.ic_play),
                contentDescription = "Oynat",
                tint              = Color.White,
                modifier          = Modifier.size(28.dp).offset(x = 2.dp),
            )
        }

        // YouTube logosu sağ alt
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                "▶ YouTube'da izle",
                fontSize  = 10.sp,
                color     = Color.White,
                fontWeight = FontWeight.Medium,
            )
        }
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
