package com.heftreng.app.ui.screens.kurdi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.heftreng.app.data.model.KurdiLesson
import com.heftreng.app.navigation.Screen
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.KurdiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(
    navController: NavController,
    vm: KurdiViewModel = hiltViewModel(),
) {
    val lessons by vm.lessons.collectAsState()
    val xp by vm.xp.collectAsState()
    val streak by vm.streak.collectAsState()
    val level by vm.level.collectAsState()
    val loading by vm.loading.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dersler", "Ferheng", "Reziman")

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Kurdi Ferbibe", fontWeight = FontWeight.Bold, color = OnBackground) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = Surface,
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seviye $level", fontWeight = FontWeight.Bold, color = Amber, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (xp % 100) / 100f },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Amber, trackColor = SurfaceVar,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$xp XP", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("\uD83D\uDD25", fontSize = 24.sp)
                        Text("$streak", fontWeight = FontWeight.Black, color = Amber, fontSize = 13.sp)
                        Text("Streak", color = Muted, fontSize = 10.sp)
                    }
                }
            }
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Background, contentColor = Amber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Amber,
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i, onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        selectedContentColor = Amber, unselectedContentColor = Muted,
                    )
                }
            }
            when (selectedTab) {
                0 -> LessonsTab(lessons, loading) { lesson ->
                    navController.navigate(Screen.LessonDetail.go(lesson.id))
                }
                1 -> DictionaryTab()
                2 -> GrammarTab()
            }
        }
    }
}

@Composable
fun LessonsTab(lessons: List<KurdiLesson>, loading: Boolean, onStart: (KurdiLesson) -> Unit) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Amber) }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(lessons, key = { it.id }) { lesson ->
            LessonCard(lesson = lesson, onClick = { onStart(lesson) })
        }
    }
}

