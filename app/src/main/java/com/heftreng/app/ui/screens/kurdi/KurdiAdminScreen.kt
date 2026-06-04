package com.heftreng.app.ui.screens.kurdi

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.heftreng.app.ui.theme.*
import com.heftreng.app.viewmodel.*
import com.heftreng.app.viewmodel.AdminViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// ═══════════════════════════════════════════════════════════════════════════════
// KURDİ ADMİN EKRANI — Ders / Kelime / Egzersiz düzenleme
// ═══════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KurdiAdminScreen(
    navController: NavController,
    vm        : KurdiViewModel = hiltViewModel(),
    adminVm   : AdminViewModel = hiltViewModel(),
) {
    val perms   by adminVm.perms.collectAsState()
    val isAdmin  = perms?.isStaff() == true

    // ── Güvenlik: sadece kurdi izni olanlar erişebilir ────────────
    if (perms != null && !isAdmin) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    // Admin dersleri yükle
    LaunchedEffect(Unit) { vm.loadAdminLessons() }

    val lessons by vm.lessons.collectAsState()
    var selectedTab    by remember { mutableIntStateOf(0) }
    var selectedLesson by remember { mutableStateOf<KfLesson?>(null) }

    // Ders seçilmişse düzenleme ekranı
    if (selectedLesson != null) {
        LessonEditScreen(
            lesson = selectedLesson!!,
            onBack = { selectedLesson = null },
        )
        return
    }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kurdî Admin", fontWeight = FontWeight.ExtraBold, color = Amber, fontSize = 18.sp)
                        Text("Ders & İçerik Yönetimi", color = Muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {

            // ── Sekmeler ──────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = Background,
                contentColor     = Amber,
                indicator = { tabs ->
                    Box(Modifier.tabIndicatorOffset(tabs[selectedTab]).height(2.dp).background(Amber))
                },
                divider = { HorizontalDivider(color = Divider, thickness = 0.5.dp) },
            ) {
                listOf("📚 Dersler", "➕ Yeni Ders", "🏛 Üniteler", "🤖 Yapay Zeka", "📥 JSON", "🚩 Raporlar").forEachIndexed { i, t ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = { Text(t, fontSize = 11.sp) },
                        selectedContentColor   = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            when (selectedTab) {
                0 -> LessonListTab(lessons = lessons, vm = vm, onSelect = { selectedLesson = it })
                1 -> NewLessonTab(vm = vm, onCreated = { selectedTab = 0 })
                2 -> UnitManagerTab(vm = vm)
                3 -> AdminAiLessonTab(vm = vm, lessons = lessons)
                4 -> JsonImportTab(vm = vm)
                5 -> ReportsTab(vm = vm)
            }
        }
    }
}

// ── Ders listesi ──────────────────────────────────────────────────────────────
@Composable
private fun LessonListTab(
    lessons  : List<KfLesson>,
    vm       : KurdiViewModel,
    onSelect : (KfLesson) -> Unit,
) {
    if (lessons.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ders bulunamadı", color = Muted)
        }
        return
    }

    var editTarget   by remember { mutableStateOf<KfLesson?>(null) }
    var deleteTarget by remember { mutableStateOf<KfLesson?>(null) }

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lessons, key = { it.id }) { lesson ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(14.dp),
                color    = HeftSurface,
            ) {
                Row(
                    Modifier.padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(lesson.emoji, fontSize = 26.sp)
                    Column(Modifier.weight(1f).clickable { onSelect(lesson) }) {
                        Text(lesson.nameTr, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(lesson.nameKu, color = Muted, fontSize = 12.sp)
                        Text("${lesson.id} • ${lesson.unitId} • ${lesson.xp} XP",
                            color = Muted, fontSize = 10.sp)
                    }
                    // Düzenle
                    IconButton(onClick = { editTarget = lesson }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(18.dp))
                    }
                    // Sil
                    IconButton(onClick = { deleteTarget = lesson }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // ── Ders Düzenleme Dialog ─────────────────────────────────────────────────
    editTarget?.let { lesson ->
        LessonEditDialog(
            lesson    = lesson,
            vm        = vm,
            onDismiss = { editTarget = null },
            onSave    = { nameTr, nameKu, emoji, xp, order, unitId ->
                vm.updateLesson(lesson.id, nameTr, nameKu, emoji, xp, order, unitId,
                    onDone  = { editTarget = null },
                    onError = { editTarget = null },
                )
            },
        )
    }

    // ── Silme Onayı ───────────────────────────────────────────────────────────
    deleteTarget?.let { lesson ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Dersi Sil?", color = OnBackground, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("«${lesson.nameTr}» silinecek.", color = OnBackground)
                    Text("Bu derse ait tüm kelime ve egzersizler de kalıcı olarak silinir.",
                        color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.deleteLesson(lesson.id,
                            onDone  = { deleteTarget = null },
                            onError = { deleteTarget = null },
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text("Sil", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("İptal", color = Muted) }
            },
            containerColor = HeftSurface,
        )
    }
}

// ── Ders Düzenleme Dialog ─────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonEditDialog(
    lesson    : KfLesson,
    vm        : KurdiViewModel,
    onDismiss : () -> Unit,
    onSave    : (String, String, String, Int, Int, String) -> Unit,
) {
    val units  by vm.units.collectAsState()
    var nameTr by remember { mutableStateOf(lesson.nameTr) }
    var nameKu by remember { mutableStateOf(lesson.nameKu) }
    var emoji  by remember { mutableStateOf(lesson.emoji) }
    var xp     by remember { mutableStateOf(lesson.xp.toString()) }
    var order  by remember { mutableStateOf(lesson.order.toString()) }
    var selectedUnit     by remember(units) { mutableStateOf(units.find { it.id == lesson.unitId }) }
    var unitDropExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(emoji, fontSize = 20.sp)
                Text("Dersi Düzenle", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ID: ${lesson.id}", color = Muted, fontSize = 11.sp)
                AdminField(nameTr, { nameTr = it }, "Türkçe Ad *")
                AdminField(nameKu, { nameKu = it }, "Kürtçe Ad")
                AdminField(emoji,  { emoji  = it }, "Emoji")

                // ── Ünite Dropdown ────────────────────────────────────────────
                ExposedDropdownMenuBox(
                    expanded         = unitDropExpanded,
                    onExpandedChange = { unitDropExpanded = !unitDropExpanded },
                ) {
                    OutlinedTextField(
                        value         = selectedUnit?.let { "${it.icon} ${it.ttl}" } ?: "Ünite seç…",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Ünite", fontSize = 12.sp) },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitDropExpanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Amber, unfocusedBorderColor = Divider,
                            focusedTextColor = OnBackground, unfocusedTextColor = OnBackground,
                            unfocusedContainerColor = HeftSurface, focusedContainerColor = HeftSurface,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = unitDropExpanded,
                        onDismissRequest = { unitDropExpanded = false },
                        modifier         = Modifier.background(HeftSurface),
                    ) {
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(unit.icon, fontSize = 16.sp)
                                        Column {
                                            Text(unit.ttl, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(unit.id, color = Muted, fontSize = 10.sp)
                                        }
                                    }
                                },
                                onClick = { selectedUnit = unit; unitDropExpanded = false },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminField(xp,    { xp    = it }, "XP",   keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    AdminField(order, { order = it }, "Sıra", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nameTr.isNotBlank())
                        onSave(nameTr, nameKu, emoji, xp.toIntOrNull() ?: lesson.xp,
                            order.toIntOrNull() ?: lesson.order, selectedUnit?.id ?: lesson.unitId)
                },
                enabled = nameTr.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                shape   = RoundedCornerShape(10.dp),
            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("İptal", color = Muted) } },
        containerColor = HeftSurface,
    )
}

