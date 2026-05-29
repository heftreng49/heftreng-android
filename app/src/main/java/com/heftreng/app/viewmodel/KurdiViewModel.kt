package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.heftreng.app.data.model.AiExercise
import com.heftreng.app.data.model.AiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

// ── Veri modelleri (site ile tam uyumlu) ──────────────────────────────────────
data class KfUnit(
    val id     : String = "",
    val ttl    : String = "",      // site: ttl / nameTr
    val nameKu : String = "",
    val desc   : String = "",      // site: desc (not descTr)
    val descTr : String = "",      // eski uyumluluk
    val icon   : String = "📖",
    val color  : String = "#8B5CF6",
    val order  : Int    = 0,
)

data class KfLesson(
    val id        : String  = "",
    val unitId    : String  = "",
    val nameTr    : String  = "",
    val nameKu    : String  = "",
    val emoji     : String  = "📖",
    val xp        : Int     = 10,
    val order     : Int     = 0,
    val tip       : String  = "",
    val completed : Boolean = false,
)

data class KfVocab(
    val id : String = "",
    val ku : String = "",
    val kp : String = "",    // telaffuz
    val tr : String = "",
    val e  : String = "",    // emoji
)

data class KfExercise(
    val id         : String           = "",
    val type       : String           = "mcq",   // mcq | fill | match | build
    val question   : String           = "",
    val questionTr : String           = "",
    val optA       : String           = "",
    val optB       : String           = "",
    val optC       : String           = "",
    val optD       : String           = "",
    val answer     : String           = "",
    val wrong      : List<String>     = emptyList(),
    val pairs      : List<Pair<String,String>> = emptyList(), // match tipi
    val words      : List<String>     = emptyList(), // build tipi
    val tr         : String           = "",      // build'in Türkçe çevirisi
)

data class ActiveLesson(
    val lesson    : KfLesson,
    val vocab     : List<KfVocab>,
    val exercises : List<KfExercise>,
)

