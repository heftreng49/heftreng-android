<script lang="ts">
  interface Props {
    width?:  string;
    height?: string;
    radius?: string;
    count?:  number;
  }
  let {
    width  = '100%',
    height = '16px',
    radius = '6px',
    count  = 1,
  }: Props = $props();
</script>

{#each { length: count } as _}
  <span
    class="skeleton"
    style="width:{width}; height:{height}; border-radius:{radius}"
    aria-hidden="true"
  ></span>
{/each}

<style>
  .skeleton {
    display: block;
    background: linear-gradient(90deg, #ede9f5 25%, #d8d0ed 50%, #ede9f5 75%);
    background-size: 200% 100%;
    animation: shimmer 1.4s infinite;
    margin-bottom: 8px;
  }
  @keyframes shimmer {
    0%   { background-position: 200% 0; }
    100% { background-position: -200% 0; }
  }
</style>
