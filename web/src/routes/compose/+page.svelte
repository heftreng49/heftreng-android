<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { page } from "$app/stores";
  import { collection, addDoc, Timestamp, doc, getDoc, updateDoc } from "firebase/firestore";
  import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
  import { db, storage } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";
  import { authLoading } from "$lib/store/auth";

  // ── Mod: yeni gönderi mi, düzenleme mi? ─────────────────────
  let editPostId     = $state<string | null>(null);
  let isEditMode     = $state(false);
  let isQuotePost    = $state(false);
  let loadingPost    = $state(false);

  let text           = $state("");
  let title          = $state("");
  let category       = $state("");
  let imageFile      = $state<File | null>(null);
  let imagePreview   = $state<string | null>(null);
  let submitting     = $state(false);
  let error          = $state("");

  // ── Alıntı alanı ─────────────────────────────────────────────
  let quoteText      = $state("");
  let bookName       = $state("");
  let authorName     = $state("");
  let coverImg       = $state("");
  let showQuotePanel = $state(false);

  // ── Alıntı - kitap arama (Android AuthorQuoteDialog mantığı) ─
  let authorBooks      = $state<any[]>([]);
  let selectedBook     = $state<any | null>(null);
  let manualBook       = $state("");
  let bookSearchTerm   = $state("");
  let bookSearchResults = $state<any[]>([]);
  let bookSearchLoading = $state(false);
  let showBookDropdown  = $state(false);
  let linkedLibrary     = $state(false);

  const categories = ["Şiir", "Deneme", "Hikaye", "Roman", "Alıntı", "Düşünce", "Diğer"];

  // ── Init ─────────────────────────────────────────────────────
  onMount(() => {
    const unsub = authLoading.subscribe(loading => {
      if (!loading && !$currentUser) goto("/login");
    });

    // ?edit=<id> parametresi varsa düzenleme moduna geç
    const editId = $page.url.searchParams.get("edit");
    const type   = $page.url.searchParams.get("type");

    if (editId) {
      editPostId  = editId;
      isEditMode  = true;
      loadExistingPost(editId);
    } else if (type === "quote") {
      showQuotePanel = true;
    }

    return unsub;
  });

  async function loadExistingPost(id: string) {
    loadingPost = true;
    try {
      const snap = await getDoc(doc(db, "feed", id));
      if (!snap.exists()) { error = "Gönderi bulunamadı."; loadingPost = false; return; }
      const d = snap.data();
      // Sahiplik kontrolü
      if ($currentUser && d.uid !== $currentUser.uid) {
        error = "Bu gönderiyi düzenleme yetkiniz yok.";
        loadingPost = false;
        return;
      }
      title      = d.title      ?? "";
      text       = d.text       ?? "";
      category   = d.category   ?? "";
      quoteText  = d.quoteText  ?? "";
      bookName   = d.bookName   ?? "";
      authorName = d.authorName ?? "";
      coverImg   = d.coverImg   ?? "";
      isQuotePost = !!d.quoteText;
      if (isQuotePost) {
        showQuotePanel = true;
        manualBook     = d.bookName ?? "";
      }
    } catch (e) { console.error(e); error = "Gönderi yüklenirken hata oluştu."; }
    finally { loadingPost = false; }
  }

  // ── Kitap arama (Supabase library_books) ────────────────────
  async function searchBooks(term: string) {
    if (term.length < 2) { bookSearchResults = []; return; }
    bookSearchLoading = true;
    try {
      const { data } = await supabase
        .from("library_books")
        .select("id, title, author_name, cover_img, publish_year")
        .ilike("title", `%${term}%`)
        .limit(8);
      bookSearchResults = data ?? [];
    } catch (e) { console.error(e); }
    finally { bookSearchLoading = false; }
  }

  let searchTimer: any;
  function onBookSearchInput(e: Event) {
    const val = (e.target as HTMLInputElement).value;
    bookSearchTerm = val;
    clearTimeout(searchTimer);
    showBookDropdown = val.length >= 2;
    searchTimer = setTimeout(() => searchBooks(val), 300);
  }

  function selectBook(book: any) {
    selectedBook     = book;
    bookName         = book.title;
    authorName       = book.author_name ?? authorName;
    coverImg         = book.cover_img   ?? "";
    bookSearchTerm   = "";
    bookSearchResults = [];
    showBookDropdown  = false;
    linkedLibrary     = true;
  }

  function clearSelectedBook() {
    selectedBook    = null;
    bookName        = manualBook;
    coverImg        = "";
    linkedLibrary   = false;
  }

  function onFileChange(e: Event) {
    const input = e.target as HTMLInputElement;
    const file  = input.files?.[0];
    if (!file) return;
    imageFile    = file;
    imagePreview = URL.createObjectURL(file);
  }

  function removeImage() { imageFile = null; imagePreview = null; }

  function clearQuote() {
    quoteText       = "";
    bookName        = "";
    manualBook      = "";
    authorName      = "";
    coverImg        = "";
    selectedBook    = null;
    linkedLibrary   = false;
    showQuotePanel  = false;
    bookSearchTerm  = "";
    bookSearchResults = [];
  }

  // ── Kaydet / Paylaş ─────────────────────────────────────────
  async function submit() {
    if (!$currentUser) return;
    if (!text.trim() && !imageFile && !quoteText.trim()) return;
    submitting = true;
    error = "";
    try {
      if (isEditMode && editPostId) {
        // ── DÜZENLEME ────────────────────────────────────────
        if (isQuotePost) {
          const updates: any = { quoteText: quoteText.trim() };
          if (bookName.trim())   updates.bookName   = bookName.trim();
          if (authorName.trim()) updates.authorName = authorName.trim();
          await updateDoc(doc(db, "feed", editPostId), updates);
        } else {
          await updateDoc(doc(db, "feed", editPostId), {
            text:  text.trim(),
            title: title.trim(),
          });
        }
      } else {
        // ── YENİ GÖNDERİ ────────────────────────────────────
        let imageURL = "";
        if (imageFile) {
          const storageRef = ref(storage, `posts/${$currentUser.uid}/${Date.now()}.jpg`);
          await uploadBytes(storageRef, imageFile);
          imageURL = await getDownloadURL(storageRef);
        }

        const finalBookName   = selectedBook?.title    ?? bookName.trim();
        const finalAuthorName = selectedBook?.author_name ?? authorName.trim();
        const finalCoverImg   = selectedBook?.cover_img   ?? coverImg.trim();
        const libraryBookId   = selectedBook?.id ?? "";

        await addDoc(collection(db, "feed"), {
          uid:            $currentUser.uid,
          displayName:    $currentUser.displayName ?? "",
          name:           $currentUser.displayName ?? "",
          username:       "",
          photoURL:       $currentUser.photoURL    ?? "",
          authorEmail:    $currentUser.email       ?? "",
          text:           text.trim(),
          title:          title.trim(),
          category:       category,
          imgUrl:         imageURL,
          imageURL:       imageURL,
          quoteText:      quoteText.trim(),
          bookName:       finalBookName,
          authorName:     finalAuthorName,
          coverImg:       finalCoverImg,
          libraryBookId:  libraryBookId,
          type:           quoteText.trim() ? "library_quote" : "",
          visibility:     "public",
          mentions:       [],
          likes:          0,
          saves:          0,
          cmtCount:       0,
          reposts:        0,
          ts:             Timestamp.now(),
        });
      }

      goto("/feed");
    } catch(e: any) {
      error = "İşlem sırasında hata oluştu.";
      console.error(e);
    } finally {
      submitting = false;
    }
  }

  let charCount  = $derived(text.length);
  let canSubmit  = $derived((text.trim().length > 0 || imageFile !== null || quoteText.trim().length > 0) && !submitting);
  let pageTitle  = $derived(isEditMode ? (isQuotePost ? "Alıntıyı Düzenle" : "Gönderiyi Düzenle") : "Yeni Gönderi");
  let submitLabel = $derived(submitting ? (isEditMode ? "Kaydediliyor..." : "Gönderiliyor...") : (isEditMode ? "Kaydet" : "Paylaş"));
