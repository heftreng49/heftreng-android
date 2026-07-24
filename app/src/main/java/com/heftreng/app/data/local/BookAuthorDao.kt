package com.heftreng.app.data.local

import androidx.room.*

// ─────────────────────────────────────────────────────────────────────────────
//  BookDao — cached_books tablosu için CRUD
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface BookDao {

    @Query("SELECT * FROM cached_books ORDER BY cachedAt DESC LIMIT :limit")
    suspend fun getCachedBooks(limit: Int = 50): List<CachedBook>

    @Query("SELECT * FROM cached_books WHERE id = :id LIMIT 1")
    suspend fun getCachedBook(id: String): CachedBook?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(books: List<CachedBook>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(book: CachedBook)

    @Query("DELETE FROM cached_books")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(books: List<CachedBook>) {
        clear()
        insertAll(books)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  AuthorDao — cached_authors tablosu için CRUD
// ─────────────────────────────────────────────────────────────────────────────

@Dao
interface AuthorDao {

    @Query("SELECT * FROM cached_authors ORDER BY name ASC LIMIT :limit")
    suspend fun getCachedAuthors(limit: Int = 50): List<CachedAuthor>

    @Query("SELECT * FROM cached_authors WHERE id = :id LIMIT 1")
    suspend fun getCachedAuthor(id: String): CachedAuthor?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(authors: List<CachedAuthor>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(author: CachedAuthor)

    @Query("DELETE FROM cached_authors")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(authors: List<CachedAuthor>) {
        clear()
        insertAll(authors)
    }
}
