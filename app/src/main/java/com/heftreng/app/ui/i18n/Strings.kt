package com.heftreng.app.ui.i18n

// ═══════════════════════════════════════════════════════════════════════════════
// HEFTREng — Türkçe / Kurmancî / Zazakî / Soranî Lokalizasyon
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
    private fun t(lang: String, tr: String, ku: String, zza: String = ku, ckb: String = ku) = when (lang) {
        "ku"  -> ku
        "zza" -> zza
        "ckb" -> ckb
        else  -> tr
    }

    // ── Alt Navigasyon ────────────────────────────────────────────────────────
    fun navFeed(l: String)         = t(l, "Gönderi", "Nivîs", "Parvekerdiş", "بڵاوکراوە")
    fun navBlog(l: String)         = t(l, "Blog", "Blog", "Blog", "بلۆگ")
    fun navBooks(l: String)        = t(l, "Kitaplar", "Pirtûk", "Pirtûkî", "پڕتووکەکان")
    fun navLibrary(l: String)      = t(l, "Kütüphane", "Pirtûkxane", "Pirtûkxane", "پڕتووکخانە")
    fun navDiscover(l: String)     = t(l, "Keşfet", "Vedîtin", "Bıvin", "دۆزینەوە")
    fun navKurdi(l: String)        = t(l, "Kurdî", "Kurdî", "Kurdî", "کوردی")
    fun navProfile(l: String)      = t(l, "Profil", "Profîl", "Profîl", "پرۆفایل")
    fun navSearch(l: String)       = t(l, "Keşfet", "Keşif bike", "Cigêrayış", "گەڕان")
    fun navMessages(l: String)     = t(l, "Mesajlar", "Peyam", "Peyamî", "پەیامەکان")
    fun navNotifs(l: String)       = t(l, "Bildirimler", "Agahî", "Agahdarî", "ئاگادارکردنەوەکان")
    fun navSettings(l: String)     = t(l, "Ayarlar", "Mîheng", "Sazkerdışî", "ڕێکخستنەکان")

    // ── Genel Butonlar ────────────────────────────────────────────────────────
    fun save(l: String)            = t(l, "Kaydet", "Tomar bike", "Qeyd bıke", "پاشەکەوت بکە")
    fun ok(l: String)              = t(l, "Tamam", "Temam", "Temam", "باشە")
    fun cancel(l: String)          = t(l, "İptal", "Betal bike", "Betal bıke", "هەڵوەشاندنەوە")
    fun delete(l: String)          = t(l, "Sil", "Jê bibe", "Bısterıne", "بسڕەوە")
    fun edit(l: String)            = t(l, "Düzenle", "Biguhêze", "Bıguherne", "دەستکاری بکە")
    fun send(l: String)            = t(l, "Gönder", "Bişîne", "Bırşawı", "بنێرە")
    fun back(l: String)            = t(l, "Geri", "Vegere", "Peyser", "بگەڕێوە")
    fun confirm(l: String)         = t(l, "Onayla", "Piştrast bike", "Senık bıke", "پشتڕاست بکەوە")
    fun loading(l: String)         = t(l, "Yükleniyor...", "Tê barkirin...", "Бар beno...", "بار دەکرێت...")
    fun retry(l: String)           = t(l, "Tekrar Dene", "Dîsa biceribîne", "Reş xeyret bıke", "دووبارە هەوڵ بدەوە")
    fun close(l: String)           = t(l, "Kapat", "Bigire", "Bıqefe", "دابخە")
    fun next(l: String)            = t(l, "Sonraki", "Pêştir", "Dıma", "دواتر")
    fun copyText(l: String)        = t(l, "Kopyala", "Kopî bike", "Kopî bıke", "کۆپی بکە")
    fun copied(l: String)          = t(l, "Kopyalandı", "Kopî bû", "Kopî bî", "کۆپی کرا")
    fun finish(l: String)          = t(l, "Bitir", "Bidawî bike", "Qedîne", "کۆتایی پێبهێنە")
    fun share(l: String)           = t(l, "Paylaş", "Parve bike", "Parve bıke", "هاوبەشی بکە")
    fun noResult(l: String)        = t(l, "Sonuç bulunamadı", "Encam nehate dîtin", "Encam nêame dîtene", "ئەنجام نەدۆزرایەوە")
    fun error(l: String)           = t(l, "Bir hata oluştu", "Çewtiyeke derket", "Xetayêk çêbî", "هەڵەیەک ڕوویدا")

    // ── Sosyal ───────────────────────────────────────────────────────────────
    fun follow(l: String)          = t(l, "Takip Et", "Şopîne", "Dıma şo", "شوێنکەوتن")
    fun unfollow(l: String)        = t(l, "Takibi Bırak", "Şopînê berde", "Dıma şîyene vindîne", "واز لە شوێنکەوتن بهێنە")
    fun followers(l: String)       = t(l, "Takipçi", "Şopîner", "Dımaşêyî", "شوێنکەوتووان")
    fun following(l: String)       = t(l, "Takip", "Şopandî", "Dımaşîyayeyî", "شوێنکەوتووەکان")
    fun followRequested(l: String) = t(l, "İstek Gönderildi", "Daxwaz Şand", "Waxte ronîya", "داواکاری نێردرا")
    fun followRequestAccept(l: String) = t(l, "Onayla", "Qebûl bike", "Qebûl bıke", "پەسەندکردن")
    fun followRequestDecline(l: String) = t(l, "Reddet", "Red bike", "Red bıke", "ڕەتکردنەوە")
    fun followRequestTitle(l: String, name: String) =
        t(l, "$name seni takip etmek istiyor", "$name dixwaze te şopîne")
    fun likes(l: String)           = t(l, "Beğeni", "Hez kirin", "Ecizî", "بەدڵبوونەکان")
    fun comments(l: String)        = t(l, "Yorum", "Şîrove", "Şîroveî", "لێدوانەکان")
    fun posts(l: String)           = t(l, "Gönderi", "Nivîs", "Parvekerdişî", "بڵاوکراوەکان")
    fun readMore(l: String)        = t(l, "Devamını Oku", "Zêdetir bixwîne", "Devamî bıwane", "زیاتر بخوێنەوە")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun login(l: String)           = t(l, "Giriş Yap", "Têkeve", "Kewe zere", "بچۆ ژوورەوە")
    fun logout(l: String)          = t(l, "Çıkış Yap", "Derkeve", "Veje derya", "بچۆ دەرەوە")
    fun register(l: String)        = t(l, "Kayıt Ol", "Qeyd bibe", "Qeyd bıbe", "تۆمارببە")
    fun email(l: String)           = t(l, "E-posta", "E-name", "E-name", "ئیمەیڵ")
    fun password(l: String)        = t(l, "Şifre", "Şîfre", "Şîfre", "وشەی نهێنی")
    fun fullName(l: String)        = t(l, "Ad Soyad", "Nav û Nasname", "Name û Pêname", "ناوی تەواو")
    fun username(l: String)        = t(l, "Kullanıcı Adı", "Navê Bikarhêner", "Nameyê Xebatkerî", "ناوی بەکارهێنەر")
    fun forgotPass(l: String)      = t(l, "Şifremi Unuttum", "Şîfreya min ji bîr bû", "Şîfreya mı xo ra şîya", "وشەی نهێنیم لەبیرچووە")
    fun noAccount(l: String)       = t(l, "Hesabın yok mu? Kayıt ol", "Hesabê te tune? Qeyd bibe", "Hesabê to çıniyo? Qeyd bıbe", "هەژمارت نییە؟ تۆمارببە")
    fun hasAccount(l: String)      = t(l, "Hesabın var mı? Giriş yap", "Hesabê te heye? Têkeve", "Hesabê to esto? Kewe zere", "هەژمارت هەیە؟ بچۆ ژوورەوە")

    // ── Feed ──────────────────────────────────────────────────────────────────
    fun whatsOnMind(l: String)     = t(l, "Ne düşünüyorsun?", "Tu çi difikiri?", "Fikirê to de çi esto?", "بیر لە چی دەکەیتەوە؟")
    fun postTitleHint(l: String)   = t(l, "Başlık (opsiyonel)", "Sernav (vebijarkî)", "Sername (tercîhî)", "سەردێڕ)")
    fun chooseTopic(l: String)     = t(l, "Konu seç", "Mijarê hilbijêre", "Mewsû bîjîne", "بابەتێک هەڵبژێرە")
    val postTopics = listOf("genel", "kitap", "alinti", "soru", "tartisma", "siir")
    fun topicLabel(l: String, key: String) = when (key) {
        "kitap"    -> t(l, "Kitap",     "Pirtûk")
        "alinti"   -> t(l, "Alıntı",    "Jêgirt")
        "soru"     -> t(l, "Soru",      "Pirs")
        "tartisma" -> t(l, "Tartışma",  "Niqaş")
        "siir"     -> t(l, "Şiir",      "Helbest")
        else       -> t(l, "Genel",     "Giştî")
    }
    fun postHint(l: String)        = t(l, "Düşüncelerini paylaş...", "Ramanên xwe parve bike...", "Fikirê xo parve bıke...", "بیرۆکەکانت هاوبەش بکە...")
    fun like(l: String)            = t(l, "Beğen", "Hez bike", "Bıheznne", "بەدڵبوون")
    fun comment(l: String)         = t(l, "Yorum", "Şîrove", "Şîrove", "لێدوان")
    fun repost(l: String)          = t(l, "Tekrar Paylaş", "Dîsa parve bike", "Onso parve bıke", "دووبارە بڵاوکردنەوە")
    fun undoRepostTitle(l: String) = t(l, "Repostu geri al", "Ji nû ve parve kirinê betal bike", "Parvekerdışî peyser bıgêre", "بڵاوکردنەوەکە پاشگەز بکەوە")
    fun undoRepostBody(l: String)  = t(l, "Bu gönderi profilinden kaldırılacak.", "Ev nivîs ê ji profîla te were rakirin.", "Na parvekerdış profîlê to ra darîyeno we.", "ئەم بڵاوکراوەیە لە پرۆفایلەکەت لادەبرێت.")
    fun undoRepostConfirm(l: String) = t(l, "Geri Al", "Betal Bike", "Peyser Bıgêre", "پاشگەزبوونەوە")
    fun addComment(l: String)      = t(l, "Yorum Ekle", "Şîrove zêde bike", "Şîrove pîya bıke", "لێدوان زیاتر بکە")
    fun commentHint(l: String)     = t(l, "Yorumunu yaz...", "Şîroveya xwe binivîse...", "Şîroveya xo bınuse...", "لێدوانەکەت بنووسە...")
    fun noPost(l: String)          = t(l, "Henüz gönderi yok", "Hîn nivîs tune", "Hela parvekerdış çıniyo", "هێشتا هیچ بڵاوکراوەیەک نییە")
    fun deletePost(l: String)      = t(l, "Gönderiyi Sil", "Nivîsê jê bibe", "Parvekerdışî bısterıne", "بڵاوکراوەکە بسڕەوە")
    fun deletePostConfirm(l: String) = t(l,
        "Bu gönderi kalıcı olarak silinecek. Emin misin?",
        "Ev nivîs dê bê vegere were jêbirin. Tu piştrast î?")
    fun report(l: String)          = t(l, "Şikayet Et", "Rapor bike", "Şıkayet bıke", "سکاڵا بکە")

    // ── Profil ────────────────────────────────────────────────────────────────
    fun editProfile(l: String)     = t(l, "Profili Düzenle", "Profîlê biguhêze", "Profîlî bıguherne", "دەستکاری پرۆفایل بکە")
    fun bio(l: String)             = t(l, "Hakkında", "Der barê min", "Heqê mı de", "دەربارەی من")
    fun website(l: String)         = t(l, "Web Sitesi", "Malpera min", "Malper", "ماڵپەڕ")
    fun joined(l: String)          = t(l, "Katıldı", "Beşdar bû", "Bî endam", "بەشداربوو")
    fun noPosts(l: String)         = t(l, "Henüz gönderi yok", "Hîn nivîs tune", "Hela parvekerdış çıniyo", "هێشتا هیچ بڵاوکراوەیەک نییە")
    fun savedPosts(l: String)      = t(l, "Kaydedilenler", "Tomarkirî", "Qeydkerdeyî", "پاشەکەوتکراوەکان")
    fun profilePhoto(l: String)    = t(l, "Profil Foto", "Wêneya Profîlê", "Resmê Profîlî", "وێنەی پرۆفایل")
    fun coverPhoto(l: String)      = t(l, "Kapak Foto", "Wêneya Bergê", "Resmê Serî", "وێنەی بەرگ")

    // ── Blog ──────────────────────────────────────────────────────────────────
    fun blogEmpty(l: String)       = t(l, "Blog yazısı bulunamadı", "Gotara blogê nehate dîtin", "Nuşteyê blogî nêamê dîtene", "بابەتی بلۆگ نەدۆزرایەوە")
    fun readTime(l: String)        = t(l, "dk okuma", "xul. xwendin", "deq. wendış", "خولەک خوێندنەوە")

    // ── Kitaplar ──────────────────────────────────────────────────────────────
    fun readingList(l: String)     = t(l, "Okuma Listesi", "Lîsteya Xwendinê", "Lîsteya wendışî", "لیستەی خوێندنەوە")
    fun addToList(l: String)       = t(l, "Listeye Ekle", "Lîsteyê zêde bike", "Lîsteyi de pîya bıke", "زێدە بکە بۆ لیست")
    fun removeFromList(l: String)  = t(l, "Listeden Çıkar", "Ji lîsteyê derxe", "Lîsteyi ra vec", "لە لیستەکەوە دەریبکە")
    fun quotes(l: String)          = t(l, "Alıntılar", "Jêgirt", "Gırotışî", "وەرگیراوەکان")
    fun authors(l: String)         = t(l, "Yazarlar", "Nivîskar", "Nuştoxî", "نووسەران")
    fun serials(l: String)         = t(l, "Seriler", "Rêze", "Rêzeyî", "زنجیرەکان")

    // ── Kurdî Dersleri ────────────────────────────────────────────────────────
    fun kurdiUnits(l: String)      = t(l, "Üniteler", "Yekîne", "Yekîteyî", "یەکەکان")
    fun kurdiDict(l: String)       = t(l, "Sözlük", "Ferheng", "Ferheng", "فەرهەنگ")
    fun kurdiGrammar(l: String)    = t(l, "Dilbilgisi", "Rêziman", "Rêziman", "ڕێزمان")
    fun kurdiLeaderboard(l: String)= t(l, "Sıralama", "Rêzkirin", "Rêzebendî", "ڕیزبەندی")
    fun leaderboardTitle(l: String)= t(l, "XP Sıralaması", "Rêzkirin a XP", "Rêzebendîya XP", "ڕیزبەندی XP")
    fun leaderboardEmpty(l: String)= t(l, "Henüz sıralama yok", "Hêj rêzkirin tune", "Hela rêzebendî çıniya", "هێشتا ڕیزبەندی نییە")
    fun leaderboardYou(l: String)  = t(l, "Sen", "Tu", "Tı", "تۆ")
    fun lessonComplete(l: String)  = t(l, "Ders Tamamlandı! 🎉", "Ders Qediya! 🎉", "Ders qediya! 🎉", "وانە تەواوبوو! 🎉")
    fun correctAnswer(l: String)   = t(l, "Doğru!", "Rast!", "Raşt!", "ڕاستە!")
    fun wrongAnswer(l: String)     = t(l, "Yanlış!", "Xelet!", "Zewt!", "هەڵەیە!")
    fun checkAnswer(l: String)     = t(l, "Kontrol Et", "Kontrol bike", "Kontrol bıke", "پشکنین بکە")
    fun continueLesson(l: String)  = t(l, "Devam", "Berdewam bike", "Berdewam bıke", "بەردەوام بە")
    fun tapInOrder(l: String)      = t(l, "Kelimelere sırayla dokun", "Peyvan bi rêzê bixin", "Ca ra Qesan sero dest bıron", "بە ڕیز دەست لە وشەکان بدە")
    fun typeAnswer(l: String)      = t(l, "Cevabını yaz...", "Bersiva xwe binivîse...", "Cawebê xo bınuse...", "وەڵامەکەت بنووسە...")
    fun xpGained(l: String)        = t(l, "XP kazandın!", "XP qezenc kir!", "To XP qezenc kerd!", "XP ـت دەستکەوت!")
    fun finishLesson(l: String)    = t(l, "Dersi Bitir 🎉", "Dersa Bidawî Bike 🎉", "Dersî bıqedîne 🎉", "وانەکە کۆتایی پێبهێنە 🎉")
    fun nextQuestion(l: String)    = t(l, "Sonraki →", "Pêştir →", "Dıma →", "دواتر →")
    fun toQuestions(l: String)     = t(l, "Sorulara Geç →", "Biçe Pirsên →", "Şo Pirsan →", "بڕۆ بۆ پرسیارەکان →")
    fun complete(l: String)        = t(l, "Tamamla 🎉", "Temam bike 🎉", "Temam bıke 🎉", "تەواوی بکە 🎉")
    fun correctCount(l: String, n: Int) = t(l, "$n doğru cevap ✓", "$n bersivên rast ✓")
    fun correctAnswerIs(l: String, a: String) = t(l, "Doğru cevap: $a", "Bersiva rast: $a")
    fun correctOrder(l: String, w: String)    = t(l, "Doğru sıra: $w",  "Rêza rast: $w")
    fun tryAgain(l: String)        = t(l, "Tekrar Dene", "Dîsa biceribîne", "Onso xeyret bıke", "دووبارە هەوڵ بدەوە")

    // ── Mesajlar ──────────────────────────────────────────────────────────────
    fun messagesTitle(l: String)   = t(l, "Mesajlar", "Peyam", "Peyamî", "پەیامەکان")
    fun messageHint(l: String)     = t(l, "Mesaj yaz...", "Peyamê binivîse...", "Peyam bınuse...", "پەیامێک بنووسە...")
    fun noMessages(l: String)      = t(l, "Henüz mesaj yok", "Hîn peyam tune", "Hela peyam çıniyo", "هێشta هیچ پەیامێک نییە")
    fun newMessage(l: String)      = t(l, "Yeni Mesaj", "Peyama Nû", "Peyamo Newe", "پەیامی نوێ")

    // ── Bildirimler ───────────────────────────────────────────────────────────
    fun noNotif(l: String)         = t(l, "Henüz bildirim yok", "Hîn agahî tune", "Hela agahdarî çıniya", "هێشتا ئاگادارکردنەوە نییە")
    fun notifLike(l: String)       = t(l, "gönderinizi beğendi", "nivîsa we xweş dît", "parvekerdışê to eciz kerd", "بڵاوکراوەکەت بەدڵ بوو")
    fun notifComment(l: String)    = t(l, "gönderinize yorum yaptı", "li ser nivîsa we şîrove kir", "parvekerdışê to sero şîrove kerd", "لەسەر بڵاوکراوەکەت لێدوانی دا")
    fun notifMention(l: String)    = t(l, "sizi bir yorumda etiketledi", "we di şîroveyekê de nîşan da", "to şîrove de etîket kerd", "لە لێدوانێکدا ئاماژەی پێکردیت")
    fun notifFollow(l: String)     = t(l, "sizi takip etmeye başladı", "dest bi şopandina we kir", "dest kerd dıma şîyena to", "دەستی کرد بە شوێنکەوتنت")
    fun notifRepost(l: String)     = t(l, "gönderinizi paylaştı", "nivîsa we parve kir", "parvekerdışê to parve kerd", "بڵاوکراوەکەت هاوبەش کرد")
    fun notifNew(l: String)        = t(l, "yeni bir bildirim", "agahdariya nû", "agahdarîya neweye", "ئاگادارکردنەوەیەکی نوێ")

    // ── Ayarlar ───────────────────────────────────────────────────────────────
    fun settingsTitle(l: String)   = t(l, "Ayarlar", "Mîheng", "Sazkerdışî", "ڕێکخستنەکان")
    fun darkMode(l: String)        = t(l, "Karanlık Mod", "Moda Tarî", "Modê Tarî", "دۆخی تاریک")
    fun lightMode(l: String)       = t(l, "Aydınlık Mod", "Moda Ronî", "Modê Ronî", "دۆخی ڕووناک")
    fun systemMode(l: String)      = t(l, "Sistemi Takip Et", "Li gorî Pergalê", "Gorî Sîstemî", "بەپێی سیستەم")
    fun appLanguage(l: String)     = t(l, "Uygulama Dili", "Zimana Bernameyê", "Zıwanê Bernameyî", "زمانی بەرنامە")
    fun about(l: String)           = t(l, "Hakkında", "Der barê me", "Heqê ma de", "دەربارەی ئێمە")
    fun version(l: String)         = t(l, "Sürüm", "Guherto", "Guherto", "وەشان")
    fun account(l: String)         = t(l, "Hesap", "Hesab", "Hesab", "هەژمار")
    fun privacy(l: String)         = t(l, "Gizlilik", "Nepenî", "Nîmıtekî", "تایبەتمەندی")

    // ── Yazar Paneli ──────────────────────────────────────────────────────────
    fun yazarTitle(l: String)      = t(l, "Nivîskar / Yazar Paneli", "Nivîskar", "Panelê Nuştekarî", "پانێلی نووسەر")
    fun yazarWrite(l: String)      = t(l, "Yaz", "Binivîse", "Bınuse", "بنووسە")
    fun yazarMyPosts(l: String)    = t(l, "Yazılarım", "Nivîsên min", "Nuşteyê Mı", "نووسینەکانم")
    fun yazarSubmit(l: String)     = t(l, "Yazıyı Gönder", "Nivîsê bişîne", "Nuşteyî bırşawı", "نووسینەکە بنێرە")
    fun yazarCategory(l: String)   = t(l, "Kategori", "Kategorî", "Kategorî", "پۆل")
    fun yazarTags(l: String)       = t(l, "Etiketler", "Etîket", "Etîketî", "تاگەکان")
    fun yazarPending(l: String)    = t(l, "Bekliyor ⏳", "Li bendê ye ⏳", "Paweno ⏳", "چاوەڕوانە ⏳")
    fun yazarApproved(l: String)   = t(l, "Yayında ✅", "Weşandî ✅", "Weşîya ✅", "بڵاوکراوەتەوە ✅")
    fun yazarRejected(l: String)   = t(l, "Reddedildi ❌", "Red kir ❌", "Red bî ❌", "ڕەتکراوەتەوە ❌")
    fun yazarWithdraw(l: String)   = t(l, "Geri Çek", "Vegerîne", "Peyser Bıgêre", "پاشگەزبوونەوە")

    // ── Arama ─────────────────────────────────────────────────────────────────
    fun searchHint(l: String)      = t(l,
        "Kullanıcı, gönderi veya kitap ara...",
        "Bikarhêner, nivîs an pirtûk bigere...")
    fun searchPeople(l: String)    = t(l, "Kişiler", "Kes", "Kesî", "کەسەکان")
    fun searchPosts(l: String)     = t(l, "Gönderiler", "Nivîs", "Parvekerdişî", "بڵاوکراوەکان")
    fun searchBooks(l: String)     = t(l, "Kitaplar", "Pirtûk", "Pirtûkî", "پڕتووکەکان")

    // ── Feed (yeni) ───────────────────────────────────────────────────────────
    fun filterAll(l: String)        = t(l, "Herkes", "Hemû", "Pêro", "هەمووان")
    fun filterFollowing(l: String)  = t(l, "Takip Edilenler", "Şopîner", "Dımaşîyayeyî", "شوێنکەوتووەکان")
    fun likedBy(l: String)          = t(l, "Beğenenler", "Hez Kirinên", "Ecizkerdoyî", "بەدڵبووان")
    fun reportPost(l: String)       = report(l)   // alias → report()
    fun newPost(l: String)          = t(l, "Yeni Gönderi", "Nivîsek Nû", "Parvekerdışo Newe", "بڵاوکراوەی نوێ")
    fun anonymous(l: String)        = t(l, "Anonim", "Bênas", "Bênas", "ناناسراو")
    fun showMore(l: String)         = t(l, "Daha Fazla Göster", "Zêdetir Nîşan Bide", "Zêdeya nîşan bıde", "زیاتر پیشان بدە")
    fun likeAction(l: String)       = like(l)   // alias → like()

    // ── Profil (yeni) ─────────────────────────────────────────────────────────
    fun followersTitle(l: String, count: Int) = t(l, "Takipçiler ($count)", "Şopîner ($count)")
    fun followingTitle(l: String, count: Int) = t(l, "Takip ($count)",      "Şopandî ($count)")

    // ── Seriler / Kitaplar (yeni) ─────────────────────────────────────────────
    fun chapter(l: String)          = t(l, "Bölüm", "Beş", "Beş", "بەش")
    fun chapters(l: String)         = t(l, "Bölümler", "Beşên", "Beşî", "بەشەکان")
    fun noChapters(l: String)       = t(l, "Henüz bölüm yok", "Hîn beş tune", "Hela beş çıniyo", "هێشتا هیچ بەشێک نییە")
    fun deleteChapter(l: String)    = t(l, "Bölümü Sil", "Beşê jê bibe", "Beşî bısterıne", "بەشەکە بسڕەوە")
    fun editChapter(l: String)      = t(l, "Bölümü Düzenle", "Beşê biguherîne", "Beşî bıguherne", "بەشەکە دەستکاری بکە")
    fun newChapter(l: String)       = t(l, "Yeni Bölüm", "Beşa Nû", "Beşo Newe", "بەشی نوێ")
    fun chapterTitle(l: String)     = t(l, "Bölüm Başlığı", "Sernavê Beşê", "Sernameyê Beşî", "سەردێڕی بەش")
    fun create(l: String)           = t(l, "Oluştur", "Çêke", "Vıraze", "دروست بکە")
    fun genre(l: String)            = t(l, "Tür", "Cûre", "Cûre", "جۆر")

    // ── Yazar Paneli (yeni) ───────────────────────────────────────────────────
    fun loginToWrite(l: String)     = t(l, "Yazı göndermek için giriş yapmalısın", "Ji bo nivîsandina nivîsê têkeve", "Seba rıştırena nuşteyî gere kewe zere", "بۆ ناردنی نووسین دەبێت بچیتە ژوورەوە")
    fun submitSuccess(l: String)    = t(l, "✓ Yazın gönderildi! Admin onayı bekleniyor.", "✓ Nivîsa te hat şandin! Li bendê admin e.", "✓ Nuşteya to amê rıştene! Pawıtışê qebûlê adminî de yo.", "✓ نووسینەکەت نێردرا! چاوەڕوانی پەسەندکردنی بەڕێوەبەرە.")
    fun noSubmissions(l: String)    = t(l, "Henüz yazı göndermedin", "Hîn tu nivîs neşandiye", "Hela to tu nuşte nêrışta", "هێشتا هیچ نووسینێکت نەنێردووە")
    fun titleLabel(l: String)       = t(l, "Başlık", "Sernavê", "Sername", "سەردێڕ")
    fun contentLabel(l: String)     = t(l, "İçerik", "Naverok", "Zerreyî", "ناوەڕۆک")
    fun summaryLabel(l: String)     = t(l, "Kısa Özet (opsiyonel)", "Kurteya Nivîsê (vebijarkî)", "Kurteya Nuşteyî (tercîhî)", "کورتەی نووسین)")
    fun sending(l: String)          = t(l, "Gönderiliyor...", "Tê şandin...", "Tê rıştene...", "دەنێردرێت...")
    fun contentLangBoth(l: String)  = t(l, "İkisi", "Herdu", "Her dı", "هەردووکیان")

    // ── Kurdi Ekranı (yeni) ───────────────────────────────────────────────────
    fun kurdiTitle(l: String)       = t(l, "Kurdî Öğren", "Kurdî Fêrbibe", "Kurdî Mûs", "کوردی فێربە")
    fun startLesson(l: String)      = t(l, "Başla!", "Destpê Bike!", "Dest pê bıke!", "دەست پێبکە!")
    fun unlockWithVideo(l: String)  = t(l, "🎬 İzle, Aç", "🎬 Temaşe Bike, Veke", "🎬 Ewnîya, Veke", "🎬 تەماشە بکە، بیکەرەوە")
    fun aiGenerating(l: String)     = t(l, "Üretiliyor…", "Tê çêkirin…", "Tê vıraştene…", "دروست دەکرێت…")
    fun aiGenerate(l: String)       = t(l, "✨ Ders Oluştur", "✨ Dersê Çêke", "✨ Dersêk Vıraze", "✨ وانە دروست بکە")
    fun topicHint(l: String)        = t(l, "Renkler, Sayılar…", "Reng, Hejmar…", "Rengî, Amarî…", "ڕەنگەکان، ژمارەکان…")

    // ── Kurdi — Feed'de Paylaşma ─────────────────────────────────────────────
    fun shareLesson(l: String)         = t(l, "Dersi paylaş", "Dersê parve bike", "Dersî parve bıke", "وانەکە هاوبەش بکە")
    fun shareGrammarRule(l: String)    = t(l, "Kuralı paylaş", "Rêzimanê parve bike", "Qeydeyî parve bıke", "ڕێساکە هاوبەش بکە")
    fun shareAchievement(l: String)    = t(l, "Başarını Paylaş", "Serkeftina Xwe Parve Bike", "Serkeftina Xo Parve Bıke", "سەرکەوتنەکەت هاوبەش بکە")
    fun shareToFeed(l: String)         = t(l, "Feed'de Paylaş", "Li Feed'ê Parve Bike", "Feed de Parve Bıke", "لە Feed'دا هاوبەش بکە")
    fun shareSuccess(l: String)        = t(l, "Feed'de paylaşıldı! 🎉", "Li Feed'ê hate parvekirin! 🎉", "Feed de amê parvekerdene! 🎉", "لە Feed'دا هاوبەش کرا! 🎉")
    fun shareLessonSuccess(l: String)  = t(l, "Ders feed'de paylaşıldı!", "Ders li Feed'ê hate parvekirin!", "Ders Feed de amê parvekerdene! 🎉", "وانەکە لە Feed'دا هاوبەش کرا! 🎉")
    fun shareGrammarSuccess(l: String) = t(l, "Kural feed'de paylaşıldı!", "Rêziman li Feed'ê hate parvekirin!", "Qeyde Feed de amê parvekerdene! 🎉", "ڕێساکە لە Feed'دا هاوبەش کرا! 🎉")
    fun shareAchievementSuccess(l: String) = t(l, "Başarın feed'de paylaşıldı!", "Serkeftina te li Feed'ê hate parvekirin!")
    fun shareFailed(l: String)         = t(l, "Paylaşılamadı, tekrar dene.", "Nehat parvekirin, dîsa biceribîne.")
    fun shareLoginRequired(l: String)  = t(l, "Paylaşmak için giriş yapmalısın.", "Ji bo parvekirinê divê tu têkeve.")
    fun lessonLockedTitle(l: String)   = t(l, "Ders Kilitli",             "Ders Kilîtkirî")
    fun lessonLockedBody(l: String)    = t(l, "Bu ders kilitli. Kısa bir video izleyerek bu dersi hemen açabilirsin.", "Ev ders kilîtkirî ye. Tu dikarî vîdyoyekê temaşe bikî û vê dersê vekî.")
    fun noAdsToday(l: String)          = t(l, "Bugünkü reklam hakkın doldu.", "Îro mafê vîdyoyê nemaye.")
    fun watchAdUnlock(l: String)       = t(l, "Video izle ve aç",          "Vîdyo temaşe bike û veke")
    fun doubleXpTitle(l: String, xp: Int) = t(l, "Tebrikler! +$xp XP Kazandın!", "Xwezî! +$xp XP Qezenç Kir!")
    fun doubleXpOffer(l: String, xp: Int) = t(l, "Kısa bir video izle, $xp XP kazan!", "Vîdyoyek kurt temaşe bike, $xp XP qezenç bike!")
    fun doubleXpClaim(l: String)       = t(l, "⚡ 2x XP Kazan",            "⚡ 2x XP Bistîne")
    fun noThanks(l: String)            = t(l, "Hayır teşekkürler",        "Na spas")
    fun kurdiGrammarLabel(l: String)   = t(l, "Dilbilgisi",               "Rêziman")
    fun kurdiLessonLabel(l: String)    = t(l, "Kurdî Dersi",              "Dersa Kurdî")
    fun kurdiAchievementLabel(l: String) = t(l, "Başarı",                 "Serkeftin")
    fun repostSharedAgain(l: String)   = t(l, "Paylaşım",                 "Dîsa Parvekirî")
    fun repostGeneric(l: String)       = t(l, "Paylaşım",                 "Parvekirî")
    fun achievementLevelLabel(l: String, level: Int) = t(l, "Seviye $level", "Ast $level")
    fun xpLabel(l: String)             = t(l, "XP",                        "XP")
    fun streakDaysLabel(l: String)     = t(l, "Günlük Seri",                "Rojên Berdewam")
    fun achievementCaption(l: String)  = t(l, "Kurdî öğrenme yolculuğunda!", "Di rêwîtiya fêrbûna Kurdî de ye!")
    fun shareAsImage(l: String)        = t(l, "Görsel Olarak Paylaş",       "Wek Wêne Parve Bike")
    fun shareHomeFeed(l: String)       = t(l, "Ana Sayfa",                  "Rûpela Sereke")

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
        "book_quote"     -> t(l, "Kitap Alıntısı",  "Jêgirta Pirtûkê")
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
    fun copyProfileLink(l: String)    = t(l, "Profil Linkini Kopyala",     "Lînka Profîlê Kopî Bike")
    fun copyPostLink(l: String)       = t(l, "Gönderi Linkini Kopyala",     "Lînka Peyamê Kopî Bike")
    fun copyLessonLink(l: String)     = t(l, "Ders Linkini Kopyala",        "Lînka Dersê Kopî Bike")
    fun copyGrammarLink(l: String)    = t(l, "Kural Linkini Kopyala",       "Lînka Rêzimanê Kopî Bike")
    fun linkCopied(l: String)         = t(l, "Link kopyalandı!",            "Lînk hate kopîkirin!")
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
    fun addQuote(l: String)              = t(l, "Alıntı ekle",                "Jêgirtê zêde bike")
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
    fun libraryTabQuotes(l: String)      = t(l, "Alıntılar",                  "Jêgirt")
    fun libraryTabReviews(l: String)     = t(l, "İncelemeler",                "Nirxandin")
    fun libraryTabAuthors(l: String)     = t(l, "Yazarlar",                   "Nivîskar")
    fun libraryTabBooks(l: String)       = t(l, "Kitaplar",                   "Pirtûk")
    fun libraryAddQuote(l: String)       = t(l, "Alıntı Ekle",                "Jêgirtê Zêde Bike")
    fun libraryAddReview(l: String)      = t(l, "İnceleme Ekle",              "Nirxandinê Zêde Bike")
    fun libraryQuoteHint(l: String)      = t(l, "Alıntı metnini girin...",    "Nivîsa jêgirtê binivîse...")
    fun libraryReviewHint(l: String)     = t(l, "İncelemenizi yazın...",      "Nirxandina xwe binivîse...")
    fun libraryNoQuotes(l: String)       = t(l, "Henüz alıntı yok",           "Hîn jêgirt tune")
    fun libraryNoReviews(l: String)      = t(l, "Henüz inceleme yok",         "Nirxandin tune")
    fun libraryNoAuthors(l: String)      = t(l, "Henüz yazar yok",            "Nivîskar tune")
    fun libraryNoBooks(l: String)        = t(l, "Henüz kitap yok",            "Pirtûk tune")

    // ── Alıntı Ekle Ekranı (QuoteDialog) ─────────────────────────────────────
    fun quoteDialogTitle(l: String)      = t(l, "Alıntı Ekle",                "Jêgirtê Zêde Bike")
    fun quoteTextLabel(l: String)        = t(l, "ALINTI METNİ *",             "NIVÎSA JÊGIRTÊ *")
    fun bookNameLabel(l: String)         = t(l, "KİTAP ADI",                  "NAVÊ PIRTÛKÊ")
    fun authorNameLabel(l: String)       = t(l, "YAZAR",                      "NIVÎSKAR")
    fun quoteCountSuffix(l: String, n: Int) = t(l, "$n alıntı",               "$n jêgirt")
    fun bookNotFoundWillAdd(l: String, name: String) =
        t(l, "\"$name\" sistemde yok — yeni kitap olarak eklenecek",
             "\"$name\" di pergalê de tune — wê wekî pirtûkeke nû were zêdekirin")
    fun authorNotFoundWillAdd(l: String, name: String) =
        t(l, "\"$name\" sistemde yok — yeni yazar olarak eklenecek",
             "\"$name\" di pergalê de tune — wê wekî nivîskarekî nû were zêdekirin")
    fun quotePreview(l: String)          = t(l, "Önizleme",                   "Pêşdîtin")
    fun quoteShowMore(l: String)         = t(l, "Kapat ▲",                    "Bigire ▲")
    fun quoteReadMore(l: String)         = t(l, "Devamını oku ▼",             "Zêdetir bixwîne ▼")
    fun editQuoteTitle(l: String)        = t(l, "Alıntıyı Düzenle",           "Jêgirtê Biguhêre")
    fun deleteQuoteTitle(l: String)      = t(l, "Alıntıyı Sil",               "Jêgirtê Jê Bibe")
    fun deleteQuoteConfirm(l: String)    = t(l, "Bu alıntıyı silmek istiyor musunuz?", "Tu dixwazî vê jêgirtê jê bibî?")
    fun libraryQuoteBook(l: String)      = t(l, "Kitap adı",                  "Navê pirtûkê")
    fun libraryQuoteAuthor(l: String)    = t(l, "Yazar adı",                  "Navê nivîskar")
    fun libraryReviewBook(l: String)     = t(l, "Kitap seçin",                "Pirtûkê hilbijêre")
    fun libraryReviewTitle(l: String)    = t(l, "İnceleme başlığı",           "Sernavê nirxandinê")
    fun libraryReviewRating(l: String)   = t(l, "Puan",                       "Pûan")
    fun bookChaptersTitle(l: String, n: Int) = t(l, "Bölümler ($n)",          "Beş ($n)")
    fun bookChaptersEmpty(l: String)     = t(l, "Henüz bölüm eklenmemiş",    "Beş tune")
    fun bookNewTitle(l: String)          = t(l, "Yeni Kitap",                 "Pirtûk Nû")
    fun bookNewNameLabel(l: String)      = t(l, "Kitap Adı *",                "Sernavê Pirtûkê *")
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
    fun profileQuotesCount(l: String, n: Int) = t(l, "$n alıntı",        "$n jêgirt")
    fun profileStreakDays(l: String, n: Int)  = t(l, "$n gün streak",    "$n roj streak")

    // ── Feed: Arkadaşlar ne okuyor? şeridi ───────────────────────────────────
    fun friendsReadingTitle(l: String)        = t(l, "Arkadaşların ne okuyor?", "Hevalên te çi dixwînin?")
    fun friendsReadingPage(l: String, n: Int) = t(l, "$n. sayfada",             "rûpel $n")

    // ── Tema Seçici ───────────────────────────────────────────────────────────
    fun themeTitle(l: String)            = t(l, "Tema",               "Reng")
    fun themeDarkMode(l: String)         = t(l, "Koyu Mod",           "Moda Tarî")
    fun themeDarkModeToLight(l: String)  = t(l, "Açık moda geç",     "Biçe moda ronî")
    fun themeDarkModeToDark(l: String)   = t(l, "Koyu moda geç",     "Biçe moda tarî")
    fun themeCharcoal(l: String)         = t(l, "Kömür Mürekkebi",   "Mûrekkeba Komirê")
    fun themeBook(l: String)             = t(l, "Kitap",              "Pirtûk")
    fun themeForest(l: String)           = t(l, "Orman",              "Daristan")
    fun themeOcean(l: String)            = t(l, "Okyanus",            "Okyanûs")
    fun themeSunset(l: String)           = t(l, "Gün Batımı",         "Rojavabûn")
    fun themeMonochrome(l: String)       = t(l, "Tek Renk",           "Yek Reng")
    fun textColorTitle(l: String)        = t(l, "Yazı Rengi",         "Rengê Nivîsê")
    fun textColorDefault(l: String)      = t(l, "Tema Varsayılanı",   "Xwerû ya Temayê")
}
