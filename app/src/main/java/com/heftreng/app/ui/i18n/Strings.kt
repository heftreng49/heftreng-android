package com.heftreng.app.ui.i18n

// ═══════════════════════════════════════════════════════════════════════════════
// HEFTREng — Türkçe / Kurmancî Lokalizasyon
//
// KULLANIM (herhangi bir @Composable içinde):
//   val lang = settingsVm.language.collectAsState().value  // zaten her ekranda var
//   Strings.feed(lang)        → "Nivis" veya "Herik"
//   Strings.save(lang)        → "Kaydet" veya "Tomar bike"
//
// Hiçbir CompositionLocal veya @Composable context gerektirmez.
// Her fonksiyon saf (pure) Kotlin — her yerden çağrılabilir.
// ═══════════════════════════════════════════════════════════════════════════════

object Strings {

    // ── Dil yardımcısı ────────────────────────────────────────────────────────
    private fun t(lang: String, tr: String, ku: String) = if (lang == "ku") ku else tr

    // ── Alt Navigasyon ────────────────────────────────────────────────────────
    fun navFeed(l: String)         = t(l, "Nivis",       "Nivis")
    fun navBlog(l: String)         = t(l, "Blog",        "Blog")
    fun navBooks(l: String)        = t(l, "Kitaplar",    "Pirtûk")
    fun navKurdi(l: String)        = t(l, "Kurdî",       "Kurdî")
    fun navProfile(l: String)      = t(l, "Profil",      "Profîl")
    fun navSearch(l: String)       = t(l, "Keşfet",      "Keşif bike")
    fun navMessages(l: String)     = t(l, "Mesajlar",    "Peyam")
    fun navNotifs(l: String)       = t(l, "Bildirimler", "Agahî")
    fun navSettings(l: String)     = t(l, "Ayarlar",     "Mîheng")

    // ── Genel Butonlar ────────────────────────────────────────────────────────
    fun save(l: String)            = t(l, "Kaydet",          "Tomar bike")
    fun cancel(l: String)          = t(l, "İptal",           "Betal bike")
    fun delete(l: String)          = t(l, "Sil",             "Jê bibe")
    fun edit(l: String)            = t(l, "Düzenle",         "Biguhêze")
    fun send(l: String)            = t(l, "Gönder",          "Bişîne")
    fun back(l: String)            = t(l, "Geri",            "Vegere")
    fun confirm(l: String)         = t(l, "Onayla",          "Piştrast bike")
    fun loading(l: String)         = t(l, "Yükleniyor...",   "Tê barkirin...")
    fun retry(l: String)           = t(l, "Tekrar Dene",     "Dîsa biceribîne")
    fun close(l: String)           = t(l, "Kapat",           "Bigire")
    fun next(l: String)            = t(l, "Sonraki",         "Pêştir")
    fun finish(l: String)          = t(l, "Bitir",           "Bidawî bike")
    fun share(l: String)           = t(l, "Paylaş",          "Parve bike")
    fun noResult(l: String)        = t(l, "Sonuç bulunamadı","Encam nehate dîtin")
    fun error(l: String)           = t(l, "Bir hata oluştu", "Çewtiyeke derket")

    // ── Sosyal ───────────────────────────────────────────────────────────────
    fun follow(l: String)          = t(l, "Takip Et",        "Şopîne")
    fun unfollow(l: String)        = t(l, "Takibi Bırak",    "Şopînê berde")
    fun followers(l: String)       = t(l, "Takipçi",         "Şopîner")
    fun following(l: String)       = t(l, "Takip",           "Şopandî")
    fun likes(l: String)           = t(l, "Beğeni",          "Hez kirin")
    fun comments(l: String)        = t(l, "Yorum",           "Şîrove")
    fun posts(l: String)           = t(l, "Gönderi",         "Nivîs")
    fun readMore(l: String)        = t(l, "Devamını Oku",    "Zêdetir bixwîne")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun login(l: String)           = t(l, "Giriş Yap",       "Têkeve")
    fun logout(l: String)          = t(l, "Çıkış Yap",       "Derkeve")
    fun register(l: String)        = t(l, "Kayıt Ol",        "Qeyd bibe")
    fun email(l: String)           = t(l, "E-posta",         "E-name")
    fun password(l: String)        = t(l, "Şifre",           "Şîfre")
    fun fullName(l: String)        = t(l, "Ad Soyad",        "Nav û Nasname")
    fun username(l: String)        = t(l, "Kullanıcı Adı",   "Navê Bikarhêner")
    fun forgotPass(l: String)      = t(l, "Şifremi Unuttum", "Şîfreya min ji bîr bû")
    fun noAccount(l: String)       = t(l, "Hesabın yok mu? Kayıt ol", "Hesabê te tune? Qeyd bibe")
    fun hasAccount(l: String)      = t(l, "Hesabın var mı? Giriş yap", "Hesabê te heye? Têkeve")

