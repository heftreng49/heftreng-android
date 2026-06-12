-- ══════════════════════════════════════════════════════════════
--  Heftreng — Row Level Security
--
--  MİMARİ NOT:
--  Uygulama Firebase Auth kullanıyor, Supabase Auth değil.
--  Bu nedenle auth.uid() her zaman null döner.
--  Güvenlik Firebase App Check + Firestore tarafında sağlanıyor.
--  Supabase tarafında anon key erişim kontrolü yeterli:
--    - Okuma: herkese açık
--    - Yazma: anon key'e açık (Firebase Auth koruyor zaten)
--
--  İleride Supabase Auth entegrasyonu yapılırsa uid kontrolü
--  burada etkinleştirilebilir.
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

-- ── Tüm tablolara anon + authenticated tam erişim ─────────────
-- (Firebase Auth katmanı güvenliği sağlıyor)

do $$ declare
  tbl text;
  tables text[] := array[
    'authors','library_books','book_quotes','book_reviews',
    'author_follows','follows','feed_likes','feed_saves',
    'comment_likes','serial_likes'
  ];
begin
  foreach tbl in array tables loop
    -- RLS aktif ama anon'a tam izin
    execute format('alter table %I enable row level security', tbl);

    execute format(
      'create policy %I on %I for all to anon, authenticated using (true) with check (true)',
      tbl || '_open', tbl
    );
  end loop;
  raise notice '✅ RLS politikaları uygulandı';
end $$;

-- ── Doğrulama ─────────────────────────────────────────────────
select tablename, policyname, cmd, roles
from pg_policies
where schemaname = 'public'
order by tablename;