// ── Yeni ders oluştur ─────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewLessonTab(vm: KurdiViewModel, onCreated: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val db      = remember { FirebaseFirestore.getInstance() }
    val units   by vm.units.collectAsState()
    var id      by remember { mutableStateOf("") }
    var nameTr  by remember { mutableStateOf("") }
    var nameKu  by remember { mutableStateOf("") }
    var emoji   by remember { mutableStateOf("📖") }
    var xp      by remember { mutableStateOf("10") }
    var order   by remember { mutableStateOf("1") }
    var saving  by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf("") }
    val snack   = remember { SnackbarHostState() }

    // Ünite dropdown state
    var unitDropExpanded by remember { mutableStateOf(false) }
    var selectedUnit     by remember(units) { mutableStateOf(units.firstOrNull()) }
    // units yüklenince ilk üniteyi otomatik seç
    LaunchedEffect(units) { if (selectedUnit == null && units.isNotEmpty()) selectedUnit = units.first() }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        containerColor = Background,
    ) { pad ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Yeni Ders Oluştur", color = OnBackground, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp) }
            item { AdminField(id, { id = it }, "Ders ID (örn: l7)", hint = "l7") }

            // ── Ünite Seçici ──────────────────────────────────────────────────
            item {
                ExposedDropdownMenuBox(
                    expanded         = unitDropExpanded,
                    onExpandedChange = { unitDropExpanded = !unitDropExpanded },
                ) {
                    OutlinedTextField(
                        value = selectedUnit?.let { "${it.icon} ${it.ttl} (${it.id})" } ?: "Ünite seç…",
                        onValueChange = {},
                        readOnly  = true,
                        label     = { Text("Ünite *", fontSize = 12.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitDropExpanded) },
                        modifier  = Modifier.fillMaxWidth().menuAnchor(),
                        colors    = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = Amber, unfocusedBorderColor = Divider,
                            focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                            unfocusedContainerColor = HeftSurface, focusedContainerColor = HeftSurface,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = unitDropExpanded,
                        onDismissRequest = { unitDropExpanded = false },
                        modifier         = Modifier.background(HeftSurface),
                    ) {
                        if (units.isEmpty()) {
                            DropdownMenuItem(
                                text    = { Text("Henüz ünite yok — önce ünite ekle", color = Muted, fontSize = 12.sp) },
                                onClick = { unitDropExpanded = false },
                            )
                        }
                        units.forEach { unit ->
                            DropdownMenuItem(
                                text = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(unit.icon, fontSize = 18.sp)
                                        Column {
                                            Text(unit.ttl, color = OnBackground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                                            Text(unit.id, color = Muted, fontSize = 10.sp)
                                        }
                                    }
                                },
                                onClick = { selectedUnit = unit; unitDropExpanded = false },
                            )
                        }
                    }
                }
            }

            item { AdminField(nameTr, { nameTr = it }, "Türkçe Ad") }
            item { AdminField(nameKu, { nameKu = it }, "Kürtçe Ad") }
            item { AdminField(emoji, { emoji = it }, "Emoji", hint = "📖") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AdminField(xp, { xp = it }, "XP", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                    AdminField(order, { order = it }, "Sıra", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f))
                }
            }
            if (error.isNotBlank()) item {
                Text(error, color = Color(0xFFEF4444), fontSize = 12.sp)
            }
            item {
                Button(
                    onClick = {
                        if (id.isBlank() || nameTr.isBlank() || selectedUnit == null) {
                            error = "ID, Türkçe ad ve ünite zorunlu"; return@Button
                        }
                        scope.launch {
                            saving = true
                            try {
                                db.collection("kf_lessons").document(id).set(
                                    mapOf(
                                        "id"     to id,
                                        "unitId" to selectedUnit!!.id,
                                        "nameTr" to nameTr,
                                        "nameKu" to nameKu,
                                        "emoji"  to emoji,
                                        "xp"     to (xp.toIntOrNull() ?: 10),
                                        "order"  to (order.toIntOrNull() ?: 1),
                                    )
                                ).await()
                                snack.showSnackbar("✓ Ders oluşturuldu")
                                onCreated()
                            } catch (e: Exception) {
                                error = e.message ?: "Hata"
                            }
                            saving = false
                        }
                    },
                    enabled  = !saving,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                ) { Text(if (saving) "Kaydediliyor..." else "Ders Oluştur", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// DERS DÜZENLEME EKRANI — Kelimeler + Egzersizler
// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LessonEditScreen(lesson: KfLesson, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val db    = remember { FirebaseFirestore.getInstance() }
    var tab   by remember { mutableIntStateOf(0) }

    // Kelimeler
    var vocabs    by remember { mutableStateOf<List<Map<String,Any>>>(emptyList()) }
    // Egzersizler
    var exercises by remember { mutableStateOf<List<Map<String,Any>>>(emptyList()) }
    var loading   by remember { mutableStateOf(true) }
    val snack     = remember { SnackbarHostState() }

    // Yükle
    LaunchedEffect(lesson.id) {
        loading = true
        try {
            val vs = db.collection("kf_vocab").whereEqualTo("lessonId", lesson.id).get().await()
            vocabs = vs.documents.map { doc ->
                (doc.data ?: emptyMap<String,Any>()).toMutableMap().also { it["_docId"] = doc.id }
            }
            val es = db.collection("kf_exercises").whereEqualTo("lessonId", lesson.id).get().await()
            exercises = es.documents.map { doc ->
                (doc.data ?: emptyMap<String,Any>()).toMutableMap().also { it["_docId"] = doc.id }
            }
        } catch (_: Exception) {}
        loading = false
    }

    Scaffold(
        containerColor = Background,
        snackbarHost   = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lesson.emoji, fontSize = 20.sp)
                        Column {
                            Text(lesson.nameTr, fontWeight = FontWeight.ExtraBold, color = OnBackground, fontSize = 15.sp)
                            Text(lesson.nameKu, color = Muted, fontSize = 11.sp)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Background),
            )
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(
                selectedTabIndex = tab,
                containerColor   = Background,
                contentColor     = Amber,
                indicator = { tabs -> Box(Modifier.tabIndicatorOffset(tabs[tab]).height(2.dp).background(Amber)) },
                divider   = { HorizontalDivider(color = Divider, thickness = 0.5.dp) },
            ) {
                listOf("📝 Kelimeler (${vocabs.size})", "❓ Egzersizler (${exercises.size})").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i },
                        text = { Text(t, fontSize = 12.sp) },
                        selectedContentColor = Amber, unselectedContentColor = Muted)
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Amber)
                }
            } else when (tab) {
                0 -> VocabTab(
                    lessonId = lesson.id,
                    vocabs   = vocabs,
                    db       = db,
                    snack    = snack,
                    onReload = {
                        scope.launch {
                            val vs = db.collection("kf_vocab").whereEqualTo("lessonId", lesson.id).get().await()
                            vocabs = vs.documents.map { doc ->
                                (doc.data ?: emptyMap<String,Any>()).toMutableMap().also { it["_docId"] = doc.id }
                            }
                        }
                    },
                )
                1 -> ExerciseTab(
                    lessonId  = lesson.id,
                    exercises = exercises,
                    db        = db,
                    snack     = snack,
                    onReload  = {
                        scope.launch {
                            val es = db.collection("kf_exercises").whereEqualTo("lessonId", lesson.id).get().await()
                            exercises = es.documents.map { doc ->
                                (doc.data ?: emptyMap<String,Any>()).toMutableMap().also { it["_docId"] = doc.id }
                            }
                        }
                    },
                )
            }
        }
    }
}

