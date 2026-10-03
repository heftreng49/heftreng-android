<script lang="ts">
  import { onMount }     from 'svelte';
  import { goto }        from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import { requireAuth } from '$lib/utils/auth.guard';
  import { db }          from '$lib/firebase/config';
  import { supabase }    from '$lib/supabase/config';
  import { doc, getDoc, updateDoc, deleteDoc } from 'firebase/firestore';
  import PostCard    from '$lib/components/PostCard.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import EmptyState  from '$lib/components/EmptyState.svelte';
  import PageTopBar  from '$lib/components/PageTopBar.svelte';

  let posts   = $state<any[]>([]);
  let loading = $state(true);
  let confirmDeleteId = $state<string | null>(null);

  onMount(async () => {
    const ok = await requireAuth();
    if (ok) load();
  });

  async function load() {
    if (!$currentUser) return;
    loading = true;
    try {
      // Supabase feed tablosunda visibility='archived' olan kullanıcı gönderileri
      const { data } = await supabase
        .from('feed')
        .select('post_id')
        .eq('uid', $currentUser.uid)
        .eq('visibility', 'archived')
        .order('created_at', { ascending: false })
        .limit(50);

      const ids = (data ?? []).map((r: any) => r.post_id as string);

      // Firestore'dan detayları çek
      const fetched: any[] = [];
      await Promise.all(ids.map(async (id) => {
        const snap = await getDoc(doc(db, 'feed', id));
        if (snap.exists()) fetched.push({ id: snap.id, ...snap.data() });
      }));
      // ts'ye göre sırala
      posts = fetched.sort((a, b) => (b.ts?.seconds ?? 0) - (a.ts?.seconds ?? 0));
    } catch (e) { console.error(e); }
    finally { loading = false; }
  }

  // Geri yükle — visibility = public
  async function restore(postId: string) {
    try {
      await updateDoc(doc(db, 'feed', postId), { visibility: 'public' });
      await supabase.from('feed').update({ visibility: 'public' }).eq('post_id', postId);
      posts = posts.filter(p => p.id !== postId);
    } catch (e) { console.error(e); }
  }

  // Kalıcı sil
  async function permanentDelete(postId: string) {
    try {
      await deleteDoc(doc(db, 'feed', postId));
      await supabase.from('feed').delete().eq('post_id', postId);
      posts = posts.filter(p => p.id !== postId);
    } catch (e) { console.error(e); }
    finally { confirmDeleteId = null; }
  }
</script>

<PageTopBar title="Arşivlenen Gönderiler" onBack={() => goto('/profile/' + $currentUser?.uid)} />

<div class="page">
  {#if loading}
    {#each Array(5) as _}<Skeleton height={100} />{/each}
  {:else if posts.length === 0}
    <EmptyState icon="📦" title="Arşiv boş" subtitle="Arşivlediğin gönderiler burada görünür." />
  {:else}
    {#each posts as post (post.id)}
      <div class="archived-row">
        <PostCard
          {post}
          currentUid={$currentUser?.uid ?? null}
        />
        <div class="archived-actions">
          <button class="btn-restore" onclick={() => restore(post.id)}>
            ↩ Geri Yükle
          </button>
          <button class="btn-delete" onclick={() => confirmDeleteId = post.id}>
            🗑️ Kalıcı Sil
          </button>
        </div>
      </div>
    {/each}
  {/if}
</div>

<!-- Silme onay dialogu -->
{#if confirmDeleteId}
  <div class="dialog-overlay" role="button" tabindex="-1"
    onkeydown={() => confirmDeleteId = null}
    onclick={() => confirmDeleteId = null}>
  </div>
  <div class="dialog">
    <h3>Kalıcı olarak silinsin mi?</h3>
    <p>Bu işlem geri alınamaz.</p>
    <div class="dialog-actions">
      <button class="btn-cancel" onclick={() => confirmDeleteId = null}>İptal</button>
      <button class="btn-confirm" onclick={() => permanentDelete(confirmDeleteId!)}>Sil</button>
    </div>
  </div>
{/if}

<style>
  .page { max-width: 640px; margin: 0 auto; padding: 12px; }

  .archived-row { margin-bottom: 8px; }
  .archived-actions {
    display: flex; gap: 8px; padding: 0 4px 10px;
  }
  .btn-restore, .btn-delete {
    flex: 1; padding: 8px; border-radius: 10px; border: none;
    font-size: 13px; font-weight: 600; cursor: pointer; font-family: inherit;
  }
  .btn-restore {
    background: color-mix(in srgb, var(--primary) 12%, transparent);
    color: var(--primary);
  }
  .btn-restore:hover { background: color-mix(in srgb, var(--primary) 20%, transparent); }
  .btn-delete {
    background: color-mix(in srgb, #ef4444 10%, transparent);
    color: #ef4444;
  }
  .btn-delete:hover { background: color-mix(in srgb, #ef4444 18%, transparent); }

  .dialog-overlay { position: fixed; inset: 0; background: rgba(0,0,0,.4); z-index: 200; }
  .dialog {
    position: fixed; left: 50%; top: 50%; transform: translate(-50%, -50%);
    z-index: 201; background: var(--surface); border-radius: 16px;
    padding: 24px; width: min(320px, 90vw); box-shadow: 0 8px 32px rgba(0,0,0,.2);
  }
  .dialog h3 { margin: 0 0 6px; font-size: 16px; }
  .dialog p  { margin: 0 0 16px; font-size: 13px; color: var(--muted); }
  .dialog-actions { display: flex; gap: 10px; justify-content: flex-end; }
  .btn-cancel {
    background: none; border: none; padding: 8px 14px; cursor: pointer;
    color: var(--muted); font-size: 14px; font-family: inherit;
  }
  .btn-confirm {
    background: #ef4444; color: #fff; border: none; padding: 8px 18px;
    border-radius: 8px; font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit;
  }
</style>
