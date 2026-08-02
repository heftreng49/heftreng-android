// Android Models.kt → Message + Conversation karşılığı
// Alan adları Android Firestore şemasıyla birebir eşleştirildi
import {
  collection, query, orderBy, limit, onSnapshot,
  addDoc, updateDoc, doc, getDocs, serverTimestamp,
  getDoc, where,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import type { User } from '$lib/models/user';

// ── Firestore şemasıyla birebir eşleşen tipler (Android Models.kt karşılığı) ─

export interface Message {
  id:             string;
  conversationId: string;   // Android: conversationId
  senderId:       string;   // Android: senderId  (eskisi: uid)
  text:           string;
  imageUrl:       string;
  audioUrl:       string;
  createdAt:      any;      // Android: createdAt (eskisi: ts)
  read:           boolean;  // Android: read      (eskisi: readBy[])
  readAt:         any;
  deleted:        boolean;
  edited:         boolean;
  replyToId:      string;
  replyToText:    string;
  replyToName:    string;
  isLikedByMe:    boolean;
  likesCount:     number;
  mentions:       string[];
}

export interface Conversation {
  id:             string;
  participantIds: string[];  // Android: participantIds (eskisi: participants)
  lastMessage:    string;    // Android: lastMessage    (eskisi: lastMsg)
  lastMessageAt:  any;       // Android: lastMessageAt  (eskisi: lastMsgTs)
  unreadCount:    number;    // Android: unreadCount
  // Client-only alanlar — Firestore'a yazılmaz (@Exclude karşılığı)
  otherUser?:     User;
  otherUid?:      string;
  otherName?:     string;
  otherPhoto?:    string;
  otherOnline?:   boolean;
}

// ── Konuşma listesi — tek seferlik fetch ─────────────────────────────────────
export async function fetchConversations(uid: string): Promise<Conversation[]> {
  try {
    const q = query(
      collection(db, 'conversations'),
      where('participantIds', 'array-contains', uid),
      orderBy('lastMessageAt', 'desc'),
      limit(30),
    );
    const snap = await getDocs(q);
    if (snap.empty) return [];

    const convs: Conversation[] = [];
    for (const d of snap.docs) {
      const data = d.data();
      const participantIds: string[] = data.participantIds ?? [];
      const otherUid = participantIds.find(p => p !== uid) ?? '';

      // Karşı taraf bilgisi: users koleksiyonundan çek
      let otherName  = '';
      let otherPhoto = '';
      if (otherUid) {
        try {
          const uSnap = await getDoc(doc(db, 'users', otherUid));
          if (uSnap.exists()) {
            otherName  = uSnap.data().displayName ?? '';
            otherPhoto = uSnap.data().photoURL    ?? '';
          }
        } catch {}
      }

      // unreadCount: Android şemasında doğrudan alan olarak tutuluyor
      const unreadCount: number = data.unreadCount ?? 0;

      convs.push({
        id:             d.id,
        participantIds,
        lastMessage:    data.lastMessage    ?? '',
        lastMessageAt:  data.lastMessageAt  ?? null,
        unreadCount,
        otherUid,
        otherName,
        otherPhoto,
        otherOnline: false,
      });
    }
    return convs;
  } catch (e: any) {
    console.warn('fetchConversations error:', e.code, e.message);
    return [];
  }
}

// ── Konuşma listesi — realtime listener ──────────────────────────────────────
/** [id]/+page.svelte'in ihtiyaç duyduğu listener versiyonu */
export function listenConversations(
  uid: string,
  cb: (convs: Conversation[]) => void,
): () => void {
  const q = query(
    collection(db, 'conversations'),
    where('participantIds', 'array-contains', uid),
    orderBy('lastMessageAt', 'desc'),
    limit(30),
  );
  return onSnapshot(q, async snap => {
    const convs: Conversation[] = [];
    for (const d of snap.docs) {
      const data = d.data();
      const participantIds: string[] = data.participantIds ?? [];
      const otherUid = participantIds.find(p => p !== uid) ?? '';
      let otherName  = '';
      let otherPhoto = '';
      if (otherUid) {
        try {
          const uSnap = await getDoc(doc(db, 'users', otherUid));
          if (uSnap.exists()) {
            otherName  = uSnap.data().displayName ?? '';
            otherPhoto = uSnap.data().photoURL    ?? '';
          }
        } catch {}
      }
      convs.push({
        id:             d.id,
        participantIds,
        lastMessage:    data.lastMessage   ?? '',
        lastMessageAt:  data.lastMessageAt ?? null,
        unreadCount:    data.unreadCount   ?? 0,
        otherUid,
        otherName,
        otherPhoto,
        otherOnline: false,
      });
    }
    cb(convs);
  });
}

// ── Mesajlar — realtime listener ─────────────────────────────────────────────
export function listenMessages(
  convId: string,
  cb: (msgs: Message[]) => void,
): () => void {
  const q = query(
    collection(db, 'convMessages', convId, 'msgs'),
    orderBy('createdAt', 'asc'),  // Android: createdAt (eskisi: ts)
    limit(100),
  );
  return onSnapshot(q, snap => {
    cb(snap.docs.map(d => ({ id: d.id, ...d.data() } as Message)));
  });
}

// ── Mesaj gönder ─────────────────────────────────────────────────────────────
export async function sendMessage(
  convId: string,
  uid: string,
  text: string,
): Promise<void> {
  const msgRef = collection(db, 'convMessages', convId, 'msgs');
  // Android Message alanlarıyla birebir eşleşen doküman
  await addDoc(msgRef, {
    conversationId: convId,
    senderId:       uid,       // Android: senderId
    text,
    imageUrl:       '',
    audioUrl:       '',
    createdAt:      serverTimestamp(),  // Android: createdAt
    read:           false,
    readAt:         null,
    deleted:        false,
    edited:         false,
    replyToId:      '',
    replyToText:    '',
    replyToName:    '',
    isLikedByMe:    false,
    likesCount:     0,
    mentions:       [],
  });
  await updateDoc(doc(db, 'conversations', convId), {
    lastMessage:   text,                // Android: lastMessage
    lastMessageAt: serverTimestamp(),   // Android: lastMessageAt
  });
}

// ── Mesaj sil ────────────────────────────────────────────────────────────────
export async function deleteMessage(convId: string, msgId: string): Promise<void> {
  await updateDoc(doc(db, 'convMessages', convId, 'msgs', msgId), {
    text: '',
    deleted: true,
  });
}

// ── Okundu işaretle ──────────────────────────────────────────────────────────
export async function markConversationRead(convId: string, uid: string): Promise<void> {
  // Android şeması: unreadCount direkt alan
  await updateDoc(doc(db, 'conversations', convId), { unreadCount: 0 });
}

// ── Konuşma oluştur veya bul ─────────────────────────────────────────────────
export async function findOrCreateConversation(
  uid: string,
  otherUid: string,
): Promise<string> {
  const q = query(
    collection(db, 'conversations'),
    where('participantIds', 'array-contains', uid),
  );
  const snap = await getDocs(q);
  const existing = snap.docs.find(d =>
    (d.data().participantIds as string[]).includes(otherUid)
  );
  if (existing) return existing.id;

  // Android Conversation alanlarıyla birebir eşleşen doküman
  const ref = await addDoc(collection(db, 'conversations'), {
    participantIds: [uid, otherUid],   // Android: participantIds
    lastMessage:    '',                 // Android: lastMessage
    lastMessageAt:  serverTimestamp(), // Android: lastMessageAt
    unreadCount:    0,                 // Android: unreadCount
  });
  return ref.id;
}

// ── Presence (online/offline) ────────────────────────────────────────────────
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
