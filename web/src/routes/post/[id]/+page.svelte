<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/stores";
  import { doc, getDoc, deleteDoc } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";

  const postId = $derived($page.params.id);

  // ── State ─────────────────────────────────────────────────────
  let post           = $state<any>(null);
  let loading        = $state(true);
  let loadFailed     = $state(false);

  let comments       = $state<any[]>([]);
  let cmtLoading     = $state(true);

  let likesCount     = $state(0);
  let isLikedByMe    = $state(false);
  let isSavedByMe    = $state(false);

  let commentText    = $state("");
  let submitting     = $state(false);
  let replyTo        = $state<any | null>(null);
  let editTarget     = $state<any | null>(null);
  let deleteTarget   = $state<any | null>(null);
  let expandedThreads = $state<Set<string>>(new Set());

  // Beğenen listesi
  let showLikers     = $state(false);
  let likers         = $state<any[]>([]);
  let likersLoading  = $state(false);
  let likersSort     = $state<"new"|"old"|"mixed">("new");

  // Yorum beğenen
  let cmtLikersId    = $state<string | null>(null);
  let cmtLikers      = $state<any[]>([]);
  let cmtLikersLoading = $state(false);

  // ── Zaman formatı ─────────────────────────────────────────────
  function ago(ts: any): string {
    const ms = ts?.seconds ? ts.seconds * 1000 : ts ? new Date(ts).getTime() : 0;
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

  // ── Yorum thread yapısı (Instagram tarzı) ─────────────────────
  let commentThreads = $derived((() => {
    const allIds = new Set(comments.map((c: any) => c.id));
    const topLevel = comments.filter((c: any) => !c.reply_to_cmt_id || !allIds.has(c.reply_to_cmt_id));
    const repliesByParent: Record<string, any[]> = {};
    for (const c of comments) {
      if (c.reply_to_cmt_id && allIds.has(c.reply_to_cmt_id)) {
        (repliesByParent[c.reply_to_cmt_id] ??= []).push(c);
      }
    }
    return topLevel.map((p: any) => ({ parent: p, replies: repliesByParent[p.id] ?? [] }));
  })());

  // ── Yükle ─────────────────────────────────────────────────────
  onMount(async () => {
    await loadPost();
    await loadComments();
  });

  async function loadPost() {
    loading = true;
    try {
      const snap = await getDoc(doc(db, "feed", postId));
      if (!snap.exists()) { loadFailed = true; return; }
      post = { id: snap.id, ...snap.data() };

      // Beğeni sayısı + durum
      const [countRes, interRes] = await Promise.all([
        supabase.from("feed_likes").select("id", { count: "exact", head: true }).eq("post_id", postId),
        $currentUser
          ? supabase.from("feed_likes").select("id").eq("post_id", postId).eq("uid", $currentUser.uid).maybeSingle()
          : Promise.resolve({ data: null }),
      ]);
      likesCount  = countRes.count ?? post.likesCount ?? 0;
      isLikedByMe = !!interRes.data;

      if ($currentUser) {
        const { data: sv } = await supabase.from("feed_saves")
          .select("id").eq("post_id", postId).eq("uid", $currentUser.uid).maybeSingle();
        isSavedByMe = !!sv;
      }
    } catch(e) { console.error(e); loadFailed = true; }
    finally { loading = false; }
  }

  async function loadComments() {
    cmtLoading = true;
    try {
      const { data } = await supabase
        .from("feed_comments")
        .select("*")
        .eq("post_id", postId)
        .order("created_at", { ascending: true });
      const rows = data ?? [];

      // Kendi yorum beğenilerim
      let myLikedIds = new Set<string>();
      if ($currentUser && rows.length) {
        const { data: cl } = await supabase
          .from("comment_likes").select("comment_id")
          .eq("uid", $currentUser.uid)
          .in("comment_id", rows.map((r: any) => r.id));
        myLikedIds = new Set((cl ?? []).map((r: any) => r.comment_id));
      }
      comments = rows.map((r: any) => ({ ...r, isLikedByMe: myLikedIds.has(r.id) }));
    } catch(e) { console.error(e); }
    finally { cmtLoading = false; }
  }

  // ── Beğeni ────────────────────────────────────────────────────
  async function toggleLike() {
    if (!$currentUser) { window.location.href = "/login"; return; }
    const was = isLikedByMe;
    isLikedByMe = !was;
    likesCount  = Math.max(0, likesCount + (was ? -1 : 1));
    try {
      const id = `${postId}_${$currentUser.uid}`;
      if (was) {
        await supabase.from("feed_likes").delete().eq("post_id", postId).eq("uid", $currentUser.uid);
      } else {
        await supabase.from("feed_likes").upsert({
          id, post_id: postId, uid: $currentUser.uid,
          name: $currentUser.displayName ?? "", photo_url: $currentUser.photoURL ?? "",
        });
      }
    } catch(e) {
      isLikedByMe = was;
      likesCount  = Math.max(0, likesCount + (was ? 1 : -1));
    }
  }

  // ── Kaydet ────────────────────────────────────────────────────
  async function toggleSave() {
    if (!$currentUser) { window.location.href = "/login"; return; }
    const was = isSavedByMe;
    isSavedByMe = !was;
    try {
      const id = `${postId}_${$currentUser.uid}`;
      if (was) await supabase.from("feed_saves").delete().eq("id", id);
      else await supabase.from("feed_saves").upsert({ id, post_id: postId, uid: $currentUser.uid });
    } catch(e) { isSavedByMe = was; }
  }

  // ── Gönderiyi sil ─────────────────────────────────────────────
  async function deletePost() {
    if (!$currentUser || $currentUser.uid !== post?.uid) return;
    if (!confirm("Gönderiyi silmek istediğinize emin misiniz?")) return;
    await deleteDoc(doc(db, "feed", postId));
    window.location.href = "/feed";
  }

  // ── Yorum gönder / düzenle ────────────────────────────────────
  async function submitComment() {
    if (!$currentUser || !commentText.trim()) return;
    submitting = true;
    try {
      if (editTarget) {
        const { data } = await supabase.from("feed_comments")
          .update({ text: commentText.trim() })
          .eq("id", editTarget.id).select().single();
        if (data) comments = comments.map((c: any) => c.id === editTarget.id ? { ...c, text: data.text } : c);
        editTarget = null;
      } else {
        const { data } = await supabase.from("feed_comments").insert({
          post_id: postId,
          uid: $currentUser.uid,
          name: $currentUser.displayName ?? "",
          photo_url: $currentUser.photoURL ?? "",
          text: commentText.trim(),
          reply_to_cmt_id: replyTo?.id ?? null,
        }).select().single();
        if (data) comments = [...comments, { ...data, isLikedByMe: false }];
        replyTo = null;
      }
      commentText = "";
    } catch(e) { console.error(e); }
    finally { submitting = false; }
  }

  // ── Yorum sil ─────────────────────────────────────────────────
  async function deleteComment() {
    if (!deleteTarget) return;
    await supabase.from("feed_comments").delete().eq("id", deleteTarget.id);
    comments = comments.filter((c: any) => c.id !== deleteTarget.id);
    deleteTarget = null;
  }

  // ── Yorum beğen ───────────────────────────────────────────────
  async function toggleCommentLike(cmt: any) {
    if (!$currentUser) return;
    const was = cmt.isLikedByMe;
    comments = comments.map((c: any) => c.id === cmt.id ? {
      ...c, isLikedByMe: !was,
      likes_count: Math.max(0, (c.likes_count ?? 0) + (was ? -1 : 1)),
    } : c);
    try {
      const id = `${cmt.id}_${$currentUser.uid}`;
      if (was) await supabase.from("comment_likes").delete().eq("comment_id", cmt.id).eq("uid", $currentUser.uid);
      else await supabase.from("comment_likes").upsert({ id, comment_id: cmt.id, uid: $currentUser.uid, name: $currentUser.displayName ?? "", photo_url: $currentUser.photoURL ?? "" });
    } catch(e) { console.error(e); }
  }

  // ── Beğenenler listesi ────────────────────────────────────────
  async function openLikers() {
    if (likesCount === 0) return;
    showLikers = true;
    likersLoading = true;
    try {
      const { data } = await supabase.from("feed_likes").select("uid,name,photo_url,created_at")
        .eq("post_id", postId).order("created_at", { ascending: false }).limit(100);
      const seen = new Set<string>();
      likers = (data ?? []).filter((r: any) => { if (seen.has(r.uid)) return false; seen.add(r.uid); return true; });
    } catch(e) { console.error(e); }
    finally { likersLoading = false; }
  }

  async function openCmtLikers(cmt: any) {
    if (!(cmt.likes_count > 0)) return;
    cmtLikersId = cmt.id;
    cmtLikersLoading = true;
    try {
      const { data } = await supabase.from("comment_likes").select("uid,name,photo_url,created_at")
        .eq("comment_id", cmt.id).order("created_at", { ascending: false }).limit(50);
      cmtLikers = data ?? [];
    } catch(e) { console.error(e); }
    finally { cmtLikersLoading = false; }
  }

  let sortedLikers = $derived((() => {
    const arr = [...likers];
    if (likersSort === "new") return arr.sort((a,b) => (b.created_at ?? "").localeCompare(a.created_at ?? ""));
    if (likersSort === "old") return arr.sort((a,b) => (a.created_at ?? "").localeCompare(b.created_at ?? ""));
    for (let i = arr.length - 1; i > 0; i--) { const j = Math.floor(Math.random() * (i+1)); [arr[i], arr[j]] = [arr[j], arr[i]]; }
    return arr;
  })());

  function toggleThread(id: string) {
    const next = new Set(expandedThreads);
    next.has(id) ? next.delete(id) : next.add(id);
    expandedThreads = next;
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
</script>

<svelte:head>
  <title>{post?.displayName ?? "Gönderi"} — Heftreng</title>
</svelte:head>

<!-- Geri + Başlık -->
<div class="top-bar">
  <button class="back-btn" onclick={() => history.back()} aria-label="Geri">
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" width="22" height="22">
      <polyline points="15 18 9 12 15 6"/>
    </svg>
  </button>
  <span class="top-title">Gönderi</span>
</div>

<main class="wrap">

  {#if loading}
    <!-- Skeleton -->
    <div class="card skeleton-card">
      <div class="sk-head">
        <div class="sk-av"></div>
        <div class="sk-meta">
          <div class="sk-line" style="width:50%"></div>
          <div class="sk-line" style="width:30%"></div>
        </div>
      </div>
      <div class="sk-line" style="width:90%;margin-top:12px"></div>
      <div class="sk-line" style="width:70%"></div>
      <div class="sk-line" style="width:80%"></div>
    </div>

  {:else if loadFailed || !post}
    <div class="not-found">
      <span class="nf-emoji">😕</span>
      <p>Gönderi bulunamadı.</p>
      <button onclick={() => history.back()} class="nf-back">Geri dön</button>
    </div>

  {:else}

    <!-- ── Gönderi kartı ─────────────────────────────────────── -->
    <article class="card post-card">
      <div class="card-head">
        <a href="/profile/{post.uid}" class="avatar-link">
          <div class="avatar-ring">
            {#if post.photoURL}
              <img src={post.photoURL} alt={post.displayName} class="avatar-img" />
            {:else}
              <div class="avatar-fallback">{(post.displayName ?? "?")[0].toUpperCase()}</div>
            {/if}
          </div>
        </a>
        <div class="meta">
          <a href="/profile/{post.uid}" class="display-name">{post.displayName ?? "Anonim"}</a>
          <div class="meta-row">
            {#if post.username}<span class="username">@{post.username}</span><span class="dot">·</span>{/if}
            <span class="time">{ago(post.ts)}</span>
          </div>
        </div>
        {#if $currentUser?.uid === post.uid}
          <div class="post-actions-top">
            <a href="/compose?edit={post.id}" class="act-icon" aria-label="Düzenle">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            </a>
            <button class="act-icon danger" onclick={deletePost} aria-label="Sil">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/></svg>
            </button>
          </div>
        {/if}
      </div>

      <!-- QuoteCard -->
      {#if post.quoteText}
        <div class="quote-card">
          <span class="quote-mark">❝</span>
          <div class="quote-inner">
            <p class="quote-text">{post.quoteText}</p>
            {#if post.bookName || post.authorName}
              <div class="quote-source">
                <div class="quote-cover">
                  {#if post.coverImg}<img src={post.coverImg} alt={post.bookName} />{:else}
                    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="14" height="14"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
                  {/if}
                </div>
                <div>
                  {#if post.bookName}<span class="quote-book">{post.bookName}</span>{/if}
                  {#if post.authorName}<span class="quote-author">{post.authorName}</span>{/if}
                </div>
              </div>
            {/if}
          </div>
        </div>
      {/if}

      {#if post.category}<div class="category-chip">{post.category}</div>{/if}
      {#if post.title}<h1 class="post-title">{post.title}</h1>{/if}
      {#if post.text}<p class="post-text">{post.text}</p>{/if}

      <!-- Repost embed -->
      {#if post.repostType && post.repostType !== "kf_achievement"}
        <div class="repost-embed" onclick={() => window.location.href = '/post/' + post.repostId} role="button" tabindex="0">
          <div class="repost-label">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="11" height="11"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
            {repostLabel(post.repostType)}
          </div>
          {#if post.repostTitle || post.serialTitle}<p class="repost-title">{post.repostTitle || post.serialTitle}</p>{/if}
          {#if post.repostText}<p class="repost-text">{post.repostText}</p>{/if}
          {#if post.repostImg || post.serialCover}<img src={post.repostImg || post.serialCover} alt="" class="repost-img" />{/if}
        </div>
      {/if}

      {#if post.imgUrl || post.imageURL}
        <img src={post.imgUrl || post.imageURL} alt="" class="post-img" />
      {/if}

      <!-- Aksiyon çubuğu -->
      <div class="actions">
        <button class="act-btn" class:liked={isLikedByMe} onclick={toggleLike} aria-label="Beğen">
          {#if isLikedByMe}
            <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
          {:else}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
          {/if}
        </button>
        <button class="act-btn" onclick={() => window.location.href = '#comment-input'} aria-label="Yorum yap">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
        </button>
        <button class="act-btn save-btn" class:saved={isSavedByMe} onclick={toggleSave} aria-label="Kaydet">
          {#if isSavedByMe}
            <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
          {:else}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="22" height="22"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
          {/if}
        </button>
      </div>

      <!-- Beğeni sayısı satırı (Android gibi ayrı row) -->
      {#if likesCount > 0}
        <div class="divider"></div>
        <button class="likes-row" onclick={openLikers}>
          <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16" style="color:#FF3A5C"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
          <span><strong>{likesCount}</strong> beğeni</span>
        </button>
      {/if}
    </article>

    <!-- ── Yorumlar başlığı ──────────────────────────────────── -->
    <div class="section-header">
      <span class="section-title">Yorumlar</span>
      {#if comments.length > 0}<span class="section-count">{comments.length}</span>{/if}
    </div>
    <div class="divider-full"></div>

    <!-- ── Yorum listesi ────────────────────────────────────── -->
    {#if cmtLoading}
      {#each Array(3) as _}
        <div class="sk-cmt">
          <div class="sk-av-sm"></div>
          <div class="sk-cmt-body">
            <div class="sk-line" style="width:40%"></div>
            <div class="sk-line" style="width:80%;margin-top:6px"></div>
          </div>
        </div>
      {/each}
    {:else if commentThreads.length === 0}
      <div class="empty-cmt">
        <span>💬</span>
        <p>Henüz yorum yok.</p>
      </div>
    {:else}
      {#each commentThreads as thread (thread.parent.id)}
        {@const cmt = thread.parent}
        {@const isOwner = $currentUser?.uid === cmt.uid}
        {@const isPostOwner = $currentUser?.uid === post.uid}
        {@const canDelete = isOwner || isPostOwner}
        {@const isExpanded = expandedThreads.has(cmt.id)}

        <!-- Üst yorum -->
        <div class="cmt-row">
          <a href="/profile/{cmt.uid}" class="cmt-av">
            {#if cmt.photo_url}<img src={cmt.photo_url} alt={cmt.name} />{:else}
              <span>{(cmt.name ?? "?")[0].toUpperCase()}</span>
            {/if}
          </a>
          <div class="cmt-body">
            <div class="cmt-head">
              <a href="/profile/{cmt.uid}" class="cmt-name">{cmt.name ?? "Anonim"}</a>
              <span class="cmt-time">{ago(cmt.created_at)}</span>
            </div>
            <p class="cmt-text">{cmt.text}</p>
            <div class="cmt-acts">
              <button class="cmt-like" class:liked={cmt.isLikedByMe} onclick={() => toggleCommentLike(cmt)}>
                <svg viewBox="0 0 24 24" fill={cmt.isLikedByMe ? "currentColor" : "none"} stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              </button>
              {#if (cmt.likes_count ?? 0) > 0}
                <button class="cmt-likes-count" onclick={() => openCmtLikers(cmt)}>{cmt.likes_count}</button>
              {/if}
              {#if $currentUser}
                <button class="cmt-act-btn" onclick={() => { replyTo = cmt; editTarget = null; document.getElementById('comment-input')?.focus(); }}>Yanıtla</button>
              {/if}
              {#if isOwner}
                <button class="cmt-act-btn" onclick={() => { editTarget = cmt; replyTo = null; commentText = cmt.text; document.getElementById('comment-input')?.focus(); }}>Düzenle</button>
              {/if}
              {#if canDelete}
                <button class="cmt-act-btn danger" onclick={() => deleteTarget = cmt}>Sil</button>
              {/if}
            </div>
          </div>
        </div>

        <!-- "N yanıtı gör" butonu -->
        {#if thread.replies.length > 0}
          <button class="replies-toggle" onclick={() => toggleThread(cmt.id)}>
            <span class="toggle-line"></span>
            {isExpanded ? "Gizle" : `${thread.replies.length} yanıtı gör`}
          </button>
        {/if}

        <!-- Yanıtlar — girintili -->
        {#if isExpanded}
          {#each thread.replies as reply (reply.id)}
            {@const rOwner = $currentUser?.uid === reply.uid}
            {@const rCanDel = rOwner || isPostOwner}
            <div class="cmt-row reply-row">
              <a href="/profile/{reply.uid}" class="cmt-av">
                {#if reply.photo_url}<img src={reply.photo_url} alt={reply.name} />{:else}
                  <span>{(reply.name ?? "?")[0].toUpperCase()}</span>
                {/if}
              </a>
              <div class="cmt-body">
                <div class="cmt-head">
                  <a href="/profile/{reply.uid}" class="cmt-name">{reply.name ?? "Anonim"}</a>
                  <span class="cmt-time">{ago(reply.created_at)}</span>
                </div>
                <p class="cmt-text">{reply.text}</p>
                <div class="cmt-acts">
                  <button class="cmt-like" class:liked={reply.isLikedByMe} onclick={() => toggleCommentLike(reply)}>
                    <svg viewBox="0 0 24 24" fill={reply.isLikedByMe ? "currentColor" : "none"} stroke="currentColor" stroke-width="2" width="13" height="13"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
                  </button>
                  {#if (reply.likes_count ?? 0) > 0}
                    <button class="cmt-likes-count" onclick={() => openCmtLikers(reply)}>{reply.likes_count}</button>
                  {/if}
                  {#if $currentUser}
                    <button class="cmt-act-btn" onclick={() => { replyTo = cmt; editTarget = null; document.getElementById('comment-input')?.focus(); }}>Yanıtla</button>
                  {/if}
                  {#if rOwner}
                    <button class="cmt-act-btn" onclick={() => { editTarget = reply; replyTo = null; commentText = reply.text; document.getElementById('comment-input')?.focus(); }}>Düzenle</button>
                  {/if}
                  {#if rCanDel}
                    <button class="cmt-act-btn danger" onclick={() => deleteTarget = reply}>Sil</button>
                  {/if}
                </div>
              </div>
            </div>
          {/each}
        {/if}

        <div class="cmt-divider"></div>
      {/each}
    {/if}

    <!-- ── Yorum yaz ─────────────────────────────────────────── -->
    {#if $currentUser}
      <div class="composer">
        <!-- Yanıt / düzenle göstergesi -->
        {#if replyTo || editTarget}
          <div class="reply-banner">
            {#if editTarget}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
              <span>Yorum düzenleniyor</span>
            {:else}
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="14" height="14"><polyline points="9 17 4 12 9 7"/><path d="M20 18v-2a4 4 0 0 0-4-4H4"/></svg>
              <span>@{replyTo?.name} yanıtlanıyor</span>
            {/if}
            <button class="banner-close" onclick={() => { replyTo = null; editTarget = null; commentText = ""; }}>✕</button>
          </div>
        {/if}
        <div class="composer-row">
          <div class="cmt-av-sm">
            {#if $currentUser.photoURL}<img src={$currentUser.photoURL} alt="" />{:else}
              <span>{($currentUser.displayName ?? "?")[0].toUpperCase()}</span>
            {/if}
          </div>
          <input
            id="comment-input"
            class="cmt-input"
            placeholder={replyTo ? `@${replyTo.name} yanıtla...` : editTarget ? "Düzenle..." : "Yorum yaz..."}
            bind:value={commentText}
            onkeydown={(e) => e.key === "Enter" && !e.shiftKey && submitComment()}
            maxlength={500}
          />
          <button class="send-btn" onclick={submitComment} disabled={!commentText.trim() || submitting}>
            {#if submitting}
              <div class="spinner"></div>
            {:else}
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            {/if}
          </button>
        </div>
      </div>
    {:else}
      <div class="login-prompt">
        <a href="/login">Yorum yapmak için giriş yap →</a>
      </div>
    {/if}

    <div style="height:24px"></div>
  {/if}
</main>

<!-- ── Beğenen listesi sheet ─────────────────────────────────── -->
{#if showLikers}
  <div class="sheet-backdrop" onclick={() => showLikers = false}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <div>
        <span class="sheet-title">Beğenenler</span>
        {#if likers.length > 0}<span class="sheet-count">{likers.length} kişi</span>{/if}
      </div>
      <button class="sheet-close" onclick={() => showLikers = false}>✕</button>
    </div>
    {#if likers.length > 1}
      <div class="sort-chips">
        {#each [["new","Yeni"],["old","Eski"],["mixed","Karışık"]] as [val, label]}
          <button class="sort-chip" class:active={likersSort === val} onclick={() => likersSort = val as any}>{label}</button>
        {/each}
      </div>
    {/if}
    <div class="sheet-list">
      {#if likersLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else if sortedLikers.length === 0}
        <p class="sheet-empty">Henüz beğeni yok.</p>
      {:else}
        {#each sortedLikers as lk (lk.uid)}
          <a href="/profile/{lk.uid}" class="liker-row" onclick={() => showLikers = false}>
            <div class="liker-av">
              {#if lk.photo_url}<img src={lk.photo_url} alt={lk.name} />{:else}<span>{(lk.name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="liker-name">{lk.name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Yorum beğenen sheet ───────────────────────────────────── -->
{#if cmtLikersId}
  <div class="sheet-backdrop" onclick={() => cmtLikersId = null}></div>
  <div class="sheet">
    <div class="sheet-handle"></div>
    <div class="sheet-header">
      <span class="sheet-title">Yorum Beğenenler</span>
      <button class="sheet-close" onclick={() => cmtLikersId = null}>✕</button>
    </div>
    <div class="sheet-list">
      {#if cmtLikersLoading}
        <div class="sheet-loading"><div class="spinner"></div></div>
      {:else}
        {#each cmtLikers as lk (lk.uid)}
          <a href="/profile/{lk.uid}" class="liker-row" onclick={() => cmtLikersId = null}>
            <div class="liker-av">
              {#if lk.photo_url}<img src={lk.photo_url} alt={lk.name} />{:else}<span>{(lk.name ?? "?")[0].toUpperCase()}</span>{/if}
            </div>
            <span class="liker-name">{lk.name ?? "Anonim"}</span>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
          </a>
        {/each}
      {/if}
    </div>
  </div>
{/if}

<!-- ── Yorum silme onayı ──────────────────────────────────────── -->
{#if deleteTarget}
  <div class="sheet-backdrop" onclick={() => deleteTarget = null}></div>
  <div class="confirm-dialog">
    <h3>Yorumu Sil</h3>
    <p>{deleteTarget.text?.slice(0, 80)}{deleteTarget.text?.length > 80 ? "…" : ""}</p>
    <div class="confirm-btns">
      <button class="confirm-cancel" onclick={() => deleteTarget = null}>İptal</button>
      <button class="confirm-ok" onclick={deleteComment}>Sil</button>
    </div>
  </div>
{/if}

<style>
/* ── Genel ──────────────────────────────────────────────────── */
.wrap { max-width: 600px; margin: 0 auto; padding: 0 0 80px; }

/* Top bar */
.top-bar { max-width: 600px; margin: 0 auto; display: flex; align-items: center; gap: 10px; padding: 12px 14px 8px; position: sticky; top: 0; background: var(--bg); z-index: 10; border-bottom: 1px solid var(--divider); }
.back-btn { width: 36px; height: 36px; border-radius: 50%; border: none; background: var(--surface-var); color: var(--on-bg); display: flex; align-items: center; justify-content: center; cursor: pointer; }
.top-title { font-size: 16px; font-weight: 700; color: var(--on-bg); }

/* Not found */
.not-found { display: flex; flex-direction: column; align-items: center; padding: 60px 20px; gap: 12px; }
.nf-emoji { font-size: 48px; }
.not-found p { font-size: 15px; color: var(--muted); }
.nf-back { background: none; border: none; color: var(--primary); font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit; }

/* Kart */
.card { background: var(--card); padding: 16px; }
.post-card { border-bottom: 6px solid var(--surface-var); }

/* Kart başlığı */
.card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.avatar-link { flex-shrink: 0; text-decoration: none; }
.avatar-ring { width: 46px; height: 46px; border-radius: 50%; background: linear-gradient(135deg, var(--primary), color-mix(in srgb, var(--primary) 60%, purple)); padding: 1.5px; display: flex; align-items: center; justify-content: center; }
.avatar-img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; display: block; }
.avatar-fallback { width: 100%; height: 100%; border-radius: 50%; background: var(--surface-var); color: var(--on-bg); font-size: 17px; font-weight: 700; display: flex; align-items: center; justify-content: center; }
.meta { flex: 1; min-width: 0; }
.display-name { font-size: 15px; font-weight: 700; color: var(--on-bg); text-decoration: none; display: block; }
.display-name:hover { text-decoration: underline; }
.meta-row { display: flex; align-items: center; gap: 4px; margin-top: 2px; }
.username, .time { font-size: 12px; color: var(--muted); }
.dot { font-size: 12px; color: var(--muted); }

/* Düzenle/sil butonları */
.post-actions-top { display: flex; gap: 4px; }
.act-icon { width: 34px; height: 34px; border-radius: 50%; border: none; background: var(--surface-var); color: var(--muted); display: flex; align-items: center; justify-content: center; cursor: pointer; text-decoration: none; transition: background 0.15s; }
.act-icon:hover { background: var(--divider); }
.act-icon.danger { color: #ef4444; }

/* İçerik */
.category-chip { display: inline-block; background: color-mix(in srgb, var(--primary) 12%, transparent); color: var(--primary); font-size: 11px; font-weight: 600; padding: 3px 10px; border-radius: 99px; margin-bottom: 8px; }
.post-title { font-size: 20px; font-weight: 800; color: var(--on-bg); line-height: 1.3; margin-bottom: 8px; }
.post-text { font-size: 16px; color: var(--on-bg); line-height: 1.7; white-space: pre-wrap; margin-bottom: 12px; }
.post-img { width: 100%; border-radius: 12px; max-height: 420px; object-fit: cover; margin-bottom: 12px; display: block; }

/* QuoteCard */
.quote-card { position: relative; background: linear-gradient(135deg, color-mix(in srgb,#F59E0B 8%,transparent), color-mix(in srgb,#9B72F5 6%,transparent)); border: 1px solid color-mix(in srgb,#F59E0B 35%,transparent); border-radius: 14px; padding: 14px 14px 12px; margin-bottom: 12px; overflow: hidden; }
.quote-mark { position: absolute; top: -6px; left: 6px; font-size: 52px; color: color-mix(in srgb,#F59E0B 15%,transparent); font-weight: 900; line-height: 1; pointer-events: none; font-family: Georgia,serif; }
.quote-inner { padding-left: 8px; position: relative; }
.quote-text { font-size: 15px; font-style: italic; color: var(--on-surface); line-height: 1.65; font-weight: 500; margin-bottom: 10px; }
.quote-source { display: flex; align-items: center; gap: 8px; }
.quote-cover { width: 28px; height: 42px; border-radius: 3px; background: color-mix(in srgb,#F59E0B 10%,transparent); display: flex; align-items: center; justify-content: center; overflow: hidden; flex-shrink: 0; }
.quote-cover img { width: 100%; height: 100%; object-fit: cover; }
.quote-cover svg { color: #F59E0B; }
.quote-book { display: block; font-size: 11px; font-weight: 600; color: #F59E0B; }
.quote-author { display: block; font-size: 10px; color: var(--muted); margin-top: 1px; }

/* Repost */
.repost-embed { background: var(--surface-var); border: 0.5px solid var(--divider); border-radius: 13px; padding: 12px; margin-bottom: 12px; cursor: pointer; display: flex; flex-direction: column; gap: 5px; }
.repost-label { display: flex; align-items: center; gap: 4px; font-size: 9px; font-weight: 700; color: var(--primary); letter-spacing: 0.8px; text-transform: uppercase; }
.repost-title { font-size: 13px; font-weight: 600; color: var(--on-bg); }
.repost-text { font-size: 13px; color: var(--on-surface); line-height: 1.5; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; }
.repost-img { width: 100%; height: 120px; object-fit: cover; border-radius: 8px; }

/* Aksiyonlar */
.actions { display: flex; align-items: center; gap: 4px; padding-top: 4px; }
.act-btn { display: flex; align-items: center; gap: 5px; padding: 8px 12px; border-radius: 20px; color: var(--muted); cursor: pointer; border: none; background: transparent; transition: color 0.15s, background 0.15s; }
.act-btn:hover { background: var(--surface-var); }
.act-btn.liked { color: #FF3A5C; }
.act-btn.save-btn.saved { color: #F59E0B; }

/* Beğeni sayısı satırı */
.divider { height: 1px; background: var(--divider); margin: 8px 0; }
.likes-row { display: flex; align-items: center; gap: 7px; padding: 10px 0 4px; cursor: pointer; border: none; background: none; color: var(--on-bg); font-size: 14px; font-family: inherit; }
.likes-row:hover span { text-decoration: underline; }

/* Yorumlar başlığı */
.section-header { display: flex; align-items: center; justify-content: space-between; padding: 14px 16px 10px; }
.section-title { font-size: 16px; font-weight: 700; color: var(--on-bg); }
.section-count { font-size: 13px; color: var(--muted); }
.divider-full { height: 1px; background: var(--divider); }

/* Yorum satırı */
.cmt-row { display: flex; gap: 10px; padding: 12px 16px 6px; }
.reply-row { padding-left: 48px; background: color-mix(in srgb, var(--surface-var) 50%, transparent); }
.cmt-av { width: 36px; height: 36px; border-radius: 50%; background: var(--surface-var); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 700; color: var(--on-bg); text-decoration: none; }
.cmt-av img { width: 100%; height: 100%; object-fit: cover; }
.cmt-body { flex: 1; }
.cmt-head { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; }
.cmt-name { font-size: 13px; font-weight: 700; color: var(--on-bg); text-decoration: none; }
.cmt-name:hover { text-decoration: underline; }
.cmt-time { font-size: 11px; color: var(--muted); }
.cmt-text { font-size: 14px; color: var(--on-bg); line-height: 1.55; white-space: pre-wrap; }
.cmt-acts { display: flex; align-items: center; gap: 10px; margin-top: 5px; }
.cmt-like { display: flex; align-items: center; gap: 3px; background: none; border: none; cursor: pointer; color: var(--muted); padding: 0; }
.cmt-like.liked { color: #FF3A5C; }
.cmt-likes-count { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 11px; padding: 0; font-family: inherit; }
.cmt-likes-count:hover { text-decoration: underline; }
.cmt-act-btn { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 12px; padding: 0; font-family: inherit; font-weight: 500; }
.cmt-act-btn:hover { color: var(--primary); }
.cmt-act-btn.danger { color: rgba(239,68,68,0.7); }
.cmt-act-btn.danger:hover { color: #ef4444; }
.cmt-divider { height: 0.5px; background: color-mix(in srgb, var(--divider) 60%, transparent); margin: 2px 16px 0 56px; }

/* Yanıt toggle */
.replies-toggle { display: flex; align-items: center; gap: 8px; padding: 4px 16px 8px 56px; background: none; border: none; cursor: pointer; color: var(--muted); font-size: 12px; font-weight: 600; font-family: inherit; }
.toggle-line { display: block; width: 24px; height: 1px; background: var(--muted); opacity: 0.5; }

/* Boş */
.empty-cmt { display: flex; flex-direction: column; align-items: center; padding: 32px 20px; gap: 8px; color: var(--muted); }
.empty-cmt span { font-size: 32px; }
.empty-cmt p { font-size: 14px; }

/* Composer */
.composer { position: sticky; bottom: 60px; background: var(--bg); border-top: 1px solid var(--divider); padding: 8px 12px; padding-bottom: max(8px, env(safe-area-inset-bottom)); z-index: 20; }
.reply-banner { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--primary); padding: 4px 0 6px; }
.reply-banner span { flex: 1; }
.banner-close { background: none; border: none; cursor: pointer; color: var(--muted); font-size: 14px; }
.composer-row { display: flex; align-items: center; gap: 8px; }
.cmt-av-sm { width: 34px; height: 34px; border-radius: 50%; background: var(--surface-var); overflow: hidden; flex-shrink: 0; display: flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; color: var(--on-bg); }
.cmt-av-sm img { width: 100%; height: 100%; object-fit: cover; }
.cmt-input { flex: 1; border: 1px solid var(--divider); border-radius: 22px; padding: 9px 14px; font-size: 14px; background: var(--surface-var); color: var(--on-bg); outline: none; font-family: inherit; }
.cmt-input:focus { border-color: var(--primary); }
.send-btn { width: 38px; height: 38px; border-radius: 50%; background: var(--primary); color: #fff; border: none; cursor: pointer; display: flex; align-items: center; justify-content: center; flex-shrink: 0; transition: opacity 0.15s; }
.send-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.login-prompt { text-align: center; padding: 16px; }
.login-prompt a { color: var(--primary); font-weight: 600; text-decoration: none; font-size: 14px; }

/* Sheet */
.sheet-backdrop { position: fixed; inset: 0; background: rgba(0,0,0,0.4); z-index: 300; }
.sheet { position: fixed; bottom: 0; left: 0; right: 0; max-width: 600px; margin: 0 auto; background: var(--surface); border-radius: 20px 20px 0 0; z-index: 301; display: flex; flex-direction: column; max-height: 70vh; }
.sheet-handle { width: 40px; height: 4px; background: var(--divider); border-radius: 2px; margin: 12px auto 4px; flex-shrink: 0; }
.sheet-header { display: flex; align-items: center; justify-content: space-between; padding: 0 16px 12px; border-bottom: 1px solid var(--divider); flex-shrink: 0; }
.sheet-title { font-size: 15px; font-weight: 700; color: var(--on-bg); }
.sheet-count { font-size: 12px; color: var(--muted); margin-left: 6px; }
.sheet-close { background: none; border: none; color: var(--muted); font-size: 18px; cursor: pointer; }
.sheet-list { flex: 1; overflow-y: auto; padding: 8px 0; }
.sheet-loading { display: flex; justify-content: center; padding: 24px; }
.sheet-empty { text-align: center; color: var(--muted); font-size: 14px; padding: 24px 0; }
.sort-chips { display: flex; gap: 8px; padding: 8px 16px 12px; flex-shrink: 0; }
.sort-chip { padding: 5px 14px; border-radius: 99px; font-size: 12px; font-weight: 500; border: none; cursor: pointer; background: var(--surface-var); color: var(--muted); font-family: inherit; }
.sort-chip.active { background: var(--primary); color: #fff; font-weight: 600; }
.liker-row { display: flex; align-items: center; gap: 10px; padding: 10px 16px; text-decoration: none; transition: background 0.1s; }
.liker-row:hover { background: var(--surface-var); }
.liker-av { width: 38px; height: 38px; border-radius: 50%; background: var(--surface-var); overflow: hidden; display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; color: var(--on-bg); flex-shrink: 0; }
.liker-av img { width: 100%; height: 100%; object-fit: cover; }
.liker-name { flex: 1; font-size: 14px; font-weight: 600; color: var(--on-bg); }

/* Confirm dialog */
.confirm-dialog { position: fixed; left: 50%; top: 50%; transform: translate(-50%,-50%); background: var(--surface); border-radius: 18px; padding: 24px; width: min(340px, 90vw); z-index: 302; box-shadow: 0 8px 32px rgba(0,0,0,0.2); }
.confirm-dialog h3 { font-size: 16px; font-weight: 700; color: var(--on-bg); margin-bottom: 8px; }
.confirm-dialog p { font-size: 13px; color: var(--muted); line-height: 1.5; margin-bottom: 20px; }
.confirm-btns { display: flex; gap: 10px; justify-content: flex-end; }
.confirm-cancel { background: var(--surface-var); border: none; border-radius: 10px; padding: 9px 18px; font-size: 14px; color: var(--muted); cursor: pointer; font-family: inherit; }
.confirm-ok { background: #ef4444; border: none; border-radius: 10px; padding: 9px 18px; font-size: 14px; color: #fff; font-weight: 600; cursor: pointer; font-family: inherit; }

/* Skeleton */
.skeleton-card { border-radius: 0; }
.sk-head { display: flex; gap: 10px; margin-bottom: 12px; }
.sk-av { width: 46px; height: 46px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }
.sk-av-sm { width: 36px; height: 36px; border-radius: 50%; background: var(--shimmer); flex-shrink: 0; animation: shimmer 1.4s ease-in-out infinite; }
.sk-meta { flex: 1; display: flex; flex-direction: column; gap: 8px; padding-top: 4px; }
.sk-line { height: 13px; background: var(--shimmer); border-radius: 6px; animation: shimmer 1.4s ease-in-out infinite; }
.sk-cmt { display: flex; gap: 10px; padding: 12px 16px; }
.sk-cmt-body { flex: 1; display: flex; flex-direction: column; gap: 8px; padding-top: 4px; }

/* Spinner */
.spinner { width: 16px; height: 16px; border: 2px solid rgba(255,255,255,0.4); border-top-color: #fff; border-radius: 50%; animation: spin 0.7s linear infinite; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer { 0%,100% { opacity: 1; } 50% { opacity: 0.5; } }
</style>
