package com.heftreng.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// ═══════════════════════════════════════════════════════════════════════════
//  CachedAuthor — Offline cache (Room)
//
//  Kütüphane → Yazarlar sekmesi ve Yazar Detayı için yerel kopya.
//  version = 3 ile eklendi (bkz. HeftrengDatabase).
// ═══════════════════════════════════════════════════════════════════════════

@Entity(tableName = "cached_authors")
data class CachedAuthor(
    @PrimaryKey
    val id            : String,
    val name          : String  = "",
    val bio           : String  = "",
    val photoUrl      : String  = "",
    val birthYear     : Int     = 0,
    val nationality   : String  = "",
    val bookCount     : Int     = 0,
    val quoteCount    : Int     = 0,
    val reviewCount   : Int     = 0,
    val followerCount : Int     = 0,
    val cachedAt      : Long    = System.currentTimeMillis(),
)
