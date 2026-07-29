<!-- Android QuoteCompose.kt → QuoteCard web karşılığı -->
<!-- Tüm sayfalar (feed, profil, kütüphane, kitap, yazar) bu bileşeni kullanır -->
<script lang="ts">
  interface Props {
    quoteText:   string;
    bookName?:   string;
    authorName?: string;
    coverImg?:   string;
    language?:   string;
    /** true → detail ekranı, kırpma yok (Android expandByDefault) */
    expanded?:   boolean;
    /** Kitap sayfası ID'si — verilirse kitap adı tıklanabilir link olur */
    bookId?:     string;
    /** Yazar sayfası ID'si — verilirse yazar adı tıklanabilir link olur */
    authorId?:   string;
    /** Callback alternatifi (ID yoksa) */
    onTapBook?:  (name: string) => void;
    onTapAuthor?:(name: string) => void;
  }

  let {
    quoteText,
    bookName   = '',
    authorName = '',
    coverImg   = '',
    language   = 'tr',
    expanded   = false,
    bookId     = '',
    authorId   = '',
    onTapBook,
    onTapAuthor,
  }: Props = $props();

  const isRtl = $derived(language === 'ku' || language === 'ar');

  // Android: isLong = quoteText.length > 280
  const isLong    = $derived(quoteText.length > 280);
  let isExpanded  = $state(false);
  $effect(() => { isExpanded = expanded; });

  const displayText = $derived(
    (!isLong || isExpanded || expanded) ? quoteText : quoteText.slice(0, 280).trimEnd() + '…'
  );

  // Kitap linki — önce bookId ile direkt sayfa, yoksa callback
  function handleBookTap(e: MouseEvent) {
    if (bookId) return; // <a> etiketi zaten navigate eder
    e.preventDefault();
    onTapBook?.(bookName);
  }

  function handleAuthorTap(e: MouseEvent) {
    if (authorId) return;
    e.preventDefault();
    onTapAuthor?.(authorName);
  }
</script>

