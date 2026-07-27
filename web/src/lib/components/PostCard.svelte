<script lang="ts">
  // Android ConnectedPostCard / UnifiedCards karşılığı
  // Props: post + currentUid, event dispatcher'lar
  import { createEventDispatcher } from 'svelte';
  import Avatar     from './Avatar.svelte';
  import LikeButton from './LikeButton.svelte';
  import { ago, shortNum } from '$lib/models/util';
  import type { Post } from '$lib/models/post';

  interface Props {
    post:       Post;
    currentUid: string | null;
  }
  let { post, currentUid }: Props = $props();

  const dispatch = createEventDispatcher<{
    like:    Post;
    save:    Post;
    comment: Post;
    delete:  Post;
    edit:    Post;
  }>();

  let menuOpen   = $state(false);
  let expanded   = $state(false);
  const isLong   = $derived((post.text?.length ?? 0) > 280);
  const isOwner  = $derived(currentUid === post.uid);

  function repostLabel(type: string): string {
    const map: Record<string, string> = {
      serial: '📖 Seri', chapter: '📄 Bölüm',
      book_chapter: '📄 Kitap Bölümü', blog: '📝 Blog',
      kf_lesson: '🇹🇷 Kurdî Ders', grammar: '📚 Dilbilgisi',
      kf_achievement: '🏆 Başarı',
    };
    return map[type] ?? type;
  }
</script>

<!-- svelte-ignore a11y-click-events-have-key-events -->
<article
  class="card"
  onclick={() => window.location.href = '/post/' + post.id}
  role="button"
  tabindex="0"
