package com.heftreng.app.ui.screens.kurdi

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.data.model.KurdiExercise
import com.heftreng.app.data.model.KurdiLesson
import com.heftreng.app.data.model.KurdiWord
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.KurdiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(
    language: String = "tr",
    vm      : KurdiViewModel = hiltViewModel(),
) {
    val lessons      by vm.lessons.collectAsState()
    val loading      by vm.loading.collectAsState()
    val xp           by vm.xp.collectAsState()
    val level        by vm.level.collectAsState()
    val streak       by vm.streak.collectAsState()
    val activeLesson by vm.activeLesson.collectAsState()
    val lessonDone   by vm.lessonDone.collectAsState()
    val exercises    by vm.exercises.collectAsState()
    val exIndex      by vm.exerciseIndex.collectAsState()

    var selectedTab  by remember { mutableStateOf(0) }
    val tabs = if (language == "ku")
        listOf("Ders", "Ferheng", "Rêziman")
    else
        listOf("Dersler", "Ferheng", "Rêziman")

    // Aktif ders ekranı
    if (activeLesson != null && !lessonDone) {
        LessonScreen(
            lesson   = activeLesson!!,
            exercises= exercises,
            index    = exIndex,
            vm       = vm,
            language = language,
        )
        return
    }

    // Ders tamamlandı ekranı
    if (lessonDone) {
        LessonCompleteScreen(
            lesson   = activeLesson!!,
            vm       = vm,
            language = language,
        )
        return
    }

    // Ana ekran
    Column(Modifier.fillMaxSize().background(Background)) {
        // Header — XP, streak
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0B3C), Surface)))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Column {
                Text(
                    "Kurdî Fêrbibe",
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color      = Color.White,
                )
                Text(
                    if (language == "ku") "Zimanê xwe fêr bibe!" else "Kürtçe öğren!",
                    fontSize = 13.sp,
                    color    = PrimaryVar.copy(0.8f),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    KurdiStat(icon = "⚡", value = "$xp XP", label = "Lv.$level")
                    if (streak > 0) KurdiStat(icon = "🔥", value = streak.toString(), label = if (language == "ku") "Rêz" else "Seri")
                    KurdiStat(icon = "✅", value = lessons.count { it.completed }.toString(), label = if (language == "ku") "Qediya" else "Tamamlanan")
                }
                Spacer(Modifier.height(8.dp))
                // XP bar
                val xpInLevel = xp % 100
                LinearProgressIndicator(
                    progress   = { xpInLevel / 100f },
                    color      = Primary,
                    trackColor = Divider,
                    modifier   = Modifier.fillMaxWidth().clip(RoundedCornerShape(99.dp)),
                )
            }
        }

        // Tab bar
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor   = Surface,
            contentColor     = Primary,
            indicator        = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = Primary,
                )
            },
        ) {
            tabs.forEachIndexed { i, label ->
                Tab(
                    selected = selectedTab == i,
                    onClick  = { selectedTab = i },
                    text     = { Text(label, fontSize = 13.sp, color = if (selectedTab == i) Primary else Muted) },
                )
            }
        }

        when (selectedTab) {
            0 -> LessonsTab(lessons = lessons, loading = loading, vm = vm, language = language)
            1 -> FerhengTab(vm = vm, language = language)
            2 -> RezimantTab(language = language)
        }
    }
}

// ─── XP İSTATİSTİK ───────────────────────────────────────────────────────────
@Composable
private fun KurdiStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $value", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
        Text(label, fontSize = 10.sp, color = PrimaryVar.copy(0.7f))
    }
}

