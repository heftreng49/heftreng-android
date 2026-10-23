package com.heftreng.app.ui.screens.dailyword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.Background
import com.heftreng.app.ui.theme.OnBackground
import com.heftreng.app.ui.theme.Primary
import com.heftreng.app.ui.theme.HeftSurface
import com.heftreng.app.ui.theme.OnSurface
import com.heftreng.app.viewmodel.DailyWordViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyWordScreen(
    navController : NavController,
    language      : String = "tr",
    requestedDate : String? = null,          // bildirimden gelen yyyy-MM-dd, null = bugün
    vm            : DailyWordViewModel = hiltViewModel(),
) {
    val words   by vm.words.collectAsState()
    val loading by vm.loading.collectAsState()
    val scope   = rememberCoroutineScope()
    val context = LocalContext.current

    // Bildirim tarihiyle açıldıysa o tarihi yükle (ilk kompozisyonda bir kez)
    LaunchedEffect(requestedDate) {
        vm.loadWords(requestedDate)
    }

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
                    titleContentColor          = OnBackground,
                    navigationIconContentColor = OnBackground,
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
                    color    = OnBackground.copy(alpha = 0.5f),
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
                                contentColor   = OnBackground,
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
    val surfaceColor  = HeftSurface
    val primaryColor  = Primary
    val onSurfaceColor = OnSurface

    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = surfaceColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {

            // ── Başlık satırı ─────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Peyvên Rojê · Heftreng",
                    color      = primaryColor,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    java.text.SimpleDateFormat("d MMM", java.util.Locale("tr"))
                        .format(java.util.Date()),
                    color    = onSurfaceColor.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            HorizontalDivider(color = primaryColor.copy(alpha = 0.15f))

            // ── Kelime kartları ───────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                words.forEachIndexed { i, item ->
                    WordRow(
                        item     = item,
                        index    = i,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // ── Footer ────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Heft Reng Kurdî",
                    color         = onSurfaceColor.copy(alpha = 0.4f),
                    fontSize      = 11.sp,
                    fontWeight    = FontWeight.Bold,
                    letterSpacing = 0.3.sp,
                )
            }
        }
    }
}

@Composable
// Kart başına arka plan rengi — her kart farklı pastel tonda
private val CARD_COLORS = listOf(
    Color(0xFFF0FAF0), // yeşilimsi
    Color(0xFFFFF8F0), // turuncumsu
    Color(0xFFF3F0FA), // morumsu
    Color(0xFFEFF6FF), // mavimsi
    Color(0xFFFFF0F0), // kırmızımsı
)
private val CARD_ACCENT = listOf(
    Color(0xFF2E7D32),
    Color(0xFFE65100),
    Color(0xFF4527A0),
    Color(0xFF1565C0),
    Color(0xFFC62828),
)

