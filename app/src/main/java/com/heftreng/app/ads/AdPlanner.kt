package com.heftreng.app.ads

import com.heftreng.app.data.model.CmsAdConfig

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  AdPlanner — bir liste (feed, kütüphane sekmesi, blog, kurdi ders listesi
 *  vb.) içinde HANGİ index'te HANGİ reklamın gösterileceğini tek geçişte,
 *  tek index havuzundan hesaplayan SAF fonksiyon.
 *
 *  ESKİ SİSTEMİN SORUNU: her ekranda banner ve native pozisyonu birbirinden
 *  habersiz iki ayrı "% frequency == 0" formülüyle hesaplanıyordu. Varsayılan
 *  config'lerle (native: position=5,frequency=5 — banner: position=5) her
 *  ikisi de 5, 10, 15... index'lerinde aynı anda "doğru" oluyor ve aynı karta
 *  hem native hem banner biniyordu.
 *
 *  YENİ SİSTEM: tek fonksiyon, tek "occupied" set. Native önce yerleşir,
 *  banner sadece BOŞ index'lere yerleşir (çakışırsa bir sonraki boşa kayar).
 *  Aynı index'e iki placement yazılması yapısal olarak imkansızdır — dönen
 *  değer Map<Int, AdPlacement> olduğu için bir index'in en fazla bir
 *  placement'ı olabilir.
 *
 *  Bu fonksiyon state tutmaz, hiçbir I/O yapmaz — birim testle doğrulanabilir.
 * ═══════════════════════════════════════════════════════════════════════════
 */

/** Bir listede tek bir index'te gösterilecek reklamı tanımlar. */
sealed class AdPlacement {
    abstract val slotKey: String

    data class Banner(
        override val slotKey: String,
        val unitId: String,
        val size: String,
    ) : AdPlacement()

    data class Native(
        override val slotKey: String,
        val unitId: String,
        val size: String = "small",
    ) : AdPlacement()
}

/** CmsAdConfig'ten planlama için gereken minimum bilgi. */
data class SlotSpec(
    val unitId: String,
    val position: Int,
    val frequency: Int,
    val bannerSize: String = "adaptive",
)

/** CmsAdConfig + resolved unitId'den SlotSpec üretir. unitId boşsa null döner (o tür planlanmaz). */
fun CmsAdConfig.toSlotSpec(resolvedUnitId: String?): SlotSpec? {
    if (resolvedUnitId.isNullOrBlank()) return null
    return SlotSpec(
        unitId    = resolvedUnitId,
        position  = position.coerceAtLeast(1),
        frequency = frequency.coerceAtLeast(1),
        bannerSize = bannerSize,
    )
}

/**
 * itemCount kartlık bir liste için reklam planını hesaplar.
 *
 * @param itemCount   listedeki toplam öğe sayısı (0 tabanlı index alanı: 0..itemCount-1)
 * @param nativeSpec  native reklam config'i (null → native hiç planlanmaz)
 * @param bannerSpec  banner reklam config'i (null → banner hiç planlanmaz)
 * @param screenKey   ekran adı — üretilen slotKey'lerin ön eki (örn. "feed", "lib_quotes")
 *
 * Semantik (mevcut davranışla uyumlu):
 *  - native: position = ilk native'in göründüğü index, frequency = sonraki tekrar aralığı
 *  - banner: position = HEM ilk banner'ın göründüğü index HEM tekrar aralığı
 *    (mevcut sistemde banner'ın ayrı bir "frequency" alanı kullanılmıyordu — bu davranış korunuyor)
 */
fun buildAdPlan(
    itemCount : Int,
    nativeSpec: SlotSpec?,
    bannerSpec: SlotSpec?,
    screenKey : String,
): Map<Int, AdPlacement> {
    if (itemCount <= 0) return emptyMap()

    val plan     = mutableMapOf<Int, AdPlacement>()
    val occupied = mutableSetOf<Int>()

    nativeSpec?.let { spec ->
        var idx = spec.position
        while (idx < itemCount) {
            plan[idx] = AdPlacement.Native(
                slotKey = "${screenKey}_native_$idx",
                unitId  = spec.unitId,
                size    = spec.bannerSize,
            )
            occupied += idx
            idx += spec.frequency
        }
    }

    bannerSpec?.let { spec ->
        var idx = spec.position
        while (idx < itemCount) {
            var target = idx
            while (target in occupied && target < itemCount) target++
            if (target < itemCount) {
                plan[target] = AdPlacement.Banner(
                    slotKey = "${screenKey}_banner_$target",
                    unitId  = spec.unitId,
                    size    = spec.bannerSize,
                )
                occupied += target
            }
            idx += spec.frequency  // DÜZELTME: position değil frequency
        }
    }

    return plan
}
