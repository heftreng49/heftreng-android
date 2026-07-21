-- presence tablosu: kullanici online/offline durumu
-- Eski surum uyumlulugu icin Firestore'a da yazilmaya devam edilir

create table if not exists presence (
  uid          text primary key,
  online       boolean default false,
  last_seen    timestamptz default now(),
  platform     text default 'android',
  app_version  text default ''
);

alter table presence enable row level security;

drop policy if exists "herkes okuyabilir" on presence;
drop policy if exists "kendi kaydini yazabilir" on presence;
drop policy if exists "kendi kaydini guncelleyebilir" on presence;

create policy "herkes okuyabilir"
  on presence for select using (true);

create policy "kendi kaydini yazabilir"
  on presence for insert with check (true);

create policy "kendi kaydini guncelleyebilir"
  on presence for update using (true);
