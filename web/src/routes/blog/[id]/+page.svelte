<script lang="ts">
  import { onMount }       from 'svelte';
  import { page }          from '$app/stores';
  import { fetchBlogPost } from '$lib/services/blog.service';
  import Skeleton from '$lib/components/Skeleton.svelte';

  const id     = $derived($page.params.id);
  let post     = $state<any>(null);
  let loading  = $state(true);

  onMount(async () => {
    post    = await fetchBlogPost(id);
    loading = false;
  });
</script>

<svelte:head>
  <title>{post?.title ? `${post.title} — Heft Reng` : "Blog — Heft Reng"}</title>
  <meta name="description" content={post?.summary ? post.summary.slice(0, 160) : (post?.title ?? "Nivîsa Blogê ya Heft Reng")} />
  <meta property="og:title"       content={post?.title ? `${post.title} — Heft Reng` : "Heft Reng"} />
  <meta property="og:description" content={post?.summary ? post.summary.slice(0, 200) : (post?.title ?? "Nivîsa Blogê ya Heft Reng")} />
  <meta property="og:url"         content={"https://heftreng.onrender.com/blog/" + $page.params.id} />
  <meta property="og:type"        content="article" />
  <meta property="og:image"       content={post?.coverUrl || "https://heftreng.onrender.com/og-default.png"} />
  <meta name="twitter:title"      content={post?.title ? `${post.title} — Heft Reng` : "Heft Reng"} />
  <meta name="twitter:description" content={post?.summary ? post.summary.slice(0, 200) : "Nivîsa Blogê ya Heft Reng"} />
  <meta name="twitter:image"      content={post?.coverUrl || "https://heftreng.onrender.com/og-default.png"} />
</svelte:head>

<div class="blog-post-page">
  {#if loading}
    <div class="bp-sk">
      <Skeleton width="100%" height="220px" radius="0" />
      <div style="padding:16px;display:flex;flex-direction:column;gap:10px">
        <Skeleton width="80%" height="20px" />
        <Skeleton width="50%" height="14px" />
        <Skeleton width="100%" height="12px" />
        <Skeleton width="90%" height="12px" />
      </div>
    </div>
  {:else if !post}
    <div class="bp-not-found">Yazı bulunamadı.</div>
  {:else}
    {#if post.thumbnail}
      <img src={post.thumbnail} alt={post.title} class="bp-cover" />
    {/if}
    <div class="bp-body">
      {#if post.labels.length > 0}
        <div class="bp-labels">
          {#each post.labels as l}<span class="bp-label">{l}</span>{/each}
        </div>
      {/if}
      <h1 class="bp-title">{post.title}</h1>
      <div class="bp-meta">
        {#if post.author.imageUrl}<img src={post.author.imageUrl} alt={post.author.name} class="bp-av" />{/if}
        <span class="bp-author">{post.author.name}</span>
        <span class="bp-date">{new Date(post.published).toLocaleDateString('tr-TR', {day:'numeric',month:'long',year:'numeric'})}</span>
      </div>
      <!-- Blog HTML içeriği -->
      <div class="bp-content">{@html post.content}</div>
    </div>
  {/if}
</div>

<style>
.blog-post-page { min-height: 100dvh; padding-bottom: 80px; }
.bp-cover { width: 100%; max-height: 260px; object-fit: cover; }
.bp-body { padding: 16px; }
.bp-labels { display: flex; flex-wrap: wrap; gap: 6px; margin-bottom: 10px; }
.bp-label { font-size: 10px; font-weight: 700; border-radius: 4px; padding: 2px 8px;
  background: color-mix(in srgb,var(--primary) 12%,transparent); color: var(--primary); }
.bp-title { font-size: 1.3rem; font-weight: 800; color: var(--on-bg); margin: 0 0 12px; line-height: 1.3; }
.bp-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 16px; }
.bp-av { width: 28px; height: 28px; border-radius: 50%; object-fit: cover; }
.bp-author { font-size: .8rem; font-weight: 600; color: var(--on-bg); }
.bp-date { font-size: .75rem; color: var(--muted); }
.bp-content { font-size: .92rem; line-height: 1.75; color: var(--on-bg); }
.bp-content :global(img) { max-width: 100%; border-radius: 10px; margin: 10px 0; }
.bp-content :global(a)   { color: var(--primary); }
.bp-content :global(h2)  { font-size: 1.1rem; font-weight: 700; margin-top: 20px; }
.bp-not-found { display:flex;align-items:center;justify-content:center;height:200px;color:var(--muted); }
.bp-sk { display: flex; flex-direction: column; }
</style>
