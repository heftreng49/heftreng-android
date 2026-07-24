package com.heftreng.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// ═══════════════════════════════════════════════════════════════════════════
//  CachedBook — Offline cache (Room)
//
//  Kütüphane → Kitaplar sekmesi ve Kitap Detayı için yerel kopya.
//  Internet yokken son çekilen kitaplar buradan gösterilir.
//  version = 3 ile eklendi (bkz. HeftrengDatabase).
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "cached_books")
data class CachedBook(
    @PrimaryKey
    val id          : String,
    val title       : String  = "",
    val authorId    : String  = "",
    val authorName  : String  = "",
    val coverImg    : String  = "",
    val genre       : String  = "",
    val publishYear : Int     = 0,
    val synopsis    : String  = "",
    val pageCount   : Int     = 0,
    val quoteCount  : Int     = 0,
    val reviewCount : Int     = 0,
    val avgRating   : Float   = 0f,
    val cachedAt    : Long    = System.currentTimeMillis(),
)
