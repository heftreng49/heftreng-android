<script lang="ts">
  // Android yorum bottom sheet karşılığı
  // Yeni: yorum beğeni, yorum düzenleme, mention autocomplete
  import { onMount, tick } from 'svelte';
  import Avatar from './Avatar.svelte';
  import { ago } from '$lib/models/util';
  import { fetchComments, sendComment, deleteComment } from '$lib/services/comment.service';
  import { toggleCommentLike } from '$lib/services/post.service';
  import { searchMentionUsers, getActiveMentionQuery, extractMentions, type MentionUser } from '$lib/services/mention.service';
  import { supabase } from '$lib/supabase/config';
  import type { Comment } from '$lib/models/comment';
  import type { User as FirebaseUser } from 'firebase/auth';

  interface Props {
    postId:         string;
    currentUser:    FirebaseUser | null;
    onClose?:       () => void;
    onCountChange?: (count: number) => void;
  }
  let { postId, currentUser, onClose, onCountChange }: Props = $props();

  let comments  : Comment[] = $state([]);
  let loading    = $state(true);
  let text       = $state('');
  let sending    = $state(false);
  let replyTo    : Comment | null = $state(null);

  // Düzenleme
  let editingId   : string | null = $state(null);
  let editText    = $state('');
  let editSaving  = $state(false);

  // Mention autocomplete
  let mentionQuery    = $state<string | null>(null);
  let mentionResults  = $state<MentionUser[]>([]);
  let mentionLoading  = $state(false);
  let inputEl         = $state<HTMLInputElement | null>(null);
  let cursorPos       = $state(0);

  onMount(async () => { await load(); });

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
      const mentions = extractMentions(text);
      const c = await sendComment({
        post_id:         postId,
        uid:             currentUser.uid,
        name:            currentUser.displayName ?? '',
        photo_url:       currentUser.photoURL    ?? '',
        text:            text.trim(),
        reply_to_cmt_id: replyTo?.id ?? undefined,
        mentions,
      });
      comments = [...comments, c];
      onCountChange?.(comments.length);
      text = ''; replyTo = null; mentionQuery = null; mentionResults = [];
    } catch(e) { console.error(e); }
    finally { sending = false; }
  }

  // ── Mention autocomplete ─────────────────────────────────────────────────
  async function onInput(e: Event) {
    const el = e.target as HTMLInputElement;
    cursorPos = el.selectionStart ?? text.length;
    const q = getActiveMentionQuery(text, cursorPos);
    if (q === null) { mentionQuery = null; mentionResults = []; return; }
    mentionQuery = q;
    if (q.length < 1) { mentionResults = []; return; }
    mentionLoading = true;
    try { mentionResults = await searchMentionUsers(q); }
    finally { mentionLoading = false; }
  }

  function insertMention(user: MentionUser) {
    if (!inputEl) return;
    const before   = text.slice(0, cursorPos);
    const after    = text.slice(cursorPos);
    // @sorguyu sil, mention ekle
    const replaced = before.replace(/@[a-z0-9_]*$/i, `@${user.username} `);
    text = replaced + after;
    mentionQuery = null; mentionResults = [];
    tick().then(() => inputEl?.focus());
  }

  // ── Yorum beğeni ────────────────────────────────────────────────────────
  async function handleCommentLike(c: Comment) {
    if (!currentUser) return;
    const wasLiked = c.isLikedByMe ?? false;
    // Optimistic
    comments = comments.map(x =>
      x.id === c.id ? {
        ...x,
        isLikedByMe: !wasLiked,
        likesCount: Math.max(0, (x.likesCount ?? 0) + (wasLiked ? -1 : 1)),
      } : x
    );
    try {
      await toggleCommentLike(
        c.id, currentUser.uid,
        currentUser.displayName ?? '', currentUser.photoURL ?? '',
        wasLiked,
      );
      // Supabase'deki likes_count'u güncelle
      await supabase.from('feed_comments')
        .update({ likes_count: Math.max(0, (c.likesCount ?? 0) + (wasLiked ? -1 : 1)) })
        .eq('id', c.id);
    } catch(e) {
      // Rollback
      comments = comments.map(x =>
        x.id === c.id ? { ...x, isLikedByMe: wasLiked, likesCount: Math.max(0, (x.likesCount ?? 0) + (wasLiked ? 1 : -1)) } : x
      );
    }
  }

  // ── Yorum düzenleme ──────────────────────────────────────────────────────
  function startEdit(c: Comment) {
    editingId = c.id;
    editText  = c.text;
  }

  async function saveEdit(c: Comment) {
    if (!editText.trim()) return;
    editSaving = true;
    try {
      const { data, error } = await supabase.from('feed_comments')
        .update({ text: editText.trim() })
        .eq('id', c.id)
        .eq('uid', currentUser?.uid ?? '')
        .select().single();
      if (error) throw error;
      comments = comments.map(x => x.id === c.id ? { ...x, text: editText.trim() } : x);
      editingId = null;
    } catch(e) { console.error(e); }
    finally { editSaving = false; }
  }
