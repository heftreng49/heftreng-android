package com.heftreng.app.viewmodel

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.AiExercise
import com.heftreng.app.data.model.AiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.net.ssl.HttpsURLConnection

// ── Veri modelleri (site ile tam uyumlu) ─────────────────────────────────────
data class KfUnit(
    val id      : String = "",
    val ttl     : String = "",    // site: ttl / nameTr
    val descTr  : String = "",
    val icon    : String = "📖",
    val color   : String = "#8B5CF6",
    val order   : Int    = 0,
)

data class KfLesson(
    val id       : String  = "",
    val unitId   : String  = "",
    val nameTr   : String  = "",
    val nameKu   : String  = "",
    val emoji    : String  = "📖",
    val xp       : Int     = 10,
    val order    : Int     = 0,
    val tip      : String  = "",
    val completed: Boolean = false,
)

data class KfVocab(
    val id : String = "",
    val ku : String = "",
    val kp : String = "",   // telaffuz
    val tr : String = "",
    val e  : String = "",   // emoji
)

data class KfExercise(
    val id       : String       = "",
    val type     : String       = "mcq",  // mcq / fill / match
    val question : String       = "",
    val optA     : String       = "",
    val optB     : String       = "",
    val optC     : String       = "",
    val optD     : String       = "",
    val answer   : String       = "",
    val wrong    : List<String> = emptyList(),
    val pairs    : List<Pair<String,String>> = emptyList(),
)

