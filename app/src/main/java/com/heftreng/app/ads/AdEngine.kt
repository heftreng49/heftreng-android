package com.heftreng.app.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
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
 *  Tüm banner ve native reklam yükleme/retry/havuz mantığı tek yerde.
 *  Ekranlar sadece key (String) bazında istekte bulunur.
 *
 *  Düzeltmeler (v2):
 *   - spawnBannerAdView: retry döngüsünde ESKİ AdView destroy edilmeden
 *     önce yeni istek başlıyordu → yarış durumu + bellek sızıntısı. Düzeltildi.
 *   - loadBanner / loadPositionedBanner: "zaten yüklü" guard'ı unit ID
 *     değişimini de doğru şekilde algılar hale getirildi.
 *   - warmUpNativePool: havuz dolduğunda nativePoolFilling bayrağı temizlenir.
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AdEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        private const val MAX_RETRY         = 4
        private const val RETRY_BASE_DELAY  = 8_000L   // 8s, 16s, 32s, 64s
        private const val POOL_TARGET       = 3        // havuzda hep hazır bekleyecek reklam
        private const val POOL_MAX          = 6        // bellek koruması üst sınırı
    }

    // ── Tekil (single) banner ─────────────────────────────────────────────
    private val singleBannerView   = mutableMapOf<String, AdView>()
    private val singleBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val singleBannerSize   = mutableMapOf<String, String>()
    private val singleBannerUnit   = mutableMapOf<String, String>()
    private val singleRetryCount   = mutableMapOf<String, Int>()

    fun bannerLoadedFlow(key: String): StateFlow<Boolean> =
        singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedBanner(key: String): AdView? = singleBannerView[key]

    /** Tekil banner slotunu yükler. Unit ID veya boyut değiştiyse otomatik yeniler. */
    fun loadBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow  = singleBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = singleBannerSize[key] != null && singleBannerSize[key] != bannerSize
        val unitChanged = singleBannerUnit[key] != null && singleBannerUnit[key] != unitId
        if (loadedFlow.value && !sizeChanged && !unitChanged) return

        if (sizeChanged || unitChanged) {
            singleBannerView.remove(key)?.destroy()
            loadedFlow.value = false
        }
        singleBannerSize[key]  = bannerSize
        singleBannerUnit[key]  = unitId
        singleRetryCount[key]  = 0
        spawnBannerAdView(key, unitId, bannerSize) { adView ->
            singleBannerView.remove(key)?.destroy()   // ← önceki varsa temizle
            singleBannerView[key] = adView
            loadedFlow.value = true
        }
    }

    // ── Pozisyon bazlı banner ──────────────────────────────────────────────
    private val posBannerView   = mutableMapOf<String, AdView>()
    private val posBannerLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posBannerSize   = mutableMapOf<String, String>()
    private val posBannerUnit   = mutableMapOf<String, String>()

    fun positionedBannerLoadedFlow(key: String): StateFlow<Boolean> =
        posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedBanner(key: String): AdView? = posBannerView[key]

    fun loadPositionedBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val loadedFlow  = posBannerLoaded.getOrPut(key) { MutableStateFlow(false) }
        val sizeChanged = posBannerSize[key] != null && posBannerSize[key] != bannerSize
        val unitChanged = posBannerUnit[key] != null && posBannerUnit[key] != unitId
        if (loadedFlow.value && !sizeChanged && !unitChanged) return

        if (sizeChanged || unitChanged) {
            posBannerView.remove(key)?.destroy()
            loadedFlow.value = false
        }
        posBannerSize[key] = bannerSize
        posBannerUnit[key] = unitId
        spawnBannerAdView(key, unitId, bannerSize) { adView ->
            posBannerView.remove(key)?.destroy()   // ← önceki varsa temizle
            posBannerView[key] = adView
            loadedFlow.value = true
        }
    }

    fun releasePositionedBanners(keyPrefix: String? = null) {
        val keys = if (keyPrefix == null) posBannerView.keys.toList()
                   else posBannerView.keys.filter { it.startsWith(keyPrefix) }
        keys.forEach { k ->
            posBannerView.remove(k)?.destroy()
            posBannerLoaded.remove(k)
            posBannerSize.remove(k)
            posBannerUnit.remove(k)
        }
    }

    /**
     * Tek bir AdView üretip yükler.
     * Retry'da: callback saklayarak sadece en son istek "kazanır",
     * aradaki stale AdView'lar anında destroy edilir.
     */
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
        }

        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                singleRetryCount[key] = 0
                onLoaded(adView)
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                adView.destroy()  // ← başarısız AdView'ı hemen temizle
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

        adView.loadAd(
            AdRequest.Builder()
                .setContentUrl("https://heftreng.app")
                .build()
        )
    }

    fun resolveAdSize(bannerSize: String): AdSize {
        val dm    = appContext.resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt().coerceAtLeast(320)
        return when (bannerSize) {
            "banner"           -> AdSize.BANNER
            "medium_rectangle" -> AdSize.MEDIUM_RECTANGLE
            "large_banner"     -> AdSize.LARGE_BANNER
            else               -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, width)
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  NATIVE AD HAVUZU
    //  unitId bazlı ortak havuzlar — aynı unitId'yi kullanan tüm
    //  pozisyonlar aynı havuzdan beslenir.
    // ══════════════════════════════════════════════════════════════════════
    private val nativePool        = mutableMapOf<String, ArrayDeque<NativeAd>>()
    private val nativePoolFilling = mutableMapOf<String, Boolean>()

    private val posNativeAd     = mutableMapOf<String, NativeAd>()
    private val posNativeLoaded = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val posNativeUnit   = mutableMapOf<String, String>()

    fun positionedNativeLoadedFlow(key: String): StateFlow<Boolean> =
        posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }.asStateFlow()

    fun cachedPositionedNative(key: String): NativeAd? = posNativeAd[key]

    /** Havuzu önceden doldurur — idempotent, ekran açılışında bir kez çağrılır. */
    fun warmUpNativePool(unitId: String) {
        if (unitId.isBlank()) return
        val pool   = nativePool.getOrPut(unitId) { ArrayDeque() }
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
                // Havuz hedefine ulaşıldıysa doldurma bayrağını kaldır
                nativePoolFilling[unitId] = pool.size < POOL_TARGET
                if (pool.size < POOL_TARGET) fillNativePoolSlot(unitId)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    scope.launch {
                        if (retry >= MAX_RETRY) {
                            nativePoolFilling[unitId] = false
                            return@launch
                        }
                        delay(backoffDelay(retry + 1))
                        fillNativePoolSlot(unitId, retry + 1)
                    }
                }
            })
            .build().loadAd(AdRequest.Builder().build())
    }

    /**
     * Pozisyon için ANINDA havuzdan reklam çeker.
     * Havuz doluysa → 0 gecikme.
     * Havuz boşsa → doğrudan yükler + arka planda havuzu ısıtır.
     */
    fun preloadPositionedNative(key: String, unitId: String) {
        if (unitId.isBlank()) return
        val loadedFlow = posNativeLoaded.getOrPut(key) { MutableStateFlow(false) }
        if (loadedFlow.value && posNativeUnit[key] == unitId) return
        posNativeUnit[key] = unitId

        val pool      = nativePool.getOrPut(unitId) { ArrayDeque() }
        val fromPool  = pool.removeFirstOrNull()
        if (fromPool != null) {
            posNativeAd.remove(key)?.destroy()
            posNativeAd[key]    = fromPool
            loadedFlow.value    = true
            warmUpNativePool(unitId)   // çekilen yerin yerine arka planda doldur
            return
        }
        // Havuz boş — doğrudan yükle, paralelde havuzu doldur
        warmUpNativePool(unitId)
        loadNativeDirect(key, unitId, loadedFlow)
    }

    private fun loadNativeDirect(
        key: String,
        unitId: String,
        loadedFlow: MutableStateFlow<Boolean>,
        retry: Int = 0,
    ) {
        AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                posNativeAd.remove(key)?.destroy()
                posNativeAd[key]  = nativeAd
                loadedFlow.value  = true
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
            posNativeAd.remove(k)?.destroy()
            posNativeLoaded.remove(k)
            posNativeUnit.remove(k)
        }
    }

    fun releaseAdPool(unitId: String? = null) {
        val pools = if (unitId == null) nativePool.values else listOfNotNull(nativePool[unitId])
        pools.forEach { pool -> while (pool.isNotEmpty()) pool.removeFirst().destroy() }
        if (unitId != null) nativePool.remove(unitId) else nativePool.clear()
        if (unitId != null) nativePoolFilling.remove(unitId) else nativePoolFilling.clear()
    }

    // ── Yardımcılar ───────────────────────────────────────────────────────
    private fun backoffDelay(retry: Int): Long =
        RETRY_BASE_DELAY * (1L shl (retry - 1).coerceAtMost(4))   // 8s,16s,32s,64s,128s

    /**
     * CMS config'inden gerçek unit ID'yi belirler.
     * CMS'te özel unitId tanımlıysa → onu kullan, yoksa → prod varsayılanı.
     * enabled=false ise null döner → reklam gösterilmez.
     */
    fun resolveUnitId(config: CmsAdConfig?, prodId: String): String? {
        config ?: return null
        if (!config.enabled) return null
        return config.unitId.ifBlank { prodId }
    }

    fun destroyAll() {
        singleBannerView.values.forEach { it.destroy() }
        posBannerView.values.forEach { it.destroy() }
        posNativeAd.values.forEach { it.destroy() }
        nativePool.values.forEach { pool -> pool.forEach { it.destroy() } }
        singleBannerView.clear(); singleBannerLoaded.clear()
        singleBannerSize.clear(); singleBannerUnit.clear()
        posBannerView.clear();    posBannerLoaded.clear()
        posNativeAd.clear();      nativePool.clear()
        nativePoolFilling.clear()
    }
}
