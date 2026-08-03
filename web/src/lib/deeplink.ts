/**
 * deeplink.ts — Heftreng web ↔ Android derin bağlantı yardımcısı.
 *
 * Web URL'si aynı zamanda Android App Link olarak çalışır:
 *   https://heftreng.web.app/post/ID  →  uygulama yüklüyse doğrudan açar
 *   https://heftreng.web.app/post/ID  →  uygulama yoksa web'i gösterir
 *
 * KURULUM NOTU:
 *   Android tarafında AndroidManifest.xml'e https App Link eklenmeli (aşağıda belirtildi).
 *   web/public/.well-known/assetlinks.json dosyası Firebase Hosting'de yayınlanmalı.
 *   firebase.json'a /.well-known/ için header eklenmeli (aşağıda).
 */

/** Uygulamanın Firebase Hosting adresi */
export const WEB_BASE = 'https://heftreng.web.app';

/** Web URL'sinden Android deep link URL'si üret (paylaşım için) */
export function shareUrl(path: string): string {
  // path örn: "/post/abc123" veya "/profile/uid"
  return `${WEB_BASE}${path}`;
}

/** navigator.share varsa native paylaşım, yoksa clipboard */
export async function shareContent(opts: {
  title: string;
  text?: string;
  path: string;
}): Promise<'shared' | 'copied' | 'error'> {
  const url = shareUrl(opts.path);
  if (typeof navigator === 'undefined') return 'error';

  if (navigator.share) {
    try {
      await navigator.share({ title: opts.title, text: opts.text, url });
      return 'shared';
    } catch {
      // kullanıcı iptal etti veya hata — clipboard'a düş
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    return 'copied';
  } catch {
    return 'error';
  }
}

/**
 * Sayfaya göre OG/meta etiket içeriği üret.
 * SvelteKit'te +page.svelte içinde <svelte:head> ile kullan.
 */
export function buildMeta(opts: {
  title: string;
  description?: string;
  image?: string;
  path: string;
  type?: 'article' | 'profile' | 'website';
}) {
  return {
    title:       `${opts.title} — Heftreng`,
    description: opts.description ?? 'Heftreng — Kürtçe ve Türkçe sosyal platform.',
    url:         shareUrl(opts.path),
    image:       opts.image ?? `${WEB_BASE}/og-default.png`,
    type:        opts.type ?? 'website',
  };
}
