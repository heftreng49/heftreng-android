package com.heftreng.app.di

import android.content.Context
import androidx.room.Room
import com.heftreng.app.data.local.AuthorDao
import com.heftreng.app.data.local.BookDao
import com.heftreng.app.data.local.HeftrengDatabase
import com.heftreng.app.data.local.MIGRATION_2_3
import com.heftreng.app.data.local.QuoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════
//  DatabaseModule — Offline cache (Room)
//
//  v3: BookDao + AuthorDao eklendi. MIGRATION_2_3 ile mevcut kullanıcıların
//  cached_quotes verileri korunur, yeni tablolar eklenir.
// ═══════════════════════════════════════════════════════════════════════════

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHeftrengDatabase(@ApplicationContext context: Context): HeftrengDatabase =
        Room.databaseBuilder(context, HeftrengDatabase::class.java, "heftreng.db")
            .addMigrations(MIGRATION_2_3)
            // fallbackToDestructiveMigration kaldırıldı — migration tanımlı olduğu için
            // mevcut kullanıcıların cached_quotes verileri silinmez.
            .build()

    @Provides
    @Singleton
    fun provideQuoteDao(db: HeftrengDatabase): QuoteDao = db.quoteDao()

    @Provides
    @Singleton
    fun provideBookDao(db: HeftrengDatabase): BookDao = db.bookDao()

    @Provides
    @Singleton
    fun provideAuthorDao(db: HeftrengDatabase): AuthorDao = db.authorDao()
}
