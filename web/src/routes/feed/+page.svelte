<script lang="ts">
  import { onMount } from "svelte";
  import { collection, query, orderBy, limit, getDocs, deleteDoc, doc, startAfter } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";

  let posts        = $state<any[]>([]);
  let loading      = $state(true);
  let menuOpenId   = $state<string | null>(null);
  let activeTab    = $state(0);
  let expandedIds  = $state<Set<string>>(new Set());
  let lastDoc      = $state<any>(null);
  let hasMore      = $state(false);
  let loadingMore  = $state(false);
  const PAGE_SIZE  = 20;

  // ── Yorum paneli ─────────────────────────────────────────────
  let commentPostId   = $state<string | null>(null);
  let comments        = $state<any[]>([]);
  let commentsLoading = $state(false);
  let commentText     = $state("");
  let commentSending  = $state(false);
  let replyTo         = $state<any | null>(null);

  // ── Beğenen listesi ──────────────────────────────────────────
  let likersPostId   = $state<string | null>(null);
  let likers         = $state<any[]>([]);
  let likersLoading  = $state(false);
  let likersSort     = $state<'new'|'old'|'mixed'>('new');

  // ── Zaman formatı ────────────────────────────────────────────
  function ago(ts: any): string {
    const ms   = ts?.seconds ? ts.seconds * 1000 : Number(ts);
    const diff = Date.now() - ms;
    const m = Math.floor(diff / 60000);
    const h = Math.floor(diff / 3600000);
    const d = Math.floor(diff / 86400000);
    if (m < 1)   return "şimdi";
    if (m < 60)  return `${m}dk`;
    if (h < 24)  return `${h}sa`;
    if (d < 7)   return `${d}g`;
    if (d < 30)  return `${Math.floor(d/7)}hf`;
    if (d < 365) return `${Math.floor(d/30)}ay`;
    return `${Math.floor(d/365)}y`;
  }

  function toggleExpand(id: string) {
    const next = new Set(expandedIds);
    next.has(id) ? next.delete(id) : next.add(id);
    expandedIds = next;
  }

  // ── Veri yükleme ─────────────────────────────────────────────
  onMount(async () => {
    await loadPosts();
    document.addEventListener("click", () => { menuOpenId = null; });
  });

  async function loadPosts() {
    loading = true;
    try {
      const q    = query(collection(db, "feed"), orderBy("ts", "desc"), limit(PAGE_SIZE));
      const snap = await getDocs(q);
      posts   = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      lastDoc = snap.docs[snap.docs.length - 1] ?? null;
      hasMore = snap.docs.length === PAGE_SIZE;
      await enrichPosts(posts.map(p => p.id));
    } catch(e) { console.error(e); }
    finally { loading = false; }
  }

  async function loadMore() {
    if (!lastDoc || loadingMore) return;
    loadingMore = true;
    try {
      const q    = query(collection(db, "feed"), orderBy("ts", "desc"), startAfter(lastDoc), limit(PAGE_SIZE));
      const snap = await getDocs(q);
      const newPosts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      lastDoc = snap.docs[snap.docs.length - 1] ?? lastDoc;
      hasMore = snap.docs.length === PAGE_SIZE;
      posts   = [...posts, ...newPosts];
      await enrichPosts(newPosts.map(p => p.id));
    } catch(e) { console.error(e); }
    finally { loadingMore = false; }
  }

  // Beğeni sayısı + yorum sayısı + kişisel etkileşim durumu
  async function enrichPosts(ids: string[]) {
    if (!ids.length) return;

    // Beğeni sayıları
    const { data: likeRows } = await supabase
      .from("feed_likes").select("post_id").in("post_id", ids);
    const likeCounts: Record<string, number> = {};
    for (const r of likeRows ?? [])
      likeCounts[r.post_id] = (likeCounts[r.post_id] ?? 0) + 1;

    // Yorum sayıları
    const { data: cmtRows } = await supabase
      .from("feed_comments").select("post_id").in("post_id", ids);
    const cmtCounts: Record<string, number> = {};
    for (const r of cmtRows ?? [])
      cmtCounts[r.post_id] = (cmtCounts[r.post_id] ?? 0) + 1;

    // Kişisel etkileşim (giriş yapmışsa)
    let likedSet  = new Set<string>();
    let savedSet  = new Set<string>();
    if ($currentUser) {
      const [lR, sR] = await Promise.all([
        supabase.from("feed_likes").select("post_id")
          .eq("uid", $currentUser.uid).in("post_id", ids),
        supabase.from("feed_saves").select("post_id")
          .eq("uid", $currentUser.uid).in("post_id", ids),
      ]);
      likedSet = new Set((lR.data ?? []).map((r: any) => r.post_id));
      savedSet = new Set((sR.data ?? []).map((r: any) => r.post_id));
    }

    posts = posts.map(p => ids.includes(p.id) ? {
      ...p,
      likesCount:    likeCounts[p.id]  ?? p.likesCount    ?? 0,
      commentsCount: cmtCounts[p.id]   ?? p.commentsCount ?? 0,
      isLikedByMe:   likedSet.has(p.id),
      isSavedByMe:   savedSet.has(p.id),
    } : p);
  }

  // ── Beğeni ───────────────────────────────────────────────────
  async function toggleLike(p: any) {
    if (!$currentUser) { window.location.href = "/login"; return; }
    const wasLiked = p.isLikedByMe;
    // Optimistic UI
    posts = posts.map(x => x.id === p.id ? {
      ...x,
      isLikedByMe: !wasLiked,
      likesCount: Math.max(0, (x.likesCount ?? 0) + (wasLiked ? -1 : 1)),
    } : x);
    try {
      const id = `${p.id}_${$currentUser.uid}`;
      if (wasLiked) {
        await supabase.from("feed_likes").delete()
          .eq("post_id", p.id).eq("uid", $currentUser.uid);
      } else {
        await supabase.from("feed_likes").upsert({
          id, post_id: p.id, uid: $currentUser.uid,
          name:      $currentUser.displayName ?? "",
          photo_url: $currentUser.photoURL    ?? "",
        });
      }
    } catch(e) {
      console.error(e);
      // Geri al
      posts = posts.map(x => x.id === p.id ? {
        ...x, isLikedByMe: wasLiked,
        likesCount: Math.max(0, (x.likesCount ?? 0) + (wasLiked ? 1 : -1)),
      } : x);
    }
  }

  // ── Kaydet ───────────────────────────────────────────────────
  async function toggleSave(p: any) {
    if (!$currentUser) { window.location.href = "/login"; return; }
    const wasSaved = p.isSavedByMe;
    posts = posts.map(x => x.id === p.id ? { ...x, isSavedByMe: !wasSaved } : x);
    try {
      const id = `${p.id}_${$currentUser.uid}`;
      if (wasSaved) {
        await supabase.from("feed_saves").delete().eq("id", id);
      } else {
        await supabase.from("feed_saves").upsert({ id, post_id: p.id, uid: $currentUser.uid });
      }
    } catch(e) {
      console.error(e);
      posts = posts.map(x => x.id === p.id ? { ...x, isSavedByMe: wasSaved } : x);
    }
  }

  // ── Beğenenler ───────────────────────────────────────────────
  async function openLikers(p: any, e: Event) {
    e.stopPropagation();
    if (!(p.likesCount > 0)) return;
    likersPostId  = p.id;
    likersSort    = 'new';
    await loadLikers(p.id);
  }

  function closeLikers() { likersPostId = null; likers = []; }

  async function loadLikers(postId: string) {
    likersLoading = true;
    try {
      const { data } = await supabase
        .from("feed_likes")
        .select("uid, name, photo_url, created_at")
        .eq("post_id", postId)
        .order("created_at", { ascending: false })
        .limit(100);
      // uid'e göre tekilleştir (Android'deki distinctBy mantığı)
      const seen = new Set<string>();
      likers = (data ?? []).filter((r: any) => {
        if (seen.has(r.uid)) return false;
        seen.add(r.uid);
        return true;
      });
    } catch(e) { console.error(e); }
    finally { likersLoading = false; }
  }

  let sortedLikers = $derived((() => {
    if (likersSort === 'new') return [...likers].sort((a,b) => (b.created_at ?? '').localeCompare(a.created_at ?? ''));
    if (likersSort === 'old') return [...likers].sort((a,b) => (a.created_at ?? '').localeCompare(b.created_at ?? ''));
    // mixed — shuffle
    const arr = [...likers];
    for (let i = arr.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [arr[i], arr[j]] = [arr[j], arr[i]];
    }
    return arr;
  })());

  // ── Yorumlar ─────────────────────────────────────────────────
  async function openComments(p: any) {
    commentPostId = p.id;
    replyTo       = null;
    commentText   = "";
    await loadComments(p.id);
  }

  function closeComments() {
    commentPostId = null;
    comments      = [];
    replyTo       = null;
    commentText   = "";
  }

  async function loadComments(postId: string) {
    commentsLoading = true;
    try {
      const { data } = await supabase
        .from("feed_comments")
        .select("*")
        .eq("post_id", postId)
        .order("created_at", { ascending: true });
      const rows = data ?? [];

      // Kendi beğenilerim
      let myLikedCmtIds = new Set<string>();
      if ($currentUser && rows.length) {
        const { data: cl } = await supabase
          .from("comment_likes").select("comment_id")
          .eq("uid", $currentUser.uid)
          .in("comment_id", rows.map((r: any) => r.id));
        myLikedCmtIds = new Set((cl ?? []).map((r: any) => r.comment_id));
      }

      const cmtMap: Record<string, any> = {};
      for (const r of rows) cmtMap[r.id] = r;

      comments = rows.map((r: any) => ({
        ...r,
        isLikedByMe: myLikedCmtIds.has(r.id),
        replyToName: r.reply_to_cmt_id ? (cmtMap[r.reply_to_cmt_id]?.name ?? "") : null,
      }));
    } catch(e) { console.error(e); }
    finally { commentsLoading = false; }
  }

  async function sendComment() {
    if (!$currentUser || !commentText.trim() || !commentPostId) return;
    commentSending = true;
    try {
      await supabase.from("feed_comments").insert({
        post_id:        commentPostId,
        uid:            $currentUser.uid,
        name:           $currentUser.displayName ?? "",
        photo_url:      $currentUser.photoURL    ?? "",
        text:           commentText.trim(),
        reply_to_cmt_id: replyTo?.id ?? null,
      });
      posts = posts.map(p => p.id === commentPostId
        ? { ...p, commentsCount: (p.commentsCount ?? 0) + 1 } : p);
      commentText = "";
      replyTo     = null;
      await loadComments(commentPostId);
    } catch(e) { console.error(e); }
    finally { commentSending = false; }
  }

  async function toggleCommentLike(cmt: any) {
    if (!$currentUser) return;
    const wasLiked = cmt.isLikedByMe;
    comments = comments.map(c => c.id === cmt.id ? {
      ...c, isLikedByMe: !wasLiked,
      likes_count: Math.max(0, (c.likes_count ?? 0) + (wasLiked ? -1 : 1)),
    } : c);
    try {
      const id = `${cmt.id}_${$currentUser.uid}`;
      if (wasLiked) {
        await supabase.from("comment_likes").delete()
          .eq("comment_id", cmt.id).eq("uid", $currentUser.uid);
      } else {
        await supabase.from("comment_likes").upsert({
          id, comment_id: cmt.id, uid: $currentUser.uid,
          name:      $currentUser.displayName ?? "",
          photo_url: $currentUser.photoURL    ?? "",
        });
      }
      // Gerçek sayıyı güncelle
      const { data: cl } = await supabase
        .from("comment_likes").select("id").eq("comment_id", cmt.id);
      const realCount = cl?.length ?? 0;
      await supabase.from("feed_comments")
        .update({ likes_count: realCount }).eq("id", cmt.id);
      comments = comments.map(c => c.id === cmt.id
        ? { ...c, likes_count: realCount } : c);
    } catch(e) { console.error(e); }
  }

  // ── Sil ──────────────────────────────────────────────────────
  async function deletePost(p: any) {
    if (!$currentUser || $currentUser.uid !== p.uid) return;
    menuOpenId = null;
    if (!confirm("Gönderiyi silmek istediğinize emin misiniz?")) return;
    try {
      await deleteDoc(doc(db, "feed", p.id));
      posts = posts.filter(x => x.id !== p.id);
    } catch(e) { console.error(e); }
  }

  // ── Paylaş / Kopyala ─────────────────────────────────────────
  function sharePost(p: any) {
    menuOpenId = null;
    const url = window.location.origin + "/post/" + p.id;
    if (navigator.share) navigator.share({ title: p.displayName, text: p.text?.slice(0, 100), url });
    else { navigator.clipboard.writeText(url); alert("Bağlantı kopyalandı!"); }
  }

  function copyId(p: any) {
    menuOpenId = null;
    navigator.clipboard.writeText("#" + p.id);
    alert("Gönderi ID'si kopyalandı!");
  }

  function toggleMenu(e: Event, id: string) {
    e.stopPropagation();
    menuOpenId = menuOpenId === id ? null : id;
  }

  function repostLabel(type: string): string {
    const map: Record<string, string> = {
      serial: "📖 Kitap", chapter: "📄 Bölüm",
      book_chapter: "📄 Kitap Bölümü", blog: "📝 Blog",
      kf_lesson: "🇹🇷 Kurdî Ders", grammar: "📚 Dilbilgisi",
      kf_achievement: "🏆 Başarı",
    };
    return map[type] ?? type;
  }

  let filteredPosts = $derived(
    activeTab === 0 ? posts : posts.filter(p => p.uid === $currentUser?.uid)
  );
