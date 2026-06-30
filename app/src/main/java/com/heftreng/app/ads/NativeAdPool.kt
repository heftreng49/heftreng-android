package com.heftreng.app.ads

/**
 * NativeAdPool — Basit native reklam havuzu.
 *
 * Tek sorumluluk: uygulama açılırken arka planda N reklam yükle,
 * listeye sırayla ver. Kullanıcı o pozisyona geldiğinde 0ms gecikme.
 *
 * Karmaşıklık yok: no cursor, no pagination, no state machine.
 * Sadece bir ArrayDeque ve bir AdLoader.
 */

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NativeAdPool(
    private val context : Context,
    private val scope   : CoroutineScope,
) {
    // Hazır reklamlar
    private val pool = ArrayDeque<NativeAd>()

    // UI'ın durumu takip etmesi için
    private val _size = MutableStateFlow(0)
    val size = _size.asStateFlow()

    private var isLoading = false
    private var unitId    = ""

    companion object {
        const val TARGET_SIZE = 6   // Her zaman bu kadar hazır tut
        const val MAX_SIZE    = 10  // Maksimum stok
        const val BATCH       = 5   // Tek seferde bu kadar yükle
    }

    /** Uygulama açılışında veya feed açılınca çağır. */
    fun warmUp(adUnitId: String) {
        if (adUnitId.isBlank()) return
        unitId = adUnitId
        refillIfNeeded()
    }

    /** Sıradaki reklamı al. Havuz boşsa null döner (shimmer gösterilir). */
    fun next(): NativeAd? {
        val ad = pool.removeFirstOrNull()
        _size.value = pool.size
        // Aldıktan sonra eksilen slot için arka planda doldur
        if (pool.size < TARGET_SIZE) refillIfNeeded()
        Log.d("AdPool", "next() → pool kaldı: ${pool.size}")
        return ad
    }

    /** Tüm reklamları temizle (uygulama kapanırken). */
    fun destroy() {
        pool.forEach { it.destroy() }
        pool.clear()
        _size.value = 0
    }

    private fun refillIfNeeded() {
        if (isLoading) return
        if (pool.size >= TARGET_SIZE) return
        if (unitId.isBlank()) return

        val needed = (TARGET_SIZE - pool.size).coerceAtMost(BATCH)
        if (needed <= 0) return

        isLoading = true
        Log.d("AdPool", "Yükleniyor: $needed reklam (havuz: ${pool.size})")

        scope.launch {
            // Küçük gecikme — AdMob rate limit koruması
            if (pool.isEmpty()) delay(0) else delay(200)

            AdLoader.Builder(context, unitId)
                .forNativeAd { nativeAd ->
                    if (pool.size < MAX_SIZE) {
                        pool.addLast(nativeAd)
                        _size.value = pool.size
                        Log.d("AdPool", "Eklendi → havuz: ${pool.size}")
                    } else {
                        nativeAd.destroy()
                    }
                }
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("AdPool", "Yüklenemedi: ${error.message}")
                    }
                })
                .build()
                .loadAds(AdRequest.Builder().build(), needed)

            isLoading = false
        }
    }
}
