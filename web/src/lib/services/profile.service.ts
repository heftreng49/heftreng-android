// Android ProfileViewModel karşılığı
// profile/[uid]/+page.svelte içindeki tüm Firestore/Supabase çağrıları buraya taşındı

import {
  doc, getDoc, getDocs, updateDoc, setDoc, deleteDoc, addDoc,
  collection, query, where, orderBy, limit, startAfter,
  increment, serverTimestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { db, storage } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import type { User } from '$lib/models/user';
import type { Post } from '$lib/models/post';

// ── Tipler ──────────────────────────────────────────────────────────────────

export interface ProfileUser extends Partial<User> {
  uid: string;
  isPrivate?: boolean;
}

export interface ProfilePost extends Partial<Post> {
  id: string;
  likesCount: number;
  isLikedByMe: boolean;
  isSavedByMe: boolean;
}

export interface SocialCounts {
  followersCount: number;
  followingCount: number;
  postsCount: number;
}

export interface FollowEntry {
  from_uid?: string;
  from_name?: string;
  from_photo?: string;
  target_uid?: string;
  target_name?: string;
  target_photo?: string;
}

export interface LibraryBook {
  id: string;
  title: string;
  author_name: string;
  cover_img: string;
  publish_year?: number;
  synopsis?: string;
  genre?: string;
  page_count?: number;
}

export interface NewBookPayload {
  title: string;
  synopsis: string;
  genre: string;
  cover_img: string;
  publish_year?: number;
  page_count?: number;
  author_uid: string;
  author_name: string;
}

const PAGE = 15;

// ── Kullanıcı profili yükle ─────────────────────────────────────────────────

export async function fetchUser(uid: string): Promise<ProfileUser | null> {
  const snap = await getDoc(doc(db, 'users', uid));
  if (!snap.exists()) return null;
  return { uid: snap.id, ...(snap.data() as Partial<User>) };
}

// ── Sosyal sayılar ──────────────────────────────────────────────────────────

export async function fetchSocialCounts(uid: string): Promise<SocialCounts> {
  try {
    const snap = await getDoc(doc(db, 'users', uid));
    if (snap.exists()) {
      const d = snap.data();
      return {
        followersCount: d.followersCount ?? 0,
        followingCount: d.followingCount ?? 0,
        postsCount: d.postsCount ?? 0,
      };
    }
  } catch (_) {
    // Firestore başarısız olursa Supabase'den say
    const [fR, gR] = await Promise.all([
      supabase.from('follows').select('id', { count: 'exact', head: true }).eq('target_uid', uid),
      supabase.from('follows').select('id', { count: 'exact', head: true }).eq('from_uid', uid),
    ]);
    return {
      followersCount: (fR as any).count ?? 0,
      followingCount: (gR as any).count ?? 0,
      postsCount: 0,
    };
  }
  return { followersCount: 0, followingCount: 0, postsCount: 0 };
}

// ── Takip durumu kontrol ─────────────────────────────────────────────────────

export async function checkFollowStatus(
  fromUid: string,
  targetUid: string,
  isPrivate: boolean,
): Promise<{ isFollowing: boolean; followRequestStatus: 'none' | 'pending' }> {
  const { data } = await supabase
    .from('follows')
    .select('id')
    .eq('from_uid', fromUid)
    .eq('target_uid', targetUid)
    .maybeSingle();

  const isFollowing = !!data;
  let followRequestStatus: 'none' | 'pending' = 'none';

  if (isPrivate && !isFollowing) {
    const reqSnap = await getDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid));
    followRequestStatus = reqSnap.exists() ? 'pending' : 'none';
  }

  return { isFollowing, followRequestStatus };
}

// ── Takip et / bırak ────────────────────────────────────────────────────────

export async function followUser(
  fromUid: string,
  fromName: string,
  fromPhoto: string,
  targetUid: string,
  targetName: string,
  targetPhoto: string,
): Promise<void> {
  const id = `${fromUid}_${targetUid}`;
  await supabase.from('follows').upsert({
    id,
    from_uid: fromUid,
    from_name: fromName,
    from_photo: fromPhoto,
    target_uid: targetUid,
    target_name: targetName,
    target_photo: targetPhoto,
  });
  await Promise.all([
    updateDoc(doc(db, 'users', targetUid), { followersCount: increment(1) }),
    updateDoc(doc(db, 'users', fromUid), { followingCount: increment(1) }),
  ]);
}

export async function unfollowUser(fromUid: string, targetUid: string): Promise<void> {
  await supabase.from('follows').delete().eq('from_uid', fromUid).eq('target_uid', targetUid);
  await Promise.all([
    updateDoc(doc(db, 'users', targetUid), { followersCount: increment(-1) }),
    updateDoc(doc(db, 'users', fromUid), { followingCount: increment(-1) }),
  ]);
}

// ── Gizli hesap: takip isteği gönder / iptal ────────────────────────────────

