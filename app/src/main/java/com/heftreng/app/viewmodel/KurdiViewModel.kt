package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.heftreng.app.data.model.KurdiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth     : FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<KurdiLesson>>(emptyList())
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
                if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    val currentXp = (userDoc.getLong("xp") ?: 0).toInt()
                    _xp.value     = currentXp
                    _streak.value = (userDoc.getLong("streak") ?: 0).toInt()
                    _level.value  = xpToLevel(currentXp)
                }

                val snap = firestore.collection("kurdiLessons")
                    .orderBy("order", Query.Direction.ASCENDING).get().await()

                val completedIds = if (uid.isNotEmpty()) {
                    firestore.collection("kurdiProgress").document(uid)
                        .collection("completed").get().await()
                        .documents.map { it.id }.toSet()
                } else emptySet()

                _lessons.value = if (snap.documents.isNotEmpty()) {
                    snap.documents.mapNotNull { doc ->
                        val d = doc.data ?: return@mapNotNull null
                        KurdiLesson(
                            id        = doc.id,
                            title     = d["title"] as? String ?: "",
                            subtitle  = d["subtitle"] as? String ?: d["desc"] as? String ?: "",
                            type      = d["type"] as? String ?: "mcq",
                            xpReward  = (d["xpReward"] as? Long)?.toInt() ?: 10,
                            completed = completedIds.contains(doc.id),
                            order     = (d["order"] as? Long)?.toInt() ?: 0,
                        )
                    }
                } else sampleLessons.map { it.copy(completed = completedIds.contains(it.id)) }
            } catch (e: Exception) {
                _lessons.value = sampleLessons
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLesson(lesson: KurdiLesson) { /* Ders detay ekranına NavHost üzerinden git */ }

    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        val lesson = _lessons.value.find { it.id == lessonId } ?: return
        if (lesson.completed) return

        val gained  = lesson.xpReward
        val newXp   = _xp.value + gained
        val newLevel= xpToLevel(newXp)

        // Anlık UI
        _xp.value    = newXp
        _level.value = newLevel
        _lessons.value = _lessons.value.map { if (it.id == lessonId) it.copy(completed = true) else it }

        viewModelScope.launch {
            try {
                firestore.collection("kurdiProgress").document(uid)
                    .collection("completed").document(lessonId)
                    .set(mapOf("ts" to Timestamp.now(), "xpEarned" to gained)).await()

                val newStreak = updateStreak()
                _streak.value = newStreak

                firestore.collection("users").document(uid).update(mapOf(
                    "xp"     to newXp,
                    "level"  to newLevel,
                    "streak" to newStreak,
                )).await()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // Streak mantığı: ardışık gün kontrolü
    private suspend fun updateStreak(): Int {
        return try {
            val progDoc  = firestore.collection("kurdiProgress").document(uid).get().await()
            val lastDate = progDoc.getTimestamp("lastActivityDate")
            val now      = Timestamp.now()
            val diffDays = if (lastDate != null) (now.seconds - lastDate.seconds) / 86400 else -1L
            val newStreak = when {
                lastDate == null || diffDays > 1 -> 1
                diffDays == 1L                   -> _streak.value + 1
                else                             -> _streak.value // aynı gün, değişmez
            }
            firestore.collection("kurdiProgress").document(uid).set(
                mapOf("lastActivityDate" to now, "currentStreak" to newStreak),
                SetOptions.merge()
            ).await()
            newStreak
        } catch (e: Exception) { _streak.value }
    }

    // 100 XP = 1 level (Kurdî ekranında LevelProgress'te kullanılır)
    private fun xpToLevel(xp: Int) = (xp / 100) + 1

    private val sampleLessons = listOf(
        KurdiLesson("1", "Silav û Nasîn",    "Merhaba ve Tanışma",    "mcq",   10, false, 1),
        KurdiLesson("2", "Jimare",           "Sayılar 1-10",          "fill",  10, false, 2),
        KurdiLesson("3", "Reng",             "Renkler",               "match", 10, false, 3),
        KurdiLesson("4", "Malbat",           "Aile üyeleri",          "build", 10, false, 4),
        KurdiLesson("5", "Xwarinên rojane",  "Günlük yiyecekler",     "mcq",   15, false, 5),
        KurdiLesson("6", "Roj û Meh",        "Günler ve Aylar",       "fill",  15, false, 6),
    )
}
