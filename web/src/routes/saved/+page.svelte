<script lang="ts">
  import { onMount } from 'svelte';
  import { goto }    from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import { supabase }    from '$lib/supabase/config';
  import { db }          from '$lib/firebase/config';
  import { doc, getDoc } from 'firebase/firestore';
  import { togglePostSave } from '$lib/services/post.service';
  import PostCard    from '$lib/components/PostCard.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import EmptyState  from '$lib/components/EmptyState.svelte';
  import InfiniteScroll from '$lib/components/InfiniteScroll.svelte';

  let posts    = $state<any[]>([]);
  let loading  = $state(true);
  let hasMore  = $state(false);
  let page     = $state(0);
  const PAGE   = 15;

  onMount(() => { if (!$currentUser) goto('/login'); else load(); });

  async function load(append = false) {
    if (!$currentUser) return;
    loading = true;
    const from = page * PAGE;
    const { data } = await supabase.from('feed_saves')
      .select('post_id, created_at')
      .eq('uid', $currentUser.uid)
      .order('created_at', { ascending: false })
      .range(from, from + PAGE - 1);

    const rows = data ?? [];
    hasMore = rows.length === PAGE;

    // Firestore'dan post verilerini çek
    const fetched: any[] = [];
    await Promise.all(rows.map(async (r: any) => {
      const snap = await getDoc(doc(db, 'feed', r.post_id));
      if (snap.exists()) fetched.push({ id: snap.id, ...snap.data(), isSavedByMe: true });
    }));
    posts = append ? [...posts, ...fetched] : fetched;
    loading = false;
  }

  async function loadMore() {
    page++;
    await load(true);
  }

  async function unsave(p: any) {
    if (!$currentUser) return;
    await togglePostSave(p.id, $currentUser.uid, true);
    posts = posts.filter(x => x.id !== p.id);
  }
</script>

<svelte:head><title>Kaydedilenler — Heftreng</title></svelte:head>

<div class="saved-page">
  <div class="saved-topbar"><h2>Kaydedilenler</h2></div>

  {#if loading && posts.length === 0}
    <div class="sk-list">
      {#each {length:5} as _}
        <div class="sk-row">
          <Skeleton width="40px" height="40px" radius="50%" />
          <div style="flex:1"><Skeleton width="40%" height="13px"/><Skeleton width="70%" height="11px"/></div>
        </div>
      {/each}
    </div>
  {:else if posts.length === 0}
    <EmptyState icon="🔖" message="Henüz kaydedilen gönderi yok." />
  {:else}
    <div class="post-list">
      {#each posts as p (p.id)}
        <PostCard
          post={p}
          currentUid={$currentUser?.uid}
          on:save={() => unsave(p)}
        />
      {/each}
    </div>
    <InfiniteScroll {hasMore} loading={loading} onLoadMore={loadMore} />
  {/if}
</div>

<style>
.saved-page { min-height: 100dvh; }
.saved-topbar { padding: 14px 16px 10px; }
.saved-topbar h2 { margin: 0; font-size: 1.1rem; font-weight: 700; }
.sk-list { display: flex; flex-direction: column; }
.sk-row { display: flex; gap: 10px; padding: 12px 16px; align-items: center; }
.post-list { display: flex; flex-direction: column; }
</style>
