package com.heftreng.app.ui.screens.dailyword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.ui.theme.HeftBg
import com.heftreng.app.ui.theme.Primary
import com.heftreng.app.viewmodel.DailyWordViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DailyWordScreen(
    navController: NavController,
    language     : String = "tr",
    vm           : DailyWordViewModel = hiltViewModel(),
) {
    val words by vm.words.collectAsState()
    val loading by vm.loading.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val pagerState = rememberPagerState(pageCount = { if (words.isEmpty()) 1 else words.size })

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
                    containerColor    = HeftBg,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                ),
            )
        },
        containerColor = HeftBg,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(color = Primary)
                words.isEmpty() -> {
                    Text(
                        if (language == "ku") "Îro peyv nehat zêdekirin"
                        else "Bugün henüz kelime eklenmedi",
                        color    = Color.White.copy(alpha = 0.5f),
                        fontSize = 15.sp,
                    )
                }
                else -> {
                    Column(
                        modifier            = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Sayfa göstergesi
                        if (words.size > 1) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${pagerState.currentPage + 1} / ${words.size}",
                                color    = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp,
                            )
                        }

                        // Kelime kartları — yatay kaydırma
                        HorizontalPager(
                            state    = pagerState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                        ) { page ->
                            val word = words[page]
                            WordCard(
                                word      = word.word,
                                meaningTr = word.meaningTr,
                                meaningKu = word.meaningKu,
                                exampleKu = word.exampleKu,
                                pageIndex = page,
                                total     = words.size,
                            )
                        }

                        // Nokta göstergesi
                        if (words.size > 1) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                repeat(words.size) { i ->
                                    Box(
                                        modifier = Modifier
                                            .size(if (i == pagerState.currentPage) 8.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (i == pagerState.currentPage) Primary
                                                else Color.White.copy(alpha = 0.25f)
                                            )
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))

                        // Paylaş butonu
                        Button(
                            onClick = {
                                scope.launch {
                                    shareWordCard(
                                        context   = context,
                                        word      = words[pagerState.currentPage],
                                        pageIndex = pagerState.currentPage,
                                        total     = words.size,
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .height(50.dp),
                            shape  = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Primary,
                                contentColor   = Color.White,
                            ),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (language == "ku") "Parve Bike" else "Paylaş",
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 15.sp,
                            )
                        }

                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WordCard(
    word      : String,
    meaningTr : String,
    meaningKu : String,
    exampleKu : String,
    pageIndex : Int,
    total     : Int,
    modifier  : Modifier = Modifier,
) {
    // Her kart için farklı gradient tonu
    val gradients = listOf(
        listOf(Color(0xFF6C3FF5), Color(0xFF3B82F6)),
        listOf(Color(0xFF0EA5E9), Color(0xFF6C3FF5)),
        listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4)),
        listOf(Color(0xFF7C3AED), Color(0xFF4F46E5)),
        listOf(Color(0xFF3B82F6), Color(0xFF8B5CF6)),
    )
    val gradient = gradients[pageIndex % gradients.size]

    Box(
        modifier          = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        contentAlignment  = Alignment.Center,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(24.dp),
            colors   = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(gradient)
                    )
                    .padding(28.dp),
            ) {
                Column(
                    modifier            = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Üst etiket
                    Surface(
                        color = Color.White.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            "Heftreng · Peyvên Rojê",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            color    = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Spacer(Modifier.height(24.dp))

                    // Ana kelime
                    Text(
                        word,
                        color      = Color.White,
                        fontSize   = 38.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign  = TextAlign.Center,
                        lineHeight = 44.sp,
                    )

                    Spacer(Modifier.height(8.dp))

                    // Türkçe anlam
                    if (meaningTr.isNotBlank()) {
                        Text(
                            meaningTr,
                            color      = Color.White.copy(alpha = 0.9f),
                            fontSize   = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign  = TextAlign.Center,
                        )
                    }

                    // Kürtçe anlam
                    if (meaningKu.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            meaningKu,
                            color    = Color.White.copy(alpha = 0.7f),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    // Örnek cümle
                    if (exampleKu.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "“$exampleKu”",
                            color      = Color.White.copy(alpha = 0.85f),
                            fontSize   = 14.sp,
                            fontStyle  = FontStyle.Italic,
                            textAlign  = TextAlign.Center,
                            lineHeight = 21.sp,
                        )
                    }

                    // Sayfa göstergesi (birden fazla kelimeyse)
                    if (total > 1) {
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "${pageIndex + 1} / $total",
                            color    = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }
}

private suspend fun shareWordCard(
    context   : Context,
    word      : com.heftreng.app.viewmodel.DailyWordViewModel.WordItem,
    pageIndex : Int,
    total     : Int,
) {
    withContext(Dispatchers.Main) {
        try {
            // Bitmap oluştur
            val width  = 1080
            val height = 1080
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Gradient arka plan
            val gradients = listOf(
                intArrayOf(0xFF6C3FF5.toInt(), 0xFF3B82F6.toInt()),
                intArrayOf(0xFF0EA5E9.toInt(), 0xFF6C3FF5.toInt()),
                intArrayOf(0xFF8B5CF6.toInt(), 0xFF06B6D4.toInt()),
                intArrayOf(0xFF7C3AED.toInt(), 0xFF4F46E5.toInt()),
                intArrayOf(0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt()),
            )
            val (c1, c2) = gradients[pageIndex % gradients.size]

            val bgPaint = android.graphics.Paint()
            val shader = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                c1, c2,
                android.graphics.Shader.TileMode.CLAMP,
            )
            bgPaint.shader = shader
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Metin çiz
            val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color    = android.graphics.Color.WHITE
                typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
            }

            // Kelime
            textPaint.textSize = 96f
            textPaint.textAlign = android.graphics.Paint.Align.CENTER
            canvas.drawText(word.word, width / 2f, height / 2f - 80f, textPaint)

            // TR anlam
            textPaint.textSize = 48f
            textPaint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            textPaint.alpha = 230
            canvas.drawText(word.meaningTr, width / 2f, height / 2f + 10f, textPaint)

            // Örnek cümle
            if (word.exampleKu.isNotBlank()) {
                textPaint.textSize = 36f
                textPaint.alpha = 180
                textPaint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.ITALIC)
                val example = if (word.exampleKu.length > 50) word.exampleKu.take(50) + "…" else word.exampleKu
                canvas.drawText("“$example”", width / 2f, height / 2f + 100f, textPaint)
            }

            // Heftreng etiketi
            textPaint.textSize = 30f
            textPaint.alpha = 140
            textPaint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
            canvas.drawText("Heftreng · Peyvên Rojê", width / 2f, height - 60f, textPaint)

            // Dosyaya kaydet
            val file = File(context.cacheDir, "heftreng_word_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                file,
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type      = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TEXT, "${word.word} — ${word.meaningTr}\n\nheftreng.com")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Paylaş"))
        } catch (e: Exception) {
            android.util.Log.e("DailyWordScreen", "share error: ${e.message}")
        }
    }
}