export async function sendFollowRequest(
  fromUid: string,
  fromName: string,
  fromPhoto: string,
  targetUid: string,
): Promise<void> {
  await setDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid), {
    fromUid,
    fromName,
    fromPhoto,
    targetUid,
    ts: serverTimestamp(),
  });
  await addDoc(collection(db, 'userNotifs', targetUid, 'msgs'), {
    fromUid,
    fromName,
    fromPhoto,
    type: 'follow_request',
    feedId: '',
    postId: '',
    title: `${fromName} seni takip etmek istiyor`,
    sub: '',
    ico: 'person_add',
    message: `${fromName} seni takip etmek istiyor`,
    url: '',
    read: false,
    ts: serverTimestamp(),
  });
}

export async function cancelFollowRequest(fromUid: string, targetUid: string): Promise<void> {
  await deleteDoc(doc(db, 'followRequests', targetUid, 'pending', fromUid));
}

// ── Gönderileri yükle (sayfalı) ─────────────────────────────────────────────

export async function fetchUserPosts(
  uid: string,
  currentUid?: string,
): Promise<{ posts: ProfilePost[]; lastDoc: any; hasMore: boolean }> {
  const q = query(
    collection(db, 'feed'),
    where('uid', '==', uid),
    orderBy('ts', 'desc'),
    limit(PAGE),
  );
  const snap = await getDocs(q);
  const rawPosts = snap.docs.map(d => ({ id: d.id, ...(d.data() as Partial<Post>) }));
  const lastDoc = snap.docs[snap.docs.length - 1] ?? null;
  const hasMore = snap.docs.length === PAGE;

  const enriched = await enrichPosts(rawPosts, currentUid);
  return { posts: enriched, lastDoc, hasMore };
}

export async function fetchMoreUserPosts(
  uid: string,
  lastDoc: any,
  currentUid?: string,
): Promise<{ posts: ProfilePost[]; lastDoc: any; hasMore: boolean }> {
  const q = query(
    collection(db, 'feed'),
    where('uid', '==', uid),
    orderBy('ts', 'desc'),
    startAfter(lastDoc),
    limit(PAGE),
  );
  const snap = await getDocs(q);
  const rawPosts = snap.docs.map(d => ({ id: d.id, ...(d.data() as Partial<Post>) }));
  const newLastDoc = snap.docs[snap.docs.length - 1] ?? lastDoc;
  const hasMore = snap.docs.length === PAGE;

  const enriched = await enrichPosts(rawPosts, currentUid);
  return { posts: enriched, lastDoc: newLastDoc, hasMore };
}

async function enrichPosts(
  posts: any[],
  currentUid?: string,
): Promise<ProfilePost[]> {
  if (!posts.length) return [];
  const ids = posts.map(p => p.id);

  const [lR] = await Promise.all([
    supabase.from('feed_likes').select('post_id').in('post_id', ids),
  ]);
  const counts: Record<string, number> = {};
  for (const r of (lR.data ?? [])) counts[r.post_id] = (counts[r.post_id] ?? 0) + 1;

  let liked = new Set<string>();
  let saved = new Set<string>();
  if (currentUid) {
    const [likedR, savedR] = await Promise.all([
      supabase.from('feed_likes').select('post_id').eq('uid', currentUid).in('post_id', ids),
      supabase.from('feed_saves').select('post_id').eq('uid', currentUid).in('post_id', ids),
    ]);
    liked = new Set((likedR.data ?? []).map((r: any) => r.post_id));
    saved = new Set((savedR.data ?? []).map((r: any) => r.post_id));
  }

  return posts.map(p => ({
    ...p,
    likesCount: counts[p.id] ?? p.likesCount ?? 0,
    isLikedByMe: liked.has(p.id),
    isSavedByMe: saved.has(p.id),
  }));
}

// ── Beğeni toggle (profil kartından) ────────────────────────────────────────

export async function togglePostLike(
  postId: string,
  currentUid: string,
  displayName: string,
  photoURL: string,
  isLiked: boolean,
): Promise<void> {
  const id = `${postId}_${currentUid}`;
  if (isLiked) {
    await supabase.from('feed_likes').delete().eq('post_id', postId).eq('uid', currentUid);
  } else {
    await supabase.from('feed_likes').upsert({
      id, post_id: postId, uid: currentUid, name: displayName, photo_url: photoURL,
    });
  }
}

// ── Kaydet toggle ───────────────────────────────────────────────────────────

export async function togglePostSave(
  postId: string,
  currentUid: string,
  isSaved: boolean,
): Promise<void> {
  const id = `${postId}_${currentUid}`;
  if (isSaved) {
    await supabase.from('feed_saves').delete().eq('id', id);
  } else {
    await supabase.from('feed_saves').upsert({ id, post_id: postId, uid: currentUid });
  }
}

// ── Gönderi sil ─────────────────────────────────────────────────────────────

export async function deletePost(postId: string): Promise<void> {
  await deleteDoc(doc(db, 'feed', postId));
}

// ── Gönderi düzenle ─────────────────────────────────────────────────────────

export async function updatePostText(
  postId: string,
  title: string,
  text: string,
): Promise<void> {
  await updateDoc(doc(db, 'feed', postId), { text: text.trim(), title: title.trim() });
}

