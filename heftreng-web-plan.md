# Heftreng Web — Mimari Plan ve Yol Haritası

> **Durum:** Faz 1 tamamlandı. Faz 2 devam ediyor.

---

## 1. Mevcut Durum Analizi

### Web (SvelteKit — `web/src/`)

| Dosya | Satır | Sorun |
|---|---|---|
| `routes/feed/+page.svelte` | ~80 | ✅ Refactor edildi |
| `routes/post/[id]/+page.svelte` | ~45 000 bytes | Faz 2 hedefi |
| `routes/compose/+page.svelte` | ~686 | ✅ Servis katmanı entegre edildi |
| `routes/profile/[uid]/+page.svelte` | ~94 000 bytes | Faz 2 hedefi |
| `lib/stores/auth.ts` | ✅ | Yeni — `userProfile` + `isLoggedIn` eklendi |
| `lib/components/Navbar.svelte` | — | Faz 2 hedefi |
| **Tip tanımları** | ✅ | `lib/models/` tamam |

### Android (referans — iyi yapı)

```
data/model/Models.kt          ← Tüm data class'lar merkezi
data/model/SupabaseDtos.kt    ← Supabase satır tipleri
data/repository/              ← Veri katmanı (Firestore + Supabase)
ui/screens/{feature}/         ← Sadece UI
ui/component/                 ← Paylaşılan Compose bileşenleri
di/                           ← Bağımlılık enjeksiyonu
```

---

## 2. Hedef Mimari

Android MVVM'yi SvelteKit'e birebir çeviriyoruz:

```
web/src/lib/
├── models/              ← Android Models.kt + SupabaseDtos.kt karşılığı
│   ├── user.ts
│   ├── post.ts
│   ├── comment.ts
│   ├── notification.ts
│   ├── message.ts
│   ├── library.ts       (Book, Author, BookQuote, BookReview)
│   ├── serial.ts        (Serial, Chapter, BookChapter)
│   ├── cms.ts           (CmsPage, CmsCategory, AppConfig)
│   └── index.ts         (re-export)
│
├── services/            ← Android Repository karşılığı
│   ├── feed.service.ts          ✅
│   ├── social.service.ts        ✅
│   ├── comment.service.ts       ✅
│   ├── profile.service.ts       ✅
│   ├── compose.service.ts       ✅ YENİ — createPost/createQuote/updatePost/loadPost/uploadImage
│   ├── auth.service.ts          ✅ YENİ — initAuthListener/signIn/register/signOut
│   ├── notification.service.ts  Faz 3
│   ├── message.service.ts       Faz 3
│   └── library.service.ts       Faz 4
│
├── stores/              ← Android ViewModel state karşılığı
│   ├── auth.ts          ✅ — currentUser + userProfile + isLoggedIn
│   ├── feed.store.ts    ✅
│   ├── profile.store.ts ✅
│   └── ui.store.ts      ✅
│
├── components/          ← Android ui/component/ karşılığı
│   ├── PostCard.svelte    ✅
│   ├── QuoteCard.svelte   ✅ YENİ — Android QuoteCompose.kt birebir karşılığı
│   ├── Avatar.svelte      ✅
│   ├── CommentPanel.svelte ✅
│   ├── LikeButton.svelte  ✅
│   ├── Skeleton.svelte    ✅
│   ├── UserChip.svelte    Faz 2
│   ├── InfiniteScroll.svelte Faz 2
│   ├── Modal.svelte       Faz 2
│   └── LikersModal.svelte Faz 2
│
├── firebase/
│   └── config.ts        ✅
│
└── supabase/
    └── config.ts        ✅

web/src/routes/
├── +layout.svelte       ✅ (auth guard + nav)
├── +page.svelte         ✅ (redirect to /feed)
├── feed/
│   └── +page.svelte     ✅ refactor tamam
├── post/[id]/
│   └── +page.svelte     Faz 2 hedefi
├── profile/[uid]/
│   └── +page.svelte     Faz 2 hedefi
├── compose/
│   └── +page.svelte     ✅ compose.service.ts ile entegre, $lib/stores/auth kullanıyor
├── login/
└── register/
```

