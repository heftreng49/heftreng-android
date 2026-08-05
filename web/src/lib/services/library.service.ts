// ─── Kütüphane Servisi ────────────────────────────────────────────────────────
// Android: LibraryRepository.kt + LibraryViewModel.kt
//
// VERİ KAYNAĞI HARİTASI (Android ile birebir):
//   authors          → Supabase  (authors tablosu)
//   library_books    → Supabase  (library_books tablosu)
//   book_quotes      → Supabase  (book_quotes tablosu)  ← ARTIK FİREBASE DEĞİL
//   book_reviews     → Supabase  (book_reviews tablosu)
//   author_follows   → Supabase  (author_follows tablosu)
//   feed_likes       → Supabase  (beğeni tek kaynak — book_quotes.likes_count değil)
//   Firestore feed   → SADECE compose sırasında tip='library_quote' yazılır,
//                      OKUMA yapılmaz — Android'in v2 geçişiyle aynı

import { supabase } from '$lib/supabase/config';
import type { Author, LibraryBook, BookQuote, BookReview } from '$lib/models/library';

// ── Sayfa boyutu ─────────────────────────────────────────────────────────────
const PAGE = 20;

// ── getBannedUids TTL cache ───────────────────────────────────────────────────
// Android'deki 10 dakika TTL ile birebir aynı mantık.
// Her quote/review yüklemesinde Supabase'e ayrı sorgu atmayı önler.
let _bannedCache:     Set<string> = new Set();
let _bannedFetchedAt: number      = 0;
const BANNED_TTL_MS               = 10 * 60 * 1000; // 10 dakika

async function getBannedUids(): Promise<Set<string>> {
  const now = Date.now();
  if (now - _bannedFetchedAt < BANNED_TTL_MS && _bannedCache.size > 0) {
    return _bannedCache;
  }
  try {
    const { data } = await supabase
      .from('users')
      .select('uid')
      .eq('banned', true);
    _bannedCache     = new Set((data ?? []).map((r: any) => r.uid as string));
    _bannedFetchedAt = now;
    return _bannedCache;
  } catch {
    return _bannedCache; // hata → son bilinen listeyi kullan (fail-safe)
  }
}

// ── Row → Model dönüştürücüler (Android @SerialName → camelCase) ─────────────

function rowToAuthor(r: any): Author {
  return {
    id:            r.id             ?? '',
    name:          r.name           ?? '',
    bio:           r.bio            ?? '',
    photoURL:      r.photo_url      ?? '',
    birthYear:     r.birth_year     ?? 0,
    nationality:   r.nationality    ?? 0,
    bookCount:     r.book_count     ?? 0,
    quoteCount:    r.quote_count    ?? 0,
    reviewCount:   r.review_count   ?? 0,
    followerCount: r.follower_count ?? 0,
  };
}

function rowToBook(r: any): LibraryBook {
  return {
    id:          r.id           ?? '',
    title:       r.title        ?? '',
    authorId:    r.author_id    ?? '',
    authorName:  r.author_name  ?? '',
    coverImg:    r.cover_img    ?? '',
    genre:       r.genre        ?? '',
    publishYear: r.publish_year ?? 0,
    synopsis:    r.synopsis     ?? '',
    pageCount:   r.page_count   ?? 0,
    quoteCount:  r.quote_count  ?? 0,
    reviewCount: r.review_count ?? 0,
    avgRating:   r.avg_rating   ?? 0,
    likesCount:  0,
    ts:          r.created_at   ?? null,
  };
}

function rowToQuote(r: any): BookQuote {
  return {
    id:              r.id                ?? '',
    bookId:          r.book_id           ?? '',
    authorId:        r.author_id         ?? '',
    bookTitle:       r.book_title        ?? '',
    authorName:      r.author_name       ?? '',
    coverImg:        r.cover_img         ?? '',
    text:            r.text              ?? '',
    uid:             r.uid               ?? '',
    userDisplayName: r.user_display_name ?? '',
    userPhotoURL:    r.user_photo_url    ?? '',
    feedPostId:      r.feed_post_id      ?? '',
    visibility:      r.visibility        ?? 'public',
    likesCount:      0,  // feed_likes tablosundan hydrateQuoteLikes ile doldurulur
    ts:              r.created_at        ?? null,
    // client-side state
    isLikedByMe:     false,
  };
}

