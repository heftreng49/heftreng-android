package com.heftreng.app.di

import android.content.Context
import androidx.room.Room
import com.heftreng.app.data.local.HeftrengDatabase
import com.heftreng.app.data.local.QuoteDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════════════════
//  DatabaseModule — Öncelik 4: Offline cache (Room)
// ═══════════════════════════════════════════════════════════════════════════

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideHeftrengDatabase(@ApplicationContext context: Context): HeftrengDatabase =
        Room.databaseBuilder(context, HeftrengDatabase::class.java, "heftreng.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    @Singleton
    fun provideQuoteDao(db: HeftrengDatabase): QuoteDao = db.quoteDao()
}
