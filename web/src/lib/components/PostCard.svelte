<script lang="ts">
  import type { Post } from '$lib/types';
  export let post: Post;

  function timeAgo(ts: number): string {
    const diff = Date.now() - ts;
    const m = Math.floor(diff / 60000);
    if (m < 1)  return 'şimdi';
    if (m < 60) return `${m}d`;
    const h = Math.floor(m / 60);
    if (h < 24) return `${h}sa`;
    return `${Math.floor(h / 24)}g`;
  }
</script>

<article class="card">
  <div class="card-header">
    <div class="avatar-wrap">
      {#if post.photoURL}
        <img src={post.photoURL} alt={post.displayName} class="avatar" />
      {:else}
        <div class="avatar-placeholder">{post.displayName?.[0] ?? '?'}</div>
      {/if}
    </div>
    <div class="meta">
      <div class="display-name">
        {post.isAnonymous ? 'Anonim' : post.displayName}
      </div>
      <div class="username-time">
        {#if !post.isAnonymous}<span>@{post.username}</span>{/if}
        <span class="time">{timeAgo(post.ts)}</span>
      </div>
    </div>
  </div>

  <p class="body">{post.body}</p>

  {#if post.imageUrls?.length}
    <div class="images">
      {#each post.imageUrls as url}
        <img src={url} alt="gönderi" class="post-img" />
      {/each}
    </div>
  {/if}

  <div class="actions">
    <button class="action-btn">❤️ {post.likeCount}</button>
    <button class="action-btn">💬 {post.commentCount}</button>
    <button class="action-btn">🔁 {post.repostCount}</button>
  </div>
</article>

<style>
.card {
  background: var(--card);
  border-bottom: 1px solid var(--divider);
  padding: 14px 16px;
}
.card-header { display: flex; gap: 10px; margin-bottom: 10px; }
.avatar, .avatar-placeholder {
  width: 40px; height: 40px;
  border-radius: 50%; object-fit: cover; flex-shrink: 0;
}
.avatar-placeholder {
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 16px;
}
.meta { display: flex; flex-direction: column; justify-content: center; }
.display-name { font-weight: 600; font-size: 15px; color: var(--on-bg); }
.username-time { font-size: 13px; color: var(--muted); display: flex; gap: 6px; }
.body { font-size: 15px; color: var(--on-bg); line-height: 1.6; white-space: pre-wrap; }
.images { margin-top: 10px; display: flex; flex-direction: column; gap: 8px; }
.post-img { width: 100%; border-radius: 12px; }
.actions { display: flex; gap: 20px; margin-top: 12px; }
.action-btn { font-size: 14px; color: var(--muted); padding: 4px 0; }
.action-btn:hover { color: var(--primary); }
</style>