    // ── Feed ──────────────────────────────────────────────────────────────────
    fun whatsOnMind(l: String)     = t(l, "Ne düşünüyorsun?",         "Tu çi difikiri?")
    fun postHint(l: String)        = t(l, "Düşüncelerini paylaş...",  "Ramanên xwe parve bike...")
    fun like(l: String)            = t(l, "Beğen",                    "Hez bike")
    fun comment(l: String)         = t(l, "Yorum",                    "Şîrove")
    fun repost(l: String)          = t(l, "Tekrar Paylaş",            "Dîsa parve bike")
    fun addComment(l: String)      = t(l, "Yorum Ekle",               "Şîrove zêde bike")
    fun commentHint(l: String)     = t(l, "Yorumunu yaz...",          "Şîroveya xwe binivîse...")
    fun noPost(l: String)          = t(l, "Henüz gönderi yok",        "Hîn nivîs tune")
    fun deletePost(l: String)      = t(l, "Gönderiyi Sil",            "Nivîsê jê bibe")
    fun deletePostConfirm(l: String) = t(l,
        "Bu gönderi kalıcı olarak silinecek. Emin misin?",
        "Ev nivîs dê bê vegere were jêbirin. Tu piştrast î?")
    fun report(l: String)          = t(l, "Şikayet Et",               "Rapor bike")

    // ── Profil ────────────────────────────────────────────────────────────────
    fun editProfile(l: String)     = t(l, "Profili Düzenle",   "Profîlê biguhêze")
    fun bio(l: String)             = t(l, "Hakkında",          "Der barê min")
    fun website(l: String)         = t(l, "Web Sitesi",        "Malpera min")
    fun joined(l: String)          = t(l, "Katıldı",           "Beşdar bû")
    fun noPosts(l: String)         = t(l, "Henüz gönderi yok", "Hîn nivîs tune")
    fun savedPosts(l: String)      = t(l, "Kaydedilenler",     "Tomarkirî")
    fun profilePhoto(l: String)    = t(l, "Profil Foto",       "Wêneya Profîlê")
    fun coverPhoto(l: String)      = t(l, "Kapak Foto",        "Wêneya Bergê")

    // ── Blog ──────────────────────────────────────────────────────────────────
    fun blogEmpty(l: String)       = t(l, "Blog yazısı bulunamadı", "Gotara blogê nehate dîtin")
    fun readTime(l: String)        = t(l, "dk okuma",               "xul. xwendin")

    // ── Kitaplar ──────────────────────────────────────────────────────────────
    fun readingList(l: String)     = t(l, "Okuma Listesi",    "Lîsteya Xwendinê")
    fun addToList(l: String)       = t(l, "Listeye Ekle",     "Lîsteyê zêde bike")
    fun removeFromList(l: String)  = t(l, "Listeden Çıkar",   "Ji lîsteyê derxe")
    fun quotes(l: String)          = t(l, "Alıntılar",        "Gotinên Bijarte")
    fun authors(l: String)         = t(l, "Yazarlar",         "Nivîskar")
    fun serials(l: String)         = t(l, "Seriler",          "Rêze")

