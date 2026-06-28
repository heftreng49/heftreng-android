package com.heftreng.app.di

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.heftreng.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import javax.inject.Named
import javax.inject.Singleton

// ═══════════════════════════════════════════════════════════════
//  AppModule — Dependency Injection
//
//  CACHE MİMARİSİ (v2) — Fatura Dostu & Reklam Güvenli:
//
//  Firestore disk cache → 50 MB (düşürüldü; 100 MB şişirilmişti)
//  Neden 50 MB?
//    - Firestore PersistentCache sadece document verisini tutar,
//      medya/resim cache'i değil. 50 MB yüzlerce bin dökümanı karşılar.
//    - Büyük cache → eski/stale data risk artar → UI tutarsızlığı
//    - Reklam SDK'ları (AdMob) kendi network stack'lerini kullanır;
//      Firestore cache bunları hiç etkilemez.
//
//  ViewModel katmanı kendi in-memory cache'ini (Map<String, X>) tutar:
//    - SocialViewModel._userEnrichCache → uid → (name, photoURL)
//    - FeedViewModel içinde _followingUids StateFlow
//    - Reklam config (CmsAdConfig) → AppConfig ile birlikte tek get()
//
//  REKLAM GÜVENLİĞİ:
//    - Reklam config artık Firebase Remote Config'ten (cms_ads KALDIRILDI);
//      Firestore tamamen devre dışı.
//    - Reklam gösterimi AdMob SDK'sına bırakılmıştır;
//      Firestore cache reklam requestini, impression'ı veya tıklamayı
//      etkilemez — bunlar AdMob kendi ağ katmanında işler.
//    - AdMob prod ID seçimi Models.kt'daki AdMobProdIds üzerinden yapılır.
//      Cihaz zaten AdMob test cihazı olarak kayıtlı, ayrı test ID gerekmez.
//
//  SUPABASE:
//    - URL ve ANON_KEY build zamanında BuildConfig'e gömülür.
//    - CI → GitHub Secrets (SUPABASE_URL, SUPABASE_ANON_KEY)
//    - Lokal → local.properties (git'e gitmez)
// ═══════════════════════════════════════════════════════════════

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun provideFirestore(): FirebaseFirestore {
        val db = FirebaseFirestore.getInstance()
        // firestoreSettings sadece ilk kullanımdan önce set edilebilir.
        // FirebaseAppCheck veya başka bir bileşen Firestore'u daha önce başlattıysa
        // IllegalStateException fırlatılır — try/catch ile güvenli hale getiriyoruz.
        try {
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(
                    PersistentCacheSettings.newBuilder()
                        // FATURA OPTİMİZASYONU: 50 MB yeterli; 100 MB gereksiz büyük
                        // Eski stale veri birikimi önlenir, reklam gösterimi etkilenmez
                        .setSizeBytes(50L * 1024 * 1024) // 50 MB disk cache
                        .build()
                )
                .build()
            db.firestoreSettings = settings
        } catch (_: Exception) {
            // Zaten başlatılmış — mevcut ayarlarla devam et
        }
        return db
    }

    @Provides @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides @Singleton
    fun provideSupabaseClient(): SupabaseClient = createSupabaseClient(
        // Değerler build.gradle.kts'de BuildConfig'e gömülür:
        //   CI   → GitHub Secrets: SUPABASE_URL, SUPABASE_ANON_KEY
        //   Lokal → local.properties: SUPABASE_URL=..., SUPABASE_ANON_KEY=...
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Postgrest)
        install(Realtime)
    }

    @Provides @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides @Singleton
    @Named("auth_prefs")
    fun provideAuthPrefs(@ApplicationContext context: Context): SharedPreferences =
        try {
            // AES256-GCM şifrelemeli SharedPreferences — şifreler güvenli saklanır
            androidx.security.crypto.EncryptedSharedPreferences.create(
                context,
                "heft_auth_accounts",
                androidx.security.crypto.MasterKey.Builder(context)
                    .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (_: Exception) {
            // Cihaz desteklemiyorsa düz metin fallback
            context.getSharedPreferences("heft_auth_accounts", Context.MODE_PRIVATE)
        }
}
