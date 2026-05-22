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
import com.heftreng.app.ui.i18n.Strings
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
    val tabs = listOf(
        Strings.kurdiUnits(language),
        Strings.kurdiDict(language),
        Strings.kurdiGrammar(language),
        Strings.kurdiAi(language),
    )

    // Toast
    LaunchedEffect(toast) {
        if (toast != null) kotlinx.coroutines.delay(2000)
        vm.clearToast()
    }

    // Aktif ders varsa ders ekranını göster
    if (activeLesson != null) {
        LessonScreen(
            activeLesson = activeLesson!!,
            language     = language,
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
            Strings.kurdiTitle(language),
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
                    Strings.levelLabel(language, level),
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
                Text(Strings.lessonNotFound(language), color = Muted, fontSize = 14.sp)
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
                            language = language,
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
                    Strings.dailyGoal(language),
                    fontWeight = FontWeight.Bold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                Text(
                    Strings.dailyGoalDesc(language),
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
                    Strings.startLesson(language),
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
    lessons  : List<KfLesson>,
    doneIds  : Set<String>,
    color    : Color,
    language : String = "tr",
    onOpen   : (String) -> Unit,
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
                language = language,
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
    language : String = "tr",
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
                        Strings.startLesson(language),
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
    language     : String = "tr",
    onComplete   : () -> Unit,
    onClose      : () -> Unit,
) {
    val lesson    = activeLesson.lesson
    val vocab     = activeLesson.vocab
    val exercises = activeLesson.exercises
    val totalSteps = vocab.size + exercises.size

    // ── TEK state makinesi — step değişince her şey sıfırlanır ──────────────
    var step         by remember { mutableIntStateOf(0) }
    var correctCount by remember { mutableIntStateOf(0) }

    // Adım hesapları
    val vocabDone = vocab.isEmpty() || step >= vocab.size
    val exStep    = if (vocabDone) (if (vocab.isEmpty()) step else step - vocab.size) else -1
    val exDone    = exercises.isEmpty() || exStep >= exercises.size
    val currentEx = if (vocabDone && exStep >= 0 && !exDone) exercises.getOrNull(exStep) else null
    val allDone   = vocabDone && exDone

    // ── Egzersiz başına sıfırlanan state — key(step) ile yönetilir ──────────
    // Bu state'ler BuildExercise/MatchExercise içinde key(step) sayesinde
    // step her değiştiğinde otomatik sıfırlanır
    var showResult  by remember(step) { mutableStateOf(false) }
    var selectedAns by remember(step) { mutableStateOf<String?>(null) }
    var fillAnswer  by remember(step) { mutableStateOf("") }
    var buildResult by remember(step) { mutableStateOf<Boolean?>(null) } // null=bekliyor, true=doğru, false=yanlış

    // Devam butonu
    val canAdvance = when {
        allDone    -> true
        !vocabDone -> true
        currentEx?.type == "build"  -> buildResult != null
        currentEx?.type == "match"  -> showResult
        currentEx != null           -> showResult
        else -> false
    }
    val nextLabel = when {
        allDone                              -> Strings.finishLesson(language)
        !vocabDone && step == vocab.size - 1 -> Strings.toQuestions(language)
        !vocabDone                           -> Strings.nextQuestion(language)
        exStep == exercises.size - 1         -> Strings.complete(language)
        else                                 -> Strings.continueLesson(language)
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
                                progress   = { step.toFloat() / totalSteps.coerceAtLeast(1) },
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
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Background)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .navigationBarsPadding(),
            ) {
                // Sonuç bandı
                val showBand = showResult || buildResult != null
                if (showBand && !allDone && currentEx != null) {
                    val isOk = when (currentEx.type) {
                        "build" -> buildResult == true
                        "match" -> true
                        "fill"  -> fillAnswer.trim().equals(currentEx.answer, ignoreCase = true)
                        "mcq"   -> selectedAns?.trim().equals(currentEx.answer.trim(), ignoreCase = true) == true
                        else    -> true
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape    = RoundedCornerShape(12.dp),
                        color    = if (isOk) Color(0xFF22C55E).copy(0.15f) else Color(0xFFEF4444).copy(0.15f),
                    ) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                if (isOk) "✓" else "✗",
                                color      = if (isOk) Color(0xFF22C55E) else Color(0xFFEF4444),
                                fontWeight = FontWeight.Black,
                                fontSize   = 18.sp,
                            )
                            Column {
                                Text(
                                    if (isOk) Strings.correctAnswer(language) else Strings.wrongAnswer(language),
                                    color      = if (isOk) Color(0xFF22C55E) else Color(0xFFEF4444),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize   = 14.sp,
                                )
                                if (!isOk && currentEx.type != "build") {
                                    Text(
                                        Strings.correctAnswerIs(language, currentEx.answer),
                                        color    = Color(0xFFEF4444).copy(0.8f),
                                        fontSize = 12.sp,
                                    )
                                }
                                if (!isOk && currentEx.type == "build") {
                                    Text(
                                        "Doğru sıra: ${currentEx.words.joinToString(" ")}",
                                        color    = Color(0xFFEF4444).copy(0.8f),
                                        fontSize = 12.sp,
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        when {
                            allDone    -> onComplete()
                            !vocabDone -> step++
                            canAdvance -> step++
                        }
                    },
                    enabled  = canAdvance,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape    = RoundedCornerShape(14.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor         = if (allDone) Amber else Primary,
                        disabledContainerColor = SurfaceVar,
                    ),
                ) {
                    Text(nextLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        },
    ) { padding ->
        when {
            // ── Tamamlandı ────────────────────────────────────────────────────
            allDone -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Text("🎉", fontSize = 72.sp)
                    Text("Ders Tamamlandı!", fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 22.sp)
                    Surface(shape = RoundedCornerShape(20.dp), color = Amber.copy(0.15f)) {
                        Text("+${lesson.xp} XP kazandın!", color = Amber, fontWeight = FontWeight.Bold,
                            fontSize = 16.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
                    }
                    Text("$correctCount doğru cevap ✓", color = Color(0xFF22C55E), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick  = onComplete,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = Amber),
                    ) { Text("Devam", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }

            // ── Kelime kartı ──────────────────────────────────────────────────
            !vocabDone -> {
                val voc = vocab[step]
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape    = RoundedCornerShape(24.dp),
                            color    = HeftSurface,
                        ) {
                            Column(
                                Modifier.padding(36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                if (voc.e.isNotBlank()) {
                                    Text(voc.e, fontSize = 60.sp)
                                    Spacer(Modifier.height(16.dp))
                                }
                                Text(voc.ku, fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 32.sp, textAlign = TextAlign.Center)
                                if (voc.kp.isNotBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text("/${voc.kp}/", color = Muted, fontSize = 14.sp)
                                }
                                Spacer(Modifier.height(16.dp))
                                HorizontalDivider(color = Divider)
                                Spacer(Modifier.height(16.dp))
                                Text(voc.tr, fontWeight = FontWeight.SemiBold, color = Primary, fontSize = 24.sp, textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("${step + 1} / ${vocab.size}", color = Muted, fontSize = 13.sp)
                    }
                }
            }

            // ── Egzersiz ──────────────────────────────────────────────────────
            currentEx != null -> {
                val ex = currentEx
                // key(step) kritik — step değişince tüm egzersiz composable'ı sıfırdan oluşturulur
                key(step) {
                    LazyColumn(
                        modifier            = Modifier.fillMaxSize().padding(padding),
                        contentPadding      = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Soru başlığı
                        item {
                            when (ex.type) {
                                "build" -> {} // build kendi başlığını gösterir
                                else -> {
                                    Text(
                                        ex.question,
                                        fontWeight = FontWeight.ExtraBold,
                                        color      = OnBackground,
                                        fontSize   = 20.sp,
                                        textAlign  = TextAlign.Center,
                                        modifier   = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                    )
                                    if (ex.questionTr.isNotBlank()) {
                                        Text(ex.questionTr, color = Muted, fontSize = 13.sp,
                                            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    }
                                    Spacer(Modifier.height(24.dp))
                                }
                            }
                        }

                        when (ex.type) {
                            // ── Çoktan seçmeli ────────────────────────────────
                            "mcq" -> {
                                // remember LazyListScope dışında item{} içinde kullanılmalı
                                item {
                                    val options = remember(ex.id) {
                                        listOf(ex.optA, ex.optB, ex.optC, ex.optD)
                                            .filter { it.isNotBlank() }
                                            .shuffled()
                                    }
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                        options.forEach { opt ->
                                    val isSel     = selectedAns == opt
                                    val isCorrect = showResult && opt.trim().equals(ex.answer.trim(), ignoreCase = true)
                                    val isWrong   = showResult && isSel && !opt.trim().equals(ex.answer.trim(), ignoreCase = true)
                                            Surface(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable(enabled = !showResult) {
                                                        selectedAns = opt
                                                        showResult  = true
                                                        if (opt.trim().equals(ex.answer.trim(), ignoreCase = true)) correctCount++
                                                    },
                                                shape  = RoundedCornerShape(14.dp),
                                                color  = when {
                                                    isCorrect -> Color(0xFF22C55E).copy(0.12f)
                                                    isWrong   -> Color(0xFFEF4444).copy(0.12f)
                                                    isSel     -> Primary.copy(0.12f)
                                                    else      -> HeftSurface
                                                },
                                                border = BorderStroke(2.dp, when {
                                                    isCorrect -> Color(0xFF22C55E)
                                                    isWrong   -> Color(0xFFEF4444)
                                                    isSel     -> Primary
                                                    else      -> Divider
                                                }),
                                            ) {
                                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                                    Text(opt, color = OnBackground, fontWeight = FontWeight.SemiBold,
                                                        fontSize = 15.sp, modifier = Modifier.weight(1f))
                                                    if (showResult) {
                                                        Icon(
                                                            if (isCorrect) Icons.Default.CheckCircle
                                                            else if (isWrong) Icons.Default.Cancel
                                                            else Icons.Default.RadioButtonUnchecked,
                                                            null,
                                                            tint     = when { isCorrect -> Color(0xFF22C55E); isWrong -> Color(0xFFEF4444); else -> Divider },
                                                            modifier = Modifier.size(20.dp),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // ── Boşluk doldur ─────────────────────────────────
                            "fill" -> item {
                                val isCorrectFill = fillAnswer.trim().equals(ex.answer.trim(), ignoreCase = true)
                                OutlinedTextField(
                                    value         = fillAnswer,
                                    onValueChange = { if (!showResult) fillAnswer = it },
                                    placeholder   = { Text(Strings.typeAnswer(language), color = Muted) },
                                    modifier      = Modifier.fillMaxWidth(),
                                    shape         = RoundedCornerShape(12.dp),
                                    singleLine    = true,
                                    readOnly      = showResult,
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor      = if (showResult && isCorrectFill) Color(0xFF22C55E) else if (showResult) Color(0xFFEF4444) else Primary,
                                        unfocusedBorderColor    = if (showResult && isCorrectFill) Color(0xFF22C55E) else if (showResult) Color(0xFFEF4444) else Divider,
                                        focusedTextColor        = OnBackground,
                                        unfocusedTextColor      = OnBackground,
                                        unfocusedContainerColor = HeftSurface,
                                        focusedContainerColor   = HeftSurface,
                                    ),
                                )
                                Spacer(Modifier.height(12.dp))
                                if (!showResult) {
                                    Button(
                                        onClick  = {
                                            showResult = true
                                            if (fillAnswer.trim().equals(ex.answer.trim(), ignoreCase = true)) correctCount++
                                        },
                                        enabled  = fillAnswer.isNotBlank(),
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                                    ) { Text("Kontrol Et", color = Color.White, fontWeight = FontWeight.ExtraBold) }
                                } else if (!isCorrectFill) {
                                    // Yanlışsa tekrar deneme imkanı ver
                                    OutlinedButton(
                                        onClick  = {
                                            fillAnswer = ""
                                            showResult = false
                                        },
                                        modifier = Modifier.fillMaxWidth().height(48.dp),
                                        shape    = RoundedCornerShape(12.dp),
                                        border   = BorderStroke(2.dp, Primary),
                                    ) {
                                        Icon(Icons.Default.Refresh, null, tint = Primary, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Tekrar Dene", color = Primary, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // ── Eşleştir ──────────────────────────────────────
                            "match" -> item {
                                MatchExercise(
                                    pairs     = ex.pairs,
                                    onCorrect = { correctCount++ },
                                    onAllDone = { showResult = true },
                                )
                            }

                            // ── Cümle kur ─────────────────────────────────────
                            "build" -> item {
                                BuildExercise(
                                    question  = ex.question,
                                    words     = ex.words,
                                    tr        = ex.tr,
                                    language  = language,
                                    onChecked = { isOk ->
                                        buildResult = isOk
                                        if (isOk) correctCount++
                                    },
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

// ── Cümle kurma egzersizi (build) — web temasıyla birebir aynı mantık ──────────
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BuildExercise(
    question : String  = "",
    words    : List<String>,
    tr       : String,
    language : String  = "tr",
    onChecked: (Boolean) -> Unit,
) {
    // Bank: words'ü karıştır — her kelime kaç kez varsa o kadar görünür
    val bank    = remember(words) { words.shuffled() }
    // placed: kullanıcının seçtiği kelimeler (bank index bazlı takip)
    val usedIdx = remember { mutableStateListOf<Int>() }
    val placed  = remember { mutableStateListOf<String>() }
    var checked by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(false) }

    Column(
        modifier            = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Soru başlığı
        if (question.isNotBlank()) {
            Text(
                question,
                fontWeight = FontWeight.ExtraBold,
                color      = OnBackground,
                fontSize   = 20.sp,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            )
        }
        // Türkçe ipucu — web: .kp-build-tr
        if (tr.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SurfaceVar,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text("🇹🇷 ", fontSize = 14.sp)
                    Text(tr, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // Yerleştirme alanı — web: #kpBuildArea
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    when {
                        !checked -> HeftSurface
                        correct  -> Color(0xFF22C55E).copy(0.08f)
                        else     -> Color(0xFFEF4444).copy(0.08f)
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
                .heightIn(min = 56.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (placed.isEmpty()) {
                Text(Strings.tapInOrder(language), color = Muted, fontSize = 13.sp)
            } else {
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement   = Arrangement.spacedBy(7.dp),
                ) {
                    placed.forEachIndexed { i, word ->
                        // Tıklayınca geri al
                        Surface(
                            modifier = Modifier.clickable(enabled = !checked) {
                                placed.removeAt(i)
                                // Bu kelimenin en son kullanılan bank index'ini serbest bırak
                                val bankIdx = usedIdx.lastOrNull { bank.getOrNull(it) == word }
                                if (bankIdx != null) usedIdx.remove(bankIdx)
                            },
                            shape  = RoundedCornerShape(9.dp),
                            color  = Primary.copy(0.15f),
                            border = BorderStroke(2.dp, Primary),
                        ) {
                            Text(
                                word,
                                color      = Primary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize   = 14.sp,
                                modifier   = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Kelime bankası — web: #kpBuildBank
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            bank.forEachIndexed { idx, word ->
                val isUsed = idx in usedIdx
                Surface(
                    modifier = Modifier
                        .alpha(if (isUsed) 0.2f else 1f)
                        .clickable(enabled = !checked && !isUsed) {
                            placed.add(word)
                            usedIdx.add(idx)
                        },
                    shape  = RoundedCornerShape(10.dp),
                    color  = HeftSurface,
                    border = BorderStroke(2.dp, if (isUsed) Divider.copy(alpha = 0.3f) else Divider),
                ) {
                    Text(
                        word,
                        color      = OnBackground,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize   = 14.sp,
                        modifier   = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Kontrol Et butonu — sadece tüm kelimeler seçildiğinde aktif
        if (!checked) {
            Button(
                onClick = {
                    // Web temasıyla aynı: given.trim().toLowerCase() === expected.trim().toLowerCase()
                    val given    = placed.joinToString(" ").trim().lowercase()
                    val expected = words.joinToString(" ").trim().lowercase()
                    correct = given == expected
                    checked = true
                    onChecked(correct)
                },
                enabled  = placed.size == words.size,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
            ) {
                Text(Strings.checkAnswer(language).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, letterSpacing = 0.5.sp)
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
            Text(Strings.kurdiDict(language), fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text(Strings.comingSoon(language), color = Muted, fontSize = 14.sp)
        }
    }
}

// ── Dilbilgisi sekmesi ───────────────────────────────────────────────────────
@Composable
private fun GrammarTab(language: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("🎓", fontSize = 48.sp)
            Text(Strings.kurdiGrammar(language), fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text(Strings.comingSoon(language), color = Muted, fontSize = 14.sp)
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
            Text(Strings.aiLessonTitle(language), fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
            Text(Strings.aiLessonDesc(language), color = Muted, fontSize = 12.sp)
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
                label       = { Text(Strings.topicHintLabel(language), color = Muted, fontSize = 12.sp) },
                placeholder = { Text(Strings.topicHint(language), color = Muted, fontSize = 12.sp) },
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
                Text(if (aiLoading) Strings.aiGenerating(language) else Strings.aiGenerate(language), color = Color.White, fontWeight = FontWeight.Bold)
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
