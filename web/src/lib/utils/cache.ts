// ─────────────────────────────────────────────────────────────────────────
// Basit TTL (time-to-live) tabanlı önbellek katmanı.
//
// Amaç: message.service.ts / notification.service.ts gibi servislerde
// Firestore ve Supabase'e giden gereksiz okuma/yazma isteklerini azaltmak
// (egress tasarrufu). Üç yapı taşı sunar:
//
//   1) cacheGet / cacheSet / cacheDelete  — anahtar bazlı TTL cache
//      (bellek + opsiyonel sessionStorage yedeklemesi, sayfa yenilense
//      bile TTL dolmadıysa tekrar okuma yapılmaz)
//   2) getOrFetch                          — "cache'te varsa döndür, yoksa
//      fetcher'ı çalıştırıp cache'le" kalıbı + eşzamanlı çağrıları
//      tekilleştiren in-flight promise havuzu (aynı veri için N bileşen
//      aynı anda istekte bulunursa tek bir ağ isteği yapılır)
//   3) shouldWrite                         — belirli bir aralıkta aynı
//      yazma işleminin tekrarını engeller (presence, markAsRead gibi sık
//      tetiklenen ama nadiren gerçekten değişen yazmalar için)
//
// Not: Bu modül framework'ten bağımsızdır, herhangi bir serviste
// (Firestore/Supabase fark etmeksizin) kullanılabilir.
// ─────────────────────────────────────────────────────────────────────────

interface CacheEntry<T> {
  value: T;
  ts:    number;
}

const mem = new Map<string, CacheEntry<any>>();
const inFlight = new Map<string, Promise<any>>();

const SESSION_PREFIX = 'hft_cache_';

function readSession<T>(key: string): CacheEntry<T> | null {
  try {
    const raw = sessionStorage.getItem(SESSION_PREFIX + key);
    if (!raw) return null;
    return JSON.parse(raw) as CacheEntry<T>;
  } catch {
    return null; // sessionStorage yok (SSR) veya bozuk veri — sessizce yoksay
  }
}

function writeSession<T>(key: string, entry: CacheEntry<T>): void {
  try {
    sessionStorage.setItem(SESSION_PREFIX + key, JSON.stringify(entry));
  } catch {
    // quota dolu / SSR / gizli mod — cache yine bellekte çalışmaya devam eder
  }
}

/** key altında TTL'i dolmamış bir değer varsa döner, yoksa null. */
export function cacheGet<T>(key: string, ttlMs: number): T | null {
  let entry = mem.get(key) as CacheEntry<T> | undefined;
  if (!entry) {
    const fromSession = readSession<T>(key);
    if (fromSession) {
      entry = fromSession;
      mem.set(key, entry); // sonraki okumalar için belleğe al
    }
  }
  if (!entry) return null;
  if (Date.now() - entry.ts > ttlMs) return null;
  return entry.value;
}

/** Değeri cache'e yazar. persist=true ise sessionStorage'a da yedekler
 *  (sayfa yenilendiğinde bile geçerli kalır — profil bilgisi gibi az
 *  değişen veriler için kullanılır). */
export function cacheSet<T>(key: string, value: T, persist = false): void {
  const entry: CacheEntry<T> = { value, ts: Date.now() };
  mem.set(key, entry);
  if (persist) writeSession(key, entry);
}

export function cacheDelete(key: string): void {
  mem.delete(key);
  try { sessionStorage.removeItem(SESSION_PREFIX + key); } catch {}
}

/**
 * TTL süresi içinde cache'ten döner; süre dolmuş veya hiç yoksa fetcher'ı
 * çalıştırıp sonucu cache'ler. Aynı key için eşzamanlı çağrılar tek bir
 * fetcher çalıştırır (in-flight tekilleştirme) — ör. iki bileşen aynı
 * anda aynı kullanıcı profilini isterse Firestore'a tek istek gider.
 */
export async function getOrFetch<T>(
  key: string,
  ttlMs: number,
  fetcher: () => Promise<T>,
  persist = false,
): Promise<T> {
  const cached = cacheGet<T>(key, ttlMs);
  if (cached !== null) return cached;

  const pending = inFlight.get(key);
  if (pending) return pending as Promise<T>;

  const p = fetcher()
    .then(value => {
      cacheSet(key, value, persist);
      inFlight.delete(key);
      return value;
    })
    .catch(err => {
      inFlight.delete(key);
      throw err;
    });
  inFlight.set(key, p);
  return p;
}

/**
 * Aynı yazma işleminin minIntervalMs içinde tekrarını engeller.
 * true dönerse işlem yapılabilir (ve otomatik "yapıldı" damgası basılır),
 * false dönerse arayan taraf yazmayı tamamen atlamalıdır.
 * Örn: setPresence('uid', true) art arda 3 kez tetiklense bile
 * Supabase'e tek upsert gider.
 */
export function shouldWrite(key: string, minIntervalMs: number): boolean {
  const last = mem.get(key)?.ts ?? 0;
  if (Date.now() - last < minIntervalMs) return false;
  mem.set(key, { value: true, ts: Date.now() });
  return true;
}
