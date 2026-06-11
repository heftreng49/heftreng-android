-- ══════════════════════════════════════════════════════════════════════
--  Heftreng — Supabase Schema  (idempotent — defalarca çalıştırılabilir)
-- ══════════════════════════════════════════════════════════════════════

-- ── Yardımcı: policy varsa sil, yoksa geç ────────────────────────────
-- PostgreSQL'de "create policy if not exists" yok, drop + create yapıyoruz.

-- ── Authors ───────────────────────────────────────────────────────────
create table if not exists authors (
    id            text primary key,
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

create index if not exists authors_fts           on authors using gin(to_tsvector('simple', name));
create index if not exists authors_name_lower_idx on authors (name_lower);

alter table authors enable row level security;
drop policy if exists "public_read_authors" on authors;
create policy "public_read_authors" on authors for select using (true);

-- ── Library Books ─────────────────────────────────────────────────────
create table if not exists library_books (
    id            text primary key,
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

create index if not exists library_books_fts           on library_books using gin(to_tsvector('simple', title));
create index if not exists library_books_title_lower_idx on library_books (title_lower);
create index if not exists library_books_author_id_idx   on library_books (author_id);

alter table library_books enable row level security;
drop policy if exists "public_read_library_books" on library_books;
create policy "public_read_library_books" on library_books for select using (true);

-- ── Book Quotes ───────────────────────────────────────────────────────
create table if not exists book_quotes (
    id                text primary key,
    book_id           text references library_books(id) on delete cascade,
    author_id         text references authors(id) on delete set null,
    book_title        text    default '',
    author_name       text    default '',
    text              text    not null,
    uid               text    default '',
    user_display_name text    default '',
    user_photo_url    text    default '',
    feed_post_id      text    default '',
    likes_count       int     default 0,
    created_at        timestamptz default now()
);

create index if not exists book_quotes_book_id_idx    on book_quotes(book_id);
create index if not exists book_quotes_author_id_idx  on book_quotes(author_id);
create index if not exists book_quotes_uid_idx        on book_quotes(uid);
create index if not exists book_quotes_text_search_idx
    on book_quotes using gin(to_tsvector('simple', text));

alter table book_quotes enable row level security;
drop policy if exists "book_quotes_read" on book_quotes;
create policy "book_quotes_read" on book_quotes for select using (true);

-- ── Book Reviews ──────────────────────────────────────────────────────
create table if not exists book_reviews (
    id                text primary key,
    book_id           text references library_books(id) on delete cascade,
    author_id         text references authors(id) on delete set null,
    book_title        text    default '',
    author_name       text    default '',
    text              text    not null,
    rating            float   default 0,
    uid               text    default '',
    user_display_name text    default '',
    user_photo_url    text    default '',
    feed_post_id      text    default '',
    likes_count       int     default 0,
    created_at        timestamptz default now()
);

create index if not exists book_reviews_book_id_idx   on book_reviews(book_id);
create index if not exists book_reviews_author_id_idx on book_reviews(author_id);
create index if not exists book_reviews_uid_idx       on book_reviews(uid);
create index if not exists book_reviews_rating_idx    on book_reviews(rating);

alter table book_reviews enable row level security;
drop policy if exists "book_reviews_read" on book_reviews;
create policy "book_reviews_read" on book_reviews for select using (true);

-- ── Author Follows ────────────────────────────────────────────────────
create table if not exists author_follows (
    author_id  text not null references authors(id) on delete cascade,
    user_id    text not null,
    created_at timestamptz default now(),
    primary key (author_id, user_id)
);

create index if not exists author_follows_user_idx on author_follows(user_id);

alter table author_follows enable row level security;
drop policy if exists "author_follows_read" on author_follows;
create policy "author_follows_read" on author_follows for select using (true);
