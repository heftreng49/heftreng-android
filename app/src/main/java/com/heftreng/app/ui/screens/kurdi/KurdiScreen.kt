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
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.KurdiViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiScreen(vm: KurdiViewModel = hiltViewModel()) {
    val lessons by vm.lessons.collectAsState()
    val xp by vm.xp.collectAsState()
    val streak by vm.streak.collectAsState()
    val level by vm.level.collectAsState()
    val loading by vm.loading.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dersler", "Ferheng", "Rêziman")

    Column(modifier = Modifier.fillMaxSize().background(Background)) {
            // XP & Streak kartı
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = HeftSurface,
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seviye $level", fontWeight = FontWeight.Bold, color = Amber, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { (xp % 100) / 100f },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Amber,
                            trackColor = SurfaceVar,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text("$xp XP", color = Muted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔥", fontSize = 24.sp)
                        Text("$streak", fontWeight = FontWeight.Black, color = Amber, fontSize = 13.sp)
                        Text("Streak", color = Muted, fontSize = 10.sp)
                    }
                }
            }

            // Sekmeler
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Background,
                contentColor = Amber,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Amber,
                    )
                }
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) },
                        selectedContentColor = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            when (selectedTab) {
                0 -> LessonsTab(lessons, loading, vm)
                1 -> DictionaryTab()
                2 -> GrammarTab()
            }
        }
}

@Composable
fun LessonsTab(lessons: List<com.heftreng.app.data.model.KurdiLesson>, loading: Boolean, vm: KurdiViewModel) {
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
            LessonCard(lesson) { vm.startLesson(lesson) }
        }
    }
}

@Composable
fun LessonCard(lesson: com.heftreng.app.data.model.KurdiLesson, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(14.dp),
        color = HeftSurface,
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(
                    if (lesson.completed) Amber else SurfaceVar,
                    RoundedCornerShape(12.dp),
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
                    Text("+${lesson.xpReward} XP", color = Amber, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun DictionaryTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("📚", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Ferheng", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text("Yakında", color = Muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun GrammarTab() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎓", fontSize = 48.sp)
            Spacer(Modifier.height(12.dp))
            Text("Rêziman", fontWeight = FontWeight.Bold, color = OnBackground, fontSize = 18.sp)
            Text("Yakında", color = Muted, fontSize = 14.sp)
        }
    }
}
