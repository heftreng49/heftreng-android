-- ══════════════════════════════════════════════════════════════
--  Heftreng — Row Level Security Kuralları
--  GitHub Actions ile deploy edilir — elle çalıştırmana gerek yok
--
--  Güvenlik modeli:
--  - Herkes okuyabilir (anon key yeterli)
--  - Sadece kendi uid'iyle yazabilir (auth.uid() kontrolü)
--  - Admin işlemleri service_role key ile (migration workflow)
-- ══════════════════════════════════════════════════════════════

-- ── Mevcut policy'leri temizle ────────────────────────────────
do $$ declare
  r record;
begin
  for r in select tablename, policyname from pg_policies
           where schemaname = 'public'
           and tablename in (
             'authors','library_books','book_quotes','book_reviews',
             'author_follows','follows','feed_likes','feed_saves',
             'comment_likes','serial_likes'
           )
  loop
    execute format('drop policy if exists %I on %I', r.policyname, r.tablename);
  end loop;
end $$;

-- ══════════════════════════════════════════════════════════════
--  KÜTÜPHANE TABLOLARI
-- ══════════════════════════════════════════════════════════════

-- ── authors ───────────────────────────────────────────────────
-- Herkes okur | Giriş yapmış kullanıcı yazar ekler/günceller
create policy "authors_select"
  on authors for select to anon, authenticated using (true);

create policy "authors_insert"
  on authors for insert to authenticated with check (true);

create policy "authors_update"
  on authors for update to authenticated using (true);

create policy "authors_upsert"
  on authors for insert to authenticated with check (true);

-- ── library_books ─────────────────────────────────────────────
create policy "library_books_select"
  on library_books for select to anon, authenticated using (true);

create policy "library_books_insert"
  on library_books for insert to authenticated with check (true);

create policy "library_books_update"
  on library_books for update to authenticated using (true);

-- ── book_quotes ───────────────────────────────────────────────
-- Herkes okur | Sadece kendi alıntısını yazar/siler
create policy "book_quotes_select"
  on book_quotes for select to anon, authenticated using (true);

create policy "book_quotes_insert"
  on book_quotes for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "book_quotes_update"
  on book_quotes for update to authenticated
  using (uid = auth.uid()::text);

create policy "book_quotes_delete"
  on book_quotes for delete to authenticated
  using (uid = auth.uid()::text);

-- ── book_reviews ──────────────────────────────────────────────
create policy "book_reviews_select"
  on book_reviews for select to anon, authenticated using (true);

create policy "book_reviews_insert"
  on book_reviews for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "book_reviews_update"
  on book_reviews for update to authenticated
  using (uid = auth.uid()::text);

create policy "book_reviews_delete"
  on book_reviews for delete to authenticated
  using (uid = auth.uid()::text);

-- ── author_follows ────────────────────────────────────────────
-- Herkes okur | Sadece kendi takibini ekler/siler
create policy "author_follows_select"
  on author_follows for select to anon, authenticated using (true);

create policy "author_follows_insert"
  on author_follows for insert to authenticated
  with check (user_id = auth.uid()::text);

create policy "author_follows_delete"
  on author_follows for delete to authenticated
  using (user_id = auth.uid()::text);

-- ══════════════════════════════════════════════════════════════
--  SOSYAL TABLOLAR
-- ══════════════════════════════════════════════════════════════

-- ── follows ───────────────────────────────────────────────────
-- Herkes okur | Sadece kendi takip ilişkisini yazar/siler
create policy "follows_select"
  on follows for select to anon, authenticated using (true);

create policy "follows_insert"
  on follows for insert to authenticated
  with check (from_uid = auth.uid()::text);

create policy "follows_delete"
  on follows for delete to authenticated
  using (from_uid = auth.uid()::text);

-- ── feed_likes ────────────────────────────────────────────────
create policy "feed_likes_select"
  on feed_likes for select to anon, authenticated using (true);

create policy "feed_likes_insert"
  on feed_likes for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "feed_likes_delete"
  on feed_likes for delete to authenticated
  using (uid = auth.uid()::text);

-- ── feed_saves ────────────────────────────────────────────────
create policy "feed_saves_select"
  on feed_saves for select to anon, authenticated using (true);

create policy "feed_saves_insert"
  on feed_saves for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "feed_saves_delete"
  on feed_saves for delete to authenticated
  using (uid = auth.uid()::text);

-- ── comment_likes ─────────────────────────────────────────────
create policy "comment_likes_select"
  on comment_likes for select to anon, authenticated using (true);

create policy "comment_likes_insert"
  on comment_likes for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "comment_likes_delete"
  on comment_likes for delete to authenticated
  using (uid = auth.uid()::text);

-- ── serial_likes ──────────────────────────────────────────────
create policy "serial_likes_select"
  on serial_likes for select to anon, authenticated using (true);

create policy "serial_likes_insert"
  on serial_likes for insert to authenticated
  with check (uid = auth.uid()::text);

create policy "serial_likes_delete"
  on serial_likes for delete to authenticated
  using (uid = auth.uid()::text);

-- ══════════════════════════════════════════════════════════════
--  RLS AÇIK OLDUĞUNU DOĞRULA
-- ══════════════════════════════════════════════════════════════
do $$ declare
  tbl text;
  tables text[] := array[
    'authors','library_books','book_quotes','book_reviews',
    'author_follows','follows','feed_likes','feed_saves',
    'comment_likes','serial_likes'
  ];
begin
  foreach tbl in array tables loop
    execute format('alter table %I enable row level security', tbl);
    execute format('alter table %I force row level security', tbl);
  end loop;
  raise notice '✅ RLS tüm tablolarda aktif';
end $$;
