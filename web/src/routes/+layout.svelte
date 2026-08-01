<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { initAuthListener, signOut } from '$lib/services/auth.service';
  import { currentUser, authLoading } from '$lib/stores/auth';
  import { theme, applyTheme } from '$lib/store/theme';
  import { listenNotifications } from '$lib/services/notification.service';
  import { listenConversations } from '$lib/services/message.service';
  import { getTheme } from '$lib/services/settings.service';
  import { unreadNotifCount, unreadMsgCount } from '$lib/stores/ui.store';
  import { lang, strings as s } from '$lib/i18n/strings';

  let { children } = $props();

  const hideNavRoutes = ['/login', '/register'];
  let showBottomNav = $derived(!hideNavRoutes.includes($page.url.pathname));
  let currentPath   = $derived($page.url.pathname);

  let drawerOpen = $state(false);

  function isActive(path: string): boolean {
    if (path === '/feed') return currentPath === '/feed' || currentPath === '/';
    if (path === '/profile') return currentPath.startsWith('/profile');
    return currentPath.startsWith(path);
  }

  function closeDrawer() { drawerOpen = false; }

  async function handleSignOut() {
    closeDrawer();
    await signOut();
    window.location.href = '/login';
  }

  function setTheme(mode: string) {
    theme.update(t => ({ ...t, mode }));
    applyTheme($theme.variant, mode);
  }

  let unsubNotifs: (() => void) | null = null;
  let unsubMsgs:   (() => void) | null = null;

  onMount(() => {
    // Tema
    const saved = getTheme();
    applyTheme(saved.variant, saved.mode);
    // Dil — HTML lang attribute'unu ayarla
    const savedLang = localStorage.getItem('hf_lang') ?? 'tr';
    lang.set(savedLang);

    // Sistem modu değişimini canlı dinle
    const mq = window.matchMedia('(prefers-color-scheme: dark)');
    const onSystemChange = () => {
      const t = getTheme();
      if (t.mode === 'system') applyTheme(t.variant, 'system');
    };
    mq.addEventListener('change', onSystemChange);

    const unsubAuth = initAuthListener();

    // currentUser değişince realtime listener'ları yeniden başlat
    const unsubStore = currentUser.subscribe(user => {
      unsubNotifs?.(); unsubNotifs = null;
      unsubMsgs?.();   unsubMsgs   = null;
      if (!user) { unreadNotifCount.set(0); unreadMsgCount.set(0); return; }

      unsubNotifs = listenNotifications(user.uid, (notifs) => {
        unreadNotifCount.set(notifs.filter((n: any) => !n.read).length);
      });
      unsubMsgs = listenConversations(user.uid, (convs) => {
        unreadMsgCount.set(convs.reduce((s: number, cv: any) => s + (cv.unreadCount ?? 0), 0));
      });
    });

    return () => { unsubAuth(); unsubStore(); unsubNotifs?.(); unsubMsgs?.(); mq.removeEventListener('change', onSystemChange); };
  });

  // Drawer menü öğeleri — reaktif dil
  $: navGrid = [
    { icon: 'feed',     label: s.navFeed($lang),      href: '/feed' },
    { icon: 'search',   label: s.navSearch($lang),    href: '/search' },
    { icon: 'library',  label: s.navLibrary($lang),   href: '/library' },
    { icon: 'kurdi',    label: s.navKurdi($lang),     href: '/kurdi' },
    { icon: 'notif',    label: s.navNotifs($lang),    href: '/notifications' },
    { icon: 'message',  label: s.navMessages($lang),  href: '/messages' },
    { icon: 'saved',    label: s.savedPosts($lang),   href: '/saved' },
    { icon: 'settings', label: s.navSettings($lang),  href: '/settings' },
  ];
</script>

