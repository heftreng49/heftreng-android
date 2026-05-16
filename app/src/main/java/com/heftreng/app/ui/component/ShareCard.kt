package com.heftreng.app.ui.component

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.heftreng.app.data.model.Post
import com.heftreng.app.ui.theme.*
import com.heftreng.app.utils.ShareTarget
import com.heftreng.app.utils.shareBitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Paylaşım önizleme dialogu ─────────────────────────────────────────────────
// Kullanıcı hedef seçince bu dialog açılır.
// Kart görünür render edilir (Coil yükler), "Paylaş" butonuna basılınca bitmap alınır.
@Composable
fun SharePreviewDialog(
    post     : Post,
    target   : ShareTarget,
    onDismiss: () -> Unit,
) {
    val context       = LocalContext.current
    val scope         = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var capturing     by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape          = RoundedCornerShape(20.dp),
            color          = HeftSurface,
            tonalElevation = 0.dp,
            modifier       = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // Başlık
                Row(
                    modifier          = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Paylaşım Önizlemesi",
                        color      = OnBackground,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        modifier   = Modifier.weight(1f),
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, null, tint = Muted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Kart — görünür render, graphicsLayer kayıt altında
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .drawWithContent {
                            graphicsLayer.record { this@drawWithContent.drawContent() }
                            drawLayer(graphicsLayer)
                        },
                ) {
                    ShareCardContent(post = post)
                }

                Spacer(Modifier.height(16.dp))

                // Paylaş butonu
                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        scope.launch {
                            // Coil son frame'i çizsin diye kısa bekle
                            delay(120)
                            val bmp: Bitmap = graphicsLayer
                                .toImageBitmap()
                                .asAndroidBitmap()
                                .copy(Bitmap.Config.ARGB_8888, false)
                            shareBitmap(context, bmp, target)
                            onDismiss()
                        }
                    },
                    enabled  = !capturing,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = Amber,
                        contentColor   = Color.Black,
                    ),
                ) {
                    if (capturing) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(18.dp),
                            color       = Color.Black,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            when (target) {
                                ShareTarget.WHATSAPP  -> "WhatsApp'ta Paylaş"
                                ShareTarget.INSTAGRAM -> "Instagram'da Paylaş"
                                ShareTarget.ANY       -> "Paylaş"
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── Paylaşım kartı içeriği ────────────────────────────────────────────────────
@Composable
fun ShareCardContent(post: Post) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0E0E1A))
            .padding(20.dp),
    ) {
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
                        model              = post.photoURL,
                        contentDescription = null,
                        modifier           = Modifier.fillMaxSize(),
                        contentScale       = ContentScale.Crop,
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

        if (post.text.isNotBlank()) {
            Text(post.text, color = Color(0xFFE0E0F0), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
        }

        val img = post.imgUrl.ifBlank { post.imageURL }
        if (img.isNotBlank()) {
            AsyncImage(
                model              = img,
                contentDescription = null,
                modifier           = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale       = ContentScale.Crop,
            )
            Spacer(Modifier.height(10.dp))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1A1A2E))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "heftreng.com",
                color      = Color(0xFFFFB300),
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier   = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}
