package com.heftreng.app.ui.screens.dailyword

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.heftreng.app.utils.DailyWordCardBitmap
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

    // İlk yükleme — Unit key ile her composable oturumunda bir kez çalışır.
    // requestedDate parametresi her zaman doğru tarihle gelir çünkü NavHost
    // her farklı back-stack entry'si için yeni bir ViewModel oluşturur.
    LaunchedEffect(Unit) {
        vm.loadWords(requestedDate)
    }

    // Ekran her öne geldiğinde (gün geçişi dahil) yeniden yükle
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && requestedDate == null) {
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
                // displayDate bugünden farklıysa (yeni gün) yeniden yükle
                if (vm.displayDate.value != today) {
                    vm.loadWords(null)
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
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
            val cardWords = words.map {
                DailyWordCardBitmap.WordItem(
                    word      = it.word,
                    meaningTr = it.meaningTr,
                    meaningKu = it.meaningKu,
                    exampleKu = it.exampleKu,
                    exampleTr = it.exampleTr,
                    emoji     = it.emoji,
                )
            }
            val bitmap = DailyWordCardBitmap.build(context, cardWords, notifMode = false)
                ?: return@withContext
            val file = File(context.cacheDir, "heftreng_words_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, out) }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
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
