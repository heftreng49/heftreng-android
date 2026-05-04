package com.heftreng.app.ui.screens.kurdi

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.heftreng.app.data.model.KurdiLesson
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.KurdiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(
    language : String = "tr",
    vm       : KurdiViewModel = hiltViewModel(),
) {
    // collectAsState ile null gelmez — MutableStateFlow emptyList() ile başlıyor
    val lessons  by vm.lessons.collectAsState()
    val xp       by vm.xp.collectAsState()
    val streak   by vm.streak.collectAsState()
    val level    by vm.level.collectAsState()
    val loading  by vm.loading.collectAsState()

    var selectedTab by remember { mutableStateOf(0) }
    val tabs = if (language == "ku")
        listOf("Ders", "Ferheng", "Rêziman", "AI Ders")
    else
        listOf("Dersler", "Ferheng", "Rêziman", "AI Ders")

    Column(modifier = Modifier.fillMaxSize().background(Background)) {

        // Başlık
        Text(
            "Kurdî Fêrbibe",
            fontWeight = FontWeight.Bold,
            color      = OnBackground,
            fontSize   = 18.sp,
            modifier   = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        )

        // XP & Streak kartı
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            color = HeftSurface,
        ) {
            Row(
                modifier            = Modifier.padding(14.dp),
                verticalAlignment   = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (language == "ku") "Asta $level" else "Seviye $level",
                        fontWeight = FontWeight.Bold,
                        color      = Primary,
                        fontSize   = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    val progress = if (xp <= 0) 0f else ((xp % 100) / 100f).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress   = { progress },
                        modifier   = Modifier.fillMaxWidth().height(7.dp),
                        color      = Primary,
                        trackColor = SurfaceVar,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("$xp XP", color = Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.width(16.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔥", fontSize = 24.sp)
                    Text(
                        "$streak",
                        fontWeight = FontWeight.Black,
                        color      = Primary,
                        fontSize   = 13.sp,
                    )
                    Text("Streak", color = Muted, fontSize = 10.sp)
                }
            }
        }

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
                    text                   = {
                        Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    },
                    selectedContentColor   = Primary,
                    unselectedContentColor = Muted,
                )
            }
        }

        // İçerik
        when (selectedTab) {
            0    -> LessonsTab(lessons, loading, vm)
            1    -> DictionaryTab(language)
            2    -> GrammarTab(language)
            3    -> AiLessonTab(language, vm)
            else -> LessonsTab(lessons, loading, vm)
        }
    }
}

@Composable
fun LessonsTab(
    lessons : List<KurdiLesson>,
    loading : Boolean,
    vm      : KurdiViewModel,
) {
    when {
        loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        }
        lessons.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📚", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text("Ders bulunamadı", color = Muted, fontSize = 14.sp)
                }
            }
        }
        else -> {
            LazyColumn(
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(lessons, key = { it.id }) { lesson ->
                    LessonCard(lesson) { vm.startLesson(lesson) }
                }
            }
        }
    }
}

@Composable
fun LessonCard(lesson: KurdiLesson, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = HeftSurface,
    ) {
        Row(
            modifier          = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (lesson.completed) Primary else SurfaceVar,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = if (lesson.completed) Icons.Default.CheckCircle
                                        else Icons.Default.Book,
                    contentDescription = null,
                    tint               = if (lesson.completed) Color.White else Muted,
                    modifier           = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    fontWeight = FontWeight.SemiBold,
                    color      = OnBackground,
                    fontSize   = 14.sp,
                )
                if (lesson.subtitle.isNotBlank()) {
                    Text(lesson.subtitle, color = Muted, fontSize = 12.sp)
                }
            }
            if (lesson.xpReward > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = SurfaceVar) {
                    Text(
                        "+${lesson.xpReward} XP",
                        color      = Primary,
                        fontSize   = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DictionaryTab(language: String = "tr") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📖", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Ferheng",
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 18.sp,
            )
            Text(
                if (language == "ku") "Zû tê" else "Yakında",
                color    = Muted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun GrammarTab(language: String = "tr") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎓", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Rêziman",
                fontWeight = FontWeight.Bold,
                color      = OnBackground,
                fontSize   = 18.sp,
            )
            Text(
                if (language == "ku") "Zû tê" else "Yakında",
                color    = Muted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun AiLessonTab(language: String = "tr", vm: KurdiViewModel = hiltViewModel()) {
    val aiLesson  by vm.aiLesson.collectAsState()
    val aiLoading by vm.aiLoading.collectAsState()
    val aiError   by vm.aiError.collectAsState()
    var apiKey by remember { mutableStateOf("") }
    var topic  by remember { mutableStateOf("") }
    var level  by remember { mutableStateOf("destpêk") }
    val levels = listOf("destpêk" to "🌱 Başlangıç", "navîn" to "🌿 Orta", "pêşketî" to "🌳 İleri")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("AI ile Kurdî Ders", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 16.sp)
            Text("OpenRouter API anahtarını gir.", color = Muted, fontSize = 12.sp)
        }
        item {
            OutlinedTextField(value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API Key", color = Muted, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
        }
        item {
            OutlinedTextField(value = topic, onValueChange = { topic = it },
                label = { Text(if (language=="ku") "Mijar" else "Konu", color = Muted, fontSize = 12.sp) },
                placeholder = { Text("Renkler, Sayılar…", color = Muted, fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Primary, unfocusedBorderColor = SurfaceVar,
                    focusedTextColor = OnBackground, unfocusedTextColor = OnBackground))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                levels.forEach { (lv, label) ->
                    val sel = level == lv
                    Surface(modifier = Modifier.clickable { level = lv },
                        shape = RoundedCornerShape(20.dp), color = if (sel) Primary else SurfaceVar) {
                        Text(label, color = if (sel) Color.White else Muted, fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                    }
                }
            }
        }
        item {
            Button(onClick = { vm.generateAiLesson(apiKey, topic, level) },
                enabled = !aiLoading && apiKey.isNotBlank() && topic.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp)) {
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
                                Text("• $opt", color = if (opt==ex.answer) Primary else OnBackground,
                                    fontSize = 12.sp, fontWeight = if (opt==ex.answer) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
            item { TextButton(onClick = { vm.clearAiLesson() }) { Text("Temizle", color = Muted) } }
        }
    }
}
