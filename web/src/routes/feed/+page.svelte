<script lang="ts">
  import { onMount }         from 'svelte';
  import PostCard            from '$lib/components/PostCard.svelte';
  import Skeleton            from '$lib/components/Skeleton.svelte';
  import CommentPanel        from '$lib/components/CommentPanel.svelte';
  import TabBar              from '$lib/components/TabBar.svelte';
  import PullToRefresh       from '$lib/components/PullToRefresh.svelte';
  import EmptyState          from '$lib/components/EmptyState.svelte';
  import { currentUser }     from '$lib/stores/auth';
  import {
    posts, feedLoading, hasMore, lastDoc,
    commentPostId, resetFeed,
  } from '$lib/stores/feed.store';
  import { fetchFeedPage, enrichPosts, updatePost, deletePost as svcDelete } from '$lib/services/feed.service';
  import { toggleLike, toggleSave, fetchUnreadCounts, isFollowing } from '$lib/services/social.service';
  import { supabase } from '$lib/supabase/config';
  import type { Post } from '$lib/models/post';

  // ── State ──────────────────────────────────────────────────────────────────
  let activeTab        = $state(0);   // 0=Herkes  1=Takip Edilenler
  let loadingMore      = $state(false);
  let fabSheetOpen     = $state(false);
  let unreadNotifs     = $state(0);
  let unreadMessages   = $state(0);

  let refreshing = $state(false);

  // Takip edilen uid'ler (Takip Edilenler sekmesi için)
  let followingUids    = $state<Set<string>>(new Set());

  // Önerilen kullanıcılar
  interface SuggestedUser { uid: string; name: string; photoURL: string; bio: string; isFollowing: boolean; }
  let suggestedUsers   = $state<SuggestedUser[]>([]);
  let followingInProgress = $state<Set<string>>(new Set());

  // ── Init ───────────────────────────────────────────────────────────────────
  onMount(async () => {
    await load();
    if ($currentUser) {
      // Önce followingUids yükle — suggestedUsers exclude listesi buna bağlı
      await loadFollowingUids();
      const [counts] = await Promise.all([
        fetchUnreadCounts($currentUser.uid),
        loadSuggestedUsers(),
      ]);
      unreadNotifs   = counts.notifs;
      unreadMessages = counts.messages;
    }
  });

  async function loadFollowingUids() {
    if (!$currentUser) return;
    const { data } = await supabase
      .from('follows')
      .select('target_uid')
      .eq('from_uid', $currentUser.uid);
    followingUids = new Set((data ?? []).map((r: any) => r.target_uid as string));
  }

  async function loadSuggestedUsers() {
    if (!$currentUser) return;
    const myUid = $currentUser.uid;

    // followingUids state'i bu noktada dolu — exclude listesini taze oluştur
    const excludeUids = new Set([myUid, ...followingUids]);

    try {
      const { data } = await supabase
        .from('users')
        .select('uid, display_name, photo_url, bio')
        .order('created_at', { ascending: false })
        .limit(200);

      // Takip edilenler ve kendi UID'si kesinlikle dışarıda
      const candidates = (data ?? []).filter((r: any) =>
        r.uid &&
        r.display_name &&
        !excludeUids.has(r.uid as string)
      );

      // Karıştır, 8 al
      const shuffled = candidates.sort(() => Math.random() - 0.5).slice(0, 8);
      suggestedUsers = shuffled.map((r: any) => ({
        uid: r.uid, name: r.display_name ?? '', photoURL: r.photo_url ?? '', bio: r.bio ?? '', isFollowing: false,
      }));
    } catch(e) { console.error('suggestedUsers:', e); }
  }

  // ── Takip Et (carousel'den) ────────────────────────────────────────────────
  async function handleSuggestedFollow(user: SuggestedUser) {
    if (!$currentUser) { window.location.href = '/login'; return; }
    const inProg = new Set(followingInProgress);
    inProg.add(user.uid);
    followingInProgress = inProg;

    const wasFollowing = user.isFollowing;
    suggestedUsers = suggestedUsers.map(u => u.uid === user.uid ? { ...u, isFollowing: !wasFollowing } : u);

    try {
      if (wasFollowing) {
        await supabase.from('follows').delete().eq('from_uid', $currentUser.uid).eq('target_uid', user.uid);
      } else {
        await supabase.from('follows').upsert({
          id: `${$currentUser.uid}_${user.uid}`,
          from_uid: $currentUser.uid,
          from_name: $currentUser.displayName ?? '',
          from_photo: $currentUser.photoURL ?? '',
          target_uid: user.uid,
          target_name: user.name,
          target_photo: user.photoURL,
        });
      }
    } catch {
      suggestedUsers = suggestedUsers.map(u => u.uid === user.uid ? { ...u, isFollowing: wasFollowing } : u);
    } finally {
      const p = new Set(followingInProgress);
      p.delete(user.uid);
      followingInProgress = p;
    }
  }

  // ── Feed yükle ─────────────────────────────────────────────────────────────
  async function load() {
    if ($feedLoading) return;
    feedLoading.set(true);
    resetFeed();
    try {
      const res = await fetchFeedPage();
      const enriched = await enrichPosts(res.posts, $currentUser?.uid ?? null);
      posts.set(enriched);
      lastDoc.set(res.lastDoc);
      hasMore.set(res.hasMore);
    } finally { feedLoading.set(false); }
  }

  async function loadMore() {
    if (loadingMore || !$hasMore) return;
    loadingMore = true;
    try {
      const res = await fetchFeedPage($lastDoc);
      const enriched = await enrichPosts(res.posts, $currentUser?.uid ?? null);
      posts.update(prev => [...prev, ...enriched]);
      lastDoc.set(res.lastDoc);
      hasMore.set(res.hasMore);
    } finally { loadingMore = false; }
  }



  async function handleRefresh() {
    await load();
    await loadSuggestedUsers();
  }

  // ── Post aksiyonları ───────────────────────────────────────────────────────
  async function handleLike(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser) { window.location.href = '/login'; return; }
    const wasLiked = p.isLikedByMe ?? false;
    posts.update(list => list.map(x => x.id === p.id
      ? { ...x, isLikedByMe: !wasLiked, likesCount: Math.max(0, (x.likesCount??0) + (wasLiked?-1:1)) } : x));
    try {
      await toggleLike(p.id, $currentUser.uid, $currentUser.displayName??'', $currentUser.photoURL??'', wasLiked);
    } catch {
      posts.update(list => list.map(x => x.id === p.id
        ? { ...x, isLikedByMe: wasLiked, likesCount: Math.max(0, (x.likesCount??0) + (wasLiked?1:-1)) } : x));
    }
  }

  async function handleSave(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser) { window.location.href = '/login'; return; }
    const wasSaved = p.isSavedByMe ?? false;
    posts.update(list => list.map(x => x.id === p.id ? { ...x, isSavedByMe: !wasSaved } : x));
    try { await toggleSave(p.id, $currentUser.uid, wasSaved); }
    catch { posts.update(list => list.map(x => x.id === p.id ? { ...x, isSavedByMe: wasSaved } : x)); }
  }

  function handleComment(e: CustomEvent<Post>) { commentPostId.set(e.detail.id); }

  async function handleDelete(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser || $currentUser.uid !== p.uid) return;
    if (!confirm('Gönderiyi silmek istediğinize emin misiniz?')) return;
    await svcDelete(p.id);
    posts.update(list => list.filter(x => x.id !== p.id));
  }

  function handleEdit(e: CustomEvent<Post>) { window.location.href = '/compose?edit=' + e.detail.id; }

  // ── Filtreleme (Takip Edilenler sekmesi) ───────────────────────────────────
  const filteredPosts = $derived(
    activeTab === 1
      ? $posts.filter(p => followingUids.has((p as any).uid ?? ''))
      : $posts
  );
