/**
 * capacitor-push-init.ts
 *
 * Bu dosyayı web/src/lib/services/ klasörüne ekle.
 * Sonra +layout.svelte'deki onMount içinde şöyle çağır:
 *
 *   import { initCapacitorPush } from '$lib/services/capacitor-push-init';
 *   onMount(() => {
 *     initCapacitorPush();
 *     // ... mevcut kodun devamı
 *   });
 */

import { Capacitor } from '@capacitor/core';

export async function initCapacitorPush() {
  // Sadece gerçek iOS/Android cihazda çalış — web'de atla
  if (!Capacitor.isNativePlatform()) return;

  // Dynamic import — web build'de tree-shake edilir
  const { PushNotifications } = await import('@capacitor/push-notifications');

  // İzin iste
  const permission = await PushNotifications.requestPermissions();
  if (permission.receive !== 'granted') return;

  // FCM token'ı al — mevcut HeftrangMessagingService mantığıyla uyumlu
  await PushNotifications.register();

  // Token gelince Firestore'a yaz (Android ile aynı yol: users/{uid}/fcmTokens)
  PushNotifications.addListener('registration', async (token) => {
    const { getAuth } = await import('firebase/auth');
    const { getFirestore, doc, setDoc, serverTimestamp } = await import('firebase/firestore');

    const user = getAuth().currentUser;
    if (!user) return;

    const db = getFirestore();
    await setDoc(
      doc(db, 'users', user.uid, 'fcmTokens', token.value),
      { token: token.value, platform: 'ios', updatedAt: serverTimestamp() },
      { merge: true }
    );
  });

  // Uygulama açıkken gelen bildirimi göster
  PushNotifications.addListener('pushNotificationReceived', (notification) => {
    console.log('[Push] Received:', notification.title);
    // İstersen burada toast gösterebilirsin:
    // toastStore.show(notification.title ?? '');
  });

  // Bildirime tıklanınca ilgili sayfaya git
  PushNotifications.addListener('pushNotificationActionPerformed', (action) => {
    const data = action.notification.data;
    if (data?.postId) {
      window.location.href = `/post/${data.postId}`;
    } else if (data?.convId) {
      window.location.href = `/messages/${data.convId}`;
    }
  });
}