</script>

<!-- Panel arka planı -->
<!-- svelte-ignore a11y_click_events_have_key_events -->
<div class="overlay" onclick={() => onClose?.()} role="button" tabindex="-1" aria-label="Kapat"></div>

<div class="panel">
  <div class="panel-handle"></div>
  <div class="panel-header">
    <span class="panel-title">Yorumlar ({comments.length})</span>
    <button class="close-btn" onclick={() => onClose?.()}>✕</button>
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

            <!-- Düzenleme modu -->
            {#if editingId === c.id}
              <div class="edit-row">
                <input
                  class="edit-input"
                  bind:value={editText}
                  onkeydown={(e) => {
                    if (e.key === 'Enter') saveEdit(c);
                    if (e.key === 'Escape') editingId = null;
                  }}
                />
                <button class="edit-save" onclick={() => saveEdit(c)} disabled={editSaving || !editText.trim()}>
                  {editSaving ? '…' : '✓'}
                </button>
                <button class="edit-cancel" onclick={() => editingId = null}>✕</button>
              </div>
            {:else}
              <p class="comment-text">{c.text}</p>
            {/if}

            <!-- Aksiyonlar -->
            <div class="comment-actions">
              <button class="cmt-action" onclick={() => replyTo = c}>Yanıtla</button>

              <!-- Beğeni -->
              <button
                class="cmt-action like-btn"
                class:liked={c.isLikedByMe}
                onclick={() => handleCommentLike(c)}
                disabled={!currentUser}
              >
                {#if c.isLikedByMe}
                  ❤️
                {:else}
                  🤍
                {/if}
                {#if (c.likesCount ?? 0) > 0}
                  <span class="like-count">{c.likesCount}</span>
                {/if}
              </button>

              {#if currentUser?.uid === c.uid}
                <button class="cmt-action" onclick={() => startEdit(c)}>Düzenle</button>
                <button class="cmt-action danger" onclick={async () => {
                  await deleteComment(c.id, c.uid, postId);
                  comments = comments.filter(x => x.id !== c.id);
                  onCountChange?.(comments.length);
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

    <!-- Mention önerileri -->
    {#if mentionResults.length > 0}
      <div class="mention-list">
        {#each mentionResults as u (u.uid)}
          <button class="mention-item" onclick={() => insertMention(u)}>
            <Avatar src={u.photoURL} name={u.name} size={24} />
            <span class="mention-name">{u.name}</span>
            <span class="mention-handle">@{u.username}</span>
          </button>
        {/each}
      </div>
    {/if}

    <div class="input-row">
      {#if currentUser}
        <Avatar src={currentUser.photoURL ?? ''} name={currentUser.displayName ?? ''} size={32} />
        <input
          bind:this={inputEl}
          class="cmt-input"
          placeholder="Yorum yaz… (@kullanıcı etiketleyebilirsin)"
          bind:value={text}
          oninput={onInput}
          onkeydown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(); }
            if (e.key === 'Escape') { mentionQuery = null; mentionResults = []; }
          }}
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
  .overlay { position: fixed; inset: 0; background: rgba(0,0,0,.35); z-index: 100; }
  .panel {
    position: fixed; bottom: 0; left: 0; right: 0; z-index: 101;
    background: var(--surface, #fff); border-radius: 20px 20px 0 0;
    display: flex; flex-direction: column;
    max-height: 80dvh; box-shadow: 0 -4px 24px rgba(0,0,0,.12);
  }
  .panel-handle { width: 36px; height: 4px; background: #ddd; border-radius: 2px; margin: 10px auto 0; }
  .panel-header { display: flex; align-items: center; justify-content: space-between; padding: 10px 16px 8px; }
  .panel-title { font-weight: 700; font-size: 15px; }
  .close-btn { background: none; border: none; cursor: pointer; font-size: 18px; color: #999; }

  .panel-body { flex: 1; overflow-y: auto; padding: 0 16px 8px; }
  .loader, .empty { text-align: center; padding: 20px; color: #999; font-size: 14px; }

  .comment-row { display: flex; gap: 8px; margin-bottom: 12px; }
  .comment-bubble { flex: 1; background: color-mix(in srgb, var(--primary, #7c4dff) 6%, transparent); border-radius: 12px; padding: 8px 12px; }
  .comment-head { display: flex; align-items: center; gap: 8px; margin-bottom: 2px; }
  .comment-name { font-size: 13px; font-weight: 700; }
  .comment-time { font-size: 11px; color: #999; }
  .reply-ref { font-size: 11px; color: var(--primary, #7c4dff); margin-bottom: 2px; }
  .comment-text { font-size: 13px; margin: 0; line-height: 1.5; }
  .comment-actions { display: flex; align-items: center; gap: 10px; margin-top: 6px; flex-wrap: wrap; }
  .cmt-action { background: none; border: none; font-size: 11px; color: #888; cursor: pointer; padding: 0; }
  .cmt-action.danger { color: #e03; }
  .cmt-action.like-btn { display: flex; align-items: center; gap: 3px; font-size: 13px; transition: transform .1s; }
  .cmt-action.like-btn:active { transform: scale(1.2); }
  .cmt-action.like-btn.liked { color: #e03; }
  .like-count { font-size: 11px; color: #888; }

  /* Yorum düzenleme */
  .edit-row { display: flex; align-items: center; gap: 6px; margin: 4px 0; }
  .edit-input {
    flex: 1; border: 1px solid var(--primary, #7c4dff); border-radius: 8px;
    padding: 5px 8px; font-size: 13px; font-family: inherit; outline: none;
    background: var(--surface, #fff); color: var(--on-bg, #000);
  }
  .edit-save {
    width: 28px; height: 28px; border-radius: 50%; background: var(--primary, #7c4dff);
    color: #fff; border: none; cursor: pointer; font-size: 14px;
  }
  .edit-save:disabled { opacity: .5; cursor: default; }
  .edit-cancel { background: none; border: none; color: #999; cursor: pointer; font-size: 16px; padding: 0 4px; }

  /* Mention önerileri */
  .mention-list {
    border: 1px solid var(--divider, #eee); border-radius: 10px;
    overflow: hidden; margin-bottom: 6px;
    background: var(--surface, #fff);
    max-height: 160px; overflow-y: auto;
  }
  .mention-item {
    display: flex; align-items: center; gap: 8px; width: 100%;
    background: none; border: none; border-bottom: 1px solid var(--divider, #eee);
    padding: 8px 12px; cursor: pointer; text-align: left; font-family: inherit;
    transition: background .1s;
  }
  .mention-item:last-child { border-bottom: none; }
  .mention-item:hover { background: color-mix(in srgb, var(--primary, #7c4dff) 6%, transparent); }
  .mention-name { font-size: 13px; font-weight: 600; color: var(--on-bg, #000); }
  .mention-handle { font-size: 12px; color: #888; margin-left: 2px; }

  .panel-input { border-top: 1px solid #f0ebf9; padding: 10px 12px; }
  .reply-banner {
    font-size: 12px; color: var(--primary, #6b4fa0); margin-bottom: 6px;
    display: flex; align-items: center; gap: 4px;
  }
  .reply-banner button { background: none; border: none; cursor: pointer; color: #999; }
  .input-row { display: flex; align-items: center; gap: 8px; }
  .cmt-input {
    flex: 1; border: 1px solid #e0d7f0; border-radius: 20px;
    padding: 8px 14px; font-size: 13px; outline: none;
    background: var(--surface-var, #f7f4ff); color: var(--on-bg, #000);
    font-family: inherit;
  }
  .cmt-input:focus { border-color: var(--primary, #7c4dff); }
  .send-btn {
    width: 36px; height: 36px; border-radius: 50%;
    background: var(--primary, #7c4dff); color: #fff; border: none;
    font-size: 16px; cursor: pointer; flex-shrink: 0;
  }
  .send-btn:disabled { opacity: .5; cursor: default; }
  .login-prompt { font-size: 13px; color: var(--primary, #7c4dff); text-decoration: underline; }
</style>
