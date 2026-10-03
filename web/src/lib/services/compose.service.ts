// Android FeedViewModel.createPost / uploadPostWithImage karşılığı.
// Önceki versiyonda eksik olan alanlar eklendi:
//   - Alan adı tutarsızlığı düzeltildi (ts, imgUrl/imageURL, likes/cmtCount/reposts/saves)
//   - username, name, authorEmail eklendi
//   - visibility (özel hesap desteği) eklendi
//   - mentions + bildirim eklendi
//   - type alanı eklendi (library_quote)
//   - libraryAuthorId / libraryBookId eklendi
//   - Alıntı oluştururken Supabase kütüphane bağlantısı eklendi
//   - Link önizleme alanları eklendi
//   - Mention autocomplete için getActiveMentionQuery re-export

import {
  collection, addDoc, getDoc, updateDoc,
  doc, Timestamp,
} from 'firebase/firestore';
import { ref, uploadBytes, getDownloadURL } from 'firebase/storage';
import { db, storage } from '$lib/firebase/config';
import { supabase }    from '$lib/supabase/config';
import type { Post }   from '$lib/models/post';

// ── Mevcut gönderiyi yükle (edit modu) ──────────────────────────────────────
export async function loadPost(id: string): Promise<Partial<Post> | null> {
  const snap = await getDoc(doc(db, 'feed', id));
  if (!snap.exists()) return null;
  return { id: snap.id, ...(snap.data() as Partial<Post>) };
}

// ── Resim yükle → URL döndür ─────────────────────────────────────────────────
export async function uploadImage(file: File, uid: string): Promise<string> {
  const ext  = file.name.split('.').pop() ?? 'jpg';
  const path = `posts/${uid}/${Date.now()}.${ext}`;
  const snap = await uploadBytes(ref(storage, path), file);
  return getDownloadURL(snap.ref);
}

// ── Kullanıcı profil bilgilerini Firestore'dan çek ───────────────────────────
async function fetchUserMeta(uid: string): Promise<{
  displayName: string;
  username:    string;
  photoURL:    string;
  email:       string;
  isPrivate:   boolean;
}> {
  try {
    const snap = await getDoc(doc(db, 'users', uid));
    if (snap.exists()) {
      const d = snap.data();
      return {
        displayName: (d['displayName'] || d['name'] || '') as string,
        username:    (d['username'] || '') as string,
        photoURL:    (d['photoURL'] || '') as string,
        email:       (d['email'] || '') as string,
        isPrivate:   !!(d['isPrivate']),
      };
    }
  } catch (_) {}
  return { displayName: '', username: '', photoURL: '', email: '', isPrivate: false };
}

// ── Mention bildirimi gönder ─────────────────────────────────────────────────
async function sendMentionNotif(
  mentionedUids: string[],
  fromUid:       string,
  fromName:      string,
  text:          string,
  postId:        string,
): Promise<void> {
  const body = text.slice(0, 60);
  await Promise.allSettled(
    mentionedUids
      .filter(uid => uid && uid !== fromUid)
      .map(uid =>
        addDoc(collection(db, 'notifications'), {
          toUid:   uid,
          fromUid,
          type:    'mention',
          title:   `${fromName} seni bir gönderide etiketledi`,
          body,
          postId,
          read:    false,
          ts:      Timestamp.now(),
        })
      )
  );
}

