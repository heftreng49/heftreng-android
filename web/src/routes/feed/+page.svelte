<script lang="ts">
  import { onMount } from 'svelte';
  import { collection, query, orderBy, limit, getDocs } from 'firebase/firestore';
  import { db } from '$lib/firebase/config';
  import type { Post } from '$lib/types';
  import Navbar from '$lib/components/Navbar.svelte';
  import PostCard from '$lib/components/PostCard.svelte';

  let posts: Post[] = [];
  let loading = true;
  let error = '';

  onMount(async () => {
    try {
      const q = query(collection(db, 'feed'), orderBy('ts', 'desc'), limit(30));
      const snap = await getDocs(q);
      posts = snap.docs.map(d => ({ id: d.id, ...d.data() } as Post));
    } catch (e) {
      error = 'Gönderiler yüklenemedi.';
      console.error(e);
    } finally {
      loading = false;
    }
  });
</script>

<Navbar />

<main class="feed-container">
  {#if loading}
    {#each Array(5) as _}
      <div class="skeleton-card">
        <div class="sk-avatar"></div>
        <div class="sk-lines">
          <div class="sk-line sk-name"></div>
          <div class="sk-line sk-body"></div>
          <div class="sk-line sk-body2"></div>
        </div>
      </div>
    {/each}
  {:else if error}
    <p class="error">{error}</p>
  {:else if posts.length === 0}
    <p class="empty">Henüz gönderi yok.</p>
  {:else}
    {#each posts as post (post.id)}
      <PostCard {post} />
    {/each}
  {/if}
</main>

<style>
.feed-container { max-width: 600px; margin: 0 auto; }
.error, .empty { text-align: center; padding: 40px 16px; color: var(--muted); }

.skeleton-card {
  display: flex; gap: 10px; padding: 14px 16px;
  border-bottom: 1px solid var(--divider);
}
.sk-avatar {
  width: 40px; height: 40px; border-radius: 50%;
  background: var(--shimmer); flex-shrink: 0;
  animation: shimmer 1.2s infinite;
}
.sk-lines { flex: 1; display: flex; flex-direction: column; gap: 8px; }
.sk-line { background: var(--shimmer); border-radius: 6px; animation: shimmer 1.2s infinite; }
.sk-name  { height: 14px; width: 40%; }
.sk-body  { height: 14px; width: 90%; }
.sk-body2 { height: 14px; width: 70%; }

@keyframes shimmer {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
