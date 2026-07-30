<!-- Android SettingsRow / SettingsSwitchRow karşılığı -->
<script lang="ts">
  interface Props {
    icon?:      string;
    label:      string;
    sub?:       string;
    href?:      string;
    checked?:   boolean;
    toggle?:    boolean;   // toggle switch mi?
    danger?:    boolean;
    onToggle?:  (val: boolean) => void;
    onClick?:   () => void;
  }
  let { icon='', label, sub='', href='', checked=$bindable(false),
        toggle=false, danger=false, onToggle, onClick }: Props = $props();
</script>

{#if href}
  <a {href} class="settings-row" class:danger>
    {#if icon}<span class="row-icon">{icon}</span>{/if}
    <div class="row-body">
      <span class="row-label" class:danger>{label}</span>
      {#if sub}<span class="row-sub">{sub}</span>{/if}
    </div>
    <span class="row-chevron">›</span>
  </a>
{:else if toggle}
  <label class="settings-row">
    {#if icon}<span class="row-icon">{icon}</span>{/if}
    <div class="row-body">
      <span class="row-label">{label}</span>
      {#if sub}<span class="row-sub">{sub}</span>{/if}
    </div>
    <input type="checkbox" class="toggle" bind:checked
      onchange={() => onToggle?.(!checked)} />
  </label>
{:else}
  <!-- svelte-ignore a11y_click_events_have_key_events -->
  <!-- svelte-ignore a11y_no_static_element_interactions -->
  <div class="settings-row" class:danger onclick={onClick}>
    {#if icon}<span class="row-icon">{icon}</span>{/if}
    <div class="row-body">
      <span class="row-label" class:danger>{label}</span>
      {#if sub}<span class="row-sub">{sub}</span>{/if}
    </div>
    {#if !danger}<span class="row-chevron">›</span>{/if}
  </div>
{/if}

<style>
.settings-row {
  display: flex; align-items: center; gap: 14px;
  padding: 13px 16px; border-bottom: 1px solid var(--divider);
  cursor: pointer; transition: background 0.12s;
  text-decoration: none; color: inherit;
}
.settings-row:hover { background: var(--surface-var); }
.row-icon { font-size: 1.15rem; flex-shrink: 0; width: 24px; text-align: center; }
.row-body { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.row-label { font-size: 0.9rem; font-weight: 500; color: var(--on-bg); }
.row-label.danger { color: #ef4444; }
.row-sub { font-size: 0.75rem; color: var(--muted); }
.row-chevron { color: var(--muted); font-size: 1.2rem; }
/* Toggle switch */
.toggle { appearance: none; width: 44px; height: 24px; border-radius: 12px;
  background: var(--divider); cursor: pointer; position: relative;
  transition: background 0.2s; flex-shrink: 0; }
.toggle:checked { background: var(--primary); }
.toggle::after { content: ''; position: absolute; width: 18px; height: 18px;
  border-radius: 50%; background: #fff; top: 3px; left: 3px;
  transition: transform 0.2s; box-shadow: 0 1px 4px rgba(0,0,0,.2); }
.toggle:checked::after { transform: translateX(20px); }
</style>
