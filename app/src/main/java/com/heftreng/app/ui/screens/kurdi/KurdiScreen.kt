package com.heftreng.app.ui.screens.kurdi

// ═══════════════════════════════════════════════════════════════════════════
//  KurdiScreen — Site teması (heft-reng.blogspot.com) ile tam uyumlu
//
//  Site yapısı:
//  - kf_units   → Üniteler (Destpêk, Jimare, Reng…)
//  - kf_lessons → Her üniteye ait dersler
//  - kf_vocab   → Kelime kartları
//  - kf_exercises → Sorular (mcq / fill / match)
//
//  Ekranlar:
//  1. Ana ekran  → XP kartı + günlük hedef + ünite yol haritası
//  2. Ders ekranı → Kelime flash cards + sorular + XP ödülü
//  3. Ferheng    → Sözlük (Yakında)
//  4. Rêziman    → Dilbilgisi (Yakında)
//  5. AI Ders    → OpenRouter Gemini ile üretilen ders
// ═══════════════════════════════════════════════════════════════════════════

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.*

// ── Ana ekran ────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(
    language : String = "tr",
    vm       : KurdiViewModel = hiltViewModel(),
) {
    val units       by vm.units.collectAsState()
    val lessons     by vm.lessons.collectAsState()
    val doneIds     by vm.doneIds.collectAsState()
    val xp          by vm.xp.collectAsState()
    val streak      by vm.streak.collectAsState()
    val level       by vm.level.collectAsState()
    val loading     by vm.loading.collectAsState()
    val activeLesson by vm.activeLesson.collectAsState()
    val toast       by vm.toast.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dersler", "Ferheng", "Rêziman", "AI Ders")

    // Toast
    LaunchedEffect(toast) {
        if (toast != null) kotlinx.coroutines.delay(2000)
        vm.clearToast()
    }

    // Aktif ders varsa ders ekranını göster
    if (activeLesson != null) {
        LessonScreen(
            activeLesson = activeLesson!!,
            onComplete   = { vm.completeLesson(activeLesson!!.lesson.id); vm.closeLesson() },
            onClose      = { vm.closeLesson() },
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .statusBarsPadding()
    ) {
        // Başlık
        Text(
            "Kurdî Fêrbibe",
            fontWeight = FontWeight.ExtraBold,
            color      = OnBackground,
            fontSize   = 20.sp,
            modifier   = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        // XP & Streak kartı
        XpStreakCard(xp = xp, streak = streak, level = level, language = language)

        Spacer(Modifier.height(8.dp))

        // Sekmeler
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Background,
            contentColor     = Primary,
            indicator        = { tabPositions ->
                if (tabPositions.isNotEmpty() && selectedTab < tabPositions.size) {
                    Box(
                        Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(2.dp)
                            .background(Primary)
                    )
                }
            },
        ) {
            tabs.forEachIndexed { i, title ->
                Tab(
                    selected               = selectedTab == i,
                    onClick                = { selectedTab = i },
                    text                   = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                    selectedContentColor   = Primary,
                    unselectedContentColor = Muted,
                )
            }
        }

        // İçerik
        when (selectedTab) {
            0 -> UnitsTab(
                units    = units,
                lessons  = lessons,
                doneIds  = doneIds,
                loading  = loading,
                language = language,
                onNext   = { vm.getNextLesson()?.let { vm.openLesson(it.id) } },
                onOpen   = { lessonId -> vm.openLesson(lessonId) },
            )
            1 -> DictionaryTab(language)
            2 -> GrammarTab(language)
            3 -> AiLessonTab(language, vm)
        }
    }

    // Toast snackbar
    if (toast != null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Surface(
                modifier = Modifier.padding(24.dp).navigationBarsPadding(),
                shape    = RoundedCornerShape(24.dp),
                color    = Primary,
            ) {
                Text(
                    toast ?: "",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    modifier   = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
        }
    }
}

// ── XP / Streak kartı ────────────────────────────────────────────────────────
@Composable
private fun XpStreakCard(xp: Int, streak: Int, level: Int, language: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        color = HeftSurface,
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (language == "ku") "Asta $level" else "Seviye $level",
                    fontWeight = FontWeight.Bold,
                    color      = Primary,
                    fontSize   = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                val progress = if (xp <= 0) 0f else ((xp % 100) / 100f).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress   = { progress },
                    modifier   = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color      = Primary,
                    trackColor = SurfaceVar,
                )
                Spacer(Modifier.height(4.dp))
                Text("$xp XP", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(20.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🔥", fontSize = 26.sp)
                Text(
                    "$streak",
                    fontWeight = FontWeight.Black,
                    color      = Amber,
                    fontSize   = 15.sp,
                )
                Text("Streak", color = Muted, fontSize = 10.sp)
            }
        }
    }
}