@HiltViewModel
class KurdiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val prefs by lazy { context.getSharedPreferences("hf_kurdi", Context.MODE_PRIVATE) }

    // OR API key — localStorage('kf_or_key') ile eşdeğer
    private val _orApiKey = MutableStateFlow(prefs.getString("kf_or_key", "") ?: "")
    val orApiKey = _orApiKey.asStateFlow()

    fun saveOrKey(key: String) {
        _orApiKey.value = key
        prefs.edit().putString("kf_or_key", key).apply()
    }

    // ── State ─────────────────────────────────────────────────────────────────
    private val _units        = MutableStateFlow<List<KfUnit>>(emptyList())
    val units = _units.asStateFlow()

    private val _lessons      = MutableStateFlow<List<KfLesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _doneIds      = MutableStateFlow<Set<String>>(emptySet())
    val doneIds = _doneIds.asStateFlow()

    private val _xp           = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak       = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level        = MutableStateFlow(1)
    val level = _level.asStateFlow()

    private val _activeLesson = MutableStateFlow<ActiveLesson?>(null)
    val activeLesson = _activeLesson.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _toast        = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    // ── AI ────────────────────────────────────────────────────────────────────
    private val _aiLesson  = MutableStateFlow<AiLesson?>(null)
    val aiLesson  = _aiLesson.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()
    private val _aiError   = MutableStateFlow<String?>(null)
    val aiError   = _aiError.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    // ── Ana yükleme — site ile tam senkron ────────────────────────────────────
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // ── 1. Kullanıcı belgesinden kf_done + xp + streak oku ────────
                // Site mantığı: users/{uid} belgesindeki kf_done array'i
                // KPS.done ile merge edilir — en kapsamlısı kazanır
                val completedIds = mutableSetOf<String>()

                if (uid.isNotEmpty()) {
                    try {
                        val userDoc = firestore.collection("users").document(uid).get().await()

                        // Site formatı: kf_done array (ana format)
                        val fbDone = (userDoc.get("kf_done") as? List<*>)
                            ?.mapNotNull { it as? String } ?: emptyList()
                        completedIds.addAll(fbDone)

                        // Uygulama eski formatı: kf_progress alt koleksiyonu (geriye uyumluluk)
                        try {
                            val subCol = firestore.collection("users").document(uid)
                                .collection("kf_progress").get().await()
                            subCol.documents.forEach { completedIds.add(it.id) }
                        } catch (_: Exception) {}

                        // XP ve Streak — site ve uygulama arasında hangisi büyükse al
                        val fbXp     = maxOf(
                            (userDoc.getLong("kf_xp")  ?: 0).toInt(),
                            (userDoc.getLong("xp")     ?: 0).toInt()
                        )
                        val fbStreak = maxOf(
                            (userDoc.getLong("kf_streak") ?: 0).toInt(),
                            (userDoc.getLong("streak")    ?: 0).toInt()
                        )

                        _xp.value     = fbXp
                        _streak.value = fbStreak
                        _level.value  = maxOf(1, fbXp / 100 + 1)

                        // Eğer alt koleksiyon siteden fazla ders içeriyorsa → Firebase'e merge et
                        if (completedIds.size > fbDone.size && uid.isNotEmpty()) {
                            firestore.collection("users").document(uid)
                                .set(mapOf("kf_done" to completedIds.toList()), SetOptions.merge())
                                .await()
                        }

                    } catch (e: Exception) { e.printStackTrace() }
                }

                _doneIds.value = completedIds

                // ── 2. kf_units + kf_lessons yükle ────────────────────────────
                loadUnitsAndLessons(completedIds)

            } catch (e: Exception) {
                e.printStackTrace()
                // Mock verisi göster
                _units.value   = MOCK_UNITS
                _lessons.value = MOCK_LESSONS.map { it.copy(completed = it.id in _doneIds.value) }
            } finally {
                _loading.value = false
            }
        }
    }

    private suspend fun loadUnitsAndLessons(doneIds: Set<String>) {
        try {
            val unitsSnap   = firestore.collection("kf_units")
                .limit(100).get().await()
            val lessonsSnap = firestore.collection("kf_lessons").get().await()

            val units = unitsSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                KfUnit(
                    id     = doc.id,
                    ttl    = (d["ttl"] as? String)?.takeIf { it.isNotBlank() }
                             ?: d["nameTr"] as? String ?: d["name"] as? String ?: "Ünite",
                    nameKu = d["nameKu"] as? String ?: "",
                    desc   = d["desc"]   as? String ?: "",
                    descTr = d["descTr"] as? String ?: d["desc"] as? String ?: "",
                    icon   = d["icon"]   as? String ?: "📖",
                    color  = d["color"]  as? String ?: "#8B5CF6",
                    order  = (d["order"] as? Long)?.toInt() ?: 0,
                )
            }

            val allLessons = lessonsSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                KfLesson(
                    id        = doc.id,
                    unitId    = d["unitId"]  as? String ?: "",
                    nameTr    = (d["nameTr"] as? String)?.takeIf { it.isNotBlank() }
                               ?: d["title"] as? String ?: d["name"] as? String ?: "",
                    nameKu    = d["nameKu"]  as? String ?: d["nameKmr"] as? String ?: "",
                    emoji     = d["emoji"]   as? String ?: "📖",
                    xp        = (d["xp"]     as? Long)?.toInt() ?: 10,
                    order     = (d["order"]  as? Long)?.toInt() ?: 0,
                    tip       = d["tip"]     as? String ?: "",
                    completed = doc.id in doneIds,
                )
            }

            if (units.isNotEmpty()) {
                _units.value   = units.sortedBy { it.order }
                _lessons.value = allLessons.sortedWith(compareBy({ it.unitId }, { it.order }))
            } else {
                // kf_units boşsa kf_lessons'tan varsayılan ünite oluştur
                if (allLessons.isNotEmpty()) {
                    _units.value   = listOf(KfUnit(id = "u_default", ttl = "Dersler", nameKu = "", desc = "", icon = "📚", color = "#8B5CF6", order = 0))
                    _lessons.value = allLessons.map { it.copy(unitId = "u_default") }
                } else {
                    // Her ikisi de boşsa mock veri
                    _units.value   = MOCK_UNITS
                    _lessons.value = MOCK_LESSONS.map { it.copy(completed = it.id in doneIds) }
                }
            }

        } catch (_: Exception) {
            _units.value   = MOCK_UNITS
            _lessons.value = MOCK_LESSONS.map { it.copy(completed = it.id in _doneIds.value) }
        }
    }

    // ── Ders aç ───────────────────────────────────────────────────────────────
    fun openLesson(lessonId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val lesson = _lessons.value.find { it.id == lessonId }
                    ?: run { _loading.value = false; return@launch }

                // kf_vocab yükle
                val vocabList: List<KfVocab> = try {
                    val snap = firestore.collection("kf_vocab")
                        .whereEqualTo("lessonId", lessonId).get().await()
                    snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        KfVocab(
                            id = doc.id,
                            ku = (d["ku"] as? String)?.takeIf { it.isNotBlank() }
                                 ?: d["kur"] as? String ?: "",
                            kp = d["kp"] as? String ?: "",
                            tr = d["tr"] as? String ?: "",
                            e  = d["e"]  as? String ?: "📖",
                        )
                    }
                } catch (_: Exception) { emptyList() }

                // kf_exercises yükle
                val exerciseList: List<KfExercise> = try {
                    val snap = firestore.collection("kf_exercises")
                        .whereEqualTo("lessonId", lessonId).get().await()
                    snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        // match tipi — pairs: [[ku, tr], ...]
                        val pairsRaw = d["pairs"] as? List<*>
                        val pairs = pairsRaw?.mapNotNull { item ->
                            val pair = item as? List<*>
                            val a = pair?.getOrNull(0) as? String ?: return@mapNotNull null
                            val b = pair.getOrNull(1) as? String ?: return@mapNotNull null
                            a to b
                        } ?: emptyList()
                        // build tipi — words: [String]
                        val words = (d["words"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        KfExercise(
                            id         = doc.id,
                            type       = d["type"]       as? String ?: "mcq",
                            question   = d["question"]   as? String ?: "",
                            questionTr = d["questionTr"] as? String ?: d["tr"] as? String ?: "",
                            optA       = d["optA"]       as? String ?: "",
                            optB       = d["optB"]       as? String ?: "",
                            optC       = d["optC"]       as? String ?: "",
                            optD       = d["optD"]       as? String ?: "",
                            answer     = (d["answer"] as? String)?.takeIf { it.isNotBlank() }
                                         ?: (d["correct"] as? String)?.takeIf { it.isNotBlank() }
                                         ?: d["optA"] as? String ?: "", // web temasında optA her zaman doğru
                            wrong      = (d["wrong"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            pairs      = pairs,
                            words      = words,
                            tr         = d["tr"] as? String ?: "",
                        )
                    }
                } catch (_: Exception) { emptyList() }

                val finalVocab: List<KfVocab> = vocabList
                    .distinctBy { it.ku.trim().lowercase() }
                    .ifEmpty { MOCK_VOCAB[lessonId] ?: emptyList() }

                // Firestore'da mükerrer doküman varsa temizle
                // Aynı tip+soru kombinasyonu sadece bir kez gösterilir
                val finalExercises: List<KfExercise> = exerciseList
                    .distinctBy { "${it.type}|${it.question.trim().lowercase()}" }
                    .ifEmpty { MOCK_EXERCISES[lessonId] ?: emptyList() }

                _activeLesson.value = ActiveLesson(
                    lesson    = lesson,
                    vocab     = finalVocab,
                    exercises = finalExercises,
                )

            } catch (e: Exception) {
                _toast.value = "Ders yüklenemedi"
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun closeLesson() { _activeLesson.value = null }

    // ── Ders tamamla — site ile tam uyumlu yazma ──────────────────────────────
    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained   = lesson.xp
        val newXp    = _xp.value + gained
        val newLevel = maxOf(_level.value, newXp / 100 + 1)

        // Anında UI güncelle
        _xp.value     = newXp
        _level.value  = newLevel
        _doneIds.value = _doneIds.value + lessonId
        _lessons.value = _lessons.value.map {
            if (it.id == lessonId) it.copy(completed = true) else it
        }
        _toast.value = "+$gained XP 🎉"

        viewModelScope.launch {
            try {
                val today = SimpleDateFormat("EEE MMM dd yyyy", Locale.US).format(Date())

                // ── Site formatı — users/{uid} belgesine merge ────────────────
                // Tam olarak sitenin _saveState() ile aynı alanları yaz
                firestore.collection("users").document(uid).set(
                    mapOf(
                        "kf_done"    to FieldValue.arrayUnion(lessonId),  // array merge
                        "kf_xp"      to newXp,
                        "kf_streak"  to _streak.value,
                        "kf_lastDate" to today,
                        "kf_level"   to newLevel,
                        "xp"         to newXp,
                        "level"      to newLevel,
                        "updatedAt"  to FieldValue.serverTimestamp(),
                    ),
                    SetOptions.merge()
                ).await()

                // ── Uygulama formatı — alt koleksiyon (geriye uyumluluk) ──────
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf("ts" to Timestamp.now(), "xpEarned" to gained), SetOptions.merge())
                    .await()

                // Streak güncelle
                updateStreak(today)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun updateStreak(today: String) {
        try {
            val u        = firestore.collection("users").document(uid).get().await()
            val lastDate = u.getString("kf_lastDate") ?: ""
            val cal      = Calendar.getInstance()
            val todayCal = SimpleDateFormat("EEE MMM dd yyyy", Locale.US).parse(today)
            val lastCal  = if (lastDate.isNotBlank()) {
                try { SimpleDateFormat("EEE MMM dd yyyy", Locale.US).parse(lastDate) }
                catch (_: Exception) { null }
            } else null

            val diffDays = if (lastCal != null && todayCal != null) {
                ((todayCal.time - lastCal.time) / 86400000L)
            } else -1L

            val newStreak = when {
                lastCal == null || diffDays > 1 -> 1
                diffDays == 1L                  -> _streak.value + 1
                else                            -> _streak.value
            }
            _streak.value = newStreak

            firestore.collection("users").document(uid).set(
                mapOf("kf_streak" to newStreak, "streak" to newStreak, "kf_lastDate" to today),
                SetOptions.merge()
            ).await()

        } catch (e: Exception) { e.printStackTrace() }
    }

    fun clearToast() { _toast.value = null }

    fun getNextLesson(): KfLesson? =
        _lessons.value.sortedWith(compareBy({ it.unitId }, { it.order }))
            .firstOrNull { !it.completed }

    // ── AI Ders ───────────────────────────────────────────────────────────────
    fun generateAiLesson(apiKey: String, topic: String, level: String = "destpêk") {
        if (apiKey.isBlank() || topic.isBlank()) return
        _aiLoading.value = true; _aiError.value = null
        viewModelScope.launch {
            try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val payload = JSONObject().apply {
                    put("model", "google/gemini-2.0-flash-001")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content",
                                "Sen bir Kürtçe (Kurmancî) öğretmenisin. " +
                                "JSON formatında ders üret: " +
                                "{\"topic\":\"...\",\"exercises\":[{\"type\":\"mcq\",\"ku\":\"...\",\"tr\":\"...\",\"options\":[...],\"answer\":\"...\"}]}. " +
                                "Sadece JSON döndür, başka şey yazma."
                            )
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", "Konu: $topic | Seviye: $level | 5 egzersiz üret")
                        })
                    })
                    put("max_tokens", 1500)
                }
                val url  = URL("https://openrouter.ai/api/v1/chat/completions")
                val conn = (url.openConnection() as HttpsURLConnection).also {
                    it.requestMethod = "POST"
                    it.setRequestProperty("Content-Type", "application/json")
                    it.setRequestProperty("Authorization", "Bearer $apiKey")
                    it.setRequestProperty("HTTP-Referer", "https://heft-reng.blogspot.com")
                    it.setRequestProperty("X-Title", "Heftreng Kurdî")
                    it.doOutput = true
                    it.connectTimeout = 15000
                    it.readTimeout    = 30000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val raw  = conn.inputStream.bufferedReader().readText()
                val txt  = JSONObject(raw)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content")
                    .trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                val lj = JSONObject(txt)
                val ea = lj.optJSONArray("exercises") ?: JSONArray()
                _aiLesson.value = AiLesson(
                    topic = topic, level = level,
                    exercises = (0 until ea.length()).map { i ->
                        val ex = ea.getJSONObject(i)
                        val op = ex.optJSONArray("options")
                        AiExercise(
                            type    = ex.optString("type", "mcq"),
                            ku      = ex.optString("ku", ""),
                            tr      = ex.optString("tr", ""),
                            options = if (op != null) (0 until op.length()).map { op.getString(it) } else emptyList(),
                            answer  = ex.optString("answer", ""),
                        )
                    }
                )
            } } catch (e: Exception) {
                _aiError.value = "Hata: ${e.message ?: e.javaClass.simpleName} — API key geçersiz veya ağ hatası"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun clearAiLesson() { _aiLesson.value = null; _aiError.value = null }

    // ── Admin: kullanıcı bağlamı olmadan tüm dersleri yükle ──────────────────
    fun loadAdminLessons() {
        viewModelScope.launch {
            try {
                val snap = firestore.collection("kf_lessons")
                    .limit(200).get().await()
                val lessons = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    KfLesson(
                        id        = doc.id,
                        unitId    = d["unitId"] as? String ?: "",
                        nameTr    = d["nameTr"] as? String ?: d["name"] as? String ?: "",
                        nameKu    = d["nameKu"] as? String ?: "",
                        emoji     = d["emoji"]  as? String ?: "📖",
                        xp        = (d["xp"]    as? Long)?.toInt() ?: 10,
                        order     = (d["order"] as? Long)?.toInt() ?: 1,
                        tip       = d["tip"]    as? String ?: "",
                        completed = false,
                    )
                }
                _lessons.value = lessons
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // ── AI Ders Kaydet — üretilen egzersizleri seçili derse yaz ─────────────
    fun saveAiLessonToFirestore(
        targetLessonId: String,
        lesson        : AiLesson,
        onDone        : (Int) -> Unit,
        onError       : (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    var saved = 0
                    lesson.exercises.forEach { ex ->
                        val data = mutableMapOf<String, Any>(
                            "lessonId" to targetLessonId,
                            "type"     to (ex.type.takeIf { it.isNotBlank() } ?: "mcq"),
                            "question" to ex.ku,
                            "tr"       to ex.tr,
                        )
                        if (ex.options.isNotEmpty()) {
                            data["optA"]   = ex.options.getOrElse(0) { "" }
                            data["optB"]   = ex.options.getOrElse(1) { "" }
                            data["optC"]   = ex.options.getOrElse(2) { "" }
                            data["optD"]   = ex.options.getOrElse(3) { "" }
                            data["answer"] = ex.answer.ifBlank { ex.options.getOrElse(0) { "" } }
                        } else {
                            data["answer"] = ex.answer
                        }
                        firestore.collection("kf_exercises").add(data).await()
                        saved++
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        onDone(saved)
                    }
                }
            } catch (e: Exception) {
                onError(e.message ?: "Kaydetme hatası")
            }
        }
    }

    // ── Eski uyumluluk ────────────────────────────────────────────────────────
    fun startLesson(lesson: com.heftreng.app.data.model.KurdiLesson) = openLesson(lesson.id)
}

