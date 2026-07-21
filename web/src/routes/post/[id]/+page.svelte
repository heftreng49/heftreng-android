<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/stores";
  import { doc, getDoc } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";
  import Navbar from "$lib/components/Navbar.svelte";

  const postId = $derived($page.params.id);

  let post = $state<any>(null);
  let comments = $state<any[]>([]);
  let loading = $state(true);
  let commentsLoading = $state(true);
  let liked = $state(false);
  let likesCount = $state(0);
  let commentText = $state("");
  let submitting = $state(false);

  onMount(async () => {
    // 1. Firestore'dan gonderiyi cek
    try {
      const snap = await getDoc(doc(db, "feed", postId));
      if (snap.exists()) {
        post = { id: snap.id, ...snap.data() };
      }
    } catch(e) { console.error(e); }
    finally { loading = false; }

    // 2. Supabase'den begeni sayisi ve durum
    try {
      const [countRes, likedRes] = await Promise.all([
        supabase.from("feed_likes").select("id", { count: "exact", head: true }).eq("post_id", postId),
        $currentUser
          ? supabase.from("feed_likes").select("id").eq("post_id", postId).eq("user_uid", $currentUser.uid).maybeSingle()
          : Promise.resolve({ data: null }),
      ]);
      likesCount = countRes.count ?? post?.likesCount ?? 0;
      liked = !!likedRes.data;
    } catch(e) { console.error(e); }

    // 3. Supabase'den yorumlar
    try {
      const { data } = await supabase
        .from("feed_comments")
        .select("*")
        .eq("post_id", postId)
        .order("created_at", { ascending: true });
      comments = data ?? [];
    } catch(e) { console.error(e); }
    finally { commentsLoading = false; }
  });

  async function toggleLike() {
    if (!$currentUser) return;
    const id = $currentUser.uid + "_" + postId;
    if (liked) {
      liked = false; likesCount--;
      await supabase.from("feed_likes").delete().eq("id", id);
    } else {
      liked = true; likesCount++;
      await supabase.from("feed_likes").upsert({ id, user_uid: $currentUser.uid, post_id: postId });
    }
  }

  async function addComment() {
    if (!$currentUser || !commentText.trim()) return;
    submitting = true;
    try {
      const { data, error } = await supabase.from("feed_comments").insert({
        post_id:   postId,
        uid:       $currentUser.uid,
        name:      $currentUser.displayName ?? "",
        photo_url: $currentUser.photoURL ?? "",
        text:      commentText.trim(),
      }).select().single();
      if (!error && data) {
        comments = [...comments, data];
        commentText = "";
      }
    } catch(e) { console.error(e); }
    finally { submitting = false; }
  }

  function ago(ts: any) {
    const ms = ts?.seconds ? ts.seconds * 1000 : ts ? new Date(ts).getTime() : 0;
    const m = Math.floor((Date.now() - ms) / 60000);
    if (m < 1) return "az once";
    if (m < 60) return m + " dk";
    if (m < 1440) return Math.floor(m/60) + " sa";
    return Math.floor(m/1440) + " g";
  }
</script>

<Navbar />

