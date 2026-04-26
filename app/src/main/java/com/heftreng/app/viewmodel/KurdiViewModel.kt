package com.heftreng.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.heftreng.app.data.model.KurdiLesson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class KurdiViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
) : ViewModel() {

    private val _lessons = MutableStateFlow<List<KurdiLesson>>(emptyList())
    val lessons = _lessons.asStateFlow()

    private val _xp     = MutableStateFlow(0)
    val xp = _xp.asStateFlow()

    private val _streak = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _level  = MutableStateFlow(1)
    val level = _level.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading = _loading.asStateFlow()

    val uid get() = auth.currentUser?.uid ?: ""

    init { load() }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            try {
                // Oncelikle sample dersleri yukle, Firestore beklenmeden gorunsun
                if (_lessons.value.isEmpty()) {
                    _lessons.value = sampleLessons
                    _loading.value = false
                }
                // Kullanici XP/streak
                if (uid.isNotEmpty()) {
                    val userDoc = firestore.collection("users").document(uid).get().await()
                    _xp.value     = (userDoc.getLong("xp") ?: 0).toInt()
                    _streak.value = (userDoc.getLong("streak") ?: 0).toInt()
                    _level.value  = (userDoc.getLong("level") ?: 1).toInt()
                }

                // Dersler — XML temasıyla aynı: kf_lessons koleksiyonu
                // orderBy index gerektiriyor, client-side sort yapıyoruz
                val snap = firestore.collection("kf_lessons")
                    .limit(100).get().await()

                // Tamamlanan dersler
                val completedIds = if (uid.isNotEmpty()) {
                    firestore.collection("users").document(uid)
                        .collection("kf_progress").get().await()
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
                } else {
                    // Firestore'da ders yoksa örnek dersler göster
                    sampleLessons
                }
            } catch (e: Exception) {
                _lessons.value = sampleLessons
                e.printStackTrace()
            } finally {
                _loading.value = false
            }
        }
    }

    fun startLesson(lesson: KurdiLesson) {
        // İleride ders ekranına geçiş yapılacak
    }

    fun completeLesson(lessonId: String) {
        if (uid.isEmpty()) return
        viewModelScope.launch {
            try {
                firestore.collection("kurdiProgress").document(uid)
                    .collection("completed").document(lessonId).set(mapOf("ts" to com.google.firebase.Timestamp.now())).await()
                val newXp = _xp.value + 10
                firestore.collection("users").document(uid).update(mapOf(
                    "xp"    to newXp,
                    "level" to (newXp / 100) + 1,
                )).await()
                _xp.value   = newXp
                _level.value = (newXp / 100) + 1
                _lessons.value = _lessons.value.map {
                    if (it.id == lessonId) it.copy(completed = true) else it
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private val sampleLessons = listOf(
        KurdiLesson("1", "Silav û Nasîn",    "Merhaba ve Tanışma",    "mcq",   10, false, 1),
        KurdiLesson("2", "Jimare",           "Sayılar 1-10",          "fill",  10, false, 2),
        KurdiLesson("3", "Reng",             "Renkler",               "match", 10, false, 3),
        KurdiLesson("4", "Malbat",           "Aile üyeleri",          "build", 10, false, 4),
        KurdiLesson("5", "Xwarinên rojane",  "Günlük yiyecekler",     "mcq",   15, false, 5),
        KurdiLesson("6", "Roj û Meh",        "Günler ve Aylar",       "fill",  15, false, 6),
    )
}
