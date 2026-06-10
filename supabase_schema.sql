-- ══════════════════════════════════════════════════════════════
--  Heftreng — Supabase Schema (Adım 1: Library arama tabloları)
--  Dashboard → SQL Editor → New query → buraya yapıştır → Run
-- ══════════════════════════════════════════════════════════════

-- ── Yazarlar ─────────────────────────────────────────────────
create table if not exists authors (
    id            text primary key,          -- Firestore doc id (geçiş kolaylığı için)
    name          text not null,
    name_lower    text generated always as (lower(name)) stored,
    bio           text    default '',
    photo_url     text    default '',
    birth_year    int     default 0,
    nationality   text    default '',
    book_count    int     default 0,
    quote_count   int     default 0,
    review_count  int     default 0,
    follower_count int    default 0,
    created_at    timestamptz default now()
);

-- Türkçe/Kürtçe karakterleri de kapsayan simple full-text index
create index if not exists authors_fts
    on authors using gin(to_tsvector('simple', name));

-- prefix araması için btree index (ilike '%q%' yerine ilike 'q%' da çalışır)
create index if not exists authors_name_lower_idx on authors (name_lower);

-- ── Kütüphane Kitapları ──────────────────────────────────────
create table if not exists library_books (
    id            text primary key,          -- Firestore doc id
    title         text not null,
    title_lower   text generated always as (lower(title)) stored,
    author_id     text references authors(id) on delete set null,
    author_name   text    default '',
    cover_img     text    default '',
    genre         text    default '',
    publish_year  int     default 0,
    synopsis      text    default '',
    page_count    int     default 0,
    quote_count   int     default 0,
    review_count  int     default 0,
    avg_rating    float   default 0,
    created_at    timestamptz default now()
);

create index if not exists library_books_fts
    on library_books using gin(to_tsvector('simple', title));

create index if not exists library_books_title_lower_idx on library_books (title_lower);
create index if not exists library_books_author_id_idx   on library_books (author_id);

-- ── Row Level Security — sadece okuma açık (anonim erişim) ──
alter table authors       enable row level security;
alter table library_books enable row level security;

-- Herkes okuyabilir (anon key yeterli)
create policy "public_read_authors"
    on authors for select using (true);

create policy "public_read_library_books"
    on library_books for select using (true);

-- Yazma şimdilik kapalı — sonraki adımda Firebase'den sync eklenecek
-- (service_role key ile server-side yazılacak)