// ─── DERSLER SEKMESİ ─────────────────────────────────────────────────────────
@Composable
private fun LessonsTab(
    lessons : List<KurdiLesson>,
    loading : Boolean,
    vm      : KurdiViewModel,
    language: String,
) {
    if (loading) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }
    if (lessons.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📚", fontSize = 48.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (language == "ku") "Ders tune" else "Henüz ders yok",
                    color = Muted, fontSize = 14.sp,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(lessons, key = { _, l -> l.id }) { index, lesson ->
            LessonCard(
                lesson   = lesson,
                index    = index,
                language = language,
                onClick  = { vm.startLesson(lesson) },
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

// ─── DERS KARTI ──────────────────────────────────────────────────────────────
@Composable
private fun LessonCard(
    lesson  : KurdiLesson,
    index   : Int,
    language: String,
    onClick : () -> Unit,
) {
    val typeEmoji = mapOf(
        "mcq"   to "🔤",
        "fill"  to "✏️",
        "build" to "🔧",
        "match" to "🔗",
    )

    Card(
        onClick   = onClick,
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(
            containerColor = if (lesson.completed) Color(0xFF0D2B1A) else Surface,
        ),
        shape     = RoundedCornerShape(14.dp),
        border    = BorderStroke(
            1.dp,
            if (lesson.completed) OkColor.copy(0.4f) else Divider,
        ),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Numara veya tamamlandı ikonu
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (lesson.completed)
                            Brush.linearGradient(listOf(OkColor, Color(0xFF16A34A)))
                        else
                            Brush.linearGradient(listOf(Primary, Accent))
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (lesson.completed) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(22.dp))
                } else {
                    Text(
                        typeEmoji[lesson.type] ?: "📖",
                        fontSize = 18.sp,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title.ifEmpty { "Ders ${index + 1}" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = OnBackground,
                )
                if (lesson.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(lesson.subtitle, fontSize = 12.sp, color = Muted)
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LessonChip("⚡ ${lesson.xpReward} XP")
                    LessonChip(lesson.type.uppercase())
                }
            }

            if (!lesson.completed) {
                Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun LessonChip(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(SurfaceVar)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, fontSize = 10.sp, color = Muted)
    }
}

// ─── DERS EKRANI ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonScreen(
    lesson   : KurdiLesson,
    exercises: List<KurdiExercise>,
    index    : Int,
    vm       : KurdiViewModel,
    language : String,
) {
    val lessonLoading by vm.lessonLoading.collectAsState()

    if (lessonLoading || exercises.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Background), Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    val exercise = exercises.getOrNull(index) ?: return
    val progress = (index + 1).toFloat() / exercises.size.toFloat()

    Column(Modifier.fillMaxSize().background(Background)) {
        // Üst bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(Surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { vm.resetLesson() }) {
                Icon(Icons.Default.Close, null, tint = Muted)
            }
            LinearProgressIndicator(
                progress   = { progress },
                color      = Primary,
                trackColor = Divider,
                modifier   = Modifier.weight(1f).clip(RoundedCornerShape(99.dp)),
            )
            Spacer(Modifier.width(8.dp))
            Text("${index + 1}/${exercises.size}", fontSize = 12.sp, color = Muted)
        }

        // Egzersiz
        ExerciseView(
            exercise = exercise,
            language = language,
            onCorrect = { vm.nextExercise() },
        )
    }
}

