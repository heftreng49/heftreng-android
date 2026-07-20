<script lang="ts">
  import '../app.css';
  import { onMount } from 'svelte';
  import { auth } from '$lib/firebase/config';
  import { onAuthStateChanged } from 'firebase/auth';
  import { currentUser, authLoading } from '$lib/store/auth';
  import { theme, applyTheme } from '$lib/store/theme';

  let { children } = $props();

  onMount(() => {
    applyTheme($theme.variant, $theme.mode);
    const unsub = onAuthStateChanged(auth, (user) => {
      currentUser.set(user);
      authLoading.set(false);
    });
    return unsub;
  });
</script>

{@render children()}
