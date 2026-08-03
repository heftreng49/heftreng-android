// Android sosyal işlemler: beğeni, kaydetme, takip — Supabase tabanlı
import { supabase } from '$lib/supabase/config';
import { getOrFetch, cacheDelete } from '$lib/utils/cache';
import { invalidatePostInteractions } from './feed.service';

// /feed sayfası her açıldığında (uygulamanın ana ekranı) bu dosyadaki
// fetchUnreadCounts/fetchFollowingIds/fetchSuggestedUsers/isFollowing
// tetikleniyordu — hepsi TTL cache'e alındı. fetchSuggestedUsers özellikle
// 200 satır çeken ağır bir sorgu olduğundan daha uzun TTL kullanıyor.
const UNREAD_TTL_MS     = 10_000;
const FOLLOWING_TTL_MS  = 60_000;
const IS_FOLLOW_TTL_MS  = 30_000;
const SUGGESTED_TTL_MS  = 5 * 60_000;

// ── Beğeni toggle ────────────────────────────────────────────────────────────
export async function toggleLike(
  postId: string,
  uid:    string,
  name:   string,
  photo:  string,
  liked:  boolean,
): Promise<void> {
  if (liked) {
    await supabase.from('feed_likes').delete()
      .eq('post_id', postId).eq('uid', uid);
  } else {
    await supabase.from('feed_likes').upsert({
      id:        `${postId}_${uid}`,
      post_id:   postId,
      uid,
      name,
      photo_url: photo,
    });
  }
  invalidatePostInteractions(postId, uid);
}

// ── Kaydet toggle ────────────────────────────────────────────────────────────
export async function toggleSave(
  postId: string,
  uid:    string,
  saved:  boolean,
): Promise<void> {
  if (saved) {
    await supabase.from('feed_saves').delete()
      .eq('post_id', postId).eq('uid', uid);
  } else {
    await supabase.from('feed_saves').upsert({
      id:      `${postId}_${uid}`,
      post_id: postId,
      uid,
    });
  }
  invalidatePostInteractions(postId, uid);
}

// ── Beğenenleri yükle ────────────────────────────────────────────────────────
export async function fetchLikers(
  postId: string,
  sort:   'new' | 'old' | 'mixed' = 'new',
): Promise<{ uid: string; name: string; photoURL: string }[]> {
  return getOrFetch(`likers_${postId}_${sort}`, 15_000, async () => {
    const order = sort === 'old' ? { ascending: true } : { ascending: false };
    const { data } = await supabase
      .from('feed_likes')
      .select('uid, name, photo_url, created_at')
      .eq('post_id', postId)
      .order('created_at', order);

    return (data ?? []).map((r: { uid: string; name: string; photo_url: string }) => ({
      uid:      r.uid,
      name:     r.name,
      photoURL: r.photo_url,
    }));
  });
}

// ── Takip toggle ─────────────────────────────────────────────────────────────
export async function toggleFollow(
  fromUid:    string,
  fromName:   string,
  fromPhoto:  string,
  targetUid:  string,
  targetName: string,
  targetPhoto:string,
  following:  boolean,
): Promise<void> {
  if (following) {
    await supabase.from('follows').delete()
      .eq('from_uid', fromUid).eq('target_uid', targetUid);
  } else {
    await supabase.from('follows').upsert({
      id:           `${fromUid}_${targetUid}`,
      from_uid:     fromUid,
      from_name:    fromName,
      from_photo:   fromPhoto,
      target_uid:   targetUid,
      target_name:  targetName,
      target_photo: targetPhoto,
    });
  }
  cacheDelete(`following_ids_${fromUid}`);
  cacheDelete(`is_following_${fromUid}_${targetUid}`);
}

// ── Takip durumu ─────────────────────────────────────────────────────────────
export async function isFollowing(fromUid: string, targetUid: string): Promise<boolean> {
  return getOrFetch(`is_following_${fromUid}_${targetUid}`, IS_FOLLOW_TTL_MS, async () => {
    const { data } = await supabase
      .from('follows')
      .select('id')
      .eq('from_uid', fromUid)
      .eq('target_uid', targetUid)
      .maybeSingle();
    return !!data;
  });
}

// ── Okunmamış sayaçları ──────────────────────────────────────────────────────
export async function fetchUnreadCounts(uid: string): Promise<{
  notifs:   number;
  messages: number;
}> {
  return getOrFetch(`unread_counts_${uid}`, UNREAD_TTL_MS, async () => {
    const [nR, mR] = await Promise.all([
      supabase.from('notifications').select('id', { count: 'exact', head: true })
        .eq('to_uid', uid).eq('is_read', false),
      supabase.from('messages').select('id', { count: 'exact', head: true })
        .eq('to_uid', uid).eq('is_read', false),
    ]);
    return {
      notifs:   nR.count ?? 0,
      messages: mR.count ?? 0,
    };
  });
}

// ── Takip edilenlerin UID listesi ─────────────────────────────────────────────
export async function fetchFollowingIds(uid: string): Promise<Set<string>> {
  const cached = await getOrFetch(`following_ids_${uid}`, FOLLOWING_TTL_MS, async () => {
    const { data } = await supabase
      .from('follows')
      .select('target_uid')
      .eq('from_uid', uid);
    return (data ?? []).map((r: any) => r.target_uid as string);
  });
  return new Set(cached);
}

// ── Önerilen kullanıcılar (takip edilmeyenler) ───────────────────────────────
export interface SuggestedUser {
  uid:       string;
  name:      string;
  photoURL:  string;
  bio:       string;
  isFollowing: boolean;
}

export async function fetchSuggestedUsers(
  currentUid: string,
  excludeUids: Set<string>,
  count = 8,
): Promise<SuggestedUser[]> {
  // Ağır olan 200 satırlık ham sorgu cache'lenir (5dk); dışlama/karıştırma
  // her çağrıda taze uygulanır çünkü excludeUids çağrıdan çağrıya değişebilir.
  const rows = await getOrFetch(`suggested_users_raw_${currentUid}`, SUGGESTED_TTL_MS, async () => {
    const { data } = await supabase
      .from('users')
      .select('uid, display_name, photo_url, bio')
      .order('created_at', { ascending: false })
      .limit(200);
    return data ?? [];
  });

  const candidates = rows.filter((r: any) =>
    r.uid && r.display_name && !excludeUids.has(r.uid as string)
  );
  const shuffled = candidates.sort(() => Math.random() - 0.5).slice(0, count);
  return shuffled.map((r: any) => ({
    uid:        r.uid,
    name:       r.display_name ?? '',
    photoURL:   r.photo_url ?? '',
    bio:        r.bio ?? '',
    isFollowing: false,
  }));
}
