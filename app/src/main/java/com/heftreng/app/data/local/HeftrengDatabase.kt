package com.heftreng.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

// ═══════════════════════════════════════════════════════════════════════════
//  HeftrengDatabase — Öncelik 4: Offline cache (Room)
//  Şimdilik yalnızca alıntı önbelleği (cached_quotes). İleride okuma listesi /
//  rozetler gibi başka offline veriler de buraya eklenebilir (version artırılır).
// ═══════════════════════════════════════════════════════════════════════════

@Database(
    entities = [CachedQuote::class],
    version  = 1,
    exportSchema = false,
)
abstract class HeftrengDatabase : RoomDatabase() {
    abstract fun quoteDao(): QuoteDao
}
