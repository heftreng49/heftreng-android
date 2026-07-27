<!-- Android LikersBottomSheet karşılığı — gönderi/yorum beğenenleri listeler -->
<script lang="ts">
  import Modal from './Modal.svelte';
  import UserChip from './UserChip.svelte';

  interface Liker {
    uid:        string;
    name:       string;
    photo_url?: string;
    created_at?: string;
  }

  interface Props {
    open:     boolean;
    likers:   Liker[];
    loading:  boolean;
    title?:   string;
    onclose?: () => void;
  }

  let {
    open    = $bindable(false),
    likers  = [],
    loading = false,
    title   = 'Beğenenler',
    onclose,
  }: Props = $props();
</script>

<Modal bind:open {title} maxWidth="420px" {onclose}>
  {#if loading}
    <div class="loading">
      <div class="spinner"></div>
    </div>
  {:else if likers.length === 0}
    <p class="empty">Henüz kimse beğenmedi.</p>
  {:else}
    <ul class="likers-list">
      {#each likers as liker (liker.uid)}
        <li>
          <UserChip
            uid={liker.uid}
            name={liker.name}
            photoURL={liker.photo_url ?? ''}
          />
        </li>
      {/each}
    </ul>
  {/if}
</Modal>

<style>
.loading {
  display: flex;
  justify-content: center;
  padding: 24px;
}
.spinner {
  width: 28px; height: 28px;
  border: 3px solid #eee;
  border-top-color: #888;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.empty {
  text-align: center;
  color: #aaa;
  font-size: 0.9rem;
  padding: 12px 0;
  margin: 0;
}
.likers-list {
  list-style: none;
  padding: 0; margin: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-height: 60vh;
  overflow-y: auto;
}
</style>