---

## 3. Temel Kural: Nereye Ne Gider?

| Katman | Ne içerir | Ne içermez |
|---|---|---|
| **models/** | TypeScript interface'ler, tip sabitleri | Fonksiyon, iş mantığı |
| **services/** | Firestore/Supabase sorguları, CRUD | UI state, Svelte store |
| **stores/** | Reactive state (`writable`, `derived`) | Doğrudan DB sorgusu |
| **components/** | Tekrar kullanılan Svelte bileşeni | Sayfa-özel mantık |
| **routes/\*/+page.svelte** | Store bağlama + component dizimi | Doğrudan DB sorgusu, uzun mantık |

**Altın kural:** Bir `+page.svelte` içinde `supabase.from(...)` veya `getDocs(...)` görüyorsan, bu kod `services/` katmanına taşınmalı.

---

## 4. Tip Sistemi — Android → TypeScript Çevirisi

### `@get:Exclude @set:Exclude` → `clientState` ayrımı

Android'de Firestore'a yazılmayan alanlar `@Exclude` ile işaretlendi.
TypeScript'te bunları açıkça optional yapıyoruz:

```typescript
// Firestore'dan gelenler — zorunlu
interface Post {
  id: string
  uid: string
  likesCount: number
  // ...

  // Client-side state — Android'deki @Exclude karşılığı
  isLikedByMe?: boolean
  isSavedByMe?: boolean
  isRepostedByMe?: boolean
  myRepostId?: string
}
```

### Timestamp → number

Android'de `com.google.firebase.Timestamp`. Web'de Firestore SDK'dan
`{ seconds: number, nanoseconds: number }` gelir. Utility fonksiyon ile çözüyoruz:

```typescript
// lib/models/util.ts
export function tsToMs(ts: any): number {
  if (!ts) return 0
  if (ts?.seconds) return ts.seconds * 1000
  return Number(ts)
}
```

---

## 5. Yol Haritası

### Faz 1 — Temel ✅ Tamamlandı

- [x] `lib/models/` klasörü ve tüm interface'ler
- [x] `lib/services/feed.service.ts`
- [x] `lib/services/social.service.ts`
- [x] `lib/services/comment.service.ts`
- [x] `lib/services/profile.service.ts`
- [x] `lib/services/auth.service.ts` ← **YENİ** (initAuthListener, signIn, register, signOut)
- [x] `lib/services/compose.service.ts` ← **YENİ** (loadPost, createPost, createQuote, updatePost, uploadImage)
- [x] `lib/stores/auth.ts` (userProfile + isLoggedIn genişletildi)
- [x] `lib/stores/feed.store.ts`
- [x] `lib/stores/profile.store.ts`
- [x] `lib/stores/ui.store.ts`
- [x] `lib/components/PostCard.svelte`
- [x] `lib/components/Avatar.svelte`
- [x] `lib/components/Skeleton.svelte`
- [x] `lib/components/LikeButton.svelte`
- [x] `lib/components/CommentPanel.svelte`
- [x] `lib/components/QuoteCard.svelte` ← **YENİ** (Android QuoteCompose.kt birebir karşılığı)
- [x] `routes/feed/+page.svelte` refactor (~80 satıra indirildi)
- [x] `routes/compose/+page.svelte` → compose.service.ts kullanıyor, `$lib/stores/auth` import yolu düzeltildi

### Faz 2 — Profil & Post Detayı
- [ ] `lib/components/UserChip.svelte`
- [ ] `lib/components/InfiniteScroll.svelte`
- [ ] `lib/components/Modal.svelte`
- [ ] `lib/components/LikersModal.svelte`
- [ ] `profile/[uid]/+page.svelte` refactor (~80 satıra indir)
- [ ] `post/[id]/+page.svelte` refactor (~60 satıra indir)
- [ ] `+layout.svelte` → `auth.service.ts#initAuthListener()` kullanacak şekilde güncelle

