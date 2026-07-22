package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.heftreng.app.worker.KurdiReminderWorker
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

/**
 * ÖNCEDEN: ders bitince admin'in elle girdiği TEK sabit XP (lesson.xp) veriliyordu —
 * 15 egzersizli bir derste hepsi yanlış cevaplansa da, hepsi doğru cevaplansa da
 * AYNI XP veriliyordu, soru tipleri hiç fark etmiyordu. Artık her egzersiz tipi
 * için ayrı XP değeri var, sadece DOĞRU cevaplanan egzersizler XP katkısı yapıyor,
 * toplam otomatik hesaplanıyor (bkz. LessonScreen + completeLesson).
 * Tek doğruluk kaynağı burası — admin panelinde de aynı değerler gösterilir.
 */
object KfExerciseXp {
    const val FILL  = 2  // Boşluk doldurma
    const val BUILD = 3  // Cümle kurma
    const val MCQ   = 2  // Çoktan seçmeli
    const val MATCH = 2  // Eşleştirme

    fun forType(type: String): Int = when (type) {
        "fill"  -> FILL
        "build" -> BUILD
        "mcq"   -> MCQ
        "match" -> MATCH
        else    -> MCQ
    }
}

data class ActiveLesson(
    val lesson    : KfLesson,
    val vocab     : List<KfVocab>,
    val exercises : List<KfExercise>,
)

// ── Sözlük girişi ─────────────────────────────────────────────────────────────
data class DictEntry(
    val id       : String = "",
    val ku       : String = "",   // Kürtçe kelime
    val tr       : String = "",   // Türkçe karşılık
    val kp       : String = "",   // telaffuz
    val e        : String = "📖",  // emoji
    val category : String = "",   // kategori (isteğe bağlı)
)

// ── Dilbilgisi kuralı ─────────────────────────────────────────────────────────
data class GrammarRule(
    val id        : String = "",
    val title     : String = "",  // Kürtçe başlık
    val titleTr   : String = "",  // Türkçe başlık
    val content   : String = "",  // Kürtçe içerik
    val contentTr : String = "",  // Türkçe içerik
    val order     : Int    = 0,
)

data class LessonReport(
    val id              : String  = "",
    val lessonId        : String  = "",
    val lessonName      : String  = "",
    val message         : String  = "",
    val uid             : String  = "",
    val userName        : String  = "",
    val resolved        : Boolean = false,
    val ts              : Long    = 0L,
    val exerciseIndex   : Int?    = null,   // 1-tabanlı, null = vocab bölümü
    val exerciseType    : String  = "",     // mcq, fill, match, build
    val exerciseQuestion: String  = "",     // sorunun metni
)