<!-- Global header (drawer açma + logo) -->
{#if showBottomNav}
  <header class="global-header">
    <button class="hdr-hamburger" onclick={() => drawerOpen = !drawerOpen} aria-label="Menü">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="20" height="20">
        <line x1="3" y1="6" x2="21" y2="6"/>
        <line x1="3" y1="12" x2="21" y2="12"/>
        <line x1="3" y1="18" x2="21" y2="18"/>
      </svg>
    </button>
    <span class="hdr-logo">heftreng</span>

    <div class="hdr-actions">
      <a href="/search" class="hdr-icon" aria-label="Ara">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="21" height="21"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
      </a>
      {#if $currentUser}
        <a href="/notifications" class="hdr-icon badge-wrap" aria-label="Bildirimler">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="21" height="21"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          {#if $unreadNotifCount > 0}<span class="badge">{$unreadNotifCount > 99 ? '99+' : $unreadNotifCount}</span>{/if}
        </a>
        <a href="/messages" class="hdr-icon badge-wrap" aria-label="Mesajlar">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="21" height="21"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          {#if $unreadMsgCount > 0}<span class="badge">{$unreadMsgCount > 99 ? '99+' : $unreadMsgCount}</span>{/if}
        </a>
      {/if}
    </div>
  </header>
{/if}

<!-- Drawer backdrop -->
{#if drawerOpen}
  <div class="drawer-backdrop" onclick={closeDrawer} onkeydown={(e) => e.key === "Escape" && closeDrawer()} role="button" tabindex="-1" aria-label="Kapat"></div>
{/if}

<!-- Sol Drawer -->
<aside class="drawer" class:open={drawerOpen}>
  <div class="drawer-inner">

    <!-- Profil özeti -->
    {#if $currentUser}
      <a href="/profile/{$currentUser.uid}" class="dr-profile" onclick={closeDrawer}>
        <div class="dr-av">
          {#if $currentUser.photoURL}
            <img src={$currentUser.photoURL} alt="" />
          {:else}
            <span>{($currentUser.displayName ?? '?')[0].toUpperCase()}</span>
          {/if}
        </div>
        <div class="dr-profile-info">
          <span class="dr-name">{$currentUser.displayName ?? 'Kullanıcı'}</span>
          <span class="dr-email">{$currentUser.email ?? ''}</span>
        </div>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16" style="color:var(--muted)"><polyline points="9 18 15 12 9 6"/></svg>
      </a>
    {:else}
      <a href="/login" class="dr-login-btn" onclick={closeDrawer}>Giriş Yap</a>
    {/if}

    <div class="dr-divider"></div>

    <!-- 3'lü navigasyon grid -->
    <div class="dr-grid">
      {#each navGrid as item}
        <a href={item.href} class="dr-grid-item" class:dr-active={isActive(item.href)} onclick={closeDrawer}>
          <!-- Feed -->
          {#if item.icon === 'feed'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M3 3h18v2H3zm0 4h18v2H3zm0 4h18v2H3zm0 4h12v2H3z"/></svg>
          <!-- Arama -->
          {:else if item.icon === 'search'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>
          <!-- Kütüphane -->
          {:else if item.icon === 'library'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
          <!-- Kurdî -->
          {:else if item.icon === 'kurdi'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M5 8l6 6"/><path d="M4 14l6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="M22 22l-5-10-5 10"/><path d="M14 18h6"/></svg>
          <!-- Bildirim -->
          {:else if item.icon === 'notif'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/></svg>
          <!-- Mesaj -->
          {:else if item.icon === 'message'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
          <!-- Kaydedilenler -->
          {:else if item.icon === 'saved'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/></svg>
          <!-- Ayarlar -->
          {:else if item.icon === 'settings'}
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="22" height="22"><circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z"/></svg>
{/if}
          <span>{item.label}</span>
        </a>
      {/each}
    </div>

    <div class="dr-divider"></div>

    <!-- Tema seçici -->
    <div class="dr-theme">
      <svg viewBox="0 0 24 24" fill="none" stroke="#F59E0B" stroke-width="2" width="16" height="16"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>
      {#each [['light','Açık'],['dark','Koyu'],['system','Sistem']] as [mode, label]}
        <button
          class="theme-chip"
          class:selected={$theme.mode === mode}
          onclick={() => setTheme(mode)}
        >{label}</button>
      {/each}
    </div>

    <div class="dr-divider"></div>

    <!-- Uygulamayı paylaş -->
    <button class="dr-share" onclick={() => {
      closeDrawer();
      if (navigator.share) navigator.share({ title: 'Heftreng', url: 'https://heftreng.onrender.com' });
      else navigator.clipboard.writeText('https://heftreng.onrender.com');
    }}>
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><line x1="8.59" y1="13.51" x2="15.42" y2="17.49"/><line x1="15.41" y1="6.51" x2="8.59" y2="10.49"/></svg>
      Uygulamayı Paylaş
    </button>

    <!-- Çıkış -->
    {#if $currentUser}
      <button class="dr-signout" onclick={handleSignOut}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/></svg>
        Çıkış Yap
      </button>
    {/if}

  </div>
</aside>

{@render children()}

{#if showBottomNav && $currentUser}
  <nav class="bottom-nav">
    <a href="/feed" class="nav-item" class:active={isActive('/feed')} aria-label="Gönderi">
      {#if isActive('/feed')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M3 3h18v2H3zm0 4h18v2H3zm0 4h18v2H3zm0 4h12v2H3z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M3 3h18v2H3zm0 4h18v2H3zm0 4h18v2H3zm0 4h12v2H3z"/></svg>
      {/if}
      <span>{s.navFeed($lang)}</span>
    </a>
    <a href="/library" class="nav-item" class:active={isActive('/library')} aria-label={s.navLibrary($lang)}>
      {#if isActive('/library')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15zm2.5-2.5H18v3H6.5A.5.5 0 0 1 6 19.5v0A.5.5 0 0 1 6.5 19z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      {/if}
      <span>{s.navLibrary($lang)}</span>
    </a>
    <a href="/kurdi" class="nav-item" class:active={isActive('/kurdi')} aria-label={s.navKurdi($lang)}>
      {#if isActive('/kurdi')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M5 8l6 6"/><path d="M4 14l6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="M22 22l-5-10-5 10"/><path d="M14 18h6"/></svg>
      {/if}
      <span>{s.navKurdi($lang)}</span>
    </a>
    <a href="/profile/{$currentUser?.uid}" class="nav-item" class:active={isActive('/profile')} aria-label={s.navProfile($lang)}>
      {#if $currentUser?.photoURL}
        <img src={$currentUser.photoURL} alt="" class="nav-avatar" class:active-av={isActive('/profile')} />
      {:else if isActive('/profile')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      {/if}
      <span>{s.navProfile($lang)}</span>
    </a>
  </nav>
{/if}

<style>
/* ── Bottom Nav ───────────────────────────────────────────────── */
.bottom-nav {
  position: fixed; bottom: 0; left: 0; right: 0;
  height: 60px; background: var(--surface);
  border-top: 1px solid var(--divider);
  display: flex; align-items: center; justify-content: space-around;
  z-index: 100; padding-bottom: env(safe-area-inset-bottom);
}
.nav-item {
  display: flex; flex-direction: column; align-items: center;
  gap: 2px; color: var(--muted); text-decoration: none;
  font-size: 10px; font-weight: 500; padding: 6px 12px;
  border-radius: 12px; transition: color 0.15s; flex: 1;
}
.nav-item.active { color: var(--primary); }
.nav-item:hover  { color: var(--on-bg); }
.nav-avatar { width: 24px; height: 24px; border-radius: 50%; object-fit: cover; border: 1.5px solid var(--divider); }
.nav-avatar.active-av { border-color: var(--primary); }

/* ── Global Header ────────────────────────────────────────────── */
.global-header {
  position: sticky; top: 0; left: 0; right: 0;
  height: 52px;
  background: var(--surface);
  border-bottom: 1px solid var(--divider);
  display: flex; align-items: center; gap: 10px;
  padding: 0 14px;
  z-index: 100;
  max-width: 100%;
}
.hdr-hamburger {
  width: 36px; height: 36px; border-radius: 50%;
  background: none; border: none;
  display: flex; align-items: center; justify-content: center;
  color: var(--on-bg); cursor: pointer; flex-shrink: 0;
  transition: background 0.15s;
}
.hdr-hamburger:hover { background: var(--surface-var); }
.hdr-logo {
  font-size: 20px; font-weight: 900; color: var(--primary);
  font-family: system-ui, sans-serif; flex: 1;
}
.hdr-actions { display: flex; align-items: center; gap: 0; }
.hdr-icon {
  width: 38px; height: 38px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  color: var(--on-bg); text-decoration: none;
  transition: background 0.15s; position: relative;
}
.hdr-icon:hover { background: var(--surface-var); }
.badge-wrap { position: relative; }
.badge {
  position: absolute; top: -4px; right: -5px;
  min-width: 16px; height: 16px; border-radius: 8px;
  background: #ef4444; color: #fff;
  font-size: 9px; font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  padding: 0 3px; border: 1.5px solid var(--surface);
  pointer-events: none;
}

/* ── Drawer backdrop ──────────────────────────────────────────── */
.drawer-backdrop {
  position: fixed; inset: 0; background: rgba(0,0,0,0.45);
  z-index: 250; backdrop-filter: blur(2px);
}

/* ── Drawer ───────────────────────────────────────────────────── */
.drawer {
  position: fixed; top: 0; left: 0; bottom: 0;
  width: min(300px, 85vw);
  background: var(--surface);
  z-index: 260;
  transform: translateX(-100%);
  transition: transform 0.28s cubic-bezier(.4,0,.2,1);
  display: flex; flex-direction: column;
  box-shadow: 4px 0 24px rgba(0,0,0,0.15);
}
.drawer.open { transform: translateX(0); }

.drawer-inner {
  flex: 1; overflow-y: auto;
  padding: 20px 16px;
  padding-top: max(20px, env(safe-area-inset-top));
  padding-bottom: max(20px, env(safe-area-inset-bottom));
  display: flex; flex-direction: column; gap: 0;
}


/* Profil */
.dr-profile {
  display: flex; align-items: center; gap: 12px;
  padding: 10px; border-radius: 14px; text-decoration: none;
  transition: background 0.15s; margin-bottom: 12px;
}
.dr-profile:hover { background: var(--surface-var); }
.dr-av {
  width: 46px; height: 46px; border-radius: 50%;
  background: var(--surface-var); overflow: hidden; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px; font-weight: 700; color: var(--on-bg);
}
.dr-av img { width: 100%; height: 100%; object-fit: cover; }
.dr-profile-info { flex: 1; min-width: 0; }
.dr-name { display: block; font-size: 14px; font-weight: 700; color: var(--on-bg); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dr-email { display: block; font-size: 11px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.dr-login-btn { display: block; text-align: center; padding: 12px; border-radius: 12px; background: var(--primary); color: #fff; font-weight: 700; font-size: 14px; text-decoration: none; margin-bottom: 12px; }

/* Divider */
.dr-divider { height: 1px; background: var(--divider); margin: 8px 0; }

/* 3'lü Grid */
.dr-grid {
  display: grid; grid-template-columns: repeat(3, 1fr);
  border: 1px solid var(--divider); border-radius: 14px;
  overflow: hidden; margin: 8px 0;
  background: var(--surface-var);
}
.dr-grid-item {
  display: flex; flex-direction: column; align-items: center;
  gap: 5px; padding: 14px 4px; text-decoration: none;
  color: var(--on-bg); font-size: 10px; font-weight: 500;
  transition: background 0.15s;
  border-right: 0.5px solid var(--divider);
  border-bottom: 0.5px solid var(--divider);
}
.dr-grid-item:nth-child(3n) { border-right: none; }
.dr-grid-item:nth-last-child(-n+3) { border-bottom: none; }
.dr-grid-item:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }
.dr-grid-item.dr-active { color: var(--primary); }
.dr-grid-item.dr-active svg { stroke: var(--primary); }

/* Tema */
.dr-theme { display: flex; align-items: center; gap: 6px; padding: 8px 4px; }
.theme-chip {
  flex: 1; padding: 6px 0; border-radius: 99px; border: none;
  font-size: 11px; font-weight: 500; cursor: pointer;
  background: var(--surface-var); color: var(--muted);
  font-family: inherit; transition: background 0.15s, color 0.15s;
}
.theme-chip.selected { background: var(--primary); color: #fff; font-weight: 700; }

/* Paylaş / Çıkış */
.dr-share, .dr-signout {
  display: flex; align-items: center; gap: 12px;
  padding: 12px 10px; border-radius: 12px; width: 100%;
  border: none; background: none; cursor: pointer;
  font-size: 14px; font-family: inherit; transition: background 0.15s;
  text-align: left;
}
.dr-share { color: var(--primary); margin-top: 4px; }
.dr-share:hover { background: color-mix(in srgb, var(--primary) 8%, transparent); }
.dr-signout { color: #ef4444; margin-top: 2px; }
.dr-signout:hover { background: rgba(239,68,68,0.08); }
</style>
