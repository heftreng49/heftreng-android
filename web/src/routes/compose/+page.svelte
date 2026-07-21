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
  let imageFile = $state<File | null>(null);
  let imagePreview = $state<string | null>(null);
  let submitting = $state(false);
  let error = $state("");

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

  function removeImage() {
    imageFile = null;
    imagePreview = null;
  }

  async function submit() {
    if (!$currentUser) return;
    if (!text.trim() && !imageFile) return;
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
        title:        "",
        category:     "",
        imgUrl:       imageURL,
        imageURL:     imageURL,
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
      error = "Gonderi gonderilirken hata olustu.";
      console.error(e);
    } finally {
      submitting = false;
    }
  }

  let charCount = $derived(text.length);
  let canSubmit = $derived((text.trim().length > 0 || imageFile !== null) && !submitting);
</script>

<Navbar />
<main class="wrap">
  <div class="composer">
    <div class="top-bar">
      <a href="/feed" class="cancel">Iptal</a>
      <h2 class="title">Yeni Gonderi</h2>
      <button class="send-btn" onclick={submit} disabled={!canSubmit}>
        {submitting ? "Gonderiliyor..." : "Paylas"}
      </button>
    </div>

    <div class="body-area">
      {#if $currentUser}
        <div class="user-row">
          {#if $currentUser.photoURL}
            <img src={$currentUser.photoURL} alt="" class="av"/>
          {:else}
            <div class="av-ph">{($currentUser.displayName??"?")[0].toUpperCase()}</div>
          {/if}
          <span class="uname">{$currentUser.displayName ?? $currentUser.email}</span>
        </div>
      {/if}

      <textarea
        class="textbox"
        placeholder="Ne dusunuyorsun?"
        bind:value={text}
        maxlength={1000}
        rows={5}
      ></textarea>

      {#if imagePreview}
        <div class="img-preview-wrap">
          <img src={imagePreview} alt="" class="img-preview"/>
          <button class="remove-img" onclick={removeImage}>✕</button>
        </div>
      {/if}

      {#if error}
        <p class="error">{error}</p>
      {/if}
    </div>

    <div class="bottom-bar">
      <label class="img-btn">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
          <circle cx="8.5" cy="8.5" r="1.5"/>
          <polyline points="21 15 16 10 5 21"/>
        </svg>
        <span>Fotograf</span>
        <input type="file" accept="image/*" onchange={onFileChange} style="display:none"/>
      </label>
      <span class="char-count" class:warn={charCount > 900}>{charCount}/1000</span>
    </div>
  </div>
</main>

<style>
.wrap { max-width:600px; margin:0 auto; padding:12px; }
.composer { background:var(--card); border-radius:16px; box-shadow:0 1px 4px rgba(0,0,0,.08); overflow:hidden; }

.top-bar { display:flex; align-items:center; justify-content:space-between; padding:12px 16px; border-bottom:1px solid var(--divider); }
.cancel { font-size:15px; color:var(--muted); text-decoration:none; }
.cancel:hover { color:var(--on-bg); }
.title { font-size:16px; font-weight:600; color:var(--on-bg); }
.send-btn { padding:8px 20px; background:var(--primary); color:#fff; border-radius:20px; font-size:14px; font-weight:600; border:none; cursor:pointer; opacity:1; transition:opacity .15s; }
.send-btn:disabled { opacity:.4; cursor:not-allowed; }

.body-area { padding:16px; }
.user-row { display:flex; align-items:center; gap:10px; margin-bottom:12px; }
.av, .av-ph { width:40px; height:40px; border-radius:50%; object-fit:cover; flex-shrink:0; }
.av-ph { background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; font-size:16px; }
.uname { font-size:15px; font-weight:600; color:var(--on-bg); }
.textbox { width:100%; border:none; background:transparent; color:var(--on-bg); font-size:16px; line-height:1.6; resize:none; outline:none; font-family:inherit; }
.textbox::placeholder { color:var(--muted); }

.img-preview-wrap { position:relative; margin-top:12px; }
.img-preview { width:100%; border-radius:12px; max-height:300px; object-fit:cover; display:block; }
.remove-img { position:absolute; top:8px; right:8px; background:rgba(0,0,0,.6); color:#fff; border:none; border-radius:50%; width:28px; height:28px; font-size:14px; cursor:pointer; display:flex; align-items:center; justify-content:center; }

.error { color:var(--error); font-size:14px; margin-top:8px; }

.bottom-bar { display:flex; align-items:center; justify-content:space-between; padding:10px 16px; border-top:1px solid var(--divider); }
.img-btn { display:flex; align-items:center; gap:6px; color:var(--primary); font-size:14px; font-weight:500; cursor:pointer; }
.img-btn svg { width:20px; height:20px; }
.char-count { font-size:13px; color:var(--muted); }
.char-count.warn { color:var(--error); }
</style>
