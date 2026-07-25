<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { page } from '$app/stores';
  import { auth } from '$lib/firebase/config';
  import { onAuthStateChanged } from 'firebase/auth';
  import { currentUser, authLoading } from '$lib/store/auth';
  import { theme, applyTheme } from '$lib/store/theme';

  let { children } = $props();

  // Alt menü gösterilmeyecek rotalar
  const hideNavRoutes = ['/login', '/register'];
  let showBottomNav = $derived(!hideNavRoutes.includes($page.url.pathname));

  // Aktif sekme tespiti
  let currentPath = $derived($page.url.pathname);
  function isActive(path: string): boolean {
    if (path === '/feed') return currentPath === '/feed' || currentPath === '/';
    if (path === '/profile') return currentPath.startsWith('/profile');
    return currentPath.startsWith(path);
  }

  onMount(() => {
    applyTheme($theme.variant, $theme.mode);
    const unsub = onAuthStateChanged(auth, (user) => {
      currentUser.set(user);
      authLoading.set(false);
    });
    return unsub;
  });
</script>

{@render children()}

{#if showBottomNav && $currentUser}
  <nav class="bottom-nav">
    <!-- Gönderi (Feed) -->
    <a href="/feed" class="nav-item" class:active={isActive('/feed')} aria-label="Gönderi">
      {#if isActive('/feed')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M3 3h18v2H3zm0 4h18v2H3zm0 4h18v2H3zm0 4h12v2H3z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M3 3h18v2H3zm0 4h18v2H3zm0 4h18v2H3zm0 4h12v2H3z"/></svg>
      {/if}
      <span>Gönderi</span>
    </a>

    <!-- Kütüphane -->
    <a href="/library" class="nav-item" class:active={isActive('/library')} aria-label="Kütüphane">
      {#if isActive('/library')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15zm2.5-2.5H18v3H6.5A.5.5 0 0 1 6 19.5v0A.5.5 0 0 1 6.5 19z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>
      {/if}
      <span>Kütüphane</span>
    </a>

    <!-- Kurdî -->
    <a href="/kurdi" class="nav-item" class:active={isActive('/kurdi')} aria-label="Kurdî">
      {#if isActive('/kurdi')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M12.87 15.07l-2.54-2.51.03-.03c1.74-1.94 2.98-4.17 3.71-6.53H17V4h-7V2H8v2H1v1.99h11.17C11.5 7.92 10.44 9.75 9 11.35 8.07 10.32 7.3 9.19 6.69 8h-2c.73 1.63 1.73 3.17 2.98 4.56l-5.09 5.02L4 19l5-5 3.11 3.11.76-2.04zM18.5 10h-2L12 22h2l1.12-3h4.75L21 22h2l-4.5-12zm-2.62 7l1.62-4.33L19.12 17h-3.24z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M5 8l6 6"/><path d="M4 14l6-6 2-3"/><path d="M2 5h12"/><path d="M7 2h1"/><path d="M22 22l-5-10-5 10"/><path d="M14 18h6"/></svg>
      {/if}
      <span>Kurdî</span>
    </a>

    <!-- Profil -->
    <a href="/profile/{$currentUser?.uid}" class="nav-item" class:active={isActive('/profile')} aria-label="Profil">
      {#if $currentUser?.photoURL}
        <img src={$currentUser.photoURL} alt="" class="nav-avatar" class:active-av={isActive('/profile')} />
      {:else if isActive('/profile')}
        <svg viewBox="0 0 24 24" fill="currentColor" width="24" height="24"><path d="M12 12c2.7 0 4.8-2.1 4.8-4.8S14.7 2.4 12 2.4 7.2 4.5 7.2 7.2 9.3 12 12 12zm0 2.4c-3.2 0-9.6 1.6-9.6 4.8v2.4h19.2v-2.4c0-3.2-6.4-4.8-9.6-4.8z"/></svg>
      {:else}
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" width="24" height="24"><path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/></svg>
      {/if}
      <span>Profil</span>
    </a>
  </nav>
{/if}

<style>
.bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 60px;
  background: var(--surface);
  border-top: 1px solid var(--divider);
  display: flex;
  align-items: center;
  justify-content: space-around;
  z-index: 100;
  padding-bottom: env(safe-area-inset-bottom);
}
.nav-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  color: var(--muted);
  text-decoration: none;
  font-size: 10px;
  font-weight: 500;
  padding: 6px 12px;
  border-radius: 12px;
  transition: color 0.15s;
  flex: 1;
}
.nav-item.active { color: var(--primary); }
.nav-item:hover { color: var(--on-bg); }
.nav-avatar {
  width: 24px;
  height: 24px;
  border-radius: 50%;
  object-fit: cover;
  border: 1.5px solid var(--divider);
}
.nav-avatar.active-av { border-color: var(--primary); }
</style>
