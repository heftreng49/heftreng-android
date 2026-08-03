// Android ProfileRepository karşılığı — Firestore users + Supabase follows
import {
  doc, getDoc, updateDoc, setDoc,
  collection, query, where, orderBy, limit,
  getDocs, startAfter, increment, deleteDoc,
  addDoc, serverTimestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { db, storage } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import type { User } from '$lib/models/user';
import type { Post } from '$lib/models/post';
import { getOrFetch, cacheDelete } from '$lib/utils/cache';
import { invalidateProfileCache as invalidateMsgProfileCache } from './message.service';

const PAGE = 15;
// Profil ekranı (kendi profilin + başkalarının profili) sık ziyaret edilir;
// bu alanlar dakikalarca aynı kalır, TTL cache egress'i belirgin azaltır.
const PROFILE_TTL_MS      = 60_000;
const SOCIAL_COUNTS_TTL_MS = 30_000;
const FOLLOW_STATUS_TTL_MS = 30_000;
const FOLLOW_LIST_TTL_MS   = 30_000;

// ── Profil getir ─────────────────────────────────────────────────────────────
export async function fetchProfile(uid: string): Promise<User | null> {
  return getOrFetch(`profile_${uid}`, PROFILE_TTL_MS, async () => {
    const snap = await getDoc(doc(db, 'users', uid));
    if (!snap.exists()) return null;
    return { uid: snap.id, ...snap.data() } as User;
  });
}

// ── Sosyal sayaçlar ──────────────────────────────────────────────────────────
export async function fetchSocialCounts(uid: string): Promise<{ followers: number; following: number; posts: number }> {
  return getOrFetch(`social_counts_${uid}`, SOCIAL_COUNTS_TTL_MS, async () => {
    try {
      const snap = await getDoc(doc(db, 'users', uid));
      if (snap.exists()) {
        const d = snap.data();
        return { followers: d.followersCount ?? 0, following: d.followingCount ?? 0, posts: d.postsCount ?? 0 };
      }
    } catch {}
    const [fR, gR] = await Promise.all([
      supabase.from('follows').select('id', { count: 'exact', head: true }).eq('target_uid', uid),
      supabase.from('follows').select('id', { count: 'exact', head: true }).eq('from_uid', uid),
    ]);
    return { followers: fR.count ?? 0, following: gR.count ?? 0, posts: 0 };
  });
}

// ── Takip durumu + gizli hesap isteği ────────────────────────────────────────
export async function checkFollowStatus(fromUid: string, targetUid: string, isPrivate: boolean): Promise<{
  isFollowing: boolean;
  followRequestStatus: 'none' | 'pending' | 'accepted';
}> {
  return getOrFetch(`follow_status_${fromUid}_${targetUid}`, FOLLOW_STATUS_TTL_MS, async () => {
    const { data } = await supabase.from('follows')
      .select('id').eq('from_uid', fromUid).eq('target_uid', targetUid).maybeSingle();
    const isFollowing = !!data;

    let followRequestStatus: 'none' | 'pending' | 'accepted' = 'none';
    if (isPrivate && !isFollowing) {
      try {
        const reqSnap = await getDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid));
        followRequestStatus = reqSnap.exists() ? 'pending' : 'none';
      } catch {}
    }
    return { isFollowing, followRequestStatus };
  });
}

// ── Normal takip / bırak ─────────────────────────────────────────────────────
export async function toggleFollow(
  fromUid: string, fromName: string, fromPhoto: string,
  targetUid: string, targetName: string, targetPhoto: string,
  isFollowing: boolean,
): Promise<void> {
  const id = `${fromUid}_${targetUid}`;
  if (isFollowing) {
    await supabase.from('follows').delete().eq('from_uid', fromUid).eq('target_uid', targetUid);
    await Promise.all([
      updateDoc(doc(db, 'users', targetUid), { followersCount: increment(-1) }),
      updateDoc(doc(db, 'users', fromUid),   { followingCount: increment(-1) }),
    ]);
  } else {
    await supabase.from('follows').upsert({ id, from_uid: fromUid, from_name: fromName, from_photo: fromPhoto, target_uid: targetUid, target_name: targetName, target_photo: targetPhoto });
    await Promise.all([
      updateDoc(doc(db, 'users', targetUid), { followersCount: increment(1) }),
      updateDoc(doc(db, 'users', fromUid),   { followingCount: increment(1) }),
    ]);
  }
  cacheDelete(`follow_status_${fromUid}_${targetUid}`);
  cacheDelete(`social_counts_${targetUid}`);
  cacheDelete(`social_counts_${fromUid}`);
}

// ── Gizli hesap: takip isteği gönder / iptal ─────────────────────────────────
export async function sendFollowRequest(fromUid: string, fromName: string, fromPhoto: string, targetUid: string): Promise<void> {
  await setDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid), {
    fromUid, fromName, fromPhoto, targetUid, ts: serverTimestamp(),
  });
  await addDoc(collection(db, 'userNotifs', targetUid, 'msgs'), {
    fromUid, fromName, fromPhoto,
    type: 'follow_request', feedId: '', postId: '',
    title: `${fromName} seni takip etmek istiyor`,
    sub: '', ico: 'person_add',
    message: `${fromName} seni takip etmek istiyor`,
    url: '', read: false, ts: serverTimestamp(),
  });
}

export async function cancelFollowRequest(fromUid: string, targetUid: string): Promise<void> {
  await deleteDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid));
}

