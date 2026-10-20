package com.heftreng.app.ui.screens.dailyword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.heftreng.app.R
import com.heftreng.app.ui.theme.Background
import com.heftreng.app.ui.theme.Primary
import com.heftreng.app.viewmodel.DailyWordViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyWordScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : DailyWordViewModel = hiltViewModel(),
) {
    val words   by vm.words.collectAsState()
    val loading by vm.loading.collectAsState()
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (language == "ku") "Peyvên Rojê" else "Günün Kelimeleri",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 17.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor             = Background,
                    titleContentColor          = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = Background,
    ) { padding ->
        Box(
            modifier          = Modifier.fillMaxSize().padding(padding),
            contentAlignment  = Alignment.Center,
        ) {
            when {
                loading      -> CircularProgressIndicator(color = Primary)
                words.isEmpty() -> Text(
                    if (language == "ku") "Îro peyv nehat zêdekirin"
                    else "Bugün henüz kelime eklenmedi",
                    color    = Color.White.copy(alpha = 0.5f),
                    fontSize = 15.sp,
                )
                else -> {
                    Column(
                        modifier            = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(Modifier.height(16.dp))

                        // ── Kelime listesi kartı ──────────────────────────────
                        WordListCard(
                            words    = words,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                        )

                        Spacer(Modifier.height(20.dp))

                        // ── Paylaş butonu ─────────────────────────────────────
                        Button(
                            onClick = {
                                scope.launch {
                                    shareWordListCard(context = context, words = words)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .height(52.dp),
                            shape  = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor   = Color.White,
                            ),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (language == "ku") "Parve Bike" else "Paylaş",
                                fontWeight = FontWeight.Bold,
                                fontSize   = 15.sp,
                            )
                        }

                        Spacer(Modifier.height(28.dp))
                    }
                }
            }
        }
    }
}

// ── Kelime listesi kartı (ekranda gösterilen) ─────────────────────────────────
@Composable
fun WordListCard(
    words    : List<DailyWordViewModel.WordItem>,
    modifier : Modifier = Modifier,
) {
    // Uygulama renkleriyle uyumlu koyu kart
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
        border   = androidx.compose.foundation.BorderStroke(
            1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFF1A1630), Color(0xFF0F0C1E))
                    )
                )
        ) {
            // Arka plan glow efekti
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .offset(x = 120.dp, y = (-40).dp)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                Color.Transparent,
                            )
                        ),
                        RoundedCornerShape(50)
                    )
            )

            Column(modifier = Modifier.fillMaxWidth()) {

                // ── Başlık satırı ─────────────────────────────────
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Uygulama ikonu
                    Image(
                        painter  = painterResource(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(7.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Peyvên Rojê · Heftreng",
                        color      = Primary,
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        java.text.SimpleDateFormat("d MMM", java.util.Locale("tr"))
                            .format(java.util.Date()),
                        color    = Color.White.copy(alpha = 0.28f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                HorizontalDivider(color = Color(0xFF8B5CF6).copy(alpha = 0.18f))

                // ── Kelime satırları ──────────────────────────────
                words.forEachIndexed { i, item ->
                    WordRow(
                        item     = item,
                        index    = i,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (i < words.lastIndex) {
                        HorizontalDivider(
                            modifier  = Modifier.padding(horizontal = 18.dp),
                            color     = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF8B5CF6).copy(alpha = 0.18f))

                // ── Footer ────────────────────────────────────────
                Row(
                    modifier          = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Image(
                        painter  = painterResource(R.drawable.ic_app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(6.dp))
                    // Google Play renk ikonu (basit üçgen)
                    PlayStoreIcon(size = 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Heft Reng Kurdî",
                        color      = Color.White.copy(alpha = 0.45f),
                        fontSize   = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun WordRow(
    item     : DailyWordViewModel.WordItem,
    index    : Int,
    modifier : Modifier = Modifier,
) {
    val bgAlpha = if (index % 2 == 1) 0.03f else 0f

    Row(
        modifier          = modifier
            .background(Color(0xFF8B5CF6).copy(alpha = bgAlpha))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Emoji / numaralı kutu
        Box(
            modifier         = Modifier
                .size(width = 52.dp, height = 46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF8B5CF6).copy(alpha = 0.15f))
                .then(
                    Modifier.padding(1.dp)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                wordEmoji(index),
                fontSize = 22.sp,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.word,
                    color      = Color.White,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle  = FontStyle.Normal,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "—",
                    color    = Primary,
                    fontSize = 16.sp,
                )
            }
            Text(
                item.meaningTr,
                color      = Color.White.copy(alpha = 0.55f),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
@Composable
private fun PlayStoreIcon(size: androidx.compose.ui.unit.Dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Kırmızı
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.56f, h * 0.5f); lineTo(0f, h); close()
            },
            color = Color(0xFFEA4335),
        )
        // Sarı
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f); lineTo(w * 0.56f, h * 0.5f); lineTo(w * 0.8f, h * 0.3f); close()
            },
            color = Color(0xFFFBBC04),
        )
        // Yeşil
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, h); lineTo(w * 0.56f, h * 0.5f); lineTo(w * 0.8f, h * 0.7f); close()
            },
            color = Color(0xFF34A853),
        )
        // Mavi
        drawPath(
            path = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.8f, h * 0.3f); lineTo(w * 0.56f, h * 0.5f)
                lineTo(w * 0.8f, h * 0.7f); lineTo(w, h * 0.6f); lineTo(w, h * 0.4f); close()
            },
            color = Color(0xFF4285F4),
        )
    }
}

