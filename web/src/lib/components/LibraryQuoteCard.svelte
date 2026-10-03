<!-- Android BookQuoteCard (UnifiedCards.kt) birebir karşılığı -->
<script lang="ts">
  import Avatar   from './Avatar.svelte';
  import { ago }  from '$lib/models/util';
  import type { BookQuote } from '$lib/models/library';

  interface Props {
    quote:        BookQuote;
    currentUid:   string | null;
    language?:    string;
    onLike?:      (q: BookQuote) => void;
    onComment?:   (q: BookQuote) => void;
    onEdit?:      (q: BookQuote) => void;
    onDelete?:    (q: BookQuote) => void;
  }
  let {
    quote, currentUid,
    language  = 'tr',
    onLike, onComment, onEdit, onDelete,
  }: Props = $props();

  const isOwner    = $derived(!!currentUid && currentUid === quote.uid);
  let menuOpen     = $state(false);
  let isExpanded   = $state(false);
  const isLong     = $derived((quote.text?.length ?? 0) > 280);
  const displayText = $derived(
    isLong && !isExpanded
      ? quote.text.slice(0, 280).trimEnd() + '…'
      : quote.text
  );
</script>

<div class="card">
  <!-- ── Üst: Avatar + isim + zaman + ⋮ menü ─────────────────────────── -->
  <div class="card-head">
    <a href="/profile/{quote.uid}" class="avatar-wrap" onclick={(e) => e.stopPropagation()}>
      <Avatar src={quote.userPhotoURL} name={quote.userDisplayName} size={38} />
    </a>
    <div class="user-info">
      <a href="/profile/{quote.uid}" class="user-name" onclick={(e) => e.stopPropagation()}>
        {quote.userDisplayName || '—'}
      </a>
      <span class="user-time">{ago(quote.ts)}</span>
    </div>

    <!-- ⋮ Menü (sadece kendi alıntısına) -->
    {#if isOwner}
      <!-- svelte-ignore a11y_click_events_have_key_events -->
      <div class="menu-wrap" onclick={(e) => e.stopPropagation()}>
        <button class="menu-btn" onclick={() => menuOpen = !menuOpen}>
          <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
            <circle cx="12" cy="5" r="1.8"/>
            <circle cx="12" cy="12" r="1.8"/>
            <circle cx="12" cy="19" r="1.8"/>
          </svg>
        </button>
        {#if menuOpen}
          <div class="dropdown">
            <button class="dropdown-item" onclick={() => { menuOpen=false; onEdit?.(quote); }}>
              ✏️ {language === 'ku' ? 'Biguhere' : 'Düzenle'}
            </button>
            <button class="dropdown-item danger" onclick={() => { menuOpen=false; onDelete?.(quote); }}>
              🗑️ {language === 'ku' ? 'Jê bibe' : 'Sil'}
            </button>
          </div>
        {/if}
      </div>
    {/if}
  </div>

  <!-- ── Orta: Sol amber çizgi + kapak + kitap/yazar + alıntı ─────────── -->
  <div class="quote-box">
    <!-- Sol amber şerit (Android: 3dp amber çizgi) -->
    <div class="amber-bar"></div>

    <div class="quote-content">
      <!-- Kitap bilgisi (kapak + başlık + yazar) -->
      {#if quote.bookTitle || quote.authorName}
        <a
          href={quote.bookId ? `/library/book/${quote.bookId}` : quote.authorId ? `/library/author/${quote.authorId}` : undefined}
          class="book-row"
          onclick={(e) => e.stopPropagation()}
        >
          <!-- Kapak (28×42px) -->
          <div class="cover-wrap">
            {#if quote.coverImg}
              <img src={quote.coverImg} alt={quote.bookTitle} class="cover-img" />
            {:else}
              <svg viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="1.5" width="14" height="14">
                <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
              </svg>
            {/if}
          </div>
          <div class="book-meta">
            {#if quote.bookTitle}
              <span class="book-title">{quote.bookTitle}</span>
            {/if}
            {#if quote.authorName}
              <span class="author-name">{quote.authorName}</span>
            {/if}
          </div>
        </a>
      {/if}

      <!-- Alıntı metni (❝ + italik) -->
      <p class="quote-text">❝ {displayText}</p>
      {#if isLong}
        <button class="expand-btn" onclick={() => isExpanded = !isExpanded}>
          {isExpanded
            ? (language === 'ku' ? 'Kêmtir nîşan bide' : 'Daha az göster')
            : (language === 'ku' ? 'Bêtir bixwîne'     : 'Devamını oku')}
        </button>
      {/if}
    </div>
  </div>

  <!-- ── Alt: Beğeni + yorum + kitap linki ────────────────────────────── -->
  <div class="card-actions">
    <!-- Beğeni -->
    <button
      class="action-btn"
      class:liked={quote.isLikedByMe}
      onclick={() => onLike?.(quote)}
      disabled={!currentUid}
    >
      <svg viewBox="0 0 24 24" width="18" height="18"
        fill={quote.isLikedByMe ? '#ef4444' : 'none'}
        stroke={quote.isLikedByMe ? '#ef4444' : 'currentColor'}
        stroke-width="2">
        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
      </svg>
      {#if (quote.likesCount ?? 0) > 0}
        <span class="count">{quote.likesCount}</span>
      {/if}
    </button>

    <!-- Yorum -->
    {#if onComment}
      <button class="action-btn" onclick={() => onComment?.(quote)}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
      </button>
    {/if}

    <!-- Sağa ittir: Kitap sayfası linki -->
    {#if quote.bookId}
      <a href="/library/book/{quote.bookId}" class="book-link" onclick={(e) => e.stopPropagation()}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14">
          <path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/>
          <path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/>
        </svg>
        {language === 'ku' ? 'Pirtûk' : 'Kitap'}
      </a>
    {/if}
  </div>

  <div class="divider"></div>
</div>

<style>
  .card {
    background: var(--bg, #fff);
    padding: 10px 16px;
  }

  /* ── Üst başlık ─────────────────────────────────────────────────── */
  .card-head {
    display: flex; align-items: center; gap: 10px; margin-bottom: 10px;
  }
  .avatar-wrap { flex-shrink: 0; text-decoration: none; }
  .user-info   { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 1px; }
  .user-name {
    font-size: 14px; font-weight: 600; color: var(--on-bg);
    text-decoration: none; white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .user-name:hover { text-decoration: underline; }
  .user-time { font-size: 12px; color: var(--muted); }

  /* ⋮ Menü */
  .menu-wrap { position: relative; }
  .menu-btn {
    background: none; border: none; cursor: pointer; padding: 4px;
    color: var(--muted); border-radius: 6px; display: flex; align-items: center;
  }
  .menu-btn:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }
  .dropdown {
    position: absolute; right: 0; top: 28px; z-index: 50;
    background: var(--surface); border: 1px solid var(--divider);
    border-radius: 10px; padding: 6px 0; min-width: 140px;
    box-shadow: 0 8px 24px rgba(0,0,0,.15);
  }
  .dropdown-item {
    display: flex; align-items: center; gap: 6px; width: 100%;
    text-align: left; background: none; border: none; padding: 9px 14px;
    font-size: 13px; cursor: pointer; color: var(--on-bg); font-family: inherit;
  }
  .dropdown-item:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }
  .dropdown-item.danger { color: #ef4444; }

  /* ── Alıntı kutusu ──────────────────────────────────────────────── */
  .quote-box {
    display: flex; gap: 10px; margin-bottom: 8px;
    background: var(--surface-var, #f7f4ff);
    border-radius: 10px; padding: 12px; overflow: hidden;
  }
  /* Sol amber şerit */
  .amber-bar {
    width: 3px; border-radius: 2px;
    background: #F59E0B; flex-shrink: 0; align-self: stretch;
  }
  .quote-content { flex: 1; min-width: 0; }

  /* Kitap satırı */
  .book-row {
    display: flex; align-items: center; gap: 6px;
    margin-bottom: 8px; text-decoration: none;
  }
  .cover-wrap {
    width: 28px; height: 42px; border-radius: 3px; flex-shrink: 0;
    background: color-mix(in srgb, #F59E0B 10%, transparent);
    display: flex; align-items: center; justify-content: center; overflow: hidden;
  }
  .cover-img { width: 100%; height: 100%; object-fit: cover; }
  .book-meta { display: flex; flex-direction: column; gap: 2px; min-width: 0; }
  .book-title {
    font-size: 11px; font-weight: 600; color: #F59E0B;
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }
  .author-name {
    font-size: 10px; color: var(--muted);
    white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  }

  /* Alıntı metni */
  .quote-text {
    margin: 0; font-size: 14px; line-height: 1.65;
    color: var(--on-surface); font-style: italic; font-weight: 500;
    white-space: pre-wrap;
  }
  .expand-btn {
    background: none; border: none; cursor: pointer;
    font-size: 12px; font-weight: 600; color: #F59E0B;
    padding: 4px 0; font-family: inherit;
  }

  /* ── Aksiyon çubuğu ─────────────────────────────────────────────── */
  .card-actions {
    display: flex; align-items: center; gap: 4px; margin-bottom: 8px;
  }
  .action-btn {
    display: inline-flex; align-items: center; gap: 4px;
    background: none; border: none; cursor: pointer;
    color: var(--muted); font-size: 13px;
    padding: 4px 8px; border-radius: 6px; transition: color .15s;
    font-family: inherit;
  }
  .action-btn:hover  { color: var(--primary); }
  .action-btn.liked  { color: #ef4444; }
  .action-btn:disabled { opacity: .4; cursor: default; }
  .count { font-size: 13px; }

  .book-link {
    display: inline-flex; align-items: center; gap: 4px;
    margin-left: auto; font-size: 12px; font-weight: 600;
    color: var(--primary); text-decoration: none;
    padding: 4px 8px; border-radius: 6px;
  }
  .book-link:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }

  .divider { height: 0.5px; background: var(--divider); margin: 0 -16px; }
</style>
