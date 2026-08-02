// Firestore şeması — MessagesViewModel.kt'den türetildi (gerçek alan adları)
//
// conversations/{convId}
//   participants : string[]       (whereArrayContains ile sorgulanıyor)
//   updated_at   : Timestamp      (sıralama için)
//   last_msg     : string
//   unread_{uid} : number         (her kullanıcı için ayrı alan)
//
// convMessages/{convId}/msgs/{msgId}
//   senderUid    : string
//   text         : string
//   image_url    : string
//   audio_url    : string
//   createdAt    : Timestamp
//   read         : boolean
//   readAt       : Timestamp | null
//   deleted      : boolean
//   edited       : boolean
//   reply_to_id  : string
//   reply_to_text: string
//   reply_to_name: string
//   mentions     : string[]
//   likesCount   : number
//   likedByUids  : string[]

import {
  collection, query, orderBy, limit, onSnapshot,
  addDoc, updateDoc, setDoc, doc, getDocs,
  serverTimestamp, getDoc, where, increment, FieldValue,
} from 'firebase/firestore';
import { db }       from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';

export interface Message {
  id:           string;
  conversationId: string;
  senderUid:    string;    // Firestore: senderUid
  text:         string;
  imageUrl:     string;    // Firestore: image_url
  audioUrl:     string;    // Firestore: audio_url
  createdAt:    any;       // Firestore: createdAt
  read:         boolean;
  readAt:       any;
  deleted:      boolean;
  edited:       boolean;
  replyToId:    string;    // Firestore: reply_to_id
  replyToText:  string;    // Firestore: reply_to_text
  replyToName:  string;    // Firestore: reply_to_name
  mentions:     string[];
  likesCount:   number;
  isLikedByMe:  boolean;
}

export interface Conversation {
  id:           string;
  participants: string[];  // Firestore: participants
  lastMsg:      string;    // Firestore: last_msg
  updatedAt:    any;       // Firestore: updated_at
  unread:       number;    // Firestore: unread_{uid}
  // Client-only — Firestore'a yazılmaz
  otherUid?:    string;
  otherName?:   string;
  otherPhoto?:  string;
  otherOnline?: boolean;
}

