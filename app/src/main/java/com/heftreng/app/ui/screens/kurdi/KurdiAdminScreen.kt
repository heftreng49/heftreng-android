package com.heftreng.app.ui.screens.kurdi

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
                listOf("📚 Dersler", "➕ Yeni Ders", "🤖 Yapay Zeka").forEachIndexed { i, t ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text     = { Text(t, fontSize = 13.sp) },
                        selectedContentColor   = Amber,
                        unselectedContentColor = Muted,
                    )
                }
            }

            when (selectedTab) {
                0 -> LessonListTab(lessons = lessons, onSelect = { selectedLesson = it })
                1 -> NewLessonTab(vm = vm, onCreated = { selectedTab = 0 })
                2 -> AdminAiLessonTab(vm = vm, lessons = lessons)
            }
        }
    }
}

// ── Ders listesi ──────────────────────────────────────────────────────────────
@Composable
private fun LessonListTab(
    lessons  : List<KfLesson>,
    onSelect : (KfLesson) -> Unit,
) {
    if (lessons.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Ders bulunamadı", color = Muted)
        }
        return
    }
    LazyColumn(
        modifier        = Modifier.fillMaxSize(),
        contentPadding  = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(lessons, key = { it.id }) { lesson ->
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(lesson) },
                shape    = RoundedCornerShape(14.dp),
                color    = HeftSurface,
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(lesson.emoji, fontSize = 28.sp)
                    Column(Modifier.weight(1f)) {
                        Text(lesson.nameTr, color = OnBackground, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(lesson.nameKu, color = Muted, fontSize = 12.sp)
                        Text("ID: ${lesson.id} • Birim: ${lesson.unitId} • ${lesson.xp} XP",
                            color = Muted, fontSize = 10.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}

// ── Yeni ders oluştur ─────────────────────────────────────────────────────────
@Composable
private fun NewLessonTab(vm: KurdiViewModel, onCreated: () -> Unit) {
    val scope   = rememberCoroutineScope()
    val db      = remember { FirebaseFirestore.getInstance() }
    var id      by remember { mutableStateOf("") }
    var unitId  by remember { mutableStateOf("u1") }
    var nameTr  by remember { mutableStateOf("") }
    var nameKu  by remember { mutableStateOf("") }
    var emoji   by remember { mutableStateOf("📖") }
    var xp      by remember { mutableStateOf("10") }
    var order   by remember { mutableStateOf("1") }
    var saving  by remember { mutableStateOf(false) }
    var error   by remember { mutableStateOf("") }
    val snack   = remember { SnackbarHostState() }

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
            item { AdminField(unitId, { unitId = it }, "Birim ID (örn: u1)", hint = "u1") }
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
                        if (id.isBlank() || nameTr.isBlank()) { error = "ID ve Türkçe ad zorunlu"; return@Button }
                        scope.launch {
                            saving = true
                            try {
                                db.collection("kf_lessons").document(id).set(
                                    mapOf(
                                        "id"     to id,
                                        "unitId" to unitId,
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

// ═══════════════════════════════════════════════════════════════════════════════
// ADMİN AI DERS SEKMESİ — Yapay zeka ile egzersiz üret + Firestore'a kaydet
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