// ── Takipçi / Takip listesi ──────────────────────────────────────────────────
export async function fetchFollowers(uid: string): Promise<{ uid: string; name: string; photo: string }[]> {
  return getOrFetch(`followers_${uid}`, FOLLOW_LIST_TTL_MS, async () => {
    const { data } = await supabase.from('follows')
      .select('from_uid,from_name,from_photo').eq('target_uid', uid)
      .order('created_at', { ascending: false }).limit(100);
    return (data ?? []).map((r: any) => ({ uid: r.from_uid, name: r.from_name, photo: r.from_photo }));
  });
}

export async function fetchFollowing(uid: string): Promise<{ uid: string; name: string; photo: string }[]> {
  return getOrFetch(`following_list_${uid}`, FOLLOW_LIST_TTL_MS, async () => {
    const { data } = await supabase.from('follows')
      .select('target_uid,target_name,target_photo').eq('from_uid', uid)
      .order('created_at', { ascending: false }).limit(100);
    return (data ?? []).map((r: any) => ({ uid: r.target_uid, name: r.target_name, photo: r.target_photo }));
  });
}

// ── Profil gönderileri ───────────────────────────────────────────────────────
export async function fetchUserPosts(uid: string, lastDoc?: unknown): Promise<{
  posts: Post[]; lastDoc: unknown; hasMore: boolean;
}> {
  const constraints: any[] = [
    where('uid', '==', uid), orderBy('ts', 'desc'), limit(PAGE),
  ];
  if (lastDoc) constraints.push(startAfter(lastDoc));
  const snap = await getDocs(query(collection(db, 'feed'), ...constraints));
  return {
    posts: snap.docs.map(d => ({ id: d.id, ...d.data() } as Post)),
    lastDoc: snap.docs[snap.docs.length - 1] ?? lastDoc,
    hasMore: snap.docs.length === PAGE,
  };
}

export async function enrichPostsWithInteractions(ids: string[], uid?: string): Promise<{
  likeCounts: Record<string, number>;
  likedIds: Set<string>;
  savedIds: Set<string>;
}> {
  if (!ids.length) return { likeCounts: {}, likedIds: new Set(), savedIds: new Set() };
  const [lR] = await Promise.all([
    supabase.from('feed_likes').select('post_id').in('post_id', ids),
  ]);
  const likeCounts: Record<string, number> = {};
  for (const r of lR.data ?? []) likeCounts[r.post_id] = (likeCounts[r.post_id] ?? 0) + 1;

  let likedIds = new Set<string>();
  let savedIds = new Set<string>();
  if (uid) {
    const [lR2, sR] = await Promise.all([
      supabase.from('feed_likes').select('post_id').eq('uid', uid).in('post_id', ids),
      supabase.from('feed_saves').select('post_id').eq('uid', uid).in('post_id', ids),
    ]);
    likedIds = new Set((lR2.data ?? []).map((r: any) => r.post_id));
    savedIds = new Set((sR.data ?? []).map((r: any) => r.post_id));
  }
  return { likeCounts, likedIds, savedIds };
}

// ── Okuma listesi ─────────────────────────────────────────────────────────────
export async function fetchReadingList(uid: string): Promise<Record<string, any[]>> {
  const { data } = await supabase.from('reading_list').select('*').eq('uid', uid);
  const grouped: Record<string, any[]> = {};
  for (const r of data ?? []) (grouped[r.status] ??= []).push(r);
  return grouped;
}

// ── Profil güncelle ──────────────────────────────────────────────────────────
export async function updateProfile(uid: string, data: Partial<User>): Promise<void> {
  await updateDoc(doc(db, 'users', uid), data as Record<string, unknown>);
  cacheDelete(`profile_${uid}`);
  invalidateMsgProfileCache(uid); // konuşma listesindeki ad/foto cache'i de bayat kalmasın
}

export async function checkUsernameAvailable(username: string, excludeUid: string): Promise<boolean> {
  const { data } = await supabase.from('users').select('uid')
    .eq('username', username.trim()).neq('uid', excludeUid).maybeSingle();
  return !data;
}

export async function syncUsernameToSupabase(uid: string, username: string, displayName: string, photoURL: string): Promise<void> {
  await supabase.from('users').upsert({ uid, username, display_name: displayName, photo_url: photoURL });
}

// ── Fotoğraf yükle ───────────────────────────────────────────────────────────
export async function uploadAvatar(uid: string, file: File): Promise<string> {
  const r = ref(storage, `avatars/${uid}/profile.jpg`);
  await uploadBytes(r, file);
  return getDownloadURL(r);
}

export async function uploadCoverPhoto(uid: string, file: File): Promise<string> {
  const r = ref(storage, `covers/${uid}/cover.jpg`);
  await uploadBytes(r, file);
  return getDownloadURL(r);
}

// ── Yeni kullanıcı belgesi oluştur ───────────────────────────────────────────
export async function createUserDoc(uid: string, data: Partial<User>): Promise<void> {
  await setDoc(doc(db, 'users', uid), {
    uid, displayName: '', name: '', username: '', email: '',
    photoURL: '', coverPhoto: '', bio: '', website: '',
    followersCount: 0, followingCount: 0, postsCount: 0,
    level: 1, xp: 0, streak: 0, booksRead: 0, quotesShared: 0,
    banned: false, emailVerified: false, isPrivate: false,
    messagePermission: 'everyone', createdAt: Date.now(),
    ...data,
  });
}