</script>

<svelte:head>
  <title>Heftreng — Akış</title>
</svelte:head>

<main class="page">
  <!-- Sekmeler -->
  <div class="tabs">
    <button class="tab" class:active={activeTab === 0} onclick={() => activeTab = 0}>Herkes</button>
    <button class="tab" class:active={activeTab === 1} onclick={() => activeTab = 1}>Takip Edilenler</button>
    <div class="tab-indicator" style="transform: translateX({activeTab * 100}%)"></div>
  </div>

  <div class="feed">
    {#if loading}
      {#each Array(5) as _}
        <div class="skeleton">
          <div class="sk-av"></div>
          <div class="sk-body">
            <div class="sk-line" style="width:60%"></div>
            <div class="sk-line" style="width:40%"></div>
            <div class="sk-line" style="width:90%;margin-top:12px"></div>
            <div class="sk-line" style="width:75%"></div>
          </div>
        </div>
      {/each}

    {:else if filteredPosts.length === 0}
      <div class="empty">
        <svg width="56" height="56" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
        </svg>
        <p>Henüz gönderi yok.</p>
        {#if $currentUser}
          <a href="/compose" class="compose-link">İlk gönderiyi sen yaz →</a>
        {/if}
      </div>

    {:else}
      {#each filteredPosts as p (p.id)}
        {@const isExpanded  = expandedIds.has(p.id)}
        {@const isLongText  = (p.text?.length ?? 0) > 280}

        <article class="card" onclick={() => window.location.href = '/post/' + p.id} role="button" tabindex="0">

          <!-- Başlık -->
          <div class="card-head">
            <a href="/profile/{p.uid}" class="avatar-link" onclick={(e) => e.stopPropagation()}>
              <div class="avatar-ring">
                {#if p.photoURL}
                  <img src={p.photoURL} alt={p.displayName} class="avatar-img" />
                {:else}
                  <div class="avatar-fallback">{(p.displayName ?? "?")[0].toUpperCase()}</div>
                {/if}
              </div>
            </a>
            <div class="meta">
              <a href="/profile/{p.uid}" class="display-name" onclick={(e) => e.stopPropagation()}>
                {p.displayName ?? "Anonim"}
              </a>
              <div class="meta-row">
                {#if p.username}<span class="username">@{p.username}</span><span class="dot">·</span>{/if}
                <span class="time">{ago(p.ts)}</span>
              </div>
            </div>

            <!-- 3-nokta menü -->
            <div class="menu-wrap" onclick={(e) => e.stopPropagation()}>
              <button class="menu-btn" onclick={(e) => toggleMenu(e, p.id)} aria-label="Seçenekler">
                <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                  <circle cx="12" cy="5" r="1.8"/><circle cx="12" cy="12" r="1.8"/><circle cx="12" cy="19" r="1.8"/>
                </svg>
              </button>
              {#if menuOpenId === p.id}
                <div class="dropdown">
                  {#if $currentUser?.uid === p.uid}
                    <a href="/compose?edit={p.id}" class="dropdown-item" onclick={() => menuOpenId = null}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                      Düzenle
                    </a>
                    <button class="dropdown-item danger" onclick={() => deletePost(p)}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
                      Sil
                    </button>
                  {:else}
                    <button class="dropdown-item danger" onclick={() => menuOpenId = null}>
                      <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/></svg>
                      Şikayet Et
                    </button>
                  {/if}
                  <div class="dropdown-divider"></div>
                  <button class="dropdown-item" onclick={() => sharePost(p)}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
                    Bağlantıyı Kopyala
                  </button>
                  <button class="dropdown-item" onclick={() => copyId(p)}>
                    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    Gönderi ID'sini Kopyala
                  </button>
                </div>
              {/if}
            </div>
          </div>

          <!-- İçerik -->
          <div class="card-body">
            {#if p.quoteText}
              <div class="quote-card" onclick={(e) => e.stopPropagation()}>
                <span class="quote-mark">❝</span>
                <div class="quote-inner">
                  <p class="quote-text">{p.quoteText}</p>
                  {#if p.bookName || p.authorName}
                    <div class="quote-source">
                      <div class="quote-cover">
                        {#if p.coverImg}
                          <img src={p.coverImg} alt={p.bookName} />
                        {:else}
                          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                        {/if}
                      </div>
                      <div>
                        {#if p.bookName}<span class="quote-book">{p.bookName}</span>{/if}
                        {#if p.authorName}<span class="quote-author">{p.authorName}</span>{/if}
                      </div>
                    </div>
                  {/if}
                </div>
              </div>
            {/if}

            {#if p.category}
              <div class="category-chip">{p.category}</div>
            {/if}
            {#if p.title}
              <h2 class="post-title">{p.title}</h2>
            {/if}
            {#if p.text}
              <p class="post-text" class:clamped={isLongText && !isExpanded}>{p.text}</p>
              {#if isLongText}
                <button class="read-more" onclick={(e) => { e.stopPropagation(); toggleExpand(p.id); }}>
                  {isExpanded ? "Daha az göster" : "Devamını oku"}
                </button>
              {/if}
            {/if}

            {#if p.repostType && p.repostType !== "kf_achievement"}
              <div class="repost-embed" onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + p.repostId; }}>
                <div class="repost-label">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
                  {repostLabel(p.repostType)}
                </div>
                {#if p.repostAuthor}
                  <div class="repost-author">
                    <div class="repost-av">
                      {#if p.repostAuthorPhoto}<img src={p.repostAuthorPhoto} alt={p.repostAuthor} />{:else}{p.repostAuthor[0].toUpperCase()}{/if}
                    </div>
                    <span>{p.repostAuthor}</span>
                  </div>
                {/if}
                {#if p.repostTitle || p.serialTitle}<p class="repost-title">{p.repostTitle || p.serialTitle}</p>{/if}
                {#if p.chapterTitle}<p class="repost-chapter">{p.chapterTitle}</p>{/if}
                {#if p.repostText}<p class="repost-text">{p.repostText}</p>{/if}
                {#if p.repostImg || p.serialCover}<img src={p.repostImg || p.serialCover} alt="" class="repost-img" />{/if}
              </div>
            {/if}

            {#if p.repostType === "kf_achievement"}
              <div class="achievement-card">
                <div class="achievement-inner">
                  <div class="achievement-top">
                    <span class="achievement-trophy">🏆</span>
                    <span class="achievement-level">Seviye {p.repostLevel}</span>
                  </div>
                  <div class="achievement-stats">
                    <div><span class="stat-num">{p.repostXp}</span><span class="stat-lbl">XP</span></div>
                    <div><span class="stat-num">{p.repostStreak}</span><span class="stat-lbl">Gün serisi</span></div>
                  </div>
                  <p class="achievement-caption">Kurdî öğrenme yolculuğunda harika ilerleme!</p>
                </div>
              </div>
            {/if}
          </div>

          {#if p.imgUrl || p.imageURL}
            <img src={p.imgUrl || p.imageURL} alt="" class="post-img" onclick={(e) => e.stopPropagation()} />
          {/if}

          <!-- Aksiyon çubuğu -->
          <div class="actions" onclick={(e) => e.stopPropagation()}>
            <button class="act-btn" class:liked={p.isLikedByMe} onclick={() => toggleLike(p)} oncontextmenu={(e) => openLikers(p, e)} aria-label="Beğen">
              {#if p.isLikedByMe}
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              {:else}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              {/if}
              {#if (p.likesCount ?? 0) > 0}
                <span class="likes-count" onclick={(e) => openLikers(p, e)} role="button" tabindex="0">{p.likesCount}</span>
              {/if}
            </button>

            <button class="act-btn" onclick={() => openComments(p)} aria-label="Yorum yap">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              {#if (p.commentsCount ?? 0) > 0}<span>{p.commentsCount}</span>{/if}
            </button>

            <button class="act-btn" onclick={() => sharePost(p)} aria-label="Paylaş">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
            </button>

            <div class="act-spacer"></div>

            <button class="act-btn save-btn" class:saved={p.isSavedByMe} onclick={() => toggleSave(p)} aria-label="Kaydet">
              {#if p.isSavedByMe}
                <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
              {:else}
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
              {/if}
            </button>
          </div>
        </article>
      {/each}

      {#if hasMore}
        <div class="load-more-wrap">
          <button class="load-more-btn" onclick={loadMore} disabled={loadingMore}>
            {#if loadingMore}<div class="spinner"></div>Yükleniyor...{:else}Daha fazla göster{/if}
          </button>
        </div>
      {/if}
    {/if}
  </div>

  {#if $currentUser}
    <a href="/compose" class="fab" aria-label="Gönderi yaz">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="24" height="24">
        <path d="M12 20h9"/><path d="M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4Z"/>
      </svg>
    </a>
  {/if}
</main>


<!-- ── Beğenen listesi bottom sheet ──────────────────────────────── -->
{#if likersPostId}
  <div class="sheet-backdrop" onclick={closeLikers}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <div>
        <span class="sheet-title">Beğenenler</span>
        {#if likers.length > 0}<span class="sheet-count">{likers.length} kişi</span>{/if}
      </div>
      <button class="sheet-close" onclick={closeLikers}>✕</button>
    </div>

    {#if likers.length > 1}
      <div class="sort-chips">
        {#each [['new','Yeni'],['old','Eski'],['mixed','Karışık']] as [val, label]}
          <button
            class="sort-chip"
            class:active={likersSort === val}
            onclick={() => likersSort = val as any}
          >{label}</button>
        {/each}
      </div>
    {/if}

    <div class="comments-list">
      {#if likersLoading}
        <div class="cmt-loading"><div class="spinner"></div></div>
      {:else if likers.length === 0}
        <p class="cmt-empty">Henüz beğeni yok.</p>
      {:else}
        {#each sortedLikers as lk (lk.uid)}
          <a href="/profile/{lk.uid}" class="liker-row" onclick={closeLikers}>
            <div class="cmt-av">
              {#if lk.photo_url}
                <img src={lk.photo_url} alt={lk.name} />
              {:else}
                <span>{(lk.name ?? "?")[0].toUpperCase()}</span>
              {/if}
            </div>
            <span class="liker-name">{lk.name ?? "Anonim"}</span>
            <svg class="liker-arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Yorum bottom sheet ──────────────────────────────────────── -->
{#if commentPostId}
  <div class="sheet-backdrop" onclick={closeComments}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Yorumlar</span>
      <button class="sheet-close" onclick={closeComments}>✕</button>
    </div>

    <div class="comments-list">
      {#if commentsLoading}
        <div class="cmt-loading">
          <div class="spinner"></div>
        </div>
      {:else if comments.length === 0}
        <p class="cmt-empty">Henüz yorum yok. İlk yorumu sen yap!</p>
      {:else}
        {#each comments as c (c.id)}
          <div class="cmt-row">
            <div class="cmt-av">
              {#if c.photo_url}
                <img src={c.photo_url} alt={c.name} />
              {:else}
                <span>{(c.name ?? "?")[0].toUpperCase()}</span>
              {/if}
            </div>
            <div class="cmt-body">
              <div class="cmt-head">
                <span class="cmt-name">{c.name ?? "Anonim"}</span>
                <span class="cmt-time">{ago(c.created_at)}</span>
              </div>
              {#if c.replyToName}
                <span class="cmt-reply-tag">@{c.replyToName}</span>
              {/if}
              <p class="cmt-text">{c.text}</p>
              <div class="cmt-actions">
                <button class="cmt-like" class:liked={c.isLikedByMe} onclick={() => toggleCommentLike(c)}>
                  <svg viewBox="0 0 24 24" fill={c.isLikedByMe ? "currentColor" : "none"} stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  {#if (c.likes_count ?? 0) > 0}<span>{c.likes_count}</span>{/if}
                </button>
                {#if $currentUser}
                  <button class="cmt-reply-btn" onclick={() => replyTo = c}>Yanıtla</button>
                {/if}
              </div>
            </div>
          </div>
        {/each}
      {/if}
    </div>

    {#if $currentUser}
      <div class="cmt-input-wrap">
        {#if replyTo}
          <div class="reply-banner">
            <span>@{replyTo.name} yanıtlanıyor</span>
            <button onclick={() => replyTo = null}>✕</button>
          </div>
        {/if}
        <div class="cmt-input-row">
          <input
            class="cmt-input"
            placeholder="Yorum yaz..."
            bind:value={commentText}
            onkeydown={(e) => e.key === 'Enter' && !e.shiftKey && sendComment()}
          />
          <button class="cmt-send" onclick={sendComment} disabled={!commentText.trim() || commentSending}>
            {#if commentSending}
              <div class="spinner small"></div>
            {:else}
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            {/if}
          </button>
        </div>
      </div>
    {/if}
  </div>
{/if}

<style>
.page { max-width: 600px; margin: 0 auto; padding-bottom: 72px; position: relative; }

/* Sekmeler */
.tabs { display: flex; position: relative; background: var(--surface); border-bottom: 1px solid var(--divider); overflow: hidden; }
.tab { flex: 1; padding: 14px; font-size: 14px; font-weight: 500; color: var(--muted); background: none; border: none; cursor: pointer; position: relative; z-index: 1; transition: color 0.2s; }
.tab.active { color: var(--primary); font-weight: 600; }
.tab-indicator { position: absolute; bottom: 0; left: 0; width: 50%; height: 2px; background: var(--primary); border-radius: 2px 2px 0 0; transition: transform 0.25s cubic-bezier(.4,0,.2,1); }

/* Feed */
.feed { display: flex; flex-direction: column; gap: 8px; padding: 8px 12px; }

/* Kart */
.card { background: var(--card); border-radius: 18px; border: 0.7px solid var(--divider); padding: 14px 15px 10px; cursor: pointer; transition: border-color 0.15s; display: block; text-align: left; width: 100%; }
.card:hover { border-color: color-mix(in srgb, var(--primary) 30%, var(--divider)); }

/* Başlık */
.card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.avatar-link { flex-shrink: 0; text-decoration: none; }
.avatar-ring { width: 44px; height: 44px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), color-mix(in srgb, var(--primary) 60%, purple)); padding: 1.5px; display: flex; align-items: center; justify-content: center; }
.avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.avatar-fallback { width: 100%; height: 100%; border-radius: 50%; background: var(--surface-var); color: var(--on-bg); font-size: 16px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.meta { flex: 1; min-width: 0; }
.display-name { font-size: 14px; font-weight: 700; color: var(--on-bg); text-decoration: none; display: block; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.display-name:hover { text-decoration: underline; }
.meta-row { display: flex; align-items: center; gap: 4px; margin-top: 1px; }
.username, .time { font-size: 12px; color: var(--muted); }
.dot { font-size: 12px; color: var(--muted); }

/* Menü */
.menu-wrap { position: relative; }
.menu-btn { width: 34px; height: 34px; border-radius: 50%; display: flex; align-items: center; justify-content: center; color: var(--muted); background: none; border: none; cursor: pointer; transition: background 0.15s; }
.menu-btn:hover { background: var(--surface-var); }
.dropdown { position: absolute; right: 0; top: calc(100% + 4px); background: var(--surface); border: 1px solid var(--divider); border-radius: 14px; min-width: 210px; box-shadow: 0 8px 24px rgba(0,0,0,0.14); overflow: hidden; z-index: 200; }
.dropdown-item { display: flex; align-items: center; gap: 10px; padding: 11px 14px; font-size: 13px; color: var(--on-surface); background: none; border: none; cursor: pointer; width: 100%; text-align: left; text-decoration: none; font-family: inherit; transition: background 0.1s; }
.dropdown-item:hover { background: var(--surface-var); }
.dropdown-item.danger { color: var(--error, #ef4444); }
.dropdown-divider { height: 1px; background: var(--divider); }

/* Kategori */
.category-chip { display: inline-block; background: color-mix(in srgb, var(--primary) 12%, transparent); color: var(--primary); font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 99px; margin-bottom: 6px; }

/* İçerik */
.card-body { margin-bottom: 2px; }
.post-title { font-size: 16px; font-weight: 700; color: var(--on-bg); line-height: 1.35; margin-bottom: 5px; }
.post-text { font-size: 15px; color: var(--on-bg); line-height: 1.65; white-space: pre-wrap; margin-bottom: 4px; }
.post-text.clamped { display: -webkit-box; -webkit-line-clamp: 6; -webkit-box-orient: vertical; overflow: hidden; }
.read-more { background: none; border: none; color: var(--primary); font-size: 13px; font-weight: 600; cursor: pointer; padding: 0; margin-bottom: 8px; font-family: inherit; }
.post-img { width: 100%; border-radius: 12px; max-height: 320px; object-fit: cover; margin-bottom: 10px; display: block; }

/* QuoteCard */
.quote-card { position: relative; background: linear-gradient(135deg, color-mix(in srgb,#F59E0B 8%,transparent), color-mix(in srgb,#9B72F5 6%,transparent)); border: 1px solid color-mix(in srgb,#F59E0B 35%,transparent); border-radius: 14px; padding: 14px 14px 12px; margin-bottom: 10px; overflow: hidden; }
.quote-mark { position: absolute; top: -6px; left: 6px; font-size: 52px; color: color-mix(in srgb,#F59E0B 15%,transparent); font-weight: 900; line-height: 1; pointer-events: none; font-family: Georgia,serif; }
.quote-inner { padding-left: 8px; position: relative; }
.quote-text { font-size: 14px; font-style: italic; color: var(--on-surface); line-height: 1.6; font-weight: 500; margin-bottom: 10px; }
.quote-source { display: flex; align-items: center; gap: 8px; }
.quote-cover { width: 28px; height: 42px; border-radius: 3px; background: color-mix(in srgb,#F59E0B 10%,transparent); display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; }
.quote-cover img { width: 100%; height: 100%; object-fit: cover; }
.quote-cover svg { color: #F59E0B; }
.quote-book { display: block; font-size: 11px; font-weight: 600; color: #F59E0B; }
.quote-author { display: block; font-size: 10px; color: var(--muted); margin-top: 1px; }

/* Repost */
.repost-embed { background: var(--surface-var); border: 0.5px solid var(--divider); border-radius: 13px; padding: 12px; margin-bottom: 8px; cursor: pointer; transition: border-color 0.15s; display: flex; flex-direction: column; gap: 5px; }
.repost-embed:hover { border-color: var(--primary); }
.repost-label { display: flex; align-items: center; gap: 4px; font-size: 9px; font-weight: 700; color: var(--primary); letter-spacing: 0.8px; text-transform: uppercase; }
.repost-author { display: flex; align-items: center; gap: 5px; font-size: 12px; font-weight: 600; color: var(--on-surface); }
.repost-av { width: 16px; height: 16px; border-radius: 50%; background: var(--surface); overflow: hidden; display: flex; align-items: center; justify-content: center; font-size: 8px; font-weight: 700; color: var(--on-bg); }
.repost-av img { width: 100%; height: 100%; object-fit: cover; }
.repost-title { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.repost-chapter { font-size: 12px; color: var(--muted); }
.repost-text { font-size: 13px; color: var(--on-surface); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.repost-img { width: 100%; height: 120px; object-fit: cover; border-radius: 8px; }

/* Başarı kartı */
.achievement-card { border-radius: 16px; overflow: hidden; margin-bottom: 8px; }
.achievement-inner { background: linear-gradient(135deg,#F5A623,#E8871E,#D9691B); padding: 18px; display: flex; flex-direction: column; gap: 10px; }
.achievement-top { display: flex; align-items: center; gap: 8px; }
.achievement-trophy { font-size: 28px; }
.achievement-level { font-size: 18px; font-weight: 900; color: white; }
.achievement-stats { display: flex; gap: 20px; }
.stat-num { display: block; font-size: 20px; font-weight: 700; color: white; }
.stat-lbl { font-size: 11px; color: rgba(255,255,255,0.85); }
.achievement-caption { font-size: 11.5px; font-weight: 500; color: rgba(255,255,255,0.9); }

/* Aksiyonlar */
.actions { display: flex; align-items: center; margin-top: 4px; }
.act-btn { display: flex; align-items: center; gap: 5px; padding: 7px 10px; border-radius: 20px; color: var(--muted); font-size: 13px; font-weight: 500; cursor: pointer; border: none; background: transparent; transition: color 0.15s, background 0.15s, transform 0.1s; font-family: inherit; }
.act-btn:hover { background: var(--surface-var); }
.act-btn:active { transform: scale(0.92); }
.act-btn.liked { color: #FF3A5C; }
.act-btn.liked:hover { background: rgba(255,58,92,0.1); }
.act-btn.save-btn.saved { color: #F59E0B; }
.act-spacer { flex: 1; }

/* FAB */
.fab { position: fixed; bottom: 76px; right: 20px; width: 56px; height: 56px; border-radius: 50%; background: var(--primary); color: #fff; display: flex; align-items: center; justify-content: center; box-shadow: 0 4px 16px rgba(0,0,0,0.22); transition: transform 0.15s, box-shadow 0.15s; z-index: 50; }
.fab:hover { transform: scale(1.06); }
.fab:active { transform: scale(0.94); }

/* Boş / skeleton */
.empty { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; color: var(--muted); gap: 12px; }
.empty p { font-size: 15px; }
.compose-link { color: var(--primary); font-weight: 600; font-size: 14px; }
.skeleton { display: flex; gap: 12px; padding: 14px 15px; background: var(--card); border-radius: 18px; border: 0.7px solid var(--divider); }
.sk-av { width: 44px; height: 44px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }
.sk-body { flex: 1; display: flex; flex-direction: column; gap: 8px; padding-top: 4px; }
.sk-line { height: 13px; background: var(--shimmer); border-radius: 6px; animation: shimmer 1.4s ease-in-out infinite; }

/* Daha fazla yükle */
.load-more-wrap { padding: 8px 12px 4px; display: flex; justify-content: center; }
.load-more-btn { padding: 12px 28px; border: 1.5px solid var(--divider); border-radius: 99px; background: var(--surface); color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; display: flex; align-items: center; gap: 8px; transition: border-color 0.15s, background 0.15s; }
.load-more-btn:hover:not(:disabled) { border-color: var(--primary); background: color-mix(in srgb,var(--primary) 6%,var(--surface)); }
.load-more-btn:disabled { opacity: 0.6; cursor: not-allowed; }

/* Spinner */
.spinner { width: 14px; height: 14px; border: 2px solid var(--divider); border-top-color: var(--primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
.spinner.small { width: 16px; height: 16px; }

/* ── Yorum bottom sheet ────────────────────────────────────── */
.sheet-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 300; }
.sheet {
  position: fixed; bottom: 0; left: 0; right: 0;
  max-width: 600px; margin: 0 auto;
  background: var(--surface);
  border-radius: 20px 20px 0 0;
  z-index: 301;
  display: flex; flex-direction: column;
  max-height: 75vh;
}
.sheet-handle { width: 40px; height: 4px; background: var(--divider); border-radius: 2px; margin: 12px auto 4px; }
.sheet-header { display: flex; align-items: center; justify-content: space-between; padding: 0 16px 12px; border-bottom: 1px solid var(--divider); }
.sheet-title { font-size: 15px; font-weight: 700; color: var(--on-bg); }
.sheet-close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; padding: 4px; }
.comments-list { flex: 1; overflow-y: auto; padding: 8px 16px; display: flex; flex-direction: column; gap: 14px; }
.cmt-loading { display: flex; justify-content: center; padding: 24px; }
.cmt-empty { text-align: center; color: var(--muted); font-size: 14px; padding: 24px 0; }
.cmt-row { display: flex; gap: 10px; }
.cmt-av { width: 34px; height: 34px; border-radius: 50%; background: var(--surface-var); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; color: var(--on-bg); }
.cmt-av img { width: 100%; height: 100%; object-fit: cover; }
.cmt-body { flex: 1; }
.cmt-head { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.cmt-name { font-size: 13px; font-weight: 700; color: var(--on-bg); }
.cmt-time { font-size: 11px; color: var(--muted); }
.cmt-reply-tag { display: inline-block; font-size: 12px; color: var(--primary); font-weight: 600; margin-bottom: 2px; }
.cmt-text { font-size: 14px; color: var(--on-bg); line-height: 1.5; white-space: pre-wrap; }
.cmt-actions { display: flex; align-items: center; gap: 12px; margin-top: 4px; }
.cmt-like { display: flex; align-items: center; gap: 4px; background: none; border: none; cursor: pointer; color: var(--muted); font-size: 12px; padding: 0; font-family: inherit; }
.cmt-like.liked { color: #FF3A5C; }
.cmt-reply-btn { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 12px; padding: 0; font-family: inherit; font-weight: 500; }
.cmt-reply-btn:hover { color: var(--primary); }
.cmt-input-wrap { border-top: 1px solid var(--divider); padding: 10px 16px; padding-bottom: max(10px, env(safe-area-inset-bottom)); }
.reply-banner { display: flex; align-items: center; justify-content: space-between; font-size: 12px; color: var(--primary); padding: 4px 0 6px; }
.reply-banner button { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 14px; }
.cmt-input-row { display: flex; align-items: center; gap: 8px; }
.cmt-input { flex: 1; border: 1px solid var(--divider); border-radius: 20px; padding: 9px 14px; font-size: 14px; background: var(--surface-var); color: var(--on-bg); outline: none; font-family: inherit; }
.cmt-input:focus { border-color: var(--primary); }
.cmt-send { width: 38px; height: 38px; border-radius: 50%; background: var(--primary); color: #fff; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: opacity 0.15s; }
.cmt-send:disabled { opacity: 0.4; cursor: not-allowed; }

@keyframes spin { to { transform: rotate(360deg); } }
/* Beğeni sayısı tıklanabilir */
.likes-count { cursor: pointer; }
.likes-count:hover { text-decoration: underline; }

/* Sıralama chips */
.sort-chips { display: flex; gap: 8px; padding: 0 16px 10px; }
.sort-chip { padding: 5px 14px; border-radius: 99px; font-size: 12px; font-weight: 500; border: none; cursor: pointer; background: var(--surface-var); color: var(--muted); font-family: inherit; transition: background 0.15s, color 0.15s; }
.sort-chip.active { background: var(--primary); color: #fff; font-weight: 600; }

/* Liker satırı */
.liker-row { display: flex; align-items: center; gap: 10px; padding: 8px 16px; text-decoration: none; transition: background 0.1s; border-radius: 12px; }
.liker-row:hover { background: var(--surface-var); }
.liker-name { flex: 1; font-size: 14px; font-weight: 600; color: var(--on-bg); }
.liker-arrow { color: var(--muted); }
.sheet-count { font-size: 12px; color: var(--muted); margin-left: 6px; }

@keyframes shimmer { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