{#if quoteText}
<div class="quote-card" class:rtl={isRtl}>

  <!-- Büyük tırnak ikonu (Android'deki "\u201c" 56sp konumu) -->
  <span class="quote-icon" aria-hidden="true">❝</span>

  <!-- Alıntı metni -->
  <div class="quote-inner">
    <p class="quote-text">{displayText}</p>

    <!-- Devamını oku (Android: isLong && !expandByDefault) -->
    {#if isLong && !expanded}
      <button class="expand-btn" onclick={() => isExpanded = !isExpanded}>
        {isExpanded
          ? (language === 'ku' ? 'Kêmtir nîşan bide' : 'Daha az göster')
          : (language === 'ku' ? 'Bêtir bixwîne'     : 'Devamını oku')}
      </button>
    {/if}

    <!-- Kitap + yazar bilgisi -->
    {#if bookName || authorName}
      <div class="quote-meta">

        <!-- Kapak (Android: 28x42dp Box) -->
        {#if bookId}
          <a href="/library/book/{bookId}" class="cover-wrap" onclick={(e) => e.stopPropagation()}>
            {#if coverImg}
              <img src={coverImg} alt={bookName} class="cover-img" />
            {:else}
              <div class="cover-ph">
                <svg viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="1.5" width="14" height="14">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                </svg>
              </div>
            {/if}
          </a>
        {:else if coverImg || !bookName}
          <!-- svelte-ignore a11y_click_events_have_key_events -->
          <!-- svelte-ignore a11y_no_static_element_interactions -->
          <div class="cover-wrap" onclick={(e) => { e.stopPropagation(); handleBookTap(e); }} style={onTapBook ? 'cursor:pointer' : ''}>
            {#if coverImg}
              <img src={coverImg} alt={bookName} class="cover-img" />
            {:else}
              <div class="cover-ph">
                <svg viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="1.5" width="14" height="14">
                  <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
                  <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
                </svg>
              </div>
            {/if}
          </div>
        {:else}
          <div class="cover-ph">
            <svg viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="1.5" width="14" height="14">
              <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
              <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/>
            </svg>
          </div>
        {/if}

        <!-- Kitap adı + yazar adı -->
        <div class="meta-text">
          {#if bookName}
            {#if bookId}
              <a href="/library/book/{bookId}" class="book-name" onclick={(e) => e.stopPropagation()}>{bookName}</a>
            {:else if onTapBook}
              <button class="book-name book-btn" onclick={(e) => { e.stopPropagation(); onTapBook?.(bookName); }}>{bookName}</button>
            {:else}
              <span class="book-name">{bookName}</span>
            {/if}
          {/if}

          {#if authorName}
            {#if authorId}
              <a href="/library/author/{authorId}" class="author-name-text" onclick={(e) => e.stopPropagation()}>{authorName}</a>
            {:else if onTapAuthor}
              <button class="author-name-text author-btn" onclick={(e) => { e.stopPropagation(); onTapAuthor?.(authorName); }}>{authorName}</button>
            {:else}
              <span class="author-name-text">{authorName}</span>
            {/if}
          {/if}
        </div>
      </div>
    {/if}
  </div>
</div>
{/if}

<style>
/* Android QuoteCompose.kt birebir karşılığı */
.quote-card {
  position: relative;
  border-radius: 14px;
  padding: 14px 14px 12px;
  background: linear-gradient(
    135deg,
    color-mix(in srgb, #F59E0B 8%, transparent),
    color-mix(in srgb, #9B72F5 6%, transparent)
  );
  border: 1px solid color-mix(in srgb, #F59E0B 35%, transparent);
  overflow: hidden;
  margin: 0;
}
.quote-card.rtl { direction: rtl; }

/* Android: Text("\u201c", fontSize=56sp, color=Amber.copy(alpha=0.12)) */
.quote-icon {
  position: absolute;
  top: -6px;
  left: 6px;
  font-size: 52px;
  line-height: 1;
  color: color-mix(in srgb, #F59E0B 14%, transparent);
  font-family: Georgia, serif;
  font-weight: 900;
  pointer-events: none;
  user-select: none;
}

.quote-inner {
  padding-left: 8px;
  position: relative;
}

/* Android: fontSize=14sp, fontStyle=Italic, fontWeight=Medium */
.quote-text {
  margin: 0 0 8px;
  font-size: 14px;
  line-height: 1.65;
  color: var(--on-surface);
  font-style: italic;
  font-weight: 500;
  white-space: pre-wrap;
}

/* Android: "Devamını oku" / "Daha az göster" */
.expand-btn {
  display: block;
  background: none;
  border: none;
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
  color: #F59E0B;
  padding: 0;
  margin-bottom: 8px;
  font-family: inherit;
}

/* Android: Row(verticalAlignment=Center, horizontalArrangement=spacedBy(8dp)) */
.quote-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}

/* Android: Box(28x42dp, clip=RoundedCornerShape(3dp), background=Amber.copy(0.10)) */
.cover-wrap {
  width: 28px;
  height: 42px;
  border-radius: 3px;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
  text-decoration: none;
}
.cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.cover-ph {
  width: 28px;
  height: 42px;
  border-radius: 3px;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.meta-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

/* Android: color=Amber, fontSize=11sp, fontWeight=SemiBold, maxLines=1 */
.book-name {
  font-size: 11px;
  font-weight: 600;
  color: #F59E0B;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-decoration: none;
  display: block;
}
a.book-name:hover { text-decoration: underline; }

.book-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
  text-align: left;
}

/* Android: color=Muted, fontSize=10sp, maxLines=1 */
.author-name-text {
  font-size: 10px;
  color: var(--muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  display: block;
  text-decoration: none;
}
a.author-name-text:hover { text-decoration: underline; }

.author-btn {
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
  font-family: inherit;
  text-align: left;
}
</style>
