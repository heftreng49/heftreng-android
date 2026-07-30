<!-- Android NotificationsScreen notifIcon/notifIconColor karşılığı -->
<script lang="ts">
  import { notifMeta } from '$lib/services/notification.service';
  import { ago } from '$lib/utils/time';

  interface Props {
    notif:     any;
    onClick?:  (notif: any) => void;
  }
  let { notif, onClick }: Props = $props();
  const meta = $derived(notifMeta(notif.type));
</script>

<!-- svelte-ignore a11y_click_events_have_key_events -->
<!-- svelte-ignore a11y_no_static_element_interactions -->
<div
  class="notif-item"
  class:unread={!notif.read}
  onclick={() => onClick?.(notif)}
>
  <div class="notif-icon-wrap" style="background: {meta.color}22; color: {meta.color}">
    <span class="notif-icon">{meta.icon}</span>
  </div>
  <div class="notif-body">
    {#if notif.fromPhoto}
      <img src={notif.fromPhoto} alt={notif.fromName} class="notif-avatar" />
    {/if}
    <div class="notif-text">
      <p class="notif-msg">{notif.message || notif.title}</p>
      <span class="notif-time">{ago(notif.ts)}</span>
    </div>
  </div>
  {#if !notif.read}
    <div class="notif-dot" style="background:{meta.color}"></div>
  {/if}
</div>

<style>
.notif-item {
  display: flex; align-items: flex-start; gap: 12px;
  padding: 13px 16px; border-bottom: 1px solid var(--divider);
  cursor: pointer; transition: background 0.12s; position: relative;
}
.notif-item:hover { background: var(--surface-var); }
.notif-item.unread { background: color-mix(in srgb, var(--primary) 4%, transparent); }
.notif-icon-wrap {
  width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
}
.notif-icon { font-size: 16px; }
.notif-body { flex: 1; min-width: 0; display: flex; gap: 10px; align-items: flex-start; }
.notif-avatar { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.notif-text { flex: 1; min-width: 0; }
.notif-msg { margin: 0 0 3px; font-size: 0.88rem; line-height: 1.4; color: var(--on-bg); }
.notif-time { font-size: 0.75rem; color: var(--muted); }
.notif-dot {
  width: 8px; height: 8px; border-radius: 50%;
  flex-shrink: 0; margin-top: 6px;
}
</style>
