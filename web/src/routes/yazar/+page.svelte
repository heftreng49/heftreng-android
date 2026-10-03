<script lang="ts">
  // Android YazarScreen karşılığı — Blog yazısı oluştur / yönet
  import { onMount }     from 'svelte';
  import { goto }        from '$app/navigation';
  import { currentUser } from '$lib/stores/auth';
  import { requireAuth } from '$lib/utils/auth.guard';
  import { supabase }    from '$lib/supabase/config';
  import { db }          from '$lib/firebase/config';
  import {
    collection, addDoc, updateDoc, deleteDoc,
    doc, query, where, orderBy, getDocs, Timestamp,
  } from 'firebase/firestore';
  import PageTopBar  from '$lib/components/PageTopBar.svelte';
  import EmptyState  from '$lib/components/EmptyState.svelte';
  import Skeleton    from '$lib/components/Skeleton.svelte';
  import { draftSave, draftLoad, draftClear } from '$lib/utils/draft';

  let activeTab    = $state(0); // 0=Yaz  1=Yazılarım
  let myPosts      = $state<any[]>([]);
  let loading      = $state(false);
  let submitting   = $state(false);
  let submitResult = $state('');
  let editingPost  = $state<any | null>(null);

  // Form alanları
  let title    = $state('');
  let body     = $state('');
  let category = $state('genel');
  let imageUrl = $state('');

  const categories = ['genel','edebiyat','kültür','tarih','dil','bilim','sanat','diğer'];

  onMount(async () => {
    const ok = await requireAuth();
    if (!ok) return;

    // Taslak yükle
    const draft = draftLoad();
    if (draft && !title && !body) {
      title = draft.title;
      body  = draft.text;
    }

    await loadMyPosts();
  });

  // Taslak otomatik kaydet
  $effect(() => {
    if (body || title) draftSave(body, title);
  });

  async function loadMyPosts() {
    if (!$currentUser) return;
    loading = true;
    try {
      const q = query(
        collection(db, 'blog'),
        where('uid', '==', $currentUser.uid),
        orderBy('ts', 'desc'),
      );
      const snap = await getDocs(q);
      myPosts = snap.docs.map(d => ({ id: d.id, ...d.data() }));
    } catch(e) { console.error(e); }
    finally { loading = false; }
  }

  async function submit() {
    if (!$currentUser || !title.trim() || !body.trim()) return;
    submitting = true; submitResult = '';
    try {
      if (editingPost) {
        // Güncelle
        await updateDoc(doc(db, 'blog', editingPost.id), {
          title: title.trim(), body: body.trim(),
          category, imageUrl: imageUrl.trim(),
          updatedAt: Timestamp.now(),
        });
        await supabase.from('blog').update({
          title: title.trim(), body: body.trim(), category,
          image_url: imageUrl.trim(),
        }).eq('id', editingPost.id);
        submitResult = '✓ Yazı güncellendi';
      } else {
        // Yeni yazı
        const ref = await addDoc(collection(db, 'blog'), {
          uid:         $currentUser.uid,
          displayName: $currentUser.displayName ?? '',
          photoURL:    $currentUser.photoURL    ?? '',
          title:       title.trim(),
          body:        body.trim(),
          category,
          imageUrl:    imageUrl.trim(),
          ts:          Timestamp.now(),
          likesCount:  0,
          commentsCount: 0,
          status:      'published',
        });
        await supabase.from('blog').upsert({
          id:           ref.id,
          uid:          $currentUser.uid,
          title:        title.trim(),
          body:         body.trim(),
          category,
          image_url:    imageUrl.trim(),
          status:       'published',
        });
        submitResult = '✓ Yazın yayınlandı!';
      }
      draftClear();
      title = ''; body = ''; imageUrl = ''; editingPost = null;
      await loadMyPosts();
      activeTab = 1;
    } catch(e: any) {
      submitResult = '✗ ' + (e.message ?? 'Hata oluştu');
    } finally { submitting = false; }
  }

  function startEdit(p: any) {
    editingPost = p;
    title    = p.title ?? '';
    body     = p.body  ?? '';
    category = p.category ?? 'genel';
    imageUrl = p.imageUrl ?? '';
    activeTab = 0;
    window.scrollTo(0, 0);
  }

  async function deletePost(id: string) {
    if (!confirm('Bu yazıyı silmek istediğine emin misin?')) return;
    await deleteDoc(doc(db, 'blog', id));
    await supabase.from('blog').delete().eq('id', id);
    myPosts = myPosts.filter(p => p.id !== id);
  }
</script>

<PageTopBar title="Yazar Paneli" onBack={() => goto('/')} />

