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
                        color     = primaryColor.copy(alpha = 0.08f),
                    )
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
    val primaryColor   = Primary
    val onSurfaceColor = OnSurface
    val bgAlpha        = if (index % 2 == 1) 0.04f else 0f

    Row(
        modifier          = modifier
            .background(primaryColor.copy(alpha = bgAlpha))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Numara kutusu (emoji yerine)
        Box(
            modifier         = Modifier
                .size(width = 40.dp, height = 40.dp)
                .background(primaryColor.copy(alpha = 0.12f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${index + 1}",
                color      = primaryColor,
                fontSize   = 15.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        Spacer(Modifier.width(14.dp))

        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.word,
                    color      = onSurfaceColor,
                    fontSize   = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontStyle  = FontStyle.Normal,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "—",
                    color    = primaryColor,
                    fontSize = 15.sp,
                )
            }
            Text(
                item.meaningTr,
                color      = onSurfaceColor.copy(alpha = 0.55f),
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
            )
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
            // Her kelime için ~140px + header 110 + footer 90
            val rowH   = 140
            val height = 110 + words.size * rowH + 20 + 90
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = AndroidCanvas(bitmap)

            // ── Arka plan ─────────────────────────────────────────────
            // Paylaşım görseli tema bağımsız, sabit koyu arka plan kullanır
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

            // ── Header ────────────────────────────────────────────────
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color    = 0xFF8B5CF6.toInt()
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = 38f
                textAlign = Paint.Align.LEFT
            }

            var y = 60f
            canvas.drawText("Peyvên Rojê · Heftreng", 48f, y + 48f, tp)

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

                // Numara kutu arka planı
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x268B5CF6.toInt() }
                val boxRect  = RectF(40f, y + 25f, 40f + 90f, y + 25f + 90f)
                canvas.drawRoundRect(boxRect, 18f, 18f, boxPaint)

                // Numara
                val numTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0xFF8B5CF6.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                    textSize  = 46f
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${i + 1}", 85f, y + 84f, numTp)

                // Kelime (Kürtçe)
                val wordTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0xFFF0EEFA.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                    textSize  = 60f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(item.word, 160f, y + 70f, wordTp)

                // Ayraç çizgi
                val dashTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0xFF8B5CF6.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                    textSize  = 48f
                    textAlign = Paint.Align.LEFT
                }
                val wordW = wordTp.measureText(item.word)
                canvas.drawText("—", 160f + wordW + 16f, y + 68f, dashTp)

                // Türkçe anlam
                val trTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color     = 0x99F0EEFA.toInt()
                    typeface  = Typeface.create("sans-serif", Typeface.NORMAL)
                    textSize  = 40f
                    textAlign = Paint.Align.LEFT
                }
                canvas.drawText(item.meaningTr, 160f, y + 118f, trTp)

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

            // Footer
            val footerTp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color     = 0x77F0EEFA.toInt()
                typeface  = Typeface.create("sans-serif", Typeface.BOLD)
                textSize  = 36f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Heft Reng Kurdî", width / 2f, y + 58f, footerTp)

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
