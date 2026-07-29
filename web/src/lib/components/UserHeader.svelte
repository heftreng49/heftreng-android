<script lang="ts">
  import { ago } from '$lib/utils/time';

  export interface MenuItem {
    label:    string;
    href?:    string;
    onclick?: () => void;
    danger?:  boolean;
  }

  interface Props {
    uid:          string;
    displayName:  string;
    photoURL?:    string;
    ts?:          unknown;
    tag?:         string;       // "alıntı paylaştı" gibi alt etiket
    size?:        number;       // avatar boyutu, default 36
    menuItems?:   MenuItem[];   // yoksa 3-nokta butonu render edilmez
  }

  let {
    uid,
    displayName,
    photoURL  = '',
    ts,
    tag       = '',
    size      = 36,
    menuItems = [],
  }: Props = $props();

  let menuOpen = $state(false);

  function toggleMenu(e: Event) {
    e.stopPropagation();
    menuOpen = !menuOpen;
  }

  function closeMenu() { menuOpen = false; }

  function handleItem(item: MenuItem, e: Event) {
    e.stopPropagation();
    menuOpen = false;
    item.onclick?.();
  }

  const timeAgo = $derived(ts ? ago(ts) : '');
</script>

<svelte:window onclick={closeMenu} />

<div class="user-header">
  <!-- Avatar -->
  <a href="/profile/{uid}" class="av-wrap" style="--sz:{size}px" onclick={(e) => e.stopPropagation()}>
    {#if photoURL}
      <img src={photoURL} alt={displayName} />
    {:else}
      <span>{(displayName || '?')[0].toUpperCase()}</span>
    {/if}
  </a>

  <!-- İsim + zaman -->
  <div class="meta">
    <a href="/profile/{uid}" class="username" onclick={(e) => e.stopPropagation()}>
      {displayName}
    </a>
    <div class="sub-row">
      {#if timeAgo}
        <span class="timestamp">{timeAgo}</span>
      {/if}
      {#if tag}
        {#if timeAgo}<span class="sep">·</span>{/if}
        <span class="tag">{tag}</span>
      {/if}
    </div>
  </div>

  <!-- 3-nokta menü -->
  {#if menuItems.length > 0}
    <div class="menu-wrap" onclick={(e) => e.stopPropagation()}>
      <button class="menu-btn" onclick={toggleMenu} aria-label="Seçenekler">
        <svg viewBox="0 0 24 24" fill="currentColor" width="16" height="16">
          <circle cx="12" cy="5"  r="1.8"/>
          <circle cx="12" cy="12" r="1.8"/>
          <circle cx="12" cy="19" r="1.8"/>
        </svg>
      </button>

      {#if menuOpen}
        <div class="dropdown" role="menu">
          {#each menuItems as item}
            {#if item.href}
              <a
                href={item.href}
                class="drop-item"
                class:danger={item.danger}
                role="menuitem"
                onclick={(e) => handleItem(item, e)}
              >
                {item.label}
              </a>
            {:else}
              <button
                class="drop-item"
                class:danger={item.danger}
                role="menuitem"
                onclick={(e) => handleItem(item, e)}
              >
                {item.label}
              </button>
            {/if}
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
.user-header {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 11px 12px 0;
}

/* Avatar */
.av-wrap {
  width:  var(--sz, 36px);
  height: var(--sz, 36px);
  border-radius: 50%;
  background: var(--surface-var);
  overflow: hidden;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: calc(var(--sz, 36px) * 0.38);
  font-weight: 700;
  color: var(--on-bg);
  text-decoration: none;
}
.av-wrap img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Metin */
.meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.username {
  font-size: 13px;
  font-weight: 700;
  color: var(--on-bg);
  text-decoration: none;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.username:hover { text-decoration: underline; }

.sub-row {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.timestamp { font-size: 11px; color: var(--muted); }
.sep       { font-size: 11px; color: var(--muted); }
.tag       { font-size: 11px; color: var(--muted); }

/* 3-nokta menü */
.menu-wrap { position: relative; margin-left: auto; flex-shrink: 0; }

.menu-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: none;
  background: none;
  cursor: pointer;
  color: var(--muted);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
}
.menu-btn:hover { background: var(--surface-var); }

.dropdown {
  position: absolute;
  right: 0;
  top: calc(100% + 4px);
  background: var(--surface);
  border: 1px solid var(--divider);
  border-radius: 12px;
  min-width: 170px;
  box-shadow: 0 8px 24px rgba(0,0,0,0.14);
  overflow: hidden;
  z-index: 200;
}

.drop-item {
  display: flex;
  align-items: center;
  width: 100%;
  padding: 11px 14px;
  font-size: 13px;
  color: var(--on-surface);
  background: none;
  border: none;
  cursor: pointer;
  text-align: left;
  text-decoration: none;
  font-family: inherit;
  transition: background 0.1s;
}
.drop-item:hover  { background: var(--surface-var); }
.drop-item.danger { color: #ef4444; }
</style>