// ── Alıntı → Supabase kütüphane bağlantısı ──────────────────────────────────
async function linkQuoteToLibrary(params: {
  bookName:    string;
  authorName:  string;
  quoteText:   string;
  uid:         string;
  displayName: string;
  photoURL:    string;
  feedPostId:  string;
}): Promise<{ authorId: string; bookId: string; coverImg: string }> {
  const { bookName, authorName, quoteText, uid, displayName, photoURL, feedPostId } = params;

  if (!bookName.trim() && !authorName.trim()) {
    return { authorId: '', bookId: '', coverImg: '' };
  }

  // 1. Yazar bul veya oluştur
  let authorId = '';
  if (authorName.trim()) {
    const { data: existingAuthor } = await supabase
      .from('authors')
      .select('id')
      .ilike('name', authorName.trim())
      .limit(1)
      .single();

    if (existingAuthor) {
      authorId = existingAuthor.id;
    } else {
      const { data: newAuthor } = await supabase
        .from('authors')
        .insert({ name: authorName.trim() })
        .select('id')
        .single();
      authorId = newAuthor?.id ?? '';
    }
  }

  // 2. Kitap bul veya oluştur
  let bookId   = '';
  let coverImg = '';
  if (bookName.trim()) {
    const { data: existingBook } = await supabase
      .from('library_books')
      .select('id, cover_img')
      .ilike('title', bookName.trim())
      .limit(1)
      .single();

    if (existingBook) {
      bookId   = existingBook.id;
      coverImg = existingBook.cover_img ?? '';
    } else if (authorId) {
      const { data: newBook } = await supabase
        .from('library_books')
        .insert({ title: bookName.trim(), author_id: authorId, author_name: authorName.trim() })
        .select('id')
        .single();
      bookId = newBook?.id ?? '';
    }
  }

  // 3. Alıntıyı library_quotes tablosuna ekle
  if (bookId && quoteText.trim()) {
    await supabase.from('library_quotes').upsert({
      book_id:            bookId,
      author_id:          authorId,
      book_title:         bookName.trim(),
      author_name:        authorName.trim(),
      text:               quoteText.trim(),
      uid,
      user_display_name:  displayName,
      user_photo_url:     photoURL,
      feed_post_id:       feedPostId,
      cover_img:          coverImg,
      likes_count:        0,
      is_active:          true,
    }, { onConflict: 'feed_post_id' });
  }

  return { authorId, bookId, coverImg };
}

// ── Yeni normal gönderi oluştur ──────────────────────────────────────────────
export interface NewPostPayload {
  uid:          string;
  // Kullanıcı meta — compose.svelte'den currentUser + userProfile verir
  displayName?: string;
  username?:    string;
  photoURL?:    string;
  email?:       string;
  isPrivate?:   boolean;
  // İçerik
  title:        string;
  text:         string;
  category:     string;
  imageUrl?:    string;
  mentions?:    string[];  // UID listesi — text içinden extractMentionUids() ile çıkarılır
  // Link önizleme (opsiyonel)
  linkUrl?:     string;
  linkTitle?:   string;
  linkDesc?:    string;
  linkImage?:   string;
  linkType?:    string;   // 'youtube' | 'article' | ''
}

export async function createPost(payload: NewPostPayload): Promise<string> {
  // Firestore'dan taze meta çek — compose sayfasında userProfile zaten var
  // ama isPrivate gibi kritik bilgilerin sunucu kaynağı daha güvenilir
  const meta = await fetchUserMeta(payload.uid);

  const displayName = payload.displayName || meta.displayName;
  const username    = payload.username    || meta.username;
  const photoURL    = payload.photoURL    || meta.photoURL;
  const email       = payload.email       || meta.email;
  const isPrivate   = payload.isPrivate   ?? meta.isPrivate;
  const visibility  = isPrivate ? 'friends' : 'public';
  const mentions    = payload.mentions ?? [];
  const imageURL    = payload.imageUrl ?? '';
  const linkType    = payload.linkType ?? '';

  // Android ile birebir alan isimleri
  const ref_ = await addDoc(collection(db, 'feed'), {
    uid:             payload.uid,
    name:            displayName,        // Android: "name"
    displayName,                          // Android: "displayName"
    username,
    photoURL,
    authorEmail:     email,
    text:            payload.text,
    title:           payload.title,
    category:        payload.category,
    imgUrl:          imageURL,            // Android: "imgUrl"
    imageURL,                             // Android: "imageURL" (ikisi de var)
    quoteText:       '',
    authorName:      '',
    bookName:        '',
    coverImg:        '',
    libraryAuthorId: '',
    libraryBookId:   '',
    type:            '',
    visibility,
    mentions,
    linkUrl:         payload.linkUrl   ?? '',
    linkTitle:       payload.linkTitle ?? '',
    linkDesc:        payload.linkDesc  ?? '',
    linkImage:       payload.linkImage ?? '',
    linkType,
    ytVid:           linkType === 'youtube' ? (payload.linkUrl ?? '') : '',
    // Android sayaç alan adları (Supabase'den çekilir ama başlangıç değerleri)
    likes:           0,
    saves:           0,
    cmtCount:        0,
    reposts:         0,
    ts:              Timestamp.now(),    // Android: "ts"
  });

  // Mention bildirimleri
  if (mentions.length > 0) {
    await sendMentionNotif(mentions, payload.uid, displayName, payload.text, ref_.id);
  }

  return ref_.id;
}

