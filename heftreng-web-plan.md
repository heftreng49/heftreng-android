# Heftreng Web — Mimari Plan ve Yol Haritası

> **Durum:** Faz 2 tamamlandı. Faz 3 devam ediyor.

---

## 1. Mevcut Durum Analizi

| Dosya | Durum |
|---|---|
| `routes/feed/+page.svelte` | ✅ Refactor edildi |
| `routes/compose/+page.svelte` | ✅ compose.service.ts ile entegre |
| `routes/post/[id]/+page.svelte` | ✅ post.service.ts ile entegre |
| `routes/profile/[uid]/+page.svelte` | ✅ profile.service.ts ile entegre |
| `lib/stores/auth.ts` | ✅ userProfile + isLoggedIn |
| `lib/components/Navbar.svelte` | Faz 3 hedefi |

---

## 2. Hedef Mimari

```
web/src/lib/
├── models/              ✅ Tüm interface'ler tamam
│
├── services/
│   ├── feed.service.ts          ✅
│   ├── social.service.ts        ✅
│   ├── comment.service.ts       ✅
│   ├── profile.service.ts       ✅ Faz 2'de genişletildi
│   ├── post.service.ts          ✅ YENİ (Faz 2)
│   ├── compose.service.ts       ✅
│   ├── auth.service.ts          ✅
│   ├── notification.service.ts  Faz 3
│   ├── message.service.ts       Faz 3
│   └── library.service.ts       Faz 4
│
├── stores/
│   ├── auth.ts          ✅
│   ├── feed.store.ts    ✅
│   ├── profile.store.ts ✅
│   └── ui.store.ts      ✅
│
├── components/
│   ├── PostCard.svelte      ✅
│   ├── QuoteCard.svelte     ✅
│   ├── Avatar.svelte        ✅
│   ├── CommentPanel.svelte  ✅
│   ├── LikeButton.svelte    ✅
│   ├── Skeleton.svelte      ✅
│   ├── UserChip.svelte      ✅ YENİ (Faz 2)
│   ├── InfiniteScroll.svelte ✅ YENİ (Faz 2) — Intersection Observer
│   ├── Modal.svelte         ✅ YENİ (Faz 2) — genel amaçlı dialog
│   ├── LikersModal.svelte   ✅ YENİ (Faz 2) — beğenenler listesi
│   └── Navbar.svelte        Faz 3
│
├── firebase/ ✅
└── supabase/ ✅

web/src/routes/
├── feed/+page.svelte            ✅
├── compose/+page.svelte         ✅
├── post/[id]/+page.svelte       ✅ Faz 2'de refactor
├── profile/[uid]/+page.svelte   ✅ Faz 2'de refactor
├── login/                       Faz 3
└── register/                    Faz 3
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

**Altın kural:** `+page.svelte` içinde `supabase.from(...)` veya `getDocs(...)` görüyorsan → `services/` katmanına taşı.

---

## 4. Yol Haritası

### Faz 1 — Temel ✅ Tamamlandı
- [x] `lib/models/` — tüm interface'ler
- [x] `lib/services/feed.service.ts`
- [x] `lib/services/social.service.ts`
- [x] `lib/services/comment.service.ts`
- [x] `lib/services/profile.service.ts`
- [x] `lib/services/auth.service.ts`
- [x] `lib/services/compose.service.ts`
- [x] `lib/stores/` — auth, feed, profile, ui
- [x] `lib/components/` — PostCard, Avatar, Skeleton, LikeButton, CommentPanel, QuoteCard
- [x] `routes/feed/+page.svelte` refactor
- [x] `routes/compose/+page.svelte` refactor

### Faz 2 — Profil & Post Detayı ✅ Tamamlandı
- [x] `lib/components/UserChip.svelte` ← YENİ
- [x] `lib/components/InfiniteScroll.svelte` ← YENİ (Intersection Observer)
- [x] `lib/components/Modal.svelte` ← YENİ (genel dialog)
- [x] `lib/components/LikersModal.svelte` ← YENİ (Android LikersBottomSheet karşılığı)
- [x] `lib/services/post.service.ts` ← YENİ (fetchPost, toggleLike, toggleSave, yorum CRUD, likers)
- [x] `lib/services/profile.service.ts` genişletildi (toggleFollow, follow request, posts, enrich, upload)
- [x] `routes/post/[id]/+page.svelte` refactor (post.service.ts kullanıyor)
- [x] `routes/profile/[uid]/+page.svelte` refactor (profile.service.ts kullanıyor)

### Faz 3 — Kimlik & Navigasyon
- [ ] `lib/components/Navbar.svelte`
- [ ] `routes/login/+page.svelte` refactor (auth.service.ts kullanacak)
- [ ] `routes/register/+page.svelte` refactor
- [ ] `routes/+layout.svelte` → `auth.service.ts#initAuthListener()` ile güncelle
- [ ] `lib/services/notification.service.ts`
- [ ] `lib/services/message.service.ts`

