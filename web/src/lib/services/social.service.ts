// Android sosyal işlemler: beğeni, kaydetme, takip — Supabase tabanlı
import { supabase } from '$lib/supabase/config';

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
}

// ── Beğenenleri yükle ────────────────────────────────────────────────────────
export async function fetchLikers(
  postId: string,
  sort:   'new' | 'old' | 'mixed' = 'new',
): Promise<{ uid: string; name: string; photoURL: string }[]> {
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
}

// ── Takip durumu ─────────────────────────────────────────────────────────────
export async function isFollowing(fromUid: string, targetUid: string): Promise<boolean> {
  const { data } = await supabase
    .from('follows')
    .select('id')
    .eq('from_uid', fromUid)
    .eq('target_uid', targetUid)
    .maybeSingle();
  return !!data;
}

// ── Okunmamış sayaçları ──────────────────────────────────────────────────────
export async function fetchUnreadCounts(uid: string): Promise<{
  notifs:   number;
  messages: number;
}> {
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
}

// ── Takip edilenlerin UID listesi ─────────────────────────────────────────────
export async function fetchFollowingIds(uid: string): Promise<Set<string>> {
  const { data } = await supabase
    .from('follows')
    .select('target_uid')
    .eq('from_uid', uid);
  return new Set((data ?? []).map((r: any) => r.target_uid as string));
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
  const { data } = await supabase
    .from('users')
    .select('uid, display_name, photo_url, bio')
    .order('created_at', { ascending: false })
    .limit(200);

  const candidates = (data ?? []).filter((r: any) =>
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
