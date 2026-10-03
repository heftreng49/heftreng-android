// Android FeedViewModel.repost / unrepost / repostBookChapter karşılığı
// Firestore feed koleksiyonuna deterministik ID ile repost dokümanı yazar.
import {
  collection, doc, getDoc, setDoc, deleteDoc, updateDoc,
  increment, Timestamp,
} from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import { cacheDelete } from '$lib/utils/cache';
import { invalidatePostInteractions } from './feed.service';
import type { Post } from '$lib/models/post';

// ── Feed gönderisi repost ────────────────────────────────────────────────────
export async function repost(
  post: Post,
  currentUid: string,
  currentName: string,
  currentPhoto: string,
  currentUsername: string,
): Promise<string> {
  // Deterministik ID: repost_<uid>_<origId> — çift repost imkansız
  const chainedRepost   = post.repostType === 'feed' && !!post.repostId;
  const origId          = chainedRepost ? post.repostId : post.id;
  const origAuthorName  = chainedRepost ? (post.repostAuthor  || post.displayName) : post.displayName;
  const origAuthorPhoto = chainedRepost ? (post.repostAuthorPhoto || post.photoURL) : post.photoURL;
  const origAuthorUid   = chainedRepost ? (post.repostAuthorUid  || post.uid)      : post.uid;
  const previewText = (post.text || post.quoteText || post.repostText || '').slice(0, 200);
  const previewImg  = post.imageURL || post.repostImg || '';

  const repostDocId = `repost_${currentUid}_${origId}`;
  const ref = doc(db, 'feed', repostDocId);

  // Zaten varsa iki kez yazma
  const existing = await getDoc(ref);
  if (existing.exists()) return repostDocId;

  await setDoc(ref, {
    uid:               currentUid,
    displayName:       currentName,
    name:              currentName,
    username:          currentUsername,
    photoURL:          currentPhoto,
    text:              '',
    imageURL:          '',
    imgUrl:            '',
    repostType:        'feed',
    repostId:          origId,
    repostUid:         origAuthorUid,
    repostText:        previewText,
    repostAuthor:      origAuthorName,
    repostAuthorPhoto: origAuthorPhoto,
    repostAuthorUid:   origAuthorUid,
    repostImg:         previewImg,
    likesCount:        0,
    saves:             0,
    commentsCount:     0,
    reposts:           0,
    ts:                Timestamp.now(),
  });

  // Orijinal gönderinin sayacını artır
  await updateDoc(doc(db, 'feed', post.id), { reposts: increment(1) });

  invalidatePostInteractions(post.id, currentUid);
  return repostDocId;
}

// ── Repost geri al ───────────────────────────────────────────────────────────
export async function unrepost(
  postId: string,
  repostDocId: string,
): Promise<void> {
  await deleteDoc(doc(db, 'feed', repostDocId));
  await updateDoc(doc(db, 'feed', postId), { reposts: increment(-1) });
  invalidatePostInteractions(postId);
}

// ── Kullanıcının repost durumunu sorgula (feed enrichment için) ──────────────
export async function fetchMyReposts(
  uid: string,
  postIds: string[],
): Promise<Map<string, string>> {
  // Supabase veya Firestore ile kontrol — Firestore'da repost_<uid>_<origId> ID'leri
  // birden fazla gönderiye bakarken toplu getAll kullanılabilir ama web SDK'sı
  // getAll desteklemiyor. Bunun yerine repost_uid UID'sini indeksleyelim.
  if (!postIds.length) return new Map();

  const repostCol = collection(db, 'feed');
  // Her postId için deterministik ID'yi check et — maksimum 10'ar batch
  const map = new Map<string, string>();
  const chunks: string[][] = [];
  for (let i = 0; i < postIds.length; i += 10) chunks.push(postIds.slice(i, i + 10));

  await Promise.all(
    chunks.map(async (chunk) => {
      await Promise.all(
        chunk.map(async (postId) => {
          const repostDocId = `repost_${uid}_${postId}`;
          const snap = await getDoc(doc(db, 'feed', repostDocId));
          if (snap.exists()) map.set(postId, repostDocId);
        }),
      );
    }),
  );
  return map;
}

// ── Kitap bölümü repost ──────────────────────────────────────────────────────
export async function repostBookChapter(opts: {
  uid:            string;
  displayName:    string;
  username:       string;
  photoURL:       string;
  chapterId:      string;
  chapterTitle:   string;
  chapterOrder:   number;
  chapterText:    string;
  serialId:       string;
  serialTitle:    string;
  serialDesc:     string;
  serialCover:    string;
  serialAuthorName: string;
  serialAuthorUid:  string;
  serialBg:         string;
  chCount:          number;
}): Promise<string> {
  const repostDocId = `repost_${opts.uid}_ch_${opts.chapterId}`;
  const ref = doc(db, 'feed', repostDocId);
  const existing = await getDoc(ref);
  if (existing.exists()) return repostDocId;

  await setDoc(ref, {
    uid:                    opts.uid,
    displayName:            opts.displayName,
    username:               opts.username,
    photoURL:               opts.photoURL,
    text:                   '',
    repostType:             'book_chapter',
    repostId:               opts.chapterId,
    chapterId:              opts.chapterId,
    chapterTitle:           opts.chapterTitle,
    chapterOrder:           opts.chapterOrder,
    repostText:             opts.chapterText.slice(0, 200),
    serialId:               opts.serialId,
    repostSerialId:         opts.serialId,
    repostSerialTitle:      opts.serialTitle,
    repostSerialDesc:       opts.serialDesc.slice(0, 200),
    repostSerialCover:      opts.serialCover,
    repostSerialAuthorName: opts.serialAuthorName,
    repostSerialAuthorUid:  opts.serialAuthorUid,
    repostSerialBg:         opts.serialBg,
    repostSerialChCount:    opts.chCount,
    likesCount: 0, saves: 0, commentsCount: 0, reposts: 0,
    ts: Timestamp.now(),
  });
  return repostDocId;
}
