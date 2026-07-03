package com.heftreng.app.util

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

// ═══════════════════════════════════════════════════════════════
//  MentionHelper — @kullanıcı arama mantığı, ortak yardımcı
//
//  Önceden bu mantık sadece FeedViewModel içindeydi (searchMentionUsers).
//  Mesajlara da mention eklenirken kod tekrarı olmasın diye buraya taşındı.
//  Hem FeedViewModel hem MessagesViewModel bu fonksiyonu çağırır.
// ═══════════════════════════════════════════════════════════════
object MentionHelper {

    data class MentionUser(
        val uid      : String,
        val name     : String,
        val photoURL : String = "",
    )

    /** @sonrası yazılan metne göre Firestore users koleksiyonundan displayName prefix araması yapar. */
    suspend fun searchUsers(firestore: FirebaseFirestore, query: String): List<MentionUser> {
        if (query.isBlank()) return emptyList()
        return try {
            val qLower = query.lowercase()
            val qCap   = query.replaceFirstChar { it.uppercase() }
            val seenIds = mutableSetOf<String>()
            val results = mutableListOf<MentionUser>()

            for (prefix in listOf(query, qLower, qCap).distinct()) {
                val snap = firestore.collection("users")
                    .orderBy("displayName")
                    .startAt(prefix).endAt(prefix + "\uF8FF")
                    .limit(8).get().await()
                for (doc in snap.documents) {
                    if (!seenIds.add(doc.id)) continue
                    val d = doc.data ?: continue
                    val name = (d["displayName"] as? String)?.ifBlank { null }
                        ?: (d["name"] as? String)?.ifBlank { null }
                        ?: continue
                    results += MentionUser(
                        uid      = doc.id,
                        name     = name,
                        photoURL = d["photoURL"] as? String ?: "",
                    )
                }
            }
            results.take(8)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
