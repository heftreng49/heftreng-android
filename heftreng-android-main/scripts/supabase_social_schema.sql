-- ══════════════════════════════════════════════════════════════
--  Heftreng — Social Tables (follows, feedLikes, feedSaves,
--              commentLikes, serialLikes)
--  Supabase SQL Editor → Run
-- ══════════════════════════════════════════════════════════════

-- ── follows ───────────────────────────────────────────────────
create table if not exists follows (
    id           text primary key default gen_random_uuid()::text,
    from_uid     text not null,
    from_name    text default '',
    from_photo   text default '',
    target_uid   text not null,
    target_name  text default '',
    target_photo text default '',
    created_at   timestamptz default now()
);
create unique index if not exists follows_pair_idx on follows (from_uid, target_uid);
create index if not exists follows_from_uid_idx   on follows (from_uid);
create index if not exists follows_target_uid_idx on follows (target_uid);
create index if not exists follows_created_at_idx on follows (created_at desc);

-- ── feed_likes ────────────────────────────────────────────────
create table if not exists feed_likes (
    id         text primary key,   -- "{postId}_{uid}" — Firestore ile aynı format
    post_id    text not null,
    uid        text not null,
    name       text default '',
    photo_url  text default '',
    created_at timestamptz default now()
);
create unique index if not exists feed_likes_pair_idx  on feed_likes (post_id, uid);
create index if not exists feed_likes_post_id_idx      on feed_likes (post_id);
create index if not exists feed_likes_uid_idx          on feed_likes (uid);

-- ── feed_saves ────────────────────────────────────────────────
create table if not exists feed_saves (
    id         text primary key,   -- "{postId}_{uid}"
    post_id    text not null,
    uid        text not null,
    created_at timestamptz default now()
);
create unique index if not exists feed_saves_pair_idx on feed_saves (post_id, uid);
create index if not exists feed_saves_post_id_idx     on feed_saves (post_id);
create index if not exists feed_saves_uid_idx         on feed_saves (uid);

-- ── comment_likes ─────────────────────────────────────────────
create table if not exists comment_likes (
    id          text primary key,  -- "{commentId}_{uid}"
    comment_id  text not null,
    uid         text not null,
    name        text default '',
    photo_url   text default '',
    created_at  timestamptz default now()
);
create unique index if not exists comment_likes_pair_idx       on comment_likes (comment_id, uid);
create index if not exists comment_likes_comment_id_idx        on comment_likes (comment_id);
create index if not exists comment_likes_uid_idx               on comment_likes (uid);

-- ── serial_likes ──────────────────────────────────────────────
create table if not exists serial_likes (
    id          text primary key,  -- "{serialId}_{uid}"
    serial_id   text not null,
    uid         text not null,
    name        text default '',
    photo_url   text default '',
    created_at  timestamptz default now()
);
create unique index if not exists serial_likes_pair_idx    on serial_likes (serial_id, uid);
create index if not exists serial_likes_serial_id_idx      on serial_likes (serial_id);
create index if not exists serial_likes_uid_idx            on serial_likes (uid);

-- ── RLS ───────────────────────────────────────────────────────
alter table follows       enable row level security;
alter table feed_likes    enable row level security;
alter table feed_saves    enable row level security;
alter table comment_likes enable row level security;
alter table serial_likes  enable row level security;

do $$ begin
  -- follows
  if not exists (select 1 from pg_policies where tablename='follows' and policyname='all_follows') then
    create policy "all_follows" on follows for all to anon, authenticated using (true) with check (true);
  end if;
  -- feed_likes
  if not exists (select 1 from pg_policies where tablename='feed_likes' and policyname='all_feed_likes') then
    create policy "all_feed_likes" on feed_likes for all to anon, authenticated using (true) with check (true);
  end if;
  -- feed_saves
  if not exists (select 1 from pg_policies where tablename='feed_saves' and policyname='all_feed_saves') then
    create policy "all_feed_saves" on feed_saves for all to anon, authenticated using (true) with check (true);
  end if;
  -- comment_likes
  if not exists (select 1 from pg_policies where tablename='comment_likes' and policyname='all_comment_likes') then
    create policy "all_comment_likes" on comment_likes for all to anon, authenticated using (true) with check (true);
  end if;
  -- serial_likes
  if not exists (select 1 from pg_policies where tablename='serial_likes' and policyname='all_serial_likes') then
    create policy "all_serial_likes" on serial_likes for all to anon, authenticated using (true) with check (true);
  end if;
end $$;
