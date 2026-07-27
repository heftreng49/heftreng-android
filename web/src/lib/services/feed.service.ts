// Android FeedRepository karşılığı
// Kural: Hiçbir +page.svelte içinde doğrudan getDocs/supabase.from() olmamalı.
import {
  collection, query, orderBy, limit, getDocs,
  doc, getDoc, addDoc, updateDoc, deleteDoc,
  startAfter, Timestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { db, storage } from '$lib/firebase/config';
import { supabase }    from '$lib/supabase/config';
import type { Post }   from '$lib/models/post';

const PAGE_SIZE = 20;
const FEED_COL  = 'feed';

// ── Sayfa yükle ─────────────────────────────────────────────────────────────
export async function fetchFeedPage(after: unknown = null): Promise<{
  posts:   Post[];
  lastDoc: unknown;
  hasMore: boolean;
}> {
  const constraints: Parameters<typeof query>[1][] = [
    orderBy('ts', 'desc'),
    limit(PAGE_SIZE),
  ];
  if (after) constraints.push(startAfter(after));

  const snap = await getDocs(query(collection(db, FEED_COL), ...constraints));
  const posts = snap.docs.map(d => ({ id: d.id, ...d.data() } as Post));
  return {
    posts,
    lastDoc: snap.docs[snap.docs.length - 1] ?? null,
    hasMore: snap.docs.length === PAGE_SIZE,
  };
}

// ── Tek gönderi ─────────────────────────────────────────────────────────────
export async function fetchPost(id: string): Promise<Post | null> {
  const snap = await getDoc(doc(db, FEED_COL, id));
  if (!snap.exists()) return null;
  return { id: snap.id, ...snap.data() } as Post;
}

// ── Kullanıcı gönderileri (profil) ─────────────────────────────────────────
export async function fetchUserPosts(uid: string, after: unknown = null): Promise<{
  posts:   Post[];
  lastDoc: unknown;
  hasMore: boolean;
}> {
  const { default: fq } = await import('firebase/firestore').then(m => ({ default: m }));
  const constraints: Parameters<typeof query>[1][] = [
    fq.where('uid', '==', uid),
    orderBy('ts', 'desc'),
    limit(PAGE_SIZE),
  ];
  if (after) constraints.push(startAfter(after));
  const snap = await getDocs(query(collection(db, FEED_COL), ...constraints));
  const posts = snap.docs.map(d => ({ id: d.id, ...d.data() } as Post));
  return {
    posts,
    lastDoc: snap.docs[snap.docs.length - 1] ?? null,
    hasMore: snap.docs.length === PAGE_SIZE,
  };
}

// ── Gönderi yayınla ─────────────────────────────────────────────────────────
export async function createPost(data: Omit<Post, 'id' | 'isLikedByMe' | 'isSavedByMe' | 'isRepostedByMe' | 'myRepostId'>): Promise<string> {
  const ref2 = await addDoc(collection(db, FEED_COL), {
    ...data,
    ts: Timestamp.now(),
  });
  return ref2.id;
}

// ── Gönderi güncelle ────────────────────────────────────────────────────────
export async function updatePost(id: string, data: Partial<Post>): Promise<void> {
  await updateDoc(doc(db, FEED_COL, id), data as Record<string, unknown>);
}

// ── Gönderi sil ─────────────────────────────────────────────────────────────
export async function deletePost(id: string): Promise<void> {
  await deleteDoc(doc(db, FEED_COL, id));
}

// ── Resim yükle ─────────────────────────────────────────────────────────────
export async function uploadPostImage(uid: string, file: File): Promise<string> {
  const ext  = file.name.split('.').pop() ?? 'jpg';
  const path = `posts/${uid}/${Date.now()}.${ext}`;
  const snap = await uploadBytes(ref(storage, path), file);
  return getDownloadURL(snap.ref);
}

// ── Enrich: beğeni/yorum sayısı + kişisel durum ────────────────────────────
export async function enrichPosts(posts: Post[], uid: string | null): Promise<Post[]> {
  if (!posts.length) return posts;
  const ids = posts.map(p => p.id);

  const [likeRows, cmtRows] = await Promise.all([
    supabase.from('feed_likes').select('post_id').in('post_id', ids),
    supabase.from('feed_comments').select('post_id').in('post_id', ids),
  ]);

  const likeCounts: Record<string, number> = {};
  for (const r of likeRows.data ?? [])
    likeCounts[r.post_id] = (likeCounts[r.post_id] ?? 0) + 1;

  const cmtCounts: Record<string, number> = {};
  for (const r of cmtRows.data ?? [])
    cmtCounts[r.post_id] = (cmtCounts[r.post_id] ?? 0) + 1;

  let likedSet = new Set<string>();
  let savedSet = new Set<string>();
  if (uid) {
    const [lR, sR] = await Promise.all([
      supabase.from('feed_likes').select('post_id').eq('uid', uid).in('post_id', ids),
      supabase.from('feed_saves').select('post_id').eq('uid', uid).in('post_id', ids),
    ]);
    likedSet = new Set((lR.data ?? []).map((r: { post_id: string }) => r.post_id));
    savedSet = new Set((sR.data ?? []).map((r: { post_id: string }) => r.post_id));
  }

  return posts.map(p => ({
    ...p,
    likesCount:    likeCounts[p.id]  ?? p.likesCount    ?? 0,
    commentsCount: cmtCounts[p.id]   ?? p.commentsCount ?? 0,
    isLikedByMe:   likedSet.has(p.id),
    isSavedByMe:   savedSet.has(p.id),
  }));
}
