<script lang="ts">
  import { onMount } from "svelte";
  import { goto } from "$app/navigation";
  import { collection, addDoc, Timestamp } from "firebase/firestore";
  import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
  import { db, storage } from "$lib/firebase/config";
  import { currentUser } from "$lib/store/auth";
  import { authLoading } from "$lib/store/auth";
  import Navbar from "$lib/components/Navbar.svelte";

  let text = $state("");
  let title = $state("");
  let category = $state("");
  let imageFile = $state<File | null>(null);
  let imagePreview = $state<string | null>(null);
  let submitting = $state(false);
  let error = $state("");

  // Alıntı alanı
  let quoteText = $state("");
  let bookName = $state("");
  let authorName = $state("");
  let showQuotePanel = $state(false);

  const categories = ["Şiir", "Deneme", "Hikaye", "Roman", "Alıntı", "Düşünce", "Diğer"];

  onMount(() => {
    const unsub = authLoading.subscribe(loading => {
      if (!loading && !$currentUser) goto("/login");
    });
    return unsub;
  });

  function onFileChange(e: Event) {
    const input = e.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) return;
    imageFile = file;
    imagePreview = URL.createObjectURL(file);
  }

  function removeImage() { imageFile = null; imagePreview = null; }
  function clearQuote() { quoteText = ""; bookName = ""; authorName = ""; showQuotePanel = false; }

  async function submit() {
    if (!$currentUser) return;
    if (!text.trim() && !imageFile && !quoteText.trim()) return;
    submitting = true;
    error = "";
    try {
      let imageURL = "";
      if (imageFile) {
        const storageRef = ref(storage, `posts/${$currentUser.uid}/${Date.now()}.jpg`);
        await uploadBytes(storageRef, imageFile);
        imageURL = await getDownloadURL(storageRef);
      }

      await addDoc(collection(db, "feed"), {
        uid:          $currentUser.uid,
        displayName:  $currentUser.displayName ?? "",
        name:         $currentUser.displayName ?? "",
        username:     "",
        photoURL:     $currentUser.photoURL ?? "",
        authorEmail:  $currentUser.email ?? "",
        text:         text.trim(),
        title:        title.trim(),
        category:     category,
        imgUrl:       imageURL,
        imageURL:     imageURL,
        quoteText:    quoteText.trim(),
        bookName:     bookName.trim(),
        authorName:   authorName.trim(),
        coverImg:     "",
        visibility:   "public",
        mentions:     [],
        likes:        0,
        saves:        0,
        cmtCount:     0,
        reposts:      0,
        ts:           Timestamp.now(),
      });

      goto("/feed");
    } catch(e: any) {
      error = "Gönderi gönderilirken hata oluştu.";
      console.error(e);
    } finally {
      submitting = false;
    }
  }

  let charCount = $derived(text.length);
  let canSubmit = $derived((text.trim().length > 0 || imageFile !== null || quoteText.trim().length > 0) && !submitting);
</script>

<Navbar />
<main class="wrap">
  <div class="composer">

    <!-- Üst bar -->
    <div class="top-bar">
      <a href="/feed" class="cancel">İptal</a>
      <h2 class="title-bar">Yeni Gönderi</h2>
      <button class="send-btn" onclick={submit} disabled={!canSubmit}>
        {submitting ? "Gönderiliyor..." : "Paylaş"}
      </button>
    </div>

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

      <!-- Başlık (opsiyonel) -->
      <input
        class="title-input"
        placeholder="Başlık (opsiyonel)"
        bind:value={title}
        maxlength={120}
      />

      <!-- Ana metin -->
      <textarea
        class="textbox"
        placeholder="Ne düşünüyorsun?"
        bind:value={text}
        maxlength={1000}
        rows={5}
      ></textarea>

      <!-- Alıntı paneli -->
      {#if showQuotePanel}
        <div class="quote-panel">
          <div class="quote-panel-header">
            <span class="quote-panel-title">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M3 21c3 0 7-1 7-8V5c0-1.25-.756-2.017-2-2H4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2 1 0 1 0 1 1v1c0 1-1 2-2 2s-1 .008-1 1.031V20c0 1 0 1 1 1z"/><path d="M15 21c3 0 7-1 7-8V5c0-1.25-.757-2.017-2-2h-4c-1.25 0-2 .75-2 1.972V11c0 1.25.75 2 2 2h.75c0 2.25.25 4-2.75 4v3c0 1 0 1 1 1z"/></svg>
              Alıntı Ekle
            </span>
            <button class="quote-close" onclick={clearQuote}>✕</button>
          </div>
          <textarea
            class="quote-input"
            placeholder="Alıntı metni..."
            bind:value={quoteText}
            rows={3}
          ></textarea>
          <input class="quote-field" placeholder="Kitap adı (opsiyonel)" bind:value={bookName} />
          <input class="quote-field" placeholder="Yazar adı (opsiyonel)" bind:value={authorName} />
        </div>
      {/if}

      <!-- Görsel önizleme -->
      {#if imagePreview}
        <div class="img-preview-wrap">
          <img src={imagePreview} alt="" class="img-preview"/>
          <button class="remove-img" onclick={removeImage}>✕</button>
        </div>
      {/if}

      <!-- Kategori seçimi -->
      <div class="categories">
        {#each categories as cat}
          <button
            class="cat-chip"
            class:selected={category === cat}
            onclick={() => category = category === cat ? "" : cat}
          >{cat}</button>
        {/each}
      </div>

      {#if error}
        <p class="error">{error}</p>
      {/if}
    </div>

    <!-- Alt bar -->
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

.body-area { padding: 16px; display: flex; flex-direction: column; gap: 10px; }
.user-row { display: flex; align-items: center; gap: 10px; }
.av, .av-ph { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.av-ph { background: var(--primary); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 16px; }
.uname { font-size: 15px; font-weight: 600; color: var(--on-bg); }

.title-input {
  width: 100%;
  border: none;
  border-bottom: 1px solid var(--divider);
  background: transparent;
  color: var(--on-bg);
  font-size: 17px;
  font-weight: 600;
  padding: 6px 0;
  outline: none;
  font-family: inherit;
}
.title-input::placeholder { color: var(--muted); font-weight: 400; }

.textbox { width: 100%; border: none; background: transparent; color: var(--on-bg); font-size: 16px; line-height: 1.6; resize: none; outline: none; font-family: inherit; }
.textbox::placeholder { color: var(--muted); }

/* Alıntı paneli */
.quote-panel {
  background: color-mix(in srgb, #F59E0B 6%, var(--surface-var));
  border: 1px solid color-mix(in srgb, #F59E0B 30%, transparent);
  border-radius: 14px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quote-panel-header { display: flex; align-items: center; justify-content: space-between; }
.quote-panel-title { display: flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; color: #F59E0B; }
.quote-close { background: none; border: none; color: var(--muted); cursor: pointer; font-size: 16px; padding: 0; line-height: 1; }
.quote-input { width: 100%; border: none; border-bottom: 1px solid var(--divider); background: transparent; color: var(--on-bg); font-size: 14px; font-style: italic; line-height: 1.6; resize: none; outline: none; font-family: inherit; padding-bottom: 6px; }
.quote-input::placeholder { color: var(--muted); font-style: italic; }
.quote-field { width: 100%; border: none; border-bottom: 1px solid var(--divider); background: transparent; color: var(--on-bg); font-size: 13px; padding: 4px 0; outline: none; font-family: inherit; }
.quote-field::placeholder { color: var(--muted); }

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
</style>
