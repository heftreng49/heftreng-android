package com.heftreng.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// ═══════════════════════════════════════════════════════════════════════════
//  CachedQuote — Öncelik 4: Offline cache (Room)
//
//  Keşfet → Alıntılar sekmesinde gösterilen kütüphane alıntılarının
//  (feed type="library_quote") yerel kopyası. İnternet yokken son çekilen
//  alıntılar buradan gösterilir. PostCard'ı render etmek için yeterli alanlar.
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "cached_quotes")
data class CachedQuote(
    @PrimaryKey
    val id              : String,
    val uid             : String  = "",
    val displayName     : String  = "",
    val photoURL        : String  = "",
    val quoteText       : String  = "",
    val bookName        : String  = "",
    val authorName      : String  = "",
    val likesCount      : Int     = 0,
    val commentsCount   : Int     = 0,
    val repostsCount    : Int     = 0,
    val coverImg        : String  = "",
    val libraryBookId   : String  = "",
    val libraryAuthorId : String  = "",
    val feedPostId      : String  = "",
    val tsMillis        : Long    = 0L,
    val cachedAt        : Long    = System.currentTimeMillis(),
)