</script>

<main class="wrap">
  <div class="composer">

    <!-- Üst bar -->
    <div class="top-bar">
      <a href="/feed" class="cancel">İptal</a>
      <h2 class="title-bar">{pageTitle}</h2>
      <button class="send-btn" onclick={submit} disabled={!canSubmit}>
        {submitLabel}
      </button>
    </div>

    {#if loadingPost}
      <div class="loading-state">
        <div class="spinner"></div>
        <span>Gönderi yükleniyor...</span>
      </div>
    {:else}

    <div class="body-area">
      <!-- Kullanıcı satırı -->
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

      {#if !isQuotePost}
        <!-- Başlık (opsiyonel) -->
        <input
          class="title-input"
          placeholder="Başlık (opsiyonel)"
          bind:value={title}
          maxlength={120}
          disabled={isEditMode && isQuotePost}
        />

        <!-- Ana metin -->
        <textarea
          class="textbox"
          placeholder="Ne düşünüyorsun?"
          bind:value={text}
          maxlength={1000}
          rows={5}
        ></textarea>
      {/if}

      <!-- ── Alıntı paneli (Android AuthorQuoteDialog mantığı) ── -->
      {#if showQuotePanel}
        <div class="quote-panel">
          <div class="quote-panel-header">
            <span class="quote-panel-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
              {isEditMode ? "Alıntıyı Düzenle" : "Alıntı Ekle"}
            </span>
            {#if !isEditMode}
              <button class="quote-close" onclick={clearQuote}>✕</button>
            {/if}
          </div>

          <!-- Alıntı metni -->
          <textarea
            class="quote-input"
            placeholder="Alıntı metni..."
            bind:value={quoteText}
            rows={4}
          ></textarea>

          <!-- Yazar adı -->
          <div class="quote-field-group">
            <label class="quote-label">Yazar adı (opsiyonel)</label>
            <input
              class="quote-field"
              placeholder="Yazar..."
              bind:value={authorName}
            />
          </div>

          <!-- Kitap arama — Android'deki dropdown mantığı -->
          {#if !isEditMode}
            <div class="quote-field-group book-search-wrap">
              <label class="quote-label">Kitap ara veya yaz (opsiyonel)</label>
              {#if selectedBook}
                <!-- Seçili kitap göstergesi -->
                <div class="selected-book-row">
                  {#if selectedBook.cover_img}
                    <img src={selectedBook.cover_img} alt={selectedBook.title} class="selected-book-cover"/>
                  {:else}
                    <div class="selected-book-cover no-cover">
                      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                    </div>
                  {/if}
                  <div class="selected-book-info">
                    <span class="selected-book-title">{selectedBook.title}</span>
                    {#if selectedBook.author_name}<span class="selected-book-author">{selectedBook.author_name}</span>{/if}
                    <span class="library-badge">✓ Kütüphane bağlandı</span>
                  </div>
                  <button class="clear-book-btn" onclick={clearSelectedBook} title="Kitap seçimini kaldır">✕</button>
                </div>
              {:else}
                <div class="book-search-container">
                  <input
                    class="quote-field"
                    placeholder="Kitap ara veya kitap adını yaz..."
                    value={bookSearchTerm || bookName}
                    oninput={onBookSearchInput}
                    onblur={() => { setTimeout(() => { showBookDropdown = false; }, 200); }}
                    onfocus={() => { if (bookSearchTerm.length >= 2) showBookDropdown = true; }}
                  />
                  {#if bookSearchLoading}
                    <div class="book-search-spinner"><div class="spinner small"></div></div>
                  {/if}
                  {#if showBookDropdown && bookSearchResults.length > 0}
                    <div class="book-dropdown">
                      {#each bookSearchResults as book}
                        <button class="book-option" onclick={() => selectBook(book)}>
                          {#if book.cover_img}
                            <img src={book.cover_img} alt={book.title} class="book-option-cover"/>
                          {:else}
                            <div class="book-option-cover no-cover">
                              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="12" height="12"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                            </div>
                          {/if}
                          <div class="book-option-text">
                            <span class="book-option-title">{book.title}</span>
                            {#if book.author_name}<span class="book-option-author">{book.author_name}</span>{/if}
                          </div>
                        </button>
                      {/each}
                    </div>
                  {/if}
                </div>
              {/if}
            </div>
          {:else}
            <!-- Edit modunda sadece kitap adı göster -->
            <div class="quote-field-group">
              <label class="quote-label">Kitap adı</label>
              <input class="quote-field" placeholder="Kitap adı..." bind:value={bookName}/>
            </div>
          {/if}
        </div>
      {/if}

      <!-- Görsel önizleme -->
      {#if imagePreview}
        <div class="img-preview-wrap">
          <img src={imagePreview} alt="" class="img-preview"/>
          <button class="remove-img" onclick={removeImage}>✕</button>
        </div>
      {/if}

      <!-- Kategori seçimi (sadece yeni / normal gönderi) -->
      {#if !isEditMode || !isQuotePost}
        <div class="categories">
          {#each categories as cat}
            <button
              class="cat-chip"
              class:selected={category === cat}
              onclick={() => category = category === cat ? "" : cat}
            >{cat}</button>
          {/each}
        </div>
      {/if}

      {#if error}
        <p class="error">{error}</p>
      {/if}
    </div>

    <!-- Alt bar -->
    {#if !isEditMode}
      <div class="bottom-bar">
        <label class="tool-btn" title="Fotoğraf ekle">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>
          <input type="file" accept="image/*" onchange={onFileChange} style="display:none"/>
        </label>

        <button
          class="tool-btn"
          class:quote-active={showQuotePanel}
          onclick={() => showQuotePanel = !showQuotePanel}
          title="Alıntı ekle"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
        </button>

        <span class="char-count" class:warn={charCount > 900}>{charCount}/1000</span>
      </div>
    {/if}
    {/if}
  </div>
</main>

<style>
.wrap { max-width: 600px; margin: 0 auto; padding: 12px; padding-bottom: 80px; }
.composer { background: var(--card); border-radius: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.08); overflow: hidden; }

.top-bar { display: flex; align-items: center; justify-content: space-between; padding: 12px 16px; border-bottom: 1px solid var(--divider); }
.cancel { font-size: 15px; color: var(--muted); text-decoration: none; }
.cancel:hover { color: var(--on-bg); }
.title-bar { font-size: 16px; font-weight: 600; color: var(--on-bg); }
.send-btn { padding: 8px 20px; background: var(--primary); color: #fff; border-radius: 20px; font-size: 14px; font-weight: 600; border: none; cursor: pointer; transition: opacity .15s; }
.send-btn:disabled { opacity: .4; cursor: not-allowed; }

.loading-state { display: flex; align-items: center; gap: 10px; padding: 32px 20px; color: var(--muted); font-size: 14px; }

.body-area { padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.user-row { display: flex; align-items: center; gap: 10px; }
.av, .av-ph { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.av-ph { background: var(--primary); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; }
.uname { font-size: 15px; font-weight: 600; color: var(--on-bg); }

.title-input {
  width: 100%; border: none; border-bottom: 1px solid var(--divider);
  background: transparent; color: var(--on-bg); font-size: 17px; font-weight: 600;
  padding: 6px 0; outline: none; font-family: inherit; box-sizing: border-box;
}
.title-input::placeholder { color: var(--muted); font-weight: 400; }

.textbox { width: 100%; border: none; background: transparent; color: var(--on-bg); font-size: 16px; line-height: 1.6; resize: none; outline: none; font-family: inherit; }
.textbox::placeholder { color: var(--muted); }

/* ── Alıntı paneli ────────────────────────────────── */
.quote-panel {
  background: color-mix(in srgb, #F59E0B 6%, var(--surface-var));
  border: 1px solid color-mix(in srgb, #F59E0B 30%, transparent);
  border-radius: 14px; padding: 14px; display: flex; flex-direction: column; gap: 12px;
}
.quote-panel-header { display: flex; align-items: center; justify-content: space-between; }
.quote-panel-title { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #F59E0B; }
.quote-close { background: none; border: none; color: var(--muted); cursor: pointer; font-size: 16px; padding: 0; line-height: 1; }
.quote-input {
  width: 100%; border: none; border-bottom: 1px solid color-mix(in srgb, #F59E0B 40%, transparent);
  background: transparent; color: var(--on-bg); font-size: 15px; font-style: italic;
  line-height: 1.6; resize: none; outline: none; font-family: inherit; padding-bottom: 8px;
  box-sizing: border-box;
}
.quote-input::placeholder { color: var(--muted); font-style: italic; }

.quote-field-group { display: flex; flex-direction: column; gap: 4px; }
.quote-label { font-size: 11px; font-weight: 600; color: #F59E0B; text-transform: uppercase; letter-spacing: 0.04em; }
.quote-field {
  width: 100%; border: none; border-bottom: 1px solid var(--divider);
  background: transparent; color: var(--on-bg); font-size: 13px;
  padding: 4px 0; outline: none; font-family: inherit; box-sizing: border-box;
}
.quote-field::placeholder { color: var(--muted); }

/* ── Kitap arama ────────────────────────────────── */
.book-search-wrap { position: relative; }
.book-search-container { position: relative; }
.book-search-spinner { position: absolute; right: 0; top: 50%; transform: translateY(-50%); }

.book-dropdown {
  position: absolute; top: calc(100% + 4px); left: 0; right: 0;
  background: var(--surface); border: 1px solid var(--divider); border-radius: 12px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14); z-index: 100; overflow: hidden; max-height: 240px; overflow-y: auto;
}
.book-option {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  width: 100%; background: none; border: none; cursor: pointer; text-align: left;
  font-family: inherit; transition: background 0.1s;
}
.book-option:hover { background: var(--surface-var); }
.book-option-cover {
  width: 28px; height: 42px; border-radius: 3px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.book-option-cover.no-cover { color: #F59E0B; }
.book-option-text { display: flex; flex-direction: column; min-width: 0; }
.book-option-title { font-size: 13px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.book-option-author { font-size: 11px; color: var(--muted); margin-top: 2px; }

/* ── Seçili kitap ────────────────────────────────── */
.selected-book-row {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  background: color-mix(in srgb, var(--primary) 8%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 25%, transparent);
  border-radius: 10px;
}
.selected-book-cover {
  width: 32px; height: 48px; border-radius: 4px; object-fit: cover; flex-shrink: 0;
  background: color-mix(in srgb, #F59E0B 10%, transparent);
  display: flex; align-items: center; justify-content: center; overflow: hidden;
}
.selected-book-cover.no-cover { color: #F59E0B; }
.selected-book-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.selected-book-title { font-size: 13px; font-weight: 600; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.selected-book-author { font-size: 11px; color: var(--muted); }
.library-badge { font-size: 11px; color: var(--primary); font-weight: 600; margin-top: 2px; }
.clear-book-btn { background: none; border: none; color: var(--muted); cursor: pointer; padding: 4px; font-size: 14px; flex-shrink: 0; }
.clear-book-btn:hover { color: var(--on-bg); }

/* Kategoriler */
.categories { display: flex; flex-wrap: wrap; gap: 6px; }
.cat-chip { padding: 5px 12px; border-radius: 99px; font-size: 12px; font-weight: 500; border: 1px solid var(--divider); background: transparent; color: var(--muted); cursor: pointer; font-family: inherit; transition: all 0.15s; }
.cat-chip.selected { background: var(--primary); color: #fff; border-color: var(--primary); }
.cat-chip:hover:not(.selected) { border-color: var(--primary); color: var(--primary); }

.img-preview-wrap { position: relative; }
.img-preview { width: 100%; border-radius: 12px; max-height: 300px; object-fit: cover; display: block; }
.remove-img { position: absolute; top: 8px; right: 8px; background: rgba(0,0,0,.6); color: #fff; border: none; border-radius: 50%; width: 28px; height: 28px; font-size: 14px; cursor: pointer; display: flex; align-items: center; justify-content: center; }

.error { color: var(--error, #ef4444); font-size: 14px; }

.bottom-bar { display: flex; align-items: center; gap: 4px; padding: 10px 16px; border-top: 1px solid var(--divider); }
.tool-btn { display: flex; align-items: center; justify-content: center; width: 38px; height: 38px; border-radius: 50%; color: var(--primary); background: none; border: none; cursor: pointer; transition: background 0.15s; }
.tool-btn:hover { background: var(--surface-var); }
.tool-btn.quote-active { background: color-mix(in srgb, #F59E0B 15%, transparent); color: #F59E0B; }
.char-count { margin-left: auto; font-size: 13px; color: var(--muted); }
.char-count.warn { color: var(--error, #ef4444); }

/* Spinner */
.spinner { width: 18px; height: 18px; border: 2px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
.spinner.small { width: 14px; height: 14px; }
@keyframes spin { to { transform: rotate(360deg); } }
</style>
