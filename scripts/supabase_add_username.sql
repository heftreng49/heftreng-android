-- ══════════════════════════════════════════════════════════════
--  Heftreng — users tablosuna username kolonu ekleme
--  Supabase SQL Editor → Run
--  Idempotent: defalarca calistirmak guvenli
-- ══════════════════════════════════════════════════════════════

-- 1. Kolon ekle
alter table users
  add column if not exists username       text not null default '',
  add column if not exists username_lower text not null default '';

-- 2. Unique index — bos string'ler haric (iki kullanici bos username ile cakismasin)
create unique index if not exists users_username_lower_idx
  on users (username_lower)
  where username_lower <> '';

-- 3. Prefix arama hizi icin text_pattern_ops index
create index if not exists users_username_lower_prefix_idx
  on users (username_lower text_pattern_ops)
  where username_lower <> '';

-- 4. display_name prefix index (ilike '%abc%' icin)
create index if not exists users_display_name_lower_idx
  on users (lower(display_name) text_pattern_ops)
  where display_name <> '';
