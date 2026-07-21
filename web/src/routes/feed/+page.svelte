<script lang="ts">
  import { onMount } from "svelte";
  import { collection, query, orderBy, limit, getDocs } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { supabase } from "$lib/supabase/config";
  import { currentUser } from "$lib/store/auth";
  import Navbar from "$lib/components/Navbar.svelte";

  let posts = $state<any[]>([]);
  let loading = $state(true);

  onMount(async () => {
    try {
      const q = query(collection(db, "feed"), orderBy("ts", "desc"), limit(30));
      const snap = await getDocs(q);
      posts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
      await loadInteractions(posts.map(p => p.id));
      await loadLikeCounts(posts.map(p => p.id));
    } catch(e) { console.error(e); }
    finally { loading = false; }
  });

  function ago(ts: any) {
    const ms = ts?.seconds ? ts.seconds * 1000 : Number(ts);
    const m = Math.floor((Date.now() - ms) / 60000);
    if (m < 1) return "az once";
    if (m < 60) return m + " dk";
    if (m < 1440) return Math.floor(m/60) + " sa";
    return Math.floor(m/1440) + " g";
  }

  async function like(p: any) {
    if (!$currentUser) return;
    // Optimistic update
    posts = posts.map(x => x.id === p.id ? {...x, likesCount: (x.likesCount??0)+1, liked: true} : x);
    try {
      await supabase.from('feed_likes').upsert({
        id:       $currentUser.uid + '_' + p.id,
        user_uid: $currentUser.uid,
        post_id:  p.id,
      });
    } catch(e) { console.error(e); }
  }

  async function unlike(p: any) {
    if (!$currentUser) return;
    posts = posts.map(x => x.id === p.id ? {...x, likesCount: Math.max((x.likesCount??1)-1,0), liked: false} : x);
    try {
      await supabase.from('feed_likes').delete().eq('id', $currentUser.uid + '_' + p.id);
    } catch(e) { console.error(e); }
  }

  async function save(p: any) {
    if (!$currentUser) return;
    posts = posts.map(x => x.id === p.id ? {...x, saved: true} : x);
    try {
      await supabase.from('feed_saves').upsert({
        id:       $currentUser.uid + '_' + p.id,
        user_uid: $currentUser.uid,
        post_id:  p.id,
      });
    } catch(e) { console.error(e); }
  }

  async function loadLikeCounts(postIds: string[]) {
    if (postIds.length === 0) return;
    try {
      const { data } = await supabase
        .from('feed_likes')
        .select('post_id')
        .in('post_id', postIds);
      if (!data) return;
      const counts: Record<string, number> = {};
      for (const r of data) {
        counts[r.post_id] = (counts[r.post_id] ?? 0) + 1;
      }
      posts = posts.map(p => ({ ...p, likesCount: counts[p.id] ?? p.likesCount ?? 0 }));
    } catch(e) { console.error(e); }
  }

  // Kullanicinin begendigi ve kaydettigi postlari yukle
  async function loadInteractions(postIds: string[]) {
    if (!$currentUser || postIds.length === 0) return;
    try {
      const [likesRes, savesRes] = await Promise.all([
        supabase.from('feed_likes').select('post_id').eq('user_uid', $currentUser.uid).in('post_id', postIds),
        supabase.from('feed_saves').select('post_id').eq('user_uid', $currentUser.uid).in('post_id', postIds),
      ]);
      const likedIds = new Set((likesRes.data ?? []).map((r: any) => r.post_id));
      const savedIds = new Set((savesRes.data ?? []).map((r: any) => r.post_id));
      posts = posts.map(p => ({ ...p, liked: likedIds.has(p.id), saved: savedIds.has(p.id) }));
    } catch(e) { console.error(e); }
  }
</script>

