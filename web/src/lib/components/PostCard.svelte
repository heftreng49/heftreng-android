<script lang="ts">
  import Avatar from "./Avatar.svelte";
  import { timeAgo } from "$lib/utils/time";
  export let post: any;
</script>

<article class="card">
  <!-- Header -->
  <a href="/profile/{post.uid}" class="head">
    <div class="av-wrap">
      <Avatar src={post.photoURL} name={post.displayName} size={44} />
    </div>
    <div class="meta">
      <span class="name">{post.displayName || "Anonim"}</span>
      <span class="sub">
        {#if post.username}<span>@{post.username}</span> · {/if}{timeAgo(post.ts)}
      </span>
    </div>
  </a>

  <!-- Body -->
  <a href="/post/{post.id}" class="body-link">

    {#if post.quoteText}
      <div class="quote-card">
        {#if post.coverImg}<img src={post.coverImg} alt="" class="quote-cover" />{/if}
        <div>
          <p class="quote-text">"{post.quoteText}"</p>
          <p class="quote-meta">
            {#if post.bookName}<span class="qbook">📖 {post.bookName}</span>{/if}
            {#if post.authorName} — {post.authorName}{/if}
          </p>
        </div>
      </div>
    {/if}

    {#if post.category}
      <span class="cat">{post.category}</span>
    {/if}

    {#if post.title}
      <p class="post-title">{post.title}</p>
    {/if}

    {#if post.text}
      <p class="post-text">{post.text}</p>
    {/if}

    {#if post.imgUrl || post.imageURL}
      <img src={post.imgUrl || post.imageURL} alt="" class="post-img" />
    {/if}

    {#if post.repostType && post.repostType !== "kf_achievement"}
      <div class="rp-card">
        {#if post.repostType === "feed" && post.repostAuthor}
          <div class="rp-head">
            <Avatar src={post.repostAuthorPhoto} name={post.repostAuthor} size={18} />
            <span class="rp-name">{post.repostAuthor}</span>
          </div>
        {/if}
        {#if post.repostTitle}<p class="rp-title">{post.repostTitle}</p>{/if}
        {#if post.repostText}<p class="rp-text">{post.repostText}</p>{/if}
        {#if post.repostImg || post.serialCover}
          <img src={post.repostImg || post.serialCover} alt="" class="rp-img" />
        {/if}
        {#if post.serialTitle}
          <p class="rp-serial">📖 {post.serialTitle}{post.chapterTitle ? " · " + post.chapterTitle : ""}</p>
        {/if}
      </div>
    {/if}

    {#if post.repostType === "kf_achievement"}
      <div class="ach">
        <span class="ach-icon">🏆</span>
        <div>
          <p class="ach-title">Başarı</p>
          {#if post.repostLevel}<p class="ach-sub">Seviye {post.repostLevel} · {post.repostXp} XP</p>{/if}
        </div>
      </div>
    {/if}

  </a>

  <!-- Actions -->
  <div class="acts">
    <button class="act"><span class="act-icon">❤️</span> {post.likesCount ?? 0}</button>
    <button class="act"><span class="act-icon">💬</span> {post.commentsCount ?? 0}</button>
    <button class="act"><span class="act-icon">🔁</span> {post.repostsCount ?? 0}</button>
  </div>
</article>

<style>
.card {
  margin: 6px 10px;
  background: var(--card);
  border-radius: 18px;
  border: 0.7px solid var(--divider);
  padding: 15px;
  overflow: hidden;
}
.head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  text-decoration: none;
}
.av-wrap {
  background: linear-gradient(135deg, var(--grad-start), var(--grad-end));
  border-radius: 50%;
  padding: 1.5px;
  flex-shrink: 0;
}
.meta { display: flex; flex-direction: column; gap: 1px; min-width: 0; }
.name { font-weight: 700; font-size: 14px; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sub  { font-size: 12px; color: var(--muted); }

.body-link { display: block; text-decoration: none; }

.cat {
  display: inline-block;
  background: color-mix(in srgb, var(--primary) 14%, transparent);
  color: var(--primary);
  font-size: 11px; font-weight: 600;
  padding: 2px 9px; border-radius: 99px;
  margin-bottom: 5px;
}
.post-title { font-size: 16px; font-weight: 700; color: var(--on-bg); line-height: 1.35; margin-bottom: 5px; }
.post-text  { font-size: 15px; color: var(--on-bg); line-height: 1.65; white-space: pre-wrap; margin-bottom: 8px; }
.post-img   { width: 100%; border-radius: 12px; margin-top: 8px; max-height: 400px; object-fit: cover; }

/* Quote */
.quote-card {
  display: flex; gap: 10px;
  background: var(--surface-var);
  border-left: 3px solid var(--primary);
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 8px;
}
.quote-cover { width: 46px; height: 64px; object-fit: cover; border-radius: 6px; flex-shrink: 0; }
.quote-text  { font-style: italic; font-size: 14px; color: var(--on-bg); line-height: 1.5; margin-bottom: 4px; }
.quote-meta  { font-size: 12px; color: var(--muted); }
.qbook { color: var(--primary); }

/* Repost */
.rp-card {
  background: var(--surface-var);
  border: 1px solid var(--divider);
  border-radius: 12px;
  padding: 10px 12px;
  margin-top: 6px;
}
.rp-head  { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
.rp-name  { font-size: 12px; color: var(--muted); font-weight: 600; }
.rp-title { font-size: 13px; font-weight: 700; color: var(--on-bg); margin-bottom: 3px; }
.rp-text  { font-size: 13px; color: var(--on-surface); line-height: 1.5; }
.rp-img   { width: 100%; border-radius: 8px; margin-top: 6px; max-height: 180px; object-fit: cover; }
.rp-serial { font-size: 12px; color: var(--primary); margin-top: 4px; }

/* Achievement */
.ach {
  display: flex; align-items: center; gap: 12px;
  background: color-mix(in srgb, var(--primary) 10%, transparent);
  border: 1px solid color-mix(in srgb, var(--primary) 25%, transparent);
  border-radius: 12px; padding: 10px 14px; margin-top: 6px;
}
.ach-icon  { font-size: 26px; }
.ach-title { font-weight: 700; font-size: 14px; color: var(--on-bg); }
.ach-sub   { font-size: 12px; color: var(--muted); }

/* Actions */
.acts { display: flex; gap: 4px; margin-top: 12px; }
.act {
  display: flex; align-items: center; gap: 5px;
  padding: 6px 12px; border-radius: 99px;
  font-size: 13px; color: var(--muted);
  font-family: inherit;
  transition: background 0.15s;
}
.act:hover { background: var(--surface-var); }
.act-icon { font-size: 15px; }
</style>