// ─── EGZERSİZ GÖRÜNÜMÜ ───────────────────────────────────────────────────────
@Composable
private fun ExerciseView(
    exercise : KurdiExercise,
    language : String,
    onCorrect: () -> Unit,
) {
    var selected   by remember(exercise.id) { mutableStateOf<String?>(null) }
    var isCorrect  by remember(exercise.id) { mutableStateOf<Boolean?>(null) }
    var inputText  by remember(exercise.id) { mutableStateOf("") }
    var buildWords by remember(exercise.id) { mutableStateOf(listOf<String>()) }

    // Seçenekleri karıştır
    val options = remember(exercise.id) {
        (exercise.wrong + exercise.answer).shuffled()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // Soru
        Text(
            exercise.question,
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
            color      = OnBackground,
            textAlign  = TextAlign.Center,
        )
        if (exercise.tr.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(exercise.tr, fontSize = 14.sp, color = Muted, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(32.dp))

        when (exercise.type) {
            "mcq" -> {
                // Çoktan seçmeli
                options.forEach { opt ->
                    val color = when {
                        isCorrect == null              -> Divider
                        opt == exercise.answer         -> OkColor
                        opt == selected && isCorrect == false -> ErrorColor
                        else                           -> Divider
                    }
                    OutlinedButton(
                        onClick = {
                            if (isCorrect != null) return@OutlinedButton
                            selected  = opt
                            isCorrect = opt == exercise.answer
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape    = RoundedCornerShape(12.dp),
                        border   = BorderStroke(1.5.dp, color),
                        colors   = ButtonDefaults.outlinedButtonColors(
                            contentColor = OnBackground,
                        ),
                    ) {
                        Text(opt, fontSize = 15.sp)
                    }
                }
            }
            "fill" -> {
                // Boşluk doldur
                OutlinedTextField(
                    value         = inputText,
                    onValueChange = { if (isCorrect == null) inputText = it },
                    modifier      = Modifier.fillMaxWidth(),
                    placeholder   = { Text(if (language == "ku") "Bersivê binivîse..." else "Cevabı yaz...", color = Muted) },
                    singleLine    = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = if (isCorrect == true) OkColor else if (isCorrect == false) ErrorColor else Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor     = OnBackground,
                        unfocusedTextColor   = OnBackground,
                        cursorColor          = Primary,
                    ),
                )
                Spacer(Modifier.height(12.dp))
                if (isCorrect == null) {
                    Button(
                        onClick = {
                            isCorrect = inputText.trim().lowercase() == exercise.answer.trim().lowercase()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape    = RoundedCornerShape(12.dp),
                    ) {
                        Text(if (language == "ku") "Kontrol bike" else "Kontrol Et")
                    }
                }
            }
            "build" -> {
                // Cümle kur
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVar)
                        .border(1.dp, if (isCorrect == true) OkColor else if (isCorrect == false) ErrorColor else Divider, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        buildWords.joinToString(" ").ifEmpty { "..." },
                        color = OnBackground, fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(16.dp))
                // Kelimeler
                val availableWords = remember(exercise.id) { exercise.words.shuffled() }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement   = Arrangement.spacedBy(8.dp),
                ) {
                    availableWords.forEach { word ->
                        val used = buildWords.contains(word)
                        OutlinedButton(
                            onClick = {
                                if (isCorrect != null) return@OutlinedButton
                                buildWords = if (used) buildWords - word else buildWords + word
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (used) Primary else SurfaceVar,
                                contentColor   = if (used) Color.White else OnBackground,
                            ),
                            border = BorderStroke(1.dp, if (used) Primary else Divider),
                            shape  = RoundedCornerShape(99.dp),
                        ) {
                            Text(word, fontSize = 14.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (isCorrect == null) {
                    Button(
                        onClick = {
                            val ans = buildWords.joinToString(" ").trim().lowercase()
                            isCorrect = ans == exercise.answer.trim().lowercase()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape    = RoundedCornerShape(12.dp),
                        enabled  = buildWords.isNotEmpty(),
                    ) {
                        Text(if (language == "ku") "Kontrol bike" else "Kontrol Et")
                    }
                }
            }
        }

        // Geri bildirim + devam
        isCorrect?.let { correct ->
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (correct) OkColor.copy(0.15f) else ErrorColor.copy(0.15f))
                    .border(1.dp, if (correct) OkColor else ErrorColor, RoundedCornerShape(12.dp))
                    .padding(14.dp)
            ) {
                Column {
                    Text(
                        if (correct) (if (language == "ku") "✅ Rast!" else "✅ Doğru!")
                        else         (if (language == "ku") "❌ Xelet" else "❌ Yanlış"),
                        color      = if (correct) OkColor else ErrorColor,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                    )
                    if (!correct) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${if (language == "ku") "Bersiva rast:" else "Doğru cevap:"} ${exercise.answer}",
                            color   = OnSurface,
                            fontSize= 13.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick  = onCorrect,
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (correct) OkColor else Primary,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (language == "ku") "Berdewam bike" else "Devam Et")
            }
        }

        Spacer(Modifier.height(40.dp))
    }
}

// ─── DERS TAMAMLANDI ─────────────────────────────────────────────────────────
@Composable
private fun LessonCompleteScreen(
    lesson  : KurdiLesson,
    vm      : KurdiViewModel,
    language: String,
) {
    val xp by vm.xp.collectAsState()

    LaunchedEffect(lesson.id) {
        vm.completeLesson(lesson.id, lesson.xpReward)
    }

    Box(
        Modifier.fillMaxSize().background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text("🎉", fontSize = 72.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                if (language == "ku") "Derse Qediya!" else "Ders Tamamlandı!",
                fontSize   = 26.sp,
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "+${lesson.xpReward} XP",
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = Primary,
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick  = { vm.resetLesson() },
                modifier = Modifier.fillMaxWidth(),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary),
                shape    = RoundedCornerShape(99.dp),
            ) {
                Text(
                    if (language == "ku") "Vegere Dersan" else "Derslere Dön",
                    fontSize = 15.sp,
                )
            }
        }
    }
}

// ─── FERHENG SEKMESİ ─────────────────────────────────────────────────────────
@Composable
private fun FerhengTab(vm: KurdiViewModel, language: String) {
    val vocab   by vm.vocabList.collectAsState()
    var filter  by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { vm.loadVocab() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value         = filter,
            onValueChange = { filter = it },
            modifier      = Modifier.fillMaxWidth().padding(12.dp),
            placeholder   = {
                Text(if (language == "ku") "Bêje bigere..." else "Kelime ara...", color = Muted)
            },
            leadingIcon   = { Icon(Icons.Default.Search, null, tint = Muted) },
            singleLine    = true,
            shape         = RoundedCornerShape(99.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = Primary,
                unfocusedBorderColor = Divider,
                focusedTextColor     = OnBackground,
                unfocusedTextColor   = OnBackground,
                cursorColor          = Primary,
            ),
        )

        val filtered = vocab.filter {
            filter.isBlank() ||
            it.ku.contains(filter, true) ||
            it.tr.contains(filter, true) ||
            it.kp.contains(filter, true)
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text(if (language == "ku") "Bêje tune" else "Kelime bulunamadı", color = Muted)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { word ->
                    VocabCard(word, language)
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun VocabCard(word: KurdiWord, language: String) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        colors    = CardDefaults.cardColors(containerColor = Surface),
        shape     = RoundedCornerShape(10.dp),
        border    = BorderStroke(1.dp, Divider),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(word.ku, fontWeight = FontWeight.Bold, color = Primary, fontSize = 15.sp)
                if (word.kp.isNotEmpty())
                    Text("[${word.kp}]", fontSize = 11.sp, color = Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(word.tr, color = OnBackground, fontSize = 14.sp)
                if (word.e.isNotEmpty())
                    Text(word.e, fontSize = 11.sp, color = Muted)
            }
        }
    }
}

// ─── RÊZİMAN SEKMESİ ─────────────────────────────────────────────────────────
@Composable
private fun RezimantTab(language: String) {
    val topics = listOf(
        "Navdêr / İsimler" to "Bijî, mêr, jin, zarrok...",
        "Lêker / Fiiller"  to "Çûn, hatin, xwarin...",
        "Rewşname / Sıfatlar" to "Baş, xirab, mezin, biçûk...",
        "Hejmar / Sayılar" to "Yek, du, sê, çar...",
        "Dem / Zamanlar"   to "Niha, borî, pêş de...",
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(topics) { (title, sub) ->
            Card(
                modifier  = Modifier.fillMaxWidth(),
                colors    = CardDefaults.cardColors(containerColor = Surface),
                shape     = RoundedCornerShape(12.dp),
                border    = BorderStroke(1.dp, Divider),
                elevation = CardDefaults.cardElevation(0.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(title, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 15.sp)
                    Spacer(Modifier.height(2.dp))
                    Text(sub, fontSize = 12.sp, color = Muted)
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