{#if loading}
  <div class="loading">
    <div class="sk-av"></div>
    <div class="sk-lines"><div class="sk-l"></div><div class="sk-l w60"></div></div>
  </div>
{:else if !post}
  <p class="empty">Gonderi bulunamadi.</p>
{:else}
  <main class="wrap">
    <!-- Gonderi -->
    <article class="card">
      <div class="head">
        <a href="/profile/{post.uid}" class="av-wrap">
          {#if post.photoURL}
            <img src={post.photoURL} alt="" class="av"/>
          {:else}
            <div class="av-ph">{(post.displayName??"?")[0].toUpperCase()}</div>
          {/if}
        </a>
        <div class="meta">
          <a href="/profile/{post.uid}" class="name">{post.displayName ?? "Anonim"}</a>
          <div class="time">{ago(post.ts)}</div>
        </div>
      </div>
      {#if post.text}<p class="body">{post.text}</p>{/if}
      {#if post.imgUrl || post.imageURL}
        <img src={post.imgUrl || post.imageURL} alt="" class="post-img"/>
      {/if}
      <div class="acts">
        <button class="act" class:liked onclick={toggleLike}>
          <svg viewBox="0 0 24 24" fill={liked ? "currentColor" : "none"} stroke="currentColor" stroke-width="2">
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
          <span>{likesCount}</span>
        </button>
        <div class="act static">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
          </svg>
          <span>{comments.length}</span>
        </div>
      </div>
    </article>

    <!-- Yorum yaz -->
    {#if $currentUser}
      <div class="composer">
        <div class="av-sm-wrap">
          {#if $currentUser.photoURL}
            <img src={$currentUser.photoURL} alt="" class="av-sm"/>
          {:else}
            <div class="av-ph-sm">{($currentUser.displayName??"?")[0].toUpperCase()}</div>
          {/if}
        </div>
        <input
          class="cmt-input"
          placeholder="Yorum yaz..."
          bind:value={commentText}
          onkeydown={(e) => e.key === "Enter" && addComment()}
          maxlength={500}
        />
        <button class="send" onclick={addComment} disabled={!commentText.trim() || submitting}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
          </svg>
        </button>
      </div>
    {/if}

    <!-- Yorumlar -->
    <div class="comments">
      {#if commentsLoading}
        {#each Array(3) as _}
          <div class="sk-cmt"><div class="sk-av-sm"></div><div class="sk-lines"><div class="sk-l"></div><div class="sk-l w80"></div></div></div>
        {/each}
      {:else if comments.length === 0}
        <p class="empty-cmt">Henuz yorum yok.</p>
      {:else}
        {#each comments as c (c.id)}
          <div class="comment">
            <a href="/profile/{c.uid}" class="av-wrap">
              {#if c.photo_url}
                <img src={c.photo_url} alt="" class="av-sm"/>
              {:else}
                <div class="av-ph-sm">{(c.name??"?")[0].toUpperCase()}</div>
              {/if}
            </a>
            <div class="cmt-body">
              <a href="/profile/{c.uid}" class="cmt-name">{c.name || "Anonim"}</a>
              <p class="cmt-text">{c.text}</p>
              <span class="cmt-time">{ago(c.created_at)}</span>
            </div>
          </div>
        {/each}
      {/if}
    </div>
  </main>
{/if}

<style>
.wrap { max-width:600px; margin:0 auto; padding:12px; display:flex; flex-direction:column; gap:10px; }
.empty { text-align:center; padding:40px; color:var(--muted); }

.card { background:var(--card); border-radius:16px; padding:14px 16px; box-shadow:0 1px 4px rgba(0,0,0,.08); }
.head { display:flex; gap:10px; margin-bottom:10px; align-items:center; }
.av-wrap { flex-shrink:0; }
.av,.av-ph { width:42px; height:42px; border-radius:50%; object-fit:cover; display:block; }
.av-ph { background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; font-size:16px; }
.meta { display:flex; flex-direction:column; gap:2px; }
.name { font-weight:600; font-size:15px; color:var(--on-bg); text-decoration:none; }
.time { font-size:12px; color:var(--muted); }
.body { font-size:15px; color:var(--on-bg); line-height:1.65; white-space:pre-wrap; margin-bottom:12px; }
.post-img { width:100%; border-radius:12px; margin-bottom:12px; max-height:400px; object-fit:cover; }
.acts { display:flex; gap:4px; }
.act { display:flex; align-items:center; gap:5px; padding:7px 14px; border-radius:20px; background:var(--surface-var); color:var(--muted); font-size:14px; font-weight:500; cursor:pointer; border:none; transition:background .15s, color .15s; }
.act.liked { background:var(--primary); color:#fff; }
.act.static { cursor:default; }
.act svg { width:16px; height:16px; }

.composer { background:var(--card); border-radius:16px; padding:10px 12px; display:flex; align-items:center; gap:10px; box-shadow:0 1px 4px rgba(0,0,0,.08); }
.av-sm,.av-ph-sm { width:34px; height:34px; border-radius:50%; object-fit:cover; flex-shrink:0; }
.av-ph-sm { background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; font-size:13px; }
.av-sm-wrap { flex-shrink:0; }
.cmt-input { flex:1; border:none; background:var(--surface-var); border-radius:20px; padding:8px 14px; font-size:14px; color:var(--on-bg); outline:none; font-family:inherit; }
.cmt-input::placeholder { color:var(--muted); }
.send { background:var(--primary); color:#fff; border:none; border-radius:50%; width:34px; height:34px; display:flex; align-items:center; justify-content:center; cursor:pointer; flex-shrink:0; }
.send:disabled { opacity:.4; cursor:not-allowed; }
.send svg { width:15px; height:15px; }

.comments { display:flex; flex-direction:column; gap:8px; }
.empty-cmt { text-align:center; padding:20px; color:var(--muted); font-size:14px; }
.comment { background:var(--card); border-radius:14px; padding:12px 14px; display:flex; gap:10px; box-shadow:0 1px 3px rgba(0,0,0,.06); }
.cmt-body { display:flex; flex-direction:column; gap:3px; }
.cmt-name { font-weight:600; font-size:14px; color:var(--on-bg); text-decoration:none; }
.cmt-text { font-size:14px; color:var(--on-bg); line-height:1.5; white-space:pre-wrap; }
.cmt-time { font-size:12px; color:var(--muted); }

.loading { max-width:600px; margin:20px auto; padding:14px 16px; display:flex; gap:10px; }
.sk-av { width:42px; height:42px; border-radius:50%; background:var(--shimmer); flex-shrink:0; }
.sk-av-sm { width:34px; height:34px; border-radius:50%; background:var(--shimmer); flex-shrink:0; }
.sk-lines { flex:1; display:flex; flex-direction:column; gap:8px; padding-top:4px; }
.sk-l { height:14px; background:var(--shimmer); border-radius:6px; }
.w60 { width:60%; } .w80 { width:80%; }
.sk-cmt { display:flex; gap:10px; padding:12px 14px; background:var(--card); border-radius:14px; }
</style>
