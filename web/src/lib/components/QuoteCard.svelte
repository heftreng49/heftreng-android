<!-- Android QuoteCompose.kt → QuoteCard Svelte karşılığı -->
<!-- Amber/mor gradient arka plan, alıntı ikonu, kitap kapağı -->
<script lang="ts">
  interface Props {
    quoteText:   string;
    bookName?:   string;
    authorName?: string;
    coverImg?:   string;
    language?:   string;
    /** true → detail ekranı, kırpma yok (Android expandByDefault) */
    expanded?:   boolean;
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
    onTapBook,
    onTapAuthor,
  }: Props = $props();

  const isRtl = language === 'ku' || language === 'ar';
</script>

{#if quoteText}
<div class="quote-card" class:rtl={isRtl}>
  <!-- Alıntı ikonu -->
  <span class="quote-icon" aria-hidden="true">"</span>

  <!-- Alıntı metni -->
  <p class="quote-text" class:clamped={!expanded}>
    {quoteText}
  </p>

  <!-- Kitap bilgisi -->
  {#if bookName || authorName}
  <div class="quote-meta">
    {#if coverImg}
      <img src={coverImg} alt={bookName} class="cover" />
    {:else}
      <div class="cover-placeholder" aria-hidden="true">📖</div>
    {/if}
    <div class="meta-text">
      {#if bookName}
        <!-- svelte-ignore a11y-click-events-have-key-events -->
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <span
          class="book-name"
          class:clickable={!!onTapBook}
          onclick={() => onTapBook?.(bookName)}
        >{bookName}</span>
      {/if}
      {#if authorName}
        <!-- svelte-ignore a11y-click-events-have-key-events -->
        <!-- svelte-ignore a11y-no-static-element-interactions -->
        <span
          class="author-name"
          class:clickable={!!onTapAuthor}
          onclick={() => onTapAuthor?.(authorName)}
        >{authorName}</span>
      {/if}
    </div>
  </div>
  {/if}
</div>
{/if}

<style>
.quote-card {
  position: relative;
  border-radius: 14px;
  padding: 14px 16px 12px;
  background: linear-gradient(
    135deg,
    rgba(251, 191, 36, 0.08) 0%,
    rgba(155, 114, 245, 0.06) 100%
  );
  border: 1px solid;
  border-color: rgba(251, 191, 36, 0.35);
  overflow: hidden;
}
.quote-card.rtl { direction: rtl; }

.quote-icon {
  position: absolute;
  top: 6px;
  left: 12px;
  font-size: 2.4rem;
  line-height: 1;
  color: rgba(251, 191, 36, 0.35);
  font-family: Georgia, serif;
  pointer-events: none;
}

.quote-text {
  margin: 0 0 10px;
  font-size: 0.92rem;
  line-height: 1.6;
  color: var(--color-text, #1a1a1a);
  padding-top: 8px;
  font-style: italic;
}
.quote-text.clamped {
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.quote-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 8px;
}

.cover {
  width: 38px;
  height: 52px;
  object-fit: cover;
  border-radius: 5px;
  flex-shrink: 0;
}
.cover-placeholder {
  width: 38px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(155, 114, 245, 0.12);
  border-radius: 5px;
  font-size: 1.4rem;
  flex-shrink: 0;
}

.meta-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.book-name {
  font-size: 0.8rem;
  font-weight: 600;
  color: var(--color-text, #1a1a1a);
}
.author-name {
  font-size: 0.75rem;
  color: #888;
}
.clickable {
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}
</style>
