// Android SearchViewModel karşılığı
// 6 tip: post · user · serial · library_book · book_quote · author
import { collection, query, where, orderBy, limit, getDocs } from 'firebase/firestore';
import { db } from '$lib/firebase/config';
import { supabase } from '$lib/supabase/config';
import { getOrFetch } from '$lib/utils/cache';

// Arama kutusu 280ms debounce'lu (bkz. routes/search/+page.svelte); aynı
// sorgu kısa süre içinde tekrarlanırsa (geri git-gel, yazıp silip tekrar
// yazma) hem Firestore hem Supabase'e tekrar gidilmesin diye 30sn TTL.
const SEARCH_TTL_MS      = 30_000;
const SUGGESTIONS_TTL_MS = 5 * 60_000;

export type SearchResultType = 'post' | 'user' | 'serial' | 'library_book' | 'book_quote' | 'library_author';

export interface SearchResult {
  id:          string;
  type:        SearchResultType;
  title:       string;
  subtitle?:   string;
  imageUrl?:   string;
  href:        string;
}

export async function search(q: string): Promise<SearchResult[]> {
  if (!q || q.trim().length < 2) return [];
  const qLower = q.trim().toLowerCase();

  return getOrFetch(`search_${qLower}`, SEARCH_TTL_MS, async () => {
    const results: SearchResult[] = [];
    await Promise.all([
      searchFirebase(qLower, results),
      searchSupabase(qLower, results),
    ]);

    // Tekrar önle, tip sırası: user → post → serial → book → quote → author
    const seen = new Set<string>();
    const order: SearchResultType[] = ['user','post','serial','library_book','book_quote','library_author'];
    return order.flatMap(type =>
      results.filter(r => r.type === type && !seen.has(r.id) && seen.add(r.id))
    );
  });
}

async function searchFirebase(q: string, out: SearchResult[]) {
  try {
    // Feed posts — title/text ILIKE benzer (başlangıç eşleşmesi)
    const snap = await getDocs(query(
      collection(db, 'feed'),
      where('title', '>=', q),
      where('title', '<=', q + '\uf8ff'),
      orderBy('title'), limit(8),
    ));
    snap.forEach(d => {
      const data = d.data();
      out.push({ id: d.id, type: 'post', title: data.title || data.text?.slice(0,60) || '', subtitle: data.displayName, imageUrl: data.imageUrl || '', href: `/post/${d.id}` });
    });
  } catch(e) { console.error('search firebase:', e); }
}

async function searchSupabase(q: string, out: SearchResult[]) {
  const pattern = `%${q}%`;
  const [usersR, authorsR, booksR, quotesR] = await Promise.allSettled([
    // Kullanıcılar
    supabase.from('users').select('uid,display_name,username,photo_url')
      .or(`display_name.ilike.${pattern},username.ilike.${pattern}`).limit(8),
    // Yazarlar
    supabase.from('authors').select('id,name,photo_url,nationality')
      .ilike('name', pattern).limit(8),
    // Kitaplar
    supabase.from('library_books').select('id,title,author_name,cover_img')
      .ilike('title', pattern).limit(8),
    // Alıntılar
    supabase.from('book_quotes').select('id,text,book_title,author_name,feed_post_id')
      .ilike('text', pattern).not('feed_post_id', 'eq', '').limit(6),
  ]);

  if (usersR.status === 'fulfilled') {
    (usersR.value.data ?? []).forEach((u: any) => out.push({
      id: u.uid, type: 'user', title: u.display_name || u.username, subtitle: `@${u.username}`,
      imageUrl: u.photo_url, href: `/profile/${u.uid}`,
    }));
  }
  if (authorsR.status === 'fulfilled') {
    (authorsR.value.data ?? []).forEach((a: any) => out.push({
      id: a.id, type: 'library_author', title: a.name, subtitle: a.nationality,
      imageUrl: a.photo_url, href: `/library/author/${a.id}`,
    }));
  }
  if (booksR.status === 'fulfilled') {
    (booksR.value.data ?? []).forEach((b: any) => out.push({
      id: b.id, type: 'library_book', title: b.title, subtitle: b.author_name,
      imageUrl: b.cover_img, href: `/library/book/${b.id}`,
    }));
  }
  if (quotesR.status === 'fulfilled') {
    (quotesR.value.data ?? []).forEach((r: any) => out.push({
      id: r.id, type: 'book_quote', title: `"${r.text?.slice(0,80)}"`,
      subtitle: [r.author_name, r.book_title].filter(Boolean).join(' · '),
      href: `/post/${r.feed_post_id}`,
    }));
  }
}

// Önerilen kullanıcılar (arama kutusu boşken)
export async function fetchSuggestions(): Promise<SearchResult[]> {
  return getOrFetch('search_suggestions', SUGGESTIONS_TTL_MS, async () => {
    const { data } = await supabase.from('users')
      .select('uid,display_name,username,photo_url')
      .order('followers_count', { ascending: false }).limit(12);
    return (data ?? []).map((u: any) => ({
      id: u.uid, type: 'user' as const, title: u.display_name || u.username,
      subtitle: `@${u.username}`, imageUrl: u.photo_url, href: `/profile/${u.uid}`,
    }));
  });
}