<div class="page">
  <!-- Tab -->
  <div class="tabs">
    <button class="tab" class:active={activeTab===0} onclick={() => activeTab=0}>✍️ Yaz</button>
    <button class="tab" class:active={activeTab===1} onclick={() => activeTab=1}>📋 Yazılarım ({myPosts.length})</button>
  </div>

  <!-- YAZ sekmesi -->
  {#if activeTab === 0}
    <div class="form">
      {#if editingPost}
        <div class="edit-banner">
          ✏️ Düzenleniyor: <strong>{editingPost.title}</strong>
          <button onclick={() => { editingPost=null; title=''; body=''; imageUrl=''; }}>✕ İptal</button>
        </div>
      {/if}

      <label class="field-label">Başlık *</label>
      <input class="field-input" bind:value={title} placeholder="Yazının başlığı…" maxlength={120} />

      <label class="field-label">Kategori</label>
      <div class="chip-row">
        {#each categories as c}
          <button class="chip" class:selected={category===c} onclick={() => category=c}>{c}</button>
        {/each}
      </div>

      <label class="field-label">Kapak görseli URL (opsiyonel)</label>
      <input class="field-input" bind:value={imageUrl} placeholder="https://…" />

      <label class="field-label">İçerik *</label>
      <textarea class="field-body" bind:value={body} placeholder="Yazını buraya yaz…" rows={12}></textarea>

      {#if submitResult}
        <p class="result" class:err={submitResult.startsWith('✗')}>{submitResult}</p>
      {/if}

      <button
        class="submit-btn"
        onclick={submit}
        disabled={submitting || !title.trim() || !body.trim()}
      >
        {submitting ? 'Yayınlanıyor…' : editingPost ? '💾 Güncelle' : '🚀 Yayınla'}
      </button>
    </div>
  {/if}

  <!-- YAZILARIM sekmesi -->
  {#if activeTab === 1}
    {#if loading}
      {#each Array(3) as _}<Skeleton height={80} />{/each}
    {:else if myPosts.length === 0}
      <EmptyState icon="✍️" title="Henüz yazı yok" subtitle="Yaz sekmesinden ilk yazını oluştur." />
    {:else}
      {#each myPosts as p (p.id)}
        <div class="post-row">
          {#if p.imageUrl}
            <img src={p.imageUrl} alt="kapak" class="post-cover" />
          {/if}
          <div class="post-meta">
            <span class="post-cat">{p.category ?? 'genel'}</span>
            <h3 class="post-title">{p.title}</h3>
            <p class="post-preview">{(p.body ?? '').slice(0, 100)}…</p>
          </div>
          <div class="post-actions">
            <button class="action-edit"   onclick={() => startEdit(p)}>✏️</button>
            <button class="action-view"   onclick={() => goto('/blog/' + p.id)}>👁️</button>
            <button class="action-delete" onclick={() => deletePost(p.id)}>🗑️</button>
          </div>
        </div>
      {/each}
    {/if}
  {/if}
</div>

<style>
  .page { max-width: 640px; margin: 0 auto; padding: 12px; }

  .tabs { display: flex; gap: 8px; margin-bottom: 16px; }
  .tab {
    flex: 1; padding: 10px; border-radius: 10px; border: none;
    background: var(--surface-var); color: var(--muted);
    font-size: 14px; font-weight: 600; cursor: pointer; font-family: inherit;
    transition: background .15s, color .15s;
  }
  .tab.active { background: var(--primary); color: #fff; }

  .form { display: flex; flex-direction: column; gap: 6px; }
  .edit-banner {
    display: flex; align-items: center; gap: 8px; justify-content: space-between;
    background: color-mix(in srgb, var(--primary) 10%, transparent);
    border-radius: 10px; padding: 10px 12px; font-size: 13px; color: var(--primary);
  }
  .edit-banner button { background: none; border: none; cursor: pointer; color: var(--muted); }
  .field-label { font-size: 12px; font-weight: 700; color: var(--muted); margin-top: 8px; }
  .field-input {
    width: 100%; padding: 10px 12px; border: 1.5px solid var(--divider);
    border-radius: 10px; font-size: 14px; background: var(--surface-var);
    color: var(--on-bg); font-family: inherit; outline: none; box-sizing: border-box;
  }
  .field-input:focus { border-color: var(--primary); }
  .chip-row { display: flex; flex-wrap: wrap; gap: 6px; }
  .chip {
    padding: 5px 12px; border-radius: 20px; border: 1.5px solid var(--divider);
    background: none; font-size: 12px; cursor: pointer; font-family: inherit; color: var(--on-bg);
  }
  .chip.selected { border-color: var(--primary); background: color-mix(in srgb, var(--primary) 12%, transparent); color: var(--primary); font-weight: 700; }
  .field-body {
    width: 100%; padding: 10px 12px; border: 1.5px solid var(--divider);
    border-radius: 10px; font-size: 14px; background: var(--surface-var);
    color: var(--on-bg); font-family: inherit; resize: vertical; outline: none; box-sizing: border-box;
  }
  .field-body:focus { border-color: var(--primary); }
  .result { font-size: 13px; font-weight: 600; margin: 4px 0; }
  .result.err { color: #ef4444; }
  .submit-btn {
    padding: 12px; background: var(--primary); color: #fff; border: none;
    border-radius: 12px; font-size: 15px; font-weight: 700; cursor: pointer;
    font-family: inherit; margin-top: 4px;
  }
  .submit-btn:disabled { opacity: .5; cursor: default; }

  .post-row {
    display: flex; gap: 10px; align-items: flex-start;
    background: var(--surface); border-radius: 12px; padding: 12px;
    margin-bottom: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.06);
  }
  .post-cover { width: 64px; height: 64px; border-radius: 8px; object-fit: cover; flex-shrink: 0; }
  .post-meta { flex: 1; min-width: 0; }
  .post-cat { font-size: 11px; font-weight: 700; color: var(--primary); }
  .post-title { font-size: 14px; font-weight: 700; margin: 2px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .post-preview { font-size: 12px; color: var(--muted); margin: 0; }
  .post-actions { display: flex; flex-direction: column; gap: 4px; }
  .action-edit, .action-view, .action-delete {
    background: none; border: none; font-size: 16px; cursor: pointer; padding: 4px;
    border-radius: 6px;
  }
  .action-edit:hover   { background: color-mix(in srgb, var(--primary) 12%, transparent); }
  .action-delete:hover { background: color-mix(in srgb, #ef4444 12%, transparent); }
</style>