<Navbar />
<main class="feed">
  {#if loading}
    {#each Array(5) as _}
      <div class="skeleton"><div class="sk-av"></div><div class="sk-lines"><div class="sk-l"></div><div class="sk-l w80"></div></div></div>
    {/each}
  {:else if posts.length === 0}
    <p class="empty">Henuz gonderi yok.</p>
  {:else}
    {#each posts as p (p.id)}
      <article class="card">
        <div class="head">
          <a href="/profile/{p.uid}" class="av-wrap">
            {#if p.photoURL}<img src={p.photoURL} alt="" class="av"/>{:else}<div class="av-ph">{(p.displayName??"?")[0].toUpperCase()}</div>{/if}
          </a>
          <div class="meta">
            <a href="/profile/{p.uid}" class="name">{p.displayName ?? p.name ?? "Anonim"}</a>
            <div class="time">{ago(p.ts)}</div>
          </div>
        </div>
        {#if p.text}<p class="body">{p.text}</p>{/if}
        {#if p.imgUrl || p.imageURL}<img src={p.imgUrl || p.imageURL} alt="" class="post-img"/>{/if}
        <div class="acts">
          <!-- Begeni -->
          <button class="act-btn" class:liked={p.liked} onclick={(e) => { e.stopPropagation(); p.liked ? unlike(p) : like(p); }}>
            <svg viewBox="0 0 24 24" fill={p.liked ? "currentColor" : "none"} stroke="currentColor" stroke-width="2">
              <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
            </svg>
            {#if (p.likesCount ?? 0) > 0}<span>{p.likesCount}</span>{/if}
          </button>
          <!-- Yorum -->
          <button class="act-btn" onclick={(e) => { e.stopPropagation(); window.location.href = '/post/' + p.id; }}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
            </svg>
            {#if (p.commentsCount ?? 0) > 0}<span>{p.commentsCount}</span>{/if}
          </button>
          <!-- Repost -->
          <button class="act-btn" class:reposted={p.repostedByMe} onclick={(e) => e.stopPropagation()}>
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/>
              <polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/>
            </svg>
            {#if (p.repostsCount ?? 0) > 0}<span>{p.repostsCount}</span>{/if}
          </button>
          <!-- Kaydet -->
          <button class="act-btn save" class:saved={p.saved} onclick={(e) => { e.stopPropagation(); save(p); }} style="margin-left:auto">
            <svg viewBox="0 0 24 24" fill={p.saved ? "currentColor" : "none"} stroke="currentColor" stroke-width="2">
              <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>
            </svg>
          </button>
        </div>
      </article>
    {/each}
  {/if}
</main>

<style>
.feed { max-width:600px; margin:0 auto; padding:12px 12px 40px; display:flex; flex-direction:column; gap:10px; }
.empty { text-align:center; padding:40px; color:var(--muted); }
.card { background:var(--card); border-radius:16px; padding:14px 16px; box-shadow:0 1px 4px rgba(0,0,0,.08); }
.head { display:flex; gap:10px; margin-bottom:10px; align-items:center; }
.av-wrap { flex-shrink:0; }
.av,.av-ph { width:42px; height:42px; border-radius:50%; object-fit:cover; display:block; }
.av-ph { background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; font-size:16px; }
.meta { display:flex; flex-direction:column; gap:2px; }
.name { font-weight:600; font-size:15px; color:var(--on-bg); text-decoration:none; }
.name:hover { text-decoration:underline; }
.time { font-size:12px; color:var(--muted); }
.body { font-size:15px; color:var(--on-bg); line-height:1.65; white-space:pre-wrap; margin-bottom:12px; }
.post-img { width:100%; border-radius:12px; margin-bottom:12px; max-height:400px; object-fit:cover; }
.acts { display:flex; align-items:center; gap:2px; margin-top:8px; }
.act-btn { display:flex; align-items:center; gap:4px; padding:6px 10px; border-radius:20px; color:var(--muted); font-size:13px; font-weight:500; cursor:pointer; border:none; background:transparent; transition:color .15s, background .15s; }
.act-btn:hover { background:var(--surface-var); color:var(--on-bg); }
.act-btn.liked { color:#FF3A5C; }
.act-btn.reposted { color:#F59E0B; }
.act-btn.saved { color:#F59E0B; }
.act-btn.save:hover { background:var(--surface-var); color:#F59E0B; }
.act-btn svg { width:18px; height:18px; }
.skeleton { display:flex; gap:10px; padding:14px 16px; border-radius:16px; background:var(--card); }
.sk-av { width:42px; height:42px; border-radius:50%; background:var(--shimmer); flex-shrink:0; }
.sk-lines { flex:1; display:flex; flex-direction:column; gap:8px; padding-top:4px; }
.sk-l { height:14px; background:var(--shimmer); border-radius:6px; }
.w80 { width:80%; }
</style>