### Faz 4 — Kütüphane & Kurdî
- [ ] `lib/services/library.service.ts`
- [ ] `lib/models/library.ts`
- [ ] `routes/library/`
- [ ] `routes/kurdi/`

### Faz 5 — Admin & CMS
- [ ] `routes/admin/`
- [ ] `lib/models/cms.ts`

---

## 5. Servis API Özeti

### post.service.ts (Faz 2 — YENİ)
```typescript
fetchPost(postId, uid?)           → post + likesCount + isLikedByMe + isSavedByMe
deletePost(postId)                → void
fetchComments(postId, uid?)       → Comment[] (isLikedByMe ile zenginleştirilmiş)
togglePostLike(postId, uid, ...)  → void
togglePostSave(postId, uid, ...)  → void
addComment(postId, uid, ...)      → Comment
editComment(commentId, text)      → Comment
deleteComment(commentId)          → void
toggleCommentLike(cmtId, uid, ...) → void
fetchPostLikers(postId)           → {uid, name, photo_url, created_at}[]
fetchCommentLikers(commentId)     → {uid, name, photo_url, created_at}[]
```

### profile.service.ts (Faz 1+2)
```typescript
fetchProfile(uid)                → User | null
fetchSocialCounts(uid)           → {followers, following, posts}
checkFollowStatus(from, target, isPrivate) → {isFollowing, followRequestStatus}
toggleFollow(from, target, ...)  → void
sendFollowRequest(from, target)  → void
cancelFollowRequest(from, target) → void
fetchFollowers(uid)              → {uid, name, photo}[]
fetchFollowing(uid)              → {uid, name, photo}[]
fetchUserPosts(uid, lastDoc?)    → {posts, lastDoc, hasMore}
enrichPostsWithInteractions(ids, uid?) → {likeCounts, likedIds, savedIds}
fetchReadingList(uid)            → Record<status, item[]>
updateProfile(uid, data)         → void
checkUsernameAvailable(username, excludeUid) → boolean
syncUsernameToSupabase(uid, ...) → void
uploadAvatar(uid, file)          → string (URL)
uploadCoverPhoto(uid, file)      → string (URL)
```

### compose.service.ts (Faz 1)
```typescript
loadPost(id)            → Partial<Post> | null
uploadImage(file, uid)  → string (URL)
createPost(payload)     → string (id)
createQuote(payload)    → string (id)
updatePost(id, fields)  → void
```

### auth.service.ts (Faz 1)
```typescript
initAuthListener()       → unsubscribe fn
signIn(email, pw)        → UserCredential
register(email, pw, name) → UserCredential
signOut()                → void
```

---

## 6. Component API

### `InfiniteScroll.svelte` (Faz 2)
```svelte
<InfiniteScroll
  hasMore={hasMorePosts}
  loading={postsLoading}
  onLoadMore={loadMorePosts}
  threshold={200}
/>
```

### `Modal.svelte` (Faz 2)
```svelte
<Modal bind:open title="Başlık" maxWidth="480px" onclose={handleClose}>
  <!-- içerik buraya -->
</Modal>
```

### `LikersModal.svelte` (Faz 2)
```svelte
<LikersModal
  bind:open={showLikers}
  likers={likers}
  loading={likersLoading}
  title="Beğenenler"
  onclose={() => showLikers = false}
/>
```

### `UserChip.svelte` (Faz 2)
```svelte
<UserChip uid={u.uid} name={u.name} photoURL={u.photo} subtitle="Takipçi" />
```

### `QuoteCard.svelte` (Faz 1)
```svelte
<QuoteCard
  quoteText={post.quoteText}
  bookName={post.bookName}
  authorName={post.authorName}
  coverImg={post.coverImg}
  expanded={false}
/>
```