// ── Kelime sekmesi ────────────────────────────────────────────────────────────
@Composable
private fun VocabTab(
    lessonId: String,
    vocabs  : List<Map<String,Any>>,
    db      : FirebaseFirestore,
    snack   : SnackbarHostState,
    onReload: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editDoc by remember { mutableStateOf<Map<String,Any>?>(null) }

    if (editDoc != null) {
        VocabEditDialog(
            doc      = editDoc!!,
            lessonId = lessonId,
            db       = db,
            onSave   = { scope.launch { snack.showSnackbar("✓ Kelime güncellendi"); onReload() } },
            onDelete = { scope.launch { snack.showSnackbar("🗑 Kelime silindi"); onReload() } },
            onDismiss = { editDoc = null },
        )
    }
    if (showAdd) {
        VocabEditDialog(
            doc      = emptyMap(),
            lessonId = lessonId,
            db       = db,
            onSave   = { scope.launch { snack.showSnackbar("✓ Kelime eklendi"); onReload() } },
            onDelete = null,
            onDismiss = { showAdd = false },
        )
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yeni Kelime Ekle", fontWeight = FontWeight.Bold)
            }
        }
        items(vocabs, key = { it["_docId"] as? String ?: "" }) { v ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { editDoc = v },
                shape    = RoundedCornerShape(12.dp),
                color    = HeftSurface,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(v["e"] as? String ?: "📖", fontSize = 24.sp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(v["ku"] as? String ?: "", color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(v["tr"] as? String ?: "", color = Muted, fontSize = 12.sp)
                        if ((v["kp"] as? String)?.isNotBlank() == true)
                            Text("/${v["kp"]}/", color = Primary, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Kelime düzenleme dialog ───────────────────────────────────────────────────
@Composable
private fun VocabEditDialog(
    doc      : Map<String,Any>,
    lessonId : String,
    db       : FirebaseFirestore,
    onSave   : () -> Unit,
    onDelete : (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scope   = rememberCoroutineScope()
    var ku      by remember { mutableStateOf(doc["ku"] as? String ?: "") }
    var tr      by remember { mutableStateOf(doc["tr"] as? String ?: "") }
    var kp      by remember { mutableStateOf(doc["kp"] as? String ?: "") }
    var e       by remember { mutableStateOf(doc["e"]  as? String ?: "") }
    var saving  by remember { mutableStateOf(false) }
    val docId   = doc["_docId"] as? String ?: ""
    var showDel by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = { Text(if (docId.isBlank()) "Yeni Kelime" else "Kelimeyi Düzenle", color = OnBackground, fontWeight = FontWeight.ExtraBold) },
        text  = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AdminField(ku, { ku = it }, "Kürtçe *")
                AdminField(tr, { tr = it }, "Türkçe *")
                AdminField(kp, { kp = it }, "Telaffuz (opsiyonel)")
                AdminField(e,  { e  = it  }, "Emoji (opsiyonel)", hint = "📖")
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = { showDel = true }) {
                        Text("Sil", color = Color(0xFFEF4444))
                    }
                }
                TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
                Button(
                    onClick = {
                        if (ku.isBlank() || tr.isBlank()) return@Button
                        scope.launch {
                            saving = true
                            val data = mapOf("ku" to ku, "tr" to tr, "kp" to kp, "e" to e, "lessonId" to lessonId)
                            if (docId.isBlank())
                                db.collection("kf_vocab").add(data).await()
                            else
                                db.collection("kf_vocab").document(docId).set(data, SetOptions.merge()).await()
                            saving = false
                            onSave()
                            onDismiss()
                        }
                    },
                    enabled = !saving && ku.isNotBlank() && tr.isNotBlank(),
                    colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    shape   = RoundedCornerShape(8.dp),
                ) { Text(if (saving) "..." else "Kaydet", fontWeight = FontWeight.Bold) }
            }
        },
    )

    if (showDel) {
        AlertDialog(
            onDismissRequest = { showDel = false },
            containerColor   = HeftSurface,
            title = { Text("Kelimeyi sil?", color = OnBackground) },
            text  = { Text("Bu kelime kalıcı olarak silinecek.", color = Muted) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.collection("kf_vocab").document(docId).delete().await()
                            onDelete?.invoke()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape  = RoundedCornerShape(8.dp),
                ) { Text("Sil", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDel = false }) { Text("İptal", color = Muted) } },
        )
    }
}

