package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import com.heftreng.app.data.model.KurdiLesson
import com.heftreng.app.data.model.KurdiExercise
import com.heftreng.app.data.model.KurdiWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons   = MutableStateFlow<List<KurdiLesson>>(emptyList())
    val lessons: StateFlow<List<KurdiLesson>> = _lessons

    private val _exercises = MutableStateFlow<List<KurdiExercise>>(emptyList())
    val exercises: StateFlow<List<KurdiExercise>> = _exercises

    private val _vocabList = MutableStateFlow<List<KurdiWord>>(emptyList())
    val vocabList: StateFlow<List<KurdiWord>> = _vocabList

    private val _xp        = MutableStateFlow(0)
    val xp: StateFlow<Int> = _xp

    private val _level     = MutableStateFlow(1)
    val level: StateFlow<Int> = _level

    private val _streak    = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak

    private val _loading   = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _lessonLoading = MutableStateFlow(false)
    val lessonLoading: StateFlow<Boolean> = _lessonLoading

    private val _error     = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    // Aktif ders
    private val _activeLesson = MutableStateFlow<KurdiLesson?>(null)
    val activeLesson: StateFlow<KurdiLesson?> = _activeLesson

    // Egzersiz indeksi
    private val _exerciseIndex = MutableStateFlow(0)
    val exerciseIndex: StateFlow<Int> = _exerciseIndex

    // Ders tamamlama state
    private val _lessonDone = MutableStateFlow(false)
    val lessonDone: StateFlow<Boolean> = _lessonDone

    init { loadData() }

    // ─── Ders + kullanıcı verisi yükle ──────────────────────────────────────
    fun loadData() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val uid = auth.currentUser?.uid ?: ""

                // Kullanıcı XP, streak, tamamlanan dersler
                val completedIds = if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()

                    // kf_xp ve xp — max al (tema iki alanı ayrı yazıyor)
                    _xp.value = maxOf(
                        (userDoc.getLong("xp")    ?: 0).toInt(),
                        (userDoc.getLong("kf_xp") ?: 0).toInt(),
                    )
                    _level.value  = (userDoc.getLong("level") ?: 1).toInt()
                    _streak.value = maxOf(
                        (userDoc.getLong("streak")    ?: 0).toInt(),
                        (userDoc.getLong("kf_streak") ?: 0).toInt(),
                    )

                    // kf_done array (tema: web tarafı) + kf_progress subcollection (Android)
                    val kfDoneArr = (userDoc.get("kf_done") as? List<*>)
                        ?.filterIsInstance<String>()?.toSet() ?: emptySet()
                    val kfProgressIds = try {
                        firestore.collection("users").document(uid)
                            .collection("kf_progress").get().await()
                            .documents.map { it.id }.toSet()
                    } catch (_: Exception) { emptySet() }

                    kfDoneArr + kfProgressIds
                } else emptySet()

                // Dersler
                val snap = firestore.collection("kf_lessons")
                    .orderBy("order", Query.Direction.ASCENDING).get().await()
                _lessons.value = snap.documents.mapNotNull { d ->
                    val data = d.data ?: return@mapNotNull null
                    KurdiLesson(
                        id       = d.id,
                        title    = data["title"] as? String ?: "",
                        subtitle = data["subtitle"] as? String ?: data["desc"] as? String ?: "",
                        type     = data["type"] as? String ?: "mcq",
                        xpReward = (data["xpReward"] as? Long)?.toInt() ?: 10,
                        order    = (data["order"] as? Long)?.toInt() ?: 0,
                        completed= completedIds.contains(d.id),
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _loading.value = false
            }
        }
    }

    // ─── Derse başla ────────────────────────────────────────────────────────
    fun startLesson(lesson: KurdiLesson) {
        _activeLesson.value  = lesson
        _exerciseIndex.value = 0
        _lessonDone.value    = false
        viewModelScope.launch {
            _lessonLoading.value = true
            try {
                val snap = firestore.collection("kf_exercises")
                    .whereEqualTo("lessonId", lesson.id)
                    .orderBy("order", Query.Direction.ASCENDING)
                    .get().await()
                _exercises.value = snap.documents.mapNotNull { d ->
                    val data = d.data ?: return@mapNotNull null
                    KurdiExercise(
                        id         = d.id,
                        lessonId   = data["lessonId"] as? String ?: "",
                        type       = data["type"] as? String ?: "mcq",
                        question   = data["question"] as? String ?: "",
                        questionTr = data["questionTr"] as? String ?: "",
                        answer     = data["answer"] as? String ?: "",
                        wrong      = (data["wrong"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        words      = (data["words"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                        tr         = data["tr"] as? String ?: "",
                    )
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _lessonLoading.value = false
            }
        }
    }

    fun nextExercise() {
        val next = _exerciseIndex.value + 1
        if (next >= _exercises.value.size) {
            _lessonDone.value = true
        } else {
            _exerciseIndex.value = next
        }
    }

    // ─── Ders tamamla ────────────────────────────────────────────────────────
    // Tema: users/{uid} → kf_done arrayUnion, kf_xp, xp, level
    //       users/{uid}/kf_progress/{lessonId} (Android)
    fun completeLesson(lessonId: String, gained: Int) {
        val uid = auth.currentUser?.uid ?: return
        val newXp    = _xp.value + gained
        val newLevel = (newXp / 100) + 1

        // Optimistic
        _xp.value    = newXp
        _level.value = newLevel
        _lessons.value = _lessons.value.map {
            if (it.id == lessonId) it.copy(completed = true) else it
        }

        viewModelScope.launch {
            try {
                // 1) kf_progress subcollection (Android native)
                firestore.collection("users").document(uid)
                    .collection("kf_progress").document(lessonId)
                    .set(mapOf(
                        "ts"      to Timestamp.now(),
                        "xpEarned"to gained,
                    )).await()

                // 2) kf_done arrayUnion + kf_xp + xp (web tema uyumu)
                firestore.collection("users").document(uid).update(mapOf(
                    "xp"      to newXp,
                    "kf_xp"   to newXp,
                    "level"   to newLevel,
                    "kf_done" to FieldValue.arrayUnion(lessonId),
                )).await()

                // Streak güncelle
                updateStreak(uid)
            } catch (e: Exception) {
                _error.value = e.message
            }
        }
    }

    private suspend fun updateStreak(uid: String) {
        try {
            val userDoc  = firestore.collection("users").document(uid).get().await()
            val lastDate = (userDoc.get("lastKurdiDate") as? Timestamp)?.toDate()
            val today    = Calendar.getInstance()
            val now      = Timestamp.now()

            val isToday  = lastDate?.let {
                val last = Calendar.getInstance().apply { time = it }
                last.get(Calendar.YEAR)         == today.get(Calendar.YEAR) &&
                last.get(Calendar.DAY_OF_YEAR)  == today.get(Calendar.DAY_OF_YEAR)
            } ?: false

            val isYesterday = lastDate?.let {
                val last = Calendar.getInstance().apply { time = it }
                last.get(Calendar.YEAR)         == today.get(Calendar.YEAR) &&
                last.get(Calendar.DAY_OF_YEAR)  == today.get(Calendar.DAY_OF_YEAR) - 1
            } ?: false

            val newStreak = when {
                isToday     -> _streak.value
                isYesterday -> _streak.value + 1
                else        -> 1
            }
            _streak.value = newStreak

            firestore.collection("users").document(uid).update(mapOf(
                "streak"        to newStreak,
                "kf_streak"     to newStreak,
                "lastKurdiDate" to now,
            )).await()
        } catch (_: Exception) {}
    }

    // ─── Ferheng (kelimeler) ─────────────────────────────────────────────────
    fun loadVocab(lessonId: String = "") {
        viewModelScope.launch {
            try {
                var q: Query = firestore.collection("kf_vocab")
                if (lessonId.isNotEmpty()) q = q.whereEqualTo("lessonId", lessonId)
                val snap = q.limit(100).get().await()
                _vocabList.value = snap.documents.mapNotNull { d ->
                    val data = d.data ?: return@mapNotNull null
                    KurdiWord(
                        id       = d.id,
                        ku       = data["ku"] as? String ?: "",
                        kp       = data["kp"] as? String ?: "",
                        tr       = data["tr"] as? String ?: "",
                        e        = data["e"] as? String ?: "",
                        lessonId = data["lessonId"] as? String ?: "",
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun resetLesson() {
        _activeLesson.value  = null
        _exercises.value     = emptyList()
        _exerciseIndex.value = 0
        _lessonDone.value    = false
    }

    fun clearError() { _error.value = null }
}
