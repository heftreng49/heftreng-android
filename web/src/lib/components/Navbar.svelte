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