// ── Mock verisi (site ile aynı ID'ler) ────────────────────────────────────────
// Site _kpRenderMockUnits() ile tamamen eşleşiyor — kf_done senkronize çalışır
private val MOCK_UNITS = listOf(
    KfUnit(id="u1", ttl="Destpêk",  nameKu="Destpêk",  desc="Temel Kelimeler", icon="🌱", color="#58cc02", order=1),
    KfUnit(id="u2", ttl="Jimare",   nameKu="Jimare",   desc="Sayılar",         icon="🔢", color="#1cb0f6", order=2),
    KfUnit(id="u3", ttl="Reng",     nameKu="Reng",     desc="Renkler",         icon="🎨", color="#ce82ff", order=3),
    KfUnit(id="u4", ttl="Malbat",   nameKu="Malbat",   desc="Aile",            icon="👨‍👩‍👧", color="#ff9600", order=4),
    KfUnit(id="u5", ttl="Xwarin",   nameKu="Xwarin",   desc="Yemek",           icon="🍎", color="#ff4b4b", order=5),
)

private val MOCK_LESSONS = listOf(
    KfLesson("l1", "u1", "Merhaba!",         "Silav!",             "👋", 10, 1),
    KfLesson("l2", "u1", "Nasılsın?",        "Çawa yî?",           "😊", 15, 2),
    KfLesson("l3", "u2", "1-10 Arası",       "Yek-Deh",            "🔢", 20, 1),
    KfLesson("l4", "u3", "Temel Renkler",    "Rengên Bingehîn",    "🎨", 15, 1),
    KfLesson("l5", "u4", "Anne-Baba",        "Dê-Bav",             "👨‍👩‍👧", 20, 1),
    KfLesson("l6", "u5", "Meyve",            "Fêkî",               "🍎", 15, 1),
)

