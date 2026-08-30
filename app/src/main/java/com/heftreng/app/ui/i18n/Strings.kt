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
        t(l, "$name seni takip etmek istiyor", "$name dixwaze te şopîne", "$name wazeno to taqîb bıkerdo", "$name دەیەوێت شوێنت بکەوێت")
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
        "Ev nivîs dê bê vegere were jêbirin. Tu piştrast î?", "Ev nivîs dê bê vegere were jêbirin. Tu piştrast î?", "Ev nivîs dê bê vegere were jêbirin. Tu piştrast î?")
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
    fun correctCount(l: String, n: Int) = t(l, "$n doğru cevap ✓", "$n bersivên rast ✓", "$n cewabê rast ✓", "$n وەڵامی ڕاست ✓")
    fun correctAnswerIs(l: String, a: String) = t(l, "Doğru cevap: $a", "Bersiva rast: $a", "Cewabê rast: $a", "وەڵامی ڕاست: $a")
    fun correctOrder(l: String, w: String)    = t(l, "Doğru sıra: $w",  "Rêza rast: $w", "Rêza rast: $w", "ڕیزی ڕاست: $w")
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
    fun appLanguage(l: String)     = t(l, "Uygulama Dili", "Zimanê Sepanê", "Zıwanê Sepanî", "زمانی بەرنامە")
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
        "Bikarhêner, nivîs an pirtûk bigere...", "Bikarhêner, nivîs an pirtûk bigere...", "Bikarhêner, nivîs an pirtûk bigere...")
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
    fun followersTitle(l: String, count: Int) = t(l, "Takipçiler ($count)", "Şopîner ($count)", "Taqîbkarî ($count)", "$count شوێنکەوتوو")
    fun followingTitle(l: String, count: Int) = t(l, "Takip ($count)",      "Şopandî ($count)", "Taqîbkerdiş ($count)", "$count شوێنکەوتن")

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
    fun shareAchievementSuccess(l: String) = t(l, "Başarın feed'de paylaşıldı!", "Serkeftina te li Feed'ê hate parvekirin!", "Serkeftina to Nuştaran de amê parvekerdene!", "سەرکەوتنی تۆ لە بڵاوکراوەکان هاوبەش کرا!")
    fun shareFailed(l: String)         = t(l, "Paylaşılamadı, tekrar dene.", "Nehat parvekirin, dîsa biceribîne.", "Nêameyo parvekerdene, carê bin bıceribne.", "هاوبەش نەکرا، دووبارە هەوڵ بدە.")
    fun shareLoginRequired(l: String)  = t(l, "Paylaşmak için giriş yapmalısın.", "Ji bo parvekirinê divê tu têkeve.", "Seba parvekerdene gani tı bikevî.", "بۆ هاوبەش کردن دەبێت بچیتە ژوورەوە.")
    fun lessonLockedTitle(l: String)   = t(l, "Ders Kilitli",             "Ders Kilîtkirî", "Ders Kilîtbiyaye", "وانەکە داخراوە")
    fun lessonLockedBody(l: String)    = t(l, "Bu ders kilitli. Kısa bir video izleyerek bu dersi hemen açabilirsin.", "Ev ders kilîtkirî ye. Tu dikarî vîdyoyekê temaşe bikî û vê dersê vekî.", "No ders kilîtbiyaye. Tı eşkenî resmêko lewiyaye bivêyenî û no dersî akerî.", "ئەم وانەیە داخراوە. دەتوانیت وێنەیەکی جوڵاوی کورت ببینیت و ئەم وانەیە بکەیتەوە.")
    fun noAdsToday(l: String)          = t(l, "Bugünkü reklam hakkın doldu.", "Îro mafê vîdyoyê nemaye.", "Ewro heqê reklamî nêmendo.", "مافی ڕیکلامی ئەمڕۆت تەواو بوو.")
    fun watchAdUnlock(l: String)       = t(l, "Video izle ve aç",          "Vîdyo temaşe bike û veke", "Resmê lewiyaye bivîne û aker", "وێنەی جوڵاو ببینە و بیکەرەوە")
    fun doubleXpTitle(l: String, xp: Int) = t(l, "Tebrikler! +$xp XP Kazandın!", "Xwezî! +$xp XP Qezenç Kir!", "Xêrbiyo! +$xp Puan Kerdê!", "پیرۆزە! +$xp خاڵت بەدەستهێنا!")
    fun doubleXpOffer(l: String, xp: Int) = t(l, "Kısa bir video izle, $xp XP kazan!", "Vîdyoyek kurt temaşe bike, $xp XP qezenç bike!", "Resmêko lewiyaye kılm bivîne, $xp puan kero!", "وێنەیەکی جوڵاوی کورت ببینە، $xp خاڵ بەدەستبهێنە!")
    fun doubleXpClaim(l: String)       = t(l, "⚡ 2x XP Kazan",            "⚡ 2x XP Bistîne", "⚡ 2x Puan Bıgêr", "⚡ ٢ هێندە خاڵ وەربگرە")
    fun noThanks(l: String)            = t(l, "Hayır teşekkürler",        "Na spas", "Nê spas", "نەخێر سوپاس")
    fun kurdiGrammarLabel(l: String)   = t(l, "Dilbilgisi",               "Rêziman", "Rêzımaneyî", "ڕێزمان")
    fun kurdiLessonLabel(l: String)    = t(l, "Kurdî Dersi",              "Dersa Kurdî", "Dersê Kirmanckî", "وانەی کوردی")
    fun kurdiAchievementLabel(l: String) = t(l, "Başarı",                 "Serkeftin", "Serkeftış", "سەرکەوتن")
    fun repostSharedAgain(l: String)   = t(l, "Paylaşım",                 "Dîsa Parvekirî", "Carê Bin Parvekerde", "دووبارە هاوبەش کراوە")
    fun repostGeneric(l: String)       = t(l, "Paylaşım",                 "Parvekirî", "Parvekerde", "هاوبەش کراوە")
    fun achievementLevelLabel(l: String, level: Int) = t(l, "Seviye $level", "Ast $level", "Ast $level", "ئاستی $level")
    fun xpLabel(l: String)             = t(l, "XP",                        "XP", "XP", "XP")
    fun streakDaysLabel(l: String)     = t(l, "Günlük Seri",                "Rojên Berdewam", "Roję Domdar", "ڕۆژە بەردەوامەکان")
    fun achievementCaption(l: String)  = t(l, "Kurdî öğrenme yolculuğunda!", "Di rêwîtiya fêrbûna Kurdî de ye!", "Rayîrê fêrbiyayîşê Kirmanckî de yo!", "لە ڕێگای فێربوونی کوردیدایە!")
    fun shareAsImage(l: String)        = t(l, "Görsel Olarak Paylaş",       "Wek Wêne Parve Bike", "Sey Resmî Parveke", "وەک وێنە هاوبەشی بکە")
    fun shareHomeFeed(l: String)       = t(l, "Ana Sayfa",                  "Rûpela Sereke", "Pele Serekiye", "پەڕەی سەرەکی")

    // ── Arama (yeni) ──────────────────────────────────────────────────────────
    fun suggestedPeople(l: String)  = t(l, "Önerilen Kişiler",     "Kesên Pêşniyarkirî", "Kesê Pêşniyarbiyayeyî", "کەسە پێشنیارکراوەکان")
    fun seeAll(l: String)           = t(l, "Tümünü Gör",           "Hemûyî Bibîne", "Gırey Bivîne", "هەمووی ببینە")
    fun peopleHubFollowing(l: String) = t(l, "Takip Edilenler",     "Yên Tê Şopandin", "Kamê Dıme Şopnayîşî", "شوێنکەوتووەکان")
    fun peopleHubFollowers(l: String) = t(l, "Takipçiler",          "Şopîner", "Şopnayoxî", "شوێنکەوتووان")
    fun peopleHubSuggested(l: String) = t(l, "Önerilenler",         "Pêşniyar", "Pêşniyarî", "پێشنیارکراوەکان")
    fun followAction(l: String)     = follow(l)   // alias → follow()
    fun notifFollowRequest(l: String) = t(l, "Takip isteği gönderdi", "Daxwaza şopînê şand", "Waştışê şopnayîşî rakerd", "داواکاری شوێنکەوتنی نارد")
    fun notifGroupToday(l: String)    = t(l, "Bugün",     "Îro", "Ewro", "ئەمڕۆ")
    fun notifGroupWeek(l: String)     = t(l, "Bu Hafta",  "Vê Hefteyê", "Vaye Hefte", "ئەم هەفتەیە")
    fun notifGroupOlder(l: String)    = t(l, "Daha Önce", "Berê", "Verê", "پێشتر")
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
    fun online(l: String)           = t(l, "Çevrimiçi",            "Serhêl", "Serhêl", "سەرهێڵ")
    fun offline(l: String)          = t(l, "Çevrimdışı",           "Nediyar", "Nediyar", "دەرهێڵ")
    fun typing(l: String)           = t(l, "Yazıyor...",           "Dinivîse...", "Nuseno...", "دەنووسێت...")
    fun edited(l: String)           = t(l, "(düzenlendi)",         "(guherî)", "(vurriştbiyo)", "(دەستکاریکرا)")
    fun deleted(l: String)          = t(l, "Bu mesaj silindi",     "Peyam hat jêbirin", "No peyam bêbar bi", "ئەم پەیامە سڕایەوە")
    fun reply(l: String)            = t(l, "Yanıtla",              "Bersiv bide", "Bersiv bide", "وەڵام بدەرەوە")
    fun voiceMessage(l: String)     = t(l, "Sesli mesaj",          "Dengbêjiya dengî", "Peyamê Dengî", "پەیامی دەنگی")
    fun playing(l: String)          = t(l, "▶ Çalıyor",           "▶ Dide", "▶ Lewiyeno", "▶ لێدەدرێت")
    fun voice(l: String)            = t(l, "🎤 Ses",              "🎤 Deng", "🎤 Deng", "🎤 دەنگ")

    // ── Ayarlar (yeni) ────────────────────────────────────────────────────────
    fun appearance(l: String)       = t(l, "Görünüm",              "Xuyangeh", "Xuyangeh", "ڕواڵەت")
    fun changePassword(l: String)   = t(l, "Şifre Değiştir",       "Şîreya Biguherîne", "Şîfre Bıvurne", "وشەی نهێنی بگۆڕە")
    fun changeEmail(l: String)      = t(l, "E-posta Değiştir",     "E-Postayê Biguherîne", "E-Postayî Bıvurne", "ئیمەیل بگۆڕە")
    fun pushNotifs(l: String)       = t(l, "Push Bildirimleri",    "Agahdariyên Push", "Agehdariyê Push", "ئاگادارکردنەوەکان")
    fun privateAccount(l: String)   = t(l, "Gizli Hesap",          "Hesabê Veşartî", "Hesabê Vaşerde", "هەژماری تایبەت")
    fun blockedUsers(l: String)     = t(l, "Engellenen Kullanıcılar", "Bikarhênerên Astengkirî", "Karberê Astengbiyayeyî", "بەکارهێنەرە بلۆککراوەکان")
    fun unblock(l: String)          = t(l, "Engeli Kaldır",        "Astengiyê Berde", "Astengiye Wedare", "لابردنی بلۆک")
    fun blockUser(l: String)        = t(l, "Kullanıcıyı Engelle",  "Bikarhênerê Asteng bike", "Karber Asteng Ke", "بەکارهێنەر بلۆک بکە")
    fun blockUserConfirm(l: String) = t(l, "Bu kullanıcıyı engellemek istediğine emin misin?", "Tu dixwazî vî bikarhênerî asteng bikî?", "Tı waziyenê nê karberî asteng bikî?", "دڵنیایت لە بلۆک کردنی ئەم بەکارهێنەرە؟")
    fun termsOfUse(l: String)       = t(l, "Kullanım Koşulları",   "Şert û Mercên Bikarhanînê", "Şert û Mercê Karkerdışî", "مەرجەکانی بەکارهێنان")
    fun privacyPolicy(l: String)    = t(l, "Gizlilik Politikası",  "Siyaseta Nepeniyê", "Siyaseta Vaşerdışî", "سیاسەتی تایبەتمەندی")
    fun passwordMismatch(l: String) = t(l, "Şifreler eşleşmiyor", "Şîre li hev nayên", "Şîfre bi ho nêeşkên", "وشەی نهێنیەکان یەک ناگرنەوە")
    fun currentPassword(l: String)  = t(l, "Mevcut Şifre",         "Şîreya Niha", "Şîfreya Nika", "وشەی نهێنی ئێستا")
    fun newPassword(l: String)      = t(l, "Yeni Şifre",           "Şîreya Nû", "Şîfreyê Newe", "وشەی نهێنی نوێ")
    fun selectLang(l: String)       = t(l, "Uygulama dilini seç",  "Zimanê sepanê hilbijêre", "Zıwanê sepanî hilbijêre", "زمانی سەپان هەڵبژێرە")

    // ── Auth (yeni) ───────────────────────────────────────────────────────────
    fun welcome(l: String)          = t(l, "Hoş geldin",           "Xêr hatî", "Xêrame", "بەخێربێیت")

    // ── Bildirim mesajları ────────────────────────────────────────────────────

    // ── Ayarlar - açıklama metinleri ──────────────────────────────────────────
    fun settingsOther(l: String)         = t(l, "Diğer",                            "Yên Din", "Yê Bin", "هی تر")
    fun settingsAbout(l: String)         = t(l, "Heftreng Hakkında",               "Derbarê Heftreng", "Derbarê Heftreng", "دەربارەی Heftreng")
    fun settingsAboutSub(l: String)      = t(l, "Uygulama hakkında bilgi",          "Der barê sepanê de agahî", "Sepanî ser o melumat", "زانیاری دەربارەی سەپان")
    fun rateApp(l: String)               = t(l, "Bizi Değerlendir",                  "Me binirxîne", "Ma Binirxne", "هەڵسەنگاندنمان بکە")
    fun rateAppSub(l: String)            = t(l, "Play Store'da puan ver, yorum yaz",  "Li Play Store dengê xwe bide", "Li Play Store dengê xo bide", "لە Play Store هەڵسەنگاندن و بۆچوون بنووسە")
    fun copyProfileLink(l: String)    = t(l, "Profil Linkini Kopyala",     "Lînka Profîlê Kopî Bike", "Gırêdana Profîlî Kopya Ke", "بەستەری پرۆفایل کۆپی بکە")
    fun copyPostLink(l: String)       = t(l, "Gönderi Linkini Kopyala",     "Lînka Peyamê Kopî Bike", "Gırêdana Nuşteyî Kopya Ke", "بەستەری بڵاوکراوە کۆپی بکە")
    fun copyLessonLink(l: String)     = t(l, "Ders Linkini Kopyala",        "Lînka Dersê Kopî Bike", "Gırêdana Dersî Kopya Ke", "بەستەری وانە کۆپی بکە")
    fun copyGrammarLink(l: String)    = t(l, "Kural Linkini Kopyala",       "Lînka Rêzimanê Kopî Bike", "Gırêdana Rêzımaneyî Kopya Ke", "بەستەری ڕێزمان کۆپی بکە")
    fun linkCopied(l: String)         = t(l, "Link kopyalandı!",            "Lînk hate kopîkirin!", "Gırêdan ame kopyakerdene!", "بەستەرەکە کۆپی کرا!")
    fun shareApp(l: String)              = t(l, "Arkadaşlarına Öner",                 "Ji hevalên xwe re pêşniyar bike", "Embazanê Xo re Pêşniyar Ke", "بۆ هاوڕێکانت پێشنیاری بکە")
    fun shareAppSub(l: String)           = t(l, "Play Store linkini paylaş",           "Lînka Play Store parve bike", "Gırêdana Play Store parveke", "بەستەری Play Store هاوبەش بکە")
    fun shareAppChooser(l: String)       = t(l, "Arkadaşlarınla Paylaş",              "Bi hevalên xwe re parve bike", "Bi Embazanê Xo re Parveke", "لەگەڵ هاوڕێکانتدا هاوبەشی بکە")
    fun shareAppText(l: String)          = t(l,
        "Heft Reng Kurdî: Kürtçeyle kültür, edebiyat ve Kürtçe dil öğrenimi 👇\nhttps://play.google.com/store/apps/details?id=com.heftreng.app",
        "Heft Reng Kurdî: Bi kurdî Çand, wêje û fêrbûna zimani Kurdî 👇\nhttps://play.google.com/store/apps/details?id=com.heftreng.app", "Heft Reng Kurdî: Bi kurdî Çand, wêje û fêrbûna zimani Kurdî 👇\\nhttps://play.google.com/store/apps/details?id=com.heftreng.app", "Heft Reng Kurdî: Bi kurdî Çand, wêje û fêrbûna zimani Kurdî 👇\\nhttps://play.google.com/store/apps/details?id=com.heftreng.app")
    fun settingsTermsSub(l: String)      = t(l, "Kullanım şartlarını görüntüle",    "Peymanname bixwîne", "Şert û mercan bıwane", "مەرجەکان بخوێنەرەوە")
    fun settingsPrivacySub(l: String)    = t(l, "Gizlilik politikasını görüntüle", "Siyaseta nepeniyê bixwîne", "Siyaseta vaşerdışî bıwane", "سیاسەتی تایبەتمەندی بخوێنەرەوە")
    fun settingsAdminPanel(l: String)    = t(l, "Admin Paneli",                     "Panela Admin", "Panela Rêveberiyî", "پانێلی بەڕێوەبەر")
    fun settingsEditSub(l: String)       = t(l, "Profil bilgilerini düzenle",       "Profîla xwe nûve bike", "Zanyariyê profîlê xo nêweke", "زانیارییەکانی پرۆفایلت نوێ بکەرەوە")
    fun settingsPasswordSub(l: String)   = t(l, "Yeni şifre belirle",               "Şîreya nû destnîşan bike", "Şîfreyêko newe destnîşan ke", "وشەی نهێنیەکی نوێ دیاری بکە")
    fun settingsEmailAdd(l: String)      = t(l, "E-posta adresi ekle",              "Email biguherîne", "E-postayê xo bıvurne", "ئیمەیلەکەت بگۆڕە")
    fun settingsPushSub(l: String)       = t(l, "Anlık bildirimleri aç/kapat",     "Agahdariyên push veke/bigire", "Agehdariyê push aker/bıgêre", "ئاگادارکردنەوەکان دابخە/بیکەرەوە")
    fun settingsPrivateSub(l: String)    = t(l, "Sadece takipçiler görebilir",      "Tenê şopîner dikarin bibînin", "Tena şopnayoxî eşkenê bivînî", "تەنها شوێنکەوتووان دەتوانن ببینن")
    fun settingsBlockedSub(l: String)    = t(l, "Engellenen hesapları yönet",       "Bikarhênerên astengkirî birêve bibe", "Karberê astengbiyayeyî birêve bere", "بەکارهێنەرە بلۆککراوەکان بەڕێوە ببە")
    fun settingsNoBlocked(l: String)     = t(l, "Engellenmiş kullanıcı yok.",       "Bikarhênerên astengkirî tune ne.", "Karberê astengbiyayeyî çino yo.", "بەکارهێنەری بلۆککراو نییە.")
    fun settingsAnonymous(l: String)     = t(l, "Kullanıcı",                        "Bikarhêner", "Karber", "بەکارهێنەر")
    fun forgotPassPrompt(l: String)      = t(l, "Şifreni mi unuttun? Mail ile sıfırla →", "Şîreya xwe ji bîr kir? Bi maîlê sifir bike →", "Şîfreya xo ramoşt kerd? Bi e-postayî sifir ke →", "وشەی نهێنیت لەبیر کرد؟ بە ئیمەیل ڕیسێتی بکەرەوە →")
    fun pwRepeat(l: String)              = t(l, "Yeni Şifre (Tekrar)",              "Şîreya Nû (Dubare)", "Şîfreya Newe (Tekrar)", "وشەی نهێنی نوێ (دووبارە)")
    fun errPwBlank(l: String)            = t(l, "Mevcut şifreyi girin",             "Şîreya niha binivîse", "Şîfreya nika binuse", "وشەی نهێنی ئێستا بنووسە")
    fun errPwShort(l: String)            = t(l, "Yeni şifre en az 6 karakter olmalı","Şîreya nû divê herî kêm 6 tîp be", "Şîfreya newe gani leaste kêm 6 herf bo", "وشەی نهێنی نوێ دەبێت لانیکەم ٦ پیت بێت")
    fun emailConfirmSent(l: String)      = t(l, "Doğrulama e-postası gönderildi. Yeni adresinizi onaylayın.", "E-posta piştrastkirinê hate şandin. Navnîşana nû bipejirîne.", "E-postaya piştrastkerdışî ame şandene. Navnîşana xo ya newe bipeyme.", "ئیمەیلی پشتڕاستکردنەوە نێردرا. ناونیشانی نوێت پشتڕاست بکەرەوە.")
    fun currentLabel(l: String)          = t(l, "Mevcut",                           "Heyî", "Heyî", "ئێستا")
    fun newEmailLabel(l: String)         = t(l, "Yeni E-Posta",                     "E-Postaya Nû", "E-Postaya Newe", "ئیمەیلی نوێ")
    fun errInvalidEmail(l: String)       = t(l, "Geçerli bir e-posta girin",        "E-postayek derbasdar binivîse", "E-postayêko rast binuse", "ئیمەیلێکی دروست بنووسە")
    fun errEnterPw(l: String)            = t(l, "Şifrenizi girin",                  "Şîreya xwe binivîse", "Şîfreya xo binuse", "وشەی نهێنیت بنووسە")
    fun sendVerification(l: String)      = t(l, "Doğrulama Gönder",                "Piştrastkirinê Bişîne", "Piştrastkerdışî Bişêne", "پشتڕاستکردنەوە بنێرە")
    fun resetLinkSent(l: String)         = t(l, "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.", "Lînka sifirkirinê hate şandin. E-postaya xwe kontrol bike.", "Gırêdana sifirkerdışê şîfreyî ame şandene. E-postaya xo binêre.", "بەستەری ڕیسێتکردنەوەی وشەی نهێنی نێردرا. ئیمەیلەکەت بپشکنە.")
    fun resetLinkDesc(l: String)         = t(l, "Kayıtlı e-posta adresinize şifre sıfırlama bağlantısı göndereceğiz.", "Em ê lînka sifirkirinê ji bo e-postaya qeydkirî bişînin.", "Ma dê gırêdana sifirkerdışî bişêrime navnîşana to ya e-postayî ya qeydbiyaye.", "بەستەری ڕیسێتکردنەوە بۆ ناونیشانی ئیمەیلی تۆماردەکراو دەنێرین.")

    // ── Feed - PostCard / Dialog metinleri ────────────────────────────────────
    fun showMoreBtn(l: String)           = t(l, "Daha Fazla Göster",          "Zêdetir Nîşan Bide", "Zêde Bide Xuyakerdene", "زیاتر پیشان بدە")
    fun postThinkHint(l: String)         = t(l, "Ne düşünüyorsun?",           "Tu çi difikire?", "Tı çi vindenî?", "چی بیر لێدەکەیتەوە؟")
    fun addQuote(l: String)              = t(l, "Alıntı ekle",                "Jêgirtê zêde bike", "Vateyêk zêde ke", "وتەیەک زیاد بکە")
    fun shareAction(l: String)           = t(l, "Paylaş",                     "Parve bike", "Parveke", "هاوبەشی بکە")
    fun cancelAction(l: String)          = t(l, "İptal",                      "Betal bike", "Bıtal Ke", "پاشگەزبوونەوە")
    fun newPostTitle(l: String)          = t(l, "Yeni Gönderi",               "Nivîsek Nû", "Nuşteyê Newe", "بڵاوکراوەی نوێ")
    fun optionsDesc(l: String)           = t(l, "Seçenekler",                 "Vebijêrk", "Vebijêrgî", "هەڵبژاردنەکان")
    fun editAction(l: String)            = t(l, "Düzenle",                    "Biguherîne", "Vurne", "دەستکاری بکە")
    fun deleteAction(l: String)          = t(l, "Sil",                        "Jê bibe", "Bêbar Ke", "بیسڕەوە")
    fun repostAction(l: String)          = t(l, "Yeniden Paylaş",             "Ji Nû Ve Parve Bike", "Carê Bin Parveke", "دووبارە هاوبەشی بکە")
    fun shareWhatsApp(l: String)         = t(l, "WhatsApp'ta Paylaş",         "Di WhatsApp'ê de Parve Bike", "Di WhatsApp de Parveke", "لە WhatsApp دا هاوبەشی بکە")
    fun shareInstagram(l: String)        = t(l, "Instagram'da Paylaş",        "Di Instagram'ê de Parve Bike", "Di Instagram de Parveke", "لە Instagram دا هاوبەشی بکە")
    fun shareOtherApps(l: String)        = t(l, "Diğer Uygulamalar",          "Sepanên Din", "Sepanê Bini", "سەپانی تر")
    fun postTypeSerial(l: String)        = t(l, "Kitap",                      "Pirtûk", "Kıtav", "کتێب")
    fun postTypeBlog(l: String)          = t(l, "Blog Yazısı",                "Gotara Blogê", "Nuştê Blogî", "بابەتی بلۆگ")
    fun postTypeFeed(l: String)          = t(l, "Paylaşım",                   "Parvekirî", "Parvekerde", "هاوبەش کراوە")
    fun saveAction(l: String)            = t(l, "Kaydet",                     "Tomarkirin", "Qeydke", "پاشەکەوت بکە")
    fun saveDesc(l: String)              = t(l, "Kaydet",                     "Tomarkirin", "Qeydke", "پاشەکەوت بکە")
    fun deletePostTitle(l: String)       = t(l, "Gönderiyi sil?",             "Nivîs jê bibe?", "Nuşte bêbar bo?", "بڵاوکراوەکە بسڕدرێتەوە؟")
    fun deletePostDesc(l: String)        = t(l, "Bu gönderi kalıcı olarak silinecek.", "Ev nivîs dê ji holê rabe.", "No nuşte dê herbıra bêbar bo.", "ئەم بڵاوکراوەیە بۆ هەمیشە دەسڕدرێتەوە.")
    fun editPostTitle(l: String)         = t(l, "Gönderiyi Düzenle",          "Nivîsê Biguherîne", "Nuşteyî Bıvurne", "بڵاوکراوەکە دەستکاری بکە")
    fun deleteCommentTitle(l: String)    = t(l, "Yorumu Sil",                 "Şîrove Jê Bibe", "Şirove Bêbar Ke", "بۆچوونەکە بسڕەوە")
    fun timeNow(l: String)               = t(l, "az önce",                    "niha", "nika", "ئێستا")
    fun timeMin(l: String, n: Int)       = t(l, "${n}dk",                     "${n}d", "${n}deq", "${n}خولەک")
    fun timeHour(l: String, n: Int)      = t(l, "${n}sa",                     "${n}s", "${n}saet", "${n}کاتژمێر")
    fun timeDay(l: String, n: Int)       = t(l, "${n}g",                      "${n}r", "${n}roj", "${n}ڕۆژ")
    fun timeWeek(l: String, n: Int)      = t(l, "${n}hf",                     "${n}hf", "${n}hf", "${n}hf")
    fun timeMon(l: String, n: Int)       = t(l, "${n}ay",                     "${n}m", "${n}aş", "${n}مانگ")
    fun timeYear(l: String, n: Int)      = t(l, "${n}y",                      "${n}s", "${n}serr", "${n}ساڵ")
    fun reportDialogTitle(l: String, name: String) = t(l, "Hesap: $name",     "Hesab: $name", "Hesab: $name", "هەژمار: $name")
    fun reportConfirm(l: String)         = t(l, "Şikayet Et",                 "Rapor bike", "Şikayet Ke", "سکاڵا بکە")

    // ── Messages ──────────────────────────────────────────────────────────────
    fun msgSearchHint(l: String)         = t(l, "Mesajlarda ara...",          "Peyaman bigere...", "Di peyaman de bigêre...", "لە پەیامەکان بگەڕێ...")
    fun msgListTitle(l: String)          = t(l, "Mesajlar",                   "Peyam", "Peyamî", "پەیامەکان")
    fun msgLoading(l: String)            = t(l, "Yükleniyor...",              "Tê barkirin...", "Bar beno...", "بارکردن...")
    fun msgEmpty(l: String)              = t(l, "Henüz mesajın yok",          "Peyam tune", "Peyamê to hêna çino yo", "هێشتا پەیامت نییە")
    fun msgEmptyDesc(l: String)          = t(l, "Yeni bir konuşma başlat",    "Peyamek nû dest pê bike", "Sohbetêko newe bide destpêkerdene", "گفتوگۆیەکی نوێ دەست پێ بکە")
    fun msgDeleteConvTitle(l: String)    = t(l, "Sohbeti Sil",                "Sohbet Sil", "Sohbet Bêbar Ke", "گفتوگۆکە بسڕەوە")
    fun msgDeleteConvDesc(l: String)     = t(l, "Bu sohbeti silmek istiyor musun?", "Ev sohbet bê silîn?", "Tı waziyenê no sohbet bêbar bikî?", "دەتەوێت ئەم گفتوگۆیە بسڕیتەوە؟")
    fun msgUser(l: String)               = t(l, "Kullanıcı",                  "Bikarhêner", "Karber", "بەکارهێنەر")
    fun msgTyping(l: String)             = t(l, "yazıyor...",                 "dinivîse...", "nuseno...", "دەنووسێت...")
    fun msgOnline(l: String)             = t(l, "çevrimiçi",                  "serhêl", "serhêl", "سەرهێڵ")
    fun msgOffline(l: String)            = t(l, "çevrimdışı",                 "nediyar", "nediyar", "دەرهێڵ")
    fun msgGoProfile(l: String)          = t(l, "Profile git",                "Profîl", "Profîlî", "بۆ پرۆفایل")
    fun msgDeleteConv(l: String)         = t(l, "Sohbeti sil",                "Sohbetê jê bibe", "Sohbetî bêbar ke", "گفتوگۆکە بسڕەوە")
    fun msgSaving(l: String)             = t(l, "Kayıt yapılıyor",            "Tê tomarkirin", "Qeyd beno", "پاشەکەوت دەکرێت")
    fun msgVoice(l: String)              = t(l, "Sesli mesaj",                "Dengbêjiya dengî", "Peyamê Dengî", "پەیامی دەنگی")
    fun msgYou(l: String)                = t(l, "Sen",                        "Tu", "Tı", "تۆ")
    fun msgEditTitle(l: String)          = t(l, "Mesajı düzenle",             "Peyamê biguherîne", "Peyamî bıvurne", "پەیامەکە دەستکاری بکە")
    fun msgHint(l: String)               = t(l, "Mesaj yaz...",               "Peyamê binivîse...", "Peyam binuse...", "پەیام بنووسە...")
    fun msgEmptyConv(l: String)          = t(l, "Henüz mesaj yok, konuşmayı başlat!", "Peyam tune, dest bi axaftinê bike!", "Peyam hêna çino yo, sohbet bide destpêkerdene!", "هێشتا پەیام نییە، گفتوگۆ دەست پێ بکە!")
    fun msgReply(l: String)              = t(l, "Yanıtla",                    "Bersiv bide", "Bersiv bide", "وەڵام بدەرەوە")
    fun msgEdit(l: String)               = t(l, "Düzenle",                    "Biguherîne", "Vurne", "دەستکاری بکە")
    fun msgDelete(l: String)             = t(l, "Sil",                        "Jê bibe", "Bêbar Ke", "بیسڕەوە")
    fun msgLike(l: String)               = t(l, "Beğen",                      "Hez bike", "Hes bike", "حەز لێبکە")
    fun msgDeleted(l: String)            = t(l, "Bu mesaj silindi",           "Peyam hat jêbirin", "No peyam bêbar bi", "ئەم پەیامە سڕایەوە")
    fun msgEdited(l: String)             = t(l, "(düzenlendi)",               "(guherî)", "(vurriştbiyo)", "(دەستکاریکرا)")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun authCreateAccount(l: String)     = t(l, "Hesap oluştur",              "Hesabek nû çêke", "Hesabêko newe vırazê", "هەژمارێکی نوێ دروست بکە")
    fun authWelcome(l: String)           = t(l, "Hoş geldin",                 "Xêr hatî", "Xêrame", "بەخێربێیت")
    fun authGoogleContinue(l: String)    = t(l, "Google ile devam et",        "Bi Google re berdewam bike", "Bi Google Domede", "بە Google بەردەوامبە")
    fun authOr(l: String)                = t(l, "  ya da  ",                  "  an jî  ", "  ya zî  ", "  یان  ")
    fun authNameLabel(l: String)         = t(l, "Adın",                       "Navê te", "Namê To", "ناوت")
    fun authPasswordLabel(l: String)     = t(l, "Şifre",                      "Şîfre", "Şîfre", "وشەی نهێنی")
    fun authForgotPw(l: String)          = t(l, "Şifremi unuttum",            "Şîfreya xwe ji bîr kir", "Şîfreya xo ramoşt kerd", "وشەی نهێنیم لەبیر کرد")
    fun authRegister(l: String)          = t(l, "Kayıt ol",                   "Qeyd bibe", "Qeydbe", "تۆمار بکە")
    fun authLogin(l: String)             = t(l, "Giriş yap",                  "Têkeve", "Kewe", "بچۆ ژوورەوە")
    fun authHaveAccount(l: String)       = t(l, "Zaten hesabın var mı? Giriş yap", "Hesabê te heye? Têkeve", "Hesabê to esto? Kewe", "هەژمارت هەیە؟ بچۆ ژوورەوە")
    fun authNoAccount(l: String)         = t(l, "Hesabın yok mu? Kayıt ol",   "Hesabê te tune? Qeyd bibe", "Hesabê to çino? Qeydbe", "هەژمارت نییە؟ تۆمار بکە")
    fun authForgotTitle(l: String)       = t(l, "Şifremi Unuttum",            "Şîreya Xwe Ji Bîr Kir", "Şîfreya Xo Ramoşt Kerd", "وشەی نهێنیم لەبیر کرد")
    fun authResetSent(l: String)         = t(l, "Şifre sıfırlama bağlantısı gönderildi. E-posta kutunuzu kontrol edin.", "Lînka sifirkirina şîfreyê hate şandin. E-postaya xwe kontrol bike.", "Gırêdana sifirkerdışê şîfreyî ame şandene. E-postaya xo binêre.", "بەستەری ڕیسێتکردنەوەی وشەی نهێنی نێردرا. ئیمەیلەکەت بپشکنە.")
    fun authResetDesc(l: String)         = t(l, "E-posta adresinizi girin, sıfırlama bağlantısı gönderelim.", "E-postaya qeydkirî binivîse, em lînka sifirkirinê bişînin.", "Navnîşana xo ya e-postayî binuse, ma dê gırêdana sifirkerdışî bişêrime.", "ناونیشانی ئیمەیلت بنووسە، بەستەری ڕیسێتکردنەوەت بۆ دەنێرین.")

    // ── Kitaplar / Seriler ────────────────────────────────────────────────────
    fun booksTitle(l: String)            = t(l, "Kitaplar",                   "Pirtûk", "Kıtavî", "کتێبەکان")
    fun booksEmpty(l: String)            = t(l, "Henüz kitap yok",            "Pirtûk tune", "Kıtav hêna çino yo", "هێشتا کتێب نییە")
    fun bookAddBtn(l: String)            = t(l, "Kitap Ekle",                 "Pirtûk Zêde Bike", "Kıtav Zêde Ke", "کتێب زیاد بکە")

    // ── Kütüphane Ekranı ─────────────────────────────────────────────────────
    fun libraryTitle(l: String)          = t(l, "Kütüphane",                  "Pirtûkxane", "Pirtûkxane", "پەڕتووکخانە")
    fun discoverTitle(l: String)         = t(l, "Keşfet",                     "Vedîtin", "Bıvin", "دۆزینەوە")
    fun libraryTabQuotes(l: String)      = t(l, "Alıntılar",                  "Jêgirt", "Vateyî", "وتەکان")
    fun libraryTabReviews(l: String)     = t(l, "İncelemeler",                "Nirxandin", "Nirxandışî", "هەڵسەنگاندنەکان")
    fun libraryTabAuthors(l: String)     = t(l, "Yazarlar",                   "Nivîskar", "Nuştekarî", "نووسەران")
    fun libraryTabBooks(l: String)       = t(l, "Kitaplar",                   "Pirtûk", "Kıtavî", "کتێبەکان")
    fun libraryAddQuote(l: String)       = t(l, "Alıntı Ekle",                "Jêgirtê Zêde Bike", "Vateyî Zêde Ke", "وتە زیاد بکە")
    fun libraryAddReview(l: String)      = t(l, "İnceleme Ekle",              "Nirxandinê Zêde Bike", "Nirxandış Zêde Ke", "هەڵسەنگاندن زیاد بکە")
    fun libraryQuoteHint(l: String)      = t(l, "Alıntı metnini girin...",    "Nivîsa jêgirtê binivîse...", "Nuşta vateyî binuse...", "دەقی وتەکە بنووسە...")
    fun libraryReviewHint(l: String)     = t(l, "İncelemenizi yazın...",      "Nirxandina xwe binivîse...", "Nirxandışê xo binuse...", "هەڵسەنگاندنەکەت بنووسە...")
    fun libraryNoQuotes(l: String)       = t(l, "Henüz alıntı yok",           "Hîn jêgirt tune", "Vate hêna çino yo", "هێشتا وتە نییە")
    fun libraryNoReviews(l: String)      = t(l, "Henüz inceleme yok",         "Nirxandin tune", "Nirxandış hêna çino yo", "هێشتا هەڵسەنگاندن نییە")
    fun libraryNoAuthors(l: String)      = t(l, "Henüz yazar yok",            "Nivîskar tune", "Nuştekar hêna çino yo", "هێشتا نووسەر نییە")
    fun libraryNoBooks(l: String)        = t(l, "Henüz kitap yok",            "Pirtûk tune", "Kıtav hêna çino yo", "هێشتا کتێب نییە")

    // ── Alıntı Ekle Ekranı (QuoteDialog) ─────────────────────────────────────
    fun quoteDialogTitle(l: String)      = t(l, "Alıntı Ekle",                "Jêgirtê Zêde Bike", "Vateyî Zêde Ke", "وتە زیاد بکە")
    fun quoteTextLabel(l: String)        = t(l, "ALINTI METNİ *",             "NIVÎSA JÊGIRTÊ *", "NUŞTA VATEYÎ *", "دەقی وتەکە *")
    fun bookNameLabel(l: String)         = t(l, "KİTAP ADI",                  "NAVÊ PIRTÛKÊ", "NAMÊ KITAVÎ", "ناوی کتێب")
    fun authorNameLabel(l: String)       = t(l, "YAZAR",                      "NIVÎSKAR", "NUŞTEKAR", "نووسەر")
    fun quoteCountSuffix(l: String, n: Int) = t(l, "$n alıntı",               "$n jêgirt", "$n vate", "$n وتە")
    fun bookNotFoundWillAdd(l: String, name: String) =
        t(l, "\"$name\" sistemde yok — yeni kitap olarak eklenecek",
             "\"$name\" di pergalê de tune — wê wekî pirtûkeke nû were zêdekirin")
    fun authorNotFoundWillAdd(l: String, name: String) =
        t(l, "\"$name\" sistemde yok — yeni yazar olarak eklenecek",
             "\"$name\" di pergalê de tune — wê wekî nivîskarekî nû were zêdekirin")
    fun quotePreview(l: String)          = t(l, "Önizleme",                   "Pêşdîtin", "Pêşdîtış", "پێشبینین")
    fun quoteShowMore(l: String)         = t(l, "Kapat ▲",                    "Bigire ▲", "Bigêre ▲", "دایبخە ▲")
    fun quoteReadMore(l: String)         = t(l, "Devamını oku ▼",             "Zêdetir bixwîne ▼", "Zêde bıwane ▼", "زیاتر بخوێنەرەوە ▼")
    fun editQuoteTitle(l: String)        = t(l, "Alıntıyı Düzenle",           "Jêgirtê Biguhêre", "Vateyî Bıvurne", "وتەکە دەستکاری بکە")
    fun deleteQuoteTitle(l: String)      = t(l, "Alıntıyı Sil",               "Jêgirtê Jê Bibe", "Vateyî Bêbar Ke", "وتەکە بسڕەوە")
    fun deleteQuoteConfirm(l: String)    = t(l, "Bu alıntıyı silmek istiyor musunuz?", "Tu dixwazî vê jêgirtê jê bibî?", "Tı waziyenê nê vateyî bêbar bikî?", "دەتەوێت ئەم وتەیە بسڕیتەوە؟")
    fun libraryQuoteBook(l: String)      = t(l, "Kitap adı",                  "Navê pirtûkê", "Namê kıtavî", "ناوی کتێب")
    fun libraryQuoteAuthor(l: String)    = t(l, "Yazar adı",                  "Navê nivîskar", "Namê nuştekar", "ناوی نووسەر")
    fun libraryReviewBook(l: String)     = t(l, "Kitap seçin",                "Pirtûkê hilbijêre", "Kıtavî hilbıjêre", "کتێب هەڵبژێرە")
    fun libraryReviewTitle(l: String)    = t(l, "İnceleme başlığı",           "Sernavê nirxandinê", "Sernuşta nirxandışî", "سەردێڕی هەڵسەنگاندن")
    fun libraryReviewRating(l: String)   = t(l, "Puan",                       "Pûan", "Puan", "خاڵ")
    fun bookChaptersTitle(l: String, n: Int) = t(l, "Bölümler ($n)",          "Beş ($n)", "Beşî ($n)", "بەشەکان ($n)")
    fun bookChaptersEmpty(l: String)     = t(l, "Henüz bölüm eklenmemiş",    "Beş tune", "Beş hêna nêameyo zêdekerdene", "هێشتا بەش زیاد نەکراوە")
    fun bookNewTitle(l: String)          = t(l, "Yeni Kitap",                 "Pirtûk Nû", "Kıtavê Newe", "کتێبی نوێ")
    fun bookNewNameLabel(l: String)      = t(l, "Kitap Adı *",                "Sernavê Pirtûkê *", "Namê Kıtavî *", "ناوی کتێب *")
    fun bookDescLabel(l: String)         = t(l, "Açıklama",                   "Danasîn", "Şirovekerdış", "پێناسە")
    fun bookGenreLabel(l: String)        = t(l, "Tür",                        "Cûre", "Cure", "جۆر")
    fun bookCreateBtn(l: String)         = t(l, "Oluştur",                    "Çêke", "Vırazê", "دروستی بکە")
    fun prevChapter(l: String)           = t(l, "Önceki",                     "Berî", "Verên", "پێشوو")
    fun nextChapter(l: String)           = t(l, "Sonraki",                    "Paşê", "Peyên", "دواتر")
    fun wordCount(l: String, n: Any = "")    = if (n.toString().isBlank()) t(l, "kelime", "peyv") else t(l, "$n kelime", "$n peyv")
    fun readingStatus(l: String, key: String) = when (key) {
        "okuyorum"         -> t(l, "Okuyorum",        "Dixwînim")
        "okumak_istiyorum" -> t(l, "Okumak İstiyorum","Dixwazim Bixwînim")
        "okudum"           -> t(l, "Okudum",          "Xwendim")
        "biraktim"         -> t(l, "Bıraktım",        "Berda")
        else               -> key
    }
    fun readingListEmpty(l: String)      = t(l, "Bu listede kitap yok",       "Di vê lîsteyê de pirtûk tune", "Di nê lîsteyî de kıtav çino yo", "لەم لیستەدا کتێب نییە")
    fun addToReadingList(l: String)      = t(l, "Okuma Listesine Ekle",       "Li Lîsteya Xwendinê Zêde Bike", "Lîsteya Wendışî Zêde Ke", "بۆ لیستی خوێندنەوە زیاد بکە")

    // ── Bildirimler (NotificationsScreen) ────────────────────────────────────
    fun notifTitle(l: String)            = t(l, "Bildirimler",                "Agahdarî", "Agehdarî", "ئاگادارکردنەوەکان")
    fun notifUnread(l: String, n: Int)   = t(l, "$n okunmamış",              "$n nexwendî", "$n newendî", "$n نەخوێندراوە")
    fun notifBack(l: String)             = t(l, "Geri",                       "Vegere", "Vêr", "گەڕانەوە")
    fun notifMarkAll(l: String)          = t(l, "Tümünü oku",                 "Hemû bixwîne", "Gırey bıwane", "هەمووی وەک خوێندراو دیاری بکە")
    fun notifEmpty(l: String)            = t(l, "Henüz bildirim yok",         "Agahdarî tune", "Agehdarî hêna çino yo", "هێشتا ئاگادارکردنەوە نییە")
    fun notifEmptyDesc(l: String)        = t(l, "Yeni bildirimler burada görünecek", "Agahdariyên nû dê li vir xuya bikin", "Agehdariyê newey ê vêre eyaneyê", "ئاگادارکردنەوە نوێیەکان لێرە دەردەکەون")

    // ── PostDetail ────────────────────────────────────────────────────────────
    fun likesCount(l: String, n: Int)    = t(l, "$n beğeni",                  "$n xweşandin", "$n hesbiyayîş", "$n حەزلێکراو")
    fun replyingTo(l: String, name: String) = t(l, "@$name yanıtlanıyor",     "@$name bersiv dide", "@$name re bersiv dano", "وەڵامی @$name دەداتەوە")
    fun replyHint(l: String, name: String)  = t(l, "@$name yanıtla...",       "@$name bersiv bide...", "@$name bersiv bide...", "وەڵامی @$name بدەرەوە...")
    fun replyAction(l: String)           = t(l, "Yanıtla",                    "Bersiv bide", "Bersiv bide", "وەڵام بدەرەوە")
    fun replyingToSuffix(l: String)      = t(l, "yanıtlanıyor",               "bersiv dide", "bersiv dano", "وەڵام دەداتەوە")
    fun deleteFailed(l: String)          = t(l, "Silinemedi",                 "Nehat jêbirin", "Nêameyo bêbarkerdene", "نەسڕایەوە")
    fun editCommentTitle(l: String)      = t(l, "Yorumu Düzenle",             "Şîroveyê Biguherîne", "Şiroveyî Bıvurne", "بۆچوونەکە دەستکاری بکە")
    fun editCommentHint(l: String)       = t(l, "Yorumunu düzenle...",        "Şîroveya xwe biguherîne...", "Şiroveya xo bıvurne...", "بۆچوونەکەت دەستکاری بکە...")
    fun editedLabel(l: String)           = t(l, "düzenlendi",                 "guherî", "vurriştbiyo", "دەستکاریکرا")

    // ── LinkifyText ───────────────────────────────────────────────────────────
    fun showLess(l: String)              = t(l, "Daha Az Göster",             "Kêmtir Nîşan Bide", "Kêmtir Bide Xuyakerdene", "کەمتر پیشان بدە")

    // ── Auth ──────────────────────────────────────────────────────────────────
    fun continueWithGoogle(l: String)    = t(l, "Google ile devam et",        "Bi Google re berdewam bike", "Bi Google Domede", "بە Google بەردەوامبە")
    fun googleRecommended(l: String)     = t(l, "Önerilen",                   "Pêşniyarkirî", "Pêşniyarkerde", "پێشنیارکراو")
    fun emailNotVerifiedTitle(l: String)  = t(l, "E-posta doğrulanmamış", "E-name nehat pejirandin", "E-posta nêameya piştrastkerdene", "ئیمەیل پشتڕاست نەکراوەتەوە")
    fun emailNotVerifiedBody(l: String)   = t(l, "Gelen kutunu ve spam klasörünü kontrol et. Doğrulama maili tekrar gönderildi.", "Qutiya giriyan û qutiya spamê binêre. E-nameya piştrastkirinê ji nû ve hate şandin.", "Qutîya girewtışan û qutîya spamî binêre. E-postaya piştrastkerdışî carê bin ame şandene.", "سندوقی وەرگرتن و سپامەکەت بپشکنە. ئیمەیلی پشتڕاستکردنەوە دووبارە نێردرایەوە.")
    fun emailNotVerifiedGoogle(l: String) = t(l, "Aynı e-posta ile Google ile giriş yaparak da hesabını doğrulayabilirsin.", "Bi heman e-nameyê bi Google re têkevin da ku hesabê xwe bipejirînin.", "Tı eşkenê bi hem e-postayî bi Google kewê û hesabê xo bıpeyme.", "دەتوانیت بەهەمان ئیمەیل بە Google بچیتە ژوورەوە و هەژمارەکەت پشتڕاست بکەیتەوە.")
    fun googleWarning(l: String)         = t(l, "Google ile giriş yapmanızı öneririz. E-posta ile kayıt olursanız, e-posta doğrulaması gerekebilir.", "Em pêşniyar dikin ku bi Google têkevin. Ger bi e-nameyê qeyd bikin, dibe ku pejirandina e-nameyê pêwist be.", "Ma pêşniyar kenê ke tı bi Google kewê. Ege tı bi e-postayî qeyd bibî, dibe ke piştrastkerdışê e-postayî lazim bo.", "پێشنیارت پێدەکەین بە Google بچیتە ژوورەوە. ئەگەر بە ئیمەیل تۆمار بیت، پێویستە ئیمەیلەکەت پشتڕاست بکەیتەوە.")
    fun orDivider(l: String)             = t(l, "ya da",                      "an jî", "ya zî", "یان")
    fun yourName(l: String)              = t(l, "Adın",                       "Navê te", "Namê To", "ناوت")

    // ── PostDetailScreen ──────────────────────────────────────────────────────
    fun postNotFound(l: String)          = t(l, "Gönderi bulunamadı",         "Nivîs nehat dîtin", "Nuşte nêame dîtene", "بڵاوکراوە نەدۆزرایەوە")

    // ── KurdiScreen ───────────────────────────────────────────────────────────
    fun levelLabel(l: String, n: Int)    = t(l, "Seviye $n",                  "Asta $n", "Ast $n", "ئاستی $n")
    fun lessonNotFound(l: String)        = t(l, "Ders bulunamadı",            "Ders nehat dîtin", "Ders nêame dîtene", "وانە نەدۆزرایەوە")
    fun dailyGoal(l: String)             = t(l, "Günlük Hedef",               "Armanca Rojane", "Armancê Rojane", "ئامانجی ڕۆژانە")
    fun dailyGoalDesc(l: String)         = t(l, "Her gün pratik yap",         "Her roj pratîk bike", "Her roj pratîk bike", "هەموو ڕۆژێک ڕاهێنان بکە")
    fun comingSoon(l: String)            = t(l, "Yakında",                    "Zû tê", "Zûde Yeno", "بەم زووانە")
    fun topicHintLabel(l: String)        = t(l, "Konu",                       "Mijar", "Mijar", "بابەت")

    // ── MessagesScreens ───────────────────────────────────────────────────────
    fun searchMessages(l: String)        = t(l, "Mesajlarda ara...",          "Peyaman bigere...", "Di peyaman de bigêre...", "لە پەیامەکان بگەڕێ...")
    fun newConversation(l: String)       = t(l, "Yeni Konuşma",               "Axaftinek Nû", "Sohbetê Newe", "گفتوگۆی نوێ")
    fun deleteConvConfirm(l: String)     = t(l, "Sohbeti silmek istiyor musun?", "Dixwazî vê sohbetê jê bibî?", "Tı waziyenê nê sohbetî bêbar bikî?", "دەتەوێت ئەم گفتوگۆیە بسڕیتەوە؟")
    fun noComments(l: String)            = t(l, "Henüz yorum yok",            "Hîn şîrove tune", "Şirove hêna çino yo", "هێشتا بۆچوون نییە")
    fun post(l: String)                  = t(l, "Gönderi",                    "Nivîs", "Nuşte", "بڵاوکراوە")
    fun followSomeone(l: String)         = t(l, "Takip ettiğin kimse gönderi paylaşmadı", "Kesek ku şopandî nivîs par nekir", "Kesê ke tı şopneno nuşte parve nêkerd", "کەسانی شوێنکەوتووت هیچ بڵاوکراوەیەکیان نەکردووە")

    // ── Profil: Okuma Özeti Hero ─────────────────────────────────────────────
    fun profileBooksRead(l: String, n: Int)   = t(l, "$n kitap okudum",  "$n pirtûk min xwendin", "$n kıtavî ez wend", "$n کتێبم خوێندووەتەوە")
    fun profileQuotesCount(l: String, n: Int) = t(l, "$n alıntı",        "$n jêgirt", "$n vate", "$n وتە")
    fun profileStreakDays(l: String, n: Int)  = t(l, "$n gün streak",    "$n roj streak", "$n roj domdar", "$n ڕۆژی بەردەوام")

    // ── Feed: Arkadaşlar ne okuyor? şeridi ───────────────────────────────────
    fun friendsReadingTitle(l: String)        = t(l, "Arkadaşların ne okuyor?", "Hevalên te çi dixwînin?", "Embazê to çi wanenê?", "هاوڕێکانت چی دەخوێننەوە؟")
    fun friendsReadingPage(l: String, n: Int) = t(l, "$n. sayfada",             "rûpel $n", "pele $n de", "لاپەڕە $n")

    // ── Tema Seçici ───────────────────────────────────────────────────────────
    fun themeTitle(l: String)            = t(l, "Tema",               "Reng", "Reng", "ڕووکار")
    fun themeDarkMode(l: String)         = t(l, "Koyu Mod",           "Moda Tarî", "Moda Tarî", "دۆخی تاریک")
    fun themeDarkModeToLight(l: String)  = t(l, "Açık moda geç",     "Biçe moda ronî", "Şêre moda rondiyî", "بچۆ بۆ دۆخی ڕووناک")
    fun themeDarkModeToDark(l: String)   = t(l, "Koyu moda geç",     "Biçe moda tarî", "Şêre moda tarî", "بچۆ بۆ دۆخی تاریک")
    fun themeCharcoal(l: String)         = t(l, "Kömür Mürekkebi",   "Mûrekkeba Komirê", "Mırekebê Komırî", "مۆرەکەبی زەغاڵ")
    fun themeBook(l: String)             = t(l, "Kitap",              "Pirtûk", "Kıtav", "کتێب")
    fun themeForest(l: String)           = t(l, "Orman",              "Daristan", "Newal", "دارستان")
    fun themeOcean(l: String)            = t(l, "Okyanus",            "Okyanûs", "Okyanûs", "ئۆقیانووس")
    fun themeSunset(l: String)           = t(l, "Gün Batımı",         "Rojavabûn", "Roavabiyayîş", "ڕۆژئاوابوون")
    fun themeMonochrome(l: String)       = t(l, "Tek Renk",           "Yek Reng", "Yew Reng", "یەک ڕەنگ")
    fun textColorTitle(l: String)        = t(l, "Yazı Rengi",         "Rengê Nivîsê", "Rengê Nuştışî", "ڕەنگی نووسین")
    fun textColorDefault(l: String)      = t(l, "Tema Varsayılanı",   "Xwerû ya Temayê", "Xoser a Temayî", "بنەڕەتی ڕووکار")

    // ── Profil Ekranı — 2026-08 eklenen fonksiyonlar ──────────────────────────
    fun profileAccountPrivate(l: String) = t(l, "Bu hesap gizli", "Ev hesab veşartî ye", "No hesab vaşerde yo", "ئەم هەژمارە تایبەتە")
    fun profileBooksAndSeries(l: String) = t(l, "Kitaplar & Seriler", "Pirtûk & Rêze", "Kıtav & Rêzey", "کتێبەکان و زنجیرەکان")
    fun profileTitle(l: String) = t(l, "Profil", "Profîl", "Profîl", "پرۆفایل")
    fun profileNotFound(l: String) = t(l, "Bu hesap mevcut değil", "Ev hesab tune", "No hesab çino", "ئەم هەژمارە بوونی نییە")
    fun profileMaybeDeleted(l: String) = t(l, "Silinmiş veya askıya alınmış olabilir.", "Dibe ku hatibe rakirin.", "Bêguman ame bêbarkerdene ya zî verastbiyo.", "لەوانەیە سڕدرابێت یان ڕاگیرابێت.")
    fun profileGoBack(l: String) = t(l, "Geri Dön", "Vegere", "Vêr Şêre", "بگەڕێوە")
    fun profilePrivateTitle(l: String) = t(l, "Bu hesap gizli", "Ev hesab taybet e", "No hesab taybet o", "ئەم هەژمارە تایبەتە")
    fun profilePrivateDesc(l: String) = t(l, "Bu hesabın gönderi ve fotoğraflarını görmek için takip etmeniz gerekiyor.", "Ji bo dîtina barkirin û nivîsên vê hesabê, pêwîste hûn bişopînin.", "Seba nuşte û resmê nê hesabî bivînî, gani şıma şopnê.", "بۆ بینینی بڵاوکراوە و وێنەکانی ئەم هەژمارە دەبێت شوێنی بکەویت.")
    fun profileLoadMore(l: String) = t(l, "Daha Fazla Yükle", "Zêdetir bar bike", "Zêde Bar Ke", "زیاتر باربکە")
    fun profileNoBooksSeries(l: String) = t(l, "Henüz kitap veya seri yok", "Hîn pirtûk/rêze tune", "Hêna kıtav/rêze çino", "هێشتا کتێب یان زنجیرە نییە")
    fun profileAddNew(l: String) = t(l, "+ Yeni Ekle", "+ Nû Zêde Bike", "+ Newe Zêde Ke", "+ نوێ زیاد بکە")
    fun profileSeries(l: String) = t(l, "Seriler", "Rêze", "Rêzey", "زنجیرەکان")
    fun profileBooks(l: String) = t(l, "Kitaplar", "Pirtûk", "Kıtavî", "کتێبەکان")
    fun profileReadingListEmpty(l: String) = t(l, "Okuma listesi boş", "Lîsteya xwendinê vala ye", "Lîsteya wendışî vora ya", "لیستی خوێندنەوە بەتاڵە")
    fun profileMsgBlockedTitle(l: String) = t(l, "Mesaj Gönderilemez", "Peyam nayê şandin", "Peyam Nêeşkeno Bêrusnayene", "پەیام نانێردرێت")
    fun profileMsgBlockedDesc(l: String) = t(l, "Bu kullanıcı mesajları kısıtlamış.", "Ev bikarhêner tenê ji şopînerên xwe peyam qebûl dike.", "No karber tena şopnayoxanê xo ra peyam qebul keno.", "ئەم بەکارهێنەرە تەنها لە شوێنکەوتووانی خۆی پەیام وەردەگرێت.")
    fun profileReadBooksTitle(l: String) = t(l, "Okunan Kitaplar (\${n})", "Pirtûkên xwendî (\${n})", "Kıtavê Wendaye (\${n})", "کتێبە خوێندراوەکان (\${n})")
    fun profileNoReadBooks(l: String) = t(l, "Henüz okunan kitap yok", "Hîn pirtûk tune", "Hêna kıtavê wendaye çino", "هێشتا کتێبی خوێندراو نییە")
    fun profileMyQuotesTitle(l: String) = t(l, "Alıntılarım (\${n})", "Jêgirtên min (\${n})", "Vateyê Mı (\${n})", "وتەکانم (\${n})")
    fun profileNoQuotesYet(l: String) = t(l, "Henüz alıntı yok", "Hîn gotin tune", "Hêna vate çino", "هێشتا وتە نییە")
    fun profileStatBooksRead(l: String) = t(l, "kitap okudum", "pirtûk xwendin", "kıtav wendî", "کتێبم خوێندووەتەوە")
    fun profileStatQuotes(l: String) = t(l, "alıntı", "jêgirt", "vate", "وتە")
    fun profileStatStreak(l: String) = t(l, "gün streak", "roj streak", "roj domdar", "ڕۆژی بەردەوام")
    fun profilePhotoUpdated(l: String) = t(l, "Profil fotoğrafı güncellendi ✓", "Wêneya profîlê hate nûkirin ✓", "Resmê profîlî ame newekerdene ✓", "وێنەی پرۆفایل نوێکرایەوە ✓")
    fun profilePhotoError(l: String) = t(l, "Hata: \$msg", "Çewtî: \$msg", "Xeta: \$msg", "هەڵە: \$msg")
    fun profileCoverUpdated(l: String) = t(l, "Kapak fotoğrafı güncellendi ✓", "Wêneya bergê hate nûkirin ✓", "Resmê bergî ame newekerdene ✓", "وێنەی بەرگ نوێکرایەوە ✓")
}
