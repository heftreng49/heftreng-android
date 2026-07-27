<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/stores";
  import { collection, addDoc, Timestamp, doc, getDoc, updateDoc } from "firebase/firestore";
  import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
  import { db, storage } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser, authLoading } from "$lib/store/auth";

  // ── Mod ──────────────────────────────────────────────────────
  // mode: "post" | "quote"
  let mode          = $state<"post" | "quote">("post");
  let editPostId    = $state<string | null>(null);
  let isEditMode    = $state(false);
  let loadingPost   = $state(false);

  // ── Normal gönderi ───────────────────────────────────────────
  let text          = $state("");
  let title         = $state("");
  let category      = $state("");
  let imageFile     = $state<File | null>(null);
  let imagePreview  = $state<string | null>(null);
  let submitting    = $state(false);
  let error         = $state("");

  // ── Alıntı ekranı (QuoteDialog mantığı) ─────────────────────
  let quoteText     = $state("");
  let quoteTitle    = $state("");
  let quoteBook     = $state("");
  let quoteAuthor   = $state("");
  let quoteCover    = $state("");
  let quoteBookId   = $state("");

  // Kitap autocomplete
  let bookQuery         = $state("");
  let bookSuggestions   = $state<any[]>([]);
  let showBookDrop      = $state(false);
  let bookLoading       = $state(false);
  let bookSelectedFromLib = $state(false);

  // Yazar autocomplete
  let authorQuery       = $state("");
  let authorSuggestions = $state<any[]>([]);
  let showAuthorDrop    = $state(false);
  let authorLoading     = $state(false);

  const categories = ["Şiir", "Deneme", "Hikaye", "Roman", "Alıntı", "Düşünce", "Diğer"];

  onMount(() => {
    const unsub = authLoading.subscribe(loading => {
      if (!loading && !$currentUser) goto("/login");
    });
    const editId = $page.url.searchParams.get("edit");
    const type   = $page.url.searchParams.get("type");
    if (editId) {
      editPostId = editId;
      isEditMode = true;
      loadExistingPost(editId);
    }
    if (type === "quote") mode = "quote";
    return unsub;
  });

  async function loadExistingPost(id: string) {
    loadingPost = true;
    try {
      const snap = await getDoc(doc(db, "feed", id));
      if (!snap.exists()) { error = "Gönderi bulunamadı."; return; }
      const d = snap.data();
      if ($currentUser && d.uid !== $currentUser.uid) { error = "Yetki yok."; return; }
      if (d.quoteText) {
        mode        = "quote";
        quoteText   = d.quoteText   ?? "";
        quoteTitle  = d.title       ?? "";
        quoteBook   = d.bookName    ?? "";
        quoteAuthor = d.authorName  ?? "";
        quoteCover  = d.coverImg    ?? "";
        bookQuery   = d.bookName    ?? "";
        authorQuery = d.authorName  ?? "";
      } else {
        mode     = "post";
        title    = d.title    ?? "";
        text     = d.text     ?? "";
        category = d.category ?? "";
      }
    } catch(e) { error = "Yüklenemedi."; }
    finally { loadingPost = false; }
  }

  // ── Kitap arama (Supabase) ───────────────────────────────────
  let bookTimer: any;
  async function onBookInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    bookQuery = val;
    quoteBook = val;
    bookSelectedFromLib = false;
    quoteCover = "";
    clearTimeout(bookTimer);
    if (val.length < 1) { bookSuggestions = []; showBookDrop = false; return; }
    bookTimer = setTimeout(async () => {
      bookLoading = true;
      try {
        const { data } = await supabase
          .from("library_books")
          .select("id, title, author_name, cover_img")
          .ilike("title", `%${val}%`)
          .limit(8);
        bookSuggestions = data ?? [];
        showBookDrop = bookSuggestions.length > 0;
      } catch(e) {}
      finally { bookLoading = false; }
    }, 250);
  }

  function selectBook(b: any) {
    quoteBook   = b.title;
    bookQuery   = b.title;
    quoteBookId = b.id ?? "";
    quoteCover  = b.cover_img ?? "";
    if (!quoteAuthor && b.author_name) {
      quoteAuthor = b.author_name;
      authorQuery = b.author_name;
    }
    bookSuggestions = [];
    showBookDrop    = false;
    bookSelectedFromLib = true;
  }

  function clearBook() {
    quoteBook = ""; bookQuery = ""; quoteBookId = ""; quoteCover = "";
    bookSelectedFromLib = false; bookSuggestions = []; showBookDrop = false;
  }

  // ── Yazar arama (Supabase authors tablosu) ───────────────────
  let authorTimer: any;
  async function onAuthorInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    authorQuery = val;
    quoteAuthor = val;
    clearTimeout(authorTimer);
    if (val.length < 1) { authorSuggestions = []; showAuthorDrop = false; return; }
    authorTimer = setTimeout(async () => {
      authorLoading = true;
      try {
        const { data } = await supabase
          .from("authors")
          .select("id, name, cover_img")
          .ilike("name", `%${val}%`)
          .limit(6);
        authorSuggestions = data ?? [];
        showAuthorDrop = authorSuggestions.length > 0;
      } catch(e) {}
      finally { authorLoading = false; }
    }, 250);
  }

  function selectAuthor(a: any) {
    quoteAuthor = a.name;
    authorQuery = a.name;
    authorSuggestions = [];
    showAuthorDrop    = false;
  }

  function clearAuthor() {
    quoteAuthor = ""; authorQuery = ""; authorSuggestions = []; showAuthorDrop = false;
  }

  // ── Fotoğraf ─────────────────────────────────────────────────
  function onFileChange(e: Event) {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (!f) return;
    imageFile = f;
    imagePreview = URL.createObjectURL(f);
  }
  function removeImage() { imageFile = null; imagePreview = null; }

  // ── Paylaş ───────────────────────────────────────────────────
  async function submit() {
    if (!$currentUser) return;
    submitting = true; error = "";
    try {
      if (isEditMode && editPostId) {
        if (mode === "quote") {
          await updateDoc(doc(db, "feed", editPostId), {
            quoteText:  quoteText.trim(),
            bookName:   quoteBook.trim(),
            authorName: quoteAuthor.trim(),
          });
        } else {
          await updateDoc(doc(db, "feed", editPostId), {
            text:  text.trim(),
            title: title.trim(),
          });
        }
      } else {
        let imageURL = "";
        if (imageFile) {
          const r = ref(storage, `posts/${$currentUser.uid}/${Date.now()}.jpg`);
          await uploadBytes(r, imageFile);
          imageURL = await getDownloadURL(r);
        }
        await addDoc(collection(db, "feed"), {
          uid:           $currentUser.uid,
          displayName:   $currentUser.displayName ?? "",
          name:          $currentUser.displayName ?? "",
          username:      "",
          photoURL:      $currentUser.photoURL    ?? "",
          authorEmail:   $currentUser.email       ?? "",
          text:          mode === "quote" ? "" : text.trim(),
          title:         mode === "quote" ? quoteTitle.trim() : title.trim(),
          category:      mode === "quote" ? "" : category,
          imgUrl:        imageURL,
          imageURL:      imageURL,
          quoteText:     mode === "quote" ? quoteText.trim() : "",
          bookName:      mode === "quote" ? quoteBook.trim() : "",
          authorName:    mode === "quote" ? quoteAuthor.trim() : "",
          coverImg:      mode === "quote" ? quoteCover : "",
          libraryBookId: mode === "quote" ? quoteBookId : "",
          type:          mode === "quote" ? "library_quote" : "",
          visibility:    "public",
          mentions: [], likes: 0, saves: 0, cmtCount: 0, reposts: 0,
          ts: Timestamp.now(),
        });
      }
      goto("/feed");
    } catch(e: any) {
      error = "Hata oluştu, tekrar dene.";
      console.error(e);
    } finally { submitting = false; }
  }

  // Derived
  let canSubmitPost  = $derived(text.trim().length > 0 || imageFile !== null);
  let canSubmitQuote = $derived(quoteText.trim().length > 0 && quoteBook.trim().length > 0 && quoteAuthor.trim().length > 0);
  let canSubmit      = $derived(mode === "quote" ? canSubmitQuote : canSubmitPost);
  let bookNotFound   = $derived(quoteBook.length > 0 && !bookSelectedFromLib && bookSuggestions.length === 0 && !bookLoading);
  let authorNotFound = $derived(quoteAuthor.length > 0 && authorSuggestions.length === 0 && !authorLoading && !authorSuggestions.some((a:any) => a.name === quoteAuthor));
