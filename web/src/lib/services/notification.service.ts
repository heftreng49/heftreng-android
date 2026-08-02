// Android NotificationsViewModel karşılığı
// Firestore: userNotifs/{uid}/msgs — onSnapshot ile realtime
import {
  collection, query, orderBy, limit,
  onSnapshot, updateDoc, doc, writeBatch, getDocs, where,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';

export interface Notification {
  id:          string;
  type:        string;
  fromUid:     string;
  fromName:    string;
  fromPhoto:   string;
  title:       string;
  message:     string;
  url:         string;
  read:        boolean;
  ts:          any;
}

// Tip bazlı ikon ve renk — Android notifIcon/notifIconColor karşılığı
export function notifMeta(type: string): { icon: string; color: string } {
  const map: Record<string, { icon: string; color: string }> = {
    like:                     { icon: '❤️',  color: '#EF4444' },
    cmt:                      { icon: '💬',  color: '#3B82F6' },
    comment:                  { icon: '💬',  color: '#3B82F6' },
    mention:                  { icon: '@',   color: '#F59E0B' },
    follow:                   { icon: '👤',  color: '#10B981' },
    follow_request:           { icon: '👤',  color: '#F59E0B' },
    follow_request_accepted:  { icon: '✅',  color: '#10B981' },
    repost:                   { icon: '🔁',  color: '#8B5CF6' },
    bm:                       { icon: '🔖',  color: '#6366F1' },
    bookmark:                 { icon: '🔖',  color: '#6366F1' },
    serial:                   { icon: '📖',  color: '#0EA5E9' },
    chapter:                  { icon: '📄',  color: '#0EA5E9' },
    book_chapter:             { icon: '📄',  color: '#0EA5E9' },
    library_quote:            { icon: '"',   color: '#D97706' },
    library_review:           { icon: '⭐',  color: '#D97706' },
    sys:                      { icon: '📢',  color: '#64748B' },
    admin:                    { icon: '📢',  color: '#64748B' },
    message:                  { icon: '✉️',  color: '#3B82F6' },
    appeal_result:            { icon: '⚖️',  color: '#EF4444' },
    moderation:               { icon: '🛡️',  color: '#EF4444' },
  };
  return map[type] ?? { icon: '🔔', color: '#64748B' };
}

export function listenNotifications(uid: string, cb: (notifs: Notification[]) => void): () => void {
  const q = query(
    collection(db, 'userNotifs', uid, 'msgs'),
    orderBy('ts', 'desc'), limit(50),
  );
  return onSnapshot(q, snap => {
    const notifs = snap.docs.map(d => ({ id: d.id, ...d.data() } as Notification));
    cb(notifs);
  }, (error) => {
    console.warn('listenNotifications error:', error.code);
    cb([]);
  });
}

export async function markAsRead(uid: string, notifId: string): Promise<void> {
  await updateDoc(doc(db, 'userNotifs', uid, 'msgs', notifId), { read: true });
}

export async function markAllRead(uid: string): Promise<void> {
  const snap = await getDocs(query(
    collection(db, 'userNotifs', uid, 'msgs'),
    where('read', '==', false),
  ));
  if (snap.empty) return;
  const batch = writeBatch(db);
  snap.docs.forEach(d => batch.update(d.ref, { read: true }));
  await batch.commit();
}
