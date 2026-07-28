// Android LibraryRepository + LibraryViewModel karşılığı
// Supabase tabloları: authors, library_books, book_quotes, book_reviews, author_follows
// Firebase tablosu: feed (type="library_quote")

import { supabase }       from '$lib/supabase/config';
import { db }             from '$lib/firebase/config';
import {
  collection, query, where, orderBy, limit,
  getDocs, startAfter, type QueryDocumentSnapshot,
} from 'firebase/firestore';
import type { Author, LibraryBook, BookQuote, BookReview } from '$lib/models/library';

const PAGE = 20;

// ── Supabase sütun adları (Android @SerialName karşılığı) ─────────────────
// authors      : id, name, bio, photo_url, birth_year, nationality, book_count, quote_count, review_count, follower_count
// library_books: id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating
// book_quotes  : id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, feed_post_id, visibility, likes_count, ts
// book_reviews : id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, feed_post_id, likes_count, ts
// author_follows: user_id, author_id

// ─────────────────────────────────────────────────────────────────────────────
//  Row → Model dönüştürücüler (Android @SerialName snake_case → camelCase)
// ─────────────────────────────────────────────────────────────────────────────
function rowToAuthor(r: any): Author {
  return {
    id:            r.id            ?? '',
    name:          r.name          ?? '',
    bio:           r.bio           ?? '',
    photoURL:      r.photo_url     ?? '',
    birthYear:     r.birth_year    ?? 0,
    nationality:   r.nationality   ?? '',
    bookCount:     r.book_count    ?? 0,
    quoteCount:    r.quote_count   ?? 0,
    reviewCount:   r.review_count  ?? 0,
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
    likesCount:  r.likes_count  ?? 0,
    ts:          r.created_at   ?? null,
  };
}

function rowToBookQuote(r: any): BookQuote {
  return {
    id:              r.id               ?? '',
    bookId:          r.book_id          ?? '',
    authorId:        r.author_id        ?? '',
    bookTitle:       r.book_title       ?? '',
    authorName:      r.author_name      ?? '',
    coverImg:        r.cover_img        ?? '',
    text:            r.text             ?? '',
    uid:             r.uid              ?? '',
    userDisplayName: r.user_display_name ?? '',
    userPhotoURL:    r.user_photo_url   ?? '',
    feedPostId:      r.feed_post_id     ?? '',
    visibility:      r.visibility       ?? 'public',
    likesCount:      r.likes_count      ?? 0,
    ts:              r.ts               ?? null,
  };
}

