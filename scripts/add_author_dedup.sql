-- ══════════════════════════════════════════════════════════════
--  Heftreng — Yazar/Kitap tekrar eklenmesini önleme
--  Dashboard → SQL Editor → New query → yapıştır → Run
--  Güvenli: IF NOT EXISTS — defalarca çalıştırılabilir
-- ══════════════════════════════════════════════════════════════

-- pg_trgm: benzerlik (fuzzy) arama için
create extension if not exists pg_trgm;

-- ── Normalize fonksiyonu: trim + tek boşluk + lowercase ─────────
-- (name_lower zaten var ama sadece lowercase yapıyor, boşluk farklarını yakalamıyor)
create or replace function normalize_name(input text)
returns text
language sql
immutable
as $$
  select trim(regexp_replace(lower(coalesce(input, '')), '\s+', ' ', 'g'))
$$;

-- ── authors: normalized sütun + unique index ────────────────────
alter table authors
  add column if not exists name_normalized text generated always as (normalize_name(name)) stored;

-- Tam kopyaları (varsa) tekilleştirmeden unique index atmak hata verir,
-- bu yüzden önce olası kopyaları tespit için bir görünüm bırakıyoruz.
-- Kopya YOKSA aşağıdaki satır çalışır; kopya VARSA önce elle temizleyin
-- (bkz. sorgu: select name_normalized, count(*) from authors group by 1 having count(*) > 1).
create unique index if not exists authors_name_normalized_uniq on authors (name_normalized);

-- Trigram (fuzzy benzerlik) indexi — "Mehmed Uzun" / "Mehmet Uzun" gibi
-- yazım farklarını yakalamak için similarity() sorgularını hızlandırır.
create index if not exists authors_name_trgm_idx on authors using gin (name gin_trgm_ops);

-- ── library_books için aynı mantık ───────────────────────────────
alter table library_books
  add column if not exists title_normalized text generated always as (normalize_name(title)) stored;

create unique index if not exists library_books_title_normalized_uniq on library_books (title_normalized);
create index if not exists library_books_title_trgm_idx on library_books using gin (title gin_trgm_ops);

-- ── RPC: benzer yazar ara (SADECE bilgi amaçlı — UI'da "bunu mu demek
--    istediniz?" onayı için. Kod tarafında OTOMATİK birleştirme yapılmıyor,
--    çünkü yanlışlıkla iki farklı yazarı birleştirme riski var.) ──
create or replace function find_similar_author(search_name text, min_similarity float default 0.35)
returns table(id text, name text, sim float)
language sql
stable
as $$
  select a.id, a.name, similarity(a.name, search_name) as sim
  from authors a
  where similarity(a.name, search_name) > min_similarity
  order by sim desc
  limit 5
$$;

create or replace function find_similar_book(search_title text, min_similarity float default 0.35)
returns table(id text, title text, author_id text, sim float)
language sql
stable
as $$
  select b.id, b.title, b.author_id, similarity(b.title, search_title) as sim
  from library_books b
  where similarity(b.title, search_title) > min_similarity
  order by sim desc
  limit 5
$$;

