<script lang="ts">
  // Faz 1 refactor — sadece store bağlama + component dizimi
  // Doğrudan Firestore/Supabase çağrısı YOK
  import { onMount } from 'svelte';
  import PostCard    from '$lib/components/PostCard.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import CommentPanel from '$lib/components/CommentPanel.svelte';
  import { currentUser } from '$lib/stores/auth';
  import {
    posts, feedLoading, hasMore, lastDoc,
    commentPostId, resetFeed,
  } from '$lib/stores/feed.store';
  import { fetchFeedPage, enrichPosts, updatePost, deletePost as svcDelete } from '$lib/services/feed.service';
  import { toggleLike, toggleSave, fetchUnreadCounts } from '$lib/services/social.service';
  import type { Post } from '$lib/models/post';

  let activeTab      = $state(0);
  let loadingMore    = $state(false);
  let unreadNotifs   = $state(0);
  let unreadMessages = $state(0);

  onMount(async () => {
    await load();
    if ($currentUser) {
      const counts = await fetchUnreadCounts($currentUser.uid);
      unreadNotifs   = counts.notifs;
      unreadMessages = counts.messages;
    }
  });

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

  async function handleLike(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser) { window.location.href = '/login'; return; }
    const wasLiked = p.isLikedByMe ?? false;
    // Optimistic UI
    posts.update(list => list.map(x => x.id === p.id
      ? { ...x, isLikedByMe: !wasLiked, likesCount: Math.max(0, (x.likesCount??0) + (wasLiked?-1:1)) }
      : x));
    try {
      await toggleLike(p.id, $currentUser.uid, $currentUser.displayName??'', $currentUser.photoURL??'', wasLiked);
    } catch {
      // Geri al
      posts.update(list => list.map(x => x.id === p.id
        ? { ...x, isLikedByMe: wasLiked, likesCount: Math.max(0, (x.likesCount??0) + (wasLiked?1:-1)) }
        : x));
    }
  }

  async function handleSave(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser) { window.location.href = '/login'; return; }
    const wasSaved = p.isSavedByMe ?? false;
    posts.update(list => list.map(x => x.id === p.id ? { ...x, isSavedByMe: !wasSaved } : x));
    try {
      await toggleSave(p.id, $currentUser.uid, wasSaved);
    } catch {
      posts.update(list => list.map(x => x.id === p.id ? { ...x, isSavedByMe: wasSaved } : x));
    }
  }

  function handleComment(e: CustomEvent<Post>) {
    commentPostId.set(e.detail.id);
  }

  async function handleDelete(e: CustomEvent<Post>) {
    const p = e.detail;
    if (!$currentUser || $currentUser.uid !== p.uid) return;
    if (!confirm('Gönderiyi silmek istediğinize emin misiniz?')) return;
    await svcDelete(p.id);
    posts.update(list => list.filter(x => x.id !== p.id));
  }

  function handleEdit(e: CustomEvent<Post>) {
    window.location.href = '/compose?edit=' + e.detail.id;
  }

  const filteredPosts = $derived(
    activeTab === 1 ? $posts.filter(p => p.uid === $currentUser?.uid) : $posts
  );
</script>

<svelte:head><title>Heftreng — Akış</title></svelte:head>

<!-- Sekmeler -->
<div class="tabs">
  <button class="tab" class:active={activeTab===0} onclick={() => activeTab=0}>Herkes</button>
  <button class="tab" class:active={activeTab===1} onclick={() => activeTab=1}>Benimkiler</button>
</div>

