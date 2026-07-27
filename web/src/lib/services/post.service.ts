// Android SinglePostScreen / FeedRepository karşılığı
// post/[id]/+page.svelte içindeki tüm doğrudan DB çağrılarını buraya taşıdık
import { doc, getDoc, deleteDoc } from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import type { Post } from '$lib/models/post';
import type { Comment } from '$lib/models/comment';

// ── Gönderi yükle ────────────────────────────────────────────────────────────
export async function fetchPost(postId: string, uid?: string): Promise<{
  post: Post;
  likesCount: number;
  isLikedByMe: boolean;
  isSavedByMe: boolean;
} | null> {
  const snap = await getDoc(doc(db, 'feed', postId));
  if (!snap.exists()) return null;
  const post = { id: snap.id, ...snap.data() } as Post;

  const [countRes, interRes] = await Promise.all([
    supabase.from('feed_likes').select('id', { count: 'exact', head: true }).eq('post_id', postId),
    uid
      ? supabase.from('feed_likes').select('id').eq('post_id', postId).eq('uid', uid).maybeSingle()
      : Promise.resolve({ data: null }),
  ]);
  const likesCount  = countRes.count ?? (post as any).likesCount ?? 0;
  const isLikedByMe = !!interRes.data;

  let isSavedByMe = false;
  if (uid) {
    const { data: sv } = await supabase.from('feed_saves')
      .select('id').eq('post_id', postId).eq('uid', uid).maybeSingle();
    isSavedByMe = !!sv;
  }

  return { post, likesCount, isLikedByMe, isSavedByMe };
}

// ── Gönderiyi sil ────────────────────────────────────────────────────────────
export async function deletePost(postId: string): Promise<void> {
  await deleteDoc(doc(db, 'feed', postId));
}

// ── Yorumları yükle ──────────────────────────────────────────────────────────
export async function fetchComments(postId: string, uid?: string): Promise<Comment[]> {
  const { data } = await supabase
    .from('feed_comments')
    .select('*')
    .eq('post_id', postId)
    .order('created_at', { ascending: true });
  const rows = data ?? [];

  let myLikedIds = new Set<string>();
  if (uid && rows.length) {
    const { data: cl } = await supabase
      .from('comment_likes').select('comment_id')
      .eq('uid', uid)
      .in('comment_id', rows.map((r: any) => r.id));
    myLikedIds = new Set((cl ?? []).map((r: any) => r.comment_id));
  }
  return rows.map((r: any) => ({ ...r, isLikedByMe: myLikedIds.has(r.id) })) as Comment[];
}

// ── Beğeni toggle ─────────────────────────────────────────────────────────────
export async function togglePostLike(
  postId: string,
  uid: string,
  displayName: string,
  photoURL: string,
  isLiked: boolean,
): Promise<void> {
  const id = `${postId}_${uid}`;
  if (isLiked) {
    await supabase.from('feed_likes').delete().eq('post_id', postId).eq('uid', uid);
  } else {
    await supabase.from('feed_likes').upsert({ id, post_id: postId, uid, name: displayName, photo_url: photoURL });
  }
}

// ── Kaydet toggle ─────────────────────────────────────────────────────────────
export async function togglePostSave(postId: string, uid: string, isSaved: boolean): Promise<void> {
  const id = `${postId}_${uid}`;
  if (isSaved) await supabase.from('feed_saves').delete().eq('id', id);
  else await supabase.from('feed_saves').upsert({ id, post_id: postId, uid });
}

// ── Yorum ekle / düzenle ──────────────────────────────────────────────────────
export async function addComment(
  postId: string,
  uid: string,
  name: string,
  photoURL: string,
  text: string,
  replyToId?: string,
): Promise<Comment> {
  const { data, error } = await supabase.from('feed_comments').insert({
    post_id: postId,
    uid, name,
    photo_url: photoURL,
    text,
    reply_to_cmt_id: replyToId ?? null,
  }).select().single();
  if (error) throw error;
  return { ...data, isLikedByMe: false } as Comment;
}

export async function editComment(commentId: string, text: string): Promise<Comment> {
  const { data, error } = await supabase.from('feed_comments')
    .update({ text })
    .eq('id', commentId).select().single();
  if (error) throw error;
  return data as Comment;
}

export async function deleteComment(commentId: string): Promise<void> {
  await supabase.from('feed_comments').delete().eq('id', commentId);
}

// ── Yorum beğeni toggle ───────────────────────────────────────────────────────
export async function toggleCommentLike(
  commentId: string,
  uid: string,
  displayName: string,
  photoURL: string,
  isLiked: boolean,
): Promise<void> {
  const id = `${commentId}_${uid}`;
  if (isLiked) {
    await supabase.from('comment_likes').delete().eq('comment_id', commentId).eq('uid', uid);
  } else {
    await supabase.from('comment_likes').upsert({ id, comment_id: commentId, uid, name: displayName, photo_url: photoURL });
  }
}

// ── Beğenenler listesi ────────────────────────────────────────────────────────
export async function fetchPostLikers(postId: string): Promise<{ uid: string; name: string; photo_url: string; created_at: string }[]> {
  const { data } = await supabase.from('feed_likes')
    .select('uid,name,photo_url,created_at')
    .eq('post_id', postId)
    .order('created_at', { ascending: false })
    .limit(100);
  const seen = new Set<string>();
  return (data ?? []).filter((r: any) => { if (seen.has(r.uid)) return false; seen.add(r.uid); return true; });
}

export async function fetchCommentLikers(commentId: string): Promise<{ uid: string; name: string; photo_url: string; created_at: string }[]> {
  const { data } = await supabase.from('comment_likes')
    .select('uid,name,photo_url,created_at')
    .eq('comment_id', commentId)
    .order('created_at', { ascending: false })
    .limit(50);
  return data ?? [];
}
