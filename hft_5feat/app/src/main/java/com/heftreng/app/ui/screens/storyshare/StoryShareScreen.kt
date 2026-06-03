package com.heftreng.app.ui.screens.storyshare

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.heftreng.app.ui.theme.*

// XML'deki _ssDrawCanvas / _ssRenderPreview / _ssConfirm'e karşılık gelir
// Bir alıntıyı ya da gönderiyi görsel karta dönüştürür

data class StoryTemplate(
    val id      : String,
    val label   : String,
    val bg      : Color,
    val textColor: Color,
)

val storyTemplates = listOf(
    StoryTemplate("dark",    "Karanlık",  Color(0xFF060612), Color(0xFFF0EEFF)),
    StoryTemplate("purple",  "Mor",       Color(0xFF4C1D95), Color(0xFFEDE9FE)),
    StoryTemplate("amber",   "Altın",     Color(0xFF78350F), Color(0xFFFBBF24)),
    StoryTemplate("green",   "Yeşil",     Color(0xFF064E3B), Color(0xFF6EE7B7)),
    StoryTemplate("rose",    "Gül",       Color(0xFF881337), Color(0xFFFDA4AF)),
    StoryTemplate("white",   "Beyaz",     Color(0xFFF5F3FF), Color(0xFF1A1040)),
)

@Composable
fun StoryShareDialog(
    text       : String,
    authorName : String,
    bookName   : String = "",
    onDismiss  : () -> Unit,
    onShare    : (Bitmap) -> Unit,
) {
    var selectedTemplate by remember { mutableStateOf(storyTemplates[0]) }

    val bitmap = remember(text, authorName, selectedTemplate) {
        generateStoryBitmap(text, authorName, bookName, selectedTemplate)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF12102A),
        ) {
            Column(
                modifier            = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Hikaye Paylaş", fontWeight = FontWeight.Bold, color = Color(0xFFF0EEFF), fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))

                // Önizleme
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(selectedTemplate.bg)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "❝",
                            color    = selectedTemplate.textColor.copy(alpha = 0.4f),
                            fontSize = 32.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text.take(180) + if (text.length > 180) "…" else "",
                            color      = selectedTemplate.textColor,
                            fontSize   = 14.sp,
                            lineHeight = 22.sp,
                        )
                        if (authorName.isNotBlank()) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "— $authorName${if (bookName.isNotBlank()) ", $bookName" else ""}",
                                color      = selectedTemplate.textColor.copy(alpha = 0.7f),
                                fontSize   = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "heftreng",
                            color      = selectedTemplate.textColor.copy(alpha = 0.4f),
                            fontSize   = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tema seçimi
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding        = PaddingValues(horizontal = 4.dp),
                ) {
                    items(storyTemplates) { tmpl ->
                        val selected = tmpl.id == selectedTemplate.id
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(tmpl.bg)
                                .clickable { selectedTemplate = tmpl }
                                .then(
                                    if (selected) Modifier.background(
                                        Color.White.copy(alpha = 0.2f),
                                        RoundedCornerShape(8.dp),
                                    ) else Modifier
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Paylaş butonu
                Button(
                    onClick  = { onShare(bitmap) },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Paylaş / Parve Bike", fontWeight = FontWeight.Bold)
                }

                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("İptal", color = Color(0xFF7467A0))
                }
            }
        }
    }
}

// ── Bitmap oluştur — XML'deki _ssDrawCanvas karşılığı ────────────────────────
fun generateStoryBitmap(
    text      : String,
    authorName: String,
    bookName  : String,
    tmpl      : StoryTemplate,
): Bitmap {
    val size = 1080
    val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)

    // Arka plan
    canvas.drawColor(tmpl.bg.toArgb())

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color    = tmpl.textColor.toArgb()
        textSize = 42f
        typeface = Typeface.DEFAULT
    }

    // Tırnak işareti
    paint.textSize = 120f
    paint.alpha    = 80
    canvas.drawText("❝", 80f, 220f, paint)

    // Metin
    paint.textSize = 44f
    paint.alpha    = 255
    val words  = text.split(" ")
    var line   = ""
    var y      = 300f
    for (w in words) {
        val test = "$line $w".trim()
        if (paint.measureText(test) > size - 160f) {
            canvas.drawText(line, 80f, y, paint)
            y += 64f
            line = w
        } else {
            line = test
        }
    }
    if (line.isNotEmpty()) canvas.drawText(line, 80f, y, paint)
    y += 80f

    // Yazar
    if (authorName.isNotBlank()) {
        paint.textSize = 36f
        paint.alpha    = 180
        val attr = "— $authorName${if (bookName.isNotBlank()) ", $bookName" else ""}"
        canvas.drawText(attr, 80f, y + 20f, paint)
    }

    // Watermark
    paint.textSize = 30f
    paint.alpha    = 100
    canvas.drawText("heftreng.blogspot.com", 80f, (size - 60).toFloat(), paint)

    return bmp
}