// ── Ünite yol haritası sekmesi ───────────────────────────────────────────────
@Composable
private fun UnitsTab(
    units    : List<KfUnit>,
    lessons  : List<KfLesson>,
    doneIds  : Set<String>,
    loading  : Boolean,
    language : String,
    onNext   : () -> Unit,
    onOpen   : (String) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        units.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📚", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(if (language == "ku") "Ders tune" else "Ders bulunamadı", color = Muted, fontSize = 14.sp)
            }
        }
        else -> LazyColumn(
            modifier       = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // Günlük hedef kartı
            item {
                DailyNudgeCard(
                    language = language,
                    lessons  = lessons,
                    doneIds  = doneIds,
                    onClick  = onNext,
                )
            }

            // Her ünite
            units.forEach { unit ->
                val unitLessons = lessons
                    .filter { it.unitId == unit.id }
                    .sortedBy { it.order }
                val done  = unitLessons.count { it.id in doneIds }
                val total = unitLessons.size
                val pct   = if (total > 0) done * 100 / total else 0
                val color = parseColor(unit.color)

                item(key = "unit_${unit.id}") {
                    UnitHeader(unit = unit, done = done, total = total, pct = pct, color = color)
                }

                if (unitLessons.isNotEmpty()) {
                    item(key = "path_${unit.id}") {
                        LessonPath(
                            lessons  = unitLessons,
                            doneIds  = doneIds,
                            color    = color,
                            onOpen   = onOpen,
                        )
                    }
                }
            }
        }
    }
}