data class ActiveLesson(
    val lesson  : KfLesson,
    val vocab   : List<KfVocab>,
    val exercises: List<KfExercise>,
    var currentStep : Int = 0,
)

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    // ── Units + Lessons ──────────────────────────────────────────────────────
    private val _units   = MutableStateFlow<List<KfUnit>>(emptyList())
    val units = _units.asStateFlow()

    private val _lessons = MutableStateFlow<List<KfLesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _doneIds = MutableStateFlow<Set<String>>(emptySet())
    val doneIds = _doneIds.asStateFlow()

    // ── Active lesson (ders yapma ekranı) ────────────────────────────────────
    private val _activeLesson = MutableStateFlow<ActiveLesson?>(null)
    val activeLesson = _activeLesson.asStateFlow()

    // ── XP / Streak / Level ──────────────────────────────────────────────────
    private val _xp     = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level  = MutableStateFlow(1)
    val level = _level.asStateFlow()

    // ── UI ───────────────────────────────────────────────────────────────────
    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    private val _toast   = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    // ── AI ───────────────────────────────────────────────────────────────────
    private val _aiLesson  = MutableStateFlow<AiLesson?>(null)
    val aiLesson  = _aiLesson.asStateFlow()
    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()
    private val _aiError   = MutableStateFlow<String?>(null)
    val aiError   = _aiError.asStateFlow()

    init { load() }

    // ── Yükle ────────────────────────────────────────────────────────────────
    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Kullanıcı stats
                if (uid.isNotEmpty()) {
                    try {
                        val u = firestore.collection("users").document(uid).get().await()
                        val xpVal = maxOf(
                            (u.getLong("xp")    ?: 0).toInt(),
                            (u.getLong("kf_xp") ?: 0).toInt()
                        )
                        _xp.value     = xpVal
                        _streak.value = maxOf(
                            (u.getLong("streak")    ?: 0).toInt(),
                            (u.getLong("kf_streak") ?: 0).toInt()
                        )
                        _level.value  = maxOf(1, (u.getLong("level") ?: 1).toInt(), xpVal / 100 + 1)
                    } catch (_: Exception) {}
                }

                // Tamamlanan dersler
                val completedIds: Set<String> = if (uid.isNotEmpty()) {
                    try {
                        firestore.collection("users").document(uid)
                            .collection("kf_progress").get().await()
                            .documents.map { it.id }.toSet()
                    } catch (_: Exception) { emptySet() }
                } else emptySet()
                _doneIds.value = completedIds

                // kf_units + kf_lessons
                try {
                    val unitsSnap   = firestore.collection("kf_units")
                        .orderBy("order", Query.Direction.ASCENDING).get().await()
                    val lessonsSnap = firestore.collection("kf_lessons").get().await()

                    val units = unitsSnap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        KfUnit(
                            id     = doc.id,
                            ttl    = (d["ttl"] as? String)?.takeIf { it.isNotBlank() }
                                     ?: d["nameTr"] as? String ?: d["name"] as? String ?: "Ünite",
                            descTr = (d["descTr"] as? String) ?: d["desc"] as? String ?: "",
                            icon   = d["icon"]  as? String ?: "📖",
                            color  = d["color"] as? String ?: "#8B5CF6",
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
                            nameKu    = d["nameKu"]  as? String ?: "",
                            emoji     = d["emoji"]   as? String ?: "📖",
                            xp        = (d["xp"]     as? Long)?.toInt()    ?: 10,
                            order     = (d["order"]  as? Long)?.toInt()    ?: 0,
                            tip       = d["tip"]     as? String ?: "",
                            completed = doc.id in completedIds,
                        )
                    }

                    if (units.isNotEmpty()) {
                        _units.value   = units
                        _lessons.value = allLessons
                    } else {
                        // Firestore'da kf_units yok — kf_lessons'tan oluştur
                        if (allLessons.isNotEmpty()) {
                            _units.value   = listOf(KfUnit("u_default","Dersler","",  "📚","#8B5CF6",0))
                            _lessons.value = allLessons.map { it.copy(unitId = "u_default") }
                        } else {
                            _units.value   = MOCK_UNITS
                            _lessons.value = MOCK_LESSONS.map { it.copy(completed = it.id in completedIds) }
                        }
                    }
                } catch (_: Exception) {
                    _units.value   = MOCK_UNITS
                    _lessons.value = MOCK_LESSONS.map { it.copy(completed = it.id in completedIds) }
                }

            } catch (e: Exception) {
                _units.value   = MOCK_UNITS
                _lessons.value = MOCK_LESSONS
            } finally {
                _loading.value = false
            }
        }
    }

    // ── Ders aç ──────────────────────────────────────────────────────────────
    fun openLesson(lessonId: String) {
        viewModelScope.launch {
            _loading.value = true
            try {
                val lesson = _lessons.value.find { it.id == lessonId }
                    ?: return@launch

                // kf_vocab + kf_exercises
                val vocabSnap = try {
                    firestore.collection("kf_vocab")
                        .whereEqualTo("lessonId", lessonId).get().await()
                } catch (_: Exception) { null }

                val exSnap = try {
                    firestore.collection("kf_exercises")
                        .whereEqualTo("lessonId", lessonId).get().await()
                } catch (_: Exception) { null }

                val vocab = vocabSnap?.documents?.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    KfVocab(
                        id = doc.id,
                        ku = d["ku"] as? String ?: "",
                        kp = d["kp"] as? String ?: "",
                        tr = d["tr"] as? String ?: "",
                        e  = d["e"]  as? String ?: "📖",
                    )
                } ?: emptyList()

                val exercises = exSnap?.documents?.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val pairsRaw = d["pairs"] as? List<*>
                    val pairs = pairsRaw?.mapNotNull { item ->
                        val pair = item as? List<*> ?: return@mapNotNull null
                        (pair.getOrNull(0) as? String to pair.getOrNull(1) as? String)
                            .let { if (it.first != null && it.second != null) it.first!! to it.second!! else null }
                    } ?: emptyList()
                    KfExercise(
                        id       = doc.id,
                        type     = d["type"]     as? String ?: "mcq",
                        question = d["question"] as? String ?: "",
                        optA     = d["optA"]     as? String ?: "",
                        optB     = d["optB"]     as? String ?: "",
                        optC     = d["optC"]     as? String ?: "",
                        optD     = d["optD"]     as? String ?: "",
                        answer   = d["answer"]   as? String ?: "",
                        wrong    = (d["wrong"]   as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        pairs    = pairs,
                    )
                } ?: emptyList()

                // Mock verisi kullan eğer Firestore boşsa
                val finalVocab = vocab.ifEmpty {
                    MOCK_VOCAB[lessonId] ?: emptyList()
                }
                val finalExercises = exercises.ifEmpty {
                    MOCK_EXERCISES[lessonId] ?: emptyList()
                }

                _activeLesson.value = ActiveLesson(
                    lesson   = lesson,
                    vocab    = finalVocab,
                    exercises = finalExercises,
                )

            } catch (e: Exception) {
                _toast.value = "Ders yüklenemedi: ${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun closeLesson() { _activeLesson.value = null }

    // ── Ders tamamla ─────────────────────────────────────────────────────────
    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained   = lesson.xp
        val newXp    = _xp.value + gained
        val newLevel = maxOf(_level.value, newXp / 100 + 1)

        _xp.value     = newXp
        _level.value  = newLevel
        _doneIds.value = _doneIds.value + lessonId
        _lessons.value = _lessons.value.map {
            if (it.id == lessonId) it.copy(completed = true) else it
        }
        _toast.value = "+$gained XP 🎉"

        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf("ts" to Timestamp.now(), "xpEarned" to gained)).await()
                firestore.collection("users").document(uid).update(mapOf(
                    "xp" to newXp, "kf_xp" to newXp, "level" to newLevel,
                )).await()
                updateStreak()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private suspend fun updateStreak() {
        try {
            val u        = firestore.collection("users").document(uid).get().await()
            val last     = u.getTimestamp("lastKurdiDate")
            val now      = Timestamp.now()
            val diffDays = if (last != null) (now.seconds - last.seconds) / 86400L else -1L
            val newStreak = when {
                last == null || diffDays > 1 -> 1
                diffDays == 1L               -> _streak.value + 1
                else                         -> _streak.value
            }
            _streak.value = newStreak
            firestore.collection("users").document(uid).update(mapOf(
                "streak"        to newStreak,
                "kf_streak"     to newStreak,
                "lastKurdiDate" to now,
            )).await()
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun clearToast() { _toast.value = null }

    // ── Günlük hedef — ilk tamamlanmamış dersi bul ──────────────────────────
    fun getNextLesson(): KfLesson? =
        _lessons.value.sortedBy { it.order }.firstOrNull { !it.completed }

    // ── AI Ders ──────────────────────────────────────────────────────────────
    fun generateAiLesson(apiKey: String, topic: String, level: String = "destpêk") {
        if (apiKey.isBlank() || topic.isBlank()) return
        _aiLoading.value = true; _aiError.value = null
        viewModelScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("model", "google/gemini-2.0-flash-001")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", "Kürtçe (Kurmancî) öğretmenisin. JSON formatında ders üret: {topic,exercises:[{type,ku,tr,options,answer}]}. Sadece JSON döndür.")
                        })
                        put(JSONObject().apply { put("role", "user"); put("content", "Konu: $topic | Seviye: $level") })
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
                    it.doOutput = true; it.connectTimeout = 15000; it.readTimeout = 30000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val raw = conn.inputStream.bufferedReader().readText()
                val txt = JSONObject(raw).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
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
            } catch (e: Exception) {
                _aiError.value = e.message ?: "Hata"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun clearAiLesson() { _aiLesson.value = null; _aiError.value = null }

    // ── Eski uyumluluk ───────────────────────────────────────────────────────
    fun startLesson(lesson: com.heftreng.app.data.model.KurdiLesson) {
        openLesson(lesson.id)
    }
}

// ── Mock verisi (kf_units Firestore'da boşsa) ─────────────────────────────────
private val MOCK_UNITS = listOf(
    KfUnit("u1", "Destpêk",    "Temel Dersler",    "🌱", "#8B5CF6", 1),
    KfUnit("u2", "Jimare",     "Sayılar",          "🔢", "#1CB0F6", 2),
    KfUnit("u3", "Reng",       "Renkler",          "🎨", "#FF9600", 3),
    KfUnit("u4", "Malbat",     "Aile",             "👨‍👩‍👧", "#22C55E", 4),
    KfUnit("u5", "Xwarin",     "Yemek",            "🍎", "#EF4444", 5),
)

private val MOCK_LESSONS = listOf(
    KfLesson("l1", "u1", "Merhaba!", "Silav!", "👋", 10, 1),
    KfLesson("l2", "u1", "Nasılsın?", "Çawa yî?", "😊", 15, 2),
    KfLesson("l3", "u2", "1-10 Arası", "Yek-Deh", "🔢", 20, 1),
    KfLesson("l4", "u2", "11-20 Arası", "Yazde-Bîst", "🔢", 20, 2),
    KfLesson("l5", "u3", "Temel Renkler", "Rengên Bingehîn", "🎨", 15, 1),
    KfLesson("l6", "u4", "Anne-Baba", "Dê-Bav", "👨‍👩‍👧", 20, 1),
    KfLesson("l7", "u5", "Meyve", "Fêkî", "🍎", 15, 1),
    KfLesson("l8", "u5", "Sebze", "Sebze", "🥦", 15, 2),
)

private val MOCK_VOCAB = mapOf(
    "l1" to listOf(
        KfVocab("v1","Silav",  "si-lav",   "Merhaba",      "👋"),
        KfVocab("v2","Spas",   "spas",     "Teşekkürler",  "🙏"),
        KfVocab("v3","Baş e",  "baş-e",    "İyiyim",       "😊"),
        KfVocab("v4","Xweş bî","xweş-bî",  "Hoşça kal",    "👋"),
    ),
    "l2" to listOf(
        KfVocab("v5","Çawa",    "ça-wa",  "Nasıl",     "🤔"),
        KfVocab("v6","Baş",     "baş",   "İyi",        "👍"),
        KfVocab("v7","Nexweş",  "ne-xweş","Hasta",     "🤒"),
        KfVocab("v8","Pirr baş","pirr-baş","Çok iyi",  "🌟"),
    ),
    "l5" to listOf(
        KfVocab("vc1","Sor",  "sor",  "Kırmızı","🔴"),
        KfVocab("vc2","Şîn",  "şîn",  "Mavi",   "🔵"),
        KfVocab("vc3","Kesk",  "kesk","Yeşil",   "🟢"),
        KfVocab("vc4","Zer",   "zer", "Sarı",    "🟡"),
        KfVocab("vc5","Spî",   "spî", "Beyaz",   "⚪"),
        KfVocab("vc6","Reş",   "reş", "Siyah",   "⚫"),
    ),
)

private val MOCK_EXERCISES = mapOf(
    "l1" to listOf(
        KfExercise("e1","mcq","«Silav» ne demek?","Merhaba","Teşekkür","Günaydın","İyi akşamlar","Merhaba"),
        KfExercise("e2","mcq","«Spas» ne demek?","Teşekkürler","Merhaba","İyi","Selam","Teşekkürler"),
        KfExercise("e3","fill","Silav, navê min ___ e.","","","","","Reng", listOf("Baş","Spas","Xweş")),
    ),
    "l2" to listOf(
        KfExercise("e4","mcq","«Baş» ne demek?","İyi","Hasta","Nasıl","Teşekkür","İyi"),
        KfExercise("e5","mcq","«Nexweş» ne demek?","Hasta","İyi","Nasıl","Teşekkür","Hasta"),
    ),
    "l5" to listOf(
        KfExercise("ec1","mcq","«Sor» ne demek?","Kırmızı","Mavi","Yeşil","Sarı","Kırmızı"),
        KfExercise("ec2","mcq","«Kesk» ne demek?","Yeşil","Siyah","Beyaz","Mavi","Yeşil"),
        KfExercise("ec3","mcq","«Şîn» ne demek?","Mavi","Kırmızı","Sarı","Beyaz","Mavi"),
    ),
)
