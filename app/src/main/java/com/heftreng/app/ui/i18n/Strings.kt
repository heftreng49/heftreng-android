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
    fun navLibrary(l: String)      = t(l, "Kütüphane",   "Pirtûkxane")
    fun navDiscover(l: String)     = t(l, "Keşfet",      "Vedîtin")
    fun navKurdi(l: String)        = t(l, "Kurdî",       "Kurdî")
    fun navProfile(l: String)      = t(l, "Profil",      "Profîl")
    fun navSearch(l: String)       = t(l, "Keşfet",      "Keşif bike")
    fun navMessages(l: String)     = t(l, "Mesajlar",    "Peyam")
    fun navNotifs(l: String)       = t(l, "Bildirimler", "Agahî")
    fun navSettings(l: String)     = t(l, "Ayarlar",     "Mîheng")

    // ── Genel Butonlar ────────────────────────────────────────────────────────
    fun save(l: String)            = t(l, "Kaydet",          "Tomar bike")
    fun ok(l: String)              = t(l, "Tamam",           "Temam")
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
    fun followRequested(l: String) = t(l, "İstek Gönderildi","Daxwaz Şand")
    fun followRequestAccept(l: String) = t(l, "Onayla",      "Qebûl bike")
    fun followRequestDecline(l: String) = t(l, "Reddet",     "Red bike")
    fun followRequestTitle(l: String, name: String) =
        t(l, "$name seni takip etmek istiyor", "$name dixwaze te şopîne")
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
    fun kurdiLeaderboard(l: String)= t(l, "Sıralama",               "Rêzkirin")
    fun leaderboardTitle(l: String)= t(l, "XP Sıralaması",          "Rêzkirin a XP")
    fun leaderboardEmpty(l: String)= t(l, "Henüz sıralama yok",     "Hêj rêzkirin tune")
    fun leaderboardYou(l: String)  = t(l, "Sen",                    "Tu")
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
    fun notifLike(l: String)       = t(l, "gönderinizi beğendi",         "nivîsa we xweş dît")
    fun notifComment(l: String)    = t(l, "gönderinize yorum yaptı",     "li ser nivîsa we şîrove kir")
    fun notifFollow(l: String)     = t(l, "sizi takip etmeye başladı",   "dest bi şopandina we kir")
    fun notifRepost(l: String)     = t(l, "gönderinizi paylaştı",        "nivîsa we parve kir")
    fun notifNew(l: String)        = t(l, "yeni bir bildirim",           "agahdariya nû")

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
    fun unlockWithVideo(l: String)  = t(l, "🎬 İzle, Aç",          "🎬 Temaşe Bike, Veke")
    fun aiLessonTitle(l: String)    = t(l, "AI ile Kurdî Ders",    "Dersê Kurdî bi ZZ")
    fun aiLessonDesc(l: String)     = t(l, "OpenRouter API anahtarını gir, kaydedilir.", "Miftaya OpenRouter API binivîse, tê tomarkirin.")
    fun aiGenerating(l: String)     = t(l, "Üretiliyor…",          "Tê çêkirin…")
    fun aiGenerate(l: String)       = t(l, "✨ Ders Oluştur",      "✨ Dersê Çêke")
    fun topicHint(l: String)        = t(l, "Renkler, Sayılar…",    "Reng, Hejmar…")

    // ── Arama (yeni) ──────────────────────────────────────────────────────────
    fun suggestedPeople(l: String)  = t(l, "Önerilen Kişiler",     "Kesên Pêşniyarkirî")
    fun seeAll(l: String)           = t(l, "Tümünü Gör",           "Hemûyî Bibîne")
    fun peopleHubFollowing(l: String) = t(l, "Takip Edilenler",     "Yên Tê Şopandin")
    fun peopleHubFollowers(l: String) = t(l, "Takipçiler",          "Şopîner")
    fun peopleHubSuggested(l: String) = t(l, "Önerilenler",         "Pêşniyar")
    fun followAction(l: String)     = follow(l)   // alias → follow()
    fun notifFollowRequest(l: String) = t(l, "Takip isteği gönderdi", "Daxwaza şopînê şand")
    fun notifGroupToday(l: String)    = t(l, "Bugün",     "Îro")
    fun notifGroupWeek(l: String)     = t(l, "Bu Hafta",  "Vê Hefteyê")
    fun notifGroupOlder(l: String)    = t(l, "Daha Önce", "Berê")
    fun resultTypeLabel(l: String, type: String) = when (type) {
        "post"           -> t(l, "Gönderi",         "Nivîs")
        "serial"         -> t(l, "Seri",            "Rêzedîmen")
        "library_book"   -> t(l, "Kütüphane Kitabı","Pirtûka Pirtûkxanê")
        "library_author" -> t(l, "Yazar",           "Nivîskar")
        "book"           -> t(l, "Kitap",           "Pirtûk")
        "author"         -> t(l, "Yazar",           "Nivîskar")
        "book_quote"     -> t(l, "Kitap Alıntısı",  "Gotina Pirtûkê")
        else             -> t(l, "Kişi",            "Kes")
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
    fun blockUser(l: String)        = t(l, "Kullanıcıyı Engelle",  "Bikarhênerê Asteng bike")
    fun blockUserConfirm(l: String) = t(l, "Bu kullanıcıyı engellemek istediğine emin misin?", "Tu dixwazî vî bikarhênerî asteng bikî?")
    fun termsOfUse(l: String)       = t(l, "Kullanım Koşulları",   "Şert û Mercên Bikarhanînê")
    fun privacyPolicy(l: String)    = t(l, "Gizlilik Politikası",  "Siyaseta Nepeniyê")
    fun passwordMismatch(l: String) = t(l, "Şifreler eşleşmiyor", "Şîre li hev nayên")
    fun currentPassword(l: String)  = t(l, "Mevcut Şifre",         "Şîreya Niha")
    fun newPassword(l: String)      = t(l, "Yeni Şifre",           "Şîreya Nû")
    fun selectLang(l: String)       = t(l, "Uygulama dilini seç",  "Zimanê serîlêdanê hilbijêre")

    // ── Auth (yeni) ───────────────────────────────────────────────────────────
    fun welcome(l: String)          = t(l, "Hoş geldin",           "Xêr hatî")

    // ── Bildirim mesajları ────────────────────────────────────────────────────

    // ── Ayarlar - açıklama metinleri ──────────────────────────────────────────
    fun settingsOther(l: String)         = t(l, "Diğer",                            "Yên Din")
    fun settingsAbout(l: String)         = t(l, "Heftreng Hakkında",               "Derbarê Heftreng")
    fun settingsAboutSub(l: String)      = t(l, "Uygulama hakkında bilgi",          "Serîlêdanê nas bike")
    fun rateApp(l: String)               = t(l, "Bizi Değerlendir",                  "Me binirxîne")
    fun rateAppSub(l: String)            = t(l, "Play Store'da puan ver, yorum yaz",  "Li Play Store dengê xwe bide")
    fun shareApp(l: String)              = t(l, "Arkadaşlarına Öner",                 "Ji hevalên xwe re pêşniyar bike")
    fun shareAppSub(l: String)           = t(l, "Play Store linkini paylaş",           "Lînka Play Store parve bike")
    fun shareAppChooser(l: String)       = t(l, "Arkadaşlarınla Paylaş",              "Bi hevalên xwe re parve bike")
    fun shareAppText(l: String)          = t(l,
        "Heft Reng Kurdî: Kürtçeyle kültür, edebiyat ve Kürtçe dil öğrenimi 👇\nhttps://play.google.com/store/apps/details?id=com.heftreng.app",
        "Heft Reng Kurdî: Bi kurdî Çand, wêje û fêrbûna zimani Kurdî 👇\nhttps://play.google.com/store/apps/details?id=com.heftreng.app"
    )
    fun settingsTermsSub(l: String)      = t(l, "Kullanım şartlarını görüntüle",    "Peymanname bixwîne")
    fun settingsPrivacySub(l: String)    = t(l, "Gizlilik politikasını görüntüle", "Siyaseta nepeniyê bixwîne")
    fun settingsAdminPanel(l: String)    = t(l, "Admin Paneli",                     "Panela Admin")
    fun settingsEditSub(l: String)       = t(l, "Profil bilgilerini düzenle",       "Profîla xwe nûve bike")
    fun settingsPasswordSub(l: String)   = t(l, "Yeni şifre belirle",               "Şîreya nû destnîşan bike")
    fun settingsEmailAdd(l: String)      = t(l, "E-posta adresi ekle",              "Email biguherîne")
    fun settingsPushSub(l: String)       = t(l, "Anlık bildirimleri aç/kapat",     "Agahdariyên push veke/bigire")
    fun settingsPrivateSub(l: String)    = t(l, "Sadece takipçiler görebilir",      "Tenê şopîner dikarin bibînin")
    fun settingsBlockedSub(l: String)    = t(l, "Engellenen hesapları yönet",       "Bikarhênerên astengkirî birêve bibe")
    fun settingsNoBlocked(l: String)     = t(l, "Engellenmiş kullanıcı yok.",       "Bikarhênerên astengkirî tune ne.")
    fun settingsAnonymous(l: String)     = t(l, "Kullanıcı",                        "Bikarhêner")
    fun forgotPassPrompt(l: String)      = t(l, "Şifreni mi unuttun? Mail ile sıfırla →", "Şîreya xwe ji bîr kir? Bi maîlê sifir bike →")
    fun pwRepeat(l: String)              = t(l, "Yeni Şifre (Tekrar)",              "Şîreya Nû (Dubare)")
    fun errPwBlank(l: String)            = t(l, "Mevcut şifreyi girin",             "Şîreya niha binivîse")
    fun errPwShort(l: String)            = t(l, "Yeni şifre en az 6 karakter olmalı","Şîreya nû divê herî kêm 6 tîp be")
    fun emailConfirmSent(l: String)      = t(l, "Doğrulama e-postası gönderildi. Yeni adresinizi onaylayın.", "E-posta piştrastkirinê hate şandin. Navnîşana nû bipejirîne.")
    fun currentLabel(l: String)          = t(l, "Mevcut",                           "Heyî")
    fun newEmailLabel(l: String)         = t(l, "Yeni E-Posta",                     "E-Postaya Nû")
    fun errInvalidEmail(l: String)       = t(l, "Geçerli bir e-posta girin",        "E-postayek derbasdar binivîse")
    fun errEnterPw(l: String)            = t(l, "Şifrenizi girin",                  "Şîreya xwe binivîse")
    fun sendVerification(l: String)      = t(l, "Doğrulama Gönder",                "Piştrastkirinê Bişîne")
    fun resetLinkSent(l: String)         = t(l, "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.", "Lînka sifirkirinê hate şandin. E-postaya xwe kontrol bike.")
    fun resetLinkDesc(l: String)         = t(l, "Kayıtlı e-posta adresinize şifre sıfırlama bağlantısı göndereceğiz.", "Em ê lînka sifirkirinê ji bo e-postaya qeydkirî bişînin.")

    // ── Feed - PostCard / Dialog metinleri ────────────────────────────────────
    fun showMoreBtn(l: String)           = t(l, "Daha Fazla Göster",          "Zêdetir Nîşan Bide")
    fun postThinkHint(l: String)         = t(l, "Ne düşünüyorsun?",           "Tu çi difikire?")
    fun addQuote(l: String)              = t(l, "Alıntı ekle",                "Alıntî")
    fun shareAction(l: String)           = t(l, "Paylaş",                     "Parve bike")
    fun cancelAction(l: String)          = t(l, "İptal",                      "Betal bike")
    fun newPostTitle(l: String)          = t(l, "Yeni Gönderi",               "Nivîsek Nû")
    fun optionsDesc(l: String)           = t(l, "Seçenekler",                 "Vebijêrk")
    fun editAction(l: String)            = t(l, "Düzenle",                    "Biguherîne")
    fun deleteAction(l: String)          = t(l, "Sil",                        "Jê bibe")
    fun repostAction(l: String)          = t(l, "Yeniden Paylaş",             "Ji Nû Ve Parve Bike")
    fun shareWhatsApp(l: String)         = t(l, "WhatsApp'ta Paylaş",         "Di WhatsApp'ê de Parve Bike")
    fun shareInstagram(l: String)        = t(l, "Instagram'da Paylaş",        "Di Instagram'ê de Parve Bike")
    fun shareOtherApps(l: String)        = t(l, "Diğer Uygulamalar",          "Sepanên Din")
    fun postTypeSerial(l: String)        = t(l, "Kitap",                      "Pirtûk")
    fun postTypeBlog(l: String)          = t(l, "Blog Yazısı",                "Gotara Blogê")
    fun postTypeFeed(l: String)          = t(l, "Paylaşım",                   "Parvekirî")
    fun saveAction(l: String)            = t(l, "Kaydet",                     "Tomarkirin")
    fun saveDesc(l: String)              = t(l, "Kaydet",                     "Tomarkirin")
    fun deletePostTitle(l: String)       = t(l, "Gönderiyi sil?",             "Nivîs jê bibe?")
    fun deletePostDesc(l: String)        = t(l, "Bu gönderi kalıcı olarak silinecek.", "Ev nivîs dê ji holê rabe.")
    fun editPostTitle(l: String)         = t(l, "Gönderiyi Düzenle",          "Nivîsê Biguherîne")
    fun deleteCommentTitle(l: String)    = t(l, "Yorumu Sil",                 "Şîrove Jê Bibe")
    fun timeNow(l: String)               = t(l, "az önce",                    "niha")
    fun timeMin(l: String, n: Int)       = t(l, "${n}dk",                     "${n}d")
    fun timeHour(l: String, n: Int)      = t(l, "${n}sa",                     "${n}s")
    fun timeDay(l: String, n: Int)       = t(l, "${n}g",                      "${n}r")
    fun timeWeek(l: String, n: Int)      = t(l, "${n}hf",                     "${n}hf")
    fun timeMon(l: String, n: Int)       = t(l, "${n}ay",                     "${n}m")
    fun timeYear(l: String, n: Int)      = t(l, "${n}y",                      "${n}s")
    fun reportDialogTitle(l: String, name: String) = t(l, "Hesap: $name",     "Hesab: $name")
    fun reportConfirm(l: String)         = t(l, "Şikayet Et",                 "Rapor bike")

    // ── Messages ──────────────────────────────────────────────────────────────
    fun msgSearchHint(l: String)         = t(l, "Mesajlarda ara...",          "Peyaman bigere...")
    fun msgListTitle(l: String)          = t(l, "Mesajlar",                   "Peyam")
    fun msgLoading(l: String)            = t(l, "Yükleniyor...",              "Tê barkirin...")
    fun msgEmpty(l: String)              = t(l, "Henüz mesajın yok",          "Peyam tune")
    fun msgEmptyDesc(l: String)          = t(l, "Yeni bir konuşma başlat",    "Peyamek nû dest pê bike")
    fun msgDeleteConvTitle(l: String)    = t(l, "Sohbeti Sil",                "Sohbet Sil")
    fun msgDeleteConvDesc(l: String)     = t(l, "Bu sohbeti silmek istiyor musun?", "Ev sohbet bê silîn?")
    fun msgUser(l: String)               = t(l, "Kullanıcı",                  "Bikarhêner")
    fun msgTyping(l: String)             = t(l, "yazıyor...",                 "dinivîse...")
    fun msgOnline(l: String)             = t(l, "çevrimiçi",                  "serhêl")
    fun msgOffline(l: String)            = t(l, "çevrimdışı",                 "nediyar")
    fun msgGoProfile(l: String)          = t(l, "Profile git",                "Profîl")
    fun msgDeleteConv(l: String)         = t(l, "Sohbeti sil",                "Sohbetê jê bibe")
    fun msgSaving(l: String)             = t(l, "Kayıt yapılıyor",            "Tê tomarkirin")
    fun msgVoice(l: String)              = t(l, "Sesli mesaj",                "Dengbêjiya dengî")
    fun msgYou(l: String)                = t(l, "Sen",                        "Tu")
    fun msgEditTitle(l: String)          = t(l, "Mesajı düzenle",             "Peyamê biguherîne")
    fun msgHint(l: String)               = t(l, "Mesaj yaz...",               "Peyamê binivîse...")
    fun msgEmptyConv(l: String)          = t(l, "Henüz mesaj yok, konuşmayı başlat!", "Peyam tune, dest bi axaftinê bike!")
    fun msgReply(l: String)              = t(l, "Yanıtla",                    "Bersiv bide")
    fun msgEdit(l: String)               = t(l, "Düzenle",                    "Biguherîne")
    fun msgDelete(l: String)             = t(l, "Sil",                        "Jê bibe")
    fun msgLike(l: String)               = t(l, "Beğen",                      "Hez bike")
    fun msgDeleted(l: String)            = t(l, "Bu mesaj silindi",           "Peyam hat jêbirin")
    fun msgEdited(l: String)             = t(l, "(düzenlendi)",               "(guherî)")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun authCreateAccount(l: String)     = t(l, "Hesap oluştur",              "Hesabek nû çêke")
    fun authWelcome(l: String)           = t(l, "Hoş geldin",                 "Xêr hatî")
    fun authGoogleContinue(l: String)    = t(l, "Google ile devam et",        "Bi Google re berdewam bike")
    fun authOr(l: String)                = t(l, "  ya da  ",                  "  an jî  ")
    fun authNameLabel(l: String)         = t(l, "Adın",                       "Navê te")
    fun authPasswordLabel(l: String)     = t(l, "Şifre",                      "Şîfre")
    fun authForgotPw(l: String)          = t(l, "Şifremi unuttum",            "Şîfreya xwe ji bîr kir")
    fun authRegister(l: String)          = t(l, "Kayıt ol",                   "Qeyd bibe")
    fun authLogin(l: String)             = t(l, "Giriş yap",                  "Têkeve")
    fun authHaveAccount(l: String)       = t(l, "Zaten hesabın var mı? Giriş yap", "Hesabê te heye? Têkeve")
    fun authNoAccount(l: String)         = t(l, "Hesabın yok mu? Kayıt ol",   "Hesabê te tune? Qeyd bibe")
    fun authForgotTitle(l: String)       = t(l, "Şifremi Unuttum",            "Şîreya Xwe Ji Bîr Kir")
    fun authResetSent(l: String)         = t(l, "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.", "Lînka sifirkirina şîfreyê hate şandin. E-postaya xwe kontrol bike.")
    fun authResetDesc(l: String)         = t(l, "E-posta adresinizi girin, sıfırlama bağlantısı gönderelim.", "E-postaya qeydkirî binivîse, em lînka sifirkirinê bişînin.")

    // ── Kitaplar / Seriler ────────────────────────────────────────────────────
    fun booksTitle(l: String)            = t(l, "Kitaplar",                   "Pirtûk")
    fun booksEmpty(l: String)            = t(l, "Henüz kitap yok",            "Pirtûk tune")
    fun bookAddBtn(l: String)            = t(l, "Kitap Ekle",                 "Pirtûk Zêde Bike")

    // ── Kütüphane Ekranı ─────────────────────────────────────────────────────
    fun libraryTitle(l: String)          = t(l, "Kütüphane",                  "Pirtûkxane")
    fun discoverTitle(l: String)         = t(l, "Keşfet",                     "Vedîtin")
    fun libraryTabQuotes(l: String)      = t(l, "Alıntılar",                  "Gotinên Bijarte")
    fun libraryTabReviews(l: String)     = t(l, "İncelemeler",                "Nirxandin")
    fun libraryTabAuthors(l: String)     = t(l, "Yazarlar",                   "Nivîskar")
    fun libraryTabBooks(l: String)       = t(l, "Kitaplar",                   "Pirtûk")
    fun libraryAddQuote(l: String)       = t(l, "Alıntı Ekle",                "Gotinê Zêde Bike")
    fun libraryAddReview(l: String)      = t(l, "İnceleme Ekle",              "Nirxandinê Zêde Bike")
    fun libraryQuoteHint(l: String)      = t(l, "Alıntı metnini girin...",    "Nivîsa gotinê binivîse...")
    fun libraryReviewHint(l: String)     = t(l, "İncelemenizi yazın...",      "Nirxandina xwe binivîse...")
    fun libraryNoQuotes(l: String)       = t(l, "Henüz alıntı yok",           "Gotinên bijarte tune")
    fun libraryNoReviews(l: String)      = t(l, "Henüz inceleme yok",         "Nirxandin tune")
    fun libraryNoAuthors(l: String)      = t(l, "Henüz yazar yok",            "Nivîskar tune")
    fun libraryNoBooks(l: String)        = t(l, "Henüz kitap yok",            "Pirtûk tune")
    fun libraryQuoteBook(l: String)      = t(l, "Kitap adı",                  "Navê pirtûkê")
    fun libraryQuoteAuthor(l: String)    = t(l, "Yazar adı",                  "Navê nivîskar")
    fun libraryReviewBook(l: String)     = t(l, "Kitap seçin",                "Pirtûkê hilbijêre")
    fun libraryReviewTitle(l: String)    = t(l, "İnceleme başlığı",           "Sernavê nirxandinê")
    fun libraryReviewRating(l: String)   = t(l, "Puan",                       "Pûan")
    fun bookChaptersTitle(l: String, n: Int) = t(l, "Bölümler ($n)",          "Beş ($n)")
    fun bookChaptersEmpty(l: String)     = t(l, "Henüz bölüm eklenmemiş",    "Beş tune")
    fun bookNewTitle(l: String)          = t(l, "Yeni Kitap",                 "Pirtûk Nû")
    fun bookNameLabel(l: String)         = t(l, "Kitap Adı *",                "Sernavê Pirtûkê *")
    fun bookDescLabel(l: String)         = t(l, "Açıklama",                   "Danasîn")
    fun bookGenreLabel(l: String)        = t(l, "Tür",                        "Cûre")
    fun bookCreateBtn(l: String)         = t(l, "Oluştur",                    "Çêke")
    fun prevChapter(l: String)           = t(l, "Önceki",                     "Berî")
    fun nextChapter(l: String)           = t(l, "Sonraki",                    "Paşê")
    fun wordCount(l: String, n: Any = "")    = if (n.toString().isBlank()) t(l, "kelime", "peyv") else t(l, "$n kelime", "$n peyv")
    fun readingStatus(l: String, key: String) = when (key) {
        "okuyorum"         -> t(l, "Okuyorum",        "Dixwînim")
        "okumak_istiyorum" -> t(l, "Okumak İstiyorum","Dixwazim Bixwînim")
        "okudum"           -> t(l, "Okudum",          "Xwendim")
        "biraktim"         -> t(l, "Bıraktım",        "Berda")
        else               -> key
    }
    fun readingListEmpty(l: String)      = t(l, "Bu listede kitap yok",       "Di vê lîsteyê de pirtûk tune")
    fun addToReadingList(l: String)      = t(l, "Okuma Listesine Ekle",       "Li Lîsteya Xwendinê Zêde Bike")

    // ── Bildirimler (NotificationsScreen) ────────────────────────────────────
    fun notifTitle(l: String)            = t(l, "Bildirimler",                "Agahdarî")
    fun notifUnread(l: String, n: Int)   = t(l, "$n okunmamış",              "$n nexwendî")
    fun notifBack(l: String)             = t(l, "Geri",                       "Vegere")
    fun notifMarkAll(l: String)          = t(l, "Tümünü oku",                 "Hemû bixwîne")
    fun notifEmpty(l: String)            = t(l, "Henüz bildirim yok",         "Agahdarî tune")
    fun notifEmptyDesc(l: String)        = t(l, "Yeni bildirimler burada görünecek", "Agahdariyên nû dê li vir xuya bikin")

    // ── PostDetail ────────────────────────────────────────────────────────────
    fun likesCount(l: String, n: Int)    = t(l, "$n beğeni",                  "$n xweşandin")
    fun replyingTo(l: String, name: String) = t(l, "@$name yanıtlanıyor",     "@$name bersiv dide")
    fun replyHint(l: String, name: String)  = t(l, "@$name yanıtla...",       "@$name bersiv bide...")
    fun replyAction(l: String)           = t(l, "Yanıtla",                    "Bersiv bide")
    fun replyingToSuffix(l: String)      = t(l, "yanıtlanıyor",               "bersiv dide")
    fun deleteFailed(l: String)          = t(l, "Silinemedi",                 "Nehat jêbirin")
    fun editCommentTitle(l: String)      = t(l, "Yorumu Düzenle",             "Şîroveyê Biguherîne")
    fun editCommentHint(l: String)       = t(l, "Yorumunu düzenle...",        "Şîroveya xwe biguherîne...")
    fun editedLabel(l: String)           = t(l, "düzenlendi",                 "guherî")

    // ── LinkifyText ───────────────────────────────────────────────────────────
    fun showLess(l: String)              = t(l, "Daha Az Göster",             "Kêmtir Nîşan Bide")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun continueWithGoogle(l: String)    = t(l, "Google ile devam et",        "Bi Google re berdewam bike")
    fun googleRecommended(l: String)     = t(l, "Önerilen",                   "Pêşniyarkirî")
    fun emailNotVerifiedTitle(l: String)  = t(l, "E-posta doğrulanmamış", "E-name nehat pejirandin")
    fun emailNotVerifiedBody(l: String)   = t(l, "Gelen kutunu ve spam klasörünü kontrol et. Doğrulama maili tekrar gönderildi.", "Qutiya giriyan û qutiya spamê binêre. E-nameya piştrastkirinê ji nû ve hate şandin.")
    fun emailNotVerifiedGoogle(l: String) = t(l, "Aynı e-posta ile Google ile giriş yaparak da hesabını doğrulayabilirsin.", "Bi heman e-nameyê bi Google re têkevin da ku hesabê xwe bipejirînin.")
    fun googleWarning(l: String)         = t(l, "Google ile giriş yapmanızı öneririz. E-posta ile kayıt olursanız, e-posta doğrulaması gerekebilir.", "Em pêşniyar dikin ku bi Google têkevin. Ger bi e-nameyê qeyd bikin, dibe ku pejirandina e-nameyê pêwist be.")
    fun orDivider(l: String)             = t(l, "ya da",                      "an jî")
    fun yourName(l: String)              = t(l, "Adın",                       "Navê te")

    // ── PostDetailScreen ──────────────────────────────────────────────────────
    fun postNotFound(l: String)          = t(l, "Gönderi bulunamadı",         "Nivîs nehat dîtin")

    // ── KurdiScreen ───────────────────────────────────────────────────────────
    fun levelLabel(l: String, n: Int)    = t(l, "Seviye $n",                  "Asta $n")
    fun lessonNotFound(l: String)        = t(l, "Ders bulunamadı",            "Ders nehat dîtin")
    fun dailyGoal(l: String)             = t(l, "Günlük Hedef",               "Armanca Rojane")
    fun dailyGoalDesc(l: String)         = t(l, "Her gün pratik yap",         "Her roj pratîk bike")
    fun comingSoon(l: String)            = t(l, "Yakında",                    "Zû tê")
    fun topicHintLabel(l: String)        = t(l, "Konu",                       "Mijar")

    // ── MessagesScreens ───────────────────────────────────────────────────────
    fun searchMessages(l: String)        = t(l, "Mesajlarda ara...",          "Peyaman bigere...")
    fun newConversation(l: String)       = t(l, "Yeni Konuşma",               "Axaftinek Nû")
    fun deleteConvConfirm(l: String)     = t(l, "Sohbeti silmek istiyor musun?", "Dixwazî vê sohbetê jê bibî?")
    fun noComments(l: String)            = t(l, "Henüz yorum yok",            "Hîn şîrove tune")
    fun post(l: String)                  = t(l, "Gönderi",                    "Nivîs")
    fun followSomeone(l: String)         = t(l, "Takip ettiğin kimse gönderi paylaşmadı", "Kesek ku şopandî nivîs par nekir")

    // ── Profil: Okuma Özeti Hero ─────────────────────────────────────────────
    fun profileBooksRead(l: String, n: Int)   = t(l, "$n kitap okudum",  "$n pirtûk min xwendin")
    fun profileQuotesCount(l: String, n: Int) = t(l, "$n alıntı",        "$n gotin")
    fun profileStreakDays(l: String, n: Int)  = t(l, "$n gün streak",    "$n roj streak")

    // ── Feed: Arkadaşlar ne okuyor? şeridi ───────────────────────────────────
    fun friendsReadingTitle(l: String)        = t(l, "Arkadaşların ne okuyor?", "Hevalên te çi dixwînin?")
    fun friendsReadingPage(l: String, n: Int) = t(l, "$n. sayfada",             "rûpel $n")
}