// ── Konuşma listesi — tek seferlik fetch ─────────────────────────────────────
export async function fetchConversations(uid: string): Promise<Conversation[]> {
  try {
    const q = query(
      collection(db, 'conversations'),
      where('participants', 'array-contains', uid),
      orderBy('updated_at', 'desc'),
      limit(50),
    );
    const snap = await getDocs(q);
    if (snap.empty) return [];

    const convs: Conversation[] = [];
    for (const d of snap.docs) {
      const data      = d.data();
      const parts: string[] = data.participants ?? [];
      const otherUid  = parts.find(p => p !== uid) ?? '';

      let otherName  = '';
      let otherPhoto = '';
      if (otherUid) {
        try {
          const uSnap = await getDoc(doc(db, 'users', otherUid));
          if (uSnap.exists()) {
            otherName  = uSnap.data().displayName ?? uSnap.data().name ?? '';
            otherPhoto = uSnap.data().photoURL    ?? '';
          }
        } catch {}
      }

      convs.push({
        id:           d.id,
        participants: parts,
        lastMsg:      data.last_msg   ?? '',
        updatedAt:    data.updated_at ?? null,
        unread:       data[`unread_${uid}`] ?? 0,
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
export function listenConversations(
  uid: string,
  cb: (convs: Conversation[]) => void,
  onError?: (err: any) => void,
): () => void {
  const q = query(
    collection(db, 'conversations'),
    where('participants', 'array-contains', uid),
    orderBy('updated_at', 'desc'),
    limit(50),
  );
  return onSnapshot(q, async snap => {
    const convs: Conversation[] = [];
    for (const d of snap.docs) {
      const data      = d.data();
      const parts: string[] = data.participants ?? [];
      const otherUid  = parts.find(p => p !== uid) ?? '';
      let otherName  = '';
      let otherPhoto = '';
      if (otherUid) {
        try {
          const uSnap = await getDoc(doc(db, 'users', otherUid));
          if (uSnap.exists()) {
            otherName  = uSnap.data().displayName ?? uSnap.data().name ?? '';
            otherPhoto = uSnap.data().photoURL    ?? '';
          }
        } catch {}
      }
      convs.push({
        id:           d.id,
        participants: parts,
        lastMsg:      data.last_msg   ?? '',
        updatedAt:    data.updated_at ?? null,
        unread:       data[`unread_${uid}`] ?? 0,
        otherUid,
        otherName,
        otherPhoto,
        otherOnline: false,
      });
    }
    cb(convs);
  }, (err) => {
    console.error('listenConversations:', err);
    onError?.(err);
  });
}

// ── Mesajlar — realtime listener ─────────────────────────────────────────────
export function listenMessages(
  convId: string,
  cb: (msgs: Message[]) => void,
): () => void {
  const q = query(
    collection(db, 'convMessages', convId, 'msgs'),
    orderBy('createdAt', 'asc'),
    limit(100),
  );
  return onSnapshot(q, snap => {
    const msgs: Message[] = snap.docs
      .map(d => {
        const data = d.data();
        if (data.deleted) return null;
        return {
          id:             d.id,
          conversationId: convId,
          senderUid:      data.senderUid    ?? '',
          text:           data.text         ?? '',
          imageUrl:       data.image_url    ?? '',
          audioUrl:       data.audio_url    ?? '',
          createdAt:      data.createdAt    ?? null,
          read:           data.read         ?? false,
          readAt:         data.readAt       ?? null,
          deleted:        data.deleted      ?? false,
          edited:         data.edited       ?? false,
          replyToId:      data.reply_to_id  ?? '',
          replyToText:    data.reply_to_text ?? '',
          replyToName:    data.reply_to_name ?? '',
          mentions:       data.mentions     ?? [],
          likesCount:     data.likesCount   ?? 0,
          isLikedByMe:    false,
        } as Message;
      })
      .filter(Boolean) as Message[];
    cb(msgs);
  });
}

// ── Mesaj gönder ─────────────────────────────────────────────────────────────
export async function sendMessage(
  convId:  string,
  uid:     string,
  toUid:   string,
  text:    string,
): Promise<void> {
  await addDoc(collection(db, 'convMessages', convId, 'msgs'), {
    senderUid:     uid,
    text,
    image_url:     '',
    audio_url:     '',
    createdAt:     serverTimestamp(),
    read:          false,
    deleted:       false,
    edited:        false,
    reply_to_id:   '',
    reply_to_text: '',
    reply_to_name: '',
    mentions:      [],
  });
  // conversations dokümanı — Android ile aynı alan adları
  await setDoc(doc(db, 'conversations', convId), {
    last_msg:          text,
    updated_at:        serverTimestamp(),
    [`unread_${toUid}`]: increment(1),
    [`unread_${uid}`]:   0,
  }, { merge: true });
}

// ── Mesaj sil ────────────────────────────────────────────────────────────────
export async function deleteMessage(convId: string, msgId: string): Promise<void> {
  await updateDoc(doc(db, 'convMessages', convId, 'msgs', msgId), {
    text: '', deleted: true,
  });
}

// ── Okundu işaretle ──────────────────────────────────────────────────────────
export async function markConversationRead(convId: string, uid: string): Promise<void> {
  await updateDoc(doc(db, 'conversations', convId), { [`unread_${uid}`]: 0 });
}

// ── Konuşma oluştur veya bul ─────────────────────────────────────────────────
// Android: convId = "{minUid}__{maxUid}" formatı kullanılıyor
export async function findOrCreateConversation(
  uid: string,
  otherUid: string,
): Promise<string> {
  const pa = uid < otherUid ? uid : otherUid;
  const pb = uid < otherUid ? otherUid : uid;
  const convId = `${pa}__${pb}`;

  await setDoc(doc(db, 'conversations', convId), {
    participants:      [pa, pb],
    updated_at:        serverTimestamp(),
    last_msg:          '',
    [`unread_${pa}`]:  0,
    [`unread_${pb}`]:  0,
  }, { merge: true });

  return convId;
}

// ── Presence ─────────────────────────────────────────────────────────────────
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
