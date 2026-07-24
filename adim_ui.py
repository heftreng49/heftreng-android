import os
BASE = "/data/data/com.termux/files/home/heftreng-android/web/src"
def w(path, content):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    open(full, "w").write(content)
    print(f"✓ {path}")

# app.html — font ekle
open("/data/data/com.termux/files/home/heftreng-android/web/src/../app.html","w").write("""<!doctype html>
<html lang="tr">
\t<head>
\t\t<meta charset="utf-8" />
\t\t<meta name="viewport" content="width=device-width, initial-scale=1" />
\t\t<link rel="preconnect" href="https://fonts.googleapis.com" />
\t\t<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin />
\t\t<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Playfair+Display:ital,wght@0,600;0,700;1,500&display=swap" rel="stylesheet" />
\t\t%sveltekit.head%
\t</head>
\t<body data-sveltekit-preload-data="hover" data-theme="charcoal-dark">
\t\t<div style="display:contents">%sveltekit.body%</div>
\t</body>
</html>
""")
print("✓ app.html")

# app.css — Android temasıyla birebir
w("../app.css", """\
@import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600;700;800&family=Playfair+Display:ital,wght@0,600;0,700;1,500&display=swap');

*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
html { -webkit-text-size-adjust: 100%; }
body {
  font-family: 'Inter', sans-serif;
  background: var(--bg);
  color: var(--on-bg);
  min-height: 100vh;
  -webkit-font-smoothing: antialiased;
}
a { color: inherit; text-decoration: none; }
button { cursor: pointer; border: none; background: none; font-family: inherit; }
img { display: block; }

/* ── CHARCOAL INK (varsayılan) ── */
[data-theme="charcoal-dark"] {
  --bg: #13131A; --surface: #1A1A22; --surface-var: #22222C;
  --card: #1E1E28; --on-bg: #F4F4FA; --on-surface: #CCCCD8;
  --primary: #6C8EFF; --primary-light: #8FAAFF;
  --amber: #F59E0B;
  --grad-start: #6C8EFF; --grad-end: #38BDF8;
  --muted: #5C5C6E; --divider: #222228;
  --shimmer: #1C1C22; --error: #F87171; --success: #34D399;
}
[data-theme="charcoal-light"] {
  --bg: #F5F5F7; --surface: #FFFFFF; --surface-var: #EEEEF3;
  --card: #FFFFFF; --on-bg: #111114; --on-surface: #3A3A45;
  --primary: #4A6FFF; --primary-light: #6C8EFF;
  --amber: #D97706;
  --grad-start: #4A6FFF; --grad-end: #38BDF8;
  --muted: #8E8E9E; --divider: #E5E5EA;
  --shimmer: #EEEEF3; --error: #F87171; --success: #34D399;
}
/* ── BOOK ── */
[data-theme="book-dark"] {
  --bg: #221A10; --surface: #2C2418; --surface-var: #362C22;
  --card: #30281C; --on-bg: #F8F0DC; --on-surface: #D8C8A8;
  --primary: #D4A853; --primary-light: #E8C57A;
  --amber: #D4A853;
  --grad-start: #D4A853; --grad-end: #E8846A;
  --muted: #6B5D48; --divider: #352C20;
  --shimmer: #2C2418; --error: #F87171; --success: #34D399;
}
[data-theme="book-light"] {
  --bg: #FAF3E8; --surface: #FFF8EE; --surface-var: #F0E8D8;
  --card: #FFF8EE; --on-bg: #2C1F0E; --on-surface: #5C4A30;
  --primary: #8B5E2C; --primary-light: #B07A40;
  --amber: #8B5E2C;
  --grad-start: #8B5E2C; --grad-end: #D4853A;
  --muted: #9E8A70; --divider: #DFD0BC;
  --shimmer: #F0E8D8; --error: #F87171; --success: #34D399;
}
/* ── FOREST ── */
[data-theme="forest-dark"] {
  --bg: #111C15; --surface: #18261C; --surface-var: #1F3023;
  --card: #1C2A20; --on-bg: #E8F5EC; --on-surface: #AAC8AF;
  --primary: #4CAF6F; --primary-light: #6FCB8A;
  --amber: #F59E0B;
  --grad-start: #4CAF6F; --grad-end: #88C96A;
  --muted: #4A6650; --divider: #1E2E20;
  --shimmer: #162019; --error: #F87171; --success: #34D399;
}
[data-theme="forest-light"] {
  --bg: #F2F9F2; --surface: #FFFFFF; --surface-var: #E8F4E8;
  --card: #FFFFFF; --on-bg: #0D2010; --on-surface: #2E5030;
  --primary: #2E7D32; --primary-light: #4CAF50;
  --amber: #F59E0B;
  --grad-start: #2E7D32; --grad-end: #66BB6A;
  --muted: #7C9E7E; --divider: #D0E8D0;
  --shimmer: #E8F4E8; --error: #F87171; --success: #34D399;
}
/* ── OCEAN ── */
[data-theme="ocean-dark"] {
  --bg: #0C1828; --surface: #122030; --surface-var: #192B3E;
  --card: #162436; --on-bg: #EAF4FC; --on-surface: #A0CCDF;
  --primary: #00B4D8; --primary-light: #48CAE4;
  --amber: #F59E0B;
  --grad-start: #0077B6; --grad-end: #00B4D8;
  --muted: #3A6080; --divider: #112030;
  --shimmer: #0C1A2A; --error: #F87171; --success: #34D399;
}
[data-theme="ocean-light"] {
  --bg: #EDF6FB; --surface: #FFFFFF; --surface-var: #DBF0F7;
  --card: #FFFFFF; --on-bg: #0A2035; --on-surface: #1A4A6A;
  --primary: #0077B6; --primary-light: #00B4D8;
  --amber: #F59E0B;
  --grad-start: #0077B6; --grad-end: #48CAE4;
  --muted: #6A9AB8; --divider: #BFDEEE;
  --shimmer: #DBF0F7; --error: #F87171; --success: #34D399;
}
/* ── SUNSET ── */
[data-theme="sunset-dark"] {
  --bg: #1C1018; --surface: #261822; --surface-var: #301E2C;
  --card: #2A1C26; --on-bg: #FDF0F4; --on-surface: #E0B0C5;
  --primary: #FF6B8A; --primary-light: #FF8FA8;
  --amber: #F59E0B;
  --grad-start: #FF6B35; --grad-end: #FF6B8A;
  --muted: #7A3D55; --divider: #2E1825;
  --shimmer: #25141E; --error: #F87171; --success: #34D399;
}
[data-theme="sunset-light"] {
  --bg: #FFF5F7; --surface: #FFFFFF; --surface-var: #FFE8EE;
  --card: #FFFFFF; --on-bg: #330818; --on-surface: #6B2040;
  --primary: #E91E63; --primary-light: #FF5C8D;
  --amber: #F59E0B;
  --grad-start: #FF6B35; --grad-end: #E91E63;
  --muted: #B87090; --divider: #FFCCDD;
  --shimmer: #FFE8EE; --error: #F87171; --success: #34D399;
}
/* ── MONO ── */
[data-theme="mono-dark"] {
  --bg: #0F0F0F; --surface: #171717; --surface-var: #202020;
  --card: #1A1A1A; --on-bg: #FFFFFF; --on-surface: #C0C0C0;
  --primary: #FFFFFF; --primary-light: #E0E0E0;
  --amber: #E0E0E0;
  --grad-start: #B0B0B0; --grad-end: #FFFFFF;
  --muted: #505050; --divider: #1E1E1E;
  --shimmer: #141414; --error: #F87171; --success: #34D399;
}
[data-theme="mono-light"] {
  --bg: #FFFFFF; --surface: #FFFFFF; --surface-var: #F0F0F0;
  --card: #FFFFFF; --on-bg: #000000; --on-surface: #404040;
  --primary: #000000; --primary-light: #404040;
  --amber: #404040;
  --grad-start: #404040; --grad-end: #000000;
  --muted: #909090; --divider: #E0E0E0;
  --shimmer: #F0F0F0; --error: #F87171; --success: #34D399;
}
""")

