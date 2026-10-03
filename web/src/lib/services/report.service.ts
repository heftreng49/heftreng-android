// Android ReportDialog karşılığı — Firestore reports koleksiyonu
import { collection, addDoc, Timestamp, query, where, getDocs } from 'firebase/firestore';
import { db } from '$lib/firebase/config';

export type ReportReason =
  | 'spam'
  | 'harassment'
  | 'hate_speech'
  | 'misinformation'
  | 'violence'
  | 'other';

export const REPORT_REASONS: { value: ReportReason; label: string }[] = [
  { value: 'spam',           label: '🚫 Spam / Reklam' },
  { value: 'harassment',     label: '😡 Taciz / Zorbalık' },
  { value: 'hate_speech',    label: '🔥 Nefret söylemi' },
  { value: 'misinformation', label: '❌ Yanlış bilgi' },
  { value: 'violence',       label: '⚠️ Şiddet içeriği' },
  { value: 'other',          label: '🔸 Diğer' },
];

// ── Gönderi şikayet et ───────────────────────────────────────────────────────
export async function reportPost(
  postId:    string,
  reporterUid: string,
  reason:    ReportReason,
  note:      string = '',
): Promise<void> {
  // Aynı kullanıcının aynı gönderiye iki kez şikayet etmesini önle
  const existing = await getDocs(
    query(
      collection(db, 'reports'),
      where('postId',      '==', postId),
      where('reporterUid', '==', reporterUid),
    ),
  );
  if (!existing.empty) throw new Error('Bu gönderiyi zaten şikayet ettin.');

  await addDoc(collection(db, 'reports'), {
    postId,
    reporterUid,
    reason,
    note:      note.trim().slice(0, 500),
    status:    'pending',
    createdAt: Timestamp.now(),
  });
}

// ── Kullanıcı şikayet et ─────────────────────────────────────────────────────
export async function reportUser(
  targetUid:   string,
  reporterUid: string,
  reason:      ReportReason,
  note:        string = '',
): Promise<void> {
  const existing = await getDocs(
    query(
      collection(db, 'reports'),
      where('targetUid',   '==', targetUid),
      where('reporterUid', '==', reporterUid),
    ),
  );
  if (!existing.empty) throw new Error('Bu kullanıcıyı zaten şikayet ettin.');

  await addDoc(collection(db, 'reports'), {
    targetUid,
    reporterUid,
    reason,
    note:      note.trim().slice(0, 500),
    status:    'pending',
    createdAt: Timestamp.now(),
  });
}
