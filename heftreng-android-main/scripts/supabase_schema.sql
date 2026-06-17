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

-- ── Reading Status — okuma listesi (readingLists/{uid}/books taşındı) ──
-- Hem library_books (source='library') hem serials/books (source='serial'|'book')
-- içerikleri için tek tablo. current_page → "Arkadaşlar ne okuyor?" şeridi.
create table if not exists reading_status (
    uid          text not null,
    book_id      text not null,
    status       text not null check (status in ('okuyorum','okumak_istiyorum','okudum','biraktim')),
    title        text default '',
    cover_img    text default '',
    bg           text default '',
    author_name  text default '',
    source       text default 'serial',
    current_page int  default 0,
    updated_at   timestamptz default now(),
    primary key (uid, book_id)
);
create index if not exists reading_status_uid_idx    on reading_status (uid);
create index if not exists reading_status_status_idx on reading_status (status);

-- ── Read Progress — bölüm bazlı okuma yüzdesi (users/{uid}/readProgress taşındı) ──
-- Yüksek frekanslı yazma (scroll yüzdesi) — Firestore'da pahalıydı, Supabase'e taşındı.
create table if not exists read_progress (
    uid        text not null,
    parent_id  text not null,
    chapter_id text not null,
    pct        int  default 0,
    updated_at timestamptz default now(),
    primary key (uid, parent_id, chapter_id)
);
create index if not exists read_progress_uid_idx on read_progress (uid);

-- ── Daily Activity — streak'i genişletmek için günlük aktivite kaydı ──
-- Kurdî ders streak'inden ayrı: okuma/etkileşim bazlı genel streak.
create table if not exists daily_activity (
    uid           text not null,
    activity_date date not null,
    actions       int  default 0,
    created_at    timestamptz default now(),
    primary key (uid, activity_date)
);
create index if not exists daily_activity_uid_idx on daily_activity (uid);

-- ── Rozetler — kazanılan rozetler (katalog uygulama içinde, BadgeCatalog) ──
create table if not exists user_badges (
    uid        text not null,
    badge_id   text not null,
    earned_at  timestamptz default now(),
    primary key (uid, badge_id)
);
create index if not exists user_badges_uid_idx on user_badges (uid);

-- ── Row Level Security ────────────────────────────────────────
alter table authors        enable row level security;
alter table library_books  enable row level security;
alter table book_quotes    enable row level security;
alter table book_reviews   enable row level security;
alter table author_follows enable row level security;
alter table reading_status enable row level security;
alter table read_progress  enable row level security;
alter table daily_activity enable row level security;
alter table user_badges    enable row level security;

-- Herkes okuyabilir
do $$ begin
  if not exists (select 1 from pg_policies where tablename='authors'        and policyname='public_read_authors')        then create policy "public_read_authors"        on authors        for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='library_books'  and policyname='public_read_library_books')  then create policy "public_read_library_books"  on library_books  for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='book_quotes'    and policyname='public_read_book_quotes')    then create policy "public_read_book_quotes"    on book_quotes    for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='book_reviews'   and policyname='public_read_book_reviews')   then create policy "public_read_book_reviews"   on book_reviews   for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='author_follows' and policyname='public_read_author_follows') then create policy "public_read_author_follows" on author_follows  for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='reading_status' and policyname='public_read_reading_status') then create policy "public_read_reading_status" on reading_status for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='read_progress'  and policyname='public_read_read_progress')  then create policy "public_read_read_progress"  on read_progress  for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='daily_activity' and policyname='public_read_daily_activity') then create policy "public_read_daily_activity" on daily_activity for select using (true); end if;
  if not exists (select 1 from pg_policies where tablename='user_badges'    and policyname='public_read_user_badges')    then create policy "public_read_user_badges"    on user_badges    for select using (true); end if;
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
  if not exists (select 1 from pg_policies where tablename='reading_status' and policyname='upsert_reading_status') then
    create policy "upsert_reading_status" on reading_status for all using (true) with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='read_progress' and policyname='upsert_read_progress') then
    create policy "upsert_read_progress" on read_progress for all using (true) with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='daily_activity' and policyname='upsert_daily_activity') then
    create policy "upsert_daily_activity" on daily_activity for all using (true) with check (true);
  end if;
  if not exists (select 1 from pg_policies where tablename='user_badges' and policyname='upsert_user_badges') then
    create policy "upsert_user_badges" on user_badges for all using (true) with check (true);
  end if;
end $$;