private fun wordEmoji(index: Int): String = when (index % 5) {
    0 -> "🌩️"
    1 -> "☀️"
    2 -> "🕊️"
    3 -> "🌿"
    else -> "⛰️"
}

// ── Bitmap oluştur + paylaş ───────────────────────────────────────────────────
private suspend fun shareWordListCard(
    context : Context,
    words   : List<DailyWordViewModel.WordItem>,
) {
    withContext(Dispatchers.IO) {
        try {
            val width  = 1080
            // Her kelime için ~140px + header 110 + footer 90
            val rowH   = 140
            val height = 110 + words.size * rowH + 20 + 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // ── Arka plan gradient ────────────────────────────────────
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, height.toFloat(),
                intArrayOf(0xFF1A1630.toInt(), 0xFF0F0C1E.toInt()),
                null,
                android.graphics.Shader.TileMode.CLAMP,
            )
            bgPaint.shader = bgShader
            val bgRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
            canvas.drawRoundRect(bgRect, 48f, 48f, bgPaint)

            // ── Kenarlık ──────────────────────────────────────────────
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0x558B5CF6.toInt()
                strokeWidth = 3f
            }
            canvas.drawRoundRect(bgRect, 48f, 48f, borderPaint)

            // ── Uygulama ikonu (drawable) ──────────────────────────────
            val appIconBmp = try {
                val drawable = androidx.core.content.res.ResourcesCompat.getDrawable(
                    context.resources, R.drawable.ic_app_logo, null
                )
                val bmp = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                val c   = Canvas(bmp)
                drawable?.setBounds(0, 0, 80, 80)
                drawable?.draw(c)
                bmp
            } catch (e: Exception) { null }

            // ── Header ────────────────────────────────────────────────
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color    = 0xFF8B5CF6.toInt()
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = 38f
                textAlign = Paint.Align.LEFT
            }

            var y = 60f
            if (appIconBmp != null) {
                val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                val iconRect  = RectF(48f, y, 48f + 72f, y + 72f)
                // Yuvarlak kırpma
                val clipPath  = android.graphics.Path()
                clipPath.addRoundRect(iconRect, 18f, 18f, android.graphics.Path.Direction.CW)
                canvas.save(); canvas.clipPath(clipPath)
                canvas.drawBitmap(appIconBmp, null, iconRect, iconPaint)
                canvas.restore()
            }
            canvas.drawText("Peyvên Rojê · Heftreng", 140f, y + 48f, tp)

            // Tarih
            val dateTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x55F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 32f
                textAlign = Paint.Align.RIGHT
            }
            val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("tr")).format(java.util.Date())
            canvas.drawText(dateStr, width - 48f, y + 48f, dateTp)

            y += 100f

            // Header divider
            val divPaint = Paint().apply { color = 0x308B5CF6.toInt(); strokeWidth = 2f }
            canvas.drawLine(0f, y, width.toFloat(), y, divPaint)
            y += 2f

            // ── Kelime satırları ──────────────────────────────────────
            words.forEachIndexed { i, item ->
                if (i % 2 == 1) {
                    val rowPaint = Paint().apply { color = 0x0A8B5CF6.toInt() }
                    canvas.drawRect(0f, y, width.toFloat(), y + rowH, rowPaint)
                }

                // Emoji kutu arka planı
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x268B5CF6.toInt() }
                val boxRect  = RectF(40f, y + 20f, 40f + 120f, y + 20f + 100f)
                canvas.drawRoundRect(boxRect, 20f, 20f, boxPaint)

                // Emoji (metin olarak çiz)
                val emojiTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize  = 54f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText(wordEmoji(i), 100f, y + 84f, emojiTp)

                // Kelime (Kürtçe)
                val wordTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0xFFF0EEFA.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                    textSize  = 60f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(item.word, 186f, y + 70f, wordTp)

                // Ayraç çizgi
                val dashTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0xFF8B5CF6.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                    textSize  = 48f
                    textAlign = Paint.Align.LEFT
                }
                val wordW = wordTp.measureText(item.word)
                canvas.drawText("—", 186f + wordW + 16f, y + 68f, dashTp)

                // Türkçe anlam
                val trTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0x99F0EEFA.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                    textSize  = 40f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(item.meaningTr, 186f, y + 118f, trTp)

                y += rowH.toFloat()

                if (i < words.lastIndex) {
                    val rowDivPaint = Paint().apply { color = 0x208B5CF6.toInt(); strokeWidth = 1.5f }
                    canvas.drawLine(48f, y, width - 48f, y, rowDivPaint)
                }
            }

            y += 20f

            // Footer divider
            canvas.drawLine(0f, y, width.toFloat(), y, divPaint)
            y += 2f

            // Footer — ikon + "Heft Reng Kurdî"
            if (appIconBmp != null) {
                val fIconRect = RectF((width / 2f) - 160f, y + 22f, (width / 2f) - 88f, y + 94f)
                val fClip     = android.graphics.Path()
                fClip.addRoundRect(fIconRect, 12f, 12f, android.graphics.Path.Direction.CW)
                canvas.save(); canvas.clipPath(fClip)
                canvas.drawBitmap(appIconBmp, null, fIconRect, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
            }
            val footerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x77F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 36f
                textAlign = Paint.Align.LEFT
            }
            canvas.drawText("Heft Reng Kurdî", (width / 2f) - 68f, y + 66f, footerTp)

            // ── Dosyaya kaydet & paylaş ───────────────────────────────
            val file = File(context.cacheDir, "heftreng_words_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "Heftreng · Peyvên Rojê\nheftreng.com")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                context.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
            }
        } catch (e: Exception) {
            android.util.Log.e("DailyWordScreen", "share error: ${e.message}")
        }
    }
}