function rowToBookReview(r: any): BookReview {
  return {
    id:              r.id               ?? '',
    bookId:          r.book_id          ?? '',
    authorId:        r.author_id        ?? '',
    bookTitle:       r.book_title       ?? '',
    authorName:      r.author_name      ?? '',
    text:            r.text             ?? '',
    rating:          r.rating           ?? 0,
    uid:             r.uid              ?? '',
    userDisplayName: r.user_display_name ?? '',
    userPhotoURL:    r.user_photo_url   ?? '',
    feedPostId:      r.feed_post_id     ?? '',
    likesCount:      r.likes_count      ?? 0,
    ts:              r.ts               ?? null,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
//  Alıntılar — Firebase feed collectionGroup (Android: feedVm.libraryQuotes)
// ─────────────────────────────────────────────────────────────────────────────
export interface LibraryQuotePage {
  posts:   any[];
  lastDoc: QueryDocumentSnapshot | null;
  hasMore: boolean;
}

export async function fetchLibraryQuotes(lastDoc?: QueryDocumentSnapshot | null): Promise<LibraryQuotePage> {
  const constraints: any[] = [
    where('type', '==', 'library_quote'),
    orderBy('ts', 'desc'),
    limit(PAGE),
  ];
  if (lastDoc) constraints.push(startAfter(lastDoc));
  const snap = await getDocs(query(collection(db, 'feed'), ...constraints));
  return {
    posts:   snap.docs.map(d => ({ id: d.id, ...d.data() })),
    lastDoc: snap.docs[snap.docs.length - 1] ?? null,
    hasMore: snap.docs.length === PAGE,
  };
}

// ─────────────────────────────────────────────────────────────────────────────
//  İncelemeler — Supabase book_reviews
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchReviews(): Promise<BookReview[]> {
  const { data, error } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, feed_post_id, likes_count, ts')
    .order('ts', { ascending: false })
    .limit(50);
  if (error) throw error;
  return (data ?? []).map(rowToBookReview);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Yazarlar — Supabase authors
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchAuthors(): Promise<Author[]> {
  const { data, error } = await supabase
    .from('authors')
    .select('id, name, bio, photo_url, birth_year, nationality, book_count, quote_count, review_count, follower_count')
    .order('name', { ascending: true })
    .limit(200);
  if (error) throw error;
  return (data ?? []).map(rowToAuthor);
}

export async function fetchAuthorById(id: string): Promise<Author | null> {
  const { data, error } = await supabase
    .from('authors')
    .select('id, name, bio, photo_url, birth_year, nationality, book_count, quote_count, review_count, follower_count')
    .eq('id', id)
    .single();
  if (error) return null;
  return rowToAuthor(data);
}

export async function fetchAuthorBooks(authorId: string): Promise<LibraryBook[]> {
  const { data, error } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating')
    .eq('author_id', authorId)
    .order('publish_year', { ascending: false });
  if (error) throw error;
  return (data ?? []).map(rowToBook);
}

export async function fetchAuthorReviews(authorId: string): Promise<BookReview[]> {
  const { data } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, likes_count, ts')
    .eq('author_id', authorId)
    .order('ts', { ascending: false })
    .limit(30);
  return (data ?? []).map(rowToBookReview);
}

export async function fetchAuthorQuotesFromFeed(authorName: string): Promise<any[]> {
  try {
    const snap = await getDocs(query(
      collection(db, 'feed'),
      where('type', '==', 'library_quote'),
      where('authorName', '==', authorName),
      orderBy('ts', 'desc'),
      limit(30),
    ));
    return snap.docs.map(d => ({ id: d.id, ...d.data() }));
  } catch { return []; }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Takip — author_follows
// ─────────────────────────────────────────────────────────────────────────────
export async function checkAuthorFollow(uid: string, authorId: string): Promise<boolean> {
  const { data } = await supabase
    .from('author_follows')
    .select('user_id')
    .eq('user_id', uid)
    .eq('author_id', authorId)
    .single();
  return !!data;
}

export async function followAuthor(uid: string, authorId: string): Promise<void> {
  await supabase.from('author_follows').insert({ user_id: uid, author_id: authorId });
}

export async function unfollowAuthor(uid: string, authorId: string): Promise<void> {
  await supabase.from('author_follows').delete().eq('user_id', uid).eq('author_id', authorId);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Kitaplar — Supabase library_books
// ─────────────────────────────────────────────────────────────────────────────
export async function fetchBooks(): Promise<LibraryBook[]> {
  const { data, error } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating')
    .order('title', { ascending: true })
    .limit(200);
  if (error) throw error;
  return (data ?? []).map(rowToBook);
}

export async function fetchBookById(id: string): Promise<LibraryBook | null> {
  const { data, error } = await supabase
    .from('library_books')
    .select('id, title, author_id, author_name, cover_img, genre, publish_year, synopsis, page_count, quote_count, review_count, avg_rating')
    .eq('id', id)
    .single();
  if (error) return null;
  return rowToBook(data);
}

export async function fetchBookQuotes(bookId: string, bookTitle?: string): Promise<any[]> {
  // Önce libraryBookId ile Firebase'den
  try {
    const snap = await getDocs(query(
      collection(db, 'feed'),
      where('type', '==', 'library_quote'),
      where('libraryBookId', '==', bookId),
      orderBy('ts', 'desc'),
      limit(30),
    ));
    if (snap.docs.length > 0) return snap.docs.map(d => ({ id: d.id, ...d.data() }));
  } catch {}

  // Fallback: bookName ile
  if (bookTitle) {
    try {
      const snap = await getDocs(query(
        collection(db, 'feed'),
        where('type', '==', 'library_quote'),
        where('bookName', '==', bookTitle),
        orderBy('ts', 'desc'),
        limit(30),
      ));
      return snap.docs.map(d => ({ id: d.id, ...d.data() }));
    } catch {}
  }

  // Son çare: Supabase book_quotes
  const { data } = await supabase
    .from('book_quotes')
    .select('id, book_id, author_id, book_title, author_name, cover_img, text, uid, user_display_name, user_photo_url, likes_count, ts')
    .eq('book_id', bookId)
    .order('ts', { ascending: false })
    .limit(30);
  return (data ?? []).map(rowToBookQuote);
}

export async function fetchBookReviews(bookId: string): Promise<BookReview[]> {
  const { data } = await supabase
    .from('book_reviews')
    .select('id, book_id, author_id, book_title, author_name, text, rating, uid, user_display_name, user_photo_url, likes_count, ts')
    .eq('book_id', bookId)
    .order('ts', { ascending: false })
    .limit(30);
  return (data ?? []).map(rowToBookReview);
}

// ─────────────────────────────────────────────────────────────────────────────
//  İnceleme ekle — Supabase book_reviews
// ─────────────────────────────────────────────────────────────────────────────
export async function addBookReview(params: {
  bookId:         string;
  authorId:       string;
  bookTitle:      string;
  authorName:     string;
  text:           string;
  rating:         number;
  uid:            string;
  userDisplayName: string;
  userPhotoURL:   string;
}): Promise<BookReview | null> {
  const { data, error } = await supabase
    .from('book_reviews')
    .insert({
      book_id:          params.bookId,
      author_id:        params.authorId,
      book_title:       params.bookTitle,
      author_name:      params.authorName,
      text:             params.text,
      rating:           params.rating,
      uid:              params.uid,
      user_display_name: params.userDisplayName,
      user_photo_url:   params.userPhotoURL,
    })
    .select()
    .single();
  if (error) throw error;
  return rowToBookReview(data);
}

// ─────────────────────────────────────────────────────────────────────────────
//  Admin — Yazar ekle
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

// ─────────────────────────────────────────────────────────────────────────────
//  Admin — Kitap ekle (Android LibraryAdminAddBookDialog)
// ─────────────────────────────────────────────────────────────────────────────
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
      author_id:    params.authorId,
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
//  Arama — compose QuoteDialog için (Android searchBooksForQuote / searchAuthorsForQuote)
// ─────────────────────────────────────────────────────────────────────────────
export async function searchBooks(q: string): Promise<Pick<LibraryBook, 'id' | 'title' | 'authorName' | 'coverImg'>[]> {
  const { data } = await supabase
    .from('library_books')
    .select('id, title, author_name, cover_img')
    .ilike('title', `%${q.trim()}%`)
    .limit(8);
  return (data ?? []).map(r => ({
    id:         r.id,
    title:      r.title,
    authorName: r.author_name ?? '',
    coverImg:   r.cover_img   ?? '',
  }));
}

export async function searchAuthors(q: string): Promise<Pick<Author, 'id' | 'name'>[]> {
  const { data, error } = await supabase
    .from('authors')
    .select('id, name')
    .ilike('name', `%${q.trim()}%`)
    .limit(8);
  if (error || !data?.length) {
    // Fallback: library_books'tan author_name
    const { data: bd } = await supabase
      .from('library_books')
      .select('author_name')
      .ilike('author_name', `%${q.trim()}%`)
      .limit(8);
    const unique = [...new Set((bd ?? []).map((r: any) => r.author_name as string).filter(Boolean))];
    return unique.map(name => ({ id: name, name }));
  }
  return (data ?? []).map(r => ({ id: r.id, name: r.name }));
}
