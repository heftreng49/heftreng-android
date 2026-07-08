# Mesajlaşma İyileştirme Planı

Amaç: mesajlaşma deneyimini Instagram/WhatsApp seviyesine yaklaştırmak.
Sırayla, adım adım gidiyoruz — her adım kendi commit'i ile tamamlanacak.

Durum lejantı: ⬜ Yapılmadı · 🔧 Devam ediyor · ✅ Tamamlandı

---

## Adım 1 — Bildirimden Doğrudan Yanıt (Quick Reply) ⬜

**Sorun:** Uygulama arka plandayken gelen mesaj bildirimine dokunmadan,
bildirim üzerinden direkt yazıp yanıt gönderilemiyor. Instagram/WhatsApp'ta
olduğu gibi bildirim genişleyip "Yanıtla" kutusu çıkmıyor.

**Mevcut durum:** `HeftrangMessagingService.kt` bildirimi `NotificationCompat`
ile oluşturuyor ama `RemoteInput` / `PendingIntent` aksiyonu hiç tanımlı değil.
Yani mimari sıfırdan eklenecek, üzerine inşa edilecek bir "yarım" özellik yok.

**Değişecek dosyalar:**
- `app/src/main/java/com/heftreng/app/utils/HeftrangMessagingService.kt`
  (bildirime `RemoteInput` aksiyonu ekle)