    // ── Kurdî Dersleri ────────────────────────────────────────────────────────
    fun kurdiUnits(l: String)      = t(l, "Üniteler",               "Yekîne")
    fun kurdiDict(l: String)       = t(l, "Sözlük",                 "Ferheng")
    fun kurdiGrammar(l: String)    = t(l, "Dilbilgisi",             "Rêziman")
    fun kurdiAi(l: String)         = t(l, "YZ Ders",                "Dersê ZZ")
    fun lessonComplete(l: String)  = t(l, "Ders Tamamlandı! 🎉",    "Ders Qediya! 🎉")
    fun correctAnswer(l: String)   = t(l, "Doğru!",                 "Rast!")
    fun wrongAnswer(l: String)     = t(l, "Yanlış!",                "Xelet!")
    fun checkAnswer(l: String)     = t(l, "Kontrol Et",             "Kontrol bike")
    fun continueLesson(l: String)  = t(l, "Devam",                  "Berdewam bike")
    fun tapInOrder(l: String)      = t(l, "Kelimelere sırayla dokun","Peyvan bi rêzê bixin")
    fun typeAnswer(l: String)      = t(l, "Cevabını yaz...",        "Bersiva xwe binivîse...")
    fun xpGained(l: String)        = t(l, "XP kazandın!",           "XP qezenc kir!")
    fun finishLesson(l: String)    = t(l, "Dersi Bitir 🎉",         "Dersa Bidawî Bike 🎉")
    fun nextQuestion(l: String)    = t(l, "Sonraki →",              "Pêştir →")
    fun toQuestions(l: String)     = t(l, "Sorulara Geç →",         "Biçe Pirsên →")
    fun complete(l: String)        = t(l, "Tamamla 🎉",             "Temam bike 🎉")
    fun correctCount(l: String, n: Int) = t(l, "$n doğru cevap ✓", "$n bersivên rast ✓")
    fun correctAnswerIs(l: String, a: String) = t(l, "Doğru cevap: $a", "Bersiva rast: $a")
    fun correctOrder(l: String, w: String)    = t(l, "Doğru sıra: $w",  "Rêza rast: $w")
    fun tryAgain(l: String)        = t(l, "Tekrar Dene",            "Dîsa biceribîne")

    // ── Mesajlar ──────────────────────────────────────────────────────────────
    fun messagesTitle(l: String)   = t(l, "Mesajlar",         "Peyam")
    fun messageHint(l: String)     = t(l, "Mesaj yaz...",     "Peyamê binivîse...")
    fun noMessages(l: String)      = t(l, "Henüz mesaj yok",  "Hîn peyam tune")
    fun newMessage(l: String)      = t(l, "Yeni Mesaj",       "Peyama Nû")

    // ── Bildirimler ───────────────────────────────────────────────────────────
    fun noNotif(l: String)         = t(l, "Henüz bildirim yok",          "Hîn agahî tune")
    fun notifLike(l: String)       = t(l, "gönderini beğendi",           "nivîsa te hez kir")
    fun notifComment(l: String)    = t(l, "yorum yaptı",                 "şîrove kir")
    fun notifFollow(l: String)     = t(l, "seni takip etmeye başladı",   "dest bi şopîna te kir")

    // ── Ayarlar ───────────────────────────────────────────────────────────────
    fun settingsTitle(l: String)   = t(l, "Ayarlar",          "Mîheng")
    fun darkMode(l: String)        = t(l, "Karanlık Mod",     "Moda Tarî")
    fun lightMode(l: String)       = t(l, "Aydınlık Mod",     "Moda Ronî")
    fun appLanguage(l: String)     = t(l, "Uygulama Dili",    "Zimana Bernameyê")
    fun about(l: String)           = t(l, "Hakkında",         "Der barê me")
    fun version(l: String)         = t(l, "Sürüm",            "Guherto")
    fun account(l: String)         = t(l, "Hesap",            "Hesab")
    fun privacy(l: String)         = t(l, "Gizlilik",         "Nepenî")

    // ── Yazar Paneli ──────────────────────────────────────────────────────────
    fun yazarTitle(l: String)      = t(l, "Nivîskar / Yazar Paneli", "Nivîskar")
    fun yazarWrite(l: String)      = t(l, "Yaz",               "Binivîse")
    fun yazarMyPosts(l: String)    = t(l, "Yazılarım",         "Nivîsên min")
    fun yazarSubmit(l: String)     = t(l, "Yazıyı Gönder",     "Nivîsê bişîne")
    fun yazarCategory(l: String)   = t(l, "Kategori",          "Kategorî")
    fun yazarTags(l: String)       = t(l, "Etiketler",         "Etîket")
    fun yazarPending(l: String)    = t(l, "Bekliyor ⏳",       "Li bendê ye ⏳")
    fun yazarApproved(l: String)   = t(l, "Yayında ✅",        "Weşandî ✅")
    fun yazarRejected(l: String)   = t(l, "Reddedildi ❌",     "Red kir ❌")
    fun yazarWithdraw(l: String)   = t(l, "Geri Çek",          "Vegerîne")

