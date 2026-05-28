package com.heftreng.app.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════
//  LibraryRepository — Yazar ve kütüphane kitabı işlemleri için tek kaynak
//
//  Adım 2.1 — ensureAuthorAndBook, daha önce hem FeedViewModel hem
//  LibraryViewModel içinde ayrı ayrı tanımlıydı; buraya taşındı.
//  Her iki ViewModel bu repository'yi inject eder.
// ═══════════════════════════════════════════════════════════════════════════

@Singleton
class LibraryRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
) {
    /**
     * Verilen yazar adı ve kitap adı için Firestore'da kayıt arar;
     * bulamazsa otomatik oluşturur. Her iki durumda da (authorId, bookId) döner.
     * Boş bir ad geçilirse karşılık gelen ID boş string olarak döner.
     */
    suspend fun ensureAuthorAndBook(
        authorName: String,
        bookName  : String,
    ): Pair<String, String> {
        val authorId = if (authorName.isNotBlank()) {
            findOrCreateAuthor(authorName.trim())
        } else ""

        val bookId = if (bookName.isNotBlank()) {
            findOrCreateBook(bookName.trim(), authorId, authorName.trim())
        } else ""

        return Pair(authorId, bookId)
    }

    private suspend fun findOrCreateAuthor(name: String): String {
        return try {
            val nameLower = name.lowercase().trim()
            // Önce nameLower ile ara
            var snap = firestore.collection("authors")
                .whereEqualTo("nameLower", nameLower)
                .limit(1).get().await()
            // Bulamazsa name field ile ara (admin panelinden eklenen yazarlar nameLower olmayabilir)
            if (snap.isEmpty) {
                snap = firestore.collection("authors")
                    .whereEqualTo("name", name.trim())
                    .limit(1).get().await()
            }
            if (!snap.isEmpty) {
                val doc = snap.documents[0]
                // nameLower eksikse retroaktif yaz
                if (doc.getString("nameLower") == null) {
                    try { firestore.collection("authors").document(doc.id)
                        .update("nameLower", nameLower) } catch (_: Exception) {}
                }
                doc.id
            } else {
                firestore.collection("authors").add(
                    hashMapOf(
                        "name"          to name.trim(),
                        "nameLower"     to nameLower,
                        "bio"           to "",
                        "photoURL"      to "",
                        "birthYear"     to 0,
                        "nationality"   to "",
                        "bookCount"     to 0,
                        "quoteCount"    to 0,
                        "reviewCount"   to 0,
                        "followerCount" to 0,
                        "autoCreated"   to true,
                        "ts"            to Timestamp.now(),
                    )
                ).await().id
            }
        } catch (_: Exception) { "" }
    }

    private suspend fun findOrCreateBook(
        title     : String,
        authorId  : String,
        authorName: String,
    ): String {
        return try {
            val titleLower = title.lowercase().trim()
            // Önce titleLower ile ara
            var snap = firestore.collection("library_books")
                .whereEqualTo("titleLower", titleLower)
                .limit(1).get().await()
            // Bulamazsa title field ile ara (admin panelinden eklenen kitaplar titleLower olmayabilir)
            if (snap.isEmpty) {
                snap = firestore.collection("library_books")
                    .whereEqualTo("title", title.trim())
                    .limit(1).get().await()
            }
            if (!snap.isEmpty) {
                val doc = snap.documents[0]
                // titleLower eksikse retroaktif yaz
                if (doc.getString("titleLower") == null) {
                    try { firestore.collection("library_books").document(doc.id)
                        .update("titleLower", titleLower) } catch (_: Exception) {}
                }
                doc.id
            } else {
                val bookId = firestore.collection("library_books").add(
                    hashMapOf(
                        "title"       to title,
                        "titleLower"  to title.lowercase(),
                        "authorId"    to authorId,
                        "authorName"  to authorName,
                        "coverImg"    to "",
                        "genre"       to "",
                        "publishYear" to 0,
                        "synopsis"    to "",
                        "pageCount"   to 0,
                        "quoteCount"  to 0,
                        "reviewCount" to 0,
                        "avgRating"   to 0f,
                        "autoCreated" to true,
                        "ts"          to Timestamp.now(),
                    )
                ).await().id
                // Yazar bookCount sayacını artır
                if (authorId.isNotBlank()) {
                    try {
                        firestore.collection("authors").document(authorId)
                            .update("bookCount", FieldValue.increment(1))
                    } catch (_: Exception) {}
                }
                bookId
            }
        } catch (_: Exception) { "" }
    }

    /**
     * Bir alıntıyı hem feed belgesine hem library_books alt koleksiyonuna yazar.
     * Sayaçları günceller.
     */
    suspend fun addQuoteToLibrary(
        libraryBookId  : String,
        libraryAuthorId: String,
        bookName       : String,
        authorName     : String,
        quoteText      : String,
        uid            : String,
        userDisplayName: String,
        userPhotoURL   : String,
        feedPostId     : String,
    ) {
        if (libraryBookId.isBlank()) return
        try {
            firestore.collection("library_books").document(libraryBookId)
                .collection("quotes").add(
                    hashMapOf(
                        "bookId"          to libraryBookId,
                        "authorId"        to libraryAuthorId,
                        "bookTitle"       to bookName,
                        "authorName"      to authorName,
                        "text"            to quoteText,
                        "uid"             to uid,
                        "userDisplayName" to userDisplayName,
                        "userPhotoURL"    to userPhotoURL,
                        "feedPostId"      to feedPostId,
                        "likesCount"      to 0,
                        "ts"              to Timestamp.now(),
                    )
                ).await()
            firestore.collection("library_books").document(libraryBookId)
                .update("quoteCount", FieldValue.increment(1))
            if (libraryAuthorId.isNotBlank()) {
                firestore.collection("authors").document(libraryAuthorId)
                    .update("quoteCount", FieldValue.increment(1))
            }
        } catch (_: Exception) {}
    }
}
