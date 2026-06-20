package com.heftreng.app.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import com.heftreng.app.data.model.AdMobProdIds
import com.heftreng.app.data.model.AdMobTestIds
import com.heftreng.app.data.model.CmsAdConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdEngine — Heftreng'in TEK reklam motoru.
 *
 *  Eskiden her banner/native "slot" (FEED, LIB, KURDI, BLOG...) kendi
 *  state'ine, kendi retry sayacına, kendi preload fonksiyonuna sahipti —
 *  aynı mantık 4-6 kez kopyalanmıştı. Bu dosya bunun yerine TEK bir
 *  String-key tabanlı sistem sunar: her slot sadece bir "key" stringidir
 *  (örn. "banner_feed", "native_blog"). Yeni bir yer eklemek için kod
 *  değişikliği gerekmez, sadece yeni bir key kullanılır.
 *
 *  İki temel kavram:
 *   1. SINGLE  — ekranda tek bir kalıcı reklam alanı (ör. sayfa başlığı
 *                altındaki banner). key = "banner_feed" gibi sabit.
 *   2. POOL    — liste içinde tekrarlanan reklamlar (ör. her 5 postta bir
 *                native ad). Önceden N adet reklam yüklenip kuyrukta
 *                bekletilir, pozisyon ekrana gelince ANINDA kuyruktan
 *                çekilir — bu "geç yükleniyor / hiç gelmiyor" sorununu
 *                çözer çünkü network bekleme süresi kullanıcı scroll
 *                etmeden ÖNCE, arka planda gerçekleşir.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AdEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val MAX_RETRY = 4
        private const val RETRY_BASE_DELAY_MS = 8_000L
        private const val POOL_TARGET = 3   // havuzda hep hazır bekleyecek reklam sayısı
        private const val POOL_MAX    = 6   // bellek koruması — bunu aşan fazlalık serbest bırakılır
    }

    // ── Tekil (single) banner state'leri — key bazlı, tek map ────────────────
    private val singleBannerView   = mutableMapOf<String, AdView>()
    private val singleBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val singleBannerSize   = mutableMapOf<String, String>()
    private val singleRetryCount   = mutableMapOf<String, Int>()

    fun bannerLoadedFlow(key: String): StateFlow<Boolean> =
        singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedBanner(key: String): AdView? = singleBannerView[key]

    /** Tekil bir banner slotunu yükler/yeniler. Boyut değiştiyse otomatik yeniden yükler. */
    fun loadBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow = singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = singleBannerSize[key] != null && singleBannerSize[key] != bannerSize
        if (loadedFlow.value && !sizeChanged) return
        if (sizeChanged) {
            singleBannerView[key]?.destroy()
            singleBannerView.remove(key)
            loadedFlow.value = false
        }
        singleBannerSize[key] = bannerSize
        singleRetryCount[key] = 0
        spawnBannerAdView(key, unitId, bannerSize) { adView ->
            singleBannerView[key]?.destroy()
            singleBannerView[key] = adView
            loadedFlow.value = true
        }
    }

    // ── Havuzlu (pozisyon bazlı) banner state'leri ───────────────────────────
    private val posBannerView   = mutableMapOf<String, AdView>()
    private val posBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posBannerSize   = mutableMapOf<String, String>()

    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> =
        posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedBanner(key: String): AdView? = posBannerView[key]

    fun loadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow = posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = posBannerSize[key] != null && posBannerSize[key] != bannerSize
        if (loadedFlow.value && !sizeChanged) return
        if (sizeChanged) {
            posBannerView[key]?.destroy()
            posBannerView.remove(key)
            loadedFlow.value = false
        }
        posBannerSize[key] = bannerSize
        spawnBannerAdView(key, unitId, bannerSize) { adView ->
            posBannerView[key]?.destroy()
            posBannerView[key] = adView
            loadedFlow.value = true
        }
    }

    fun releasePositionedBanners(keyPrefix: String? = null) {
        val keys = if (keyPrefix == null) posBannerView.keys.toList()
                   else posBannerView.keys.filter { it.startsWith(keyPrefix) }
        keys.forEach { k ->
            posBannerView[k]?.destroy()
            posBannerView.remove(k)
            posBannerLoaded.remove(k)
            posBannerSize.remove(k)
        }
    }

    /** Tek bir AdView üretip yükler — retry mantığı tek yerde, tüm banner tipleri için ortak. */
    private fun spawnBannerAdView(
        key: String,
        unitId: String,
        bannerSize: String,
        onLoaded: (AdView) -> Unit,
    ) {
        val adView = AdView(appContext).apply {
            setAdSize(resolveAdSize(bannerSize))
            adUnitId = unitId
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    singleRetryCount[key] = 0
                    onLoaded(this@apply)
                }
                override fun onAdFailedToLoad(e: LoadAdError) {
                    scope.launch {
                        val retry = (singleRetryCount[key] ?: 0) + 1
                        singleRetryCount[key] = retry
                        if (retry <= MAX_RETRY) {
                            delay(backoffDelay(retry))
                            spawnBannerAdView(key, unitId, bannerSize, onLoaded)
                        }
                    }
                }
            }
            loadAd(AdRequest.Builder().setContentUrl("https://heftreng.app").build())
        }
    }

    fun resolveAdSize(bannerSize: String): AdSize {
        val dm = appContext.resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt()
        return when (bannerSize) {
            "banner"           -> AdSize.BANNER
            "medium_rectangle" -> AdSize.MEDIUM_RECTANGLE
            "large_banner"     -> AdSize.LARGE_BANNER
            else               -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, width)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NATIVE AD HAVUZU — asıl "geç yükleniyor" sorununu çözen kısım.
    //  unitId bazlı ortak havuzlar: aynı unitId'yi kullanan tüm pozisyonlar
    //  (feed_native_5, feed_native_10, ...) aynı havuzdan beslenir.
    // ══════════════════════════════════════════════════════════════════════
    private val nativePool        = mutableMapOf<String, ArrayDeque<NativeAd>>()
    private val nativePoolFilling = mutableMapOf<String, Boolean>()

    private val posNativeAd     = mutableMapOf<String, NativeAd>()
    private val posNativeLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()

    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> =
        posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedNative(key: String): NativeAd? = posNativeAd[key]

    /** Havuzu önceden doldurur — ekran açılır açılmaz çağrılmalı (1 kere yeter, idempotent). */
    fun warmUpNativePool(unitId: String) {
        if (unitId.isBlank()) return
        val pool = nativePool.getOrPut(unitId) { ArrayDeque() }
        val needed = POOL_TARGET - pool.size
        if (needed <= 0 || nativePoolFilling[unitId] == true) return
        nativePoolFilling[unitId] = true
        repeat(needed) { fillNativePoolSlot(unitId) }
    }

    private fun fillNativePoolSlot(unitId: String, retry: Int = 0) {
        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                val pool = nativePool.getOrPut(unitId) { ArrayDeque() }
                if (pool.size < POOL_MAX) pool.addLast(nativeAd) else nativeAd.destroy()
                nativePoolFilling[unitId] = pool.size < POOL_TARGET
                if (pool.size < POOL_TARGET) fillNativePoolSlot(unitId)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    scope.launch {
                        if (retry >= MAX_RETRY) { nativePoolFilling[unitId] = false; return@launch }
                        delay(backoffDelay(retry + 1))
                        fillNativePoolSlot(unitId, retry + 1)
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    /**
     * Bir pozisyon (key) için ANINDA havuzdan reklam çeker. Havuz doluysa
     * sıfır gecikme. Havuz boşsa (ör. ilk açılış, henüz ısınmadı) doğrudan
     * yükler ve aynı anda havuzu da ısıtır — bir sonraki sefer havuzdan gelir.
     */
    fun preloadPositionedNative(key: String, unitId: String) {
        if (unitId.isBlank()) return
        val loadedFlow = posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }
        if (loadedFlow.value) return

        val pool = nativePool.getOrPut(unitId) { ArrayDeque() }
        val fromPool = pool.removeFirstOrNull()
        if (fromPool != null) {
            posNativeAd[key]?.destroy()
            posNativeAd[key] = fromPool
            loadedFlow.value = true
            warmUpNativePool(unitId) // çekilen yerine arka planda yenisini doldur
            return
        }

        warmUpNativePool(unitId)
        loadNativeDirect(key, unitId, loadedFlow)
    }

    private fun loadNativeDirect(key: String, unitId: String, loadedFlow: MutableStateFlow<Boolean>, retry: Int = 0) {
        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                posNativeAd[key]?.destroy()
                posNativeAd[key] = nativeAd
                loadedFlow.value = true
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    scope.launch {
                        if (retry >= MAX_RETRY) return@launch
                        delay(backoffDelay(retry + 1))
                        loadNativeDirect(key, unitId, loadedFlow, retry + 1)
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    fun releasePositionedNatives(keyPrefix: String? = null) {
        val keys = if (keyPrefix == null) posNativeAd.keys.toList()
                   else posNativeAd.keys.filter { it.startsWith(keyPrefix) }
        keys.forEach { k ->
            posNativeAd[k]?.destroy()
            posNativeAd.remove(k)
            posNativeLoaded.remove(k)
        }
    }

    fun releaseAdPool(unitId: String? = null) {
        val pools = if (unitId == null) nativePool.values else listOfNotNull(nativePool[unitId])
        pools.forEach { pool -> while (pool.isNotEmpty()) pool.removeFirst().destroy() }
        if (unitId != null) nativePool.remove(unitId) else nativePool.clear()
    }

    // ── Yardımcılar ────────────────────────────────────────────────────────
    private fun backoffDelay(retry: Int): Long =
        RETRY_BASE_DELAY_MS * (1L shl (retry - 1).coerceAtMost(4)) // 8s,16s,32s,64s,128s tavanı

    /** CMS config'inden gerçek kullanılacak unit ID'yi belirler: testMode > CMS unitId > hardcoded prod. */
    fun resolveUnitId(config: CmsAdConfig?, testId: String, prodId: String): String? {
        config ?: return null
        if (!config.enabled) return null
        return when {
            config.testMode            -> testId
            config.unitId.isNotBlank() -> config.unitId
            else                       -> prodId
        }
    }

    fun destroyAll() {
        singleBannerView.values.forEach { it.destroy() }
        posBannerView.values.forEach { it.destroy() }
        posNativeAd.values.forEach { it.destroy() }
        nativePool.values.forEach { pool -> pool.forEach { it.destroy() } }
        singleBannerView.clear(); posBannerView.clear()
        posNativeAd.clear(); nativePool.clear()
    }
}
