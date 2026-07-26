<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/stores";
  import { doc, getDoc, collection, query, where, orderBy, limit, getDocs, startAfter, updateDoc, increment } from "firebase/firestore";
  import { ref, uploadBytes, getDownloadURL } from "firebase/storage";
  import { db, storage } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";

  const uid = $derived($page.params.uid === "me" ? ($currentUser?.uid ?? "") : $page.params.uid);

  // ── State ─────────────────────────────────────────────────────
  let user          = $state<any>(null);
  let loading       = $state(true);
  let notFound      = $state(false);
  let isMe          = $derived(uid === $currentUser?.uid);

  let posts         = $state<any[]>([]);
  let postsLoading  = $state(true);
  let hasMorePosts  = $state(false);
  let lastPostDoc   = $state<any>(null);
  const PAGE = 15;

  let followersCount = $state(0);
  let followingCount = $state(0);
  let postsCount     = $state(0);
  let isFollowing    = $state(false);
  let followLoading  = $state(false);

  let selectedTab   = $state(0);
  const tabs = ["Gönderiler", "Okuma Listesi", "Kitaplar & Seriler"];

  // Takipçi/Takip listesi
  let showFollowers  = $state(false);
  let showFollowing  = $state(false);
  let followersList  = $state<any[]>([]);
  let followingList  = $state<any[]>([]);
  let listLoading    = $state(false);

  // Düzenle modu
  let editMode      = $state(false);
  let editName      = $state("");
  let editUsername  = $state("");
  let editBio       = $state("");
  let editWebsite   = $state("");
  let editSaving    = $state(false);
  let editError     = $state("");
  let photoUploading = $state(false);
  let coverUploading = $state(false);

  // Okuma listesi
  let readingList   = $state<Record<string, any[]>>({});

  // ── Yükle ─────────────────────────────────────────────────────
  onMount(async () => {
    await loadUser();
    await loadPosts();
    await loadSocialCounts();
    if ($currentUser && !isMe) await checkFollowing();
    loadReadingList();
  });

  async function loadUser() {
    loading = true;
    try {
      // Önce Firestore users koleksiyonundan dene
      const snap = await getDoc(doc(db, "users", uid));
      if (!snap.exists()) { notFound = true; return; }
      user = { uid: snap.id, ...snap.data() };
    } catch(e) { console.error(e); notFound = true; }
    finally { loading = false; }
  }

  async function loadPosts() {
    postsLoading = true;
    try {
      const q = query(
        collection(db, "feed"),
        where("uid", "==", uid),
        orderBy("ts", "desc"),
        limit(PAGE)
      );
      const snap = await getDocs(q);
      posts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      lastPostDoc = snap.docs[snap.docs.length - 1] ?? null;
      hasMorePosts = snap.docs.length === PAGE;
      postsCount = posts.length;
      await enrichPosts(posts.map(p => p.id));
    } catch(e) { console.error(e); }
    finally { postsLoading = false; }
  }

  async function loadMorePosts() {
    if (!lastPostDoc) return;
    try {
      const q = query(
        collection(db, "feed"),
        where("uid", "==", uid),
        orderBy("ts", "desc"),
        startAfter(lastPostDoc),
        limit(PAGE)
      );
      const snap = await getDocs(q);
      const newPosts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      lastPostDoc = snap.docs[snap.docs.length - 1] ?? lastPostDoc;
      hasMorePosts = snap.docs.length === PAGE;
      await enrichPosts(newPosts.map(p => p.id));
      posts = [...posts, ...newPosts];
    } catch(e) { console.error(e); }
  }

  async function enrichPosts(ids: string[]) {
    if (!ids.length) return;
    const [lR] = await Promise.all([
      supabase.from("feed_likes").select("post_id").in("post_id", ids),
    ]);
    const counts: Record<string, number> = {};
    for (const r of lR.data ?? []) counts[r.post_id] = (counts[r.post_id] ?? 0) + 1;

    let liked = new Set<string>();
    if ($currentUser) {
      const { data } = await supabase.from("feed_likes").select("post_id")
        .eq("uid", $currentUser.uid).in("post_id", ids);
      liked = new Set((data ?? []).map((r: any) => r.post_id));
    }
    posts = posts.map(p => ids.includes(p.id) ? {
      ...p,
      likesCount: counts[p.id] ?? p.likesCount ?? 0,
      isLikedByMe: liked.has(p.id),
    } : p);
  }

  async function loadSocialCounts() {
    // Firestore users dokümanından — Android ile aynı
    try {
      const snap = await getDoc(doc(db, "users", uid));
      if (snap.exists()) {
        const d = snap.data();
        followersCount = d.followersCount ?? 0;
        followingCount = d.followingCount ?? 0;
        postsCount     = d.postsCount ?? postsCount;
      }
    } catch(e) {
      // Firestore yoksa Supabase'den say
      try {
        const [fR, gR] = await Promise.all([
          supabase.from("follows").select("id", { count: "exact", head: true }).eq("target_uid", uid),
          supabase.from("follows").select("id", { count: "exact", head: true }).eq("from_uid", uid),
        ]);
        followersCount = fR.count ?? 0;
        followingCount = gR.count ?? 0;
      } catch(e2) { console.error(e2); }
    }
  }

  async function checkFollowing() {
    if (!$currentUser) return;
    const { data } = await supabase.from("follows")
      .select("id")
      .eq("from_uid", $currentUser.uid)
      .eq("target_uid", uid)
      .maybeSingle();
    isFollowing = !!data;
  }

  async function toggleFollow() {
    if (!$currentUser) { window.location.href = "/login"; return; }
    followLoading = true;
    const was = isFollowing;
    isFollowing = !was;
    followersCount = Math.max(0, followersCount + (was ? -1 : 1));
    try {
      const id = `${$currentUser.uid}_${uid}`;
      if (was) {
        // Sil: from_uid=ben, target_uid=karşı
        await supabase.from("follows").delete()
          .eq("from_uid", $currentUser.uid)
          .eq("target_uid", uid);
        // Firestore sayaçları azalt
        await Promise.all([
          updateDoc(doc(db, "users", uid),             { followersCount: increment(-1) }),
          updateDoc(doc(db, "users", $currentUser.uid), { followingCount: increment(-1) }),
        ]);
      } else {
        // Ekle: from_uid=ben, target_uid=karşı
        await supabase.from("follows").upsert({
          id,
          from_uid:     $currentUser.uid,
          from_name:    $currentUser.displayName ?? "",
          from_photo:   $currentUser.photoURL    ?? "",
          target_uid:   uid,
          target_name:  user?.displayName ?? "",
          target_photo: user?.photoURL    ?? "",
        });
        // Firestore sayaçları artır
        await Promise.all([
          updateDoc(doc(db, "users", uid),             { followersCount: increment(1) }),
          updateDoc(doc(db, "users", $currentUser.uid), { followingCount: increment(1) }),
        ]);
      }
    } catch(e) {
      console.error(e);
      isFollowing = was;
      followersCount = Math.max(0, followersCount + (was ? 1 : -1));
    }
    followLoading = false;
  }

  async function loadFollowers() {
    listLoading = true;
    try {
      const { data } = await supabase.from("follows")
        .select("from_uid, from_name, from_photo")
        .eq("target_uid", uid)
        .order("created_at", { ascending: false })
        .limit(100);
      followersList = data ?? [];
    } catch(e) { console.error(e); }
    listLoading = false;
  }

  async function loadFollowing() {
    listLoading = true;
    try {
      const { data } = await supabase.from("follows")
        .select("target_uid, target_name, target_photo")
        .eq("from_uid", uid)
        .order("created_at", { ascending: false })
        .limit(100);
      followingList = data ?? [];
    } catch(e) { console.error(e); }
    listLoading = false;
  }

  async function loadReadingList() {
    try {
      const { data } = await supabase.from("reading_list").select("*").eq("uid", uid);
      const grouped: Record<string, any[]> = {};
      for (const r of data ?? []) {
        (grouped[r.status] ??= []).push(r);
      }
      readingList = grouped;
    } catch(e) { console.error(e); }
  }

  // ── Profil düzenle ────────────────────────────────────────────
  function openEdit() {
    editName     = user?.displayName ?? "";
    editUsername = user?.username ?? "";
    editBio      = user?.bio ?? "";
    editWebsite  = user?.website ?? "";
    editError    = "";
    editMode     = true;
  }

  async function saveProfile() {
    if (!$currentUser) return;
    editSaving = true;
    editError  = "";
    try {
      // Username benzersizlik kontrolü
      if (editUsername !== user?.username) {
        const { data } = await supabase.from("users").select("uid")
          .eq("username", editUsername.trim()).neq("uid", uid).maybeSingle();
        if (data) { editError = "Bu kullanıcı adı zaten alınmış."; editSaving = false; return; }
      }
      await updateDoc(doc(db, "users", uid), {
        displayName: editName.trim(),
        username:    editUsername.trim().toLowerCase(),
        bio:         editBio.trim(),
        website:     editWebsite.trim(),
      });
      // Supabase users tablosunu da güncelle
      await supabase.from("users").upsert({
        uid, username: editUsername.trim().toLowerCase(),
        display_name: editName.trim(), bio: editBio.trim(), website: editWebsite.trim(),
      });
      user = { ...user, displayName: editName.trim(), username: editUsername.trim(), bio: editBio.trim(), website: editWebsite.trim() };
      editMode = false;
    } catch(e: any) { editError = e.message ?? "Bir hata oluştu."; }
    finally { editSaving = false; }
  }

  async function uploadPhoto(e: Event, type: "avatar" | "cover") {
    if (!$currentUser) return;
    const file = (e.target as HTMLInputElement).files?.[0];
    if (!file) return;
    if (type === "avatar") photoUploading = true;
    else coverUploading = true;
    try {
      const path = type === "avatar"
        ? `avatars/${uid}/${Date.now()}.jpg`
        : `covers/${uid}/${Date.now()}.jpg`;
      const storageRef = ref(storage, path);
      await uploadBytes(storageRef, file);
      const url = await getDownloadURL(storageRef);
      const field = type === "avatar" ? "photoURL" : "coverPhoto";
      await updateDoc(doc(db, "users", uid), { [field]: url });
      user = { ...user, [field]: url };
    } catch(e) { console.error(e); }
    finally {
      if (type === "avatar") photoUploading = false;
      else coverUploading = false;
    }
  }

  // ── Beğeni toggle ─────────────────────────────────────────────
  async function toggleLike(p: any) {
    if (!$currentUser) { window.location.href = "/login"; return; }
    const was = p.isLikedByMe;
    posts = posts.map(x => x.id === p.id ? {
      ...x, isLikedByMe: !was,
      likesCount: Math.max(0, (x.likesCount ?? 0) + (was ? -1 : 1)),
    } : x);
    const id = `${p.id}_${$currentUser.uid}`;
    if (was) await supabase.from("feed_likes").delete().eq("post_id", p.id).eq("uid", $currentUser.uid);
    else await supabase.from("feed_likes").upsert({ id, post_id: p.id, uid: $currentUser.uid, name: $currentUser.displayName ?? "", photo_url: $currentUser.photoURL ?? "" });
  }

  function ago(ts: any): string {
    const ms = ts?.seconds ? ts.seconds * 1000 : Number(ts);
    const diff = Date.now() - ms;
    const m = Math.floor(diff / 60000);
    const h = Math.floor(diff / 3600000);
    const d = Math.floor(diff / 86400000);
    if (m < 1) return "şimdi";
    if (m < 60) return `${m}dk`;
    if (h < 24) return `${h}sa`;
    if (d < 7)  return `${d}g`;
    return `${Math.floor(d/30)}ay`;
  }
