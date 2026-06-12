-- ══════════════════════════════════════════════════════════════
--  Heftreng — Supabase Schema (tam versiyon)
--  Dashboard → SQL Editor → New query → yapıştır → Run
--  Güvenli: IF NOT EXISTS — defalarca çalıştırılabilir
-- ══════════════════════════════════════════════════════════════

-- ── Authors ───────────────────────────────────────────────────
create table if not exists authors (
    id             text primary key,
    name           text not null,
    name_lower     text generated always as (lower(name)) stored,
    bio            text    default '',
    photo_url      text    default '',
    birth_year     int     default 0,
    nationality    text    default '',
    book_count     int     default 0,
    quote_count    int     default 0,
    review_count   int     default 0,
    follower_count int     default 0,
    created_at     timestamptz default now()
);
create index if not exists authors_fts           on authors using gin(to_tsvector('simple', name));
create index if not exists authors_name_lower_idx on authors (name_lower);

-- ── Library Books ─────────────────────────────────────────────
create table if not exists library_books (
    id           text primary key,
    title        text not null,
    title_lower  text generated always as (lower(title)) stored,
    author_id    text references authors(id) on delete set null,
    author_name  text    default '',
    cover_img    text    default '',
    genre        text    default '',
    publish_year int     default 0,
    synopsis     text    default '',
    page_count   int     default 0,
    quote_count  int     default 0,
    review_count int     default 0,
    avg_rating   float   default 0,
    created_at   timestamptz default now()
);
create index if not exists library_books_fts             on library_books using gin(to_tsvector('simple', title));
create index if not exists library_books_title_lower_idx on library_books (title_lower);
create index if not exists library_books_author_id_idx   on library_books (author_id);

-- ── Book Quotes ───────────────────────────────────────────────
create table if not exists book_quotes (
    id                text primary key,
    book_id           text not null references library_books(id) on delete cascade,
    author_id         text references authors(id) on delete set null,
    book_title        text    default '',
    author_name       text    default '',
    text              text    not null,
    uid               text    not null default '',
    user_display_name text    default '',
    user_photo_url    text    default '',
    feed_post_id      text    default '',
    likes_count       int     default 0,
    created_at        timestamptz default now()
);
create index if not exists book_quotes_book_id_idx   on book_quotes (book_id);
create index if not exists book_quotes_author_id_idx on book_quotes (author_id);
create index if not exists book_quotes_uid_idx       on book_quotes (uid);

-- ── Book Reviews ──────────────────────────────────────────────
create table if not exists book_reviews (
    id                text primary key,
    book_id           text not null references library_books(id) on delete cascade,
    author_id         text references authors(id) on delete set null,
    book_title        text    default '',
    author_name       text    default '',
    text              text    not null,
    rating            float   default 0,
    uid               text    not null default '',
    user_display_name text    default '',
    user_photo_url    text    default '',
    feed_post_id      text    default '',
    likes_count       int     default 0,
    created_at        timestamptz default now()
);
create index if not exists book_reviews_book_id_idx   on book_reviews (book_id);
create index if not exists book_reviews_author_id_idx on book_reviews (author_id);
create index if not exists book_reviews_uid_idx       on book_reviews (uid);

-- ── Author Follows ────────────────────────────────────────────
create table if not exists author_follows (
    author_id  text not null references authors(id) on delete cascade,
    user_id    text not null,
    created_at timestamptz default now(),
    primary key (author_id, user_id)
);
create index if not exists author_follows_user_id_idx on author_follows (user_id);

-- ── Row Level Security ────────────────────────────────────────
alter table authors        enable row level security;
alter table library_books  enable row level security;
alter table book_quotes    enable row level security;
alter table book_reviews   enable row level security;
alter table author_follows enable row level security;

-- Herkes okuyabilir
do $$ begin
  if not exists (select 1 from pg_policies where tablename='authors'        and policyname='public_read_authors')        then create policy "public_read_authors"        on authors        for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='library_books'  and policyname='public_read_library_books')  then create policy "public_read_library_books"  on library_books  for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='book_quotes'    and policyname='public_read_book_quotes')    then create policy "public_read_book_quotes"    on book_quotes    for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='book_reviews'   and policyname='public_read_book_reviews')   then create policy "public_read_book_reviews"   on book_reviews   for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='author_follows' and policyname='public_read_author_follows') then create policy "public_read_author_follows" on author_follows  for select using (true); end if;
end $$;

-- Yazma: service_role key ile (workflow'dan, Android'dan değil)
-- Android anon key sadece okuma yapabilir — güvenlik için kasıtlı

