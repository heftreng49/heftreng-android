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
    val descTr : String = "",
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
    val id       : String       = "",
    val type     : String       = "mcq",
    val question : String       = "",
    val optA     : String       = "",
    val optB     : String       = "",
    val optC     : String       = "",
    val optD     : String       = "",
    val answer   : String       = "",
    val wrong    : List<String> = emptyList(),
)

data class ActiveLesson(
    val lesson    : KfLesson,
    val vocab     : List<KfVocab>,
    val exercises : List<KfExercise>,
)

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

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
            val (unitsSnap, lessonsSnap) = kotlinx.coroutines.coroutineScope {
                val u = kotlinx.coroutines.async {
                    firestore.collection("kf_units")
                        .orderBy("order", Query.Direction.ASCENDING).get().await()
                }
                val l = kotlinx.coroutines.async {
                    firestore.collection("kf_lessons").get().await()
                }
                u.await() to l.await()
            }

            val units = unitsSnap.documents.mapNotNull { doc ->
                val d = doc.data ?: return@mapNotNull null
                KfUnit(
                    id     = doc.id,
                    ttl    = (d["ttl"] as? String)?.takeIf { it.isNotBlank() }
                             ?: d["nameTr"] as? String ?: d["name"] as? String ?: "Ünite",
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
                _units.value   = units
                _lessons.value = allLessons
            } else {
                // kf_units boşsa kf_lessons'tan varsayılan ünite oluştur
                if (allLessons.isNotEmpty()) {
                    _units.value   = listOf(KfUnit("u_default", "Dersler", "", "📚", "#8B5CF6", 0))
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

                // kf_vocab + kf_exercises paralel yükle
                val (vocabSnap, exSnap) = kotlinx.coroutines.coroutineScope {
                    val v = kotlinx.coroutines.async {
                        try { firestore.collection("kf_vocab")
                            .whereEqualTo("lessonId", lessonId).get().await() }
                        catch (_: Exception) { null }
                    }
                    val e = kotlinx.coroutines.async {
                        try { firestore.collection("kf_exercises")
                            .whereEqualTo("lessonId", lessonId).get().await() }
                        catch (_: Exception) { null }
                    }
                    v.await() to e.await()
                }

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
                    )
                } ?: emptyList()

                _activeLesson.value = ActiveLesson(
                    lesson    = lesson,
                    vocab     = vocab.ifEmpty { MOCK_VOCAB[lessonId] ?: emptyList() },
                    exercises = exercises.ifEmpty { MOCK_EXERCISES[lessonId] ?: emptyList() },
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
            try {
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
            } catch (e: Exception) {
                _aiError.value = "Hata: ${e.message}"
            } finally {
                _aiLoading.value = false
            }
        }
    }

    fun clearAiLesson() { _aiLesson.value = null; _aiError.value = null }

    // ── Eski uyumluluk ────────────────────────────────────────────────────────
    fun startLesson(lesson: com.heftreng.app.data.model.KurdiLesson) = openLesson(lesson.id)
}

// ── Mock verisi (site ile aynı ID'ler) ────────────────────────────────────────
// Site _kpRenderMockUnits() ile tamamen eşleşiyor — kf_done senkronize çalışır
private val MOCK_UNITS = listOf(
    KfUnit("u1", "Destpêk",  "Temel Kelimeler", "🌱", "#58cc02", 1),
    KfUnit("u2", "Jimare",   "Sayılar",         "🔢", "#1cb0f6", 2),
    KfUnit("u3", "Reng",     "Renkler",         "🎨", "#ce82ff", 3),
    KfUnit("u4", "Malbat",   "Aile",            "👨‍👩‍👧", "#ff9600", 4),
    KfUnit("u5", "Xwarin",   "Yemek",           "🍎", "#ff4b4b", 5),
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
        KfVocab("v1","Silav", "si-lav",  "Merhaba",     "👋"),
        KfVocab("v2","Spas",  "spas",    "Teşekkürler", "🙏"),
        KfVocab("v3","Baş e", "baş-e",   "İyiyim",      "😊"),
        KfVocab("v4","Xweş bî","xweş-bî","Hoşça kal",   "👋"),
    ),
    "l2" to listOf(
        KfVocab("v5","Çawa",    "ça-wa",  "Nasıl",       "🤔"),
        KfVocab("v6","Baş",     "baş",    "İyi",         "👍"),
        KfVocab("v7","Nexweş",  "ne-xweş","Hasta",       "🤒"),
        KfVocab("v8","Pirr baş","pirr-baş","Çok iyi",    "🌟"),
    ),
    "l4" to listOf(
        KfVocab("vc1","Sor","sor","Kırmızı","🔴"),
        KfVocab("vc2","Şîn","şîn","Mavi",   "🔵"),
        KfVocab("vc3","Kesk","kesk","Yeşil", "🟢"),
        KfVocab("vc4","Zer","zer","Sarı",    "🟡"),
        KfVocab("vc5","Spî","spî","Beyaz",   "⚪"),
        KfVocab("vc6","Reş","reş","Siyah",   "⚫"),
    ),
)

private val MOCK_EXERCISES = mapOf(
    "l1" to listOf(
        KfExercise("e1","mcq","«Silav» ne demek?","Merhaba","Teşekkür","Günaydın","Selam","Merhaba"),
        KfExercise("e2","mcq","«Spas» ne demek?","Teşekkürler","Merhaba","İyi","Hoşça kal","Teşekkürler"),
        KfExercise("e3","mcq","«Baş e» ne demek?","İyiyim","Hasta","Teşekkür","Günaydın","İyiyim"),
    ),
    "l2" to listOf(
        KfExercise("e4","mcq","«Baş» ne demek?","İyi","Hasta","Nasıl","Teşekkür","İyi"),
        KfExercise("e5","mcq","«Nexweş» ne demek?","Hasta","İyi","Nasıl","Teşekkür","Hasta"),
    ),
    "l4" to listOf(
        KfExercise("ec1","mcq","«Sor» ne demek?","Kırmızı","Mavi","Yeşil","Sarı","Kırmızı"),
        KfExercise("ec2","mcq","«Kesk» ne demek?","Yeşil","Siyah","Beyaz","Mavi","Yeşil"),
        KfExercise("ec3","mcq","«Şîn» ne demek?","Mavi","Kırmızı","Sarı","Beyaz","Mavi"),
    ),
)