function rowToReview(r: any): BookReview {
  return {
    id:              r.id                ?? '',
    bookId:          r.book_id           ?? '',
    authorId:        r.author_id         ?? '',
    bookTitle:       r.book_title        ?? '',
    authorName:      r.author_name       ?? '',
    text:            r.text              ?? '',
    rating:          r.rating            ?? 0,
    uid:             r.uid               ?? '',
    userDisplayName: r.user_display_name ?? '',
    userPhotoURL:    r.user_photo_url    ?? '',
    feedPostId:      r.feed_post_id      ?? '',
    likesCount:      0,
    ts:              r.created_at        ?? null,
    isLikedByMe:     false,
  };
}

// ── Alıntı beğeni state'ini zenginleştir ─────────────────────────────────────
// Android: LibraryRepository.getQuoteLikeStates() — feed_likes tek kaynak.
// book_quotes.likes_count DEĞİL, feed_likes tablosunu sayıyoruz.

export async function hydrateQuoteLikes(
  quotes: BookQuote[],
  uid: string | null,
): Promise<BookQuote[]> {
  const feedPostIds = quotes.map(q => q.feedPostId).filter(Boolean);
  if (!feedPostIds.length) return quotes;

  const { data: rows } = await supabase
    .from('feed_likes')
    .select('post_id, uid')
    .in('post_id', feedPostIds);

  const counts  = new Map<string, number>();
  const likedBy = new Set<string>();

  for (const r of (rows ?? [])) {
    counts.set(r.post_id, (counts.get(r.post_id) ?? 0) + 1);
    if (r.uid === uid) likedBy.add(r.post_id);
  }

  return quotes.map(q => ({
    ...q,
    likesCount:  counts.get(q.feedPostId) ?? q.likesCount,
    isLikedByMe: likedBy.has(q.feedPostId),
  }));
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alıntılar — Supabase book_quotes (ARTIK FİREBASE DEĞİL)
// ─────────────────────────────────────────────────────────────────────────────

export interface QuotePage {
  quotes:  BookQuote[];
  offset:  number;
  hasMore: boolean;
}

/**
 * Kütüphane ana ekranı — Alıntılar sekmesi.
 * Android: LibraryRepository.getRecentQuotes()
 * Supabase book_quotes='active', banlı uid'ler dışlanır.
 * ÖNCEKİ: Firestore feed (type='library_quote') — YANLIŞ, artık kullanılmaz.
 */
export async function fetchRecentQuotes(offset = 0, limit = PAGE): Promise<QuotePage> {
  const banned = await getBannedUids();

  const { data } = await supabase
    .from('book_quotes')
    .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, created_at')
    .order('created_at', { ascending: false })
    .range(offset, offset + limit - 1);

  const filtered = (data ?? [])
    .filter((r: any) => !banned.has(r.uid))
    .map(rowToQuote);

  return { quotes: filtered, offset: offset + limit, hasMore: (data ?? []).length === limit };
}

/**
 * Kitap detayı — o kitaba ait alıntılar.
 * Android: LibraryRepository.getQuotesByBook()
 * Birincil kaynak: Supabase book_quotes.book_id
 * ÖNCEKİ: Firestore feed (libraryBookId) birincil, Supabase fallback — YANLIŞ.
 */
export async function fetchQuotesByBook(bookId: string, bookTitle?: string): Promise<BookQuote[]> {
  // 1. book_id ile Supabase'den çek (birincil kaynak)
  const { data } = await supabase
    .from('book_quotes')
    .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, created_at')
    .eq('book_id', bookId)
    .order('created_at', { ascending: false })
    .limit(50);

  if ((data ?? []).length > 0) return (data ?? []).map(rowToQuote);

  // 2. bookTitle ile ilike fallback (eski kayıtlar — book_id henüz yoksa)
  if (bookTitle?.trim()) {
    const { data: d2 } = await supabase
      .from('book_quotes')
      .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, created_at')
      .ilike('book_title', bookTitle.trim())
      .order('created_at', { ascending: false })
      .limit(50);
    return (d2 ?? []).map(rowToQuote);
  }

  return [];
}

/**
 * Yazar detayı — o yazara ait alıntılar.
 * Android: LibraryRepository.getActiveQuotesByAuthorName()
 * ÖNCEKİ: Firestore feed (authorName==) — YANLIŞ, ilike bile değildi.
 */
export async function fetchQuotesByAuthor(authorId: string, authorName?: string): Promise<BookQuote[]> {
  const banned = await getBannedUids();

  // Önce author_id ile dene
  if (authorId) {
    const { data } = await supabase
      .from('book_quotes')
      .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, created_at')
      .eq('author_id', authorId)
      .order('created_at', { ascending: false })
      .limit(50);

    const filtered = (data ?? []).filter((r: any) => !banned.has(r.uid));
    if (filtered.length > 0) return filtered.map(rowToQuote);
  }

  // Fallback: author_name ilike (Android: getActiveQuotesByAuthorName)
  if (authorName?.trim()) {
    const { data } = await supabase
      .from('book_quotes')
      .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, created_at')
      .ilike('author_name', authorName.trim())
      .order('created_at', { ascending: false })
      .limit(50);
    return (data ?? []).filter((r: any) => !banned.has(r.uid)).map(rowToQuote);
  }

  return [];
}

// ─────────────────────────────────────────────────────────────────────────────
//  İncelemeler — Supabase book_reviews
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Kütüphane ana ekranı — İncelemeler sekmesi.
 * Android: LibraryRepository.getRecentReviews()
 * Sıralama: created_at DESC (ÖNCEKİ: ts — YANLIŞ, sütun adı created_at)
 */
export async function fetchRecentReviews(limit = 50): Promise<BookReview[]> {
  const banned = await getBannedUids();

  const { data } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, feed_post_id, created_at')
    .order('created_at', { ascending: false })  // ← created_at, ts değil
    .limit(limit);

  return (data ?? []).filter((r: any) => !banned.has(r.uid)).map(rowToReview);
}

export async function fetchReviewsByBook(bookId: string): Promise<BookReview[]> {
  const { data } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, feed_post_id, created_at')
    .eq('book_id', bookId)
    .order('created_at', { ascending: false })
    .limit(50);
  return (data ?? []).map(rowToReview);
}

export async function fetchReviewsByAuthor(authorId: string): Promise<BookReview[]> {
  const { data } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, feed_post_id, created_at')
    .eq('author_id', authorId)
    .order('created_at', { ascending: false })
    .limit(50);
  return (data ?? []).map(rowToReview);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yazarlar — Supabase authors
// ─────────────────────────────────────────────────────────────────────────────

export async function fetchAuthors(limit = 200): Promise<Author[]> {
  const { data } = await supabase
    .from('authors')
    .select('id, name, bio, photo_url, birth_year, nationality, book_count, quote_count, review_count, follower_count')
    .order('name', { ascending: true })
    .limit(limit);
  return (data ?? []).map(rowToAuthor);
}

export async function fetchAuthorById(id: string): Promise<Author | null> {
  const { data } = await supabase
    .from('authors')
    .select('id, name, bio, photo_url, birth_year, nationality, book_count, quote_count, review_count, follower_count')
    .eq('id', id)
    .single();
  return data ? rowToAuthor(data) : null;
}

export async function fetchAuthorBooks(authorId: string): Promise<LibraryBook[]> {
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating, created_at')
    .eq('author_id', authorId)
    .order('created_at', { ascending: false });
  return (data ?? []).map(rowToBook);
}

/**
 * Compose QuoteDialog — yazar arama önerileri.
 * Android: LibraryRepository.searchAuthorsForQuote()
 */
export async function searchAuthors(q: string): Promise<Pick<Author, 'id' | 'name'>[]> {
  if (!q.trim()) return [];
  const { data, error } = await supabase
    .from('authors')
    .select('id, name')
    .ilike('name', `%${q.trim()}%`)
    .limit(8);

  if (error || !data?.length) {
    // Fallback: kitaplar üzerinden yazar adı bul
    const { data: bd } = await supabase
      .from('library_books')
      .select('author_name')
      .ilike('author_name', `%${q.trim()}%`)
      .limit(8);
    const unique = [...new Set((bd ?? []).map((r: any) => r.author_name as string).filter(Boolean))];
    return unique.map(name => ({ id: name, name }));
  }
  return (data ?? []).map((r: any) => ({ id: r.id, name: r.name }));
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kitaplar — Supabase library_books
// ─────────────────────────────────────────────────────────────────────────────

export async function fetchBooks(limit = 200): Promise<LibraryBook[]> {
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating, created_at')
    .order('title', { ascending: true })
    .limit(limit);
  return (data ?? []).map(rowToBook);
}

export async function fetchBookById(id: string): Promise<LibraryBook | null> {
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating, created_at')
    .eq('id', id)
    .single();
  return data ? rowToBook(data) : null;
}

/**
 * Compose QuoteDialog — kitap arama önerileri.
 * Android: LibraryRepository.searchBooksForQuote()
 */
export async function searchBooks(q: string): Promise<Pick<LibraryBook, 'id' | 'title' | 'authorName' | 'coverImg'>[]> {
  if (!q.trim()) return [];
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_name, cover_img')
    .ilike('title', `%${q.trim()}%`)
    .limit(8);
  return (data ?? []).map((r: any) => ({
    id:         r.id           ?? '',
    title:      r.title        ?? '',
    authorName: r.author_name  ?? '',
    coverImg:   r.cover_img    ?? '',
  }));
}

// ─────────────────────────────────────────────────────────────────────────────
//  Beğeni — feed_likes tek kaynak (Android: toggleQuoteFeedLike)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Alıntı veya incelemenin feed gönderisini beğen/geri al.
 * Android: LibraryRepository.toggleQuoteFeedLike / toggleReviewFeedLike
 * Dönüş: yeni { count, liked }
 */
export async function toggleLibraryItemLike(
  feedPostId: string,
  uid:        string,
  name:       string,
  photoUrl:   string,
): Promise<{ count: number; liked: boolean }> {
  if (!feedPostId || !uid) return { count: 0, liked: false };

  const { data: existing } = await supabase
    .from('feed_likes')
    .select('id')
    .eq('post_id', feedPostId)
    .eq('uid', uid)
    .maybeSingle();

  if (existing) {
    await supabase.from('feed_likes').delete()
      .eq('post_id', feedPostId).eq('uid', uid);
  } else {
    await supabase.from('feed_likes').upsert({
      id:        `${feedPostId}_${uid}`,
      post_id:   feedPostId,
      uid,
      name,
      photo_url: photoUrl,
    });
  }

  const { count } = await supabase
    .from('feed_likes')
    .select('id', { count: 'exact', head: true })
    .eq('post_id', feedPostId);

  return { count: count ?? 0, liked: !existing };
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yazar takip — author_follows
// ─────────────────────────────────────────────────────────────────────────────

export async function checkAuthorFollow(uid: string, authorId: string): Promise<boolean> {
  const { data } = await supabase
    .from('author_follows')
    .select('user_id')
    .eq('user_id', uid)
    .eq('author_id', authorId)
    .maybeSingle();
  return !!data;
}

export async function followAuthor(uid: string, authorId: string): Promise<void> {
  await supabase.from('author_follows').upsert({ user_id: uid, author_id: authorId });
  // Sayacı gerçek değerle güncelle (Android: countFollowers → upsert)
  const { count } = await supabase
    .from('author_follows')
    .select('user_id', { count: 'exact', head: true })
    .eq('author_id', authorId);
  await supabase.from('authors').update({ follower_count: count ?? 0 }).eq('id', authorId);
}

export async function unfollowAuthor(uid: string, authorId: string): Promise<void> {
  await supabase.from('author_follows').delete()
    .eq('user_id', uid).eq('author_id', authorId);
  const { count } = await supabase
    .from('author_follows')
    .select('user_id', { count: 'exact', head: true })
    .eq('author_id', authorId);
  await supabase.from('authors').update({ follower_count: count ?? 0 }).eq('id', authorId);
}

// ─────────────────────────────────────────────────────────────────────────────
//  İnceleme ekle
// ─────────────────────────────────────────────────────────────────────────────

export async function addBookReview(params: {
  bookId:          string;
  authorId:        string;
  bookTitle:       string;
  authorName:      string;
  text:            string;
  rating:          number;
  uid:             string;
  userDisplayName: string;
  userPhotoURL:    string;
}): Promise<BookReview | null> {
  const { data, error } = await supabase
    .from('book_reviews')
    .insert({
      book_id:           params.bookId,
      author_id:         params.authorId,
      book_title:        params.bookTitle,
      author_name:       params.authorName,
      text:              params.text,
      rating:            params.rating,
      uid:               params.uid,
      user_display_name: params.userDisplayName,
      user_photo_url:    params.userPhotoURL,
    })
    .select()
    .single();
  if (error) throw error;
  return rowToReview(data);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Admin — Yazar / Kitap ekle
// ─────────────────────────────────────────────────────────────────────────────

export async function createAuthor(params: {
  name:        string;
  bio:         string;
  photoURL:    string;
  birthYear:   number;
  nationality: string;
}): Promise<Author | null> {
  const { data, error } = await supabase
    .from('authors')
    .insert({
      name:        params.name,
      bio:         params.bio,
      photo_url:   params.photoURL,
      birth_year:  params.birthYear,
      nationality: params.nationality,
    })
    .select()
    .single();
  if (error) throw error;
  return rowToAuthor(data);
}

export async function createLibraryBook(params: {
  title:       string;
  authorId:    string;
  authorName:  string;
  synopsis:    string;
  genre:       string;
  publishYear: number;
  pageCount:   number;
  coverImg:    string;
}): Promise<LibraryBook | null> {
  const { data, error } = await supabase
    .from('library_books')
    .insert({
      title:        params.title,
      author_id:    params.authorId || null,
      author_name:  params.authorName,
      synopsis:     params.synopsis,
      genre:        params.genre,
      publish_year: params.publishYear,
      page_count:   params.pageCount,
      cover_img:    params.coverImg,
    })
    .select()
    .single();
  if (error) throw error;
  return rowToBook(data);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Reading Status — Supabase reading_status
// ─────────────────────────────────────────────────────────────────────────────

export type ReadStatus = 'okuyorum' | 'okudum' | 'okuyacağım';

export async function upsertReadingStatus(params: {
  uid:        string;
  bookId:     string;
  title:      string;
  coverImg:   string;
  authorName: string;
  source:     'serial' | 'book' | 'library';
  status:     ReadStatus;
}): Promise<void> {
  await supabase.from('reading_status').upsert({
    uid:         params.uid,
    book_id:     params.bookId,
    title:       params.title,
    cover_img:   params.coverImg,
    author_name: params.authorName,
    source:      params.source,
    status:      params.status,
    updated_at:  new Date().toISOString(),
  });
}

export async function fetchReadingStatus(uid: string): Promise<any[]> {
  const { data } = await supabase
    .from('reading_status')
    .select('*')
    .eq('uid', uid)
    .order('updated_at', { ascending: false })
    .limit(50);
  return data ?? [];
}