export async function updatePostQuote(
  postId: string,
  quoteText: string,
  bookName: string,
  authorName: string,
): Promise<void> {
  const updates: any = { quoteText: quoteText.trim() };
  if (bookName.trim())   updates.bookName   = bookName.trim();
  if (authorName.trim()) updates.authorName = authorName.trim();
  await updateDoc(doc(db, 'feed', postId), updates);
}

// ── Profil düzenle ──────────────────────────────────────────────────────────

export async function checkUsernameAvailable(username: string, uid: string): Promise<boolean> {
  const { data } = await supabase
    .from('users')
    .select('uid')
    .eq('username', username.trim())
    .neq('uid', uid)
    .maybeSingle();
  return !data; // true = müsait
}

export async function saveProfileEdit(
  uid: string,
  fields: { displayName: string; username: string; bio: string; website: string },
): Promise<void> {
  await updateDoc(doc(db, 'users', uid), {
    displayName: fields.displayName.trim(),
    username: fields.username.trim().toLowerCase(),
    bio: fields.bio.trim(),
    website: fields.website.trim(),
  });
  await supabase.from('users').upsert({
    uid,
    username: fields.username.trim().toLowerCase(),
    display_name: fields.displayName.trim(),
    bio: fields.bio.trim(),
    website: fields.website.trim(),
  });
}

// ── Fotoğraf yükle ──────────────────────────────────────────────────────────

export async function uploadProfilePhoto(
  uid: string,
  file: File,
  type: 'avatar' | 'cover',
): Promise<string> {
  const folder = type === 'avatar' ? 'avatars' : 'covers';
  const path = `${folder}/${uid}/${Date.now()}.jpg`;
  const storageRef = ref(storage, path);
  await uploadBytes(storageRef, file);
  const url = await getDownloadURL(storageRef);
  const field = type === 'avatar' ? 'photoURL' : 'coverPhoto';
  await updateDoc(doc(db, 'users', uid), { [field]: url });
  return url;
}

// ── Takipçi / Takip listesi ─────────────────────────────────────────────────

export async function fetchFollowers(uid: string): Promise<FollowEntry[]> {
  const { data } = await supabase
    .from('follows')
    .select('from_uid, from_name, from_photo')
    .eq('target_uid', uid)
    .order('created_at', { ascending: false })
    .limit(100);
  return data ?? [];
}

export async function fetchFollowing(uid: string): Promise<FollowEntry[]> {
  const { data } = await supabase
    .from('follows')
    .select('target_uid, target_name, target_photo')
    .eq('from_uid', uid)
    .order('created_at', { ascending: false })
    .limit(100);
  return data ?? [];
}

// ── Okuma listesi ───────────────────────────────────────────────────────────

export async function fetchReadingList(uid: string): Promise<Record<string, any[]>> {
  const { data } = await supabase.from('reading_list').select('*').eq('uid', uid);
  const grouped: Record<string, any[]> = {};
  for (const r of data ?? []) {
    (grouped[r.status] ??= []).push(r);
  }
  return grouped;
}

// ── Kütüphane kitapları ──────────────────────────────────────────────────────

export async function fetchLibraryBooks(authorUid: string): Promise<LibraryBook[]> {
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_name, cover_img, publish_year, synopsis, genre, page_count')
    .eq('author_uid', authorUid)
    .order('created_at', { ascending: false });
  return data ?? [];
}

export async function addLibraryBook(payload: NewBookPayload): Promise<LibraryBook> {
  const row: any = {
    title: payload.title,
    synopsis: payload.synopsis,
    genre: payload.genre,
    cover_img: payload.cover_img,
    author_uid: payload.author_uid,
    author_name: payload.author_name,
  };
  if (payload.publish_year) row.publish_year = payload.publish_year;
  if (payload.page_count)   row.page_count   = payload.page_count;

  const { data, error } = await supabase.from('library_books').insert(row).select().single();
  if (error) throw error;
  return data;
}

// ── Profil sayfasından alıntı paylaş ────────────────────────────────────────

export interface ShareQuotePayload {
  currentUid: string;
  displayName: string;
  photoURL: string;
  email: string;
  quoteText: string;
  bookName: string;
  authorName: string;
  coverImg: string;
  bookId: string;
}

export async function shareQuoteFromProfile(payload: ShareQuotePayload): Promise<void> {
  await addDoc(collection(db, 'feed'), {
    uid:           payload.currentUid,
    displayName:   payload.displayName,
    name:          payload.displayName,
    username:      '',
    photoURL:      payload.photoURL,
    authorEmail:   payload.email,
    text:          '',
    title:         '',
    category:      '',
    imgUrl:        '',
    imageURL:      '',
    quoteText:     payload.quoteText.trim(),
    bookName:      payload.bookName,
    authorName:    payload.authorName,
    coverImg:      payload.coverImg,
    libraryBookId: payload.bookId,
    type:          'library_quote',
    visibility:    'public',
    mentions:      [],
    likes: 0, saves: 0, cmtCount: 0, reposts: 0,
    ts: serverTimestamp(),
  });
}
