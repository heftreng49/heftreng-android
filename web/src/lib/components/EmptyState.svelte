<script lang="ts">
  interface Props {
    icon?:       string;
    message:     string;
    hint?:       string;
    actionLabel?: string;  // buton/link metni
    actionHref?:  string;  // verilirse <a>, verilmezse <button>
    onAction?:   () => void;
  }
  let {
    icon        = '',
    message,
    hint        = '',
    actionLabel = '',
    actionHref  = '',
    onAction,
  }: Props = $props();
</script>

<div class="empty-state">
  {#if icon}
    <span class="empty-icon" aria-hidden="true">{icon}</span>
  {/if}
  <p class="empty-msg">{message}</p>
  {#if hint}
    <p class="empty-hint">{hint}</p>
  {/if}
  {#if actionLabel}
    {#if actionHref}
      <a href={actionHref} class="empty-action">{actionLabel}</a>
    {:else}
      <button class="empty-action" onclick={onAction}>{actionLabel}</button>
    {/if}
  {/if}
</div>

<style>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 56px 20px;
  gap: 10px;
  color: var(--muted);
  text-align: center;
}
.empty-icon {
  font-size: 44px;
  opacity: 0.5;
  line-height: 1;
}
.empty-msg {
  font-size: 15px;
  font-weight: 600;
  color: var(--on-bg);
  margin: 0;
}
.empty-hint {
  font-size: 13px;
  color: var(--muted);
  margin: 0;
}
.empty-action {
  display: inline-block;
  margin-top: 4px;
  padding: 9px 20px;
  background: var(--primary);
  color: #fff;
  border-radius: 20px;
  text-decoration: none;
  font-weight: 700;
  font-size: 13px;
  border: none;
  cursor: pointer;
  font-family: inherit;
}
</style>
