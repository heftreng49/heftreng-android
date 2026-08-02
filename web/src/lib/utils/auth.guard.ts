// Her sayfada tekrar tekrar yazmak yerine tek fonksiyon
// Kullanım: onMount(() => requireAuth($authLoading, $currentUser))
import { get } from 'svelte/store';
import { goto } from '$app/navigation';
import { authLoading, currentUser } from '$lib/stores/auth';

/**
 * authLoading bitmeden redirect yapma.
 * Bittikten sonra kullanıcı yoksa /login'e yönlendir.
 * Promise döner — await edilebilir.
 */
export function requireAuth(): Promise<boolean> {
  return new Promise((resolve) => {
    // Zaten yüklenmiş — hemen kontrol et
    if (!get(authLoading)) {
      if (!get(currentUser)) { goto('/login'); resolve(false); }
      else resolve(true);
      return;
    }
    // Yüklenmeyi bekle
    const unsub = authLoading.subscribe((loading) => {
      if (!loading) {
        unsub();
        if (!get(currentUser)) { goto('/login'); resolve(false); }
        else resolve(true);
      }
    });
  });
}