// ── Egzersiz sekmesi ──────────────────────────────────────────────────────────
@Composable
private fun ExerciseTab(
    lessonId : String,
    exercises: List<Map<String,Any>>,
    db       : FirebaseFirestore,
    snack    : SnackbarHostState,
    onReload : () -> Unit,
) {
    val scope   = rememberCoroutineScope()
    var showAdd by remember { mutableStateOf(false) }
    var editDoc by remember { mutableStateOf<Map<String,Any>?>(null) }

    if (editDoc != null) {
        ExerciseEditDialog(
            doc      = editDoc!!,
            lessonId = lessonId,
            db       = db,
            onSave   = { scope.launch { snack.showSnackbar("✓ Egzersiz güncellendi"); onReload() } },
            onDelete = { scope.launch { snack.showSnackbar("🗑 Egzersiz silindi"); onReload() } },
            onDismiss = { editDoc = null },
        )
    }
    if (showAdd) {
        ExerciseEditDialog(
            doc      = emptyMap(),
            lessonId = lessonId,
            db       = db,
            onSave   = { scope.launch { snack.showSnackbar("✓ Egzersiz eklendi"); onReload() } },
            onDelete = null,
            onDismiss = { showAdd = false },
        )
    }

    LazyColumn(
        modifier       = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(
                onClick  = { showAdd = true },
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(10.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color.White),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Yeni Egzersiz Ekle", fontWeight = FontWeight.Bold)
            }
        }
        items(exercises, key = { it["_docId"] as? String ?: "" }) { ex ->
            val type = ex["type"] as? String ?: "mcq"
            val typeColor = when (type) {
                "mcq"   -> Color(0xFF6366F1)
                "fill"  -> Color(0xFF22C55E)
                "match" -> Color(0xFFF59E0B)
                "build" -> Color(0xFFEC4899)
                else    -> Muted
            }
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { editDoc = ex },
                shape    = RoundedCornerShape(12.dp),
                color    = HeftSurface,
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = RoundedCornerShape(6.dp), color = typeColor.copy(0.15f)) {
                        Text(type.uppercase(), color = typeColor, fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        ex["question"] as? String ?: "",
                        color    = OnBackground,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(Icons.Default.Edit, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

// ── Egzersiz düzenleme dialog ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseEditDialog(
    doc      : Map<String,Any>,
    lessonId : String,
    db       : FirebaseFirestore,
    onSave   : () -> Unit,
    onDelete : (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val scope  = rememberCoroutineScope()
    val docId  = doc["_docId"] as? String ?: ""

    var type     by remember { mutableStateOf(doc["type"]       as? String ?: "mcq") }
    var question by remember { mutableStateOf(doc["question"]   as? String ?: "") }
    var optA     by remember { mutableStateOf(doc["optA"]       as? String ?: "") }
    var optB     by remember { mutableStateOf(doc["optB"]       as? String ?: "") }
    var optC     by remember { mutableStateOf(doc["optC"]       as? String ?: "") }
    var optD     by remember { mutableStateOf(doc["optD"]       as? String ?: "") }
    var answer   by remember { mutableStateOf(doc["answer"]     as? String ?: "") }
    var tr       by remember { mutableStateOf(doc["tr"]         as? String ?: "") }
    var wordsRaw by remember { mutableStateOf(
        (doc["words"] as? List<*>)?.joinToString(" ") ?: ""
    )}
    var saving   by remember { mutableStateOf(false) }
    var showDel  by remember { mutableStateOf(false) }
    var error    by remember { mutableStateOf("") }

    val types = listOf("mcq", "fill", "build")
    val typeLabels = mapOf("mcq" to "Çoktan Seçmeli", "fill" to "Boşluk Doldur", "build" to "Cümle Kur")

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = HeftSurface,
        title = {
            Text(
                if (docId.isBlank()) "Yeni Egzersiz" else "Egzersizi Düzenle",
                color = OnBackground, fontWeight = FontWeight.ExtraBold,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Tip seçimi
                Text("Egzersiz Tipi", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    types.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick  = { type = t },
                            label    = { Text(typeLabels[t] ?: t, fontSize = 11.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Amber.copy(0.2f),
                                selectedLabelColor     = Amber,
                            ),
                        )
                    }
                }

                // Soru
                AdminField(question, { question = it }, "Soru *", minLines = 2)

                when (type) {
                    "mcq" -> {
                        AdminField(optA, { optA = it }, "Seçenek A *")
                        AdminField(optB, { optB = it }, "Seçenek B")
                        AdminField(optC, { optC = it }, "Seçenek C")
                        AdminField(optD, { optD = it }, "Seçenek D")
                        AdminField(answer, { answer = it }, "Doğru Cevap * (A seçeneğiyle aynı yazılmalı)", hint = "optA değeriyle birebir aynı")
                    }
                    "fill" -> {
                        AdminField(answer, { answer = it }, "Doğru Cevap *")
                        AdminField(tr, { tr = it }, "Türkçe ipucu (opsiyonel)")
                    }
                    "build" -> {
                        AdminField(wordsRaw, { wordsRaw = it }, "Kelimeler (boşlukla ayır) *", hint = "Şev baş, xewn xweş!")
                        AdminField(tr, { tr = it }, "Türkçe çeviri *", hint = "İyi akşamlar, sana da.")
                    }
                }

                if (error.isNotBlank()) Text(error, color = Color(0xFFEF4444), fontSize = 11.sp)
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onDelete != null) {
                    TextButton(onClick = { showDel = true }) { Text("Sil", color = Color(0xFFEF4444)) }
                }
                TextButton(onClick = onDismiss) { Text("İptal", color = Muted) }
                Button(
                    onClick = {
                        if (question.isBlank()) { error = "Soru zorunlu"; return@Button }
                        if (type == "mcq"   && (optA.isBlank() || answer.isBlank())) { error = "Seçenek A ve cevap zorunlu"; return@Button }
                        if (type == "fill"  && answer.isBlank()) { error = "Cevap zorunlu"; return@Button }
                        if (type == "build" && wordsRaw.isBlank()) { error = "Kelimeler zorunlu"; return@Button }

                        scope.launch {
                            saving = true
                            try {
                                val data = mutableMapOf<String, Any>(
                                    "type"     to type,
                                    "question" to question.trim(),
                                    "lessonId" to lessonId,
                                )
                                when (type) {
                                    "mcq" -> {
                                        data["optA"]   = optA.trim()
                                        data["optB"]   = optB.trim()
                                        data["optC"]   = optC.trim()
                                        data["optD"]   = optD.trim()
                                        data["answer"] = optA.trim() // web temasıyla aynı: optA her zaman doğru
                                    }
                                    "fill" -> {
                                        data["answer"] = answer.trim()
                                        data["tr"]     = tr.trim()
                                    }
                                    "build" -> {
                                        data["words"] = wordsRaw.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
                                        data["tr"]    = tr.trim()
                                    }
                                }
                                if (docId.isBlank())
                                    db.collection("kf_exercises").add(data).await()
                                else
                                    db.collection("kf_exercises").document(docId).set(data, SetOptions.merge()).await()
                                onSave()
                                onDismiss()
                            } catch (e: Exception) {
                                error = e.message ?: "Hata"
                            }
                            saving = false
                        }
                    },
                    enabled = !saving,
                    colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    shape   = RoundedCornerShape(8.dp),
                ) { Text(if (saving) "..." else "Kaydet", fontWeight = FontWeight.Bold) }
            }
        },
    )

    if (showDel) {
        AlertDialog(
            onDismissRequest = { showDel = false },
            containerColor   = HeftSurface,
            title = { Text("Egzersizi sil?", color = OnBackground) },
            text  = { Text("Bu egzersiz kalıcı olarak silinecek.", color = Muted) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            db.collection("kf_exercises").document(docId).delete().await()
                            onDelete?.invoke()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape  = RoundedCornerShape(8.dp),
                ) { Text("Sil", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDel = false }) { Text("İptal", color = Muted) } },
        )
    }
}

