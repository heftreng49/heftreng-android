// Android yorum katmanı — Supabase feed_comments tablosu
import { supabase }      from '$lib/supabase/config';
import type { Comment, FeedCommentRow, FeedCommentInsert } from '$lib/models/comment';

// Supabase satırını domain modeline çevir
function rowToComment(r: FeedCommentRow): Comment {
  return {
    id:          r.id,
    postId:      r.post_id,
    uid:         r.uid,
    displayName: r.name ?? '',
    photoURL:    r.photo_url ?? '',
    text:        r.text,
    likesCount:  r.likes_count,
    mentions:    r.mentions ?? [],
    ts:          r.created_at,
    replyTo: r.reply_to_cmt_id
      ? { commentId: r.reply_to_cmt_id, uid: '', displayName: '' }
      : undefined,
  };
}

// ── Yorumları yükle ──────────────────────────────────────────────────────────
export async function fetchComments(postId: string): Promise<Comment[]> {
  const { data, error } = await supabase
    .from('feed_comments')
    .select('*')
    .eq('post_id', postId)
    .order('created_at', { ascending: true });
  if (error) throw error;
  return (data ?? []).map(rowToComment);
}

// ── Yorum gönder ─────────────────────────────────────────────────────────────
export async function sendComment(payload: FeedCommentInsert): Promise<Comment> {
  const { data, error } = await supabase
    .from('feed_comments')
    .insert(payload)
    .select()
    .single();
  if (error) throw error;
  return rowToComment(data as FeedCommentRow);
}

// ── Yorum sil ────────────────────────────────────────────────────────────────
export async function deleteComment(id: string, uid: string): Promise<void> {
  const { error } = await supabase
    .from('feed_comments')
    .delete()
    .eq('id', id)
    .eq('uid', uid);
  if (error) throw error;
}

// ── Yorum beğeni toggle ──────────────────────────────────────────────────────
export async function toggleCommentLike(
  commentId: string,
  uid:       string,
  name:      string,
  photo:     string,
  liked:     boolean,
): Promise<void> {
  if (liked) {
    await supabase.from('comment_likes').delete()
      .eq('comment_id', commentId).eq('uid', uid);
  } else {
    await supabase.from('comment_likes').upsert({
      id:         `${commentId}_${uid}`,
      comment_id: commentId,
      uid,
      name,
      photo_url:  photo,
    });
  }
}
