import { initializeApp, getApps } from 'firebase/app';
import { getAuth } from 'firebase/auth';
import {
  getFirestore, initializeFirestore,
  persistentLocalCache, persistentMultipleTabManager,
} from 'firebase/firestore';
import { getStorage } from 'firebase/storage';

const firebaseConfig = {
  apiKey:            import.meta.env.VITE_FIREBASE_API_KEY,
  authDomain:        import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
  projectId:         import.meta.env.VITE_FIREBASE_PROJECT_ID,
  storageBucket:     import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  appId:             import.meta.env.VITE_FIREBASE_APP_ID,
};

const app = getApps().length ? getApps()[0] : initializeApp(firebaseConfig);
export const auth    = getAuth(app);
export const storage = getStorage(app);

// ── Firestore: kalıcı yerel cache (IndexedDB) ────────────────────────────────
// Önceden getFirestore(app) düz bellek-içi cache kullanıyordu — sayfa her
// açıldığında (mesajlar/bildirimler/sohbet) onSnapshot() SIFIRDAN sunucuya
// gidiyor, ilk veri gelene kadar shimmer/skeleton uzun süre ekranda kalıyordu.
// persistentLocalCache ile son bilinen veri IndexedDB'den ANINDA gösterilir,
// SDK arka planda sunucuyla senkronize edip farkları canlı günceller.
// initializeFirestore() bir app için sadece BİR KEZ çağrılabildiğinden
// (Vite HMR'de modül yeniden yüklenirse ikinci çağrı hata verir) try/catch
// ile normal getFirestore()'a düşülüyor — böylece hem HMR hem eski
// tarayıcı/gizli sekme (IndexedDB kapalı) senaryosunda kırılmaz.
function initDb() {
  try {
    return initializeFirestore(app, {
      localCache: persistentLocalCache({ tabManager: persistentMultipleTabManager() }),
    });
  } catch {
    return getFirestore(app);
  }
}
export const db = initDb();