@HiltViewModel
class KurdiViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val prefs by lazy { context.getSharedPreferences("hf_kurdi", Context.MODE_PRIVATE) }

    // Ders içeriği cache — aynı dersi tekrar Firestore'dan çekmez
    // Oturum boyunca geçerli, uygulama kapanınca temizlenir
    private data class LessonContent(
        val vocab     : List<KfVocab>,
        val exercises : List<KfExercise>,
    )
    private val lessonContentCache = mutableMapOf<String, LessonContent>()

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

    // ── Ödüllü reklam senaryoları için state ─────────────────────────────────
    // Son tamamlanan dersin XP'si (Çift XP senaryosu)
    private val _lastLessonXp = MutableStateFlow(0)
    val lastLessonXp = _lastLessonXp.asStateFlow()

    // Geçici olarak kilidi açılan ders ID'leri (Kilit Açma senaryosu)
    private val _tempUnlockedIds = MutableStateFlow<Set<String>>(emptySet())
    val tempUnlockedIds = _tempUnlockedIds.asStateFlow()

    // Streak bozuk mu? (Streak Kurtarma senaryosu)
    private val _streakBroke   = MutableStateFlow(false)
    val streakBroke = _streakBroke.asStateFlow()

    // Önceki streak (kurtarma için saklıyoruz)
    private var savedStreakBeforeBroke = 0

    private val _activeLesson = MutableStateFlow<ActiveLesson?>(null)
    val activeLesson = _activeLesson.asStateFlow()

    private val _loading      = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    // TTL cache — 5 dk içinde load() tekrar Firestore'a gitmesin
    private val KURDI_CACHE_TTL_MS = 5L * 60_000L
    private var lastKurdiFetchMs   = 0L

    private val _toast        = MutableStateFlow<String?>(null)
    val toast = _toast.asStateFlow()

    // ── AI ────────────────────────────────────────────────────────────────────

    // ── Sözlük ────────────────────────────────────────────────────────────────
    private val _dictEntries  = MutableStateFlow<List<DictEntry>>(emptyList())
    val dictEntries = _dictEntries.asStateFlow()
    private val _dictLoading  = MutableStateFlow(false)
    val dictLoading = _dictLoading.asStateFlow()

    // ── Dilbilgisi ────────────────────────────────────────────────────────────
    private val _grammarRules  = MutableStateFlow<List<GrammarRule>>(emptyList())
    val grammarRules = _grammarRules.asStateFlow()
    private val _grammarLoading = MutableStateFlow(false)
    val grammarLoading = _grammarLoading.asStateFlow()

    // ── Liderlik Tablosu ──────────────────────────────────────────────────────
    data class LeaderEntry(
        val rank       : Int    = 0,
        val uid        : String = "",
        val displayName: String = "",
        val photoURL   : String = "",
        val kfXp       : Int    = 0,
        val isMe       : Boolean = false,
    )
    private val _leaderboard        = MutableStateFlow<List<LeaderEntry>>(emptyList())
    val leaderboard                 = _leaderboard.asStateFlow()
    private val _leaderboardLoading = MutableStateFlow(false)
    val leaderboardLoading          = _leaderboardLoading.asStateFlow()

    fun loadLeaderboard() {
        if (_leaderboardLoading.value) return
        viewModelScope.launch {
            _leaderboardLoading.value = true
            try {
                val myUid = auth.currentUser?.uid ?: ""
                val snap = firestore.collection("users")
                    .orderBy("kf_xp", Query.Direction.DESCENDING)
                    .limit(20)
                    .get()
                    .await()
                _leaderboard.value = snap.documents.mapIndexed { idx, doc ->
                    LeaderEntry(
                        rank        = idx + 1,
                        uid         = doc.id,
                        displayName = doc.getString("displayName")
                            ?: doc.getString("name") ?: "Kullanıcı",
                        photoURL    = doc.getString("photoURL") ?: "",
                        kfXp        = (doc.getLong("kf_xp") ?: 0L).toInt(),
                        isMe        = doc.id == myUid,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _leaderboardLoading.value = false
            }
        }
    }

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    // ── Ana yükleme — site ile tam senkron ────────────────────────────────────
    fun load(forceRefresh: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!forceRefresh && _loading.value) return
        if (!forceRefresh && (now - lastKurdiFetchMs) < KURDI_CACHE_TTL_MS
            && _units.value.isNotEmpty()) return
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
                lastKurdiFetchMs = System.currentTimeMillis()

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

                // Cache'de varsa Firestore'a gitme — 400 okuma tasarrufu
                val cached = lessonContentCache[lessonId]
                val vocabList: List<KfVocab>
                val exerciseList: List<KfExercise>
                if (cached != null) {
                    vocabList    = cached.vocab
                    exerciseList = cached.exercises
                } else {
                // kf_vocab yükle
                vocabList = try {
                    val snap = firestore.collection("kf_vocab")
                        .whereEqualTo("lessonId", lessonId).limit(200).get().await()
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
                exerciseList = try {
                    val snap = firestore.collection("kf_exercises")
                        .whereEqualTo("lessonId", lessonId).limit(200).get().await()
                    snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        // match tipi — yeni: [{ku,tr},...] eski: [[ku,tr],...] her ikisini destekle
                        val pairsRaw = d["pairs"] as? List<*>
                        val pairs = pairsRaw?.mapNotNull { item ->
                            when (item) {
                                is Map<*, *> -> {
                                    val a = item["ku"] as? String ?: return@mapNotNull null
                                    val b = item["tr"] as? String ?: return@mapNotNull null
                                    a to b
                                }
                                is List<*> -> {
                                    val a = item.getOrNull(0) as? String ?: return@mapNotNull null
                                    val b = item.getOrNull(1) as? String ?: return@mapNotNull null
                                    a to b
                                }
                                else -> null
                            }
                        } ?: emptyList()
                        // build tipi — words: [String]
                        val words = (d["words"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                        KfExercise(
                            id         = doc.id,
                            type       = d["type"]       as? String ?: "mcq",
                            question   = (d["question"]   as? String)?.takeIf { it.isNotBlank() }
                                         ?: d["ku"]       as? String ?: "",
                            questionTr = (d["questionTr"] as? String)?.takeIf { it.isNotBlank() }
                                         ?: d["tr"]       as? String ?: "",
                            optA       = d["optA"]       as? String ?: "",
                            optB       = d["optB"]       as? String ?: "",
                            optC       = d["optC"]       as? String ?: "",
                            optD       = d["optD"]       as? String ?: "",
                            answer     = (d["answer"]  as? String)?.takeIf { it.isNotBlank() }
                                         ?: (d["correct"] as? String)?.takeIf { it.isNotBlank() }
                                         ?: d["optA"] as? String ?: "",
                            wrong      = (d["wrong"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                            pairs      = pairs,
                            words      = words,
                            tr         = d["tr"] as? String ?: "",
                        )
                    }
                } catch (_: Exception) { emptyList() }
                // Yüklenen içeriği cache'e kaydet
                lessonContentCache[lessonId] = LessonContent(vocabList, exerciseList)
                } // end else (cache miss)

                // Mükerrer kelimeler ve egzersizler temizle
                val finalVocab: List<KfVocab> = vocabList
                    .distinctBy { it.ku.trim().lowercase() }
                    .ifEmpty { MOCK_VOCAB[lessonId] ?: emptyList() }

                val finalExercises: List<KfExercise> = exerciseList
                    .distinctBy { "${it.type}|${it.question.trim().lowercase()}" }
                    .ifEmpty { MOCK_EXERCISES[lessonId] ?: emptyList() }

                _activeLesson.value = ActiveLesson(
                    lesson    = lesson,
                    vocab     = finalVocab,
                    exercises = finalExercises,
                )

                // ── Görüntülenme sayacı — sadece ilk kez (henüz tamamlanmamışsa) ──
                if (!lesson.completed && uid.isNotEmpty()) {
                    viewModelScope.launch {
                        try {
                            firestore.collection("kf_lessons").document(lessonId)
                                .update("viewCount", com.google.firebase.firestore.FieldValue.increment(1))
                        } catch (_: Exception) {}
                    }
                }

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
    // earnedXp: LessonScreen'de her doğru cevaplanan egzersizin tipine göre
    // (bkz. KfExerciseXp) otomatik toplanan gerçek XP. -1 verilirse (geriye
    // uyumluluk için) admin'in elle girdiği sabit lesson.xp kullanılır.
    fun completeLesson(lessonId: String, earnedXp: Int = -1) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained   = if (earnedXp >= 0) earnedXp else lesson.xp
        val newXp    = _xp.value + gained
        val newLevel = maxOf(_level.value, newXp / 100 + 1)

        // Anında UI güncelle
        _xp.value          = newXp
        _level.value       = newLevel
        _lastLessonXp.value = gained  // Çift XP senaryosu için sakla
        _doneIds.value = _doneIds.value + lessonId
        _lessons.value = _lessons.value.map {
            if (it.id == lessonId) it.copy(completed = true) else it
        }
        _toast.value = "+$gained XP 🎉"
        KurdiReminderWorker.recordLessonDone(context)

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
            val todayCal = SimpleDateFormat("EEE MMM dd yyyy", Locale.US).parse(today)
            val lastCal  = if (lastDate.isNotBlank()) {
                try { SimpleDateFormat("EEE MMM dd yyyy", Locale.US).parse(lastDate) }
                catch (_: Exception) { null }
            } else null

            val diffDays = if (lastCal != null && todayCal != null) {
                ((todayCal.time - lastCal.time) / 86400000L)
            } else -1L

            val prevStreak = _streak.value
            val newStreak = when {
                lastCal == null || diffDays > 1 -> 1
                diffDays == 1L                  -> _streak.value + 1
                else                            -> _streak.value
            }

            // Streak bozuldu mu? → Kurtarma senaryosunu tetikle
            if (prevStreak > 1 && newStreak == 1 && diffDays > 1) {
                savedStreakBeforeBroke = prevStreak
                _streakBroke.value    = true
            }

            _streak.value = newStreak

            firestore.collection("users").document(uid).set(
                mapOf("kf_streak" to newStreak, "streak" to newStreak, "kf_lastDate" to today),
                SetOptions.merge()
            ).await()

        } catch (e: Exception) { e.printStackTrace() }
    }

    fun clearToast() { _toast.value = null }
    fun showToast(message: String) { _toast.value = message }

    /** Reklam (rewarded) o an yüklü değilken kullanıcıya bilgi vermek için. */
    fun showAdNotReadyToast(language: String) {
        _toast.value = if (language == "ku")
            "Reklam ne amade ye, ji kerema xwe piştre dîsa biceribîne"
        else
            "Reklam şu an hazır değil, birazdan tekrar dene"
    }

    // ── Senaryo 1: Çift XP — ders tamamlandıktan sonra XP'yi 2 katla ─────────
    fun doubleLastLessonXp() {
        if (_lastLessonXp.value <= 0) return
        val bonus = _lastLessonXp.value  // zaten bir kez verildi, bir tane daha ekle
        val newXp = _xp.value + bonus
        _xp.value    = newXp
        _level.value = maxOf(1, newXp / 100 + 1)
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .update("kf_xp", newXp, "xp", newXp)
                    .await()
            } catch (_: Exception) {}
        }
        _lastLessonXp.value = 0  // Kullanıldı, sıfırla
    }

    // ── Senaryo 2: Kilitli dersi geçici aç (sadece o oturum) ─────────────────
    fun tempUnlockLesson(lessonId: String) {
        _tempUnlockedIds.value = _tempUnlockedIds.value + lessonId
    }

    // ── Senaryo 3: Streak kurtarma — eski streak'i geri yükle ────────────────
    fun saveStreak() {
        if (savedStreakBeforeBroke <= 0) return
        val restored = savedStreakBeforeBroke
        _streak.value      = restored
        _streakBroke.value = false
        savedStreakBeforeBroke = 0
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                val today = SimpleDateFormat("EEE MMM dd yyyy", Locale.US).format(java.util.Date())
                firestore.collection("users").document(uid)
                    .set(mapOf("kf_streak" to restored, "streak" to restored, "kf_lastDate" to today),
                        SetOptions.merge()).await()
            } catch (_: Exception) {}
        }
    }

    fun dismissStreakBroke() {
        _streakBroke.value    = false
        savedStreakBeforeBroke = 0
    }

    fun getNextLesson(): KfLesson? =
        _lessons.value.sortedWith(compareBy({ it.unitId }, { it.order }))
            .firstOrNull { !it.completed }


    // ── Sözlük (kf_dict) ──────────────────────────────────────────────────────
    fun loadDict() {
        if (_dictEntries.value.isNotEmpty()) return // zaten yüklüyse tekrar çekme
        viewModelScope.launch {
            _dictLoading.value = true
            try {
                val snap = firestore.collection("kf_dict")
                    .orderBy("ku", Query.Direction.ASCENDING)
                    .limit(200).get().await()
                _dictEntries.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    DictEntry(
                        id       = doc.id,
                        ku       = d["ku"] as? String ?: "",
                        tr       = d["tr"] as? String ?: "",
                        kp       = d["kp"] as? String ?: "",
                        e        = d["e"]  as? String ?: "📖",
                        category = d["category"] as? String ?: "",
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _dictLoading.value = false }
        }
    }

    fun addDictEntry(ku: String, tr: String, kp: String, e: String, category: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val ref = firestore.collection("kf_dict").add(mapOf(
                    "ku" to ku, "tr" to tr, "kp" to kp, "e" to e,
                    "category" to category, "ts" to com.google.firebase.Timestamp.now(),
                )).await()
                _dictEntries.value = (_dictEntries.value + DictEntry(ref.id, ku, tr, kp, e, category))
                    .sortedBy { it.ku }
                onDone()
            } catch (e: Exception) { _toast.value = "Hata: ${e.message}" }
        }
    }

    fun deleteDictEntry(id: String) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_dict").document(id).delete().await()
                _dictEntries.value = _dictEntries.value.filter { it.id != id }
            } catch (e: Exception) { _toast.value = "Silinemedi" }
        }
    }

    // ── Dilbilgisi (kf_grammar) ───────────────────────────────────────────────
    fun loadGrammar() {
        if (_grammarRules.value.isNotEmpty()) return
        viewModelScope.launch {
            _grammarLoading.value = true
            try {
                val snap = firestore.collection("kf_grammar")
                    .orderBy("order", Query.Direction.ASCENDING)
                    .limit(200).get().await()
                _grammarRules.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    GrammarRule(
                        id        = doc.id,
                        title     = d["title"]     as? String ?: "",
                        titleTr   = d["titleTr"]   as? String ?: d["title"] as? String ?: "",
                        content   = d["content"]   as? String ?: "",
                        contentTr = d["contentTr"] as? String ?: d["content"] as? String ?: "",
                        order     = (d["order"] as? Long)?.toInt() ?: 0,
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _grammarLoading.value = false }
        }
    }

    fun addGrammarRule(title: String, titleTr: String, content: String, contentTr: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val order = (_grammarRules.value.maxOfOrNull { it.order } ?: 0) + 1
                val ref = firestore.collection("kf_grammar").add(mapOf(
                    "title" to title, "titleTr" to titleTr,
                    "content" to content, "contentTr" to contentTr,
                    "order" to order, "ts" to com.google.firebase.Timestamp.now(),
                )).await()
                _grammarRules.value = _grammarRules.value + GrammarRule(ref.id, title, titleTr, content, contentTr, order)
                onDone()
            } catch (e: Exception) { _toast.value = "Hata: ${e.message}" }
        }
    }

    fun deleteGrammarRule(id: String) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_grammar").document(id).delete().await()
                _grammarRules.value = _grammarRules.value.filter { it.id != id }
            } catch (e: Exception) { _toast.value = "Silinemedi" }
        }
    }

    fun updateGrammarRule(
        id: String, title: String, titleTr: String,
        content: String, contentTr: String, onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_grammar").document(id).set(mapOf(
                    "title" to title, "titleTr" to titleTr,
                    "content" to content, "contentTr" to contentTr,
                    "ts" to com.google.firebase.Timestamp.now(),
                ), com.google.firebase.firestore.SetOptions.merge()).await()
                _grammarRules.value = _grammarRules.value.map { r ->
                    if (r.id == id) r.copy(title = title, titleTr = titleTr, content = content, contentTr = contentTr)
                    else r
                }
                onDone()
            } catch (e: Exception) { _toast.value = "Güncellenemedi: ${e.message}" }
        }
    }

    fun reloadGrammar() {
        _grammarRules.value = emptyList()
        loadGrammar()
    }

    // ── Hata Raporları (kf_reports) ───────────────────────────────────────────
    private val _reports = MutableStateFlow<List<LessonReport>>(emptyList())
    val reports = _reports.asStateFlow()
    private val _reportsLoading = MutableStateFlow(false)
    val reportsLoading = _reportsLoading.asStateFlow()

    fun reportLessonError(
        lessonId: String,
        lessonName: String,
        message: String,
        exerciseIndex: Int? = null,
        exerciseType: String? = null,
        exerciseQuestion: String? = null,
        onDone: () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: "anonymous"
                val name = auth.currentUser?.displayName ?: "Anonim"
                val data = mutableMapOf<String, Any?>(
                    "lessonId"   to lessonId,
                    "lessonName" to lessonName,
                    "message"    to message,
                    "uid"        to uid,
                    "userName"   to name,
                    "resolved"   to false,
                    "ts"         to com.google.firebase.Timestamp.now(),
                )
                if (exerciseIndex != null) data["exerciseIndex"] = exerciseIndex + 1 // 1-tabanlı göster
                if (!exerciseType.isNullOrBlank()) data["exerciseType"] = exerciseType
                if (!exerciseQuestion.isNullOrBlank()) data["exerciseQuestion"] = exerciseQuestion
                firestore.collection("kf_reports").add(data).await()
                onDone()
            } catch (e: Exception) { _toast.value = "Gönderilemedi: ${e.message}" }
        }
    }

    fun loadReports() {
        viewModelScope.launch {
            _reportsLoading.value = true
            try {
                val snap = firestore.collection("kf_reports")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(100).get().await()
                _reports.value = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    LessonReport(
                        id               = doc.id,
                        lessonId         = d["lessonId"]         as? String ?: "",
                        lessonName       = d["lessonName"]       as? String ?: "",
                        message          = d["message"]          as? String ?: "",
                        uid              = d["uid"]              as? String ?: "",
                        userName         = d["userName"]         as? String ?: "",
                        resolved         = d["resolved"]         as? Boolean ?: false,
                        ts               = (d["ts"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L,
                        exerciseIndex    = (d["exerciseIndex"]   as? Long)?.toInt(),
                        exerciseType     = d["exerciseType"]     as? String ?: "",
                        exerciseQuestion = d["exerciseQuestion"] as? String ?: "",
                    )
                }
            } catch (e: Exception) { e.printStackTrace() }
            finally { _reportsLoading.value = false }
        }
    }

    fun resolveReport(id: String) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_reports").document(id).update("resolved", true).await()
                _reports.value = _reports.value.map { if (it.id == id) it.copy(resolved = true) else it }
            } catch (e: Exception) { _toast.value = "Güncellenemedi" }
        }
    }

    fun deleteReport(id: String) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_reports").document(id).delete().await()
                _reports.value = _reports.value.filter { it.id != id }
            } catch (e: Exception) { _toast.value = "Silinemedi" }
        }
    }
    fun addUnit(
        id: String, ttl: String, nameKu: String,
        desc: String, icon: String, color: String,
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                val order = (_units.value.maxOfOrNull { it.order } ?: 0) + 1
                firestore.collection("kf_units").document(id).set(mapOf(
                    "id" to id, "ttl" to ttl, "nameKu" to nameKu,
                    "desc" to desc, "icon" to icon, "color" to color, "order" to order,
                )).await()
                _units.value = (_units.value + KfUnit(id, ttl, nameKu, desc, "", icon, color, order))
                    .sortedBy { it.order }
                onDone()
            } catch (e: Exception) { onError(e.message ?: "Eklenemedi") }
        }
    }

    // ── Admin: ünite güncelle ─────────────────────────────────────────────────
    fun updateUnit(
        id: String, ttl: String, nameKu: String,
        desc: String, icon: String, color: String,
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_units").document(id).update(mapOf(
                    "ttl" to ttl, "nameKu" to nameKu,
                    "desc" to desc, "icon" to icon, "color" to color,
                )).await()
                _units.value = _units.value.map {
                    if (it.id == id) it.copy(ttl = ttl, nameKu = nameKu, desc = desc, icon = icon, color = color)
                    else it
                }
                onDone()
            } catch (e: Exception) { onError(e.message ?: "Güncellenemedi") }
        }
    }

    // ── Admin: ünite sil ──────────────────────────────────────────────────────
    fun deleteUnit(id: String, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Bu üniteye bağlı ders var mı kontrol et
                val bound = _lessons.value.count { it.unitId == id }
                if (bound > 0) { onError("Bu üniteye bağlı $bound ders var. Önce dersleri silin."); return@launch }
                firestore.collection("kf_units").document(id).delete().await()
                _units.value = _units.value.filter { it.id != id }
                onDone()
            } catch (e: Exception) { onError(e.message ?: "Silinemedi") }
        }
    }
    fun deleteLesson(lessonId: String, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // Derse ait vocab ve exercises'i de sil
                val db = firestore
                listOf("kf_vocab", "kf_exercises").forEach { col ->
                    val snap = db.collection(col).whereEqualTo("lessonId", lessonId).limit(200).get().await()
                    snap.documents.forEach { it.reference.delete().await() }
                }
                db.collection("kf_lessons").document(lessonId).delete().await()
                _lessons.value = _lessons.value.filter { it.id != lessonId }
                onDone()
            } catch (e: Exception) { onError(e.message ?: "Silinemedi") }
        }
    }

    // ── Admin: ders güncelle ──────────────────────────────────────────────────
    fun updateLesson(
        lessonId: String,
        nameTr: String, nameKu: String, emoji: String,
        xp: Int, order: Int, unitId: String,
        onDone: () -> Unit, onError: (String) -> Unit,
    ) {
        viewModelScope.launch {
            try {
                firestore.collection("kf_lessons").document(lessonId).update(mapOf(
                    "nameTr" to nameTr, "nameKu" to nameKu, "emoji" to emoji,
                    "xp" to xp, "order" to order, "unitId" to unitId,
                )).await()
                _lessons.value = _lessons.value.map {
                    if (it.id == lessonId) it.copy(nameTr = nameTr, nameKu = nameKu,
                        emoji = emoji, xp = xp, order = order, unitId = unitId)
                    else it
                }
                onDone()
            } catch (e: Exception) { onError(e.message ?: "Güncellenemedi") }
        }
    }
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

    // ── JSON'dan Ders İçe Aktar ───────────────────────────────────────────────
    data class ImportResult(
        val unitsAdded    : Int          = 0,
        val lessonsAdded  : Int          = 0,
        val vocabAdded    : Int          = 0,
        val exercisesAdded: Int          = 0,
        val skipped       : Int          = 0,
        val errors        : List<String> = emptyList(),
    )
    // Çakışma modu: "overwrite" | "skip"
    private val _importResult = MutableStateFlow<ImportResult?>(null)
    val importResult = _importResult.asStateFlow()
    private val _importing    = MutableStateFlow(false)
    val importing = _importing.asStateFlow()
    private val _exportJson   = MutableStateFlow<String?>(null)
    val exportJson = _exportJson.asStateFlow()
    fun clearImportResult() { _importResult.value = null }
    fun clearExportJson()   { _exportJson.value   = null }

    // JSON doğrulama — import öncesi yapıyı kontrol et
    data class JsonPreview(
        val unitCount    : Int,
        val lessonCount  : Int,
        val vocabTotal   : Int,
        val exerciseTotal: Int,
        val warnings     : List<String>,
        val isValid      : Boolean,
    )

    fun validateJson(jsonString: String): JsonPreview {
        return try {
            val root     = org.json.JSONObject(jsonString)
            val warnings = mutableListOf<String>()
            var unitCount = 0; var lessonCount = 0; var vocabTotal = 0; var exTotal = 0

            // Tekil unit veya units array
            when {
                root.has("units") -> unitCount = root.getJSONArray("units").length()
                root.has("unit")  -> unitCount = 1
                else              -> warnings.add("⚠️ 'unit' veya 'units' alanı bulunamadı")
            }

            // Dersler
            val la = root.optJSONArray("lessons") ?: org.json.JSONArray()
            lessonCount = la.length()
            if (lessonCount == 0) warnings.add("⚠️ 'lessons' boş veya yok")

            for (li in 0 until la.length()) {
                val l = la.getJSONObject(li)
                if (l.optString("id").isBlank())    warnings.add("Ders $li: 'id' boş")
                if (l.optString("nameTr").isBlank()) warnings.add("Ders $li: 'nameTr' boş")
                vocabTotal += l.optJSONArray("vocab")?.length() ?: 0
                val exArr   = l.optJSONArray("exercises") ?: org.json.JSONArray()
                exTotal    += exArr.length()
                if (exArr.length() == 0) warnings.add("Ders ${l.optString("id","$li")}: egzersiz yok")
            }

            JsonPreview(unitCount, lessonCount, vocabTotal, exTotal, warnings, isValid = warnings.none { it.startsWith("⚠️") })
        } catch (e: Exception) {
            JsonPreview(0, 0, 0, 0, listOf("❌ JSON parse hatası: ${e.message}"), isValid = false)
        }
    }

    fun importFromJson(
        jsonString       : String,
        overwriteExisting: Boolean = true,
        targetUnitId     : String = "",   // Boşsa JSON'daki unitId kullanılır
        targetLessonId   : String = "",   // Boşsa JSON'daki id kullanılır
    ) {
        if (jsonString.isBlank()) return
        _importing.value = true
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            var unitsAdded = 0; var lessonsAdded = 0; var vocabAdded = 0; var exAdded = 0; var skipped = 0

            suspend fun importUnit(u: org.json.JSONObject) {
                val uid  = u.optString("id").ifBlank { "u_${System.currentTimeMillis()}" }
                val uRef = firestore.collection("kf_units").document(uid)
                val ord  = (_units.value.maxOfOrNull { it.order } ?: 0) + 1
                if (!uRef.get().await().exists()) {
                    uRef.set(mapOf("id" to uid, "ttl" to u.optString("ttl"),
                        "nameKu" to u.optString("nameKu"), "desc" to u.optString("desc"),
                        "icon" to u.optString("icon","📖"), "color" to u.optString("color","#8B5CF6"),
                        "order" to u.optInt("order", ord))).await()
                    // ✅ desc eksikti — state'e eklenirken kayboluyordu, ekranda boş görünüyordu
                    _units.value = (_units.value + KfUnit(id=uid, ttl=u.optString("ttl"),
                        nameKu=u.optString("nameKu"), desc=u.optString("desc"), icon=u.optString("icon","📖"),
                        color=u.optString("color","#8B5CF6"), order=u.optInt("order",ord)))
                        .sortedBy { it.order }
                    unitsAdded++
                }
            }

            try {
                val root = org.json.JSONObject(jsonString)

                // Tekil unit veya units array
                if (root.has("units")) {
                    val ua = root.getJSONArray("units")
                    for (i in 0 until ua.length()) {
                        try { importUnit(ua.getJSONObject(i)) }
                        catch (e: Exception) { errors.add("Ünite $i: ${e.message}") }
                    }
                } else if (root.has("unit")) {
                    try { importUnit(root.getJSONObject("unit")) }
                    catch (e: Exception) { errors.add("Ünite: ${e.message}") }
                }

                val la = root.optJSONArray("lessons") ?: org.json.JSONArray()
                for (li in 0 until la.length()) {
                    val l    = la.getJSONObject(li)
                    val lid  = targetLessonId.ifBlank { l.optString("id").ifBlank { "l_${System.currentTimeMillis()}_$li" } }
                    val lUnit = targetUnitId.ifBlank { l.optString("unitId").ifBlank {
                        root.optJSONObject("unit")?.optString("id")
                            ?: (if (root.has("units")) root.getJSONArray("units").optJSONObject(0)?.optString("id") else null)
                            ?: ""
                    } }
                    try {
                        val lessonRef = firestore.collection("kf_lessons").document(lid)
                        val exists    = lessonRef.get().await().exists()

                        if (exists && !overwriteExisting) { skipped++; continue }

                        lessonRef.set(mapOf("id" to lid, "unitId" to lUnit,
                            "nameTr" to l.optString("nameTr"), "nameKu" to l.optString("nameKu"),
                            "emoji" to l.optString("emoji","📖"), "xp" to l.optInt("xp",10),
                            "order" to l.optInt("order", li+1), "tip" to l.optString("tip"))).await()

                        val nl = KfLesson(id=lid, unitId=lUnit, nameTr=l.optString("nameTr"),
                            nameKu=l.optString("nameKu"), emoji=l.optString("emoji","📖"),
                            xp=l.optInt("xp",10), order=l.optInt("order",li+1))
                        _lessons.value = if (_lessons.value.any { it.id == lid })
                            _lessons.value.map { if (it.id == lid) nl else it }
                        else (_lessons.value + nl).sortedBy { it.order }
                        lessonsAdded++

                        // Eski vocab/exercise temizle (overwrite modunda)
                        if (exists) {
                            firestore.collection("kf_vocab").whereEqualTo("lessonId",lid).limit(200).get().await()
                                .documents.forEach { it.reference.delete() }
                            firestore.collection("kf_exercises").whereEqualTo("lessonId",lid).limit(200).get().await()
                                .documents.forEach { it.reference.delete() }
                        }

                        val va = l.optJSONArray("vocab") ?: org.json.JSONArray()
                        for (vi in 0 until va.length()) {
                            val v = va.getJSONObject(vi)
                            firestore.collection("kf_vocab").add(mapOf("lessonId" to lid,
                                "ku" to v.optString("ku"), "kp" to v.optString("kp"),
                                "tr" to v.optString("tr"), "e" to v.optString("e"), "order" to vi)).await()
                            vocabAdded++
                        }

                        val ea = l.optJSONArray("exercises") ?: org.json.JSONArray()
                        for (ei in 0 until ea.length()) {
                            val ex   = ea.getJSONObject(ei)
                            val type = ex.optString("type","mcq")
                            val data = mutableMapOf<String,Any>("lessonId" to lid, "type" to type, "order" to ei)
                            when (type) {
                                "mcq"   -> { data["question"]=ex.optString("question"); data["questionTr"]=ex.optString("questionTr")
                                             data["optA"]=ex.optString("optA"); data["optB"]=ex.optString("optB")
                                             data["optC"]=ex.optString("optC"); data["optD"]=ex.optString("optD")
                                             data["answer"]=ex.optString("answer") }
                                "fill"  -> { data["question"]=ex.optString("question")
                                             // ✅ questionTr kaydediliyordu eksikti
                                             val qTr = ex.optString("questionTr","")
                                             if (qTr.isNotBlank()) data["questionTr"] = qTr
                                             data["answer"]=ex.optString("answer")
                                             val opts=ex.optJSONArray("options")
                                             if (opts!=null) data["wrong"]=(0 until opts.length())
                                                 .map{opts.getString(it)}.filter{it!=ex.optString("answer")} }
                                "match" -> { val pairsArr=ex.optJSONArray("pairs")
                                             if (pairsArr!=null) data["pairs"]=(0 until pairsArr.length())
                                                 .mapNotNull { pi ->
                                                     // ✅ hem [[ku,tr]] hem [{ku,tr}] formatını destekle
                                                     val item = pairsArr.opt(pi)
                                                     when (item) {
                                                         is org.json.JSONArray -> if (item.length() >= 2) listOf(item.getString(0), item.getString(1)) else null
                                                         is org.json.JSONObject -> {
                                                             val k = item.optString("ku").ifBlank { null }
                                                             val t = item.optString("tr").ifBlank { null }
                                                             if (k != null && t != null) listOf(k, t) else null
                                                         }
                                                         else -> null
                                                     }
                                                 }.filterNotNull() }
                                "build" -> { data["tr"]=ex.optString("tr"); data["answer"]=ex.optString("answer")
                                             val words=ex.optJSONArray("words")
                                             if (words!=null) data["words"]=(0 until words.length()).map{words.getString(it)} }
                            }
                            firestore.collection("kf_exercises").add(data).await()
                            exAdded++
                        }
                    } catch (e: Exception) { errors.add("Ders $lid: ${e.message}") }
                }
            } catch (e: Exception) { errors.add("JSON parse: ${e.message}") }
            _importResult.value = ImportResult(unitsAdded, lessonsAdded, vocabAdded, exAdded, skipped, errors)
            _importing.value    = false
        }
    }

    // ── Dersleri JSON olarak Dışa Aktar ──────────────────────────────────────
    fun exportLessonsAsJson(unitId: String? = null) {
        viewModelScope.launch {
            try {
                val units   = if (unitId != null) _units.value.filter { it.id == unitId } else _units.value
                val lessons = if (unitId != null) _lessons.value.filter { it.unitId == unitId } else _lessons.value

                val root = org.json.JSONObject()

                // units array
                val unitsArr = org.json.JSONArray()
                units.forEach { u ->
                    unitsArr.put(org.json.JSONObject().apply {
                        put("id", u.id); put("ttl", u.ttl); put("nameKu", u.nameKu)
                        put("desc", u.desc) // ✅ eskiden export edilmiyordu, içe aktarımda kayboluyordu
                        put("icon", u.icon); put("color", u.color); put("order", u.order)
                    })
                }
                root.put("units", unitsArr)

                val lessonsArr = org.json.JSONArray()
                for (lesson in lessons.sortedBy { it.order }) {
                    val lObj = org.json.JSONObject().apply {
                        put("id", lesson.id); put("unitId", lesson.unitId)
                        put("nameTr", lesson.nameTr); put("nameKu", lesson.nameKu)
                        put("emoji", lesson.emoji); put("xp", lesson.xp)
                        put("order", lesson.order); put("tip", lesson.tip)
                    }
                    // Vocab
                    try {
                        val vs = firestore.collection("kf_vocab").whereEqualTo("lessonId", lesson.id).limit(200).get().await()
                        val va = org.json.JSONArray()
                        vs.documents.sortedBy { (it.getLong("order") ?: 0) }.forEach { doc ->
                            va.put(org.json.JSONObject().apply {
                                put("ku", doc.getString("ku") ?: ""); put("kp", doc.getString("kp") ?: "")
                                put("tr", doc.getString("tr") ?: ""); put("e",  doc.getString("e")  ?: "")
                            })
                        }
                        lObj.put("vocab", va)
                    } catch (_: Exception) {}
                    // Exercises
                    try {
                        val es = firestore.collection("kf_exercises").whereEqualTo("lessonId", lesson.id).limit(200).get().await()
                        val ea = org.json.JSONArray()
                        es.documents.sortedBy { (it.getLong("order") ?: 0) }.forEach { doc ->
                            val d    = doc.data ?: return@forEach
                            val type = d["type"] as? String ?: "mcq"
                            val eObj = org.json.JSONObject().apply {
                                put("type", type)
                                when (type) {
                                    "mcq"   -> { put("question", d["question"]); put("questionTr", d["questionTr"])
                                                 put("optA", d["optA"]); put("optB", d["optB"])
                                                 put("optC", d["optC"]); put("optD", d["optD"]); put("answer", d["answer"]) }
                                    "fill"  -> { put("question", d["question"]); put("answer", d["answer"])
                                                 val wrong = (d["wrong"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
                                                 val opts = org.json.JSONArray()
                                                 opts.put(d["answer"]); wrong.forEach { opts.put(it) }
                                                 put("options", opts) }
                                    "match" -> { val pairsRaw = d["pairs"] as? List<*>
                                                 val pa = org.json.JSONArray()
                                                 pairsRaw?.forEach { p ->
                                                     when (p) {
                                                         is List<*>   -> if (p.size >= 2) pa.put(org.json.JSONArray().apply { put(p[0]); put(p[1]) })
                                                         is Map<*, *> -> { val ku = p["ku"] as? String; val tr = p["tr"] as? String
                                                                           if (ku != null && tr != null) pa.put(org.json.JSONArray().apply { put(ku); put(tr) }) }
                                                     }
                                                 }; put("pairs", pa) }
                                    "build" -> { put("tr", d["tr"]); put("answer", d["answer"])
                                                 val wa = org.json.JSONArray()
                                                 (d["words"] as? List<*>)?.forEach { wa.put(it) }; put("words", wa) }
                                }
                            }
                            ea.put(eObj)
                        }
                        lObj.put("exercises", ea)
                    } catch (_: Exception) {}
                    lessonsArr.put(lObj)
                }
                root.put("lessons", lessonsArr)
                _exportJson.value = root.toString(2) // pretty print
            } catch (e: Exception) {
                _exportJson.value = """{"error":"${e.message}"}"""
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
