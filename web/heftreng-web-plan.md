# Heftreng Web — Mimari Plan ve Yol Haritası

> **Konum:** `web/heftreng-web-plan.md` (web build tetikler, Android tetiklemez)
> **Durum:** Faz 3 tamamlandı ✅. Sırada Faz 4.

---

## Sürüm Geçmişi

| Sürüm | Açıklama |
|---|---|
| v25 | Faz 1: compose.service, auth.service, QuoteCard — yanlış zip yapısı |
| v26 | v25 zip yol düzeltmesi |
| v26b | MD dosyası zipten çıkarıldı |
| v27 | Faz 2: post.service, profile.service, UserChip/Modal/LikersModal/InfiniteScroll |
| v28 | Bugfix: çift `</script>` kapanış etiketi düzeltildi |
| v29 | Faz 3: layout + login + register → auth.service.ts entegrasyonu, eski store shim |
| v30 | Bugfix: login/register çift script, feed/Modal eski a11y formatı, QuoteCard isRtl |
| v31 | Profil düzelt: closeEditModal eksikti → gönderi düzenleme çalışmıyor; btn-quote-share kaldırıldı (Android'de yok); compose alıntı ekranı Android stili; lib/store/auth.ts shim silindi; Navbar → stores/auth |

---

## 1. Mevcut Durum (v31 itibarıyla)

| Dosya | Durum | Not |
|---|---|---|
| `routes/+layout.svelte` | ✅ | `initAuthListener()` kullanıyor |
| `routes/feed/+page.svelte` | ✅ | |
| `routes/compose/+page.svelte` | ✅ | Alıntı ekranı Android stili |
| `routes/post/[id]/+page.svelte` | ✅ | |
| `routes/profile/[uid]/+page.svelte` | ✅ | closeEditModal eklendi, btn-quote-share kaldırıldı |
| `routes/login/+page.svelte` | ✅ | |
| `routes/register/+page.svelte` | ✅ | |
| `lib/store/auth.ts` | 🗑️ **SİLİNDİ** | Shim gereksizdi, tüm importlar stores/auth'a taşındı |
| `lib/store/theme.ts` | ✅ | Aktif — layout kullanıyor |
| `lib/stores/auth.ts` | ✅ | Tek gerçek auth store |
| `lib/components/Navbar.svelte` | ✅ | stores/auth kullanıyor |

---

## 2. Mimari Harita

```
web/src/lib/
├── models/              ✅ Tüm interface'ler tamam
│
├── services/
│   ├── auth.service.ts          ✅ Faz 1
│   ├── compose.service.ts       ✅ Faz 1
│   ├── feed.service.ts          ✅ Faz 1
│   ├── social.service.ts        ✅ Faz 1
│   ├── comment.service.ts       ✅ Faz 1
│   ├── profile.service.ts       ✅ Faz 1+2
│   ├── post.service.ts          ✅ Faz 2
│   ├── notification.service.ts  ⏳ Faz 4
│   ├── message.service.ts       ⏳ Faz 4
│   └── library.service.ts       ⏳ Faz 4
│
├── stores/
│   ├── auth.ts          ✅ currentUser + userProfile + isLoggedIn
│   ├── feed.store.ts    ✅
│   ├── profile.store.ts ✅
│   └── ui.store.ts      ✅
│
├── store/ (kısmen — shim silindi)
│   └── theme.ts  → aktif, layout kullanıyor
│
├── components/
│   ├── Avatar.svelte        ✅
│   ├── Skeleton.svelte      ✅
│   ├── LikeButton.svelte    ✅
│   ├── PostCard.svelte      ✅
│   ├── CommentPanel.svelte  ✅
│   ├── QuoteCard.svelte     ✅ isRtl $derived düzeltildi
│   ├── UserChip.svelte      ✅
│   ├── InfiniteScroll.svelte ✅
│   ├── Modal.svelte         ✅ a11y ignore formatı düzeltildi
│   ├── LikersModal.svelte   ✅
│   └── Navbar.svelte        ✅ stores/auth'a taşındı
│
├── firebase/ ✅
└── supabase/ ✅

web/src/routes/
├── +layout.svelte               ✅ Faz 3
├── feed/+page.svelte            ✅
├── compose/+page.svelte         ✅ Android stili alıntı ekranı
├── post/[id]/+page.svelte       ✅
├── profile/[uid]/+page.svelte   ✅ Gönderi düzenleme düzeltildi
├── login/+page.svelte           ✅
├── register/+page.svelte        ✅
├── notifications/               ⏳ Faz 4
├── messages/                    ⏳ Faz 4
├── library/                     ⏳ Faz 4
└── kurdi/                       ⏳ Faz 4
```

---

## 3. Temel Kural: Nereye Ne Gider?

| Katman | Ne içerir | Ne içermez |
|---|---|---|
| **models/** | TypeScript interface'ler | Fonksiyon, iş mantığı |
| **services/** | Firestore/Supabase sorguları | UI state, Svelte store |
| **stores/** | Reactive state | Doğrudan DB sorgusu |
| **components/** | Tekrar kullanılan bileşen | Sayfa-özel mantık |
| **routes/\*/+page.svelte** | Store bağlama + component dizimi | Doğrudan DB sorgusu |

---

## 4. Yol Haritası

### Faz 1 ✅ (v26)
- [x] `lib/models/` + tüm `lib/services/` temeli + `lib/stores/` + core bileşenler
- [x] `routes/feed` + `routes/compose` refactor

### Faz 2 ✅ (v27 → v28 bugfix)
- [x] UserChip, InfiniteScroll, Modal, LikersModal bileşenleri
- [x] `lib/services/post.service.ts`
- [x] `routes/post/[id]` + `routes/profile/[uid]` refactor

### Faz 3 ✅ (v29 → v30 → v31 bugfix)
- [x] `routes/+layout.svelte` → `initAuthListener()` entegrasyonu
- [x] `routes/login` + `routes/register` → `auth.service.ts`
- [x] `lib/store/auth.ts` shim silindi
- [x] Navbar + tüm importlar `stores/auth`'a taşındı
- [x] Çeşitli build hataları giderildi (çift script, a11y format, isRtl)
- [x] Profil: closeEditModal eksikliği düzeltildi, btn-quote-share kaldırıldı
- [x] Compose: alıntı ekranı Android stili (karanlık zemin, büyük outlined inputlar)

### Faz 4 — Mesajlar, Bildirimler & Kütüphane ← Sıradaki
- [ ] `lib/services/notification.service.ts`
- [ ] `routes/notifications/+page.svelte`
- [ ] `lib/services/message.service.ts`
- [ ] `routes/messages/+page.svelte` (konuşma listesi + mesaj ekranı)
- [ ] `lib/services/library.service.ts`
- [ ] `routes/library/+page.svelte`

### Faz 5 — Kurdî & Admin
- [ ] `routes/kurdi/+page.svelte`
- [ ] `routes/admin/+page.svelte`
- [ ] `lib/models/cms.ts`

---

## 5. Servis API Özeti

### auth.service.ts
```typescript
initAuthListener()        → unsubscribe fn
signIn(email, pw)         → UserCredential
register(email, pw, name) → UserCredential + Firestore user doc
signOut()                 → void
```

### compose.service.ts
```typescript
loadPost(id)            → Partial<Post> | null
uploadImage(file, uid)  → string (URL)
createPost(payload)     → string (id)
createQuote(payload)    → string (id)
updatePost(id, fields)  → void
```

### post.service.ts
```typescript
fetchPostDetail(postId, uid?)      → PostDetail
deletePost(postId)                 → void
fetchComments(postId, uid?)        → Comment[]
togglePostLike / togglePostSave    → void
addComment / editComment / deleteComment → Comment / void
toggleCommentLike                  → void
fetchPostLikers / fetchCommentLikers → Liker[]
```

### profile.service.ts
```typescript
fetchUser / fetchSocialCounts / checkFollowStatus
followUser / unfollowUser / sendFollowRequest / cancelFollowRequest
fetchFollowers / fetchFollowing
fetchUserPosts / fetchMoreUserPosts
fetchReadingList / fetchLibraryBooks / addLibraryBook
saveProfileEdit / checkUsernameAvailable / uploadProfilePhoto
shareQuoteFromProfile
```

---

## 6. Component API

```svelte
<InfiniteScroll hasMore={bool} loading={bool} onLoadMore={fn} />
<Modal bind:open title="..." maxWidth="480px" onclose={fn}> ... </Modal>
<LikersModal bind:open={bool} {likers} {loading} onclose={fn} />
<UserChip uid="..." name="..." photoURL="..." subtitle="..." />
<QuoteCard quoteText="..." bookName="..." authorName="..." coverImg="..." expanded={false} />
```

---

## 7. Bilinen Silinecek / Temizlenecek
- `lib/store/auth.ts` → **SİLİNDİ** v31'de
- `lib/store/theme.ts` → Faz 5'te layout refactor edilince silinecek
