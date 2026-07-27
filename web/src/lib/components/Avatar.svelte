<script lang="ts">
  // Android Avatar/ProfileImage composable karşılığı
  interface Props {
    src?:  string;
    name?: string;
    size?: number;
    href?: string;
  }
  let { src = '', name = '', size = 40, href = '' }: Props = $props();

  // İsmin baş harfinden fallback
  const initials = $derived(
    name ? name.trim().charAt(0).toUpperCase() : '?'
  );
</script>

{#if href}
  <a {href} class="avatar-wrap" style="--sz:{size}px">
    {#if src}
      <img src={src} alt={name} class="avatar-img" />
    {:else}
      <span class="avatar-fallback">{initials}</span>
    {/if}
  </a>
{:else}
  <span class="avatar-wrap" style="--sz:{size}px">
    {#if src}
      <img src={src} alt={name} class="avatar-img" />
    {:else}
      <span class="avatar-fallback">{initials}</span>
    {/if}
  </span>
{/if}

<style>
  .avatar-wrap {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width:  var(--sz, 40px);
    height: var(--sz, 40px);
    border-radius: 50%;
    overflow: hidden;
    flex-shrink: 0;
    background: #e0d7f0;
    text-decoration: none;
  }
  .avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  .avatar-fallback {
    font-size: calc(var(--sz, 40px) * 0.4);
    font-weight: 700;
    color: #6b4fa0;
  }
</style>