// ── Ortak field bileşeni ──────────────────────────────────────────────────────
@Composable
fun AdminField(
    value       : String,
    onChange    : (String) -> Unit,
    label       : String,
    hint        : String       = "",
    minLines    : Int          = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier    : Modifier     = Modifier,
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        label         = { Text(label) },
        placeholder   = if (hint.isNotBlank()) {{ Text(hint, color = Muted, fontSize = 12.sp) }} else null,
        modifier      = modifier.fillMaxWidth(),
        shape         = RoundedCornerShape(10.dp),
        minLines      = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction    = if (minLines > 1) ImeAction.Default else ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = Amber,
            unfocusedBorderColor    = Divider,
            focusedTextColor        = OnBackground,
            unfocusedTextColor      = OnBackground,
            unfocusedContainerColor = SurfaceVar,
            focusedContainerColor   = SurfaceVar,
            focusedLabelColor       = Amber,
            unfocusedLabelColor     = Muted,
            cursorColor             = Amber,
        ),
    )
}

// ── JSON İçe Aktar ────────────────────────────────────────────────────────────
@Composable
private fun JsonImportTab(vm: KurdiViewModel) {
    val importing     by vm.importing.collectAsState()
    val importResult  by vm.importResult.collectAsState()
    var jsonText      by remember { mutableStateOf("") }
    var showSchema    by remember { mutableStateOf(false) }

    val schemaExample = """
{
  "unit": {
    "id": "u3",
    "ttl": "Renkler",
    "nameKu": "Reng",
    "icon": "🎨",
    "color": "#F59E0B",
    "order": 3
  },
  "lessons": [
    {
      "id": "l10",
      "unitId": "u3",
      "nameTr": "Renkler",
      "nameKu": "Reng",
      "emoji": "🎨",
      "xp": 15,
      "order": 1,
      "vocab": [
        { "ku": "sor", "kp": "sor", "tr": "kırmızı", "e": "🔴" },
        { "ku": "şîn", "kp": "şin", "tr": "mavi",    "e": "🔵" }
      ],
      "exercises": [
        {
          "type": "mcq",
          "question": "«Sor» ne demek?",
          "optA": "Kırmızı", "optB": "Mavi",
          "optC": "Yeşil",   "optD": "Sarı",
          "answer": "A"
        },
        {
          "type": "fill",
          "question": "Elma ___ e.",
          "answer": "sor",
          "options": ["sor", "şîn", "kesk", "zer"]
        },
        {
          "type": "match",
          "pairs": [["sor","kırmızı"],["şîn","mavi"],["kesk","yeşil"]]
        },
        {
          "type": "build",
          "tr": "Gökyüzü mavidir.",
          "answer": "Ezman şîn e.",
          "words": ["Ezman","şîn","e","sor","kesk"]
        }
      ]
    }
  ]
}""".trimIndent()

    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Başlık ve şema açıklaması ─────────────────────────────────────
        item {
            Text("📥 JSON ile Ders İçe Aktar", color = OnBackground,
                fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Spacer(Modifier.height(4.dp))
            Text("Bir veya birden fazla ders içeren JSON yapıştır, Firestore'a otomatik yükler.",
                color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        }

        // ── Şema göster/gizle ────────────────────────────────────────────
        item {
            Surface(
                shape    = RoundedCornerShape(12.dp),
                color    = HeftSurface,
                modifier = Modifier.fillMaxWidth().clickable { showSchema = !showSchema },
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Code, null, tint = Primary, modifier = Modifier.size(18.dp))
                        Text("JSON Şema Örneği", color = OnBackground, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    Icon(
                        if (showSchema) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        null, tint = Muted
                    )
                }
            }
            AnimatedVisibility(visible = showSchema) {
                Surface(
                    shape  = RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
                    color  = HeftSurface.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = schemaExample,
                        color    = OnBackground.copy(alpha = 0.8f),
                        fontSize = 10.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }
        }

        // ── Egzersiz tipleri açıklaması ───────────────────────────────────
        item {
            Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Egzersiz Tipleri", color = Primary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    listOf(
                        "mcq"   to "Çoktan seçmeli — question, optA-D, answer (A/B/C/D)",
                        "fill"  to "Boşluk doldurma — question, answer, options[]",
                        "match" to "Eşleştirme — pairs[[kürtçe,türkçe]]",
                        "build" to "Cümle kurma — tr (Türkçe), answer (Kürtçe), words[]",
                    ).forEach { (tip, desc) ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(shape = RoundedCornerShape(4.dp), color = Primary.copy(alpha = 0.15f)) {
                                Text(tip, color = Primary, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Text(desc, color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── JSON metin alanı ──────────────────────────────────────────────
        item {
            OutlinedTextField(
                value         = jsonText,
                onValueChange = { jsonText = it; vm.clearImportResult() },
                placeholder   = { Text("JSON yapıştır…", color = Muted, fontSize = 13.sp) },
                modifier      = Modifier.fillMaxWidth().height(220.dp),
                colors        = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = Amber, unfocusedBorderColor = Divider,
                    focusedTextColor     = OnBackground, unfocusedTextColor = OnBackground,
                    unfocusedContainerColor = HeftSurface, focusedContainerColor = HeftSurface,
                ),
                shape     = RoundedCornerShape(12.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize   = 11.sp,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
            )
        }

        // ── Import butonu ─────────────────────────────────────────────────
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick  = { if (jsonText.isNotBlank()) vm.importFromJson(jsonText) },
                    enabled  = jsonText.isNotBlank() && !importing,
                    colors   = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                    shape    = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    if (importing) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp),
                            color = Color.Black, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Yükleniyor…", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Firestore'a Aktar", fontWeight = FontWeight.Bold)
                    }
                }
                if (jsonText.isNotBlank()) {
                    OutlinedButton(
                        onClick = { jsonText = ""; vm.clearImportResult() },
                        shape   = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(48.dp),
                        border  = BorderStroke(1.dp, Divider),
                    ) { Text("Temizle", color = Muted) }
                }
            }
        }

        // ── Sonuç ─────────────────────────────────────────────────────────
        importResult?.let { result ->
            item {
                val hasErrors = result.errors.isNotEmpty()
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (hasErrors) Color(0xFFEF4444).copy(alpha = 0.1f)
                            else Color(0xFF22C55E).copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(
                                if (hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle,
                                null,
                                tint = if (hasErrors) Color(0xFFEF4444) else Color(0xFF22C55E),
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                if (hasErrors) "Kısmen tamamlandı" else "✅ Import başarılı!",
                                color = if (hasErrors) Color(0xFFEF4444) else Color(0xFF22C55E),
                                fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            listOf(
                                "🏛" to "${result.unitsAdded} ünite",
                                "📚" to "${result.lessonsAdded} ders",
                                "📝" to "${result.vocabAdded} kelime",
                                "🎯" to "${result.exercisesAdded} egzersiz",
                            ).forEach { (emoji, label) ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(emoji, fontSize = 18.sp)
                                    Text(label, color = OnBackground, fontSize = 11.sp)
                                }
                            }
                        }
                        if (hasErrors) {
                            HorizontalDivider(color = Color(0xFFEF4444).copy(alpha = 0.3f))
                            result.errors.forEach { err ->
                                Text("• $err", color = Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(60.dp)) }
    }
}

// ── Hata Raporları ────────────────────────────────────────────────────────────
@Composable
private fun ReportsTab(vm: KurdiViewModel) {
    val reports by vm.reports.collectAsState()
    val loading by vm.reportsLoading.collectAsState()

    LaunchedEffect(Unit) { vm.loadReports() }

    val pending  = reports.filter { !it.resolved }
    val resolved = reports.filter {  it.resolved }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Hata Raporları", fontWeight = FontWeight.Bold, color = OnBackground)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (pending.isNotEmpty()) {
                    Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEF4444).copy(alpha = 0.15f)) {
                        Text("${pending.size} bekliyor", color = Color(0xFFEF4444), fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                    }
                }
                IconButton(onClick = { vm.loadReports() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, null, tint = Muted, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (reports.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("✅", fontSize = 40.sp)
                    Text("Rapor yok", color = Muted)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (pending.isNotEmpty()) {
                    item { Text("Bekleyen (${pending.size})", color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)) }
                    items(pending, key = { it.id }) { report ->
                        ReportCard(report, onResolve = { vm.resolveReport(report.id) },
                            onDelete = { vm.deleteReport(report.id) })
                    }
                }
                if (resolved.isNotEmpty()) {
                    item { Text("Çözüldü (${resolved.size})", color = Color(0xFF22C55E),
                        fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp)) }
                    items(resolved, key = { it.id }) { report ->
                        ReportCard(report, onResolve = {},
                            onDelete = { vm.deleteReport(report.id) })
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
private fun ReportCard(report: LessonReport, onResolve: () -> Unit, onDelete: () -> Unit) {
    val dateStr = remember(report.ts) {
        if (report.ts > 0) java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(report.ts)) else ""
    }
    Surface(shape = RoundedCornerShape(12.dp), color = HeftSurface, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Default.Flag, null,
                    tint = if (report.resolved) Color(0xFF22C55E) else Color(0xFFEF4444),
                    modifier = Modifier.size(14.dp))
                Text(report.lessonName.ifBlank { report.lessonId }, color = Primary,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                if (dateStr.isNotEmpty()) Text(dateStr, color = Muted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(report.message, color = OnBackground, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(4.dp))
            Text("👤 ${report.userName}", color = Muted, fontSize = 11.sp)
            if (!report.resolved) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onResolve,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                        Icon(Icons.Default.Check, null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Çözüldü", fontSize = 12.sp, color = Color.White)
                    }
                    TextButton(onClick = onDelete,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
                        Text("Sil", color = Muted, fontSize = 12.sp)
                    }
                }
            } else {
                TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp)) {
                    Text("Sil", color = Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ADMİN AI DERS SEKMESİ — Yapay zeka ile egzersiz üret + Firestore'a kaydet
// ── Ünite Yönetimi ────────────────────────────────────────────────────────────
@Composable
private fun UnitManagerTab(vm: KurdiViewModel) {
    val units   by vm.units.collectAsState()
    val lessons by vm.lessons.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var editTarget   by remember { mutableStateOf<KfUnit?>(null) }
    var deleteTarget by remember { mutableStateOf<KfUnit?>(null) }
    var errorMsg     by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Üniteler (${units.size})", fontWeight = FontWeight.Bold, color = OnBackground)
            Button(
                onClick = { showAdd = true },
                colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                shape   = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Ünite Ekle", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        if (errorMsg.isNotBlank()) {
            Surface(color = Color(0xFFEF4444).copy(alpha = 0.15f), shape = RoundedCornerShape(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                Text(errorMsg, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(10.dp))
            }
            Spacer(Modifier.height(8.dp))
        }

        if (units.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("🏛", fontSize = 40.sp)
                    Text("Henüz ünite yok", color = Muted)
                    Text("Ünite eklemeden ders oluşturamazsın", color = Muted, fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(units, key = { it.id }) { unit ->
                    val lessonCount = lessons.count { it.unitId == unit.id }
                    Surface(shape = RoundedCornerShape(14.dp), color = HeftSurface, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(modifier = Modifier.size(40.dp).background(
                                try { Color(android.graphics.Color.parseColor(unit.color)) }
                                catch (_: Exception) { Amber },
                                RoundedCornerShape(10.dp),
                            ), contentAlignment = Alignment.Center) {
                                Text(unit.icon, fontSize = 20.sp)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(unit.ttl, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(unit.nameKu, color = Muted, fontSize = 12.sp)
                                Text("${unit.id} • $lessonCount ders • sıra: ${unit.order}", color = Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { editTarget = unit }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Edit, null, tint = Amber, modifier = Modifier.size(17.dp))
                            }
                            IconButton(onClick = { deleteTarget = unit }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Default.Delete, null, tint = Color(0xFFEF4444), modifier = Modifier.size(17.dp))
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showAdd) {
        UnitFormDialog(unit = null, onDismiss = { showAdd = false },
            onSave = { id, ttl, nameKu, desc, icon, color ->
                vm.addUnit(id, ttl, nameKu, desc, icon, color,
                    onDone = { showAdd = false; errorMsg = "" }, onError = { errorMsg = it })
            })
    }
    editTarget?.let { unit ->
        UnitFormDialog(unit = unit, onDismiss = { editTarget = null },
            onSave = { _, ttl, nameKu, desc, icon, color ->
                vm.updateUnit(unit.id, ttl, nameKu, desc, icon, color,
                    onDone = { editTarget = null; errorMsg = "" }, onError = { errorMsg = it })
            })
    }
    deleteTarget?.let { unit ->
        val lessonCount = lessons.count { it.unitId == unit.id }
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Üniteyi Sil?", color = OnBackground, fontWeight = FontWeight.Bold) },
            text  = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("«${unit.ttl}» silinecek.", color = OnBackground)
                    if (lessonCount > 0)
                        Text("⚠️ Bu üniteye bağlı $lessonCount ders var. Önce dersleri sil.",
                            color = Color(0xFFEF4444), fontSize = 12.sp)
                }
            },
            confirmButton = {
                Button(onClick = {
                    vm.deleteUnit(unit.id,
                        onDone = { deleteTarget = null; errorMsg = "" },
                        onError = { errorMsg = it; deleteTarget = null })
                }, enabled = lessonCount == 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                    shape  = RoundedCornerShape(10.dp),
                ) { Text("Sil", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("İptal", color = Muted) } },
            containerColor = HeftSurface,
        )
    }
}

@Composable
private fun UnitFormDialog(
    unit: KfUnit?, onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit,
) {
    val isEdit = unit != null
    var id     by remember { mutableStateOf(unit?.id ?: "") }
    var ttl    by remember { mutableStateOf(unit?.ttl ?: "") }
    var nameKu by remember { mutableStateOf(unit?.nameKu ?: "") }
    var desc   by remember { mutableStateOf(unit?.desc ?: "") }
    var icon   by remember { mutableStateOf(unit?.icon ?: "📖") }
    var color  by remember { mutableStateOf(unit?.color ?: "#8B5CF6") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "Üniteyi Düzenle" else "Yeni Ünite", color = OnBackground, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isEdit) AdminField(id, { id = it }, "Ünite ID (örn: u6)", hint = "u6")
                else Text("ID: ${unit!!.id}", color = Muted, fontSize = 11.sp)
                AdminField(ttl,    { ttl    = it }, "Türkçe Ad *")
                AdminField(nameKu, { nameKu = it }, "Kürtçe Ad")
                AdminField(desc,   { desc   = it }, "Açıklama")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminField(icon,  { icon  = it }, "Emoji", modifier = Modifier.weight(1f))
                    AdminField(color, { color = it }, "Renk (#hex)", modifier = Modifier.weight(1f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalId = if (isEdit) unit!!.id else id
                    if (finalId.isNotBlank() && ttl.isNotBlank()) onSave(finalId, ttl, nameKu, desc, icon, color)
                },
                enabled = (if (isEdit) true else id.isNotBlank()) && ttl.isNotBlank(),
                colors  = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Color.Black),
                shape   = RoundedCornerShape(10.dp),
            ) { Text("Kaydet", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onDismiss) { Text("İptal", color = Muted) } },
        containerColor = HeftSurface,
    )
}

// ═══════════════════════════════════════════════════════════════════════════════
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminAiLessonTab(
    vm     : KurdiViewModel,
    lessons: List<KfLesson>,
) {
    val scope      = rememberCoroutineScope()
    val aiLesson   by vm.aiLesson.collectAsState()
    val aiLoading  by vm.aiLoading.collectAsState()
    val aiError    by vm.aiError.collectAsState()
    val orApiKey   by vm.orApiKey.collectAsState()
    val snack      = remember { SnackbarHostState() }

    var apiKeyInput    by remember { mutableStateOf(orApiKey) }
    var topic          by remember { mutableStateOf("") }
    var level          by remember { mutableStateOf("destpêk") }
    var targetLessonId by remember { mutableStateOf("") }
    var expanded       by remember { mutableStateOf(false) }
    var saving         by remember { mutableStateOf(false) }

    // API key değişince kaydet
    LaunchedEffect(apiKeyInput) {
        if (apiKeyInput.isNotBlank()) vm.saveOrKey(apiKeyInput)
    }

    Scaffold(
        snackbarHost   = { SnackbarHost(snack) },
        containerColor = com.heftreng.app.ui.theme.Background,
    ) { pad ->
        LazyColumn(
            modifier       = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Başlık ────────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("🤖 Yapay Zeka ile Ders Üret",
                        color = com.heftreng.app.ui.theme.OnBackground,
                        fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    Text("OpenRouter API ile Kürtçe egzersizler üret ve doğrudan derse ekle.",
                        color = com.heftreng.app.ui.theme.Muted, fontSize = 12.sp)
                }
            }

            // ── API Key ───────────────────────────────────────────────────────
            item {
                AdminField(
                    value    = apiKeyInput,
                    onChange = { apiKeyInput = it },
                    label    = "OpenRouter API Key",
                    hint     = "sk-or-...",
                )
            }

            // ── Konu + Seviye ─────────────────────────────────────────────────
            item {
                AdminField(topic, { topic = it }, "Konu *", hint = "Selamlaşma, Sayılar, Renkler...")
            }
            item {
                val levels = listOf("destpêk" to "Başlangıç", "navîn" to "Orta", "pêşkeftî" to "İleri")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach { (key, label) ->
                        FilterChip(
                            selected = level == key,
                            onClick  = { level = key },
                            label    = { Text(label, fontSize = 12.sp) },
                            colors   = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = com.heftreng.app.ui.theme.Amber.copy(0.2f),
                                selectedLabelColor     = com.heftreng.app.ui.theme.Amber,
                            ),
                        )
                    }
                }
            }

            // ── Ders Seç (kaydetmek için) ─────────────────────────────────────
            item {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value         = lessons.find { it.id == targetLessonId }?.let { "${it.emoji} ${it.nameTr}" } ?: "Ders seçin (opsiyonel)",
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Egzersizleri eklemek için ders seç") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier      = Modifier.fillMaxWidth().menuAnchor(),
                        shape         = RoundedCornerShape(10.dp),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = com.heftreng.app.ui.theme.Amber,
                            unfocusedBorderColor = com.heftreng.app.ui.theme.Divider,
                            focusedLabelColor    = com.heftreng.app.ui.theme.Amber,
                            unfocusedLabelColor  = com.heftreng.app.ui.theme.Muted,
                            focusedTextColor     = com.heftreng.app.ui.theme.OnBackground,
                            unfocusedTextColor   = com.heftreng.app.ui.theme.Muted,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        lessons.forEach { lesson ->
                            DropdownMenuItem(
                                text    = { Text("${lesson.emoji} ${lesson.nameTr}", fontSize = 13.sp) },
                                onClick = { targetLessonId = lesson.id; expanded = false },
                            )
                        }
                    }
                }
            }

            // ── Üret Butonu ───────────────────────────────────────────────────
            item {
                Button(
                    onClick  = { vm.generateAiLesson(apiKeyInput, topic, level) },
                    enabled  = !aiLoading && apiKeyInput.isNotBlank() && topic.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(12.dp),
                    colors   = ButtonDefaults.buttonColors(
                        containerColor = com.heftreng.app.ui.theme.Primary,
                        contentColor   = Color.White,
                    ),
                ) {
                    if (aiLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (aiLoading) "Üretiliyor..." else "🤖 Ders Üret", fontWeight = FontWeight.Bold)
                }
            }

            // ── Hata ─────────────────────────────────────────────────────────
            aiError?.let { err ->
                item {
                    Surface(shape = RoundedCornerShape(10.dp), color = Color(0xFFEF4444).copy(0.1f)) {
                        Text(err, color = Color(0xFFEF4444), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                    }
                }
            }

            // ── AI Sonuçları + Kaydet ─────────────────────────────────────────
            aiLesson?.let { lesson ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "📚 ${lesson.topic} • ${lesson.exercises.size} egzersiz",
                            fontWeight = FontWeight.Bold,
                            color = com.heftreng.app.ui.theme.Primary, fontSize = 14.sp,
                        )
                        TextButton(onClick = { vm.clearAiLesson() }) {
                            Text("Temizle", color = com.heftreng.app.ui.theme.Muted, fontSize = 12.sp)
                        }
                    }
                }

                items(lesson.exercises, key = { it.ku + it.tr }) { ex ->
                    Surface(
                        shape  = RoundedCornerShape(12.dp),
                        color  = com.heftreng.app.ui.theme.HeftSurface,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Surface(shape = RoundedCornerShape(4.dp), color = com.heftreng.app.ui.theme.Primary.copy(0.15f)) {
                                    Text(ex.type.uppercase(), color = com.heftreng.app.ui.theme.Primary,
                                        fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Text(ex.ku, fontWeight = FontWeight.SemiBold,
                                    color = com.heftreng.app.ui.theme.OnBackground, fontSize = 14.sp)
                            }
                            if (ex.tr.isNotBlank())
                                Text(ex.tr, color = com.heftreng.app.ui.theme.Muted, fontSize = 12.sp)
                            if (ex.options.isNotEmpty()) {
                                ex.options.forEach { opt ->
                                    Text(
                                        "• $opt",
                                        color    = if (opt == ex.answer) com.heftreng.app.ui.theme.Primary else com.heftreng.app.ui.theme.OnBackground,
                                        fontSize = 12.sp,
                                        fontWeight = if (opt == ex.answer) FontWeight.Bold else FontWeight.Normal,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── Firestore'a kaydet ────────────────────────────────────────
                item {
                    Button(
                        onClick = {
                            if (targetLessonId.isBlank()) {
                                scope.launch { snack.showSnackbar("Önce bir ders seçin!") }
                                return@Button
                            }
                            saving = true
                            vm.saveAiLessonToFirestore(
                                targetLessonId = targetLessonId,
                                lesson         = lesson,
                                onDone = { count ->
                                    saving = false
                                    scope.launch {
                                        snack.showSnackbar("✓ $count egzersiz '${lessons.find { it.id == targetLessonId }?.nameTr}' dersine eklendi!")
                                        vm.clearAiLesson()
                                    }
                                },
                                onError = { err ->
                                    saving = false
                                    scope.launch { snack.showSnackbar("Hata: $err") }
                                },
                            )
                        },
                        enabled  = !saving && targetLessonId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(
                            containerColor = com.heftreng.app.ui.theme.Amber,
                            contentColor   = Color.Black,
                        ),
                    ) {
                        if (saving) {
                            CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(if (saving) "Kaydediliyor..." else "✅ Derse Kaydet (${lesson.exercises.size} egzersiz)",
                            fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