# PostCard — Android'e yakın kart stili
w("lib/components/PostCard.svelte", """\
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
""")

# Navbar — Android'deki gibi
w("lib/components/Navbar.svelte", """\
<script lang="ts">
  import { currentUser } from "$lib/store/auth";
  import Avatar from "./Avatar.svelte";
</script>

<header class="navbar">
  <a href="/feed" class="logo">Heftreng</a>
  <div class="acts">
    <a href="/search" class="icon-btn" title="Ara">
      <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
    </a>
    <a href="/notifications" class="icon-btn" title="Bildirimler">
      <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
    </a>
    <a href="/messages" class="icon-btn" title="Mesajlar">
      <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
    </a>
    {#if $currentUser}
      <a href="/profile/me" class="av-link">
        <div class="av-ring">
          <Avatar src={$currentUser.photoURL ?? ""} name={$currentUser.displayName ?? ""} size={30} />
        </div>
      </a>
    {:else}
      <a href="/login" class="login-btn">Giriş Yap</a>
    {/if}
  </div>
</header>

<style>
.navbar {
  position: sticky; top: 0; z-index: 100;
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px 14px;
  background: var(--surface);
  border-bottom: 0.5px solid var(--divider);
}
.logo {
  font-family: 'Playfair Display', serif;
  font-size: 21px; font-weight: 700;
  color: var(--primary);
  letter-spacing: -0.3px;
}
.acts { display: flex; align-items: center; gap: 2px; }
.icon-btn {
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%; color: var(--on-bg);
  transition: background 0.15s;
}
.icon-btn:hover { background: var(--surface-var); }
.av-ring {
  background: linear-gradient(135deg, var(--grad-start), var(--grad-end));
  border-radius: 50%; padding: 1.5px;
}
.login-btn {
  padding: 6px 14px;
  background: var(--primary); color: #fff;
  border-radius: 20px; font-size: 13px; font-weight: 600;
  margin-left: 4px;
}
</style>
""")

# store/theme.ts — body'ye data-theme yaz
w("lib/store/theme.ts", """\
import { writable } from 'svelte/store';
import { browser } from '$app/environment';

type ThemeVariant = 'charcoal' | 'book' | 'forest' | 'ocean' | 'sunset' | 'mono';
type ThemeMode = 'dark' | 'light' | 'system';

interface Theme { variant: ThemeVariant; mode: ThemeMode; }

const DEFAULT: Theme = { variant: 'charcoal', mode: 'dark' };

function load(): Theme {
  if (!browser) return DEFAULT;
  try { return JSON.parse(localStorage.getItem('heft-theme') || '') as Theme; }
  catch { return DEFAULT; }
}

export const theme = writable<Theme>(load());

export function applyTheme(variant: ThemeVariant, mode: ThemeMode) {
  if (!browser) return;
  const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches;
  const dark = mode === 'dark' || (mode === 'system' && prefersDark);
  document.body.setAttribute('data-theme', `${variant}-${dark ? 'dark' : 'light'}`);
  localStorage.setItem('heft-theme', JSON.stringify({ variant, mode }));
}

theme.subscribe(({ variant, mode }) => applyTheme(variant, mode));
""")

print("\n✅ Bitti.")
print("cd ~/heftreng-android/web && git add -A && git commit -m 'ui: android-like card style, full theme system' && git push origin main")