    // ── Arama ─────────────────────────────────────────────────────────────────
    fun searchHint(l: String)      = t(l,
        "Kullanıcı, gönderi veya kitap ara...",
        "Bikarhêner, nivîs an pirtûk bigere...")
    fun searchPeople(l: String)    = t(l, "Kişiler",    "Kes")
    fun searchPosts(l: String)     = t(l, "Gönderiler", "Nivîs")
    fun searchBooks(l: String)     = t(l, "Kitaplar",   "Pirtûk")

    // ── Feed (yeni) ───────────────────────────────────────────────────────────
    fun filterAll(l: String)        = t(l, "Herkes",               "Hemû")
    fun filterFollowing(l: String)  = t(l, "Takip Edilenler",      "Şopîner")
    fun likedBy(l: String)          = t(l, "Beğenenler",           "Hez Kirinên")
    fun reportPost(l: String)       = report(l)   // alias → report()
    fun newPost(l: String)          = t(l, "Yeni Gönderi",         "Nivîsek Nû")
    fun anonymous(l: String)        = t(l, "Anonim",               "Bênas")
    fun showMore(l: String)         = t(l, "Daha Fazla Göster",    "Zêdetir Nîşan Bide")
    fun likeAction(l: String)       = like(l)   // alias → like()

    // ── Profil (yeni) ─────────────────────────────────────────────────────────
    fun followersTitle(l: String, count: Int) = t(l, "Takipçiler ($count)", "Şopîner ($count)")
    fun followingTitle(l: String, count: Int) = t(l, "Takip ($count)",      "Şopandî ($count)")

    // ── Seriler / Kitaplar (yeni) ─────────────────────────────────────────────
    fun chapter(l: String)          = t(l, "Bölüm",               "Beş")
    fun chapters(l: String)         = t(l, "Bölümler",            "Beşên")
    fun noChapters(l: String)       = t(l, "Henüz bölüm yok",     "Hîn beş tune")
    fun deleteChapter(l: String)    = t(l, "Bölümü Sil",          "Beşê jê bibe")
    fun editChapter(l: String)      = t(l, "Bölümü Düzenle",      "Beşê biguherîne")
    fun newChapter(l: String)       = t(l, "Yeni Bölüm",          "Beşa Nû")
    fun chapterTitle(l: String)     = t(l, "Bölüm Başlığı",       "Sernavê Beşê")
    fun create(l: String)           = t(l, "Oluştur",             "Çêke")
    fun genre(l: String)            = t(l, "Tür",                 "Cûre")

    // ── Yazar Paneli (yeni) ───────────────────────────────────────────────────
    fun loginToWrite(l: String)     = t(l, "Yazı göndermek için giriş yapmalısın", "Ji bo nivîsandina nivîsê têkeve")
    fun submitSuccess(l: String)    = t(l, "✓ Yazın gönderildi! Admin onayı bekleniyor.", "✓ Nivîsa te hat şandin! Li bendê admin e.")
    fun noSubmissions(l: String)    = t(l, "Henüz yazı göndermedin", "Hîn tu nivîs neşandiye")
    fun titleLabel(l: String)       = t(l, "Başlık",               "Sernavê")
    fun contentLabel(l: String)     = t(l, "İçerik",               "Naverok")
    fun summaryLabel(l: String)     = t(l, "Kısa Özet (opsiyonel)","Kurteya Nivîsê (vebijarkî)")
    fun sending(l: String)          = t(l, "Gönderiliyor...",      "Tê şandin...")
    fun contentLangBoth(l: String)  = t(l, "İkisi",                "Herdu")

