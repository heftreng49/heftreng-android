// Android MessagesViewModel karşılığı
// Firestore: conversations/{id}, convMessages/{id}/msgs
import {
  collection, query, orderBy, limit, onSnapshot,
  addDoc, updateDoc, deleteDoc, doc, getDocs, serverTimestamp,
  getDoc, where,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';

export interface Conversation {
  id:           string;
  participants: string[];
  lastMsg:      string;
  lastMsgTs:    any;
  lastSenderUid: string;
  unreadCount:  number;
  otherUid:     string;
  otherName:    string;
  otherPhoto:   string;
  otherOnline:  boolean;
}

export interface Message {
  id:        string;
  uid:       string;
  name:      string;
  photoURL:  string;
  text:      string;
  ts:        any;
  deleted:   boolean;
  readBy:    string[];
}

// ── Konuşma listesi ─────────────────────────────────────────────────────────
export function listenConversations(
  uid: string,
  cb: (convs: Conversation[]) => void,
): () => void {
  const q = query(
    collection(db, 'conversations'),
    where('participants', 'array-contains', uid),
    orderBy('lastMsgTs', 'desc'),
    limit(30),
  );
  return onSnapshot(q, async snap => {
    const convs: Conversation[] = [];
    for (const d of snap.docs) {
      const data = d.data();
      const otherUid = (data.participants as string[]).find(p => p !== uid) ?? '';
      // Karşı kullanıcı bilgisi
      let otherName = data[`name_${otherUid}`] ?? '';
      let otherPhoto = data[`photo_${otherUid}`] ?? '';
      if (!otherName) {
        try {
          const uSnap = await getDoc(doc(db, 'users', otherUid));
          if (uSnap.exists()) {
            otherName = uSnap.data().displayName ?? '';
            otherPhoto = uSnap.data().photoURL ?? '';
          }
        } catch {}
      }
      // Okunmamış sayısı
      const unreadCount = (data.unread ?? {})[uid] ?? 0;
      convs.push({
        id: d.id, participants: data.participants,
        lastMsg: data.lastMsg ?? '', lastMsgTs: data.lastMsgTs,
        lastSenderUid: data.lastSenderUid ?? '',
        unreadCount, otherUid, otherName, otherPhoto, otherOnline: false,
      });
    }
    cb(convs);
  });
}

// ── Mesajlar ────────────────────────────────────────────────────────────────
export function listenMessages(
  convId: string,
  cb: (msgs: Message[]) => void,
): () => void {
  const q = query(
    collection(db, 'convMessages', convId, 'msgs'),
    orderBy('ts', 'asc'), limit(100),
  );
  return onSnapshot(q, snap => {
    cb(snap.docs.map(d => ({ id: d.id, ...d.data() } as Message)));
  });
}

// ── Mesaj gönder ────────────────────────────────────────────────────────────
export async function sendMessage(
  convId: string, uid: string, name: string, photoURL: string, text: string,
): Promise<void> {
  const msgRef = collection(db, 'convMessages', convId, 'msgs');
  await addDoc(msgRef, { uid, name, photoURL, text, ts: serverTimestamp(), deleted: false, readBy: [uid] });
  await updateDoc(doc(db, 'conversations', convId), {
    lastMsg: text, lastMsgTs: serverTimestamp(), lastSenderUid: uid,
    [`unread.${uid}`]: 0,  // gönderen kendi mesajını okumuş sayılır
  });
}

// ── Mesaj sil ───────────────────────────────────────────────────────────────
export async function deleteMessage(convId: string, msgId: string): Promise<void> {
  await updateDoc(doc(db, 'convMessages', convId, 'msgs', msgId), {
    text: '', deleted: true,
  });
}

// ── Okundu işaretle ─────────────────────────────────────────────────────────
export async function markConversationRead(convId: string, uid: string): Promise<void> {
  await updateDoc(doc(db, 'conversations', convId), { [`unread.${uid}`]: 0 });
}

// ── Konuşma oluştur veya bul ────────────────────────────────────────────────
export async function findOrCreateConversation(
  uid: string, name: string, photo: string,
  otherUid: string, otherName: string, otherPhoto: string,
): Promise<string> {
  const q = query(
    collection(db, 'conversations'),
    where('participants', 'array-contains', uid),
  );
  const snap = await getDocs(q);
  const existing = snap.docs.find(d =>
    (d.data().participants as string[]).includes(otherUid)
  );
  if (existing) return existing.id;

  const ref = await addDoc(collection(db, 'conversations'), {
    participants: [uid, otherUid],
    lastMsg: '', lastMsgTs: serverTimestamp(), lastSenderUid: '',
    [`name_${uid}`]: name, [`photo_${uid}`]: photo,
    [`name_${otherUid}`]: otherName, [`photo_${otherUid}`]: otherPhoto,
    unread: { [uid]: 0, [otherUid]: 0 },
  });
  return ref.id;
}

// ── Presence (online/offline) ───────────────────────────────────────────────
export async function setPresence(uid: string, online: boolean): Promise<void> {
  try {
    await supabase.from('presence').upsert({
      uid, online, last_seen: new Date().toISOString(),
    });
  } catch {}
}

export async function fetchPresence(uids: string[]): Promise<Record<string, boolean>> {
  if (!uids.length) return {};
  const { data } = await supabase.from('presence')
    .select('uid,online').in('uid', uids);
  const map: Record<string, boolean> = {};
  (data ?? []).forEach((r: any) => { map[r.uid] = r.online; });
  return map;
}
