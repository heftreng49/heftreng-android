package com.heftreng.app.di

import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AdsModule {
    // FirebaseFirestore zaten bir yerde sağlanmış olmalı, ancak burada tekrar kontrol ediyoruz.
    // Eğer başka bir modülde varsa burası hata verebilir, o durumda bu provides silinmeli.
}
