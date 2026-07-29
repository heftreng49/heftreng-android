<!--
  ActionBar — Beğeni + yorum + paylaş aksiyon satırı.
  Feed, library ve post detayında tekrar eden kalıbın tek kaynağı.

  Kullanım:
    <ActionBar
      liked={q.isLikedByMe}
      likeCount={q.likesCount}
      commentHref="/post/{q.feedPostId}"
      onLike={() => handleLike(q)}
      onShare={() => share(q)}
    />
    <!-- Kompakt (inceleme kartı altı gibi) -->
    <ActionBar liked={rv.isLikedByMe} likeCount={rv.likesCount} onLike={...} compact />
-->
<script lang="ts">
  interface Props {
    liked:         boolean;
    likeCount?:    number;
    commentHref?:  string;   // yoksa yorum ikonu gösterilmez
    commentCount?: number;
    onLike:        () => void;
    onShare?:      () => void; // yoksa paylaş ikonu gösterilmez
    compact?:      boolean;    // küçük padding/ikon
  }

  let {
    liked        = false,
    likeCount    = 0,
    commentHref  = '',
    commentCount,
    onLike,
    onShare,
    compact      = false,
  }: Props = $props();
</script>

<div class="action-bar" class:compact>
  <!-- Beğeni -->
  <button
    class="act-btn like-btn"
    class:liked
    onclick={onLike}
    aria-label="Beğen"
    aria-pressed={liked}
  >
    {#if liked}
      <svg viewBox="0 0 24 24" fill="currentColor" width={compact ? 16 : 18} height={compact ? 16 : 18}>
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
      </svg>
    {:else}
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width={compact ? 16 : 18} height={compact ? 16 : 18}>
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
      </svg>
    {/if}
    {#if likeCount > 0}
      <span>{likeCount}</span>
    {/if}
  </button>

  <!-- Yorum (commentHref verilmişse) -->
  {#if commentHref}
    <a href={commentHref} class="act-btn comment-btn" aria-label="Yorumlar">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width={compact ? 16 : 18} height={compact ? 16 : 18}>
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      {#if commentCount !== undefined && commentCount > 0}
        <span>{commentCount}</span>
      {/if}
    </a>
  {/if}

  <div class="spacer"></div>

  <!-- Paylaş (onShare verilmişse) -->
  {#if onShare}
    <button class="act-btn share-btn" onclick={onShare} aria-label="Paylaş">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width={compact ? 16 : 18} height={compact ? 16 : 18}>
        <circle cx="18" cy="5"  r="3"/>
        <circle cx="6"  cy="12" r="3"/>
        <circle cx="18" cy="19" r="3"/>
        <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
        <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
      </svg>
    </button>
  {/if}
</div>

<style>
.action-bar {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px 10px;
  border-top: 1px solid var(--divider);
  margin-top: 2px;
}
.action-bar.compact {
  padding: 4px 8px 8px;
}

.act-btn {
  display: flex;
  align-items: center;
  gap: 5px;
  background: none;
  border: none;
  cursor: pointer;
  color: var(--muted);
  font-size: 13px;
  font-family: inherit;
  padding: 6px 10px;
  border-radius: 20px;
  text-decoration: none;
  transition: background 0.15s, color 0.15s;
}
.action-bar.compact .act-btn {
  padding: 4px 8px;
  font-size: 12px;
}
.act-btn:hover { background: var(--surface-var); }

/* Beğeni — aktifken kırmızı */
.like-btn.liked { color: #FF3A5C; }

.spacer { flex: 1; }
</style>