</script>

<svelte:head><title>Heftreng — Akış</title></svelte:head>

<PullToRefresh onRefresh={handleRefresh} bind:refreshing>

  <TabBar
    tabs={['Herkes', 'Takip Edilenler']}
    bind:active={activeTab}
    stickyTop={52}
  />

  <div class="feed-page">
    {#if $feedLoading}
      <div class="skeleton-list">
        {#each {length:5} as _}
          <div class="skeleton-card">
            <Skeleton width="40px" height="40px" radius="50%" />
            <div style="flex:1">
              <Skeleton width="40%" height="14px" />
              <Skeleton width="60%" height="12px" />
              <Skeleton width="90%" height="14px" />
            </div>
          </div>
        {/each}
      </div>

    {:else if filteredPosts.length === 0}
      {#if activeTab === 1}
        <EmptyState icon="👥" message="Takip ettiğin kişilerin gönderileri burada görünür." hint="Kişi keşfet →" hintHref="/library" />
      {:else}
        <EmptyState icon="📄" message="Henüz gönderi yok.">
          {#if $currentUser}<a href="/compose" class="compose-cta" slot="action">İlk gönderiyi yaz →</a>{/if}
        </EmptyState>
      {/if}

    {:else}
      {#each filteredPosts as post, i (post.id)}
        <PostCard
          {post}
          currentUid={$currentUser?.uid ?? null}
          on:like={handleLike}
          on:save={handleSave}
          on:comment={handleComment}
          on:delete={handleDelete}
          on:edit={handleEdit}
        />

        <!-- 5. posttan sonra önerilen kullanıcılar (Android ile aynı konum) -->
        {#if i === 4 && activeTab === 0 && suggestedUsers.length > 0}
          <div class="suggested-wrap">
            <div class="suggested-header">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><line x1="19" y1="8" x2="19" y2="14"/><line x1="22" y1="11" x2="16" y2="11"/></svg>
              <span>Önerilen Kişiler</span>
              <a href="/search" class="suggested-see-all">Tümünü Gör</a>
            </div>
            <div class="suggested-scroll">
              {#each suggestedUsers as user (user.uid)}
                <div class="suggested-card">
                  <a href="/profile/{user.uid}" class="suggested-av">
                    {#if user.photoURL}
                      <img src={user.photoURL} alt={user.name} />
                    {:else}
                      <span>{(user.name || '?')[0].toUpperCase()}</span>
                    {/if}
                  </a>
                  <a href="/profile/{user.uid}" class="suggested-name">{user.name}</a>
                  {#if user.bio}
                    <p class="suggested-bio">{user.bio.slice(0, 40)}{user.bio.length > 40 ? '…' : ''}</p>
                  {/if}
                  <button
                    class="suggested-follow-btn"
                    class:following={user.isFollowing}
                    disabled={followingInProgress.has(user.uid)}
                    onclick={() => handleSuggestedFollow(user)}
                  >
                    {user.isFollowing ? 'Takip Ediliyor' : 'Takip Et'}
                  </button>
                </div>
              {/each}
            </div>
          </div>
        {/if}
      {/each}

      {#if $hasMore}
        <button class="load-more" onclick={loadMore} disabled={loadingMore}>
          {loadingMore ? 'Yükleniyor…' : 'Daha fazla göster'}
        </button>
      {/if}
    {/if}
  </div>
</PullToRefresh>

<!-- FAB -->
{#if $currentUser}
  {#if fabSheetOpen}
    <!-- svelte-ignore a11y_click_events_have_key_events -->
    <!-- svelte-ignore a11y_no_static_element_interactions -->
    <div class="fab-backdrop" onclick={() => fabSheetOpen = false}></div>
    <div class="fab-sheet">
      <div class="fab-sheet-handle"></div>
      <p class="fab-sheet-title">Ne paylaşmak istersin?</p>
      <a href="/compose?type=post" class="fab-sheet-item" onclick={() => fabSheetOpen = false}>
        <span class="fab-sheet-icon" style="background:color-mix(in srgb,var(--primary) 15%,transparent)">
          <svg viewBox="0 0 24 24" fill="none" stroke="var(--primary)" stroke-width="2.5" width="22" height="22">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </span>
        <div>
          <strong>Gönderi Yaz</strong>
          <p>Düşüncelerini paylaş</p>
        </div>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted);margin-left:auto"><polyline points="9 18 15 12 9 6"/></svg>
      </a>
      <a href="/compose?type=quote" class="fab-sheet-item" onclick={() => fabSheetOpen = false}>
        <span class="fab-sheet-icon" style="background:color-mix(in srgb,#D97706 15%,transparent)">
          <svg viewBox="0 0 24 24" fill="#D97706" width="22" height="22">
            <path d="M4.583 17.321C3.553 16.227 3 15 3 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179zm10 0C13.553 16.227 13 15 13 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179z"/>
          </svg>
        </span>
        <div>
          <strong>Alıntı Paylaş</strong>
          <p>Kitaptan bir alıntı ekle</p>
        </div>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted);margin-left:auto"><polyline points="9 18 15 12 9 6"/></svg>
      </a>
    </div>
  {/if}

  <button
    class="fab"
    class:fab-open={fabSheetOpen}
    aria-label="Paylaş"
    onclick={() => fabSheetOpen = !fabSheetOpen}
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5" width="26" height="26"
      style="transition:transform 0.25s;transform:rotate({fabSheetOpen ? 45 : 0}deg)"
    >
      <line x1="12" y1="5" x2="12" y2="19"/>
      <line x1="5" y1="12" x2="19" y2="12"/>
    </svg>
  </button>
{/if}

<!-- Yorum paneli -->
{#if $commentPostId}
  <CommentPanel
    postId={$commentPostId}
    currentUser={$currentUser}
    on:close={() => commentPostId.set(null)}
    on:countchange={(e) => posts.update(list =>
      list.map(p => p.id === $commentPostId ? { ...p, commentsCount: e.detail } : p)
    )}
  />
{/if}

<style>
/* ── Feed ───────────────────────────────────────────────────────────────────── */
.feed-page { padding: 8px 12px 80px; max-width: 680px; margin: 0 auto; }

.skeleton-list { padding: 4px 0; }
.skeleton-card {
  display: flex; gap: 10px; padding: 14px; margin-bottom: 8px;
  background: var(--card); border-radius: 14px;
}

.compose-cta {
  display: inline-block; margin-top: 4px; padding: 10px 20px;
  background: var(--primary); color: #fff; border-radius: 20px;
  text-decoration: none; font-weight: 700; font-size: 14px;
}

.load-more {
  display: block; width: 100%; padding: 14px;
  background: var(--surface-var); border: none; border-radius: 12px;
  font-size: 14px; font-weight: 600; color: var(--primary);
  cursor: pointer; margin-top: 4px; font-family: inherit;
}
.load-more:disabled { opacity: .5; cursor: default; }

/* ── Önerilen Kullanıcılar ──────────────────────────────────────────────────── */
.suggested-wrap {
  background: var(--card); border-radius: 16px;
  border: 0.7px solid var(--divider); margin-bottom: 8px; overflow: hidden;
}
.suggested-header {
  display: flex; align-items: center; gap: 6px;
  padding: 12px 14px 10px; color: var(--on-bg); font-size: 14px; font-weight: 600;
}
.suggested-header svg { color: var(--primary); flex-shrink: 0; }
.suggested-header span { flex: 1; }
.suggested-see-all {
  font-size: 13px; font-weight: 600; color: var(--primary);
  text-decoration: none;
}
.suggested-scroll {
  display: flex; gap: 10px; overflow-x: auto; padding: 4px 14px 14px;
  scrollbar-width: none; -webkit-overflow-scrolling: touch;
}
.suggested-scroll::-webkit-scrollbar { display: none; }
.suggested-card {
  display: flex; flex-direction: column; align-items: center;
  min-width: 110px; max-width: 110px; padding: 12px 8px;
  background: var(--surface-var); border-radius: 14px; gap: 5px;
  border: 0.5px solid var(--divider);
}
.suggested-av {
  width: 52px; height: 52px; border-radius: 50%;
  background: var(--surface); overflow: hidden;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; color: var(--on-bg);
  text-decoration: none; flex-shrink: 0;
  border: 2px solid color-mix(in srgb, var(--primary) 30%, transparent);
}
.suggested-av img { width: 100%; height: 100%; object-fit: cover; }
.suggested-name {
  font-size: 12px; font-weight: 600; color: var(--on-bg); text-decoration: none;
  text-align: center; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  width: 100%;
}
.suggested-bio {
  font-size: 10px; color: var(--muted); text-align: center;
  line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2;
  -webkit-box-orient: vertical; overflow: hidden; width: 100%;
}
.suggested-follow-btn {
  margin-top: 2px; padding: 5px 10px; border-radius: 99px;
  font-size: 11px; font-weight: 600; cursor: pointer;
  border: none; font-family: inherit; transition: opacity 0.15s;
  background: var(--primary); color: #fff; width: 100%;
}
.suggested-follow-btn.following {
  background: var(--surface); color: var(--muted);
  border: 1px solid var(--divider);
}
.suggested-follow-btn:disabled { opacity: 0.5; cursor: not-allowed; }

/* ── FAB ────────────────────────────────────────────────────────────────────── */
.fab {
  position: fixed; bottom: 76px; right: 20px;
  width: 52px; height: 52px; border-radius: 50%;
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  border: none; cursor: pointer;
  box-shadow: 0 4px 16px rgba(0,0,0,.25);
  z-index: 110; transition: background 0.2s, transform 0.2s;
}
.fab:hover { transform: scale(1.07); }
.fab.fab-open { background: var(--muted); }

.fab-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  z-index: 105; backdrop-filter: blur(2px);
}
.fab-sheet {
  position: fixed; bottom: 0; left: 0; right: 0;
  max-width: 600px; margin: 0 auto;
  background: var(--surface); border-radius: 20px 20px 0 0;
  padding: 0 16px 32px; z-index: 110;
  box-shadow: 0 -4px 24px rgba(0,0,0,0.15);
  animation: sheet-up 0.25s cubic-bezier(.4,0,.2,1);
}
@keyframes sheet-up {
  from { transform: translateY(100%); }
  to   { transform: translateY(0); }
}
.fab-sheet-handle {
  width: 40px; height: 4px; background: var(--divider);
  border-radius: 2px; margin: 12px auto 16px;
}
.fab-sheet-title {
  font-size: 12px; font-weight: 600;
  color: var(--muted); margin: 0 0 10px;
}
.fab-sheet-item {
  display: flex; align-items: center; gap: 14px;
  padding: 14px 12px; border-radius: 14px;
  text-decoration: none; color: var(--on-bg);
  transition: background 0.15s; margin-bottom: 6px;
  background: var(--bg);
}
.fab-sheet-item:hover { background: var(--surface-var); }
.fab-sheet-item strong { display: block; font-size: 15px; font-weight: 700; }
.fab-sheet-item p { margin: 2px 0 0; font-size: 12px; color: var(--muted); }
.fab-sheet-icon {
  width: 46px; height: 46px; border-radius: 14px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
</style>