</script>

<svelte:head>
  <title>{user?.displayName ?? "Profil"} — Heftreng</title>
</svelte:head>

<main class="wrap">

  {#if loading}
    <!-- Skeleton -->
    <div class="cover-sk"></div>
    <div class="header-sk">
      <div class="av-sk"></div>
      <div class="sk-lines">
        <div class="sk-line" style="width:50%"></div>
        <div class="sk-line" style="width:35%"></div>
        <div class="sk-line" style="width:70%;margin-top:8px"></div>
      </div>
    </div>

  {:else if notFound}
    <div class="not-found">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="64" height="64" style="color:var(--muted)"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/><line x1="18" y1="9" x2="22" y2="9"/></svg>
      <h3>Bu hesap mevcut değil</h3>
      <p>Silinmiş veya askıya alınmış olabilir.</p>
      <button onclick={() => history.back()} class="back-btn">Geri Dön</button>
    </div>

  {:else}

    <!-- ── Kapak fotoğrafı ─────────────────────────────────────── -->
    <div class="cover-wrap">
      {#if user?.coverPhoto}
        <img src={user.coverPhoto} alt="" class="cover-img" />
      {:else}
        <div class="cover-placeholder"></div>
      {/if}
      {#if isMe && editMode}
        <label class="cover-edit-btn">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
          {coverUploading ? "Yükleniyor..." : "Kapak Değiştir"}
          <input type="file" accept="image/*" style="display:none" onchange={(e) => uploadPhoto(e, 'cover')} />
        </label>
      {/if}
    </div>

    <!-- ── Profil başlığı ──────────────────────────────────────── -->
    <div class="profile-header">
      <!-- Avatar + butonlar satırı -->
      <div class="av-row">
        <div class="av-wrap">
          {#if isMe && editMode}
            <label class="av-edit">
              {#if user?.photoURL}
                <img src={user.photoURL} alt="" class="avatar" />
              {:else}
                <div class="avatar avatar-fallback">{(user?.displayName ?? "?")[0].toUpperCase()}</div>
              {/if}
              <div class="av-overlay">
                {#if photoUploading}
                  <div class="spinner"></div>
                {:else}
                  <svg viewBox="0 0 24 24" fill="none" stroke="white" stroke-width="2" width="18" height="18"><path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z"/><circle cx="12" cy="13" r="4"/></svg>
                {/if}
              </div>
              <input type="file" accept="image/*" style="display:none" onchange={(e) => uploadPhoto(e, 'avatar')} />
            </label>
          {:else}
            <div class="av-ring">
              {#if user?.photoURL}
                <img src={user.photoURL} alt={user.displayName} class="avatar" />
              {:else}
                <div class="avatar avatar-fallback">{(user?.displayName ?? "?")[0].toUpperCase()}</div>
              {/if}
            </div>
          {/if}
        </div>

        <!-- Sağ taraf butonlar -->
        <div class="header-btns">
          {#if isMe}
            {#if editMode}
              <button class="btn-cancel" onclick={() => editMode = false}>İptal</button>
              <button class="btn-save" onclick={saveProfile} disabled={editSaving}>
                {editSaving ? "Kaydediliyor..." : "Kaydet"}
              </button>
            {:else}
              <button class="btn-edit" onclick={openEdit}>Profili Düzenle</button>
              <a href="/settings" class="btn-icon" aria-label="Ayarlar">
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
              </a>
            {/if}
          {:else}
            <a href="/messages?uid={uid}" class="btn-icon" aria-label="Mesaj">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            </a>
            <button
              class="btn-follow"
              class:following={isFollowing}
              onclick={toggleFollow}
              disabled={followLoading}
            >
              {isFollowing ? "Takip Ediliyor" : "Takip Et"}
            </button>
          {/if}
        </div>
      </div>

      <!-- Düzenleme formu -->
      {#if editMode}
        <div class="edit-form">
          {#if editError}<p class="edit-error">{editError}</p>{/if}
          <div class="edit-field">
            <label>Ad Soyad</label>
            <input bind:value={editName} maxlength={50} placeholder="Adınız" />
          </div>
          <div class="edit-field">
            <label>Kullanıcı adı</label>
            <div class="input-prefix">
              <span>@</span>
              <input bind:value={editUsername} maxlength={30}
                oninput={(e) => editUsername = e.currentTarget.value.toLowerCase().replace(/[^a-z0-9_]/g,'')}
                placeholder="kullanici_adi" />
            </div>
          </div>
          <div class="edit-field">
            <label>Bio</label>
            <textarea bind:value={editBio} maxlength={200} rows={3} placeholder="Kendinizden bahsedin..."></textarea>
          </div>
          <div class="edit-field">
            <label>Website</label>
            <input bind:value={editWebsite} placeholder="https://..." />
          </div>
        </div>

      {:else}
        <!-- Profil bilgileri -->
        <div class="profile-info">
          <h1 class="display-name">{user?.displayName ?? ""}</h1>
          {#if user?.username}<p class="username">@{user.username}</p>{/if}
          {#if user?.bio}<p class="bio">{user.bio}</p>{/if}
          {#if user?.website}
            <a href={user.website} target="_blank" rel="noopener" class="website">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="12" height="12"><path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71"/><path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71"/></svg>
              {user.website.replace(/^https?:\/\//, '')}
            </a>
          {/if}
        </div>

        <!-- İstatistikler -->
        <div class="stats-row">
          <button class="stat-item" onclick={() => null}>
            <span class="stat-num">{postsCount}</span>
            <span class="stat-lbl">Gönderi</span>
          </button>
          <button class="stat-item" onclick={async () => { showFollowers = true; await loadFollowers(); }}>
            <span class="stat-num">{followersCount}</span>
            <span class="stat-lbl">Takipçi</span>
          </button>
          <button class="stat-item" onclick={async () => { showFollowing = true; await loadFollowing(); }}>
            <span class="stat-num">{followingCount}</span>
            <span class="stat-lbl">Takip</span>
          </button>
          {#if (user?.xp ?? 0) > 0}
            <div class="stat-item">
              <span class="stat-num" style="color:#F59E0B">{user.xp}</span>
              <span class="stat-lbl">XP</span>
            </div>
          {/if}
        </div>

        <!-- Okuma özeti hero -->
        {#if (user?.booksRead ?? 0) > 0 || (user?.streak ?? 0) > 0}
          <div class="reading-hero">
            <div class="rh-stat">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
              <strong>{user?.booksRead ?? 0}</strong>
              <span>kitap okudum</span>
            </div>
            <div class="rh-div"></div>
            <div class="rh-stat">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="18" height="18"><path d="M6 17h3l2-4V7H5v6h3zm8 0h3l2-4V7h-6v6h3z"/></svg>
              <strong>{posts.filter(p => p.quoteText).length}</strong>
              <span>alıntı</span>
            </div>
            <div class="rh-div"></div>
            <div class="rh-stat" style="color:#F59E0B">
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M13.5 0.67s.74 2.65.74 4.8c0 2.06-1.35 3.73-3.41 3.73-2.07 0-3.63-1.67-3.63-3.73l.03-.36C5.21 7.51 4 10.62 4 14c0 4.42 3.58 8 8 8s8-3.58 8-8C20 8.61 17.41 3.8 13.5.67z"/></svg>
              <strong>{user?.streak ?? 0}</strong>
              <span>gün streak</span>
            </div>
          </div>
        {/if}
      {/if}
    </div>

    <!-- ── Sticky sekmeler ─────────────────────────────────────── -->
    <div class="tabs-bar">
      {#each tabs as tab, i}
        <button class="tab" class:active={selectedTab === i} onclick={() => selectedTab = i}>
          {tab}
        </button>
      {/each}
      <div class="tab-indicator" style="transform: translateX({selectedTab * 100}%)"></div>
    </div>

    <!-- ── Tab içerikleri ──────────────────────────────────────── -->

    <!-- Gönderiler -->
    {#if selectedTab === 0}
      {#if postsLoading && posts.length === 0}
        {#each Array(3) as _}
          <div class="post-sk">
            <div class="sk-av-sm"></div>
            <div class="sk-lines" style="flex:1">
              <div class="sk-line" style="width:50%"></div>
              <div class="sk-line" style="width:80%;margin-top:6px"></div>
            </div>
          </div>
        {/each}
      {:else if posts.length === 0}
        <div class="empty-tab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/></svg>
          <p>Henüz gönderi yok.</p>
          {#if isMe}<a href="/compose" class="empty-link">İlk gönderiyi yaz →</a>{/if}
        </div>
      {:else}
        {#each posts as p (p.id)}
          <article class="post-card" onclick={() => window.location.href = '/post/' + p.id} role="button" tabindex="0">
            <div class="pc-head">
              <div class="pc-meta">
                <span class="pc-name">{p.displayName ?? user?.displayName ?? ""}</span>
                <span class="pc-time">{ago(p.ts)}</span>
              </div>
            </div>
            {#if p.title}<h3 class="pc-title">{p.title}</h3>{/if}
            {#if p.text}<p class="pc-text">{p.text}</p>{/if}
            {#if p.imgUrl || p.imageURL}
              <img src={p.imgUrl || p.imageURL} alt="" class="pc-img" onclick={(e) => e.stopPropagation()} />
            {/if}
            <!-- QuoteCard mini -->
            {#if p.quoteText}
              <div class="pc-quote">
                <p class="pc-quote-text">❝ {p.quoteText}</p>
                {#if p.bookName}<span class="pc-quote-book">{p.bookName}</span>{/if}
              </div>
            {/if}
            <div class="pc-actions" onclick={(e) => e.stopPropagation()}>
              <button class="pc-act" class:liked={p.isLikedByMe} onclick={() => toggleLike(p)}>
                {#if p.isLikedByMe}
                  <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                {:else}
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                {/if}
                {#if (p.likesCount ?? 0) > 0}<span>{p.likesCount}</span>{/if}
              </button>
              <button class="pc-act" onclick={() => window.location.href = '/post/' + p.id}>
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
                {#if (p.commentsCount ?? 0) > 0}<span>{p.commentsCount}</span>{/if}
              </button>
              {#if isMe}
                <a href="/compose?edit={p.id}" class="pc-act" onclick={(e) => e.stopPropagation()}>
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                </a>
              {/if}
            </div>
          </article>
          <div class="post-divider"></div>
        {/each}

        {#if hasMorePosts}
          <div class="load-more">
            <button class="load-more-btn" onclick={loadMorePosts}>Daha fazla yükle</button>
          </div>
        {/if}
      {/if}

    <!-- Okuma listesi -->
    {:else if selectedTab === 1}
      {#if Object.keys(readingList).length === 0}
        <div class="empty-tab">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          <p>Okuma listesi boş.</p>
        </div>
      {:else}
        {#each [["okuyor", "📖 Şu an okuyor"], ["okuyacak", "📚 Okuyacak"], ["okudum", "✅ Okudum"]] as [status, label]}
          {#if readingList[status]?.length}
            <div class="rl-section">
              <h3 class="rl-header">{label} <span>{readingList[status].length}</span></h3>
              {#each readingList[status] as entry}
                <div class="rl-item">
                  {#if entry.cover_url}
                    <img src={entry.cover_url} alt={entry.book_name} class="rl-cover" />
                  {:else}
                    <div class="rl-cover rl-cover-ph">📖</div>
                  {/if}
                  <div class="rl-info">
                    <span class="rl-book">{entry.book_name ?? "—"}</span>
                    {#if entry.author_name}<span class="rl-author">{entry.author_name}</span>{/if}
                  </div>
                </div>
              {/each}
            </div>
          {/if}
        {/each}
      {/if}

    <!-- Kitaplar & Seriler -->
    {:else}
      <div class="empty-tab">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="44" height="44"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
        <p>Henüz kitap veya seri yok.</p>
        {#if isMe}<a href="/library" class="empty-link">Kütüphaneye git →</a>{/if}
      </div>
    {/if}

    <div style="height:80px"></div>
  {/if}
</main>

<!-- ── Takipçiler sheet ────────────────────────────────────────── -->
{#if showFollowers}
  <div class="sheet-backdrop" onclick={() => showFollowers = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Takipçiler <span class="sheet-count">{followersCount}</span></span>
      <button class="sheet-close" onclick={() => showFollowers = false}>✕</button>
    </div>
    <div class="sheet-list">
      {#if listLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else if followersList.length === 0}
        <p class="sheet-empty">Henüz takipçi yok.</p>
      {:else}
        {#each followersList as f}
          <a href="/profile/{f.from_uid}" class="user-row" onclick={() => showFollowers = false}>
            <div class="user-av">
              {#if f.from_photo}<img src={f.from_photo} alt={f.from_name} />{:else}<span>{(f.from_name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="user-name">{f.from_name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Takip edilenler sheet ──────────────────────────────────── -->
{#if showFollowing}
  <div class="sheet-backdrop" onclick={() => showFollowing = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Takip Edilenler <span class="sheet-count">{followingCount}</span></span>
      <button class="sheet-close" onclick={() => showFollowing = false}>✕</button>
    </div>
    <div class="sheet-list">
      {#if listLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else if followingList.length === 0}
        <p class="sheet-empty">Henüz takip edilen yok.</p>
      {:else}
        {#each followingList as f}
          <a href="/profile/{f.target_uid}" class="user-row" onclick={() => showFollowing = false}>
            <div class="user-av">
              {#if f.target_photo}<img src={f.target_photo} alt={f.target_name} />{:else}<span>{(f.target_name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="user-name">{f.target_name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<style>
.wrap { max-width: 600px; margin: 0 auto; padding-bottom: 80px; }

/* Kapak */
.cover-wrap { position: relative; width: 100%; height: 120px; background: var(--surface-var); overflow: hidden; }
.cover-img { width: 100%; height: 100%; object-fit: cover; }
.cover-placeholder { width: 100%; height: 100%; background: linear-gradient(135deg, color-mix(in srgb,var(--primary) 20%,transparent), color-mix(in srgb,var(--primary) 8%,transparent)); }
.cover-edit-btn { position: absolute; bottom: 8px; right: 10px; display: flex; align-items: center; gap: 6px; padding: 6px 12px; background: rgba(0,0,0,0.55); color: #fff; border-radius: 20px; font-size: 12px; font-weight: 500; cursor: pointer; }

/* Profil başlığı */
.profile-header { padding: 0 16px 12px; background: var(--card); border-bottom: 1px solid var(--divider); }

/* Avatar satırı */
.av-row { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 12px; }
.av-wrap { margin-top: -28px; }
.av-ring { width: 78px; height: 78px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), color-mix(in srgb,var(--primary) 60%,purple)); padding: 2px; display: flex; align-items: center; justify-content: center; border: 3px solid var(--card); }
.avatar { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.avatar-fallback { background: var(--surface-var); display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; color: var(--on-bg); }
.av-edit { position: relative; cursor: pointer; display: block; width: 78px; height: 78px; border-radius: 50%; margin-top: -28px; border: 3px solid var(--card); }
.av-edit img, .av-edit .avatar-fallback { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.av-overlay { position: absolute; inset: 0; border-radius: 50%; background: rgba(0,0,0,0.45); display: flex; align-items: center; justify-content: center; }

/* Header butonlar */
.header-btns { display: flex; align-items: center; gap: 8px; padding-bottom: 4px; }
.btn-edit { padding: 8px 16px; border: 1.5px solid var(--divider); border-radius: 10px; background: none; color: var(--on-bg); font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit; transition: background 0.15s; }
.btn-edit:hover { background: var(--surface-var); }
.btn-save { padding: 8px 16px; border: none; border-radius: 10px; background: var(--primary); color: #fff; font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit; }
.btn-save:disabled { opacity: 0.6; }
.btn-cancel { padding: 8px 12px; border: 1.5px solid var(--divider); border-radius: 10px; background: none; color: var(--muted); font-size: 13px; cursor: pointer; font-family: inherit; }
.btn-icon { width: 36px; height: 36px; border-radius: 10px; background: var(--surface-var); border: none; display: flex; align-items: center; justify-content: center; color: var(--on-bg); text-decoration: none; cursor: pointer; transition: background 0.15s; }
.btn-icon:hover { background: var(--divider); }
.btn-follow { padding: 8px 18px; border: none; border-radius: 10px; background: #F59E0B; color: #000; font-size: 13px; font-weight: 700; cursor: pointer; font-family: inherit; transition: background 0.15s, color 0.15s; }
.btn-follow.following { background: var(--surface-var); color: var(--on-bg); border: 1.5px solid var(--divider); }
.btn-follow:disabled { opacity: 0.6; }

/* Profil bilgileri */
.profile-info { margin-bottom: 12px; }
.display-name { font-size: 20px; font-weight: 800; color: var(--on-bg); margin: 0 0 2px; }
.username { font-size: 13px; color: var(--muted); margin: 0 0 6px; }
.bio { font-size: 14px; color: var(--on-bg); line-height: 1.6; margin: 0 0 6px; white-space: pre-wrap; }
.website { display: inline-flex; align-items: center; gap: 4px; font-size: 12px; color: #F59E0B; text-decoration: none; }
.website:hover { text-decoration: underline; }

/* İstatistikler */
.stats-row { display: flex; gap: 20px; margin-bottom: 12px; }
.stat-item { display: flex; flex-direction: column; align-items: center; background: none; border: none; cursor: pointer; padding: 0; font-family: inherit; }
.stat-num { font-size: 18px; font-weight: 800; color: var(--on-bg); }
.stat-lbl { font-size: 11px; color: var(--primary); font-weight: 500; }

/* Okuma hero */
.reading-hero { display: flex; align-items: center; justify-content: space-evenly; background: linear-gradient(135deg, color-mix(in srgb,var(--primary) 12%,transparent), color-mix(in srgb,#F59E0B 8%,transparent)); border-radius: 14px; padding: 14px 8px; margin: 4px 0 8px; }
.rh-stat { display: flex; flex-direction: column; align-items: center; gap: 3px; font-size: 11px; color: var(--muted); }
.rh-stat strong { font-size: 16px; font-weight: 800; color: var(--on-bg); }
.rh-div { width: 1px; height: 32px; background: var(--divider); }

/* Düzenleme formu */
.edit-form { display: flex; flex-direction: column; gap: 12px; margin-top: 4px; }
.edit-error { background: rgba(239,68,68,0.1); color: #ef4444; padding: 8px 12px; border-radius: 8px; font-size: 13px; }
.edit-field { display: flex; flex-direction: column; gap: 4px; }
.edit-field label { font-size: 12px; font-weight: 600; color: var(--muted); }
.edit-field input, .edit-field textarea {
  border: 1.5px solid var(--divider); border-radius: 10px;
  padding: 10px 12px; font-size: 14px; background: var(--surface-var);
  color: var(--on-bg); font-family: inherit; outline: none; resize: vertical;
}
.edit-field input:focus, .edit-field textarea:focus { border-color: var(--primary); }
.input-prefix { display: flex; align-items: center; border: 1.5px solid var(--divider); border-radius: 10px; background: var(--surface-var); overflow: hidden; }
.input-prefix span { padding: 10px 0 10px 12px; color: var(--muted); font-size: 14px; }
.input-prefix input { flex: 1; border: none; background: none; padding: 10px 12px 10px 4px; font-size: 14px; color: var(--on-bg); font-family: inherit; outline: none; }

/* Sekmeler */
.tabs-bar { display: flex; position: sticky; top: 52px; background: var(--bg); border-bottom: 1px solid var(--divider); z-index: 10; overflow: hidden; }
.tab { flex: 1; padding: 13px 4px; font-size: 13px; font-weight: 500; color: var(--muted); background: none; border: none; cursor: pointer; position: relative; z-index: 1; transition: color 0.2s; font-family: inherit; }
.tab.active { color: var(--on-bg); font-weight: 700; }
.tab-indicator { position: absolute; bottom: 0; left: 0; width: 33.33%; height: 2.5px; background: linear-gradient(90deg, var(--primary), color-mix(in srgb,var(--primary) 60%,purple)); border-radius: 2px 2px 0 0; transition: transform 0.25s cubic-bezier(.4,0,.2,1); pointer-events: none; }

/* Post kartları */
.post-card { padding: 14px 16px 10px; cursor: pointer; background: var(--card); }
.post-card:hover { background: color-mix(in srgb, var(--surface-var) 50%, var(--card)); }
.post-divider { height: 0.5px; background: var(--divider); }
.pc-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.pc-meta { display: flex; align-items: center; gap: 6px; }
.pc-name { font-size: 13px; font-weight: 700; color: var(--on-bg); }
.pc-time { font-size: 11px; color: var(--muted); }
.pc-title { font-size: 16px; font-weight: 700; color: var(--on-bg); margin: 0 0 5px; }
.pc-text { font-size: 15px; color: var(--on-bg); line-height: 1.65; white-space: pre-wrap; display: -webkit-box; -webkit-line-clamp: 5; -webkit-box-orient: vertical; overflow: hidden; margin: 0 0 6px; }
.pc-img { width: 100%; border-radius: 10px; max-height: 280px; object-fit: cover; margin-bottom: 8px; display: block; }
.pc-quote { background: color-mix(in srgb,#F59E0B 8%,transparent); border: 1px solid color-mix(in srgb,#F59E0B 25%,transparent); border-radius: 10px; padding: 10px 12px; margin-bottom: 8px; }
.pc-quote-text { font-size: 13px; font-style: italic; color: var(--on-surface); margin: 0 0 4px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.pc-quote-book { font-size: 11px; color: #F59E0B; font-weight: 600; }
.pc-actions { display: flex; align-items: center; gap: 4px; margin-top: 4px; }
.pc-act { display: flex; align-items: center; gap: 4px; padding: 6px 10px; border-radius: 20px; color: var(--muted); font-size: 13px; cursor: pointer; border: none; background: transparent; transition: color 0.15s, background 0.15s; text-decoration: none; font-family: inherit; }
.pc-act:hover { background: var(--surface-var); }
.pc-act.liked { color: #FF3A5C; }

/* Okuma listesi */
.rl-section { padding: 12px 16px; }
.rl-header { font-size: 13px; font-weight: 700; color: var(--muted); margin: 0 0 10px; display: flex; align-items: center; gap: 6px; }
.rl-header span { background: var(--surface-var); border-radius: 99px; padding: 1px 7px; font-size: 11px; }
.rl-item { display: flex; align-items: center; gap: 10px; padding: 8px 0; border-bottom: 0.5px solid var(--divider); }
.rl-cover { width: 36px; height: 50px; border-radius: 4px; object-fit: cover; flex-shrink: 0; }
.rl-cover-ph { background: var(--surface-var); display: flex; align-items: center; justify-content: center; font-size: 18px; }
.rl-info { display: flex; flex-direction: column; gap: 3px; }
.rl-book { font-size: 14px; font-weight: 600; color: var(--on-bg); }
.rl-author { font-size: 12px; color: var(--muted); }

/* Daha fazla yükle */
.load-more { padding: 12px 16px; display: flex; justify-content: center; }
.load-more-btn { padding: 10px 24px; border: 1.5px solid var(--divider); border-radius: 99px; background: none; color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }

/* Boş state */
.empty-tab { display: flex; flex-direction: column; align-items: center; padding: 48px 20px; gap: 10px; color: var(--muted); }
.empty-tab p { font-size: 14px; }
.empty-link { color: var(--primary); font-weight: 600; font-size: 13px; text-decoration: none; }

/* Not found */
.not-found { display: flex; flex-direction: column; align-items: center; padding: 80px 20px; gap: 12px; text-align: center; }
.not-found h3 { font-size: 17px; color: var(--on-bg); margin: 0; }
.not-found p { font-size: 13px; color: var(--muted); margin: 0; }
.back-btn { background: none; border: none; color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }

/* Sheet */
.sheet-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 300; }
.sheet { position: fixed; bottom: 0; left: 0; right: 0; max-width: 600px; margin: 0 auto; background: var(--surface); border-radius: 20px 20px 0 0; z-index: 301; display: flex; flex-direction: column; max-height: 70vh; }
.sheet-handle { width: 40px; height: 4px; background: var(--divider); border-radius: 2px; margin: 12px auto 4px; flex-shrink: 0; }
.sheet-header { display: flex; align-items: center; justify-content: space-between; padding: 0 16px 12px; border-bottom: 1px solid var(--divider); flex-shrink: 0; }
.sheet-title { font-size: 15px; font-weight: 700; color: var(--on-bg); }
.sheet-count { font-size: 12px; color: var(--muted); margin-left: 4px; font-weight: 400; }
.sheet-close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; }
.sheet-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.sheet-loading { display: flex; justify-content: center; padding: 24px; }
.sheet-empty { text-align: center; color: var(--muted); font-size: 14px; padding: 24px 0; }
.user-row { display: flex; align-items: center; gap: 10px; padding: 10px 16px; text-decoration: none; transition: background 0.1s; }
.user-row:hover { background: var(--surface-var); }
.user-av { width: 40px; height: 40px; border-radius: 50%; background: var(--surface-var); overflow: hidden; display: flex; align-items: center; justify-content: center; font-size: 15px; font-weight: 700; color: var(--on-bg); flex-shrink: 0; }
.user-av img { width: 100%; height: 100%; object-fit: cover; }
.user-name { flex: 1; font-size: 14px; font-weight: 600; color: var(--on-bg); }

/* Skeleton */
.cover-sk { width: 100%; height: 120px; background: var(--shimmer); animation: shimmer 1.4s ease-in-out infinite; }
.header-sk { display: flex; gap: 12px; padding: 16px; }
.av-sk { width: 78px; height: 78px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }
.sk-lines { flex: 1; display: flex; flex-direction: column; gap: 8px; padding-top: 8px; }
.sk-line { height: 13px; background: var(--shimmer); border-radius: 6px; animation: shimmer 1.4s ease-in-out infinite; }
.post-sk { display: flex; gap: 10px; padding: 14px 16px; }
.sk-av-sm { width: 36px; height: 36px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }

/* Spinner */
.spinner { width: 18px; height: 18px; border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff; border-radius: 50%; animation: spin 0.7s linear infinite; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
