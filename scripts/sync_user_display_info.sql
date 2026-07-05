-- ══════════════════════════════════════════════════════════════
--  Heftreng — Kullanıcı adı/foto denormalizasyon senkron trigger'ı
--
--  SORUN: users.display_name / users.photo_url değiştiğinde, bu
--  değerlerin KOPYALARI aşağıdaki tablolarda (yazma anında alınmış
--  "snapshot" olarak) saklı kalıyordu ve kullanıcı adını değiştirdikten
--  sonra GEÇMİŞ kayıtlar hep ESKİ adı/fotoyu göstermeye devam ediyordu:
--    - book_quotes.user_display_name / user_photo_url
--    - book_reviews.user_display_name / user_photo_url
--    - feed_comments.name / photo_url
--    - feed_likes.name / photo_url
--    - comment_likes.name / photo_url
--    - serial_likes.name / photo_url
--    - follows.from_name/from_photo (uid = from_uid)
--    - follows.target_name/target_photo (uid = target_uid)
--
--  ÇÖZÜM: users tablosunda display_name/photo_url her güncellendiğinde
--  otomatik tetiklenen bir Postgres trigger — tüm bu tablolardaki
--  kopyaları TEK SEFERDE, anında senkronize eder. Uygulama tarafında
--  ekstra kod gerekmez, Postgres seviyesinde garanti çalışır.
--
--  Kurulum: Supabase SQL Editor'de bu dosyanın tamamını çalıştır.
--  Tekrar çalıştırmak güvenlidir (CREATE OR REPLACE / DROP IF EXISTS).
-- ══════════════════════════════════════════════════════════════

create or replace function sync_user_display_info()
returns trigger
language plpgsql
security definer
as $$
begin
  -- Sadece gerçekten değişiklik olduysa çalış (gereksiz yazmaları önle)
  if (new.display_name is distinct from old.display_name)
     or (new.photo_url is distinct from old.photo_url) then

    update book_quotes
       set user_display_name = new.display_name,
           user_photo_url    = new.photo_url
     where uid = new.uid
       and (user_display_name is distinct from new.display_name
            or user_photo_url is distinct from new.photo_url);

    update book_reviews
       set user_display_name = new.display_name,
           user_photo_url    = new.photo_url
     where uid = new.uid
       and (user_display_name is distinct from new.display_name
            or user_photo_url is distinct from new.photo_url);

    update feed_comments
       set name      = new.display_name,
           photo_url = new.photo_url
     where uid = new.uid
       and (name is distinct from new.display_name
            or photo_url is distinct from new.photo_url);

    update feed_likes
       set name      = new.display_name,
           photo_url = new.photo_url
     where uid = new.uid
       and (name is distinct from new.display_name
            or photo_url is distinct from new.photo_url);

    update comment_likes
       set name      = new.display_name,
           photo_url = new.photo_url
     where uid = new.uid
       and (name is distinct from new.display_name
            or photo_url is distinct from new.photo_url);

    update serial_likes
       set name      = new.display_name,
           photo_url = new.photo_url
     where uid = new.uid
       and (name is distinct from new.display_name
            or photo_url is distinct from new.photo_url);

    -- follows: uid hem "from" (ben başkasını takip ederken) hem "target"
    -- (başkası beni takip ederken) tarafında görünebilir — ikisi de ayrı
    -- güncellenmeli.
    update follows
       set from_name  = new.display_name,
           from_photo = new.photo_url
     where from_uid = new.uid
       and (from_name is distinct from new.display_name
            or from_photo is distinct from new.photo_url);

    update follows
       set target_name  = new.display_name,
           target_photo = new.photo_url
     where target_uid = new.uid
       and (target_name is distinct from new.display_name
            or target_photo is distinct from new.photo_url);

  end if;

  return new;
end;
$$;

drop trigger if exists trg_sync_user_display_info on users;

create trigger trg_sync_user_display_info
  after update on users
  for each row
  execute function sync_user_display_info();


-- ══════════════════════════════════════════════════════════════
--  TEK SEFERLİK BACKFILL — bu trigger kurulmadan ÖNCE oluşmuş ve
--  hâlâ eski isim/foto taşıyan kayıtları düzeltir. Trigger'ı
--  kurduktan sonra BİR KEZ çalıştırman yeterli; sonraki tüm isim
--  değişikliklerini trigger otomatik yakalayacak.
-- ══════════════════════════════════════════════════════════════

update book_quotes bq
   set user_display_name = u.display_name,
       user_photo_url    = u.photo_url
  from users u
 where u.uid = bq.uid
   and (bq.user_display_name is distinct from u.display_name
        or bq.user_photo_url is distinct from u.photo_url);

update book_reviews br
   set user_display_name = u.display_name,
       user_photo_url    = u.photo_url
  from users u
 where u.uid = br.uid
   and (br.user_display_name is distinct from u.display_name
        or br.user_photo_url is distinct from u.photo_url);

update feed_comments fc
   set name      = u.display_name,
       photo_url = u.photo_url
  from users u
 where u.uid = fc.uid
   and (fc.name is distinct from u.display_name
        or fc.photo_url is distinct from u.photo_url);

update feed_likes fl
   set name      = u.display_name,
       photo_url = u.photo_url
  from users u
 where u.uid = fl.uid
   and (fl.name is distinct from u.display_name
        or fl.photo_url is distinct from u.photo_url);

update comment_likes cl
   set name      = u.display_name,
       photo_url = u.photo_url
  from users u
 where u.uid = cl.uid
   and (cl.name is distinct from u.display_name
        or cl.photo_url is distinct from u.photo_url);

update serial_likes sl
   set name      = u.display_name,
       photo_url = u.photo_url
  from users u
 where u.uid = sl.uid
   and (sl.name is distinct from u.display_name
        or sl.photo_url is distinct from u.photo_url);

update follows f
   set from_name  = u.display_name,
       from_photo = u.photo_url
  from users u
 where u.uid = f.from_uid
   and (f.from_name is distinct from u.display_name
        or f.from_photo is distinct from u.photo_url);

update follows f
   set target_name  = u.display_name,
       target_photo = u.photo_url
  from users u
 where u.uid = f.target_uid
   and (f.target_name is distinct from u.display_name
        or f.target_photo is distinct from u.photo_url);
