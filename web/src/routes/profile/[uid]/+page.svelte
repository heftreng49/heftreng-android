<script lang="ts">
  import { onMount } from "svelte";
  import { page } from "$app/stores";
  import { doc, getDoc, collection, query, where, orderBy, getDocs } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import { currentUser } from "$lib/store/auth";
  import Navbar from "$lib/components/Navbar.svelte";

  const uid = $derived($page.params.uid);

  let profile = $state<any>(null);
  let posts = $state<any[]>([]);
  let loading = $state(true);
  let isOwn = $derived($currentUser?.uid === uid);

  onMount(async () => {
    try {
      const userSnap = await getDoc(doc(db, "users", uid));
      if (userSnap.exists()) profile = userSnap.data();

      const q = query(
        collection(db, "feed"),
        where("uid", "==", uid),
        orderBy("ts", "desc")
      );
      const snap = await getDocs(q);
      posts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
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
</script>

<Navbar />

{#if loading}
  <div class="loading">
    <div class="sk-av-lg"></div>
    <div class="sk-l w60"></div>
    <div class="sk-l w40"></div>
  </div>
{:else if !profile}
  <p class="empty">Kullanici bulunamadi.</p>
{:else}
  <div class="profile-header">
    {#if profile.photoURL}
      <img src={profile.photoURL} alt="" class="av-lg"/>
    {:else}
      <div class="av-ph-lg">{(profile.displayName??"?")[0].toUpperCase()}</div>
    {/if}
    <h1 class="dname">{profile.displayName ?? "Anonim"}</h1>
    {#if profile.username}<p class="uname">@{profile.username}</p>{/if}
    {#if profile.bio}<p class="bio">{profile.bio}</p>{/if}
    <div class="stats">
      <div class="stat"><span class="stat-n">{posts.length}</span><span class="stat-l">Gonderi</span></div>
    </div>
    {#if !isOwn}
      <button class="follow-btn">Takip Et</button>
    {:else}
      <a href="/settings" class="edit-btn">Profili Duzenle</a>
    {/if}
  </div>

  <div class="posts">
    {#if posts.length === 0}
      <p class="empty">Henuz gonderi yok.</p>
    {:else}
      {#each posts as p (p.id)}
        <article class="card">
          <div class="time">{ago(p.ts)}</div>
          {#if p.text}<p class="body">{p.text}</p>{/if}
          {#if p.imgUrl || p.imageURL}
            <img src={p.imgUrl || p.imageURL} alt="" class="post-img"/>
          {/if}
          <div class="acts">
            <button class="act">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>
              {p.likesCount ?? 0}
            </button>
            <button class="act">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
              {p.commentsCount ?? 0}
            </button>
          </div>
        </article>
      {/each}
    {/if}
  </div>
{/if}

<style>
.loading { max-width:600px; margin:40px auto; padding:0 16px; display:flex; flex-direction:column; align-items:center; gap:12px; }
.sk-av-lg { width:80px; height:80px; border-radius:50%; background:var(--shimmer); }
.sk-l { height:14px; background:var(--shimmer); border-radius:6px; width:100%; }
.w60 { width:60%; } .w40 { width:40%; }

.profile-header { max-width:600px; margin:0 auto; padding:24px 16px 16px; display:flex; flex-direction:column; align-items:center; gap:8px; border-bottom:1px solid var(--divider); }
.av-lg { width:80px; height:80px; border-radius:50%; object-fit:cover; }
.av-ph-lg { width:80px; height:80px; border-radius:50%; background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-size:32px; font-weight:700; }
.dname { font-size:20px; font-weight:700; color:var(--on-bg); margin:4px 0 0; }
.uname { font-size:14px; color:var(--muted); }
.bio { font-size:15px; color:var(--on-surface); text-align:center; max-width:400px; line-height:1.5; }
.stats { display:flex; gap:24px; margin:8px 0; }
.stat { display:flex; flex-direction:column; align-items:center; gap:2px; }
.stat-n { font-size:18px; font-weight:700; color:var(--on-bg); }
.stat-l { font-size:12px; color:var(--muted); }
.follow-btn { padding:8px 28px; background:var(--primary); color:#fff; border-radius:20px; font-size:14px; font-weight:600; border:none; cursor:pointer; }
.edit-btn { padding:8px 28px; background:var(--surface-var); color:var(--on-bg); border-radius:20px; font-size:14px; font-weight:600; text-decoration:none; }

.posts { max-width:600px; margin:0 auto; padding:12px; display:flex; flex-direction:column; gap:10px; }
.empty { text-align:center; padding:40px; color:var(--muted); }
.card { background:var(--card); border-radius:16px; padding:14px 16px; box-shadow:0 1px 4px rgba(0,0,0,.08); }
.time { font-size:12px; color:var(--muted); margin-bottom:8px; }
.body { font-size:15px; color:var(--on-bg); line-height:1.65; white-space:pre-wrap; margin-bottom:12px; }
.post-img { width:100%; border-radius:12px; margin-bottom:12px; max-height:400px; object-fit:cover; }
.acts { display:flex; gap:4px; }
.act { display:flex; align-items:center; gap:5px; padding:7px 14px; border-radius:20px; background:var(--surface-var); color:var(--muted); font-size:14px; font-weight:500; cursor:pointer; border:none; }
.act svg { width:16px; height:16px; }
</style>