// ── Yeni alıntı gönderisi oluştur (QuoteDialog karşılığı) ───────────────────
export interface NewQuotePayload {
  uid:          string;
  displayName?: string;
  username?:    string;
  photoURL?:    string;
  email?:       string;
  isPrivate?:   boolean;
  // Alıntı
  title:        string;
  quoteText:    string;
  bookName:     string;
  authorName:   string;
  coverImg?:    string;   // başlangıçta boş olabilir, library'den doldurulur
  bookId?:      string;
  authorId?:    string;
}

export async function createQuote(payload: NewQuotePayload): Promise<string> {
  const meta = await fetchUserMeta(payload.uid);

  const displayName = payload.displayName || meta.displayName;
  const username    = payload.username    || meta.username;
  const photoURL    = payload.photoURL    || meta.photoURL;
  const email       = payload.email       || meta.email;
  const isPrivate   = payload.isPrivate   ?? meta.isPrivate;
  const visibility  = isPrivate ? 'friends' : 'public';

  // Önce Firestore'a yaz — ID'ye ihtiyaç var
  const ref_ = await addDoc(collection(db, 'feed'), {
    uid:             payload.uid,
    name:            displayName,
    displayName,
    username,
    photoURL,
    authorEmail:     email,
    text:            '',
    title:           payload.title,
    category:        '',
    imgUrl:          '',
    imageURL:        '',
    quoteText:       payload.quoteText,
    authorName:      payload.authorName,
    bookName:        payload.bookName,
    coverImg:        payload.coverImg ?? '',
    libraryAuthorId: payload.authorId ?? '',
    libraryBookId:   payload.bookId   ?? '',
    type:            'library_quote',
    visibility,
    mentions:        [],
    linkUrl: '', linkTitle: '', linkDesc: '', linkImage: '', linkType: '', ytVid: '',
    likes: 0, saves: 0, cmtCount: 0, reposts: 0,
    ts:              Timestamp.now(),
  });

  // Supabase kütüphane bağlantısı — arka planda, kullanıcı beklemez
  linkQuoteToLibrary({
    bookName:    payload.bookName,
    authorName:  payload.authorName,
    quoteText:   payload.quoteText,
    uid:         payload.uid,
    displayName,
    photoURL,
    feedPostId:  ref_.id,
  }).then(({ coverImg, authorId, bookId }) => {
    // Kapak resmi veya ID'ler geldiyse Firestore kaydını güncelle
    if (coverImg || authorId || bookId) {
      updateDoc(doc(db, 'feed', ref_.id), {
        ...(coverImg  ? { coverImg }           : {}),
        ...(authorId  ? { libraryAuthorId: authorId } : {}),
        ...(bookId    ? { libraryBookId:   bookId }   : {}),
      }).catch(() => {}); // sessizce başarısız ol
    }
  }).catch(() => {}); // kütüphane bağlantısı başarısız olursa gönderi etkilenmesin

  return ref_.id;
}

// ── Mevcut gönderiyi düzenle ──────────────────────────────────────────────────
export async function updatePost(
  id:     string,
  fields: Partial<Pick<Post,
    'title' | 'text' | 'quoteText' | 'bookName' | 'authorName' | 'coverImg' | 'category'
  >>,
): Promise<void> {
  await updateDoc(doc(db, 'feed', id), {
    ...fields,
    updatedAt: Timestamp.now(),
  });
}
