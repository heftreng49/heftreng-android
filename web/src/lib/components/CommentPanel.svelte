<script lang="ts">
  // Android yorum bottom sheet karşılığı
  import { createEventDispatcher, onMount } from 'svelte';
  import Avatar from './Avatar.svelte';
  import { ago } from '$lib/models/util';
  import { fetchComments, sendComment, deleteComment } from '$lib/services/comment.service';
  import type { Comment } from '$lib/models/comment';
  import type { User as FirebaseUser } from 'firebase/auth';

  interface Props {
    postId:      string;
    currentUser: FirebaseUser | null;
  }
  let { postId, currentUser }: Props = $props();

  const dispatch = createEventDispatcher<{ close: void; countchange: number }>();

  let comments : Comment[] = $state([]);
  let loading  = $state(true);
  let text     = $state('');
  let sending  = $state(false);
  let replyTo  : Comment | null = $state(null);

  onMount(async () => {
    await load();
  });

  async function load() {
    loading = true;
    try { comments = await fetchComments(postId); }
    catch(e) { console.error(e); }
    finally { loading = false; }
  }

  async function submit() {
    if (!currentUser || !text.trim()) return;
    sending = true;
    try {
      const c = await sendComment({
        post_id:          postId,
        uid:              currentUser.uid,
        name:             currentUser.displayName ?? '',
        photo_url:        currentUser.photoURL    ?? '',
        text:             text.trim(),
        reply_to_cmt_id:  replyTo?.id ?? null ?? undefined,
      });
      comments = [...comments, c];
      dispatch('countchange', comments.length);
      text    = '';
      replyTo = null;
    } catch(e) { console.error(e); }
    finally { sending = false; }
  }
</script>

<!-- Panel arka planı -->
<!-- svelte-ignore a11y_click_events_have_key_events -->
<div class="overlay" onclick={() => dispatch('close')} role="button" tabindex="-1" aria-label="Kapat"></div>

<div class="panel">
  <div class="panel-handle"></div>
  <div class="panel-header">
    <span class="panel-title">Yorumlar ({comments.length})</span>
    <button class="close-btn" onclick={() => dispatch('close')}>✕</button>
  </div>

  <div class="panel-body">
    {#if loading}
      <div class="loader">Yükleniyor…</div>
    {:else if comments.length === 0}
      <div class="empty">Henüz yorum yok. İlk yorumu sen yaz!</div>
    {:else}
      {#each comments as c (c.id)}
        <div class="comment-row">
          <Avatar src={c.photoURL} name={c.displayName} size={34} />
          <div class="comment-bubble">
            <div class="comment-head">
              <span class="comment-name">{c.displayName}</span>
              <span class="comment-time">{ago(c.ts)}</span>
            </div>
            {#if c.replyTo}
              <div class="reply-ref">↩ {c.replyTo.displayName}</div>
            {/if}
            <p class="comment-text">{c.text}</p>
            <div class="comment-actions">
              <button class="cmt-action" onclick={() => replyTo = c}>Yanıtla</button>
              {#if currentUser?.uid === c.uid}
                <button class="cmt-action danger" onclick={async () => {
                  await deleteComment(c.id, c.uid);
                  comments = comments.filter(x => x.id !== c.id);
                  dispatch('countchange', comments.length);
                }}>Sil</button>
              {/if}
            </div>
          </div>
        </div>
      {/each}
    {/if}
  </div>

  <!-- Giriş alanı -->
  <div class="panel-input">
    {#if replyTo}
      <div class="reply-banner">
        ↩ <strong>{replyTo.displayName}</strong>'e yanıt veriliyor
        <button onclick={() => replyTo = null}>✕</button>
      </div>
    {/if}
    <div class="input-row">
      {#if currentUser}
        <Avatar src={currentUser.photoURL ?? ''} name={currentUser.displayName ?? ''} size={32} />
        <input
          class="cmt-input"
          placeholder="Yorum yaz…"
          bind:value={text}
          onkeydown={(e) => e.key === 'Enter' && !e.shiftKey && submit()}
        />
        <button class="send-btn" onclick={submit} disabled={sending || !text.trim()}>
          {sending ? '…' : '↑'}
        </button>
      {:else}
        <a href="/login" class="login-prompt">Yorum yazmak için giriş yap →</a>
      {/if}
    </div>
  </div>
</div>

<style>
  .overlay {
    position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 100;
  }
  .panel {
    position: fixed; bottom: 0; left: 0; right: 0; z-index: 101;
    background: #fff; border-radius: 20px 20px 0 0;
    display: flex; flex-direction: column;
    max-height: 80dvh; box-shadow: 0 -4px 24px rgba(0,0,0,.12);
  }
  .panel-handle {
    width: 36px; height: 4px; background: #ddd; border-radius: 2px;
    margin: 10px auto 0;
  }
  .panel-header {
    display: flex; align-items: center; justify-content: space-between;
    padding: 10px 16px 8px;
  }
  .panel-title { font-weight: 700; font-size: 15px; }
  .close-btn { background: none; border: none; cursor: pointer; font-size: 18px; color: #999; }

  .panel-body { flex: 1; overflow-y: auto; padding: 0 16px 8px; }

  .loader, .empty { text-align: center; padding: 20px; color: #999; font-size: 14px; }

  .comment-row { display: flex; gap: 8px; margin-bottom: 12px; }
  .comment-bubble { flex: 1; background: #f7f4ff; border-radius: 12px; padding: 8px 12px; }
  .comment-head { display: flex; align-items: center; gap: 8px; margin-bottom: 2px; }
  .comment-name { font-size: 13px; font-weight: 700; }
  .comment-time { font-size: 11px; color: #999; }
  .reply-ref { font-size: 11px; color: #7c4dff; margin-bottom: 2px; }
  .comment-text { font-size: 13px; margin: 0; line-height: 1.5; }
  .comment-actions { display: flex; gap: 10px; margin-top: 4px; }
  .cmt-action { background: none; border: none; font-size: 11px; color: #888; cursor: pointer; }
  .cmt-action.danger { color: #e03; }

  .panel-input { border-top: 1px solid #f0ebf9; padding: 10px 12px; }
  .reply-banner {
    font-size: 12px; color: #6b4fa0; margin-bottom: 6px;
    display: flex; align-items: center; gap: 4px;
  }
  .reply-banner button { background: none; border: none; cursor: pointer; color: #999; }
  .input-row { display: flex; align-items: center; gap: 8px; }
  .cmt-input {
    flex: 1; border: 1px solid #e0d7f0; border-radius: 20px;
    padding: 8px 14px; font-size: 13px; outline: none;
  }
  .cmt-input:focus { border-color: #7c4dff; }
  .send-btn {
    width: 36px; height: 36px; border-radius: 50%;
    background: #7c4dff; color: #fff; border: none;
    font-size: 16px; cursor: pointer; flex-shrink: 0;
  }
  .send-btn:disabled { opacity: .5; cursor: default; }
  .login-prompt { font-size: 13px; color: #7c4dff; text-decoration: underline; }
</style>
