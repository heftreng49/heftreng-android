// Android MentionHelper / searchMentionUsers karşılığı
// Compose ve yorum alanında @ yazıldığında kullanıcı önerileri getirir.
import { supabase } from '$lib/supabase/config';
import { getOrFetch } from '$lib/utils/cache';

export interface MentionUser {
  uid:      string;
  name:     string;
  username: string;
  photoURL: string;
}

const MENTION_TTL_MS = 15_000;

// ── @ ile eşleşen kullanıcıları ara (username veya displayName prefix) ───────
export async function searchMentionUsers(query: string): Promise<MentionUser[]> {
  const q = query.trim().toLowerCase();
  if (!q || q.length < 1) return [];

  return getOrFetch(`mention_${q}`, MENTION_TTL_MS, async () => {
    // Supabase users tablosunda username ILIKE veya display_name ILIKE
    const { data } = await supabase
      .from('users')
      .select('uid, display_name, username, photo_url')
      .or(`username.ilike.${q}%,display_name.ilike.${q}%`)
      .limit(8);

    return (data ?? []).map((r: any) => ({
      uid:      r.uid,
      name:     r.display_name ?? '',
      username: r.username ?? '',
      photoURL: r.photo_url ?? '',
    }));
  });
}

// ── Metin içindeki @mention'ları çıkar ──────────────────────────────────────
export function extractMentions(text: string): string[] {
  const matches = text.matchAll(/@([a-z0-9_]{2,20})/gi);
  const set = new Set<string>();
  for (const m of matches) set.add(m[1].toLowerCase());
  return [...set];
}

// ── Metin + cursor pozisyonuna göre aktif @ sorgusunu bul ────────────────────
export function getActiveMentionQuery(text: string, cursorPos: number): string | null {
  const before = text.slice(0, cursorPos);
  const match  = before.match(/@([a-z0-9_]*)$/i);
  if (!match) return null;
  return match[1]; // @ sonrası yazılan kısım
}
