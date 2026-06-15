package com.heftreng.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuoteDao {

    @Query("SELECT * FROM cached_quotes ORDER BY tsMillis DESC LIMIT :limit")
    suspend fun getCachedQuotes(limit: Int = 50): List<CachedQuote>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(quotes: List<CachedQuote>)

    @Query("DELETE FROM cached_quotes")
    suspend fun clear()

    /** Önbelleği yenile: eskileri sil, yenileri yaz — tek transaction. */
    @androidx.room.Transaction
    suspend fun replaceAll(quotes: List<CachedQuote>) {
        clear()
        insertAll(quotes)
    }
}
