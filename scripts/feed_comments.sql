-- ════════════════════════════════════════════════════════════
-- feed_comments — Gönderi yorumları (Firestore'dan Supabase'e taşındı)
-- NOT: comment_likes tablosu supabase_social_schema.sql'de zaten mevcut
--      (id: text "{commentId}_{uid}", comment_id: text) — burada tekrar
--      oluşturulmuyor, mevcut şemayla uyumlu kullanılıyor.
-- ════════════════════════════════════════════════════════════
create table if not exists public.feed_comments (
  id              uuid primary key default gen_random_uuid(),
  post_id         text not null,
  uid             text not null,
  name            text default '',
  photo_url       text default '',
  text            text not null check (char_length(text) between 1 and 500),
  likes_count     integer not null default 0,
  reply_to_cmt_id text default '',
  mentions        text[]      not null default '{}',
  created_at      timestamptz not null default now()
);

create index if not exists idx_feed_comments_post_id on public.feed_comments (post_id, created_at asc);
create index if not exists idx_feed_comments_uid     on public.feed_comments (uid);

-- ── RLS — diğer tablolarla aynı açık-anon pattern ──────────────
alter table public.feed_comments enable row level security;

do $$ begin
  if not exists (select 1 from pg_policies where tablename='feed_comments' and policyname='all_feed_comments') then
    create policy "all_feed_comments" on public.feed_comments for all to anon, authenticated using (true) with check (true);
  end if;
end $$;

-- Mevcut tabloya mentions kolonu ekle (tablo zaten oluşturulmuşsa)
alter table public.feed_comments
  add column if not exists mentions text[] not null default '{}';