    // ── Kurdi Ekranı (yeni) ───────────────────────────────────────────────────
    fun kurdiTitle(l: String)       = t(l, "Kurdî Öğren",          "Kurdî Fêrbibe")
    fun startLesson(l: String)      = t(l, "Başla!",               "Destpê Bike!")
    fun aiLessonTitle(l: String)    = t(l, "AI ile Kurdî Ders",    "Dersê Kurdî bi ZZ")
    fun aiLessonDesc(l: String)     = t(l, "OpenRouter API anahtarını gir, kaydedilir.", "Miftaya OpenRouter API binivîse, tê tomarkirin.")
    fun aiGenerating(l: String)     = t(l, "Üretiliyor…",          "Tê çêkirin…")
    fun aiGenerate(l: String)       = t(l, "✨ Ders Oluştur",      "✨ Dersê Çêke")
    fun topicHint(l: String)        = t(l, "Renkler, Sayılar…",    "Reng, Hejmar…")

    // ── Arama (yeni) ──────────────────────────────────────────────────────────
    fun suggestedPeople(l: String)  = t(l, "Önerilen Kişiler",     "Kesên Pêşniyarkirî")
    fun followAction(l: String)     = follow(l)   // alias → follow()
    fun resultTypeLabel(l: String, type: String) = when (type) {
        "post"       -> t(l, "Gönderi",       "Nivîs")
        "serial"     -> t(l, "Seri",          "Rêzedîmen")
        "book"       -> t(l, "Kitap",         "Pirtûk")
        "author"     -> t(l, "Yazar",         "Nivîskar")
        "book_quote" -> t(l, "Kitap Alıntısı","Gotina Pirtûkê")
        else         -> t(l, "Kişi",          "Kes")
    }

    // ── Mesajlar (yeni) ───────────────────────────────────────────────────────
    fun online(l: String)           = t(l, "Çevrimiçi",            "Serhêl")
    fun offline(l: String)          = t(l, "Çevrimdışı",           "Nediyar")
    fun typing(l: String)           = t(l, "Yazıyor...",           "Dinivîse...")
    fun edited(l: String)           = t(l, "(düzenlendi)",         "(guherî)")
    fun deleted(l: String)          = t(l, "Bu mesaj silindi",     "Peyam hat jêbirin")
    fun reply(l: String)            = t(l, "Yanıtla",              "Bersiv bide")
    fun voiceMessage(l: String)     = t(l, "Sesli mesaj",          "Dengbêjiya dengî")
    fun playing(l: String)          = t(l, "▶ Çalıyor",           "▶ Dide")
    fun voice(l: String)            = t(l, "🎤 Ses",              "🎤 Deng")

    // ── Ayarlar (yeni) ────────────────────────────────────────────────────────
    fun appearance(l: String)       = t(l, "Görünüm",              "Xuyangeh")
    fun changePassword(l: String)   = t(l, "Şifre Değiştir",       "Şîreya Biguherîne")
    fun changeEmail(l: String)      = t(l, "E-posta Değiştir",     "E-Postayê Biguherîne")
    fun pushNotifs(l: String)       = t(l, "Push Bildirimleri",    "Agahdariyên Push")
    fun privateAccount(l: String)   = t(l, "Gizli Hesap",          "Hesabê Veşartî")
    fun blockedUsers(l: String)     = t(l, "Engellenen Kullanıcılar", "Bikarhênerên Astengkirî")
    fun unblock(l: String)          = t(l, "Engeli Kaldır",        "Astengiyê Berde")
    fun termsOfUse(l: String)       = t(l, "Kullanım Koşulları",   "Şert û Mercên Bikarhanînê")
    fun privacyPolicy(l: String)    = t(l, "Gizlilik Politikası",  "Siyaseta Nepeniyê")
    fun passwordMismatch(l: String) = t(l, "Şifreler eşleşmiyor", "Şîre li hev nayên")
    fun currentPassword(l: String)  = t(l, "Mevcut Şifre",         "Şîreya Niha")
    fun newPassword(l: String)      = t(l, "Yeni Şifre",           "Şîreya Nû")
    fun selectLang(l: String)       = t(l, "Uygulama dilini seç",  "Zimanê serîlêdanê hilbijêre")

    // ── Auth (yeni) ───────────────────────────────────────────────────────────
    fun welcome(l: String)          = t(l, "Hoş geldin",           "Xêr hatî")
}
