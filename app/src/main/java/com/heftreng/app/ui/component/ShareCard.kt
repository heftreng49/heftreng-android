package com.heftreng.app.ui.component

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*
import com.heftreng.app.utils.ShareTarget
import com.heftreng.app.utils.captureComposable
import com.heftreng.app.utils.shareBitmap

fun sharePost(context: Context, post: Post, target: ShareTarget) {
    captureComposable(
        context  = context,
        content  = { ShareCardContent(post = post) },
        onBitmap = { bitmap -> shareBitmap(context, bitmap, target) },
    )
}

// captureComposable için lambda syntax düzeltmesi
fun captureAndShare(context: Context, post: Post, target: ShareTarget) {
    com.heftreng.app.utils.captureComposable(
        context  = context,
        widthPx  = 1080,
        content  = { ShareCardContent(post = post) },
        onBitmap = { bitmap -> shareBitmap(context, bitmap, target) },
    )
}

@Composable
fun ShareCardContent(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E1A))
            .padding(20.dp),
    ) {
        // Kullanıcı bilgisi
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Primary, Accent))),
                contentAlignment = Alignment.Center,
            ) {
                if (post.photoURL.isNotBlank()) {
                    AsyncImage(
                        model = post.photoURL, contentDescription = null,
                        modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        post.displayName.firstOrNull()?.uppercase() ?: "?",
                        color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column {
                Text(post.displayName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (post.username.isNotBlank())
                    Text("@${post.username}", color = Color(0xFF888899), fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Alıntı
        if (post.quoteText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A2E))
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "\u201C${post.quoteText}\u201D",
                        color = Color(0xFFE8E8F0), fontSize = 15.sp,
                        lineHeight = 22.sp, fontStyle = FontStyle.Italic,
                    )
                    if (post.bookName.isNotBlank()) {
                        Text(
                            "📖 ${post.bookName}" +
                                if (post.authorName.isNotBlank()) " — ${post.authorName}" else "",
                            color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Metin
        if (post.text.isNotBlank()) {
            Text(post.text, color = Color(0xFFE0E0F0), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
        }

        // Görsel
        val img = post.imgUrl.ifBlank { post.imageURL }
        if (img.isNotBlank()) {
            AsyncImage(
                model = img, contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
        }

        // Heftreng branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "heftreng.com",
                color = Color(0xFFFFB300), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