- **Yeni dosya:** `app/src/main/java/com/heftreng/app/utils/ReplyReceiver.kt`
  (bir `BroadcastReceiver` — bildirimden gelen metni alıp Firestore'a yazacak)
- `app/src/main/AndroidManifest.xml` (yeni receiver'ı kaydet)

**Yapılacaklar:**
1. `ReplyReceiver` oluştur: `BroadcastReceiver`, `RemoteInput.getResultsFromIntent()`
   ile metni oku, `conversationId` ve `otherUid`'i intent extra'sından al.
2. Firestore'a mesajı yaz (mevcut `sendMessage` mantığının sadeleştirilmiş,
   ViewModel'siz bir versiyonu — receiver'da Hilt inject kolay olmadığından
   doğrudan Firestore instance kullanılacak).
3. `HeftrangMessagingService`'te bildirim oluşturulurken:
   - `RemoteInput.Builder("key_reply_text")` ile giriş alanı tanımla.
   - `PendingIntent` ile `ReplyReceiver`'a bağlı bir `NotificationCompat.Action` ekle.
   - `addAction(replyAction)` ile bildirime ekle.
4. Yanıt gönderildikten sonra `NotificationManagerCompat.notify()` ile
   bildirimi "gönderildi" durumuna güncelle (WhatsApp'taki gibi anlık geri bildirim).
5. Test: uygulamayı arka plana al, başka bir hesaptan mesaj gönder,
   bildirimi aşağı kaydırıp yanıt kutusundan yazıp gönder, karşı tarafta
   mesajın gerçekten ulaştığını doğrula.

**Riskler / dikkat noktaları:**
- Bildirim yanıtı gönderilirken kullanıcı oturumu (`FirebaseAuth.currentUser`)
  `BroadcastReceiver` içinde senkron erişilebilir olmalı — `Application`
  context üzerinden `FirebaseAuth.getInstance()` kullanılacak.
- Aynı anda birden fazla konuşmadan bildirim geldiyse her bildirimin kendi
  `conversationId`'sini doğru taşıdığından emin olunmalı (intent extra çakışması).

---

## Adım 2 — Mesaj Kopyalama ⬜

**Sorun:** Mesaja uzun basınca çıkan menüde (Reply/Edit/Delete/Like)
"Kopyala" seçeneği yok.

**Mevcut durum:** `MessagesScreens.kt` içindeki context menüsü (`ctxMsg` state'i,
`MsgCtxItem` composable'ı) zaten var ve genişletilebilir yapıda.

**Değişecek dosyalar:**
- `app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt`

**Yapılacaklar:**
1. `ClipboardManager`'ı `LocalClipboardManager.current` ile al.
2. Context menüsüne yeni bir `MsgCtxItem(Icons.Default.ContentCopy, "Kopyala", false)`
   ekle → tıklanınca `clipboardManager.setText(AnnotatedString(msg.text))`.
3. Kopyalama sonrası küçük bir `Toast` veya `Snackbar` ile "Kopyalandı" geri bildirimi.
4. Resim/ses mesajlarında bu seçenek gizlenmeli (sadece `msg.text.isNotBlank()` ise göster).

**Riskler / dikkat noktaları:**
- Yok — düşük riskli, tek dosyalık, state gerektirmeyen bir değişiklik.

---

## Adım 3 — Mesaj Beğeni Özelliğinin Düzeltilmesi ⬜

**Sorun:** Kalp/beğen butonuna basılınca hiçbir şey olmuyormuş gibi görünüyor
(UI güncellenmiyor), oysa arka planda Firestore'a yazılıyor.

**Kök sebep (doğrulandı):**
- `toggleLike()` (`MessagesViewModel.kt`) beğeniyi `convMessages/{convId}/msgs/{msgId}/likes/{uid}`
  alt-koleksiyonuna yazıyor — bu doğru bir tasarım (mesaj dokümanının 1MB
  limitini şişirmemek için bilinçli olarak seçilmiş).
- **Ama** `Message` veri modelinde (`Models.kt`) `isLikedByMe` / `likesCount`
  alanı hiç yok, `toMessage()` bu alt-koleksiyonu hiç okumuyor, `toggleLike()`
  de `_messages` state'ini hiç güncellemiyor.
- Sonuç: beğeni Firestore'a yazılıyor ama ekrana **hiçbir zaman** yansımıyor —
  buton görsel olarak hep aynı kalıyor.

**Değişecek dosyalar:**
- `app/src/main/java/com/heftreng/app/data/model/Models.kt`
  (`Message`'a `isLikedByMe: Boolean` ve `likesCount: Int` eklenecek)
- `app/src/main/java/com/heftreng/app/viewmodel/MessagesViewModel.kt`
  (`toMessage()`, `toggleLike()`, `listenMessages()`)
- `app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt`
  (kalp ikonunun dolu/boş rengi `msg.isLikedByMe`'ye bağlanacak)

**Yapılacaklar:**
1. `Message`'a iki alan ekle: `isLikedByMe: Boolean = false`, `likesCount: Int = 0`.
2. `toggleLike()` içinde Firestore'a yazma işleminden **hemen sonra**,
   optimistic UI güncellemesi yap:
   ```kotlin
   _messages.value = _messages.value.map {
       if (it.id == msg.id) it.copy(
           isLikedByMe = !it.isLikedByMe,
           likesCount  = it.likesCount + if (!it.isLikedByMe) 1 else -1
       ) else it
   }
   ```
   (Firestore yazması başarısız olursa `catch` bloğunda bu değişikliği geri al.)
3. Mesajlar ilk yüklenirken (`listenMessages`) her mesaj için `likes` alt-koleksiyonunu
   tek tek sorgulamak pahalı olur — bunun yerine sadece **görünen sayfadaki**
   mesajlar için toplu bir `likes` sorgusu (feed'deki `syncPostCounts` mantığına
   benzer şekilde) düşünülecek. İlk sürümde optimistic-only (adım 2) yeterli;
   çoklu cihazdan senkron gösterim ikinci fazda ele alınabilir.
4. `MsgRow` composable'ında kalp ikonunu `if (msg.isLikedByMe) Icons.Filled.Favorite
   else Icons.Filled.FavoriteBorder` ve rengini kırmızı/gri olarak `msg.isLikedByMe`'ye
   bağla; varsa `likesCount` sayısını ikonun yanında göster.

**Riskler / dikkat noktaları:**
- Optimistic update ile gerçek Firestore durumu arasında geçici tutarsızlık
  olabilir (örn. iki cihazdan aynı anda beğenme) — düşük öncelikli, kabul edilebilir.
- `likesCount`'un çoklu kullanıcıdan doğru toplanması için ileride alt-koleksiyon
  sayımı (Cloud Function ile `likesCount` alanını senkron tutma) düşünülebilir.

---

## Adım 4 — Mesajlarda Mention (@kullanıcı) ⬜

**Sorun:** Feed/yorumlarda `@kullanıcı` etiketleme (mention) çalışıyor ama
birebir mesajlarda bu özellik hiç yok.

**Mevcut durum:** Mention altyapısı zaten `FeedViewModel` ve ilgili
composable'larda (`MentionSuggestionBar`, `MentionText`, `searchMentionUsers`)
var ve çalışıyor — mesajlar ekranına taşınması/uyarlanması gerekiyor.

**Değişecek dosyalar:**
- `app/src/main/java/com/heftreng/app/viewmodel/MessagesViewModel.kt`
  (mention arama fonksiyonlarını `FeedViewModel`'den uyarlayarak ekle,
  ya da ortak bir yardımcı sınıfa çıkar — kod tekrarını önlemek için tercih edilen bu)
- `app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt`
  (mesaj yazma kutusuna `MentionSuggestionBar` ekle, gönderilen metne
  mention'ları işlemek için `MentionText` ile render et)
- `app/src/main/java/com/heftreng/app/data/model/Models.kt`
  (`Message`'a `mentions: List<String> = emptyList()` alanı eklenecek — feed'deki
  `Comment.mentions` ile aynı desen)

**Yapılacaklar:**
1. Mention arama mantığını ortak bir yere çıkar (öneri: `MentionRepository`
   veya `MentionHelper` — hem `FeedViewModel` hem `MessagesViewModel` bunu kullanır).
   Bu adım kod tekrarını önler, ileride üçüncü bir ekranda mention gerekirse
   yeniden yazmaya gerek kalmaz.
2. `MessagesViewModel`'e `mentionSuggestions` StateFlow'u ve `searchMentionUsers(query)`
   /`clearMentionSuggestions()` fonksiyonlarını ekle (feed'deki birebir mantık).
3. Mesaj yazma ekranında input metni değiştikçe `@` tetikleyicisini yakala
   (`SinglePostScreen`'deki `LaunchedEffect(inputText)` deseni birebir uygulanabilir).
4. Mesaj gönderilirken seçilen mention uid'lerini `Message.mentions` alanına yaz.
5. Mesaj balonunda metni `MentionText` composable'ı ile render et — mention'a
   tıklanınca ilgili kullanıcının profiline git.
6. (İsteğe bağlı, ileri faz) Mention edilen kullanıcıya bildirim gönder —
   feed'deki mention bildirimi mantığı varsa oradan örnek alınabilir.

**Riskler / dikkat noktaları:**
- Birebir mesajlaşmada mention'ın pratik faydası grup mesajlaşması olmadan
  sınırlı olabilir (konuşma zaten iki kişi arasında) — yine de tutarlılık ve
  gelecekteki grup sohbeti ihtimali için eklenmesi mantıklı.
- Ortak `MentionHelper`'a çıkarma işlemi `FeedViewModel`'i de dokunacağından,
  bu adımda feed'in mention davranışını bozmadığından emin olunmalı (regresyon testi).

---

## Genel Test Kontrol Listesi (her adım sonrası)

- [ ] Uygulama derleniyor mu (`./gradlew assembleDebug`)
- [ ] Değişen ekran/ViewModel için ilgili akış manuel test edildi mi
- [ ] Var olan diğer özellikler (reply, edit, delete) bozulmadı mı
- [ ] Git commit mesajı `fix(messages): ...` veya `feat(messages): ...` formatında mı

---

## Termux Komut Taslağı (her adım için ortak şablon)

Her adımda ben güncellenmiş dosyaları içeren bir zip vereceğim
(`heftreng-mesaj-adimX.zip` gibi isimlendirilecek). Sen `Download` klasörüne
indirdikten sonra aşağıdaki şablonu kullan — sadece zip adını ve
`git add` yolunu, o adımda değişen dosyalarla değiştir:

```bash
cd ~/heftreng-android && \
cp /sdcard/Download/ZIP_ADI.zip . && \
mkdir -p ~/tmpx && \
unzip -o ZIP_ADI.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx ZIP_ADI.zip && \
git add DEĞİŞEN_DOSYA_YOLU_1 DEĞİŞEN_DOSYA_YOLU_2 && \
git commit -m "AÇIKLAYICI_COMMIT_MESAJI" && \
git push
```

### Adım 1 için örnek (bildirimden yanıt):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-mesaj-adim1.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-mesaj-adim1.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-mesaj-adim1.zip && \
git add app/src/main/java/com/heftreng/app/utils/HeftrangMessagingService.kt \
        app/src/main/java/com/heftreng/app/utils/ReplyReceiver.kt \
        app/src/main/AndroidManifest.xml && \
git commit -m "feat(messages): bildirimden doğrudan yanıt yazma (quick reply)" && \
git push
```

### Adım 2 için örnek (kopyalama):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-mesaj-adim2.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-mesaj-adim2.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-mesaj-adim2.zip && \
git add app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt && \
git commit -m "feat(messages): mesaj kopyalama özelliği eklendi" && \
git push
```

### Adım 3 için örnek (beğeni düzeltmesi):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-mesaj-adim3.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-mesaj-adim3.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-mesaj-adim3.zip && \
git add app/src/main/java/com/heftreng/app/data/model/Models.kt \
        app/src/main/java/com/heftreng/app/viewmodel/MessagesViewModel.kt \
        app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt && \
git commit -m "fix(messages): mesaj beğeni özelliği UI'a yansıtılmıyordu, düzeltildi" && \
git push
```

### Adım 4 için örnek (mention):
```bash
cd ~/heftreng-android && \
cp /sdcard/Download/heftreng-mesaj-adim4.zip . && \
mkdir -p ~/tmpx && \
unzip -o heftreng-mesaj-adim4.zip -d ~/tmpx && \
cp -r ~/tmpx/heftreng-android-main/. . && \
rm -rf ~/tmpx heftreng-mesaj-adim4.zip && \
git add app/src/main/java/com/heftreng/app/viewmodel/MessagesViewModel.kt \
        app/src/main/java/com/heftreng/app/ui/screens/messages/MessagesScreens.kt \
        app/src/main/java/com/heftreng/app/data/model/Models.kt && \
git commit -m "feat(messages): mesajlarda @mention desteği eklendi" && \
git push
```

---

## Sıradaki Adım

👉 **Adım 1 — Bildirimden Doğrudan Yanıt** ile başlıyoruz.
Hazır olduğunda "başla" de, kodu yazıp zip'i hazırlayayım.
