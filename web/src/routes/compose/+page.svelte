<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/stores";
  import { supabase } from "$lib/supabase/config";
  // ── Yeni store (lib/stores/auth) ──────────────────────────────────────────
  import { currentUser, authLoading } from "$lib/stores/auth";
  // ── Servis katmanı (doğrudan DB çağrısı yok) ─────────────────────────────
  import {
    loadPost,
    uploadImage,
    createPost,
    createQuote,
    updatePost,
  } from "$lib/services/compose.service";

  // ── Mod ──────────────────────────────────────────────────────
  let mode        = $state<"post" | "quote">("post");
  let editPostId  = $state<string | null>(null);
  let isEditMode  = $state(false);
  let loadingPost = $state(false);
  let submitting  = $state(false);
  let error       = $state("");

  // ── Normal gönderi ───────────────────────────────────────────
  let text      = $state("");
  let title     = $state("");
  let category  = $state("");
  let imageFile = $state<File | null>(null);
  let imagePreview = $state<string | null>(null);

  // Android postTopics listesi (aynı sıra)
  const topics: { key: string; label: string }[] = [
    { key: "genel",    label: "Genel"    },
    { key: "kitap",    label: "Kitap"    },
    { key: "alinti",   label: "Alıntı"   },
    { key: "soru",     label: "Soru"     },
    { key: "tartisma", label: "Tartışma" },
    { key: "siir",     label: "Şiir"     },
  ];

  // ── Alıntı (QuoteDialog) ─────────────────────────────────────
  let quoteText   = $state("");
  let quoteTitle  = $state("");
  let quoteBook   = $state("");
  let quoteAuthor = $state("");
  let quoteCover  = $state("");
  let quoteBookId = $state("");

  // Kitap autocomplete
  let bookQuery    = $state("");
  let bookResults  = $state<any[]>([]);
  let showBookDrop = $state(false);
  let bookLoading  = $state(false);
  let bookLinked   = $state(false);

  // Yazar autocomplete
  let authorQuery    = $state("");
  let authorResults  = $state<any[]>([]);
  let showAuthorDrop = $state(false);
  let authorLoading  = $state(false);

  // ── Init ─────────────────────────────────────────────────────
  onMount(() => {
    const unsub = authLoading.subscribe(loading => {
      if (!loading && !$currentUser) goto("/login");
    });
    const editId = $page.url.searchParams.get("edit");
    const type   = $page.url.searchParams.get("type");
    if (editId) { editPostId = editId; isEditMode = true; loadExistingPost(editId); }
    if (type === "quote") mode = "quote";
    return unsub;
  });

  async function loadExistingPost(id: string) {
    loadingPost = true;
    try {
      const d = await loadPost(id);
      if (!d) { error = "Gönderi bulunamadı."; return; }
      if ($currentUser && d.uid !== $currentUser.uid) { error = "Yetki yok."; return; }
      if (d.quoteText) {
        mode        = "quote";
        quoteText   = d.quoteText  ?? "";
        quoteTitle  = d.title      ?? "";
        quoteBook   = d.bookName   ?? "";
        quoteAuthor = d.authorName ?? "";
        quoteCover  = d.coverImg   ?? "";
        bookQuery   = d.bookName   ?? "";
        authorQuery = d.authorName ?? "";
      } else {
        mode     = "post";
        title    = d.title    ?? "";
        text     = d.text     ?? "";
        category = d.category ?? "";
      }
    } catch(e) { error = "Yüklenemedi."; }
    finally { loadingPost = false; }
  }

  // ── Kitap arama ──────────────────────────────────────────────
  let bookTimer: any;
  function onBookInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    bookQuery = val; quoteBook = val;
    bookLinked = false; quoteCover = ""; quoteBookId = "";
    clearTimeout(bookTimer);
    if (!val.trim()) { bookResults = []; showBookDrop = false; return; }
    bookTimer = setTimeout(async () => {
      bookLoading = true;
      try {
        const { data } = await supabase
          .from("library_books")
          .select("id, title, author_name, cover_img")
          .ilike("title", `%${val.trim()}%`)
          .limit(8);
        bookResults = data ?? [];
        showBookDrop = bookResults.length > 0;
      } catch(e) { console.error("Kitap arama:", e); }
      finally { bookLoading = false; }
    }, 250);
  }

  function selectBook(b: any) {
    quoteBook   = b.title;
    bookQuery   = b.title;
    quoteBookId = b.id    ?? "";
    quoteCover  = b.cover_img ?? "";
    if (!quoteAuthor && b.author_name) { quoteAuthor = b.author_name; authorQuery = b.author_name; }
    bookResults = []; showBookDrop = false; bookLinked = true;
  }

  function clearBook() {
    quoteBook = ""; bookQuery = ""; quoteBookId = ""; quoteCover = "";
    bookLinked = false; bookResults = []; showBookDrop = false;
  }

  // ── Yazar arama ──────────────────────────────────────────────
  let authorTimer: any;
  function onAuthorInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    authorQuery = val; quoteAuthor = val;
    clearTimeout(authorTimer);
    if (!val.trim()) { authorResults = []; showAuthorDrop = false; return; }
    authorTimer = setTimeout(async () => {
      authorLoading = true;
      try {
        const { data, error: err } = await supabase
          .from("authors")
          .select("id, name")
          .ilike("name", `%${val.trim()}%`)
          .limit(8);
        if (err) {
          const { data: feedData } = await supabase
            .from("library_books")
            .select("author_name")
            .ilike("author_name", `%${val.trim()}%`)
            .limit(8);
          const unique = [...new Set((feedData ?? []).map((r:any) => r.author_name).filter(Boolean))];
          authorResults = unique.map((name: string) => ({ id: name, name }));
        } else {
          authorResults = data ?? [];
        }
        showAuthorDrop = authorResults.length > 0;
      } catch(e) { console.error("Yazar arama:", e); }
      finally { authorLoading = false; }
    }, 250);
  }

  function selectAuthor(a: any) {
    quoteAuthor = a.name;
    authorQuery = a.name;
    authorResults = []; showAuthorDrop = false;
  }

  function clearAuthor() {
    quoteAuthor = ""; authorQuery = ""; authorResults = []; showAuthorDrop = false;
  }

  // ── Görsel ───────────────────────────────────────────────────
  function onFileChange(e: Event) {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (!f) return;
    imageFile = f; imagePreview = URL.createObjectURL(f);
  }
  function removeImage() { imageFile = null; imagePreview = null; }

  // ── Paylaş / Kaydet ──────────────────────────────────────────
  async function submit() {
    if (!$currentUser) return;
    submitting = true; error = "";
    try {
      if (isEditMode && editPostId) {
        if (mode === "quote") {
          await updatePost(editPostId, {
            quoteText: quoteText.trim(),
            bookName:  quoteBook.trim(),
            authorName: quoteAuthor.trim(),
          });
        } else {
          await updatePost(editPostId, { text: text.trim(), title: title.trim() });
        }
      } else {
        if (mode === "quote") {
          await createQuote({
            uid:         $currentUser.uid,
            displayName: $currentUser.displayName ?? "",
            photoURL:    $currentUser.photoURL    ?? "",
            title:       quoteTitle.trim(),
            quoteText:   quoteText.trim(),
            bookName:    quoteBook.trim(),
            authorName:  quoteAuthor.trim(),
            coverImg:    quoteCover,
            bookId:      quoteBookId,
          });
        } else {
          let imageUrl = "";
          if (imageFile) imageUrl = await uploadImage(imageFile, $currentUser.uid);
          await createPost({
            uid:         $currentUser.uid,
            displayName: $currentUser.displayName ?? "",
            photoURL:    $currentUser.photoURL    ?? "",
            title:       title.trim(),
            text:        text.trim(),
            category,
            imageUrl,
          });
        }
      }
      goto("/feed");
    } catch(e: any) { error = "Hata oluştu, tekrar dene."; console.error(e); }
    finally { submitting = false; }
  }

  let canPost  = $derived(text.trim().length > 0 || imageFile !== null);
  let canQuote = $derived(quoteText.trim().length > 0 && quoteBook.trim().length > 0 && quoteAuthor.trim().length > 0);
  let canSubmit = $derived(!submitting && (mode === "quote" ? canQuote : canPost));

  let bookNotFound   = $derived(bookQuery.length > 1 && !bookLoading && !bookLinked && bookResults.length === 0 && !showBookDrop);
  let authorNotFound = $derived(authorQuery.length > 1 && !authorLoading && authorResults.length === 0 && !showAuthorDrop);
