// Android BlogViewModel karşılığı — Google Blogger v3 API
// Blog ID ve API key environment variable'dan gelir

const BLOG_ID  = import.meta.env.VITE_BLOGGER_BLOG_ID  ?? '';
const API_KEY  = import.meta.env.VITE_BLOGGER_API_KEY   ?? '';
const BASE_URL = 'https://www.googleapis.com/blogger/v3';

export interface BlogPost {
  id:         string;
  title:      string;
  published:  string;
  updated:    string;
  url:        string;
  labels:     string[];
  thumbnail:  string;
  summary:    string;  // HTML stripped, ilk 200 karakter
  content:    string;  // full HTML (detay sayfasında)
  author:     { name: string; imageUrl: string };
}

function extractFirstImage(html: string): string {
  const m = html.match(/<img[^>]+src=["']([^"']+)["']/i);
  return m?.[1] ?? '';
}

function stripHtml(html: string): string {
  return html.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
}

function parsePost(item: any, full = false): BlogPost {
  const labels   = item.labels ?? [];
  const content  = item.content ?? item.summary?.content ?? '';
  return {
    id:        item.id,
    title:     item.title ?? '',
    published: item.published ?? '',
    updated:   item.updated ?? '',
    url:       item.url ?? '',
    labels,
    thumbnail: extractFirstImage(content),
    summary:   stripHtml(content).slice(0, 200),
    content:   full ? content : '',
    author: {
      name:     item.author?.displayName ?? '',
      imageUrl: item.author?.image?.url  ?? '',
    },
  };
}

export async function fetchBlogPosts(label?: string, pageToken?: string): Promise<{
  posts: BlogPost[]; nextToken: string | null;
}> {
  const url = new URL(`${BASE_URL}/blogs/${BLOG_ID}/posts`);
  url.searchParams.set('key',       API_KEY);
  url.searchParams.set('maxResults','10');
  url.searchParams.set('fetchBodies','true');
  url.searchParams.set('status',    'live');
  if (label)     url.searchParams.set('labels', label);
  if (pageToken) url.searchParams.set('pageToken', pageToken);

  const res  = await fetch(url.toString());
  const json = await res.json();
  return {
    posts:     (json.items ?? []).map((i: any) => parsePost(i)),
    nextToken: json.nextPageToken ?? null,
  };
}

export async function fetchBlogPost(id: string): Promise<BlogPost | null> {
  const url = `${BASE_URL}/blogs/${BLOG_ID}/posts/${id}?key=${API_KEY}&fetchBodies=true`;
  const res  = await fetch(url);
  if (!res.ok) return null;
  return parsePost(await res.json(), true);
}
