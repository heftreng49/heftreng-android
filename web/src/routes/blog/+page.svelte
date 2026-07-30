<script lang="ts">
  import { onMount }     from 'svelte';
  import { fetchBlogPosts } from '$lib/services/blog.service';
  import BlogCard    from '$lib/components/BlogCard.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import EmptyState  from '$lib/components/EmptyState.svelte';
  import PullToRefresh from '$lib/components/PullToRefresh.svelte';

  let posts      = $state<any[]>([]);
  let labels     = $state<string[]>([]);
  let activeLabel= $state<string|null>(null);
  let nextToken  = $state<string|null>(null);
  let loading    = $state(true);
  let loadingMore= $state(false);

  onMount(() => load());

  async function load(label = activeLabel, append = false) {
    if (!append) { loading = true; posts = []; nextToken = null; }
    const res = await fetchBlogPosts(label ?? undefined);
    posts     = append ? [...posts, ...res.posts] : res.posts;
    nextToken = res.nextToken;
    // Tüm label'ları topla
    const all = new Set<string>(labels);
    res.posts.flatMap((p: any) => p.labels).forEach((l: string) => all.add(l));
    labels = [...all].sort();
    loading = false;
  }

  async function handleRefresh() { await load(activeLabel); }

  async function filterBy(label: string | null) {
    activeLabel = label;
    await load(label);
  }

  async function loadMore() {
    if (!nextToken || loadingMore) return;
    loadingMore = true;
    const res = await fetchBlogPosts(activeLabel ?? undefined, nextToken);
    posts     = [...posts, ...res.posts];
    nextToken = res.nextToken;
    loadingMore = false;
  }
</script>

<svelte:head><title>Blog — Heftreng</title></svelte:head>

<PullToRefresh onRefresh={handleRefresh}>
  <div class="blog-page">
    <div class="blog-header">
      <h2>Blog</h2>
    </div>

    <!-- Label filtreleri -->
    {#if labels.length > 0}
      <div class="label-bar">
        <button class="label-chip" class:active={activeLabel === null} onclick={() => filterBy(null)}>Tümü</button>
        {#each labels as lbl}
          <button class="label-chip" class:active={activeLabel === lbl} onclick={() => filterBy(lbl)}>{lbl}</button>
        {/each}
      </div>
    {/if}

    {#if loading}
      <div class="blog-grid">
        {#each {length:4} as _}
          <div class="sk-card">
            <Skeleton width="100%" height="160px" radius="12px" />
            <Skeleton width="60%" height="14px" />
            <Skeleton width="80%" height="12px" />
          </div>
        {/each}
      </div>
    {:else if posts.length === 0}
      <EmptyState icon="📝" message="Blog yazısı bulunamadı." />
    {:else}
      <div class="blog-grid">
        {#each posts as post (post.id)}
          <BlogCard {post} />
        {/each}
      </div>
      {#if nextToken}
        <button class="load-more" onclick={loadMore} disabled={loadingMore}>
          {loadingMore ? 'Yükleniyor…' : 'Daha Fazla'}
        </button>
      {/if}
    {/if}
  </div>
</PullToRefresh>

<style>
.blog-page { min-height: 100dvh; padding-bottom: 80px; }
.blog-header { padding: 14px 16px 8px; }
.blog-header h2 { margin: 0; font-size: 1.1rem; font-weight: 700; }
.label-bar {
  display: flex; gap: 8px; padding: 4px 16px 12px;
  overflow-x: auto; scrollbar-width: none;
}
.label-bar::-webkit-scrollbar { display: none; }
.label-chip {
  flex-shrink: 0; padding: 5px 14px; border-radius: 20px; font-size: .78rem; font-weight: 600;
  border: 1.5px solid var(--divider); background: transparent; color: var(--muted);
  cursor: pointer; font-family: inherit; white-space: nowrap; transition: all .15s;
}
.label-chip.active { border-color: var(--primary); background: color-mix(in srgb,var(--primary) 12%,transparent); color: var(--primary); }
.blog-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px,1fr)); gap: 14px; padding: 0 14px; }
.sk-card { display: flex; flex-direction: column; gap: 8px; }
.load-more {
  display: block; margin: 20px auto; padding: 10px 28px;
  border-radius: 20px; border: 1.5px solid var(--primary);
  background: transparent; color: var(--primary); font-weight: 700;
  font-size: .85rem; cursor: pointer; font-family: inherit;
}
</style>
