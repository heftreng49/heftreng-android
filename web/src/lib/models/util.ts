// ── Firestore Timestamp → milisaniye ───────────────────────────────────────
// Android'deki com.google.firebase.Timestamp karşılığı.
export function tsToMs(ts: unknown): number {
  if (!ts) return 0;
  if (typeof ts === 'object' && ts !== null && 'seconds' in ts) {
    return (ts as { seconds: number }).seconds * 1000;
  }
  return Number(ts);
}

// ── Göreli zaman etiketi ────────────────────────────────────────────────────
export function ago(ts: unknown): string {
  const ms  = tsToMs(ts);
  if (!ms) return '';
  const diff = Date.now() - ms;
  const s = Math.floor(diff / 1000);
  if (s < 60)  return `${s}s`;
  const m = Math.floor(s / 60);
  if (m < 60)  return `${m}d`;
  const h = Math.floor(m / 60);
  if (h < 24)  return `${h}sa`;
  const d = Math.floor(h / 24);
  if (d < 7)   return `${d}g`;
  const w = Math.floor(d / 7);
  if (w < 5)   return `${w}h`;
  const mo = Math.floor(d / 30);
  if (mo < 12) return `${mo}ay`;
  return `${Math.floor(d / 365)}y`;
}

// ── Kısa sayı formatı (1200 → 1.2B) ────────────────────────────────────────
export function shortNum(n: number): string {
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace('.0', '') + 'M';
  if (n >= 1_000)     return (n / 1_000).toFixed(1).replace('.0', '') + 'B';
  return String(n);
}
