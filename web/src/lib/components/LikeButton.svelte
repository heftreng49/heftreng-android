<script lang="ts">
  import { shortNum } from '$lib/models/util';

  interface Props {
    liked:  boolean;
    count:  number;
    onclick?: () => void;
  }
  let { liked, count, onclick }: Props = $props();
</script>

<button
  class="like-btn"
  class:liked
  onclick={onclick}
  aria-label={liked ? 'Beğeniyi kaldır' : 'Beğen'}
>
  <svg viewBox="0 0 24 24" fill={liked ? 'currentColor' : 'none'} stroke="currentColor" stroke-width="2">
    <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
  </svg>
  <span>{shortNum(count)}</span>
</button>

<style>
  .like-btn {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    background: none;
    border: none;
    cursor: pointer;
    color: #888;
    font-size: 13px;
    padding: 4px 6px;
    border-radius: 6px;
    transition: color .15s;
  }
  .like-btn:hover { color: #e05; }
  .like-btn.liked  { color: #e05; }
  svg { width: 18px; height: 18px; }
</style>