@Composable
fun LessonCard(lesson: KurdiLesson, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp), color = Surface,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (lesson.completed) Amber else SurfaceVar, RoundedCornerShape(12.dp)
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (lesson.completed) Icons.Default.CheckCircle else Icons.Default.Book,
                    contentDescription = null,
                    tint = if (lesson.completed) Color.Black else Muted,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, fontWeight = FontWeight.SemiBold, color = OnBackground, fontSize = 14.sp)
                Text(lesson.subtitle, color = Muted, fontSize = 12.sp)
            }
            if (lesson.xpReward > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVar) {
                    Text("+${lesson.xpReward} XP", color = Amber, fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

data class LessonQuestion(val question: String, val options: List<String>, val correct: String)

fun buildQuestions(lesson: KurdiLesson): List<LessonQuestion> = when (lesson.title) {
    "Silav u Nasin", "Silav \u00fb Nas\u00een" -> listOf(
        LessonQuestion("Merhaba Kurtce?", listOf("Spas", "Silav", "Ere", "Na"), "Silav"),
        LessonQuestion("Nasilsin? Kurtce?", listOf("Tu cawa yi?", "Spas", "Xatire te", "Bas e"), "Tu cawa yi?"),
        LessonQuestion("Tesekkur ederim Kurtce?", listOf("Silav", "Ere", "Spas", "Na"), "Spas"),
        LessonQuestion("Iyi Kurtce?", listOf("Bas", "Xirab", "Silav", "Spas"), "Bas"),
    )
    "Jimare" -> listOf(
        LessonQuestion("1 sayisi Kurtce?", listOf("Du", "Yek", "Se", "Car"), "Yek"),
        LessonQuestion("2 sayisi Kurtce?", listOf("Yek", "Se", "Du", "Penc"), "Du"),
        LessonQuestion("5 sayisi Kurtce?", listOf("Ses", "Heft", "Penc", "Car"), "Penc"),
        LessonQuestion("10 sayisi Kurtce?", listOf("Neh", "Deh", "Hest", "Heft"), "Deh"),
    )
    "Reng" -> listOf(
        LessonQuestion("Kirmizi Kurtce?", listOf("Sin", "Sor", "Kesk", "Spi"), "Sor"),
        LessonQuestion("Mavi Kurtce?", listOf("Sor", "Kesk", "Sin", "Spi"), "Sin"),
        LessonQuestion("Yesil Kurtce?", listOf("Sor", "Kesk", "Sin", "Spi"), "Kesk"),
        LessonQuestion("Beyaz Kurtce?", listOf("Res", "Sor", "Sin", "Spi"), "Spi"),
    )
    "Malbat" -> listOf(
        LessonQuestion("Anne Kurtce?", listOf("Bav", "Bira", "De", "Xweh"), "De"),
        LessonQuestion("Baba Kurtce?", listOf("De", "Bav", "Kur", "Bira"), "Bav"),
        LessonQuestion("Erkek kardes Kurtce?", listOf("Xweh", "De", "Bira", "Bav"), "Bira"),
        LessonQuestion("Kiz kardes Kurtce?", listOf("Bira", "De", "Bav", "Xweh"), "Xweh"),
    )
    else -> listOf(
        LessonQuestion("Evet Kurtce?", listOf("Na", "Ere", "Silav", "Spas"), "Ere"),
        LessonQuestion("Hayir Kurtce?", listOf("Ere", "Spas", "Na", "Bas"), "Na"),
        LessonQuestion("Su Kurtce?", listOf("Nan", "Av", "Cay", "Mast"), "Av"),
        LessonQuestion("Ekmek Kurtce?", listOf("Av", "Cay", "Nan", "Run"), "Nan"),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lessonId: String,
    navController: NavController,
    vm: KurdiViewModel = hiltViewModel(),
) {
    val lessons by vm.lessons.collectAsState()
    val lesson = lessons.firstOrNull { it.id == lessonId }

    if (lesson == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val questions = remember(lesson) { buildQuestions(lesson) }
    var currentQ by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<String?>(null) }
    var answered by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lesson.title, fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 15.sp)
                        Text(lesson.subtitle, color = Muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        }
    ) { padding ->
        if (finished) {
            FinishScreen(correct, questions.size, lesson.xpReward) {
                vm.completeLesson(lessonId)
                navController.popBackStack()
            }
            return@Scaffold
        }
        val q = questions.getOrNull(currentQ) ?: return@Scaffold
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LinearProgressIndicator(
                progress = { (currentQ + 1f) / questions.size },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                color = Amber, trackColor = SurfaceVar,
            )
            Text("${currentQ + 1} / ${questions.size}", color = Muted, fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Surface) {
                Text(q.question, color = OnBackground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                    lineHeight = 26.sp, modifier = Modifier.padding(20.dp), textAlign = TextAlign.Center)
            }
            q.options.forEach { option ->
                val bgColor = when {
                    !answered -> if (selected == option) Amber.copy(alpha = 0.2f) else SurfaceVar
                    option == q.correct -> Color(0xFF10B981).copy(alpha = 0.25f)
                    option == selected && option != q.correct -> Color(0xFFEF4444).copy(alpha = 0.25f)
                    else -> SurfaceVar
                }
                val borderColor = when {
                    !answered -> if (selected == option) Amber else Divider
                    option == q.correct -> Color(0xFF10B981)
                    option == selected -> Color(0xFFEF4444)
                    else -> Divider
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !answered) {
                        selected = option; answered = true
                        if (option == q.correct) correct++
                    },
                    shape = RoundedCornerShape(12.dp), color = bgColor,
                    border = BorderStroke(1.5.dp, borderColor),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (answered) {
                            Icon(
                                if (option == q.correct) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                contentDescription = null,
                                tint = if (option == q.correct) Color(0xFF10B981)
                                       else if (option == selected) Color(0xFFEF4444) else Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(option, color = OnBackground, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            if (answered) {
                Button(
                    onClick = {
                        if (currentQ < questions.size - 1) { currentQ++; selected = null; answered = false }
                        else finished = true
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) {
                    Text(if (currentQ < questions.size - 1) "Devam" else "Bitir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun FinishScreen(correct: Int, total: Int, xpEarned: Int, onComplete: () -> Unit) {
    val pct = if (total > 0) correct * 100 / total else 0
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(if (pct >= 60) "\uD83C\uDF89" else "\uD83D\uDCAA", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(if (pct >= 60) "Harika!" else "Tekrar dene!",
            fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 20.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("$correct / $total dogru", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(16.dp))
        Surface(shape = RoundedCornerShape(12.dp), color = Surface) {
            Text("+$xpEarned XP", color = Amber, fontWeight = FontWeight.Black, fontSize = 24.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        }
        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
        ) {
            Text("Geri Don", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DictionaryTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCDA", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Ferheng", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text("Yakinda", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun GrammarTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCD6", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Reziman", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text("Yakinda", color = Muted, fontSize = 14.sp)
        }
    }
}
