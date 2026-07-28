<script lang="ts">
  import { page }        from '$app/stores';
  import { currentUser } from '$lib/stores/auth';

  // Aktif route (alt çizgi için)
  const isActive = (path: string) => $derived($page.url.pathname.startsWith(path));
</script>

<nav>
  <a href="/feed" class="logo">Heftreng</a>

  {#if $currentUser}
    <div class="nav-links">
      <a href="/feed"     class="nav-link" class:active={$page.url.pathname.startsWith('/feed')}>Gönderi</a>
      <a href="/library"  class="nav-link" class:active={$page.url.pathname.startsWith('/library')}>Kütüphane</a>
      <a href="/profile/{$currentUser.uid}" class="nav-link" class:active={$page.url.pathname.startsWith('/profile')}>Profil</a>
    </div>
  {:else}
    <a href="/login" class="btn">Giriş Yap</a>
  {/if}
</nav>

<style>
nav {
  display: flex; align-items: center; justify-content: space-between;
  padding: 12px 16px; background: var(--surface);
  border-bottom: 1px solid var(--divider); position: sticky; top: 0; z-index: 100;
}
.logo { font-family: 'Playfair Display', serif; font-size: 22px; font-weight: 600; color: var(--primary); text-decoration: none; }
.nav-links { display: flex; gap: 4px; }
.nav-link {
  padding: 7px 14px; border-radius: 20px; font-size: 14px; font-weight: 500;
  color: var(--muted); text-decoration: none; transition: all .15s;
}
.nav-link:hover { color: var(--on-bg); background: var(--surface-var); }
.nav-link.active { color: var(--primary); background: color-mix(in srgb, var(--primary) 10%, transparent); font-weight: 600; }
.btn { padding: 8px 18px; background: var(--primary); color: #fff; border-radius: 20px; font-size: 14px; font-weight: 600; text-decoration: none; }
</style>
