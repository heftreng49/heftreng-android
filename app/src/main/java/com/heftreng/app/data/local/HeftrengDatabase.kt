package com.heftreng.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// ═══════════════════════════════════════════════════════════════════════════
//  HeftrengDatabase — Offline cache (Room)
//
//  version 1 → 2 : cached_quotes eklendi
//  version 2 → 3 : cached_books + cached_authors eklendi (Sorun 1 fix)
//
//  DatabaseModule'de .addMigrations(MIGRATION_2_3) eklemeyi unutma.
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [
        CachedQuote::class,
        CachedBook::class,
        CachedAuthor::class,
    ],
    version      = 3,
    exportSchema = false,
)
abstract class HeftrengDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
    abstract fun bookDao(): BookDao
    abstract fun authorDao(): AuthorDao
}

// ── Migration 2 → 3 ──────────────────────────────────────────────────────────
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_books (
                id          TEXT NOT NULL PRIMARY KEY,
                title       TEXT NOT NULL DEFAULT '',
                authorId    TEXT NOT NULL DEFAULT '',
                authorName  TEXT NOT NULL DEFAULT '',
                coverImg    TEXT NOT NULL DEFAULT '',
                genre       TEXT NOT NULL DEFAULT '',
                publishYear INTEGER NOT NULL DEFAULT 0,
                synopsis    TEXT NOT NULL DEFAULT '',
                pageCount   INTEGER NOT NULL DEFAULT 0,
                quoteCount  INTEGER NOT NULL DEFAULT 0,
                reviewCount INTEGER NOT NULL DEFAULT 0,
                avgRating   REAL NOT NULL DEFAULT 0.0,
                cachedAt    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cached_authors (
                id            TEXT NOT NULL PRIMARY KEY,
                name          TEXT NOT NULL DEFAULT '',
                bio           TEXT NOT NULL DEFAULT '',
                photoUrl      TEXT NOT NULL DEFAULT '',
                birthYear     INTEGER NOT NULL DEFAULT 0,
                nationality   TEXT NOT NULL DEFAULT '',
                bookCount     INTEGER NOT NULL DEFAULT 0,
                quoteCount    INTEGER NOT NULL DEFAULT 0,
                reviewCount   INTEGER NOT NULL DEFAULT 0,
                followerCount INTEGER NOT NULL DEFAULT 0,
                cachedAt      INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }
}