</script>

<!-- ── Normal Gönderi ──────────────────────────────────────── -->
{#if mode === "post"}
<main class="wrap post-wrap">

  <!-- Üst bar (Android compose bar) -->
  <div class="post-topbar">
    <button class="topbar-close" onclick={() => goto("/feed")}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="22" height="22"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
    </button>

    {#if $currentUser?.photoURL}
      <img src={$currentUser.photoURL} alt="" class="topbar-av"/>
    {:else if $currentUser}
      <div class="topbar-av-ph">{($currentUser.displayName ?? "?")[0].toUpperCase()}</div>
    {/if}

    <div class="topbar-spacer"></div>

    <button class="share-pill" onclick={submit} disabled={!canSubmit}>
      {submitting ? "..." : isEditMode ? "Kaydet" : "Paylaş"}
    </button>
  </div>

  {#if loadingPost}
    <div class="loading"><div class="spinner"></div></div>
  {:else}
    <!-- İçerik -->
    <div class="post-body">
      <input class="post-title" placeholder="Başlık (opsiyonel)" bind:value={title} maxlength={120}/>
      <textarea class="post-text" placeholder="Ne düşünüyorsun?" bind:value={text} maxlength={1000} rows={7}></textarea>

      {#if imagePreview}
        <div class="img-wrap">
          <img src={imagePreview} alt="" class="img-prev"/>
          <button class="img-rm" onclick={removeImage}>✕</button>
        </div>
      {/if}
    </div>

    <!-- Kategoriler (yatay scroll) -->
    <div class="topics-row">
      {#each topics as t}
        <button
          class="topic-chip"
          class:selected={category === t.key}
          onclick={() => category = category === t.key ? "" : t.key}
        >{t.label}</button>
      {/each}
    </div>

    <!-- Alt araç çubuğu -->
    <div class="post-toolbar">
      <button class="tool-btn" onclick={() => { mode = "quote"; }}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
      </button>
      <label class="tool-btn">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
        <input type="file" accept="image/*" onchange={onFileChange} style="display:none"/>
      </label>
      <span class="char-count" class:warn={text.length > 900}>{text.length}/1000</span>
    </div>

    {#if error}<p class="error">{error}</p>{/if}
  {/if}
</main>

<!-- ── Alıntı Ekranı (Android QuoteDialog — tam ekran) ─────── -->
{:else}
<main class="wrap quote-wrap">

  <!-- Üst bar: geri ok + "Alıntı Ekle" + Paylaş -->
  <div class="quote-topbar">
    <button class="back-btn" onclick={() => { if (isEditMode) goto("/feed"); else mode = "post"; }}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" width="22" height="22"><polyline points="15 18 9 12 15 6"/></svg>
    </button>
    <div class="quote-topbar-title">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18" style="color:#6366f1"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
      <span>{isEditMode ? "Alıntıyı Düzenle" : "Alıntı Ekle"}</span>
    </div>
    <button class="share-text-btn" onclick={submit} disabled={!canSubmit}>
      {submitting ? "..." : isEditMode ? "Kaydet" : "Paylaş"}
    </button>
  </div>

  {#if loadingPost}
    <div class="loading"><div class="spinner"></div></div>
  {:else}
  <div class="quote-body">

    <!-- Başlık -->
    <input class="q-title" placeholder="Başlık (opsiyonel)" bind:value={quoteTitle} maxlength={120}/>

    <!-- Alıntı metni (OutlinedTextField) -->
    <div class="q-field">
      <textarea
        class="q-outlined"
        placeholder="ALINTI METNİ *"
        bind:value={quoteText}
        rows={5}
      ></textarea>
    </div>

    <!-- Kitap adı -->
    <div class="q-field">
      <div class="q-outlined-wrap">
        <div class="q-leading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        </div>
        <input
          class="q-outlined-input"
          placeholder="KİTAP ADI"
          value={bookQuery}
          oninput={onBookInput}
          onblur={() => setTimeout(() => { showBookDrop = false; }, 200)}
          onfocus={() => { if (bookResults.length > 0) showBookDrop = true; }}
        />
        {#if bookLoading}
          <div class="q-trailing"><div class="spinner small"></div></div>
        {:else if quoteBook}
          <button class="q-trailing-btn" onclick={clearBook}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        {/if}

        {#if showBookDrop && bookResults.length > 0}
          <div class="q-dropdown">
            {#each bookResults as b (b.id)}
              <button class="q-drop-row" onclick={() => selectBook(b)}>
                {#if b.cover_img}
                  <img src={b.cover_img} alt={b.title} class="drop-thumb"/>
                {:else}
                  <div class="drop-thumb no-img">
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="11" height="11"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                  </div>
                {/if}
                <div class="drop-info">
                  <span class="drop-title">{b.title}</span>
                  {#if b.author_name}<span class="drop-sub">{b.author_name}</span>{/if}
                </div>
                <span class="drop-count-badge">kütüphane</span>
              </button>
            {/each}
          </div>
        {/if}
      </div>

      {#if bookNotFound}
        <div class="q-hint">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          "<strong>{quoteBook}</strong>" kütüphanede yok — yine de paylaşabilirsin.
        </div>
      {/if}
    </div>

    <!-- Yazar adı -->
    <div class="q-field">
      <div class="q-outlined-wrap">
        <div class="q-leading">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
        </div>
        <input
          class="q-outlined-input"
          placeholder="YAZAR"
          value={authorQuery}
          oninput={onAuthorInput}
          onblur={() => setTimeout(() => { showAuthorDrop = false; }, 200)}
          onfocus={() => { if (authorResults.length > 0) showAuthorDrop = true; }}
        />
        {#if authorLoading}
          <div class="q-trailing"><div class="spinner small"></div></div>
        {:else if quoteAuthor}
          <button class="q-trailing-btn" onclick={clearAuthor}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>
          </button>
        {/if}

        {#if showAuthorDrop && authorResults.length > 0}
          <div class="q-dropdown">
            {#each authorResults as a (a.id)}
              <button class="q-drop-row" onclick={() => selectAuthor(a)}>
                <div class="drop-thumb author-thumb">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="11" height="11"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                </div>
                <div class="drop-info">
                  <span class="drop-title">{a.name}</span>
                </div>
              </button>
            {/each}
          </div>
        {/if}
      </div>

      {#if authorNotFound}
        <div class="q-hint">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
          "<strong>{quoteAuthor}</strong>" yazarı bulunamadı — yine de paylaşabilirsin.
        </div>
      {/if}
    </div>

    <!-- Önizleme (QuoteCard) -->
    {#if quoteText || quoteBook || quoteAuthor}
      <div class="q-preview-wrap">
        <p class="q-preview-label">Önizleme</p>
        <div class="quote-card">
          <span class="quote-bg">"</span>
          <p class="qc-text">{quoteText || "Alıntı metni…"}</p>
          {#if quoteBook || quoteAuthor}
            <div class="qc-source">
              {#if quoteCover}
                <img src={quoteCover} alt={quoteBook} class="qc-cover"/>
              {:else}
                <div class="qc-cover no-img">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="11" height="11"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                </div>
              {/if}
              <div>
                {#if quoteBook}<span class="qc-book">{quoteBook}</span>{/if}
                {#if quoteAuthor}<span class="qc-author">{quoteAuthor}</span>{/if}
              </div>
            </div>
          {/if}
        </div>
      </div>
    {/if}

    {#if error}<p class="error">{error}</p>{/if}
    <div style="height:40px"></div>
  </div>
  {/if}
</main>
{/if}

<style>
/* ── Genel ──────────────────────────────────────────────────── */
.wrap { max-width: 600px; margin: 0 auto; background: var(--surface); min-height: 100dvh; }
.loading { display: flex; justify-content: center; padding: 40px; }
.error { color: #ef4444; font-size: 13px; padding: 0 16px; }

/* ── Normal Gönderi ─────────────────────────────────────────── */
.post-wrap { display: flex; flex-direction: column; }

.post-topbar {
  display: flex; align-items: center; gap: 10px;
  padding: 10px 14px; border-bottom: 1px solid var(--divider);
  position: sticky; top: 0; background: var(--surface); z-index: 10;
}
.topbar-close {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  border-radius: 50%; background: none; border: none; cursor: pointer; color: var(--on-bg);
}
.topbar-close:hover { background: var(--surface-var); }
.topbar-av, .topbar-av-ph {
  width: 34px; height: 34px; border-radius: 50%; object-fit: cover; flex-shrink: 0;
}
.topbar-av-ph {
  background: var(--primary); color: #fff;
  display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px;
}
.topbar-spacer { flex: 1; }
.share-pill {
  padding: 8px 22px; background: var(--primary); color: #fff;
  border-radius: 24px; font-size: 15px; font-weight: 600; border: none;
  cursor: pointer; font-family: inherit; transition: opacity 0.15s;
}
.share-pill:disabled { opacity: 0.35; cursor: not-allowed; }

.post-body { padding: 14px 16px; display: flex; flex-direction: column; gap: 8px; flex: 1; }
.post-title {
  width: 100%; border: none; border-bottom: 1px solid var(--divider); background: transparent;
  color: var(--on-bg); font-size: 17px; font-weight: 600; padding: 4px 0; outline: none;
  font-family: inherit; box-sizing: border-box;
}
.post-title::placeholder { color: var(--muted); font-weight: 400; }
.post-text {
  width: 100%; border: none; background: transparent; color: var(--on-bg);
  font-size: 16px; line-height: 1.65; resize: none; outline: none; font-family: inherit;
}
.post-text::placeholder { color: var(--muted); }

.img-wrap { position: relative; }
.img-prev { width: 100%; border-radius: 12px; max-height: 280px; object-fit: cover; display: block; }
.img-rm { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,.6); color: #fff; border: none; border-radius: 50%; width: 26px; height: 26px; font-size: 13px; cursor: pointer; display: flex; align-items: center; justify-content: center; }

/* Kategoriler yatay scroll */
.topics-row {
  display: flex; gap: 8px; overflow-x: auto; padding: 8px 16px;
  border-top: 1px solid var(--divider); scrollbar-width: none;
}
.topics-row::-webkit-scrollbar { display: none; }
.topic-chip {
  flex-shrink: 0; padding: 6px 16px; border-radius: 20px; font-size: 14px;
  font-weight: 500; border: 1px solid var(--divider); background: transparent;
  color: var(--muted); cursor: pointer; font-family: inherit; transition: all 0.15s; white-space: nowrap;
}
.topic-chip.selected { background: var(--primary); color: #fff; border-color: var(--primary); }
.topic-chip:hover:not(.selected) { border-color: var(--primary); color: var(--primary); }

/* Alt araç çubuğu */
.post-toolbar {
  display: flex; align-items: center; gap: 4px; padding: 8px 14px;
  border-top: 1px solid var(--divider);
}
.tool-btn {
  display: flex; align-items: center; justify-content: center;
  width: 38px; height: 38px; border-radius: 50%; color: var(--muted);
  background: none; border: none; cursor: pointer; transition: background 0.15s;
}
.tool-btn:hover { background: var(--surface-var); color: var(--on-bg); }
.char-count { margin-left: auto; font-size: 12px; color: var(--muted); }
.char-count.warn { color: #ef4444; }

/* ── Alıntı Ekranı ──────────────────────────────────────────── */
.quote-wrap { display: flex; flex-direction: column; }

.quote-topbar {
  display: flex; align-items: center; padding: 10px 14px;
  border-bottom: 1px solid var(--divider);
  position: sticky; top: 0; background: var(--surface); z-index: 10; gap: 10px;
}
.back-btn {
  width: 36px; height: 36px; display: flex; align-items: center; justify-content: center;
  background: none; border: none; cursor: pointer; color: var(--on-bg); border-radius: 50%; flex-shrink: 0;
}
.back-btn:hover { background: var(--surface-var); }
.quote-topbar-title {
  display: flex; align-items: center; gap: 8px; flex: 1;
  font-size: 17px; font-weight: 700; color: var(--on-bg);
}
.share-text-btn {
  font-size: 15px; font-weight: 600; color: var(--primary);
  background: none; border: none; cursor: pointer; font-family: inherit; padding: 6px 4px;
  white-space: nowrap;
}
.share-text-btn:disabled { opacity: 0.35; cursor: not-allowed; }

/* Alıntı form body */
.quote-body { display: flex; flex-direction: column; gap: 0; padding: 12px 0; }

.q-title {
  width: 100%; border: none; background: transparent; color: var(--on-bg);
  font-size: 15px; font-weight: 600; padding: 4px 16px 12px; outline: none;
  font-family: inherit; box-sizing: border-box; border-bottom: 1px solid var(--divider);
}
.q-title::placeholder { color: var(--muted); font-weight: 400; font-size: 14px; }

.q-field { padding: 12px 16px; }

/* OutlinedTextField (Android Material 3 stili) */
.q-outlined {
  width: 100%; background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 10px; padding: 14px 14px; font-size: 14px; color: var(--on-bg);
  outline: none; font-family: inherit; resize: none; line-height: 1.65; box-sizing: border-box;
  font-style: italic; transition: border-color 0.15s;
}
.q-outlined:focus { border-color: var(--primary); }
.q-outlined::placeholder { color: var(--muted); font-size: 12px; letter-spacing: 0.05em; font-style: normal; font-weight: 600; }

.q-outlined-wrap {
  position: relative; display: flex; align-items: center;
  background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 10px; transition: border-color 0.15s;
}
.q-outlined-wrap:focus-within { border-color: var(--primary); }
.q-leading {
  display: flex; align-items: center; justify-content: center;
  width: 44px; height: 52px; color: var(--muted); flex-shrink: 0;
}
.q-outlined-input {
  flex: 1; border: none; background: transparent; color: var(--on-bg);
  font-size: 14px; outline: none; font-family: inherit; padding: 0 8px 0 0; height: 52px;
}
.q-outlined-input::placeholder { color: var(--muted); font-size: 11px; letter-spacing: 0.06em; font-weight: 600; }
.q-trailing { display: flex; align-items: center; padding-right: 10px; }
.q-trailing-btn {
  display: flex; align-items: center; justify-content: center; background: none; border: none;
  color: var(--muted); cursor: pointer; padding: 8px; border-radius: 50%;
}
.q-trailing-btn:hover { color: var(--on-bg); }

/* Dropdown */
.q-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0;
  background: var(--surface); border: 1px solid var(--divider); border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14); z-index: 100;
  overflow: hidden; max-height: 220px; overflow-y: auto;
}
.q-drop-row {
  display: flex; align-items: center; gap: 10px; width: 100%;
  background: none; border: none; border-bottom: 1px solid var(--divider);
  padding: 10px 14px; cursor: pointer; text-align: left; font-family: inherit;
  transition: background 0.1s;
}
.q-drop-row:last-child { border-bottom: none; }
.q-drop-row:hover { background: var(--surface-var); }
.drop-thumb {
  width: 28px; height: 42px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 12%, transparent);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.drop-thumb.no-img { color: #F59E0B; }
.drop-thumb.author-thumb { border-radius: 50%; height: 28px; color: var(--primary); background: color-mix(in srgb, var(--primary) 12%, transparent); }
.drop-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.drop-title { font-size: 13px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.drop-sub { font-size: 11px; color: var(--muted); margin-top: 1px; }
.drop-count-badge { font-size: 10px; color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, transparent); border-radius: 6px; padding: 2px 6px; white-space: nowrap; }

/* Bulunamadı hint */
.q-hint {
  display: flex; align-items: center; gap: 6px; margin-top: 8px;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  border-radius: 8px; padding: 8px 10px; font-size: 12px; color: #F59E0B;
}
.q-hint svg { stroke: #F59E0B; flex-shrink: 0; }

/* Önizleme (QuoteCard) */
.q-preview-wrap { padding: 4px 16px 0; }
.q-preview-label { font-size: 11px; font-weight: 600; color: var(--muted); text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 8px; }
.quote-card {
  position: relative; border-radius: 14px; padding: 14px 14px 14px 18px;
  background: linear-gradient(135deg, rgba(245,158,11,0.08), rgba(155,114,245,0.06));
  border: 1px solid color-mix(in srgb, #F59E0B 30%, transparent); overflow: hidden;
}
.quote-bg {
  position: absolute; top: -14px; left: 4px; font-size: 72px; font-weight: 900;
  color: rgba(245,158,11,0.12); line-height: 1; pointer-events: none; font-family: Georgia, serif;
}
.qc-text { font-size: 14px; font-style: italic; color: var(--on-bg); line-height: 1.6; margin: 0 0 10px; }
.qc-source { display: flex; align-items: center; gap: 8px; }
.qc-cover {
  width: 24px; height: 36px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 12%, transparent);
  display: flex; align-items: center; justify-content: center;
}
.qc-cover.no-img { color: #F59E0B; }
.qc-book { display: block; font-size: 11px; font-weight: 700; color: #F59E0B; }
.qc-author { display: block; font-size: 10px; color: var(--muted); margin-top: 1px; }

/* Spinner */
.spinner { width: 18px; height: 18px; border: 2px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
.spinner.small { width: 14px; height: 14px; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
