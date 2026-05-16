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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
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

                Button(
                    onClick = {
                        if (capturing) return@Button
                        capturing = true
                        scope.launch {
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
    val isDark = LocalHeftrangColors.current.isDark

    // Moda göre arka plan ve metin renkleri
    val cardBg      = if (isDark) Color(0xFF0E0E1A) else Color(0xFFF5F3FF)
    val textColor   = if (isDark) Color(0xFFE0E0F0) else Color(0xFF1A1040)
    val quoteBoxBg  = if (isDark) Color(0xFF1A1A2E) else Color(0xFFEDE9FE)
    val quoteText   = if (isDark) Color(0xFFE8E8F0) else Color(0xFF2D2060)
    val brandingBg  = if (isDark) Color(0xFF1A1A2E) else Color(0xFFEDE9FE)
    val mutedColor  = if (isDark) Color(0xFF888899) else Color(0xFF8878B8)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(cardBg)
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
                Text(post.displayName, color = textColor, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (post.username.isNotBlank())
                    Text("@${post.username}", color = mutedColor, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        // Alıntı
        if (post.quoteText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(quoteBoxBg)
                    .border(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "\u201C${post.quoteText}\u201D",
                        color      = quoteText,
                        fontSize   = 15.sp,
                        lineHeight = 22.sp,
                        fontStyle  = FontStyle.Italic,
                    )
                    if (post.bookName.isNotBlank()) {
                        Text(
                            "📖 ${post.bookName}" +
                                if (post.authorName.isNotBlank()) " — ${post.authorName}" else "",
                            color      = Color(0xFFFFB300),
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Metin
        if (post.text.isNotBlank()) {
            Text(post.text, color = textColor, fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
        }

        // Görsel
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

        // Branding — Play Store ikonu + "Heft Reng"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(brandingBg)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Row(
                modifier          = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlayStoreIcon(size = 14.dp)
                Text(
                    "Heft Reng",
                    color      = Color(0xFFFFB300),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── Play Store ikonu (SVG path, bağımlılık yok) ───────────────────────────────
@Composable
fun PlayStoreIcon(size: androidx.compose.ui.unit.Dp = 16.dp) {
    androidx.compose.foundation.Canvas(
        modifier = Modifier.size(size),
    ) {
        val w = this.size.width
        val h = this.size.height

        // Play Store'un 4 renkli ok/ok ikonunu basit olarak çiz
        // Sol üst — yeşil
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.5f, h * 0.5f)
                lineTo(0f, h * 0.55f)
                close()
            },
            color = Color(0xFF00C853),
        )
        // Sağ üst — sarı/turuncu
        drawPath(
            path = Path().apply {
                moveTo(0f, 0f)
                lineTo(w * 0.5f, h * 0.5f)
                lineTo(w * 0.85f, h * 0.3f)
                close()
            },
            color = Color(0xFFFFD600),
        )
        // Sol alt — mavi
        drawPath(
            path = Path().apply {
                moveTo(0f, h * 0.55f)
                lineTo(w * 0.5f, h * 0.5f)
                lineTo(0f, h)
                close()
            },
            color = Color(0xFF2979FF),
        )
        // Sağ alt — kırmızı
        drawPath(
            path = Path().apply {
                moveTo(w * 0.5f, h * 0.5f)
                lineTo(w * 0.85f, h * 0.3f)
                lineTo(w * 0.85f, h * 0.7f)
                close()
            },
            color = Color(0xFFFF3D00),
        )
        // Tam sağ uç
        drawPath(
            path = Path().apply {
                moveTo(w * 0.5f, h * 0.5f)
                lineTo(w * 0.85f, h * 0.7f)
                lineTo(0f, h)
                close()
            },
            color = Color(0xFFFF3D00).copy(alpha = 0.7f),
        )
    }
}
