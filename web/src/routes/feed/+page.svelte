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
  <a href="/compose" class="fab" aria-label="Gönderi yaz">+</a>
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
    background: #7c4dff; color: #fff;
    display: flex; align-items: center; justify-content: center;
    font-size: 28px; text-decoration: none;
    box-shadow: 0 4px 16px rgba(124,77,255,.35);
    z-index: 50;
  }
</style>
