-- ═══════════════════════════════════════════════════════════════════════
--  FAZ 1: Kütüphane alıntı/yorumlarına moderasyon durumu ekleniyor
--
--  Sorun: Bir alıntı Firestore feed'de moderatör tarafından kaldırılsa
--  (moderationStatus = "removed") bile, Supabase book_quotes/book_reviews
--  satırı hiç değişmediği için Kütüphane ekranında görünmeye devam
--  ediyordu. Bu script, Supabase tarafında da moderasyon durumunu tutacak
--  bir sütun ekliyor; senkronizasyonu AdminViewModel.moderatePost()/
--  restorePost() sağlayacak (ayrıca bkz. LibraryRepository.kt).
--
--  idempotent: "if not exists" kullanıldığı için birden fazla kez
--  çalıştırılması güvenli.
-- ═══════════════════════════════════════════════════════════════════════

alter table book_quotes
  add column if not exists moderation_status text not null default 'active';

alter table book_reviews
  add column if not exists moderation_status text not null default 'active';

-- feed_post_id üzerinden hızlı arama için (moderatePost/restorePost bu
-- sütunu kullanarak ilgili satırı bulacak)
create index if not exists book_quotes_feed_post_id_idx  on book_quotes  (feed_post_id);
create index if not exists book_reviews_feed_post_id_idx on book_reviews (feed_post_id);

-- Sorgu tarafında sık kullanılacak filtre için index
create index if not exists book_quotes_moderation_status_idx  on book_quotes  (moderation_status);
create index if not exists book_reviews_moderation_status_idx on book_reviews (moderation_status);
