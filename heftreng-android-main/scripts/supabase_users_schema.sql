-- ══════════════════════════════════════════════════════════════
--  Heftreng — users tablosu
--  Firebase Firestore users koleksiyonunun Supabase mirror'ı.
--  Kaynak of truth Firestore'da kalır; bu tablo takip önerileri
--  ve sosyal sorgular için kullanılır.
--  Yazma: Cloud Function (onUserCreated/onUserDeleted) — service_role
--  Okuma: Android anon key ile
-- ══════════════════════════════════════════════════════════════

create table if not exists users (
    uid          text        primary key,
    display_name text        not null default '',
    photo_url    text        not null default '',
    bio          text        not null default '',
    banned       boolean     not null default false,
    created_at   timestamptz not null default now()
);

create index if not exists users_created_at_idx on users (created_at desc);
create index if not exists users_banned_idx     on users (banned);

-- ── RLS ───────────────────────────────────────────────────────
alter table users enable row level security;

do $$ begin
  -- Herkes okuyabilir (anon key yeterli)
  if not exists (
    select 1 from pg_policies where tablename='users' and policyname='users_select'
  ) then
    create policy "users_select" on users
      for select to anon, authenticated using (true);
  end if;

  -- Yazma sadece service_role (Cloud Function) — Android anon key yazamaz
  if not exists (
    select 1 from pg_policies where tablename='users' and policyname='users_insert_service'
  ) then
    create policy "users_insert_service" on users
      for insert to service_role with check (true);
  end if;

  if not exists (
    select 1 from pg_policies where tablename='users' and policyname='users_update_service'
  ) then
    create policy "users_update_service" on users
      for update to service_role using (true);
  end if;

  if not exists (
    select 1 from pg_policies where tablename='users' and policyname='users_delete_service'
  ) then
    create policy "users_delete_service" on users
      for delete to service_role using (true);
  end if;
end $$;
