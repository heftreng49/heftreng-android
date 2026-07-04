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
        val name     : String,     // displayName — sadece öneri barında gösterilir
        val username : String,     // @kullaniciadi — metne eklenirken ve mention'ın kendisinde bu kullanılır
        val photoURL : String = "",
    )

    /**
     * @sonrası yazılan metne göre Firestore users koleksiyonunda arama yapar.
     * Önce username üzerinden (asıl mention hedefi budur), sonra displayName üzerinden arar,
     * böylece kullanıcı ister adını ister kullanıcı adını yazsın sonuç bulunur.
     */
    suspend fun searchUsers(firestore: FirebaseFirestore, query: String): List<MentionUser> {
        if (query.isBlank()) return emptyList()
        return try {
            val qLower  = query.lowercase()
            val qCap    = query.replaceFirstChar { it.uppercase() }
            val seenIds = mutableSetOf<String>()
            val results = mutableListOf<MentionUser>()

            // 1) username üzerinden ara — mention'ın asıl hedefi olduğu için öncelikli.
            //    username'ler genelde lowercase saklanır, o yüzden qLower ile arıyoruz.
            val usernameSnap = firestore.collection("users")
                .orderBy("username")
                .startAt(qLower).endAt(qLower + "\uF8FF")
                .limit(8).get().await()
            for (doc in usernameSnap.documents) {
                if (!seenIds.add(doc.id)) continue
                val d = doc.data ?: continue
                val username = (d["username"] as? String)?.ifBlank { null } ?: continue
                val name = (d["displayName"] as? String)?.ifBlank { null }
                    ?: (d["name"] as? String)?.ifBlank { null }
                    ?: username
                results += MentionUser(
                    uid      = doc.id,
                    name     = name,
                    username = username,
                    photoURL = d["photoURL"] as? String ?: "",
                )
            }

            // 2) displayName üzerinden ara — kullanıcı gerçek adını yazdıysa da bulunsun.
            //    username alanı olmayan (eski) kayıtlar mention hedefi olamayacağı için atlanır.
            if (results.size < 8) {
                for (prefix in listOf(query, qLower, qCap).distinct()) {
                    val snap = firestore.collection("users")
                        .orderBy("displayName")
                        .startAt(prefix).endAt(prefix + "\uF8FF")
                        .limit(8).get().await()
                    for (doc in snap.documents) {
                        if (!seenIds.add(doc.id)) continue
                        val d = doc.data ?: continue
                        val username = (d["username"] as? String)?.ifBlank { null } ?: continue
                        val name = (d["displayName"] as? String)?.ifBlank { null }
                            ?: (d["name"] as? String)?.ifBlank { null }
                            ?: username
                        results += MentionUser(
                            uid      = doc.id,
                            name     = name,
                            username = username,
                            photoURL = d["photoURL"] as? String ?: "",
                        )
                    }
                }
            }

            results.take(8)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