@Composable
private fun WordRow(
    item     : DailyWordViewModel.WordItem,
    index    : Int,
    modifier : Modifier = Modifier,
) {
    val accent = CARD_ACCENT[index % CARD_ACCENT.size]
    val bg     = CARD_COLORS[index % CARD_COLORS.size]

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp)),
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // ── Sol: numara + emoji ──────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Numara kutusu
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(accent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "${index + 1}",
                        color      = Color.White,
                        fontSize   = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                // Emoji (varsa)
                if (item.emoji.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(item.emoji, fontSize = 18.sp)
                }
            }

            Spacer(Modifier.width(12.dp))

            // ── Orta: kelime + anlam + örnek ────────────────────────────
            Column(modifier = Modifier.weight(1f)) {
                // Kelime + Türkçe anlam
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.word,
                        color      = Color(0xFF1A1A2E),
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("—", color = accent, fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.meaningTr,
                        color      = accent,
                        fontSize   = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines   = 1,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }

                // Kürtçe anlam (varsa)
                if (item.meaningKu.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.meaningKu,
                        color    = Color(0xFF1A1A2E).copy(alpha = 0.65f),
                        fontSize = 12.sp,
                    )
                }

                // Kürtçe örnek cümle
                if (item.exampleKu.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.Top) {
                        Text("💬 ", fontSize = 11.sp)
                        Column {
                            Text(
                                item.exampleKu,
                                color      = Color(0xFF1A1A2E).copy(alpha = 0.8f),
                                fontSize   = 12.sp,
                                fontStyle  = FontStyle.Italic,
                                fontWeight = FontWeight.Medium,
                            )
                            // Türkçe çeviri cümlesi
                            if (item.exampleTr.isNotBlank()) {
                                Text(
                                    "(${item.exampleTr})",
                                    color    = Color(0xFF1A1A2E).copy(alpha = 0.45f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Bitmap oluştur + paylaş ───────────────────────────────────────────────────
private suspend fun shareWordListCard(
    context : Context,
    words   : List<DailyWordViewModel.WordItem>,
) {
    withContext(Dispatchers.IO) {
        try {
            val width = 1080
            val pad   = 48f   // kenar boşluğu
            val r     = 48f   // köşe yuvarlama

            // ── Paint nesneleri ───────────────────────────────────────
            val headerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFF8B5CF6.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 40f
                textAlign = Paint.Align.LEFT
            }
            val dateTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x66F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 32f
                textAlign = Paint.Align.RIGHT
            }
            val numTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFF8B5CF6.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 44f
                textAlign = Paint.Align.CENTER
            }
            val wordTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFFF0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 58f
                textAlign = Paint.Align.LEFT
            }
            val dashTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFF8B5CF6.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 46f
                textAlign = Paint.Align.LEFT
            }
            val trTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xCCF0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 44f
                textAlign = Paint.Align.LEFT
            }
            val exTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x88F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.ITALIC)
                textSize  = 36f
                textAlign = Paint.Align.LEFT
            }
            val divPaint = Paint().apply { color = 0x308B5CF6.toInt(); strokeWidth = 2f }
            val footerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x88F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 34f
                textAlign = Paint.Align.CENTER
            }

            // ── Her kelimenin satır yüksekliğini hesapla ─────────────
            // Kelime + anlam satırı: 80px, örnek cümle varsa +60px, padding 50px
            val rowHeights = words.map { item ->
                if (item.exampleKu.isNotBlank()) 190f else 130f
            }
            val headerH = 120f
            val footerH = 100f
            val totalH  = (headerH + rowHeights.sum() + footerH + pad).toInt()

            val bitmap = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)

            // ── Arka plan gradient ────────────────────────────────────
            val bgShader = android.graphics.LinearGradient(
                0f, 0f, 0f, totalH.toFloat(),
                intArrayOf(0xFF1E1535.toInt(), 0xFF0D0A1A.toInt()),
                null, android.graphics.Shader.TileMode.CLAMP,
            )
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = bgShader }
            val bgRect  = RectF(0f, 0f, width.toFloat(), totalH.toFloat())
            canvas.drawRoundRect(bgRect, r, r, bgPaint)

            // ── İnce kenarlık ─────────────────────────────────────────
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                color = 0x448B5CF6.toInt()
                strokeWidth = 3f
            }
            canvas.drawRoundRect(bgRect, r, r, borderPaint)

            // ── Uygulama ikonu ────────────────────────────────────────
            val appIconBmp: Bitmap? = try {
                val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                    context.resources, com.heftreng.app.R.drawable.ic_app_logo, null)
                val b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                d?.setBounds(0, 0, 80, 80); d?.draw(AndroidCanvas(b)); b
            } catch (_: Exception) { null }

            // ── Header ────────────────────────────────────────────────
            var y = pad
            if (appIconBmp != null) {
                val iconRect = RectF(pad, y + 8f, pad + 72f, y + 80f)
                val clip = android.graphics.Path().also {
                    it.addRoundRect(iconRect, 18f, 18f, android.graphics.Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(clip)
                canvas.drawBitmap(appIconBmp, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
                canvas.drawText("Peyvên Rojê · Heftreng", pad + 88f, y + 60f, headerTp)
            } else {
                canvas.drawText("Peyvên Rojê · Heftreng", pad, y + 60f, headerTp)
            }
            val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("tr")).format(java.util.Date())
            canvas.drawText(dateStr, width - pad, y + 60f, dateTp)
            y += headerH

            canvas.drawLine(0f, y, width.toFloat(), y, divPaint)
            y += 2f

            // ── Kelime satırları ──────────────────────────────────────
            words.forEachIndexed { i, item ->
                val rowH = rowHeights[i]

                // Alternatif satır tonu
                if (i % 2 == 1) {
                    canvas.drawRect(0f, y, width.toFloat(), y + rowH,
                        Paint().apply { color = 0x0C8B5CF6.toInt() })
                }

                // Numara kutusu
                val bx = pad; val by = y + (rowH - 90f) / 2f
                canvas.drawRoundRect(RectF(bx, by, bx + 90f, by + 90f), 18f, 18f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x228B5CF6.toInt() })
                // numara dikey ortalama için font metrics kullan
                val numFm = numTp.fontMetrics
                val numCy = by + 90f / 2f - (numFm.ascent + numFm.descent) / 2f
                canvas.drawText("${i + 1}", bx + 45f, numCy, numTp)

                // Kelime — anlam yan yana
                val textX = pad + 110f
                val wordW = wordTp.measureText(item.word)
                val dashW = dashTp.measureText(" — ")

                // Satır 1: kelime + " — " + anlam
                val lineY = y + rowH * 0.42f
                canvas.drawText(item.word, textX, lineY, wordTp)
                canvas.drawText(" — ", textX + wordW, lineY, dashTp)
                canvas.drawText(item.meaningTr, textX + wordW + dashW, lineY, trTp)

                // Satır 2: örnek cümle (varsa)
                if (item.exampleKu.isNotBlank()) {
                    val exY = lineY + 58f
                    // Uzun cümleleri kırp (bitmap genişliğini aşmasın)
                    val maxW = width - textX - pad
                    val exText = if (exTp.measureText(item.exampleKu) > maxW) {
                        var t = item.exampleKu
                        while (t.isNotEmpty() && exTp.measureText("$t…") > maxW)
                            t = t.dropLast(1)
                        "$t…"
                    } else item.exampleKu
                    canvas.drawText(exText, textX, exY, exTp)
                }

                y += rowH
                if (i < words.lastIndex) {
                    canvas.drawLine(pad + 100f, y, width - pad, y,
                        Paint().apply { color = 0x188B5CF6.toInt(); strokeWidth = 1.5f })
                }
            }

            y += 16f
            canvas.drawLine(0f, y, width.toFloat(), y, divPaint)
            y += 2f

            // ── Footer — ikon + metin ─────────────────────────────────
            if (appIconBmp != null) {
                val fRect = RectF(width / 2f - 140f, y + 14f, width / 2f - 68f, y + 86f)
                val fClip = android.graphics.Path().also {
                    it.addRoundRect(fRect, 14f, 14f, android.graphics.Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(fClip)
                canvas.drawBitmap(appIconBmp, null, fRect, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
                canvas.drawText("Heft Reng Kurdî", width / 2f + 10f, y + 62f, footerTp)
            } else {
                canvas.drawText("Heft Reng Kurdî", width / 2f, y + 62f, footerTp)
            }

            // ── Dosyaya kaydet & paylaş ───────────────────────────────
            val file = File(context.cacheDir, "heftreng_words_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.provider", file
            )
            val shareText = "Heft Reng Kurdî\nhttps://play.google.com/store/apps/details?id=com.heftreng.app"
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, shareText)
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