-- ── Yazma politikaları (Android anon key ile) ─────────────────
-- Giriş yapmış kullanıcılar kendi verilerini yazabilir
do $$ begin
  if not exists (select 1 from pg_policies where tablename='book_quotes' and policyname='insert_book_quotes') then
    create policy "insert_book_quotes" on book_quotes for insert with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='book_reviews' and policyname='insert_book_reviews') then
    create policy "insert_book_reviews" on book_reviews for insert with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='author_follows' and policyname='insert_author_follows') then
    create policy "insert_author_follows" on author_follows for insert with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='author_follows' and policyname='delete_author_follows') then
    create policy "delete_author_follows" on author_follows for delete using (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='authors' and policyname='upsert_authors') then
    create policy "upsert_authors" on authors for all using (true) with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='library_books' and policyname='upsert_library_books') then
    create policy "upsert_library_books" on library_books for all using (true) with check (true);
  end if;
end $$;

-- ── Follows ───────────────────────────────────────────────────
create table if not exists follows (
    id           text primary key default gen_random_uuid()::text,
    from_uid     text not null,
    from_name    text default '',
    from_photo   text default '',
    target_uid   text not null,
    target_name  text default '',
    target_photo text default '',
    created_at   timestamptz default now(),
    unique(from_uid, target_uid)
);
create index if not exists follows_from_uid_idx    on follows (from_uid);
create index if not exists follows_target_uid_idx  on follows (target_uid);

-- ── Feed Likes ────────────────────────────────────────────────
create table if not exists feed_likes (
    id         text primary key default gen_random_uuid()::text,
    post_id    text not null,
    uid        text not null,
    name       text default '',
    photo_url  text default '',
    created_at timestamptz default now(),
    unique(post_id, uid)
);
create index if not exists feed_likes_post_id_idx on feed_likes (post_id);
create index if not exists feed_likes_uid_idx     on feed_likes (uid);

-- ── Feed Saves ────────────────────────────────────────────────
create table if not exists feed_saves (
    id         text primary key default gen_random_uuid()::text,
    post_id    text not null,
    uid        text not null,
    created_at timestamptz default now(),
    unique(post_id, uid)
);
create index if not exists feed_saves_post_id_idx on feed_saves (post_id);
create index if not exists feed_saves_uid_idx     on feed_saves (uid);

-- ── Comment Likes ─────────────────────────────────────────────
create table if not exists comment_likes (
    id         text primary key default gen_random_uuid()::text,
    comment_id text not null,
    uid        text not null,
    name       text default '',
    photo_url  text default '',
    created_at timestamptz default now(),
    unique(comment_id, uid)
);
create index if not exists comment_likes_comment_id_idx on comment_likes (comment_id);
create index if not exists comment_likes_uid_idx        on comment_likes (uid);

-- ── Serial Likes ──────────────────────────────────────────────
create table if not exists serial_likes (
    id         text primary key default gen_random_uuid()::text,
    serial_id  text not null,
    uid        text not null,
    name       text default '',
    photo_url  text default '',
    created_at timestamptz default now(),
    unique(serial_id, uid)
);
create index if not exists serial_likes_serial_id_idx on serial_likes (serial_id);
create index if not exists serial_likes_uid_idx       on serial_likes (uid);

-- ── RLS ───────────────────────────────────────────────────────
alter table follows       enable row level security;
alter table feed_likes    enable row level security;
alter table feed_saves    enable row level security;
alter table comment_likes enable row level security;
alter table serial_likes  enable row level security;

do $$ begin
  if not exists (select 1 from pg_policies where tablename='follows'       and policyname='rw_follows')       then create policy "rw_follows"       on follows       for all using (true) with check (true); end if;
  if not exists (select 1 from pg_policies where tablename='feed_likes'    and policyname='rw_feed_likes')    then create policy "rw_feed_likes"    on feed_likes    for all using (true) with check (true); end if;
  if not exists (select 1 from pg_policies where tablename='feed_saves'    and policyname='rw_feed_saves')    then create policy "rw_feed_saves"    on feed_saves    for all using (true) with check (true); end if;
  if not exists (select 1 from pg_policies where tablename='comment_likes' and policyname='rw_comment_likes') then create policy "rw_comment_likes" on comment_likes for all using (true) with check (true); end if;
  if not exists (select 1 from pg_policies where tablename='serial_likes'  and policyname='rw_serial_likes')  then create policy "rw_serial_likes"  on serial_likes  for all using (true) with check (true); end if;
end $$;