// ── Günlük hedef kartı (site: .kp-daily-nudge) ──────────────────────────────
@Composable
private fun DailyNudgeCard(
    language : String,
    lessons  : List<KfLesson>,
    doneIds  : Set<String>,
    onClick  : () -> Unit,
) {
    val hasNext = lessons.any { it.id !in doneIds }
    if (!hasNext) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1A1333),
        border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🎯", fontSize = 26.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (language == "ku") "Armanca rojane" else "Günlük hedef",
                    fontWeight = FontWeight.Bold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                Text(
                    if (language == "ku") "Îro 1 ders temam bike!" else "Bugün 1 ders tamamla!",
                    color    = Muted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Primary,
            ) {
                Text(
                    if (language == "ku") "Destpê Bike" else "Başla",
                    color      = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp,
                    modifier   = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// ── Ünite başlık kartı ───────────────────────────────────────────────────────
@Composable
private fun UnitHeader(
    unit  : KfUnit,
    done  : Int,
    total : Int,
    pct   : Int,
    color : Color,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(14.dp),
        color = HeftSurface,
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Ünite ikonu
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(color.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .border(1.5.dp, color.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(unit.icon, fontSize = 22.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(unit.ttl, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                    if (unit.desc.isNotBlank()) {
                        Text(unit.desc, color = Muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("$done/$total ders tamamlandı", color = Muted, fontSize = 11.sp)
                }
                Text(
                    "$pct%",
                    color      = color,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 13.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SurfaceVar)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(pct / 100f)
                        .height(6.dp)
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

// ── Ders yolu (site: .kp-lesson-path — daireler yol şeklinde) ────────────────
@Composable
private fun LessonPath(
    lessons : List<KfLesson>,
    doneIds : Set<String>,
    color   : Color,
    onOpen  : (String) -> Unit,
) {
    val firstNotDone = lessons.indexOfFirst { it.id !in doneIds }
        .let { if (it == -1) lessons.size else it }

    Column(
        modifier            = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        lessons.forEachIndexed { index, lesson ->
            val isDone   = lesson.id in doneIds
            val isActive = index == firstNotDone
            val isLocked = index > firstNotDone

            LessonPathNode(
                lesson   = lesson,
                isDone   = isDone,
                isActive = isActive,
                isLocked = isLocked,
                color    = color,
                index    = index,
                total    = lessons.size,
                onClick  = {
                    if (!isLocked) onOpen(lesson.id)
                },
            )
        }
    }
}

@Composable
private fun LessonPathNode(
    lesson   : KfLesson,
    isDone   : Boolean,
    isActive : Boolean,
    isLocked : Boolean,
    color    : Color,
    index    : Int,
    total    : Int,
    onClick  : () -> Unit,
) {
    // Zigzag offset — site temasındaki gibi sola-ortaya-sağa sıralanır
    val offsets = listOf(0.3f, 0.5f, 0.7f, 0.5f)
    val hAlign  = offsets[index % offsets.size]

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Spacer(Modifier.fillMaxWidth(hAlign - 0.1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Üstten bağlantı çizgisi
            if (index > 0) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(if (isDone) color else Divider)
                )
            }

            // Ders dairesi butonu
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isDone || isActive -> color
                            isLocked           -> SurfaceVar
                            else               -> SurfaceVar
                        }
                    )
                    .border(
                        width = if (isActive) 3.dp else 2.dp,
                        color = if (isLocked) Divider else color.copy(alpha = 0.7f),
                        shape = CircleShape,
                    )
                    .shadow(if (isDone || isActive) 6.dp else 0.dp, CircleShape)
                    .clickable(enabled = !isLocked) { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isDone   -> Text("⭐", fontSize = 26.sp)
                    isLocked -> Icon(Icons.Default.Lock, null, tint = Muted, modifier = Modifier.size(22.dp))
                    isActive -> Text(lesson.emoji, fontSize = 26.sp)
                    else     -> Text(lesson.emoji, fontSize = 26.sp)
                }
            }

            // "BAŞLA!" chip — sadece aktif derse
            if (isActive) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = color,
                ) {
                    Text(
                        "BAŞLA!",
                        color      = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 10.sp,
                        modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            // Ders adı
            Spacer(Modifier.height(if (isActive) 4.dp else 6.dp))
            Text(
                lesson.nameTr,
                color      = if (isLocked) Muted else OnBackground,
                fontSize   = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                textAlign  = TextAlign.Center,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                modifier   = Modifier.width(80.dp),
            )

            // Altta bağlantı çizgisi
            if (index < total - 1) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(20.dp)
                        .background(if (isDone) color else Divider)
                )
            }
        }
    }
}

// ── Ders yapma ekranı ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonScreen(
    activeLesson : ActiveLesson,
    onComplete   : () -> Unit,
    onClose      : () -> Unit,
) {
    var step         by remember { mutableStateOf(0) }
    var selectedAns  by remember { mutableStateOf<String?>(null) }
    var showResult   by remember { mutableStateOf(false) }
    var correctCount by remember { mutableStateOf(0) }
    var fillAnswer   by remember { mutableStateOf("") }

    val lesson    = activeLesson.lesson
    val vocab     = activeLesson.vocab
    val exercises = activeLesson.exercises

    val vocabDone  = vocab.isEmpty() || step >= vocab.size
    val exStep     = if (vocabDone) (if (vocab.isEmpty()) step else step - vocab.size) else 0
    val exDone     = exercises.isEmpty() || exStep >= exercises.size
    val currentEx  = if (vocabDone && !exDone) exercises.getOrNull(exStep) else null
    val allDone    = vocabDone && exDone
    val totalSteps = vocab.size + exercises.size

    // Devam butonu metni ve aktifliği
    val canAdvance = when {
        allDone    -> true
        !vocabDone -> true
        currentEx != null -> showResult
        else -> false
    }
    val nextLabel = when {
        allDone                          -> "Dersi Bitir"
        !vocabDone && step >= vocab.size - 1 -> "Sorulara Geç →"
        !vocabDone                       -> "Sonraki Kelime"
        exStep >= exercises.size - 1     -> "Dersi Tamamla 🎉"
        else                             -> "Devam"
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lesson.nameTr, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                        if (totalSteps > 0) {
                            LinearProgressIndicator(
                                progress   = { step.toFloat() / totalSteps },
                                modifier   = Modifier.fillMaxWidth().height(4.dp).padding(top = 2.dp),
                                color      = Primary,
                                trackColor = SurfaceVar,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, null, tint = OnBackground)
                    }
                },
                actions = {
                    Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(alpha = 0.15f)) {
                        Text("+${lesson.xp} XP", color = Amber, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
        // ── Devam butonu SABIT altta ───────────────────────────────────────────
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // Doğru/Yanlış sonuç bandı — showResult sonrası göster
                if (showResult && currentEx != null && !allDone) {
                    val isCorrect = when (currentEx.type) {
                        "mcq"  -> selectedAns == currentEx.answer
                        "fill" -> fillAnswer.trim().equals(currentEx.answer, ignoreCase = true)
                        else   -> true
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (isCorrect) Color(0xFF22C55E).copy(0.15f) else Color(0xFFEF4444).copy(0.15f),
                    ) {
                        Row(
                            modifier          = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(if (isCorrect) "✓" else "✗", color = if (isCorrect) Color(0xFF22C55E) else Color(0xFFEF4444), fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(
                                if (isCorrect) "Doğru!" else "Doğru cevap: ${currentEx.answer}",
                                color      = if (isCorrect) Color(0xFF22C55E) else Color(0xFFEF4444),
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp,
                            )
                        }
                    }
                }

                Button(
                    onClick  = {
                        when {
                            allDone -> onComplete()
                            !vocabDone -> {
                                step++
                            }
                            showResult || currentEx?.type == "match" || currentEx?.type == "build" -> {
                                // Sonraki soruya geç
                                step++
                                selectedAns = null
                                showResult  = false
                                fillAnswer  = ""
                            }
                        }
                    },
                    enabled  = canAdvance,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = if (allDone) Amber else Primary,
                        contentColor   = Color.White,
                    ),
                ) {
                    Text(nextLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        },
    ) { padding ->

        when {
            // ── Tamamlandı ────────────────────────────────────────────────────
            allDone -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text("🎉", fontSize = 72.sp)
                        Text("Ders Tamamlandı!", fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 22.sp)
                        Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(0.15f)) {
                            Text("+${lesson.xp} XP kazandın!", color = Amber, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                        }
                        if (correctCount > 0) {
                            Text("$correctCount doğru cevap", color = Color(0xFF22C55E), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick  = onComplete,
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(14.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                        ) {
                            Text("Devam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
            }

            // ── Kelime kartı ──────────────────────────────────────────────────
            !vocabDone -> {
                val voc = vocab[step]
                LazyColumn(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(20.dp),
                            color    = HeftSurface,
                        ) {
                            Column(
                                modifier            = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (voc.e.isNotBlank()) {
                                    Text(voc.e, fontSize = 56.sp)
                                    Spacer(Modifier.height(16.dp))
                                }
                                Text(voc.ku, fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 30.sp, textAlign = TextAlign.Center)
                                if (voc.kp.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text("/${voc.kp}/", color = Muted, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                                HorizontalDivider(color = Divider)
                                Spacer(Modifier.height(12.dp))
                                Text(voc.tr, fontWeight = FontWeight.SemiBold, color = Primary, fontSize = 22.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Adım göstergesi
                        Text(
                            "${step + 1} / ${vocab.size}",
                            color    = Muted,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            // ── Egzersiz ──────────────────────────────────────────────────────
            currentEx != null -> {
                val ex = currentEx
                LaunchedEffect(step) {
                    selectedAns = null
                    showResult  = false
                    fillAnswer  = ""
                }
                LazyColumn(
                    modifier            = Modifier.fillMaxSize().padding(padding),
                    contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        // Soru
                        Text(
                            ex.question,
                            fontWeight = FontWeight.Bold,
                            color      = OnBackground,
                            fontSize   = 18.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.fillMaxWidth(),
                        )
                        // Türkçe ipucu (fill tipinde)
                        if (ex.type == "fill" && ex.questionTr.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(ex.questionTr, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        }
                        Spacer(Modifier.height(20.dp))
                    }

                    when (ex.type) {
                        "mcq" -> {
                            val options = listOf(ex.optA, ex.optB, ex.optC, ex.optD).filter { it.isNotBlank() }
                            items(options) { opt ->
                                val isSel     = selectedAns == opt
                                val isCorrect = showResult && opt == ex.answer // answer alanını kullan
                                val bgColor   = when {
                                    !showResult -> if (isSel) Primary.copy(0.15f) else HeftSurface
                                    isCorrect   -> Color(0xFF22C55E).copy(0.15f)
                                    isSel       -> Color(0xFFEF4444).copy(0.15f)
                                    else        -> HeftSurface
                                }
                                val borderCol = when {
                                    !showResult -> if (isSel) Primary else Divider
                                    isCorrect   -> Color(0xFF22C55E)
                                    isSel       -> Color(0xFFEF4444)
                                    else        -> Divider
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clickable(enabled = !showResult) {
                                            selectedAns = opt
                                            showResult  = true
                                            if (opt == ex.answer) correctCount++
                                        },
                                    shape  = RoundedCornerShape(12.dp),
                                    color  = bgColor,
                                    border = BorderStroke(1.5.dp, borderCol),
                                ) {
                                    Row(
                                        modifier          = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(opt, color = OnBackground, fontWeight = FontWeight.Medium, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                        if (showResult) {
                                            val selCorrect = opt == ex.answer
                                            Icon(
                                                if (selCorrect) Icons.Default.CheckCircle
                                                else if (isSel) Icons.Default.Cancel
                                                else Icons.Default.RadioButtonUnchecked,
                                                null,
                                                tint     = if (selCorrect) Color(0xFF22C55E) else if (isSel) Color(0xFFEF4444) else Divider,
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        "fill" -> {
                            item {
                                OutlinedTextField(
                                    value         = fillAnswer,
                                    onValueChange = { if (!showResult) fillAnswer = it },
                                    placeholder   = { Text("Cevabını yaz…", color = Muted) },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor      = Primary,
                                        unfocusedBorderColor    = Divider,
                                        focusedTextColor        = OnBackground,
                                        unfocusedTextColor      = OnBackground,
                                        unfocusedContainerColor = HeftSurface,
                                        focusedContainerColor   = HeftSurface,
                                    ),
                                )
                                if (!showResult) {
                                    Spacer(Modifier.height(12.dp))
                                    Button(
                                        onClick  = {
                                            showResult  = true
                                            selectedAns = fillAnswer
                                            if (fillAnswer.trim().equals(ex.answer, ignoreCase = true)) correctCount++
                                        },
                                        enabled  = fillAnswer.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                                    ) {
                                        Text("Kontrol Et", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        "match" -> {
                            item {
                                MatchExercise(
                                    pairs     = ex.pairs,
                                    onCorrect = { correctCount++ },
                                    onAllDone = { showResult = true; selectedAns = "done" },
                                )
                            }
                        }
                        "build" -> {
                            item {
                                BuildExercise(
                                    words     = ex.words,
                                    tr        = ex.tr,
                                    onCorrect = { correctCount++ },
                                    onChecked = { showResult = true; selectedAns = "done" },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Eşleştirme egzersizi (match) ─────────────────────────────────────────────
@Composable
private fun MatchExercise(
    pairs    : List<Pair<String, String>>,
    onCorrect: () -> Unit,
    onAllDone: () -> Unit,
) {
    if (pairs.isEmpty()) return
    val shuffledRight = remember(pairs) { pairs.map { it.second }.shuffled() }
    var selectedLeft  by remember { mutableStateOf<String?>(null) }
    var selectedRight by remember { mutableStateOf<String?>(null) }
    val matched       = remember { mutableStateListOf<String>() }  // matched left keys
    val wrongLeft     = remember { mutableStateOf<String?>(null) }
    val wrongRight    = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedLeft, selectedRight) {
        val l = selectedLeft; val r = selectedRight
        if (l != null && r != null) {
            val correct = pairs.find { it.first == l }?.second == r
            if (correct) {
                matched.add(l)
                onCorrect()
                if (matched.size == pairs.size) onAllDone()
            } else {
                wrongLeft.value = l; wrongRight.value = r
                kotlinx.coroutines.delay(500)
                wrongLeft.value = null; wrongRight.value = null
            }
            selectedLeft = null; selectedRight = null
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            "Eşleştir", color = Muted, fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Sol — Kürtçe
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pairs.forEach { (ku, _) ->
                    val isMatched  = ku in matched
                    val isSelected = selectedLeft == ku
                    val isWrong    = wrongLeft.value == ku
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isMatched) { selectedLeft = ku },
                        shape  = RoundedCornerShape(10.dp),
                        color  = when {
                            isMatched  -> Color(0xFF22C55E).copy(0.12f)
                            isWrong    -> Color(0xFFEF4444).copy(0.12f)
                            isSelected -> Primary.copy(0.15f)
                            else       -> HeftSurface
                        },
                        border = BorderStroke(
                            1.5.dp,
                            when {
                                isMatched  -> Color(0xFF22C55E)
                                isWrong    -> Color(0xFFEF4444)
                                isSelected -> Primary
                                else       -> Divider
                            }
                        ),
                    ) {
                        Text(
                            ku,
                            color      = if (isMatched) Color(0xFF22C55E) else OnBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 13.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                }
            }
            // Sağ — Türkçe (karıştırılmış)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                shuffledRight.forEach { tr ->
                    val matchedLeft = pairs.find { it.second == tr }?.first
                    val isMatched   = matchedLeft != null && matchedLeft in matched
                    val isSelected  = selectedRight == tr
                    val isWrong     = wrongRight.value == tr
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isMatched) { selectedRight = tr },
                        shape  = RoundedCornerShape(10.dp),
                        color  = when {
                            isMatched  -> Color(0xFF22C55E).copy(0.12f)
                            isWrong    -> Color(0xFFEF4444).copy(0.12f)
                            isSelected -> Primary.copy(0.15f)
                            else       -> HeftSurface
                        },
                        border = BorderStroke(
                            1.5.dp,
                            when {
                                isMatched  -> Color(0xFF22C55E)
                                isWrong    -> Color(0xFFEF4444)
                                isSelected -> Primary
                                else       -> Divider
                            }
                        ),
                    ) {
                        Text(
                            tr,
                            color      = if (isMatched) Color(0xFF22C55E) else OnBackground,
                            fontWeight = FontWeight.Medium,
                            fontSize   = 13.sp,
                            textAlign  = TextAlign.Center,
                            modifier   = Modifier.padding(12.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

// ── Cümle kurma egzersizi (build) ─────────────────────────────────────────────
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(
    words    : List<String>,
    tr       : String,
    onCorrect: () -> Unit,
    onChecked: () -> Unit,
) {
    val bank     = remember(words) { words.shuffled() }
    val placed   = remember { mutableStateListOf<String>() }
    val usedIdx  = remember { mutableStateListOf<Int>() }   // bank index'leri
    var checked  by remember { mutableStateOf(false) }
    var correct  by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Türkçe anlam
        if (tr.isNotBlank()) {
            Text("\"$tr\"", color = Muted, fontSize = 13.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp))
        }

        // Yerleştirme alanı
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        !checked -> HeftSurface
                        correct  -> Color(0xFF22C55E).copy(0.1f)
                        else     -> Color(0xFFEF4444).copy(0.1f)
                    }
                )
                .border(
                    2.dp,
                    when {
                        !checked -> Divider
                        correct  -> Color(0xFF22C55E)
                        else     -> Color(0xFFEF4444)
                    },
                    RoundedCornerShape(12.dp),
                )
                .padding(12.dp)
                .heightIn(min = 52.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement   = Arrangement.spacedBy(7.dp),
            ) {
                placed.forEachIndexed { i, word ->
                    Surface(
                        modifier = Modifier.clickable(enabled = !checked) {
                            placed.removeAt(i)
                            // Bu kelimeye ait ilk kullanılmış bank index'ini serbest bırak
                            val idx = bank.indexOfFirst { it == word && bank.indexOf(it) in usedIdx }
                            if (idx != -1) usedIdx.remove(idx)
                        },
                        shape  = RoundedCornerShape(9.dp),
                        color  = Primary.copy(0.15f),
                        border = BorderStroke(2.dp, Primary),
                    ) {
                        Text(word, color = Primary, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp))
                    }
                }
                if (placed.isEmpty()) {
                    Text("Kelimelere dokun →", color = Muted, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Kelime bankası
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
        ) {
            bank.forEachIndexed { idx, word ->
                val isUsed = idx in usedIdx
                Surface(
                    modifier = Modifier
                        .clickable(enabled = !checked && !isUsed) {
                            placed.add(word)
                            usedIdx.add(idx)
                        }
                        .alpha(if (isUsed) 0.2f else 1f),
                    shape  = RoundedCornerShape(10.dp),
                    color  = if (isUsed) SurfaceVar else HeftSurface,
                    border = BorderStroke(2.dp, Divider),
                ) {
                    Text(word, color = OnBackground, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp))
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (!checked) {
            Button(
                onClick = {
                    correct = placed.toList() == words
                    if (correct) onCorrect()
                    checked = true
                    onChecked()
                },
                enabled  = placed.size == words.size,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            ) { Text("Kontrol Et", color = Color.White, fontWeight = FontWeight.Bold) }
        } else {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (correct) Color(0xFF22C55E).copy(0.15f) else Color(0xFFEF4444).copy(0.15f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
                        null,
                        tint = if (correct) Color(0xFF22C55E) else Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (correct) "Doğru! ✓" else "Doğru sıra: ${words.joinToString(" ")}",
                        color = if (correct) Color(0xFF22C55E) else Color(0xFFEF4444),
                        fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                    )
                }
            }
        }
    }
}

// ── Sözlük sekmesi ───────────────────────────────────────────────────────────
@Composable
private fun DictionaryTab(language: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("📖", fontSize = 48.sp)
            Text("Ferheng", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text(if (language == "ku") "Zû tê" else "Yakında", color = Muted, fontSize = 14.sp)
        }
    }
}

// ── Dilbilgisi sekmesi ───────────────────────────────────────────────────────
@Composable
private fun GrammarTab(language: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎓", fontSize = 48.sp)
            Text("Rêziman", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text(if (language == "ku") "Zû tê" else "Yakında", color = Muted, fontSize = 14.sp)
        }
    }
}

// ── AI Ders sekmesi ──────────────────────────────────────────────────────────
@Composable
fun AiLessonTab(language: String = "tr", vm: KurdiViewModel = hiltViewModel()) {
    val aiLesson  by vm.aiLesson.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()
    val aiError   by vm.aiError.collectAsState()
    val savedKey  by vm.orApiKey.collectAsState()
    var apiKey    by remember(savedKey) { mutableStateOf(savedKey) }
    var topic     by remember { mutableStateOf("") }
    var level     by remember { mutableStateOf("destpêk") }
    val levels    = listOf("destpêk" to "🌱 Başlangıç", "navîn" to "🌿 Orta", "pêşketî" to "🌳 İleri")

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("AI ile Kurdî Ders", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
            Text("OpenRouter API anahtarını gir, kaydedilir.", color = Muted, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(
                value         = apiKey,
                onValueChange = { apiKey = it; vm.saveOrKey(it) },
                label    = { Text("OpenRouter API Key", color = Muted, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                trailingIcon = {
                    if (apiKey.isNotBlank())
                        Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF22C55E), modifier = Modifier.size(18.dp))
                },
                colors   = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
            )
        }
        item {
            OutlinedTextField(
                value = topic, onValueChange = { topic = it },
                label       = { Text(if (language == "ku") "Mijar" else "Konu", color = Muted, fontSize = 12.sp) },
                placeholder = { Text("Renkler, Sayılar…", color = Muted, fontSize = 12.sp) },
                modifier    = Modifier.fillMaxWidth(), singleLine = true,
                colors      = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { (lv, label) ->
                    val sel = level == lv
                    Surface(
                        modifier = Modifier.clickable { level = lv },
                        shape    = RoundedCornerShape(20.dp),
                        color    = if (sel) Primary else SurfaceVar,
                    ) {
                        Text(label, color = if (sel) Color.White else Muted, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        item {
            Button(
                onClick  = { vm.generateAiLesson(apiKey, topic, level) },
                enabled  = !aiLoading && apiKey.isNotBlank() && topic.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                shape    = RoundedCornerShape(12.dp),
            ) {
                if (aiLoading) { CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (aiLoading) "Üretiliyor…" else "✨ Ders Oluştur", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        aiError?.let { err ->
            item {
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
                    Text(err, color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }
        }
        aiLesson?.let { lesson ->
            item { Text("📚 ${lesson.topic} — ${lesson.level}", fontWeight = FontWeight.Bold, color = Primary, fontSize = 14.sp) }
            items(lesson.exercises) { ex ->
                Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(ex.ku, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
                        Text(ex.tr, color = Muted, fontSize = 12.sp)
                        if (ex.options.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            ex.options.forEach { opt ->
                                Text("• $opt", color = if (opt == ex.answer) Primary else OnBackground,
                                    fontSize = 12.sp, fontWeight = if (opt == ex.answer) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = { vm.clearAiLesson() }) { Text("Temizle", color = Muted) } }
        }
    }
}

// ── Yardımcılar ───────────────────────────────────────────────────────────────
private fun parseColor(hex: String): Color {
    return try { Color(android.graphics.Color.parseColor(hex)) }
    catch (_: Exception) { Color(0xFF8B5CF6) }
}

private fun Modifier.shadow(elevation: Dp, shape: Shape): Modifier = this
