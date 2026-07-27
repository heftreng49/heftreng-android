<!-- Android ConnectedPostCard / ProfileScreen kullanıcı satırı karşılığı -->
<script lang="ts">
  import Avatar from './Avatar.svelte';

  interface Props {
    uid:         string;
    name:        string;
    username?:   string;
    photoURL?:   string;
    subtitle?:   string;
    size?:       number;
    /** tıklanabilir mi (profile link) */
    clickable?:  boolean;
  }

  let {
    uid,
    name,
    username   = '',
    photoURL   = '',
    subtitle   = '',
    size       = 36,
    clickable  = true,
  }: Props = $props();
</script>

<a
  href={clickable ? `/profile/${uid}` : undefined}
  class="user-chip"
  class:no-link={!clickable}
  role={clickable ? 'link' : undefined}
>
  <Avatar src={photoURL} name={name} {size} />
  <div class="info">
    <span class="name">{name}</span>
    {#if username}
      <span class="username">@{username}</span>
    {/if}
    {#if subtitle}
      <span class="subtitle">{subtitle}</span>
    {/if}
  </div>
</a>

<style>
.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  text-decoration: none;
  color: inherit;
  border-radius: 10px;
  padding: 4px 6px;
  transition: background 0.15s;
}
.user-chip:hover:not(.no-link) {
  background: rgba(0,0,0,0.05);
}
.no-link { cursor: default; }

.info {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}
.name {
  font-size: 0.88rem;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.username {
  font-size: 0.75rem;
  color: #888;
}
.subtitle {
  font-size: 0.75rem;
  color: #aaa;
}
</style>