>
  <!-- BAŞLIK -->
  <div class="card-head">
    <Avatar
      src={post.photoURL}
      name={post.displayName}
      size={40}
      href="/profile/{post.uid}"
    />
    <div class="meta">
      <a href="/profile/{post.uid}" class="display-name"
         onclick={(e) => e.stopPropagation()}>
        {post.displayName || 'Anonim'}
      </a>
      <div class="meta-row">
        {#if post.username}<span class="username">@{post.username}</span><span class="dot">·</span>{/if}
        <span class="time">{ago(post.ts)}</span>
      </div>
    </div>

    <!-- ⋮ Menü -->
    <!-- svelte-ignore a11y-click-events-have-key-events -->
    <div class="menu-wrap" onclick={(e) => e.stopPropagation()}>
      <button
        class="menu-btn"
        onclick={(e) => { e.stopPropagation(); menuOpen = !menuOpen; }}
        aria-label="Seçenekler"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
          <circle cx="12" cy="5" r="1.8"/>
          <circle cx="12" cy="12" r="1.8"/>
          <circle cx="12" cy="19" r="1.8"/>
        </svg>
      </button>
      {#if menuOpen}
        <div class="dropdown">
          {#if isOwner}
            <button class="dropdown-item" onclick={() => { menuOpen=false; dispatch('edit', post); }}>
              ✏️ Düzenle
            </button>
            <button class="dropdown-item danger" onclick={() => { menuOpen=false; dispatch('delete', post); }}>
              🗑️ Sil
            </button>
          {:else}
            <button class="dropdown-item danger" onclick={() => menuOpen=false}>
              🚩 Şikayet Et
            </button>
          {/if}
          <hr class="dropdown-sep" />
          <button class="dropdown-item" onclick={() => {
            menuOpen=false;
            const url = window.location.origin + '/post/' + post.id;
            if (navigator.share) navigator.share({ title: post.displayName, url });
            else { navigator.clipboard.writeText(url); }
          }}>
            🔗 Bağlantıyı Kopyala
          </button>
        </div>
      {/if}
    </div>
  </div>

  <!-- İÇERİK -->
  <div class="card-body">

    <!-- Alıntı kutusu (Android QuoteDialog görünümü) -->
    {#if post.quoteText}
      <!-- svelte-ignore a11y-click-events-have-key-events -->
      <div class="quote-card" onclick={(e) => e.stopPropagation()}>
        <span class="quote-mark">❝</span>
        <div class="quote-inner">
          <p class="quote-text">{post.quoteText}</p>
          {#if post.bookName || post.authorName}
            <div class="quote-source">
              {#if post.coverImg}
                <img src={post.coverImg} alt={post.bookName} class="quote-cover-img" />
              {/if}
              <div>
                {#if post.bookName}<span class="quote-book">{post.bookName}</span>{/if}
                {#if post.authorName}<span class="quote-author">{post.authorName}</span>{/if}
              </div>
            </div>
          {/if}
        </div>
      </div>
    {/if}

    {#if post.category}
      <span class="category-chip">{post.category}</span>
    {/if}
    {#if post.title}
      <h2 class="post-title">{post.title}</h2>
    {/if}
    {#if post.text}
      <p class="post-text" class:clamped={isLong && !expanded}>{post.text}</p>
      {#if isLong}
        <button class="read-more" onclick={(e) => { e.stopPropagation(); expanded = !expanded; }}>
          {expanded ? 'Daha az' : 'Devamını oku'}
        </button>
      {/if}
    {/if}

    {#if post.imageURL || post.imgUrl}
      <img src={post.imageURL || post.imgUrl} alt="gönderi görseli" class="post-img" />
    {/if}

    <!-- Repost embed -->
    {#if post.repostType && post.repostType !== 'kf_achievement'}
      <!-- svelte-ignore a11y-click-events-have-key-events -->
      <div
        class="repost-embed"
        onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + post.repostId; }}
        role="button"
        tabindex="0"
      >
        <div class="repost-label">{repostLabel(post.repostType)}</div>
        {#if post.repostAuthor}
          <div class="repost-author-row">
            <Avatar src={post.repostAuthorPhoto} name={post.repostAuthor} size={22} />
            <span class="repost-author-name">{post.repostAuthor}</span>
          </div>
        {/if}
        {#if post.repostText}
          <p class="repost-text">{post.repostText.slice(0, 200)}{post.repostText.length > 200 ? '…' : ''}</p>
        {/if}
        {#if post.repostTitle}
          <p class="repost-title">{post.repostTitle}</p>
        {/if}
      </div>
    {/if}
  </div>

  <!-- AKSİYON ÇUBUĞU -->
  <!-- svelte-ignore a11y-click-events-have-key-events -->
  <div class="card-actions" onclick={(e) => e.stopPropagation()}>
    <LikeButton
      liked={post.isLikedByMe ?? false}
      count={post.likesCount ?? 0}
      onclick={() => dispatch('like', post)}
    />

    <button class="action-btn" onclick={() => dispatch('comment', post)} aria-label="Yorum yap">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      <span>{shortNum(post.commentsCount ?? 0)}</span>
    </button>

    <button
      class="action-btn"
      class:saved={post.isSavedByMe}
      onclick={() => dispatch('save', post)}
      aria-label={post.isSavedByMe ? 'Kayıttan çıkar' : 'Kaydet'}
    >
      <svg viewBox="0 0 24 24" fill={post.isSavedByMe ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2" width="18" height="18">
        <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
      </svg>
    </button>

    <button class="action-btn" onclick={() => {
      const url = window.location.origin + '/post/' + post.id;
      if (navigator.share) navigator.share({ title: post.displayName, url });
      else navigator.clipboard.writeText(url);
    }} aria-label="Paylaş">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
        <circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/>
        <line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/>
        <line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/>
      </svg>
    </button>
  </div>
</article>

<style>
  .card {
    background: var(--card-bg, #fff);
    border-radius: 14px;
    padding: 14px;
    margin-bottom: 10px;
    cursor: pointer;
    transition: box-shadow .15s;
    box-shadow: 0 1px 4px rgba(0,0,0,.06);
  }
  .card:hover { box-shadow: 0 3px 12px rgba(0,0,0,.1); }

  /* Başlık */
  .card-head { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 10px; }
  .meta { flex: 1; min-width: 0; }
  .display-name {
    font-weight: 700; font-size: 14px; color: inherit;
    text-decoration: none; display: block;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .display-name:hover { text-decoration: underline; }
  .meta-row { display: flex; align-items: center; gap: 4px; font-size: 12px; color: #888; margin-top: 1px; }
  .username { color: #999; }
  .dot { color: #ccc; }

  /* Menü */
  .menu-wrap { position: relative; }
  .menu-btn {
    background: none; border: none; cursor: pointer;
    padding: 4px; border-radius: 6px; color: #999;
    display: flex; align-items: center;
  }
  .menu-btn:hover { background: #f0ebf9; }
  .dropdown {
    position: absolute; right: 0; top: 28px; z-index: 50;
    background: #fff; border: 1px solid #e8e0f5;
    border-radius: 10px; padding: 6px 0; min-width: 180px;
    box-shadow: 0 8px 24px rgba(0,0,0,.12);
  }
  .dropdown-item {
    display: flex; align-items: center; gap: 8px;
    width: 100%; text-align: left; background: none; border: none;
    padding: 9px 14px; font-size: 13px; cursor: pointer; color: #333;
  }
  .dropdown-item:hover { background: #f5f0fc; }
  .dropdown-item.danger { color: #e03; }
  .dropdown-sep { border: none; border-top: 1px solid #f0ebf9; margin: 4px 0; }

  /* İçerik */
  .card-body { margin-bottom: 10px; }
  .category-chip {
    display: inline-block; font-size: 11px; font-weight: 600;
    background: #f0ebf9; color: #6b4fa0;
    padding: 2px 8px; border-radius: 20px; margin-bottom: 6px;
  }
  .post-title { font-size: 16px; font-weight: 700; margin: 0 0 6px; }
  .post-text { font-size: 14px; line-height: 1.6; margin: 0; white-space: pre-wrap; }
  .post-text.clamped { display: -webkit-box; -webkit-line-clamp: 5; -webkit-box-orient: vertical; overflow: hidden; }
  .read-more {
    background: none; border: none; color: #6b4fa0; font-size: 13px;
    cursor: pointer; padding: 0; margin-top: 4px; font-weight: 600;
  }
  .post-img { width: 100%; border-radius: 10px; margin-top: 10px; object-fit: cover; max-height: 360px; }

  /* Alıntı kutusu */
  .quote-card {
    background: linear-gradient(135deg, #f8f4ff, #f0ebfb);
    border-left: 3px solid #7c4dff; border-radius: 10px;
    padding: 12px 14px; margin-bottom: 10px;
    display: flex; gap: 8px;
  }
  .quote-mark { font-size: 24px; color: #7c4dff; line-height: 1; flex-shrink: 0; }
  .quote-inner { flex: 1; min-width: 0; }
  .quote-text { font-size: 14px; font-style: italic; margin: 0 0 8px; line-height: 1.6; }
  .quote-source { display: flex; align-items: center; gap: 8px; }
  .quote-cover-img { width: 32px; height: 44px; border-radius: 4px; object-fit: cover; flex-shrink: 0; }
  .quote-book { font-size: 12px; font-weight: 700; display: block; color: #333; }
  .quote-author { font-size: 11px; color: #888; display: block; }

  /* Repost embed */
  .repost-embed {
    border: 1px solid #e8e0f5; border-radius: 10px;
    padding: 10px 12px; margin-top: 8px; cursor: pointer;
  }
  .repost-embed:hover { background: #faf8ff; }
  .repost-label { font-size: 11px; font-weight: 700; color: #7c4dff; margin-bottom: 6px; }
  .repost-author-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
  .repost-author-name { font-size: 12px; font-weight: 600; }
  .repost-text { font-size: 13px; color: #555; margin: 0; }
  .repost-title { font-size: 13px; font-weight: 700; margin: 4px 0 0; }

  /* Aksiyonlar */
  .card-actions { display: flex; align-items: center; gap: 4px; }
  .action-btn {
    display: inline-flex; align-items: center; gap: 4px;
    background: none; border: none; cursor: pointer;
    color: #888; font-size: 13px; padding: 4px 8px; border-radius: 6px;
    transition: color .15s;
  }
  .action-btn:hover { color: #6b4fa0; }
  .action-btn.saved { color: #6b4fa0; }
</style>
