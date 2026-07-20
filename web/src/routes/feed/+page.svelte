<script lang="ts">
  import { onMount } from "svelte";
  import { collection, query, orderBy, limit, getDocs } from "firebase/firestore";
  import { db } from "$lib/firebase/config";
  import Navbar from "$lib/components/Navbar.svelte";

  let posts = $state<any[]>([]);
  let loading = $state(true);

  onMount(async () => {
    try {
      const q = query(collection(db, "feed"), orderBy("ts", "desc"), limit(30));
      const snap = await getDocs(q);
      posts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    } catch(e) { console.error(e); }
    finally { loading = false; }
  });

  function ago(ts: any) {
    const ms = ts.seconds ? ts.seconds * 1000 : Number(ts);
    const m = Math.floor((Date.now() - ms) / 60000);
    if (m < 1) return "az önce";
    if (m < 60) return m + " dk";
    if (m < 1440) return Math.floor(m/60) + " sa";
    return Math.floor(m/1440) + " g";
  }
</script>

<Navbar />
<main class="feed">
  {#if loading}
    {#each Array(5) as _}
      <div class="skeleton"><div class="sk-av"></div><div class="sk-lines"><div class="sk-l"></div><div class="sk-l w80"></div></div></div>
    {/each}
  {:else if posts.length === 0}
    <p class="empty">Henüz gönderi yok.</p>
  {:else}
    {#each posts as p (p.id)}
      <article class="card">
        <div class="head">
          {#if p.photoURL}<img src={p.photoURL} alt="" class="av"/>{:else}<div class="av-ph">{(p.displayName??"?")[0]}</div>{/if}
          <div>
            <div class="name">{p.displayName ?? p.name ?? "Anonim"}</div>
            <div class="time">{ago(p.ts)}</div>
          </div>
        </div>
        {#if p.text}<p class="body">{p.text}</p>{/if}
        {#if p.imgUrl || p.imageURL}<img src={p.imgUrl || p.imageURL} alt="" class="post-img"/>{/if}
        <div class="acts">
          <span>❤️ {p.likesCount ?? 0}</span>
          <span>💬 {p.commentsCount ?? 0}</span>
          <span>🔁 {p.repostsCount ?? 0}</span>
        </div>
      </article>
    {/each}
  {/if}
</main>

<style>
.feed { max-width:600px; margin:0 auto; }
.empty { text-align:center; padding:40px; color:var(--muted); }
.card { padding:14px 16px; border-bottom:1px solid var(--divider); }
.head { display:flex; gap:10px; margin-bottom:10px; align-items:center; }
.av,.av-ph { width:40px; height:40px; border-radius:50%; object-fit:cover; flex-shrink:0; }
.av-ph { background:var(--primary); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:700; }
.name { font-weight:600; font-size:15px; color:var(--on-bg); }
.time { font-size:13px; color:var(--muted); }
.body { font-size:15px; color:var(--on-bg); line-height:1.6; white-space:pre-wrap; margin-bottom:10px; }
.post-img { width:100%; border-radius:12px; margin-bottom:10px; max-height:400px; object-fit:cover; }
.acts { display:flex; gap:20px; font-size:14px; color:var(--muted); }
.skeleton { display:flex; gap:10px; padding:14px 16px; border-bottom:1px solid var(--divider); }
.sk-av { width:40px; height:40px; border-radius:50%; background:var(--shimmer); flex-shrink:0; }
.sk-lines { flex:1; display:flex; flex-direction:column; gap:8px; padding-top:4px; }
.sk-l { height:14px; background:var(--shimmer); border-radius:6px; width:100%; }
.w80 { width:80%; }
</style>
