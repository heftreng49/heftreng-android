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
    language : String = "tr",
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
                        if (language == "ku") "Pêşdîtina Parvekirinê" else "Paylaşım Önizlemesi",
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
                    ShareCardContent(post = post, shareLanguage = language)
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
                                ShareTarget.WHATSAPP  -> if (language == "ku") "Li WhatsApp Parve Bike" else "WhatsApp'ta Paylaş"
                                ShareTarget.INSTAGRAM -> if (language == "ku") "Li Instagram Parve Bike" else "Instagram'da Paylaş"
                                ShareTarget.ANY       -> if (language == "ku") "Parve Bike" else "Paylaş"
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
fun ShareCardContent(post: Post, shareLanguage: String = "tr") {
    val colors = LocalHeftrangColors.current

    // Tema renkleriyle uyumlu — hardcoded eski mor tema renkleri kaldırıldı
    val cardBg     = colors.background
    val textColor  = colors.onBackground
    val quoteBoxBg = colors.surfaceVar
    val quoteText  = colors.onBackground
    val brandingBg = colors.surfaceVar
    val mutedColor = colors.muted

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

        // Kurdî başarı kartı — seviye/XP/streak, feed'deki gradient tasarımla aynı
        if (post.repostType == "kf_achievement") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF5A623), Color(0xFFE8871E), Color(0xFFD9691B)),
                        ),
                    )
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("🏆", fontSize = 30.sp)
                        Text(
                            com.heftreng.app.ui.i18n.Strings.achievementLevelLabel(shareLanguage, post.repostLevel),
                            color      = Color.White,
                            fontSize   = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column {
                            Text("${post.repostXp}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(com.heftreng.app.ui.i18n.Strings.xpLabel(shareLanguage), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                        Column {
                            Text("${post.repostStreak}", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            Text(com.heftreng.app.ui.i18n.Strings.streakDaysLabel(shareLanguage), color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                    Text(
                        com.heftreng.app.ui.i18n.Strings.achievementCaption(shareLanguage),
                        color      = Color.White.copy(alpha = 0.9f),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Kurdî ders kartı — tamamlanan dersi paylaşırken
        if (post.repostType == "kf_lesson") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Primary, Accent),
                        ),
                    )
                    .padding(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(post.repostTitle, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    if (post.repostText.isNotBlank()) {
                        Text(post.repostText, color = Color.White.copy(alpha = 0.9f), fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Text(
                        if (shareLanguage == "ku") "Dersek li Heft Reng qedand!" else "Heft Reng'de bir ders tamamladı!",
                        color      = Color.White.copy(alpha = 0.85f),
                        fontSize   = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // Alıntı
        if (post.quoteText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(quoteBoxBg)
                    .border(1.dp, Amber.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
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
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Kitap kapak avatarı — varsa göster
                            if (post.coverImg.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .width(28.dp)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(quoteBoxBg),
                                ) {
                                    AsyncImage(
                                        model              = post.coverImg,
                                        contentDescription = post.bookName,
                                        contentScale       = ContentScale.Crop,
                                        modifier           = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Column {
                                Text(
                                    post.bookName,
                                    color      = Amber,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (post.authorName.isNotBlank())
                                    Text(
                                        post.authorName,
                                        color    = mutedColor,
                                        fontSize = 11.sp,
                                    )
                            }
                        }
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
                    "Heft Reng Kurdî",
                    color      = Amber,
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
