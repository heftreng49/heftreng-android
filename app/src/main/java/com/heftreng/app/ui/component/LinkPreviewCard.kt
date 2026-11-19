package com.heftreng.app.ui.component

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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

    // YouTube — mevcut player kartı
    if (type == "youtube" && youtubeId.isNotBlank()) {
        YouTubePlayerCard(videoId = youtubeId, modifier = modifier)
        return
    }

    // Instagram — özel Reels önizleme kartı
    if (type == "instagram") {
        InstagramPreviewCard(
            url      = url,
            title    = title,
            image    = image,
            modifier = modifier,
        )
        return
    }

    // Genel link kartı
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

/** Instagram Reels önizleme kartı — thumbnail + gradient + play ikonu */
@Composable
private fun InstagramPreviewCard(
    url      : String,
    title    : String,
    image    : String,
    modifier : Modifier = Modifier,
) {
    val context = LocalContext.current

    // Instagram renk gradient — mor-turuncu
    val igGradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF833AB4), // mor
            Color(0xFFFD1D1D), // kırmızı
            Color(0xFFF77737), // turuncu
        )
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(9f / 16f)   // Reels dikey format
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
    ) {
        // Thumbnail
        if (image.isNotBlank()) {
            AsyncImage(
                model              = image,
                contentDescription = "Instagram Reels önizleme",
                modifier           = Modifier.fillMaxSize(),
                contentScale       = ContentScale.Crop,
            )
        } else {
            // Thumbnail yoksa gradient arka plan
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(igGradient)
            )
        }

        // Alt gradient — başlık okunabilsin diye
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
        )

        // Instagram logosu — sağ üst
        Box(
            modifier = Modifier
                .padding(10.dp)
                .align(Alignment.TopEnd)
                .size(32.dp)
                .background(Color.Black.copy(0.4f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // "IG" metin ikonu — harici SVG gerektirmez
            Text(
                text       = "IG",
                fontSize   = 11.sp,
                fontWeight = FontWeight.Bold,
                color      = Color.White,
            )
        }

        // Ortada play ikonu
        Box(
            modifier = Modifier
                .size(56.dp)
                .align(Alignment.Center)
                .background(Color.Black.copy(0.45f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter            = painterResource(R.drawable.ic_play),
                contentDescription = "Oynat",
                tint               = Color.White,
                modifier           = Modifier.size(26.dp).offset(x = 2.dp),
            )
        }

        // Alt — başlık + "Reels" etiketi
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
        ) {
            Text(
                text       = "Reels",
                fontSize   = 11.sp,
                color      = Color.White.copy(0.75f),
                fontWeight = FontWeight.Medium,
            )
            if (title.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text     = title,
                    fontSize = 13.sp,
                    color    = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
