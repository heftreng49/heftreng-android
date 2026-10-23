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
import androidx.compose.ui.text.style.TextAlign
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
    val loading  by vm.loading.collectAsState()
    val notFound by vm.notFound.collectAsState()
    val scope    = rememberCoroutineScope()
    val context  = LocalContext.current

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
                notFound     -> Text(
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

            // ── Kelime satırları ──────────────────────────────
            Column(
                modifier            = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                words.forEachIndexed { i, item ->
                    WordRow(item = item, index = i, modifier = Modifier.fillMaxWidth())
                }
            }

            HorizontalDivider(color = primaryColor.copy(alpha = 0.15f))

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
private fun WordRow(
    item     : DailyWordViewModel.WordItem,
    index    : Int,
    modifier : Modifier = Modifier,
) {
    // Renkler fonksiyon içinde — top-level val'da @Composable kullanılamaz
    val cardBg = listOf(
        Color(0xFFF0FAF0), Color(0xFFFFF8F0), Color(0xFFF3F0FA),
        Color(0xFFEFF6FF), Color(0xFFFFF0F0),
    )[index % 5]
    val accent = listOf(
        Color(0xFF2E7D32), Color(0xFFE65100), Color(0xFF4527A0),
        Color(0xFF1565C0), Color(0xFFC62828),
    )[index % 5]

    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Numara kutusu
            Box(
                modifier         = Modifier
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

            Spacer(Modifier.width(12.dp))

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
                        overflow   = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }

                // Kürtçe anlam
                if (item.meaningKu.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        item.meaningKu,
                        color    = Color(0xFF1A1A2E).copy(alpha = 0.6f),
                        fontSize = 12.sp,
                    )
                }

                // Örnek cümle
                if (item.exampleKu.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "💬 ${item.exampleKu}",
                        color      = Color(0xFF1A1A2E).copy(alpha = 0.8f),
                        fontSize   = 12.sp,
                        fontStyle  = FontStyle.Italic,
                        fontWeight = FontWeight.Medium,
                    )
                    if (item.exampleTr.isNotBlank()) {
                        Text(
                            "(${item.exampleTr})",
                            color    = Color(0xFF1A1A2E).copy(alpha = 0.45f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // Emoji (varsa)
            if (item.emoji.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Text(item.emoji, fontSize = 24.sp)
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
            val width  = 1080
            val pad    = 48f
            val cardPad = 36f   // kart iç boşluk
            val cardGap = 20f   // kartlar arası boşluk
            val cardR   = 32f   // kart köşe yuvarlama

            // ── Renk paleti — uygulama içi kartlarla aynı ────────────
            val cardBgColors = intArrayOf(
                0xFFF0FAF0.toInt(), 0xFFFFF8F0.toInt(), 0xFFF3F0FA.toInt(),
                0xFFEFF6FF.toInt(), 0xFFFFF0F0.toInt(),
            )
            val accentColors = intArrayOf(
                0xFF2E7D32.toInt(), 0xFFE65100.toInt(), 0xFF4527A0.toInt(),
                0xFF1565C0.toInt(), 0xFFC62828.toInt(),
            )

            // ── Paint nesneleri ───────────────────────────────────────
            val headerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFF8B5CF6.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 40f
                textAlign = Paint.Align.LEFT
            }
            val dateTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x99333355.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 32f
                textAlign = Paint.Align.RIGHT
            }
            val numTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFFFFFFFF.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 38f
                textAlign = Paint.Align.CENTER
            }
            val wordTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0xFF1A1A2E.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 54f
                textAlign = Paint.Align.LEFT
            }
            val dashTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // renk satır bazında ayarlanacak
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 40f
                textAlign = Paint.Align.LEFT
            }
            val trTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                // renk satır bazında ayarlanacak
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 40f
                textAlign = Paint.Align.LEFT
            }
            val meaningKuTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0x99 shl 24)
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 34f
                textAlign = Paint.Align.LEFT
            }
            val exTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0xCC shl 24)
                typeface  = Typeface.create("sans-serif", Typeface.ITALIC)
                textSize  = 34f
                textAlign = Paint.Align.LEFT
            }
            val exTrTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = (0xFF1A1A2E.toInt() and 0x00FFFFFF) or (0x73 shl 24)
                typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                textSize  = 30f
                textAlign = Paint.Align.LEFT
            }
            val footerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x88404060.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 34f
                textAlign = Paint.Align.CENTER
            }

            // ── Her kartın yüksekliğini hesapla ──────────────────────
            val boxSize = 76f   // numara kutusu
            val lineH1  = 64f   // kelime satırı yüksekliği
            val lineH2  = 44f   // meaningKu satırı
            val lineH3  = 44f   // exampleKu satırı
            val lineH4  = 38f   // exampleTr satırı

            data class RowLayout(val h: Float)
            val rowLayouts = words.map { item ->
                var h = cardPad + lineH1
                if (item.meaningKu.isNotBlank()) h += lineH2 + 6f
                if (item.exampleKu.isNotBlank()) {
                    h += lineH3 + 12f
                    if (item.exampleTr.isNotBlank()) h += lineH4 + 4f
                }
                h += cardPad
                RowLayout(h)
            }

            val headerH = 130f
            val footerH = 110f
            val totalH  = (headerH + rowLayouts.sumOf { it.h.toDouble() } +
                           cardGap * words.size + footerH + pad * 2).toInt()

            val bitmap = Bitmap.createBitmap(width, totalH, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)

            // ── Dış arka plan — açık gri ──────────────────────────────
            canvas.drawColor(0xFFF8F7FC.toInt())

            // ── Uygulama ikonu ────────────────────────────────────────
            val appIconBmp: Bitmap? = try {
                val d = androidx.core.content.res.ResourcesCompat.getDrawable(
                    context.resources, com.heftreng.app.R.drawable.ic_app_logo, null)
                val b = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
                d?.setBounds(0, 0, 80, 80); d?.draw(AndroidCanvas(b)); b
            } catch (_: Exception) { null }

            // ── Header ────────────────────────────────────────────────
            var y = pad
            val iconSize = 72f
            val iconTop  = y + (headerH - iconSize) / 2f
            if (appIconBmp != null) {
                val iconRect = RectF(pad, iconTop, pad + iconSize, iconTop + iconSize)
                val clip = android.graphics.Path().also {
                    it.addRoundRect(iconRect, 18f, 18f, android.graphics.Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(clip)
                canvas.drawBitmap(appIconBmp, null, iconRect, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
                val textLeft = pad + iconSize + 16f
                val fm = headerTp.fontMetrics
                val textY = iconTop + iconSize / 2f - (fm.ascent + fm.descent) / 2f
                canvas.drawText("Peyvên Rojê · Heftreng", textLeft, textY, headerTp)
            } else {
                canvas.drawText("Peyvên Rojê · Heftreng", pad, y + 80f, headerTp)
            }
            val dateStr = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("tr")).format(java.util.Date())
            val dfm = dateTp.fontMetrics
            val dateY = iconTop + iconSize / 2f - (dfm.ascent + dfm.descent) / 2f
            canvas.drawText(dateStr, width - pad, dateY, dateTp)
            y += headerH

            // ── Kelime kartları ───────────────────────────────────────
            words.forEachIndexed { i, item ->
                val layout  = rowLayouts[i]
                val accent  = accentColors[i % accentColors.size]
                val cardBg  = cardBgColors[i % cardBgColors.size]
                val cardTop = y + cardGap / 2f
                val cardBot = cardTop + layout.h

                // Kart gölgesi (hafif)
                val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x18000000.toInt()
                }
                canvas.drawRoundRect(RectF(pad + 4f, cardTop + 6f, width - pad + 4f, cardBot + 6f), cardR, cardR, shadowPaint)

                // Kart arka planı
                canvas.drawRoundRect(RectF(pad, cardTop, width - pad, cardBot), cardR, cardR,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cardBg })

                // Numara kutusu
                val bx    = pad + cardPad
                val bSize = boxSize
                val by    = cardTop + cardPad
                canvas.drawRoundRect(RectF(bx, by, bx + bSize, by + bSize), 16f, 16f,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accent })
                val nfm = numTp.fontMetrics
                val ncy = by + bSize / 2f - (nfm.ascent + nfm.descent) / 2f
                canvas.drawText("${i + 1}", bx + bSize / 2f, ncy, numTp)

                // İçerik başlangıcı
                val textX = bx + bSize + 24f
                var cy    = by

                // Kelime + — + anlam
                dashTp.color  = accent
                trTp.color    = accent
                val wfm = wordTp.fontMetrics
                val wy  = cy + lineH1 / 2f - (wfm.ascent + wfm.descent) / 2f
                val wordW = wordTp.measureText(item.word)
                val dashW = dashTp.measureText(" — ")
                val maxTrW = width - pad - textX - wordW - dashW - pad
                val trText = run {
                    var t = item.meaningTr
                    while (t.isNotEmpty() && trTp.measureText(t) > maxTrW) t = t.dropLast(1)
                    if (t.length < item.meaningTr.length) "$t…" else t
                }
                canvas.drawText(item.word, textX, wy, wordTp)
                canvas.drawText(" — ", textX + wordW, wy, dashTp)
                canvas.drawText(trText, textX + wordW + dashW, wy, trTp)

                // Emoji (varsa) — sağ üst
                if (item.emoji.isNotBlank()) {
                    val emojiTp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 52f }
                    canvas.drawText(item.emoji, width - pad - cardPad - 60f, wy, emojiTp)
                }

                cy += lineH1

                // Kürtçe anlam
                if (item.meaningKu.isNotBlank()) {
                    cy += 6f
                    canvas.drawText(item.meaningKu, textX, cy + lineH2 * 0.75f, meaningKuTp)
                    cy += lineH2
                }

                // Örnek cümle
                if (item.exampleKu.isNotBlank()) {
                    cy += 12f
                    val maxW  = width - pad - textX - cardPad
                    val exRaw = "💬 ${item.exampleKu}"
                    val exText = if (exTp.measureText(exRaw) > maxW) {
                        var t = item.exampleKu
                        while (t.isNotEmpty() && exTp.measureText("💬 $t…") > maxW) t = t.dropLast(1)
                        "💬 $t…"
                    } else exRaw
                    canvas.drawText(exText, textX, cy + lineH3 * 0.75f, exTp)
                    cy += lineH3

                    if (item.exampleTr.isNotBlank()) {
                        cy += 4f
                        val trRaw = "(${item.exampleTr})"
                        val trEx = if (exTrTp.measureText(trRaw) > maxW) {
                            var t = item.exampleTr
                            while (t.isNotEmpty() && exTrTp.measureText("($t…)") > maxW) t = t.dropLast(1)
                            "($t…)"
                        } else trRaw
                        canvas.drawText(trEx, textX, cy + lineH4 * 0.75f, exTrTp)
                        cy += lineH4
                    }
                }

                y += layout.h + cardGap
            }

            y += cardGap / 2f + 8f

            // ── Footer — ikon + metin ─────────────────────────────────
            val footerCy = y + footerH / 2f
            if (appIconBmp != null) {
                val fiSize = 52f
                val fiLeft = width / 2f - fiSize / 2f - footerTp.measureText("Heft Reng Kurdî") / 2f - 12f
                val fiRect = RectF(fiLeft, footerCy - fiSize / 2f, fiLeft + fiSize, footerCy + fiSize / 2f)
                val fClip = android.graphics.Path().also {
                    it.addRoundRect(fiRect, 12f, 12f, android.graphics.Path.Direction.CW)
                }
                canvas.save(); canvas.clipPath(fClip)
                canvas.drawBitmap(appIconBmp, null, fiRect, Paint(Paint.ANTI_ALIAS_FLAG))
                canvas.restore()
                val ffm = footerTp.fontMetrics
                canvas.drawText("Heft Reng Kurdî",
                    fiLeft + fiSize + 12f + footerTp.measureText("Heft Reng Kurdî") / 2f,
                    footerCy - (ffm.ascent + ffm.descent) / 2f, footerTp)
            } else {
                val ffm = footerTp.fontMetrics
                canvas.drawText("Heft Reng Kurdî", width / 2f, footerCy - (ffm.ascent + ffm.descent) / 2f, footerTp)
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
