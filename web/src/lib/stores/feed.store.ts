// Android FeedViewModel state karşılığı
import { writable } from 'svelte/store';
import type { Post }    from '$lib/models/post';
import type { Comment } from '$lib/models/comment';

// ── Feed listesi ────────────────────────────────────────────────────────────
export const posts      = writable<Post[]>([]);
export const feedLoading = writable(false);
export const hasMore     = writable(true);
// Firestore pagination cursor (DocumentSnapshot) — any çünkü web SDK tipi
export const lastDoc     = writable<unknown>(null);

// ── Yorum paneli ────────────────────────────────────────────────────────────
export const commentPostId    = writable<string | null>(null);
export const comments         = writable<Comment[]>([]);
export const commentsLoading  = writable(false);

// ── Beğenenler listesi ──────────────────────────────────────────────────────
export const likersPostId = writable<string | null>(null);
export const likers       = writable<{ uid: string; name: string; photoURL: string }[]>([]);

// ── Feed sıfırlama yardımcısı ───────────────────────────────────────────────
export function resetFeed() {
  posts.set([]);
  hasMore.set(true);
  lastDoc.set(null);
}
