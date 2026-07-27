// Android ComposeScreen / QuoteDialog mantığının servis katmanı
// compose/+page.svelte içindeki doğrudan Firestore çağrılarını buraya taşıyoruz
import {
  collection, addDoc, getDoc, updateDoc,
  doc, Timestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { db, storage } from '$lib/firebase/config';
import type { Post } from '$lib/models/post';

// ── Mevcut gönderiyi yükle (edit modu) ──────────────────────────────────────
export async function loadPost(id: string): Promise<Partial<Post> | null> {
  const snap = await getDoc(doc(db, 'feed', id));
  if (!snap.exists()) return null;
  return { id: snap.id, ...(snap.data() as Partial<Post>) };
}

// ── Resim yükle → URL döndür ─────────────────────────────────────────────────
export async function uploadImage(file: File, uid: string): Promise<string> {
  const path = `posts/${uid}/${Date.now()}_${file.name}`;
  const snap = await uploadBytes(ref(storage, path), file);
  return getDownloadURL(snap.ref);
}

// ── Yeni normal gönderi oluştur ──────────────────────────────────────────────
export interface NewPostPayload {
  uid: string;
  displayName: string;
  photoURL: string;
  title: string;
  text: string;
  category: string;
  imageUrl?: string;
}

export async function createPost(payload: NewPostPayload): Promise<string> {
  const ref_ = await addDoc(collection(db, 'feed'), {
    uid:          payload.uid,
    displayName:  payload.displayName,
    photoURL:     payload.photoURL,
    title:        payload.title,
    text:         payload.text,
    category:     payload.category,
    imageUrl:     payload.imageUrl ?? '',
    quoteText:    '',
    bookName:     '',
    authorName:   '',
    coverImg:     '',
    bookId:       '',
    likesCount:   0,
    commentsCount: 0,
    repostCount:  0,
    savedCount:   0,
    createdAt:    Timestamp.now(),
    updatedAt:    Timestamp.now(),
  });
  return ref_.id;
}

// ── Yeni alıntı gönderisi oluştur (QuoteDialog karşılığı) ───────────────────
export interface NewQuotePayload {
  uid: string;
  displayName: string;
  photoURL: string;
  title: string;
  quoteText: string;
  bookName: string;
  authorName: string;
  coverImg: string;
  bookId: string;
}

export async function createQuote(payload: NewQuotePayload): Promise<string> {
  const ref_ = await addDoc(collection(db, 'feed'), {
    uid:          payload.uid,
    displayName:  payload.displayName,
    photoURL:     payload.photoURL,
    title:        payload.title,
    text:         '',
    category:     '',
    imageUrl:     '',
    quoteText:    payload.quoteText,
    bookName:     payload.bookName,
    authorName:   payload.authorName,
    coverImg:     payload.coverImg,
    bookId:       payload.bookId,
    likesCount:   0,
    commentsCount: 0,
    repostCount:  0,
    savedCount:   0,
    createdAt:    Timestamp.now(),
    updatedAt:    Timestamp.now(),
  });
  return ref_.id;
}

// ── Mevcut gönderiyi düzenle ──────────────────────────────────────────────────
export async function updatePost(
  id: string,
  fields: Partial<Pick<Post, 'title' | 'text' | 'quoteText' | 'bookName' | 'authorName' | 'coverImg' | 'bookId'>>,
) {
  await updateDoc(doc(db, 'feed', id), {
    ...fields,
    updatedAt: Timestamp.now(),
  });
}
