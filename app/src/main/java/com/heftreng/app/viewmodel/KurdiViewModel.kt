package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.heftreng.app.data.model.KurdiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.json.JSONArray
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.heftreng.app.data.model.AiLesson
import com.heftreng.app.data.model.AiExercise
import javax.inject.Inject

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<KurdiLesson>>(SAMPLE_LESSONS)
    val lessons = _lessons.asStateFlow()

    private val _xp      = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak  = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level   = MutableStateFlow(1)
    val level = _level.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Kullanıcı stats
                if (uid.isNotEmpty()) {
                    try {
                        val u = firestore.collection("users").document(uid).get().await()
                        val xpA = (u.getLong("xp")     ?: 0).toInt()
                        val xpB = (u.getLong("kf_xp")  ?: 0).toInt()
                        val xpVal = maxOf(xpA, xpB)
                        val strA = (u.getLong("streak")    ?: 0).toInt()
                        val strB = (u.getLong("kf_streak") ?: 0).toInt()
                        _xp.value     = xpVal
                        _streak.value = maxOf(strA, strB)
                        _level.value  = maxOf(1, (u.getLong("level") ?: 1).toInt(), (xpVal / 100) + 1)
                    } catch (e: Exception) { /* stats yüklenemedi, varsayılan kullan */ }
                }

                // Tamamlanan dersler
                val completedIds: Set<String> = if (uid.isNotEmpty()) {
                    try {
                        firestore.collection("users").document(uid)
                            .collection("kf_progress").get().await()
                            .documents.map { it.id }.toSet()
                    } catch (e: Exception) { emptySet() }
                } else emptySet()

                // kf_lessons koleksiyonu
                try {
                    val snap = firestore.collection("kf_lessons")
                        .limit(100).get().await()
                    if (snap.documents.isNotEmpty()) {
                        _lessons.value = snap.documents.mapNotNull { doc ->
                            val d = doc.data ?: return@mapNotNull null
                            KurdiLesson(
                                id        = doc.id,
                                title     = d["title"]    as? String ?: "",
                                subtitle  = (d["subtitle"] as? String)?.takeIf { it.isNotBlank() }
                                             ?: d["desc"] as? String ?: "",
                                type      = d["type"]     as? String ?: "mcq",
                                xpReward  = (d["xpReward"] as? Long)?.toInt() ?: 10,
                                completed = doc.id in completedIds,
                                order     = (d["order"]   as? Long)?.toInt() ?: 0,
                            )
                        }.sortedBy { it.order }
                    } else {
                        _lessons.value = SAMPLE_LESSONS.map {
                            it.copy(completed = it.id in completedIds)
                        }
                    }
                } catch (e: Exception) {
                    _lessons.value = SAMPLE_LESSONS
                }

            } catch (e: Exception) {
                _lessons.value = SAMPLE_LESSONS
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLesson(lesson: KurdiLesson) { /* NavHost yönetir */ }

    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained   = lesson.xpReward
        val newXp    = _xp.value + gained
        val newLevel = maxOf(_level.value, (newXp / 100) + 1)

        _xp.value    = newXp
        _level.value = newLevel
        _lessons.value = _lessons.value.map {
            if (it.id == lessonId) it.copy(completed = true) else it
        }

        viewModelScope.launch {
            try {
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf("ts" to Timestamp.now(), "xpEarned" to gained)).await()

                firestore.collection("users").document(uid).update(mapOf(
                    "xp"    to newXp,
                    "kf_xp" to newXp,
                    "level" to newLevel,
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
    // ── KurdiAI — OpenRouter API ─────────────────────────────────────────────
    private val _aiLesson  = MutableStateFlow<AiLesson?>(null)
    val aiLesson = _aiLesson.asStateFlow()

    private val _aiLoading = MutableStateFlow(false)
    val aiLoading = _aiLoading.asStateFlow()

    private val _aiError   = MutableStateFlow<String?>(null)
    val aiError = _aiError.asStateFlow()

    fun generateAiLesson(apiKey: String, topic: String, level: String = "destpêk") {
        if (apiKey.isBlank() || topic.isBlank()) return
        _aiLoading.value = true
        _aiError.value   = null
        viewModelScope.launch {
            try {
                val payload = JSONObject().apply {
                    put("model", "google/gemini-2.0-flash-001")
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply { put("role","system"); put("content","Kürtçe (Kurmancî) öğretmenisin. JSON formatında ders üret: {topic,exercises:[{type,ku,tr,options,answer}]}. Sadece JSON döndür.") })
                        put(JSONObject().apply { put("role","user"); put("content","Konu: $topic | Seviye: $level") })
                    })
                    put("max_tokens", 1500)
                }
                val url  = URL("https://openrouter.ai/api/v1/chat/completions")
                val conn = url.openConnection() as HttpsURLConnection
                conn.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type","application/json")
                    setRequestProperty("Authorization","Bearer $apiKey")
                    setRequestProperty("HTTP-Referer","https://heft-reng.blogspot.com")
                    setRequestProperty("X-Title","Heftreng Kurdî")
                    doOutput = true; connectTimeout = 15000; readTimeout = 30000
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray()) }
                val raw = conn.inputStream.bufferedReader().readText()
                val txt = JSONObject(raw).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").getString("content")
                    .trim().removePrefix("```json").removePrefix("```").removeSuffix("```")
                val lj  = JSONObject(txt)
                val ea  = lj.optJSONArray("exercises") ?: JSONArray()
                _aiLesson.value = AiLesson(
                    topic     = topic, level = level,
                    exercises = (0 until ea.length()).map { i ->
                        val ex = ea.getJSONObject(i)
                        val op = ex.optJSONArray("options")
                        AiExercise(
                            type    = ex.optString("type","mcq"),
                            ku      = ex.optString("ku",""),
                            tr      = ex.optString("tr",""),
                            options = if (op!=null) (0 until op.length()).map { op.getString(it) } else emptyList(),
                            answer  = ex.optString("answer",""),
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

}

// Top-level sabit — init sırası sorunu yok
private val SAMPLE_LESSONS = listOf(
    KurdiLesson("1", "Silav û Nasîn",   "Merhaba ve Tanışma",  "mcq",   10, false, 1),
    KurdiLesson("2", "Jimare",          "Sayılar 1-10",        "fill",  10, false, 2),
    KurdiLesson("3", "Reng",            "Renkler",             "match", 10, false, 3),
    KurdiLesson("4", "Malbat",          "Aile üyeleri",        "build", 10, false, 4),
    KurdiLesson("5", "Xwarinên rojane", "Günlük yiyecekler",   "mcq",   15, false, 5),
    KurdiLesson("6", "Roj û Meh",       "Günler ve Aylar",     "fill",  15, false, 6),
    KurdiLesson("7", "Cih û Welat",     "Yerler ve Ülkeler",   "match", 15, false, 7),
    KurdiLesson("8", "Rengdêr",         "Sıfatlar",            "build", 20, false, 8),
)