### Faz 3 — Mesajlar & Bildirimler
- [ ] `lib/services/message.service.ts`
- [ ] `lib/services/notification.service.ts`
- [ ] `routes/messages/` (henüz yok)
- [ ] `routes/notifications/` (henüz yok)

### Faz 4 — Kütüphane & Kurdi
- [ ] `lib/services/library.service.ts`
- [ ] `lib/models/library.ts`
- [ ] `routes/library/`
- [ ] `routes/kurdi/`

### Faz 5 — Admin & CMS
- [ ] `routes/admin/` (Android AdminScreen karşılığı)
- [ ] `lib/models/cms.ts`

---

## 6. Servis Katmanı Kuralları

### compose.service.ts
```typescript
loadPost(id)           → Firestore'dan gönderi yükle
uploadImage(file, uid) → Storage'a resim yükle, URL döndür
createPost(payload)    → Yeni normal gönderi oluştur
createQuote(payload)   → Yeni alıntı gönderisi (Android QuoteDialog karşılığı)
updatePost(id, fields) → Mevcut gönderiyi düzenle
```

### auth.service.ts
```typescript
initAuthListener() → onAuthStateChanged dinleyicisi başlat, store'ları güncelle
signIn(email, pw)  → Firebase email/şifre girişi
register(email, pw, name) → Yeni kullanıcı + Firestore profili
signOut()          → Firebase çıkışı
```

---

## 7. Component API Tasarımı

### `PostCard.svelte`
```svelte
<PostCard
  post={post}
  currentUid={$currentUser?.uid}
  on:like={handleLike}
  on:save={handleSave}
  on:comment={openComments}
  on:delete={handleDelete}
  on:edit={openEditModal}
/>
```

### `QuoteCard.svelte` (Android QuoteCompose.kt karşılığı)
```svelte
<QuoteCard
  quoteText={post.quoteText}
  bookName={post.bookName}
  authorName={post.authorName}
  coverImg={post.coverImg}
  language="tr"
  expanded={false}
  onTapBook={(name) => goto(`/library?book=${name}`)}
  onTapAuthor={(name) => goto(`/library?author=${name}`)}
/>
```

### `CommentPanel.svelte`
```svelte
<CommentPanel
  postId={commentPostId}
  currentUser={$currentUser}
  on:close={() => commentPostId = null}
/>
```

### `Avatar.svelte`
```svelte
<Avatar src={user.photoURL} name={user.displayName} size={36} />
```

---

## 8. Dosya Oluşturma Sırası (Bağımlılık Sırasına Göre)

```
1.  lib/models/util.ts           ✅
2.  lib/models/user.ts           ✅
3.  lib/models/post.ts           ✅
4.  lib/models/comment.ts        ✅
5.  lib/models/notification.ts   ✅
6.  lib/models/message.ts        ✅
7.  lib/models/library.ts        ✅
8.  lib/models/serial.ts         ✅
9.  lib/models/cms.ts            ✅
10. lib/models/index.ts          ✅
11. lib/stores/auth.ts           ✅ (genişletildi)
12. lib/stores/feed.store.ts     ✅
13. lib/stores/profile.store.ts  ✅
14. lib/stores/ui.store.ts       ✅
15. lib/services/auth.service.ts     ✅ YENİ
16. lib/services/feed.service.ts     ✅
17. lib/services/social.service.ts   ✅
18. lib/services/comment.service.ts  ✅
19. lib/services/profile.service.ts  ✅
20. lib/services/compose.service.ts  ✅ YENİ
21. lib/components/Avatar.svelte     ✅
22. lib/components/Skeleton.svelte   ✅
23. lib/components/LikeButton.svelte ✅
24. lib/components/QuoteCard.svelte  ✅ YENİ
25. lib/components/PostCard.svelte   ✅
26. lib/components/CommentPanel.svelte ✅
27. routes/feed/+page.svelte    ✅ (refactor tamam)
28. routes/compose/+page.svelte ✅ (servis entegre, store yolu düzeltildi)
29. routes/post/[id]/+page.svelte    ← Faz 2
30. routes/profile/[uid]/+page.svelte ← Faz 2
```