</script>

<main class="wrap">
  <div class="composer">

    <!-- Üst bar -->
    <div class="top-bar">
      <a href="/feed" class="cancel">İptal</a>

      <!-- Mod geçişi (sadece yeni gönderi) -->
      {#if !isEditMode}
        <div class="mode-tabs">
          <button
            class="mode-tab"
            class:active={mode === "post"}
            onclick={() => mode = "post"}
          >Gönderi</button>
          <button
            class="mode-tab"
            class:active={mode === "quote"}
            onclick={() => mode = "quote"}
          >
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
            Alıntı
          </button>
        </div>
      {:else}
        <span class="edit-label">{mode === "quote" ? "Alıntıyı Düzenle" : "Gönderiyi Düzenle"}</span>
      {/if}

      <button class="send-btn" onclick={submit} disabled={!canSubmit || submitting}>
        {submitting ? "..." : isEditMode ? "Kaydet" : "Paylaş"}
      </button>
    </div>

    {#if loadingPost}
      <div class="loading-state"><div class="spinner"></div><span>Yükleniyor...</span></div>
    {:else}

    <!-- ── GÖNDERI MODU ────────────────────────────────────── -->
    {#if mode === "post"}
      <div class="body-area">
        {#if $currentUser}
          <div class="user-row">
            {#if $currentUser.photoURL}
              <img src={$currentUser.photoURL} alt="" class="av"/>
            {:else}
              <div class="av-ph">{($currentUser.displayName ?? "?")[0].toUpperCase()}</div>
            {/if}
            <span class="uname">{$currentUser.displayName ?? $currentUser.email}</span>
          </div>
        {/if}

        <input class="title-input" placeholder="Başlık (opsiyonel)" bind:value={title} maxlength={120}/>
        <textarea class="textbox" placeholder="Ne düşünüyorsun?" bind:value={text} maxlength={1000} rows={6}></textarea>

        {#if imagePreview}
          <div class="img-preview-wrap">
            <img src={imagePreview} alt="" class="img-preview"/>
            <button class="remove-img" onclick={removeImage}>✕</button>
          </div>
        {/if}

        <div class="categories">
          {#each categories as cat}
            <button class="cat-chip" class:selected={category === cat} onclick={() => category = category === cat ? "" : cat}>{cat}</button>
          {/each}
        </div>

        {#if error}<p class="error">{error}</p>{/if}
      </div>

      <!-- Alt araç çubuğu -->
      <div class="bottom-bar">
        <label class="tool-btn" title="Fotoğraf">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><rect x="3" y="3" width="18" height="18" rx="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
          <input type="file" accept="image/*" onchange={onFileChange} style="display:none"/>
        </label>
        <span class="char-count" class:warn={text.length > 900}>{text.length}/1000</span>
      </div>

    <!-- ── ALINTI MODU (Android QuoteDialog'u gibi tam sayfa form) ── -->
    {:else}
      <div class="quote-screen">

        {#if $currentUser}
          <div class="user-row" style="padding: 0 16px">
            {#if $currentUser.photoURL}
              <img src={$currentUser.photoURL} alt="" class="av"/>
            {:else}
              <div class="av-ph">{($currentUser.displayName ?? "?")[0].toUpperCase()}</div>
            {/if}
            <span class="uname">{$currentUser.displayName ?? $currentUser.email}</span>
          </div>
        {/if}

        <!-- Başlık -->
        <div class="q-field-wrap">
          <input class="q-title-input" placeholder="Başlık (opsiyonel)" bind:value={quoteTitle} maxlength={120}/>
        </div>

        <!-- Alıntı metni -->
        <div class="q-field-wrap">
          <label class="q-label">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
            Alıntı metni *
          </label>
          <textarea class="q-textarea" placeholder="Alıntı metnini buraya yaz..." bind:value={quoteText} rows={5}></textarea>
        </div>

        <!-- Kitap adı + autocomplete -->
        <div class="q-field-wrap">
          <label class="q-label">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
            Kitap adı *
          </label>
          <div class="q-input-wrap">
            <input
              class="q-input"
              class:linked={bookSelectedFromLib}
              placeholder="Kitap ara..."
              value={bookQuery}
              oninput={onBookInput}
              onblur={() => setTimeout(() => { showBookDrop = false; }, 180)}
              onfocus={() => { if (bookSuggestions.length > 0) showBookDrop = true; }}
            />
            {#if bookLoading}
              <div class="q-input-spinner"><div class="spinner small"></div></div>
            {:else if quoteBook}
              <button class="q-clear" onclick={clearBook}>✕</button>
            {/if}
            {#if bookSelectedFromLib}
              <span class="linked-badge">✓</span>
            {/if}

            <!-- Kitap dropdown -->
            {#if showBookDrop && bookSuggestions.length > 0}
              <div class="q-dropdown">
                {#each bookSuggestions as b (b.id)}
                  <button class="q-drop-item" onclick={() => selectBook(b)}>
                    {#if b.cover_img}
                      <img src={b.cover_img} alt={b.title} class="drop-cover"/>
                    {:else}
                      <div class="drop-cover no-cover">
                        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="12" height="12"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                      </div>
                    {/if}
                    <div class="drop-text">
                      <span class="drop-title">{b.title}</span>
                      {#if b.author_name}<span class="drop-sub">{b.author_name}</span>{/if}
                    </div>
                  </button>
                {/each}
              </div>
            {/if}
          </div>

          <!-- Kitap bulunamadı uyarısı (Android'deki "yeni oluşturulacak" mesajı) -->
          {#if bookNotFound}
            <div class="q-not-found">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              "<strong>{quoteBook}</strong>" kütüphanede bulunamadı — yine de paylaşabilirsin.
            </div>
          {/if}
        </div>

        <!-- Yazar adı + autocomplete -->
        <div class="q-field-wrap">
          <label class="q-label">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
            Yazar adı *
          </label>
          <div class="q-input-wrap">
            <input
              class="q-input"
              placeholder="Yazar ara..."
              value={authorQuery}
              oninput={onAuthorInput}
              onblur={() => setTimeout(() => { showAuthorDrop = false; }, 180)}
              onfocus={() => { if (authorSuggestions.length > 0) showAuthorDrop = true; }}
            />
            {#if authorLoading}
              <div class="q-input-spinner"><div class="spinner small"></div></div>
            {:else if quoteAuthor}
              <button class="q-clear" onclick={clearAuthor}>✕</button>
            {/if}

            <!-- Yazar dropdown -->
            {#if showAuthorDrop && authorSuggestions.length > 0}
              <div class="q-dropdown">
                {#each authorSuggestions as a (a.id)}
                  <button class="q-drop-item" onclick={() => selectAuthor(a)}>
                    <div class="drop-cover no-cover author">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="12" height="12"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
                    </div>
                    <div class="drop-text">
                      <span class="drop-title">{a.name}</span>
                    </div>
                  </button>
                {/each}
              </div>
            {/if}
          </div>

          {#if authorNotFound && quoteAuthor.length > 1}
            <div class="q-not-found">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="13" height="13"><circle cx="12" cy="8" r="4"/><path d="M4 20c0-4 3.6-7 8-7s8 3 8 7"/></svg>
              "<strong>{quoteAuthor}</strong>" yazarı bulunamadı — yine de paylaşabilirsin.
            </div>
          {/if}
        </div>

        <!-- Önizleme (Android'deki QuoteCard) -->
        {#if quoteText || quoteBook || quoteAuthor}
          <div class="q-field-wrap">
            <label class="q-label">Önizleme</label>
            <div class="quote-preview-card">
              <span class="quote-bg-mark">"</span>
              <p class="preview-text">{quoteText || "Alıntı metni..."}</p>
              {#if quoteBook || quoteAuthor}
                <div class="preview-source">
                  {#if quoteCover}
                    <img src={quoteCover} alt={quoteBook} class="preview-cover"/>
                  {:else}
                    <div class="preview-cover no-cover">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="12" height="12"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    </div>
                  {/if}
                  <div>
                    {#if quoteBook}<span class="preview-book">{quoteBook}</span>{/if}
                    {#if quoteAuthor}<span class="preview-author">{quoteAuthor}</span>{/if}
                  </div>
                </div>
              {/if}
            </div>
          </div>
        {/if}

        {#if error}<p class="error" style="padding: 0 16px">{error}</p>{/if}
        <div style="height: 32px"></div>
      </div>
    {/if}

    {/if}
  </div>
</main>

<style>
.wrap { max-width: 600px; margin: 0 auto; padding: 12px; padding-bottom: 80px; }
.composer { background: var(--card); border-radius: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); overflow: hidden; }

/* ── Üst bar ─────────────────────────────────────────────────── */
.top-bar {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px; border-bottom: 1px solid var(--divider); gap: 8px;
}
.cancel { font-size: 15px; color: var(--muted); text-decoration: none; white-space: nowrap; }
.cancel:hover { color: var(--on-bg); }
.edit-label { font-size: 15px; font-weight: 600; color: var(--on-bg); }

/* Mod geçiş tabları */
.mode-tabs {
  display: flex; background: var(--surface-var); border-radius: 20px;
  padding: 3px; gap: 2px; flex: 1; max-width: 200px; margin: 0 auto;
}
.mode-tab {
  flex: 1; padding: 6px 10px; border-radius: 16px; font-size: 13px;
  font-weight: 500; color: var(--muted); background: none; border: none;
  cursor: pointer; font-family: inherit; transition: all 0.15s;
  display: flex; align-items: center; justify-content: center; gap: 4px;
}
.mode-tab.active { background: var(--surface); color: var(--on-bg); font-weight: 600; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }
.mode-tab.active svg { stroke: #F59E0B; }

.send-btn {
  padding: 7px 18px; background: var(--primary); color: #fff;
  border-radius: 20px; font-size: 14px; font-weight: 600; border: none;
  cursor: pointer; transition: opacity .15s; white-space: nowrap;
}
.send-btn:disabled { opacity: .35; cursor: not-allowed; }

/* ── Normal gönderi ─────────────────────────────────────────── */
.body-area { padding: 14px 16px; display: flex; flex-direction: column; gap: 10px; }
.user-row { display: flex; align-items: center; gap: 10px; }
.av, .av-ph { width: 38px; height: 38px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.av-ph { background: var(--primary); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 15px; }
.uname { font-size: 14px; font-weight: 600; color: var(--on-bg); }

.title-input {
  width: 100%; border: none; border-bottom: 1px solid var(--divider); background: transparent;
  color: var(--on-bg); font-size: 17px; font-weight: 600; padding: 4px 0; outline: none;
  font-family: inherit; box-sizing: border-box;
}
.title-input::placeholder { color: var(--muted); font-weight: 400; }
.textbox {
  width: 100%; border: none; background: transparent; color: var(--on-bg);
  font-size: 16px; line-height: 1.6; resize: none; outline: none; font-family: inherit;
}
.textbox::placeholder { color: var(--muted); }

.categories { display: flex; flex-wrap: wrap; gap: 6px; }
.cat-chip {
  padding: 5px 12px; border-radius: 99px; font-size: 12px; font-weight: 500;
  border: 1px solid var(--divider); background: transparent; color: var(--muted);
  cursor: pointer; font-family: inherit; transition: all 0.15s;
}
.cat-chip.selected { background: var(--primary); color: #fff; border-color: var(--primary); }
.cat-chip:hover:not(.selected) { border-color: var(--primary); color: var(--primary); }

.img-preview-wrap { position: relative; }
.img-preview { width: 100%; border-radius: 12px; max-height: 280px; object-fit: cover; display: block; }
.remove-img { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,.6); color: #fff; border: none; border-radius: 50%; width: 26px; height: 26px; font-size: 13px; cursor: pointer; display: flex; align-items: center; justify-content: center; }

.bottom-bar { display: flex; align-items: center; gap: 4px; padding: 8px 14px; border-top: 1px solid var(--divider); }
.tool-btn { display: flex; align-items: center; justify-content: center; width: 36px; height: 36px; border-radius: 50%; color: var(--primary); background: none; border: none; cursor: pointer; }
.tool-btn:hover { background: var(--surface-var); }
.char-count { margin-left: auto; font-size: 12px; color: var(--muted); }
.char-count.warn { color: #ef4444; }

/* ── Alıntı ekranı ──────────────────────────────────────────── */
.quote-screen { display: flex; flex-direction: column; gap: 0; padding-top: 12px; }

.q-field-wrap { padding: 10px 16px; border-bottom: 1px solid var(--divider); }
.q-field-wrap:last-of-type { border-bottom: none; }

.q-label {
  display: flex; align-items: center; gap: 5px;
  font-size: 11px; font-weight: 700; color: #F59E0B;
  text-transform: uppercase; letter-spacing: 0.06em; margin-bottom: 8px;
}
.q-label svg { stroke: #F59E0B; flex-shrink: 0; }

.q-title-input {
  width: 100%; border: none; background: transparent; color: var(--on-bg);
  font-size: 17px; font-weight: 600; outline: none; font-family: inherit;
  box-sizing: border-box; padding: 2px 0;
}
.q-title-input::placeholder { color: var(--muted); font-weight: 400; }

.q-textarea {
  width: 100%; border: none; background: transparent; color: var(--on-bg);
  font-size: 15px; font-style: italic; line-height: 1.7; resize: none;
  outline: none; font-family: inherit; box-sizing: border-box;
}
.q-textarea::placeholder { color: var(--muted); font-style: italic; }

/* Kitap / yazar input + dropdown */
.q-input-wrap { position: relative; }
.q-input {
  width: 100%; background: var(--surface-var); border: 1px solid var(--divider);
  border-radius: 10px; padding: 10px 36px 10px 12px; font-size: 14px;
  color: var(--on-bg); outline: none; font-family: inherit; box-sizing: border-box;
  transition: border-color 0.15s;
}
.q-input:focus { border-color: #F59E0B; }
.q-input.linked { border-color: var(--primary); }
.q-input::placeholder { color: var(--muted); }

.q-clear {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  background: none; border: none; color: var(--muted); cursor: pointer;
  font-size: 14px; width: 24px; height: 24px; display: flex; align-items: center; justify-content: center;
}
.q-clear:hover { color: var(--on-bg); }
.q-input-spinner { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); }

.linked-badge {
  position: absolute; right: 8px; top: 50%; transform: translateY(-50%);
  color: var(--primary); font-size: 12px; font-weight: 700;
}

.q-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0;
  background: var(--surface); border: 1px solid var(--divider); border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14); z-index: 100;
  overflow: hidden; max-height: 220px; overflow-y: auto;
}
.q-drop-item {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  width: 100%; background: none; border: none; border-bottom: 1px solid var(--divider);
  cursor: pointer; text-align: left; font-family: inherit; transition: background 0.1s;
}
.q-drop-item:last-child { border-bottom: none; }
.q-drop-item:hover { background: var(--surface-var); }
.drop-cover {
  width: 28px; height: 42px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.drop-cover.no-cover { color: #F59E0B; }
.drop-cover.author { border-radius: 50%; width: 28px; height: 28px; color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, transparent); }
.drop-text { display: flex; flex-direction: column; min-width: 0; }
.drop-title { font-size: 13px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.drop-sub { font-size: 11px; color: var(--muted); margin-top: 1px; }

.q-not-found {
  display: flex; align-items: center; gap: 6px; margin-top: 8px;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  border-radius: 8px; padding: 8px 10px; font-size: 12px; color: #F59E0B;
}
.q-not-found svg { stroke: #F59E0B; flex-shrink: 0; }

/* Önizleme kartı (Android QuoteCard) */
.quote-preview-card {
  position: relative; border-radius: 14px; padding: 14px 14px 14px 18px;
  background: linear-gradient(135deg, rgba(245,158,11,0.08), rgba(155,114,245,0.06));
  border: 1px solid color-mix(in srgb, #F59E0B 35%, transparent);
  overflow: hidden;
}
.quote-bg-mark {
  position: absolute; top: -16px; left: 4px; font-size: 72px; font-weight: 900;
  color: color-mix(in srgb, #F59E0B 12%, transparent); line-height: 1; pointer-events: none;
  font-family: Georgia, serif;
}
.preview-text {
  font-size: 14px; font-style: italic; color: var(--on-bg);
  line-height: 1.6; margin: 0 0 10px; position: relative;
}
.preview-source { display: flex; align-items: center; gap: 8px; }
.preview-cover {
  width: 24px; height: 36px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex; align-items: center; justify-content: center;
}
.preview-cover.no-cover { color: #F59E0B; }
.preview-book { display: block; font-size: 11px; font-weight: 700; color: #F59E0B; }
.preview-author { display: block; font-size: 10px; color: var(--muted); margin-top: 1px; }

.loading-state { display: flex; align-items: center; gap: 10px; padding: 32px 20px; color: var(--muted); font-size: 14px; }
.error { color: #ef4444; font-size: 13px; }

.spinner { width: 18px; height: 18px; border: 2px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
.spinner.small { width: 14px; height: 14px; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
