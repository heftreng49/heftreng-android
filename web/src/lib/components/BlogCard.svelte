<!-- Android BlogPostCard karşılığı -->
<script lang="ts">
  import type { BlogPost } from '$lib/services/blog.service';
  interface Props { post: BlogPost; }
  let { post }: Props = $props();
</script>

<a href="/blog/{post.id}" class="blog-card">
  {#if post.thumbnail}
    <img src={post.thumbnail} alt={post.title} class="blog-thumb" />
  {:else}
    <div class="blog-thumb-ph">📝</div>
  {/if}
  <div class="blog-body">
    {#if post.labels.length > 0}
      <div class="blog-labels">
        {#each post.labels.slice(0,3) as label}
          <span class="label-chip">{label}</span>
        {/each}
      </div>
    {/if}
    <p class="blog-title">{post.title}</p>
    <p class="blog-summary">{post.summary}</p>
    <span class="blog-date">{new Date(post.published).toLocaleDateString('tr-TR', { day:'numeric', month:'long', year:'numeric' })}</span>
  </div>
</a>

<style>
.blog-card {
  display: flex; flex-direction: column;
  background: var(--surface); border-radius: 16px;
  border: 1px solid var(--divider); overflow: hidden;
  text-decoration: none; color: inherit;
  transition: box-shadow 0.15s, transform 0.15s;
}
.blog-card:hover { box-shadow: 0 4px 20px rgba(0,0,0,.10); transform: translateY(-2px); }
.blog-thumb { width: 100%; aspect-ratio: 16/9; object-fit: cover; }
.blog-thumb-ph {
  width: 100%; aspect-ratio: 16/9;
  background: var(--surface-var); display: flex;
  align-items: center; justify-content: center; font-size: 2.5rem;
}
.blog-body { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 6px; }
.blog-labels { display: flex; flex-wrap: wrap; gap: 5px; }
.label-chip {
  font-size: 10px; font-weight: 700; border-radius: 4px;
  padding: 2px 7px;
  background: color-mix(in srgb, var(--primary) 12%, transparent);
  color: var(--primary);
}
.blog-title { font-size: 0.95rem; font-weight: 700; color: var(--on-bg); margin: 0; line-height: 1.35; }
.blog-summary { font-size: 0.78rem; color: var(--muted); margin: 0; line-height: 1.5;
  display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.blog-date { font-size: 0.72rem; color: var(--muted); }
</style>
