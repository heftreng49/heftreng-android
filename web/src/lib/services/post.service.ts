// Android PostDetailViewModel karşılığı
// post/[id]/+page.svelte içindeki tüm Firestore/Supabase çağrıları buraya taşındı

import { doc, getDoc, deleteDoc } from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import type { Post } from '$lib/models/post';

// ── Tipler ──────────────────────────────────────────────────────────────────

export interface PostDetail extends Partial<Post> {
  id: string;
  likesCount: number;
  isLikedByMe: boolean;
  isSavedByMe: boolean;
}

export interface Comment {
  id: string;
  post_id: string;
  uid: string;
  name: string;
  photo_url: string;
  text: string;
  reply_to_cmt_id: string | null;
  likes_count?: number;
  created_at: string;
  isLikedByMe: boolean;
}

export interface Liker {
  uid: string;
  name: string;
  photo_url: string;
  created_at: string;
}

// ── Gönderi yükle ───────────────────────────────────────────────────────────

export async function fetchPostDetail(
  postId: string,
  currentUid?: string,
): Promise<PostDetail | null> {
  const snap = await getDoc(doc(db, 'feed', postId));
  if (!snap.exists()) return null;

  const post = { id: snap.id, ...(snap.data() as Partial<Post>) };

  // Beğeni sayısı + benim durumum
  const [countRes, interRes] = await Promise.all([
    supabase
      .from('feed_likes')
      .select('id', { count: 'exact', head: true })
      .eq('post_id', postId),
    currentUid
      ? supabase
          .from('feed_likes')
          .select('id')
          .eq('post_id', postId)
          .eq('uid', currentUid)
          .maybeSingle()
      : Promise.resolve({ data: null }),
  ]);

  let isSavedByMe = false;
  if (currentUid) {
    const { data: sv } = await supabase
      .from('feed_saves')
      .select('id')
      .eq('post_id', postId)
      .eq('uid', currentUid)
      .maybeSingle();
    isSavedByMe = !!sv;
  }

  return {
    ...post,
    likesCount: (countRes as any).count ?? (post as any).likesCount ?? 0,
    isLikedByMe: !!(interRes as any).data,
    isSavedByMe,
  };
}

// ── Gönderiyi sil ───────────────────────────────────────────────────────────

export async function deletePost(postId: string): Promise<void> {
  await deleteDoc(doc(db, 'feed', postId));
}

// ── Yorumları yükle ─────────────────────────────────────────────────────────

export async function fetchComments(
  postId: string,
  currentUid?: string,
): Promise<Comment[]> {
  const { data: rows } = await supabase
    .from('feed_comments')
    .select('*')
    .eq('post_id', postId)
    .order('created_at', { ascending: true });

  const list = rows ?? [];
  if (!list.length) return [];

  let myLikedIds = new Set<string>();
  if (currentUid) {
    const { data: cl } = await supabase
      .from('comment_likes')
      .select('comment_id')
      .eq('uid', currentUid)
      .in('comment_id', list.map((r: any) => r.id));
    myLikedIds = new Set((cl ?? []).map((r: any) => r.comment_id));
  }

  return list.map((r: any) => ({ ...r, isLikedByMe: myLikedIds.has(r.id) }));
}

// ── Beğeni toggle ───────────────────────────────────────────────────────────

export async function togglePostLike(
  postId: string,
  currentUid: string,
  displayName: string,
  photoURL: string,
  isLiked: boolean,
): Promise<void> {
  const id = `${postId}_${currentUid}`;
  if (isLiked) {
    await supabase
      .from('feed_likes')
      .delete()
      .eq('post_id', postId)
      .eq('uid', currentUid);
  } else {
    await supabase.from('feed_likes').upsert({
      id,
      post_id: postId,
      uid: currentUid,
      name: displayName,
      photo_url: photoURL,
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

// ── Yorum gönder ────────────────────────────────────────────────────────────

export async function addComment(
  postId: string,
  currentUid: string,
  displayName: string,
  photoURL: string,
  text: string,
  replyToCmtId?: string | null,
): Promise<Comment> {
  const { data, error } = await supabase
    .from('feed_comments')
    .insert({
      post_id: postId,
      uid: currentUid,
      name: displayName,
      photo_url: photoURL,
      text: text.trim(),
      reply_to_cmt_id: replyToCmtId ?? null,
    })
    .select()
    .single();
  if (error) throw error;
  return { ...data, isLikedByMe: false };
}

// ── Yorum düzenle ───────────────────────────────────────────────────────────

export async function editComment(
  commentId: string,
  text: string,
): Promise<{ id: string; text: string }> {
  const { data, error } = await supabase
    .from('feed_comments')
    .update({ text: text.trim() })
    .eq('id', commentId)
    .select()
    .single();
  if (error) throw error;
  return data;
}

// ── Yorum sil ───────────────────────────────────────────────────────────────

export async function deleteComment(commentId: string): Promise<void> {
  await supabase.from('feed_comments').delete().eq('id', commentId);
}

// ── Yorum beğeni toggle ─────────────────────────────────────────────────────

export async function toggleCommentLike(
  commentId: string,
  currentUid: string,
  displayName: string,
  photoURL: string,
  isLiked: boolean,
): Promise<void> {
  const id = `${commentId}_${currentUid}`;
  if (isLiked) {
    await supabase
      .from('comment_likes')
      .delete()
      .eq('comment_id', commentId)
      .eq('uid', currentUid);
  } else {
    await supabase.from('comment_likes').upsert({
      id,
      comment_id: commentId,
      uid: currentUid,
      name: displayName,
      photo_url: photoURL,
    });
  }
}

// ── Beğenenleri yükle ───────────────────────────────────────────────────────

export async function fetchPostLikers(postId: string): Promise<Liker[]> {
  const { data } = await supabase
    .from('feed_likes')
    .select('uid,name,photo_url,created_at')
    .eq('post_id', postId)
    .order('created_at', { ascending: false })
    .limit(100);

  // Aynı uid'den gelen tekrarları temizle
  const seen = new Set<string>();
  return (data ?? []).filter((r: any) => {
    if (seen.has(r.uid)) return false;
    seen.add(r.uid);
    return true;
  });
}

// ── Yorum beğenenleri yükle ─────────────────────────────────────────────────

export async function fetchCommentLikers(commentId: string): Promise<Liker[]> {
  const { data } = await supabase
    .from('comment_likes')
    .select('uid,name,photo_url,created_at')
    .eq('comment_id', commentId)
    .order('created_at', { ascending: false })
    .limit(50);
  return data ?? [];
}
