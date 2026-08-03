// ─────────────────────────────────────────────────────────────────────────────
// Konuşma listesi — PAYLAŞIMLI tek Firestore listener.
//
// Önceden +layout.svelte (rozet sayısı için, uygulama boyunca) ve
// messages/+page.svelte (liste için, sayfa her açıldığında) AYRI AYRI
// listenConversations() çağırıyordu — aynı kullanıcı için aynı sorguya
// iki paralel onSnapshot bağlantısı. Bu modül tek bir listener'ı burada
// tutar; her ikisi de aynı store'u okur. startConversationsListener()
// aynı kullanıcı için zaten aktifse hiçbir şey yapmaz (idempotent) —
// yani hem layout hem sayfa "güvenlik amaçlı" çağırabilir, gerçek
// listener sadece bir kez açılır.
// ─────────────────────────────────────────────────────────────────────────────
import { writable } from 'svelte/store';
import { listenConversations } from '$lib/services/message.service';

export const conversations        = writable<any[]>([]);
export const conversationsLoading = writable(true);
export const conversationsError   = writable(false);

let unsub:     (() => void) | null = null;
let activeUid: string | null       = null;

export function startConversationsListener(uid: string, force = false): void {
  if (!force && activeUid === uid && unsub) return; // zaten aktif — tekrar açma
  stopConversationsListener();
  activeUid = uid;
  conversationsLoading.set(true);
  conversationsError.set(false);

  unsub = listenConversations(
    uid,
    (convs) => {
      conversations.set(convs);
      conversationsLoading.set(false);
      conversationsError.set(false);
    },
    (err) => {
      console.error('conversations.store listener:', err);
      conversationsError.set(true);
      conversationsLoading.set(false);
    },
  );
}

export function stopConversationsListener(): void {
  unsub?.();
  unsub     = null;
  activeUid = null;
  conversations.set([]);
  conversationsLoading.set(true);
  conversationsError.set(false);
}
