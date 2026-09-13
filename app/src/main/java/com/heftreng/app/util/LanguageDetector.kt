package com.heftreng.app.util

/**
 * Hafif, bağımlılıksız dil tespiti (heuristic).
 *
 * Uygulamanın 4 desteklediği dil arasında (Türkçe, Kurmancî, Soranî, Zazakî)
 * bir metnin muhtemelen hangi dilde yazıldığını tahmin eder. ML Kit gibi
 * genel amaçlı kütüphaneler bu Kürtçe lehçelerini ayrı ayrı tanımadığı için
 * (çoğu zaman hepsini "ku" olarak görür ya da hiç tanımaz), burada
 * alfabe + karaktere özgü ipuçları + yaygın kelime listeleriyle basit
 * ama bu 4 dil için yeterince ayırt edici bir tahmin yapılıyor.
 *
 * Amaç: "Çevir" butonunu SADECE metin, kullanıcının uygulama dilinden
 * muhtemelen farklıysa göstermek. %100 doğruluk hedeflenmiyor — buton
 * bazen gereksiz görünse de zararı yok (kullanıcı çevirmeden geçebilir).
 */
object LanguageDetector {

    // Soranî neredeyse her zaman Arap-Fars alfabesiyle yazılır — bu en
    // güvenilir sinyal. Aralık: Arapça/Farsça/Kürtçe Sorani harfleri.
    private val ARABIC_SCRIPT_REGEX = Regex("[\\u0600-\\u06FF\\u0750-\\u077F]")

    // Kurmancî ve Zazakî'de sık kullanılıp Türkçe'de bulunmayan harfler
    // (özellikle w, q, x hiç Türkçe alfabesinde yok; ê/î/û Türkçe'de yok).
    private val KURDISH_LATIN_CHARS_REGEX = Regex("[wqxêîûḧẍʼ]", RegexOption.IGNORE_CASE)

    // Türkçe'ye özgü, Kurmancî/Zazakî'de neredeyse hiç geçmeyen harfler.
    private val TURKISH_ONLY_CHARS_REGEX = Regex("[ğışöüçĞİŞÖÜÇ]")

    // En sık kullanılan Kurmancî fonksiyon kelimeleri (bağlaç/edat/zamir).
    private val KURMANJI_STOPWORDS = setOf(
        "û", "ku", "li", "ji", "bo", "di", "de", "da", "ev", "ew", "em",
        "tu", "hûn", "wan", "wî", "wê", "min", "te", "me", "we", "çi",
        "kî", "çawa", "çima", "gelo", "jî", "an", "lê", "ne", "tune",
    )

    // En sık kullanılan Zazakî fonksiyon kelimeleri.
    private val ZAZAKI_STOPWORDS = setOf(
        "u", "ke", "de", "ra", "ser", "ez", "to", "ma", "şma", "ey",
        "çi", "çıko", "sero", "zaf", "kam", "çira", "ya", "yê", "no", "nê",
    )

    // En sık kullanılan Türkçe fonksiyon kelimeleri.
    private val TURKISH_STOPWORDS = setOf(
        "ve", "bir", "bu", "şu", "de", "da", "ile", "için", "gibi", "ama",
        "çok", "ben", "sen", "biz", "siz", "onlar", "ne", "nasıl", "neden",
        "mi", "mı", "mu", "mü", "değil", "var", "yok",
    )

    /**
     * Verilen metnin muhtemel dilini tahmin eder.
     * @return "tr", "ku", "ckb", "zza" ya da tahmin edilemiyorsa null.
     */
    fun detect(text: String): String? {
        val clean = text.trim()
        if (clean.length < 8) return null // çok kısa metinlerde tahmin güvenilmez

        // 1. Arap alfabesi varsa büyük olasılıkla Soranî.
        val arabicCharCount = ARABIC_SCRIPT_REGEX.findAll(clean).count()
        if (arabicCharCount > clean.length / 4) return "ckb"

        val words = clean
            .lowercase(java.util.Locale.ROOT)
            .split(Regex("[^\\p{L}]+"))
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return null

        val turkishStopHits  = words.count { it in TURKISH_STOPWORDS }
        val kurmanjiStopHits = words.count { it in KURMANJI_STOPWORDS }
        val zazakiStopHits   = words.count { it in ZAZAKI_STOPWORDS }

        val hasKurdishChars = KURDISH_LATIN_CHARS_REGEX.containsMatchIn(clean)
        val hasTurkishChars = TURKISH_ONLY_CHARS_REGEX.containsMatchIn(clean)

        // 2. Stopword sayımı en güçlü sinyal — en çok eşleşen dili seç.
        val scores = mapOf(
            "tr"  to turkishStopHits + (if (hasTurkishChars) 1 else 0) - (if (hasKurdishChars) 1 else 0),
            "ku"  to kurmanjiStopHits + (if (hasKurdishChars) 1 else 0) - (if (hasTurkishChars) 1 else 0),
            "zza" to zazakiStopHits + (if (hasKurdishChars) 1 else 0) - (if (hasTurkishChars) 1 else 0),
        )
        val best = scores.maxByOrNull { it.value } ?: return null
        // Yeterince belirgin bir sinyal yoksa (hepsi 0 veya negatifse) tahmin etme.
        if (best.value <= 0) return null

        // Kurmancî ve Zazakî stopword'leri çakışabiliyor (û, de, ku gibi ortak
        // kelimeler) — ikisi de eşit puanlıysa ayırt edemeyiz, null dön.
        val topEntries = scores.filterValues { it == best.value }
        if (topEntries.size > 1) return null

        return best.key
    }

    /**
     * Metnin dili, kullanıcının uygulama dilinden FARKLI olduğunda true döner.
     * Tespit belirsizse (null) false döner — yani şüpheli durumda buton
     * gösterilmez (gereksiz buton, hiç buton olmamasından daha rahatsız edici).
     */
    fun isLikelyDifferentLanguage(text: String, appLanguage: String): Boolean {
        val detected = detect(text) ?: return false
        return detected != appLanguage
    }
}