private val MOCK_VOCAB = mapOf(
    "l1" to listOf(
        KfVocab("v1","Silav",    "si-lav",   "Merhaba",     "👋"),
        KfVocab("v2","Spas",     "spas",     "Teşekkürler", "🙏"),
        KfVocab("v3","Baş e",    "baş-e",    "İyiyim",      "😊"),
        KfVocab("v4","Xweş bî",  "xweş-bî",  "Hoşça kal",   "👋"),
        KfVocab("v5","Belê",     "be-lê",    "Evet",        "✅"),
        KfVocab("v6","Na",       "na",       "Hayır",       "❌"),
    ),
    "l2" to listOf(
        KfVocab("v7","Çawa",     "ça-wa",    "Nasıl",       "🤔"),
        KfVocab("v8","Baş",      "baş",      "İyi",         "👍"),
        KfVocab("v9","Nexweş",   "ne-xweş",  "Hasta",       "🤒"),
        KfVocab("v10","Pirr baş","pirr-baş", "Çok iyi",     "🌟"),
        KfVocab("v11","Spas dikim","spas-di-kim","Teşekkür ederim","🙏"),
    ),
    "l3" to listOf(
        KfVocab("vn1","Yek",     "yek",      "Bir",         "1️⃣"),
        KfVocab("vn2","Du",      "du",       "İki",         "2️⃣"),
        KfVocab("vn3","Sê",      "sê",       "Üç",          "3️⃣"),
        KfVocab("vn4","Çar",     "çar",      "Dört",        "4️⃣"),
        KfVocab("vn5","Pênc",    "pênc",     "Beş",         "5️⃣"),
        KfVocab("vn6","Şeş",     "şeş",      "Altı",        "6️⃣"),
        KfVocab("vn7","Heft",    "heft",     "Yedi",        "7️⃣"),
        KfVocab("vn8","Heşt",    "heşt",     "Sekiz",       "8️⃣"),
        KfVocab("vn9","Neh",     "neh",      "Dokuz",       "9️⃣"),
        KfVocab("vn10","Deh",    "deh",      "On",          "🔟"),
    ),
    "l4" to listOf(
        KfVocab("vc1","Sor",     "sor",      "Kırmızı",     "🔴"),
        KfVocab("vc2","Şîn",     "şîn",      "Mavi",        "🔵"),
        KfVocab("vc3","Kesk",    "kesk",     "Yeşil",       "🟢"),
        KfVocab("vc4","Zer",     "zer",      "Sarı",        "🟡"),
        KfVocab("vc5","Spî",     "spî",      "Beyaz",       "⚪"),
        KfVocab("vc6","Reş",     "reş",      "Siyah",       "⚫"),
    ),
    "l5" to listOf(
        KfVocab("vm1","Dê",      "dê",       "Anne",        "👩"),
        KfVocab("vm2","Bav",     "bav",      "Baba",        "👨"),
        KfVocab("vm3","Bira",    "bi-ra",    "Erkek kardeş","👦"),
        KfVocab("vm4","Xwişk",   "xwişk",    "Kız kardeş",  "👧"),
        KfVocab("vm5","Kur",     "kur",      "Oğul",        "🧒"),
        KfVocab("vm6","Keç",     "keç",      "Kız",         "🧒"),
    ),
    "l6" to listOf(
        KfVocab("vf1","Sêv",     "sêv",      "Elma",        "🍎"),
        KfVocab("vf2","Moz",     "moz",      "Muz",         "🍌"),
        KfVocab("vf3","Tirî",    "ti-rî",    "Üzüm",        "🍇"),
        KfVocab("vf4","Gûz",     "gûz",      "Ceviz",       "🥜"),
        KfVocab("vf5","Tûjik",   "tû-jik",   "Çilek",       "🍓"),
    ),
)

