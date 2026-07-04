package com.heftreng.app.ads

import android.content.Context
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.heftreng.app.HeftrangApp
import com.heftreng.app.util.ConsentHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdEngine — Heftreng'in TEK reklam motoru (v2, sıfırdan yazım).
 *
 *  TEK SLOT DEPOSU: eskiden banner için "tekil" ve "pozisyon bazlı" diye iki
 *  paralel map seti ve iki ayrı spawn fonksiyonu vardı (aynı mantık, iki kopya).
 *  Artık her banner/native reklam alanı tek bir `key: String` ile tanımlanan
 *  bir SLOT'tur; hangi ekranın hangi kartı olduğu motoru ilgilendirmez.
 *
 *  Ekranlar sadece requestBanner(key, ...) / requestNative(key, ...) çağırır;
 *  hangi key'in "tekil" mi "listede kaçıncı kart" mı olduğunu motor bilmez,
 *  bilmesine de gerek yok — key zaten benzersiz.
 *
 *  AdMob politika uyumu (korunan davranışlar):
 *   - STALE_AD_TIMEOUT: yüklenip gösterilmeyen native reklamlar belirli süre
 *     sonra otomatik destroy edilir (kullanıcı görmeden bellekte tutulmaz).
 *   - DISPOSE_GRACE: LazyColumn recycle/scroll-out-in senaryosunda anında imha
 *     yerine kısa bir bekleme — kullanıcı geri dönerse imha iptal edilir.
 *   - Havuz/stoklama YOK: her slot kendi tek isteğini atar, kullanılmazsa
 *     imha edilir (AdMob'un kendi önerisi — istek/gösterim oranını korur).
 * ═══════════════════════════════════════════════════════════════════════════
 */
class AdEngine(
    private val appContext: Context,
    private val scope: CoroutineScope,
) {
    companion object {
        // NOT: MAX_RETRY/RETRY_BASE_DELAY/backoffDelay artık native ve banner
        // yükleme akışlarında KULLANILMIYOR (retry mekanizması kaldırıldı,
        // bkz. loadOneNative/spawnBanner dokümanları). Kod içinde bilerek
        // bırakıldı — ileride kontrollü bir retry istenirse hazır dursun.
        private const val MAX_RETRY        = 3
        private const val RETRY_BASE_DELAY = 5_000L // 5s, 10s, 20s (exponential)

        /**
         * NATIVE REKLAM YENİLEME SÜRESİ.
         * Yüklenip gösterilmeyen (kullanıcı o pozisyona hiç gelmemiş) native reklam
         * bu süre sonunda imha edilir.
         *
         * Neden 30 dakika:
         *  - 5 dakika (eski değer) çok kısaydı: kullanıcı feed'i kapatıp 6 dakika
         *    sonra açınca aynı pozisyon için yeni istek gidiyordu (gösterim olmadan).
         *  - AdMob'un önerisi: yüklenmiş reklamı 1 saat içinde göster. 30 dakika
         *    bu sınırın güvenli bir altı — hem taze kalır hem gereksiz istek olmaz.
         *  - Ekran kapanınca releaseAllNatives() zaten anında imha eder,
         *    bu timeout sadece "açık ekranda ama o pozisyona hiç gidilmedi" durumu için.
         */
        private const val STALE_AD_TIMEOUT_MS = 30 * 60_000L // 30 dakika

        /**
         * SCROLL-OUT GRACE PERİYODU (native).
         * Kullanıcı bir native reklam pozisyonunu scroll ile geçince Composable
         * dispose olur. Anında imha etmek yerine bu kadar bekleriz — kullanıcı
         * geri dönerse imha iptal edilir, yeni istek ATILMAz.
         *
         * Neden 2 dakika:
         *  - 10 saniye (eski değer) çok kısaydı: hızlı scroll'da veya back/forward
         *    yapan kullanıcı 11 saniye sonra aynı pozisyon için yeni istek tetikliyordu.
         *  - 2 dakika normal "scroll edip geri dön" senaryolarının tamamını kapsar.
         *  - Bu süre STALE_AD_TIMEOUT_MS'den çok kısa — dolmadan önce stale timeout
         *    zaten devreye girirse imha eder, çakışma yok.
         */
        private const val DISPOSE_GRACE_MS = 2 * 60_000L // 2 dakika
    }

    /** Bir reklam türü: banner veya native. Retry/lifecycle mantığı türe göre dallanır. */
    private enum class SlotType { BANNER, NATIVE }

    /** Tek bir reklam alanının tüm durumu — banner ve native aynı yapıyı paylaşır. */
    private class SlotState(val type: SlotType) {
        val loaded    = MutableStateFlow(false)
        var unitId    : String = ""
        var size      : String = "adaptive"     // banner için
        var retryCount: Int = 0
        var bannerView: AdView? = null          // type == BANNER ise dolu
        var nativeAd  : NativeAd? = null        // type == NATIVE ise dolu
        var loadJob   : Job? = null
        var staleJob  : Job? = null             // native: gösterilmeden zaman aşımı
        var releaseJob: Job? = null             // grace-period gecikmeli imha
        var exhausted : Boolean = false         // MAX_RETRY aşıldı, tekrar denenebilir
    }

    private val slots = mutableMapOf<String, SlotState>()

    private fun slotFor(key: String, type: SlotType): SlotState =
        slots.getOrPut(key) { SlotState(type) }

    // SDK hazır olana kadar bekle — coroutine içinde, UI'ı bloklamaz
    private suspend fun awaitSdk() = HeftrangApp.sdkReady.first { it }

    // Consent durumuna göre personalized veya NPA reklam isteği.
    fun adRequest(): AdRequest {
        val status = ConsentHelper.consentStatus.value
        val npa = status == com.google.android.ump.ConsentInformation.ConsentStatus.REQUIRED
        return if (npa) {
            AdRequest.Builder()
                .addNetworkExtrasBundle(AdMobAdapter::class.java, android.os.Bundle().apply {
                    putString("npa", "1")
                })
                .build()
        } else {
            AdRequest.Builder().build()
        }
    }

    private val nativeOptions = NativeAdOptions.Builder()
        .setVideoOptions(VideoOptions.Builder().setStartMuted(true).build())
        .setRequestMultipleImages(false)
        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
        .build()

    private fun backoffDelay(retry: Int): Long =
        RETRY_BASE_DELAY * (1L shl (retry - 1).coerceAtMost(3)) // 5s,10s,20s,40s

    /**
     * "bannerSize" alanı CMS/RC'de native VE banner reklamlar için aynı isimle
     * kullanılıyor: native "small/medium/large", banner "banner/medium_rectangle/
     * large_banner" bekler. İki kelime seti burada birbirine eşleniyor.
     */
    fun resolveAdSize(bannerSize: String): AdSize {
        val dm    = appContext.resources.displayMetrics
        val width = (dm.widthPixels / dm.density).toInt().coerceAtLeast(320)
        return when (bannerSize) {
            "banner", "small"            -> AdSize.BANNER
            "medium_rectangle", "medium" -> AdSize.MEDIUM_RECTANGLE
            "large_banner", "large"      -> AdSize.LARGE_BANNER
            else                         -> AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(appContext, width)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  BANNER — tek yol. "Tekil" veya "pozisyon bazlı" ayrımı yok; her key
    //  kendi bağımsız slotudur.
    // ═══════════════════════════════════════════════════════════════════════

    fun bannerLoadedFlow(key: String): StateFlow<Boolean> =
        slotFor(key, SlotType.BANNER).loaded.asStateFlow()

    fun cachedBanner(key: String): AdView? = slots[key]?.bannerView

    /** Banner slotunu yükler. unitId veya boyut değiştiyse otomatik yeniler. İdempotent. */
    fun requestBanner(key: String, unitId: String, bannerSize: String = "adaptive") {
        if (unitId.isBlank()) return
        val slot = slotFor(key, SlotType.BANNER)

        val changed = slot.unitId.isNotEmpty() && (slot.unitId != unitId || slot.size != bannerSize)

        // Zaten yüklü ve config aynı — hiçbir şey yapma.
        if (slot.loaded.value && !changed) return

        // Zaten bu config ile yükleniyorsa — iptal edip yeniden başlatma,
        // mevcut isteğin tamamlanmasını bekle (istek sayısını katlamamak için).
        if (!changed && slot.loadJob?.isActive == true) return

        // MAX_RETRY tükendiyse (native'deki exhausted mantığının aynısı) —
        // warmVisiblePositions scroll'da defalarca çağrılır; bunu engellemezsek
        // her scroll tetiklemesinde tükenmiş slot için sıfırdan istek zinciri
        // başlar (AdMob "ad requests" sayacını gereksiz şişirir).
        if (!changed && slot.exhausted) return

        if (changed) {
            slot.bannerView?.destroy()
            slot.bannerView = null
            slot.loaded.value = false
            slot.exhausted = false
        }
        slot.unitId     = unitId
        slot.size       = bannerSize
        slot.retryCount = 0

        slot.loadJob?.cancel()
        slot.loadJob = scope.launch {
            awaitSdk()
            spawnBanner(key, slot)
        }
    }

    /** Bir banner slotunu (ve varsa devam eden isteğini) serbest bırakır. */
    fun releaseBanner(key: String) {
        val slot = slots.remove(key) ?: return
        slot.loadJob?.cancel()
        slot.bannerView?.destroy()
    }

    /** keyPrefix ile başlayan tüm banner slotlarını serbest bırakır (ekran kapanırken). */
    fun releaseBanners(keyPrefix: String? = null) {
        val keys = slots.filterValues { it.type == SlotType.BANNER }.keys
            .let { if (keyPrefix == null) it.toList() else it.filter { k -> k.startsWith(keyPrefix) } }
        keys.forEach { releaseBanner(it) }
    }

    /**
     * RETRY KALDIRILDI (native ile aynı sebep, bkz. loadOneNative dokümanı):
     * no-fill'de otomatik tekrar deneme, istek sayısını gereksiz katlıyordu.
     * Artık no-fill = adView imha edilir, exhausted set edilmez (banner
     * "tükendi" kavramı yok — bir sonraki requestBanner çağrısı yeni bir
     * deneme başlatabilir, native'deki gibi kalıcı exhausted flag yok).
     */
    private fun spawnBanner(key: String, slot: SlotState) {
        val adView = AdView(appContext).apply {
            setAdSize(resolveAdSize(slot.size))
            adUnitId = slot.unitId
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
        adView.adListener = object : AdListener() {
            override fun onAdLoaded() {
                slot.retryCount = 0
                slot.bannerView?.destroy()
                slot.bannerView = adView
                slot.loaded.value = true
            }
            override fun onAdFailedToLoad(e: LoadAdError) {
                adView.destroy()
                slot.exhausted = true
            }
        }
        adView.loadAd(adRequest())
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  NATIVE — pozisyon başına tek istek, tek kullanım. Havuz/prefetch yok:
    //  her pozisyon ekrana gireceği an istenir, kullanılmazsa imha edilir.
    // ═══════════════════════════════════════════════════════════════════════

    fun nativeLoadedFlow(key: String): StateFlow<Boolean> =
        slotFor(key, SlotType.NATIVE).loaded.asStateFlow()

    fun cachedNative(key: String): NativeAd? = slots[key]?.nativeAd

    /**
     * Pozisyon için tek native reklam ister. Aynı key+unitId için zaten yüklü
     * veya yükleniyorsa tekrar istek atmaz (idempotent). Tükenmiş durumdaysa
     * (MAX_RETRY aşıldıysa) tekrar çağrılınca yeniden dener.
     */
    fun requestNative(key: String, unitId: String) {
        if (unitId.isBlank()) return
        val slot = slotFor(key, SlotType.NATIVE)

        // Bekleyen gecikmeli imha varsa iptal et (kullanıcı geri döndü).
        slot.releaseJob?.cancel()
        slot.releaseJob = null

        // Zaten yüklü, yükleniyor veya bu oturumda MAX_RETRY tükendi — istek atma.
        // exhausted sıfırlanmaz: warmVisiblePositions scroll'da defalarca çağrılır,
        // her geçişte sıfırlayıp yeni istek atmak aşırı istek sayısının ana nedenidir.
        val sameUnit = slot.unitId == unitId
        if (sameUnit && (slot.exhausted || slot.loaded.value || slot.loadJob?.isActive == true)) return

        slot.staleJob?.cancel()
        slot.loadJob?.cancel()
        slot.nativeAd?.destroy()
        slot.nativeAd = null
        slot.unitId = unitId
        slot.loaded.value = false

        slot.loadJob = scope.launch {
            awaitSdk()
            loadOneNative(
                unitId    = unitId,
                retry     = 0,
                onSuccess = { ad ->
                    slot.nativeAd = ad
                    slot.loaded.value = true
                    slot.staleJob = scope.launch {
                        delay(STALE_AD_TIMEOUT_MS)
                        releaseNativeImmediate(key)
                    }
                },
                onFail = {
                    slot.loaded.value = false
                    slot.exhausted = true
                },
            )
        }
    }

    /**
     * Composable dispose olduğunda çağrılır. Anında imha etmez — DISPOSE_GRACE_MS
     * kadar bekler; aynı key bu süre içinde requestNative ile tekrar istenirse
     * (kullanıcı geri döndüyse) imha iptal edilir.
     */
    fun releaseNative(key: String) {
        val slot = slots[key] ?: return
        if (slot.type != SlotType.NATIVE) return
        slot.staleJob?.cancel()
        slot.releaseJob?.cancel()
        slot.releaseJob = scope.launch {
            delay(DISPOSE_GRACE_MS)
            releaseNativeImmediate(key)
        }
    }

    /** Anında ve kalıcı imha — grace period beklemez. Ekran tamamen kapanırken kullanılır. */
    private fun releaseNativeImmediate(key: String) {
        val slot = slots.remove(key) ?: return
        slot.staleJob?.cancel()
        slot.releaseJob?.cancel()
        slot.loadJob?.cancel()
        slot.nativeAd?.destroy()
        slot.loaded.value = false
    }

    /** keyPrefix ile başlayan tüm native slotları anında serbest bırakır (ekran kapanırken). */
    fun releaseAllNatives(keyPrefix: String? = null) {
        val keys = slots.filterValues { it.type == SlotType.NATIVE }.keys
            .let { if (keyPrefix == null) it.toList() else it.filter { k -> k.startsWith(keyPrefix) } }
        keys.forEach { releaseNativeImmediate(it) }
    }

    /**
     * Tek native ad yükleme primitifi. forNativeAd ile withAdListener AYRI
     * zincirde tutulur — aynı builder'da olduklarında AdMob SDK bazı durumlarda
     * callback'lerden birini güvenilir şekilde tetiklemiyor.
     *
     * RETRY KALDIRILDI: no-fill (%37 eşleşme oranı → isteklerin ~%63'ü no-fill)
     * yaşandığında MAX_RETRY'a kadar (5s,10s,20s) otomatik tekrar deniyordu.
     * Bu, geometrik seri ile her no-fill başına ortalama ~2,3 istek üretiyordu
     * (1 + 0,63 + 0,63² + 0,63³) — 51 gösterim için 4600+ istek anomalisinin
     * ana kaynağı buydu. No-fill genelde "şu an bu unit için envanter yok"
     * demektir; hemen retry nadiren işe yarar, sadece istek/gösterim oranını
     * daha da bozar. Artık no-fill = anında vazgeç, tek istek.
     */
    private fun loadOneNative(
        unitId   : String,
        retry    : Int = 0,
        onSuccess: (NativeAd) -> Unit,
        onFail   : () -> Unit,
    ) {
        var received = false
        val adLoader = AdLoader.Builder(appContext, unitId)
            .forNativeAd { nativeAd ->
                if (!received) { received = true; onSuccess(nativeAd) }
                else nativeAd.destroy()
            }
            .withNativeAdOptions(nativeOptions)
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    onFail()
                }
            })
            .build()
        adLoader.loadAd(adRequest())
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  Yaşam döngüsü
    // ═══════════════════════════════════════════════════════════════════════

    /** MainActivity.onResume() → AdsViewModel.onAppForeground() üzerinden çağrılır. */
    fun resumeAllBanners() {
        slots.values.filter { it.type == SlotType.BANNER }
            .forEach { runCatching { it.bannerView?.resume() } }
    }

    /**
     * MainActivity.onPause() → AdsViewModel.onAppBackground() üzerinden çağrılır.
     * Native reklamlardaki video için manuel pause API'si yok (SDK otomatik yönetir).
     */
    fun pauseAllBanners() {
        slots.values.filter { it.type == SlotType.BANNER }
            .forEach { runCatching { it.bannerView?.pause() } }
    }

    /**
     * Uygulama arka plana alındığında henüz gösterilmemiş native reklamları
     * serbest bırakır — STALE_AD_TIMEOUT_MS'i beklemeden anında politika-uyumlu temizlik.
     */
    fun releaseUnseenNativesOnBackground() = releaseAllNatives()

    fun destroyAll() {
        slots.values.forEach { slot ->
            slot.loadJob?.cancel()
            slot.staleJob?.cancel()
            slot.releaseJob?.cancel()
            slot.bannerView?.destroy()
            slot.nativeAd?.destroy()
        }
        slots.clear()
    }
}
