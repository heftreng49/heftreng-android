/**
 * Zaman damgasını "şimdi", "5dk", "3sa", "2g", "1hf", "3ay" formatına çevirir.
 * Firebase Timestamp { seconds } ve ISO string her ikisini de destekler.
 * 4 sayfada kopyalanmış ago() fonksiyonunun tek kaynağı.
 */
export function ago(ts: unknown): string {
  let ms = 0;
  if (!ts) return '';
  if (typeof ts === 'object' && ts !== null && 'seconds' in ts) {
    ms = (ts as { seconds: number }).seconds * 1000;
  } else if (typeof ts === 'string' || typeof ts === 'number') {
    ms = new Date(ts).getTime();
  }
  if (!ms) return '';

  const diff = Date.now() - ms;
  const m  = Math.floor(diff / 60_000);
  const h  = Math.floor(diff / 3_600_000);
  const d  = Math.floor(diff / 86_400_000);
  const w  = Math.floor(d / 7);
  const mo = Math.floor(d / 30);

  if (m  <  1) return 'şimdi';
  if (m  < 60) return `${m}dk`;
  if (h  < 24) return `${h}sa`;
  if (d  <  7) return `${d}g`;
  if (w  <  4) return `${w}hf`;
  return `${mo}ay`;
}