<main class="feed-page">
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
    <div class="empty-state">
      <p>Henüz gönderi yok.</p>
      {#if $currentUser}<a href="/compose" class="compose-cta">İlk gönderiyi yaz →</a>{/if}
    </div>

  {:else}
    {#each filteredPosts as post (post.id)}
      <PostCard
        {post}
        currentUid={$currentUser?.uid ?? null}
        on:like={handleLike}
        on:save={handleSave}
        on:comment={handleComment}
        on:delete={handleDelete}
        on:edit={handleEdit}
      />
    {/each}

    {#if $hasMore}
      <button class="load-more" onclick={loadMore} disabled={loadingMore}>
        {loadingMore ? 'Yükleniyor…' : 'Daha fazla göster'}
      </button>
    {/if}
  {/if}
</main>

<!-- FAB -->
{#if $currentUser}
  <!-- FAB Sheet backdrop -->
  {#if fabSheetOpen}
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <!-- svelte-ignore a11y-no-static-element-interactions -->
    <div class="fab-backdrop" onclick={() => fabSheetOpen = false}></div>
  {/if}

  <!-- FAB Sheet -->
  {#if fabSheetOpen}
    <div class="fab-sheet">
      <p class="fab-sheet-title">Ne paylaşmak istersin?</p>
      <a href="/compose?type=post" class="fab-sheet-item" onclick={() => fabSheetOpen = false}>
        <span class="fab-sheet-icon" style="background:#4A6FFF">
          <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5" width="22" height="22">
            <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
            <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
          </svg>
        </span>
        <div>
          <strong>Gönderi Yaz</strong>
          <p>Düşüncelerini paylaş</p>
        </div>
      </a>
      <a href="/compose?type=quote" class="fab-sheet-item" onclick={() => fabSheetOpen = false}>
        <span class="fab-sheet-icon" style="background:#D97706">
          <svg viewBox="0 0 24 24" fill="#fff" width="22" height="22">
            <path d="M4.583 17.321C3.553 16.227 3 15 3 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179zm10 0C13.553 16.227 13 15 13 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179z"/>
          </svg>
        </span>
        <div>
          <strong>Alıntı Paylaş</strong>
          <p>Kitaptan bir alıntı ekle</p>
        </div>
      </a>
    </div>
  {/if}

  <!-- FAB Button -->
  <button
    class="fab"
    class:fab-open={fabSheetOpen}
    aria-label="Paylaş"
    onclick={() => fabSheetOpen = !fabSheetOpen}
  >
    <svg viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2.5" width="26" height="26"
      style="transition: transform 0.25s; transform: rotate({fabSheetOpen ? 45 : 0}deg)"
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
  .tabs {
    display: flex; border-bottom: 2px solid #f0ebf9;
    position: sticky; top: 0; background: #fff; z-index: 10;
  }
  .tab {
    flex: 1; padding: 12px; background: none; border: none;
    font-size: 14px; font-weight: 600; color: #999; cursor: pointer;
    border-bottom: 2px solid transparent; margin-bottom: -2px;
    transition: color .15s, border-color .15s;
  }
  .tab.active { color: #7c4dff; border-bottom-color: #7c4dff; }

  .feed-page { padding: 12px; max-width: 680px; margin: 0 auto; }

  .skeleton-list { padding: 12px; }
  .skeleton-card { display: flex; gap: 10px; padding: 14px; margin-bottom: 10px;
    background: #fff; border-radius: 14px; }

  .empty-state { text-align: center; padding: 48px 16px; color: #999; }
  .compose-cta {
    display: inline-block; margin-top: 12px; padding: 10px 20px;
    background: #7c4dff; color: #fff; border-radius: 20px; text-decoration: none;
    font-weight: 700; font-size: 14px;
  }

  .load-more {
    display: block; width: 100%; padding: 14px;
    background: #f5f0fc; border: none; border-radius: 12px;
    font-size: 14px; font-weight: 600; color: #7c4dff;
    cursor: pointer; margin-top: 4px;
  }
  .load-more:disabled { opacity: .5; cursor: default; }

  .fab {
    position: fixed; bottom: 80px; right: 20px;
    width: 52px; height: 52px; border-radius: 50%;
    background: var(--primary); color: #fff;
    display: flex; align-items: center; justify-content: center;
    border: none; cursor: pointer;
    box-shadow: 0 4px 16px rgba(0,0,0,.25);
    z-index: 60; transition: background 0.2s, transform 0.2s;
  }
  .fab:hover { transform: scale(1.07); }
  .fab.fab-open { background: #555; }

  .fab-backdrop {
    position: fixed; inset: 0;
    background: rgba(0,0,0,0.4);
    z-index: 55; backdrop-filter: blur(2px);
  }

  .fab-sheet {
    position: fixed; bottom: 148px; right: 16px;
    background: var(--surface);
    border: 1px solid var(--divider);
    border-radius: 20px;
    padding: 16px 12px 10px;
    z-index: 60;
    width: min(320px, calc(100vw - 32px));
    box-shadow: 0 8px 32px rgba(0,0,0,0.18);
    animation: sheet-pop 0.2s ease;
  }
  @keyframes sheet-pop {
    from { opacity: 0; transform: translateY(12px) scale(0.96); }
    to   { opacity: 1; transform: translateY(0)   scale(1); }
  }
  .fab-sheet-title {
    font-size: 0.78rem; font-weight: 600;
    color: var(--muted); margin: 0 4px 10px;
  }
  .fab-sheet-item {
    display: flex; align-items: center; gap: 14px;
    padding: 12px 10px; border-radius: 14px;
    text-decoration: none; color: var(--on-bg);
    transition: background 0.15s; margin-bottom: 4px;
  }
  .fab-sheet-item:hover { background: var(--surface-var); }
  .fab-sheet-item strong { display: block; font-size: 0.95rem; font-weight: 700; }
  .fab-sheet-item p { margin: 2px 0 0; font-size: 0.78rem; color: var(--muted); }
  .fab-sheet-icon {
    width: 46px; height: 46px; border-radius: 14px; flex-shrink: 0;
    display: flex; align-items: center; justify-content: center;
  }
</style>