private val MOCK_EXERCISES = mapOf(
    "l1" to listOf(
        KfExercise("e1","mcq","«Silav» ne demek?","Merhaba","Teşekkür","Günaydın","Selam","Merhaba"),
        KfExercise("e2","mcq","«Spas» ne demek?","Teşekkürler","Merhaba","İyi","Hoşça kal","Teşekkürler"),
        KfExercise("e3","mcq","«Baş e» ne demek?","İyiyim","Hasta","Teşekkür","Günaydın","İyiyim"),
        KfExercise("e4","mcq","«Belê» ne demek?","Evet","Hayır","Belki","Tamam","Evet"),
    ),
    "l2" to listOf(
        KfExercise("e5","mcq","«Baş» ne demek?","İyi","Hasta","Nasıl","Teşekkür","İyi"),
        KfExercise("e6","mcq","«Nexweş» ne demek?","Hasta","İyi","Nasıl","Teşekkür","Hasta"),
        KfExercise("e7","mcq","«Çawa yî?» ne demek?","Nasılsın?","İyiyim","Teşekkürler","Hoşça kal","Nasılsın?"),
    ),
    "l3" to listOf(
        KfExercise("en1","mcq","«Sê» kaç demek?","3","1","2","4","3"),
        KfExercise("en2","mcq","«Pênc» kaç demek?","5","6","4","7","5"),
        KfExercise("en3","mcq","«Deh» kaç demek?","10","8","9","7","10"),
        KfExercise("en4","mcq","«Heft» kaç demek?","7","6","8","9","7"),
    ),
    "l4" to listOf(
        KfExercise("ec1","mcq","«Sor» ne demek?","Kırmızı","Mavi","Yeşil","Sarı","Kırmızı"),
        KfExercise("ec2","mcq","«Kesk» ne demek?","Yeşil","Siyah","Beyaz","Mavi","Yeşil"),
        KfExercise("ec3","mcq","«Şîn» ne demek?","Mavi","Kırmızı","Sarı","Beyaz","Mavi"),
        KfExercise("ec4","mcq","«Reş» ne demek?","Siyah","Beyaz","Sarı","Yeşil","Siyah"),
    ),
    "l5" to listOf(
        KfExercise("em1","mcq","«Dê» ne demek?","Anne","Baba","Kardeş","Kız","Anne"),
        KfExercise("em2","mcq","«Bav» ne demek?","Baba","Anne","Oğul","Kız","Baba"),
        KfExercise("em3","mcq","«Xwişk» ne demek?","Kız kardeş","Erkek kardeş","Anne","Baba","Kız kardeş"),
    ),
    "l6" to listOf(
        KfExercise("ef1","mcq","«Sêv» ne demek?","Elma","Muz","Üzüm","Çilek","Elma"),
        KfExercise("ef2","mcq","«Moz» ne demek?","Muz","Elma","Üzüm","Ceviz","Muz"),
        KfExercise("ef3","mcq","«Tûjik» ne demek?","Çilek","Muz","Elma","Üzüm","Çilek"),
    ),
)
