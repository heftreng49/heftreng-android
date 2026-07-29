<script lang="ts">
  interface Props {
    icon?:    string;   // emoji
    message:  string;
    hint?:    string;   // ikincil açıklama
  }
  let { icon = '', message, hint = '' }: Props = $props();
</script>

<div class="empty-state">
  {#if icon}
    <span class="empty-icon" aria-hidden="true">{icon}</span>
  {/if}
  <p class="empty-msg">{message}</p>
  {#if hint}
    <p class="empty-hint">{hint}</p>
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
</style>
