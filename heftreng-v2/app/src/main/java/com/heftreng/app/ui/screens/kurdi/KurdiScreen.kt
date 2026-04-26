package com.heftreng.app.ui.screens.kurdi

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
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    val xp      by vm.xp.collectAsState()
    val streak  by vm.streak.collectAsState()
    val level   by vm.level.collectAsState()
    val loading by vm.loading.collectAsState()
    var tab     by remember { mutableStateOf(0) }
    val tabs = listOf("Dersler", "Ferheng", "Rêziman")

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title  = { Text("Kurdî Fêrbibe", fontWeight = FontWeight.Bold, color = onBg()) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // XP & Streak kartı
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape    = RoundedCornerShape(14.dp),
                color    = surf(),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seviye $level", fontWeight = FontWeight.Bold, color = accent(), fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress   = { (xp % 100) / 100f },
                            modifier   = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                            color      = accent(),
                            trackColor = surfVar(),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$xp XP kazanıldı", color = muted(), fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥", fontSize = 26.sp)
                        Text("$streak", fontWeight = FontWeight.Black, color = accent(), fontSize = 14.sp)
                        Text("Streak", color = muted(), fontSize = 10.sp)
                    }
                }
            }

            TabRow(
                selectedTabIndex = tab,
                containerColor   = bg(),
                contentColor     = accent(),
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[tab]),
                        color    = accent(),
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected             = tab == i,
                        onClick              = { tab = i },
                        text                 = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        selectedContentColor = accent(),
                        unselectedContentColor = muted(),
                    )
                }
            }

            when (tab) {
                0 -> LessonsTab(lessons, loading) { lesson ->
                    navController.navigate(Screen.LessonDetail.go(lesson.id))
                }
                1 -> ComingSoonTab("📚", "Ferheng", "Sözlük yakında!")
                2 -> ComingSoonTab("🎓", "Rêziman", "Dilbilgisi yakında!")
            }
        }
    }
}

@Composable
fun LessonsTab(lessons: List<KurdiLesson>, loading: Boolean, onClick: (KurdiLesson) -> Unit) {
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = accent())
        }
        return
    }
    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(lessons, key = { it.id }) { lesson ->
            LessonCard(lesson) { onClick(lesson) }
        }
    }
}

@Composable
fun LessonCard(lesson: KurdiLesson, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape    = RoundedCornerShape(14.dp),
        color    = surf(),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        if (lesson.completed) accent() else surfVar(),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (lesson.completed) Icons.Default.CheckCircle else Icons.Default.Book,
                    null,
                    tint     = if (lesson.completed) Color.Black else muted(),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(lesson.title, fontWeight = FontWeight.SemiBold, color = onBg(), fontSize = AppFontSize.sp)
                if (lesson.subtitle.isNotBlank())
                    Text(lesson.subtitle, color = muted(), fontSize = (AppFontSize - 2).sp)
            }
            if (lesson.xpReward > 0) {
                Surface(shape = RoundedCornerShape(8.dp), color = surfVar()) {
                    Text(
                        "+${lesson.xpReward} XP",
                        color    = accent(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ComingSoonTab(emoji: String, title: String, subtitle: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, color = onBg(), fontSize = 18.sp)
            Text(subtitle, color = muted(), fontSize = 14.sp)
        }
    }
}

// ════════════════════════════════════════════════════════════
//  LESSON DETAIL
// ════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LessonDetailScreen(
    lessonId     : String,
    navController: NavController,
    vm           : KurdiViewModel = hiltViewModel(),
) {
    val lessons by vm.lessons.collectAsState()
    val lesson  = lessons.find { it.id == lessonId }

    Scaffold(
        containerColor = bg(),
        topBar = {
            TopAppBar(
                title          = { Text(lesson?.title ?: "Ders", color = onBg(), fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = onBg())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg()),
            )
        }
    ) { padding ->
        if (lesson == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent())
            }
            return@Scaffold
        }

        Column(
            modifier            = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("📖", fontSize = 56.sp)
            Text(lesson.title, fontWeight = FontWeight.Bold, color = onBg(), fontSize = 22.sp)
            if (lesson.subtitle.isNotBlank())
                Text(lesson.subtitle, color = muted(), fontSize = 15.sp)

            Spacer(Modifier.height(8.dp))

            Surface(shape = RoundedCornerShape(12.dp), color = surfVar()) {
                Column(
                    modifier            = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InfoRow("Ders Türü", lesson.type.uppercase())
                    InfoRow("XP Ödülü", "+${lesson.xpReward} XP")
                    InfoRow("Durum", if (lesson.completed) "✓ Tamamlandı" else "Başlanmadı")
                }
            }

            Spacer(Modifier.weight(1f))

            if (!lesson.completed) {
                Button(
                    onClick  = {
                        vm.completeLesson(lessonId)
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = accent(), contentColor = Color.Black),
                ) {
                    Text(
                        "Dersê Biqedîne / Tamamla",
                        fontWeight = FontWeight.SemiBold,
                        modifier   = Modifier.padding(vertical = 4.dp),
                    )
                }
            } else {
                Surface(shape = RoundedCornerShape(12.dp), color = accent().copy(alpha = 0.15f)) {
                    Row(
                        modifier          = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = accent(), modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Temam / Qediya", color = accent(), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = muted(), fontSize = 13.sp)
        Text(value, color = onBg(), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}
