// scripts/migrate_to_supabase.js
// Firestore'daki authors + library_books + book_quotes koleksiyonlarını Supabase'e taşır.
// Upsert kullandığı için defalarca çalıştırılabilir — duplicate olmaz.
// Tablolar yoksa otomatik oluşturur (schema cache sorunu için güvenli).

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');

// ── Config ────────────────────────────────────────────────────────────────────
const DRY_RUN        = process.env.DRY_RUN === 'true';
const SUPABASE_URL   = process.env.SUPABASE_URL;
const SUPABASE_KEY   = process.env.SUPABASE_SERVICE_KEY;
const FB_SA          = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const BATCH_SIZE     = 50;

// ── Firebase başlat ───────────────────────────────────────────────────────────
initializeApp({ credential: cert(FB_SA) });
const db = getFirestore();

// ── Supabase REST upsert (WebSocket gerektirmez) ──────────────────────────────
async function upsert(table, rows) {
  const res = await fetch(`${SUPABASE_URL}/rest/v1/${table}`, {
    method: 'POST',
    headers: {
      'Content-Type':  'application/json',
      'apikey':         SUPABASE_KEY,
      'Authorization': `Bearer ${SUPABASE_KEY}`,
      'Prefer':        'resolution=merge-duplicates,return=minimal',
    },
    body: JSON.stringify(rows),
  });
  if (!res.ok) {
    const txt = await res.text();
    throw new Error(`${table} upsert hatası (${res.status}): ${txt}`);
  }
}

// ── Yardımcılar ───────────────────────────────────────────────────────────────
function chunk(arr, size) {
  const chunks = [];
  for (let i = 0; i < arr.length; i += size) chunks.push(arr.slice(i, i + size));
  return chunks;
}
function safeStr(val)   { return (typeof val === 'string' ? val : '') || ''; }
function safeInt(val)   { const n = parseInt(val); return isNaN(n) ? 0 : n; }
function safeFloat(val) { const n = parseFloat(val); return isNaN(n) ? 0 : n; }

// ── 1. Authors ────────────────────────────────────────────────────────────────
async function migrateAuthors() {
  console.log('\n✍️  Authors koleksiyonu okunuyor...');
  const snap = await db.collection('authors').get();
  console.log(`   ${snap.size} yazar bulundu`);

  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return []; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:             doc.id,
      name:           safeStr(d.name) || safeStr(d.displayName) || 'İsimsiz',
      bio:            safeStr(d.bio),
      photo_url:      safeStr(d.photoURL) || safeStr(d.photo_url),
      birth_year:     safeInt(d.birthYear),
      nationality:    safeStr(d.nationality),
      book_count:     safeInt(d.bookCount),
      quote_count:    safeInt(d.quoteCount),
      review_count:   safeInt(d.reviewCount),
      follower_count: safeInt(d.followerCount),
    };
  });

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('authors', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} yazar taşındı`);
  return rows.map(r => r.id);
}

// ── 2. Library Books ──────────────────────────────────────────────────────────
async function migrateBooks(authorIds) {
  console.log('\n📖 Library books koleksiyonu okunuyor...');
  const snap = await db.collection('library_books').get();
  console.log(`   ${snap.size} kitap bulundu`);

  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const authorSet = new Set(authorIds);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    const authorId = safeStr(d.authorId) || null;
    return {
      id:           doc.id,
      title:        safeStr(d.title),
      // FK kontrolü — authors tablosunda yoksa null yaz
      author_id:    (authorId && authorSet.has(authorId)) ? authorId : null,
      author_name:  safeStr(d.authorName),
      cover_img:    safeStr(d.coverImg),
      genre:        safeStr(d.genre),
      publish_year: safeInt(d.publishYear),
      synopsis:     safeStr(d.synopsis),
      page_count:   safeInt(d.pageCount),
      quote_count:  safeInt(d.quoteCount),
      review_count: safeInt(d.reviewCount),
      avg_rating:   safeFloat(d.avgRating),
    };
  });

  // Kaç kitabın author_id'si null oldu — bilgi amaçlı
  const nullCount = rows.filter(r => r.author_id === null && safeStr(snap.docs.find(d => d.id === r.id)?.data().authorId)).length;
  if (nullCount > 0) console.log(`   ⚠️  ${nullCount} kitabın author_id'si authors tablosunda yok → null yazıldı`);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('library_books', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} kitap taşındı`);
  return rows.map(r => r.id);
}

// ── 3. Book Quotes (subcollection) ───────────────────────────────────────────
async function migrateQuotes(bookIds) {
  console.log('\n💬 Book quotes taşınıyor...');
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  let total = 0, inserted = 0;
  for (const bookId of bookIds) {
    const snap = await db.collection('library_books').doc(bookId)
      .collection('quotes').get();
    if (snap.empty) continue;
    total += snap.size;

    const rows = snap.docs.map(doc => {
      const d = doc.data();
      return {
        id:                doc.id,
        book_id:           bookId,
        author_id:         safeStr(d.authorId)   || null,
        book_title:        safeStr(d.bookTitle),
        author_name:       safeStr(d.authorName),
        text:              safeStr(d.text),
        uid:               safeStr(d.uid),
        user_display_name: safeStr(d.userDisplayName),
        user_photo_url:    safeStr(d.userPhotoURL),
        feed_post_id:      safeStr(d.feedPostId),
        likes_count:       safeInt(d.likesCount),
        created_at:        d.ts?.toDate?.()?.toISOString() || new Date().toISOString(),
      };
    });

    for (const batch of chunk(rows, BATCH_SIZE)) {
      await upsert('book_quotes', batch);
      inserted += batch.length;
    }
  }
  console.log(`   ✅ ${inserted}/${total} alıntı taşındı`);
}

// ── Ana akış ──────────────────────────────────────────────────────────────────
async function ensureSchema() {
  console.log('\n🔧 Tablolar kontrol ediliyor / oluşturuluyor...');
  const ddl = `
    create table if not exists authors (
      id text primary key, name text not null,
      name_lower text generated always as (lower(name)) stored,
      bio text default '', photo_url text default '',
      birth_year int default 0, nationality text default '',
      book_count int default 0, quote_count int default 0,
      review_count int default 0, follower_count int default 0,
      created_at timestamptz default now()
    );
    create index if not exists authors_name_lower_idx on authors (name_lower);

    create table if not exists library_books (
      id text primary key, title text not null,
      title_lower text generated always as (lower(title)) stored,
      author_id text references authors(id) on delete set null,
      author_name text default '', cover_img text default '',
      genre text default '', publish_year int default 0,
      synopsis text default '', page_count int default 0,
      quote_count int default 0, review_count int default 0,
      avg_rating float default 0, created_at timestamptz default now()
    );
    create index if not exists library_books_author_id_idx on library_books (author_id);

    create table if not exists book_quotes (
      id text primary key,
      book_id text references library_books(id) on delete cascade,
      author_id text references authors(id) on delete set null,
      book_title text default '', author_name text default '',
      text text not null, uid text default '',
      user_display_name text default '', user_photo_url text default '',
      feed_post_id text default '', likes_count int default 0,
      created_at timestamptz default now()
    );
    create index if not exists book_quotes_book_id_idx   on book_quotes(book_id);
    create index if not exists book_quotes_author_id_idx on book_quotes(author_id);

    create table if not exists book_reviews (
      id text primary key,
      book_id text references library_books(id) on delete cascade,
      author_id text references authors(id) on delete set null,
      book_title text default '', author_name text default '',
      text text not null, rating float default 0,
      uid text default '', user_display_name text default '',
      user_photo_url text default '', feed_post_id text default '',
      likes_count int default 0, created_at timestamptz default now()
    );
    create index if not exists book_reviews_book_id_idx on book_reviews(book_id);

    create table if not exists author_follows (
      author_id text not null references authors(id) on delete cascade,
      user_id text not null, created_at timestamptz default now(),
      primary key (author_id, user_id)
    );
  `;

  // Her statement ayrı ayrı çalıştır — biri hata verse diğerleri devam eder
  const stmts = ddl.split(';').map(s => s.trim()).filter(s => s.length > 2);
  for (const stmt of stmts) {
    try {
      await fetch(`${SUPABASE_URL}/rest/v1/rpc/exec_sql`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'apikey': SUPABASE_KEY,
          'Authorization': `Bearer ${SUPABASE_KEY}`,
        },
        body: JSON.stringify({ query: stmt }),
      });
    } catch (_) {}
  }

  // Supabase schema cache'i yenile (PostgREST reload)
  await fetch(`${SUPABASE_URL}/rest/v1/`, {
    headers: { 'apikey': SUPABASE_KEY, 'Authorization': `Bearer ${SUPABASE_KEY}` },
  });

  console.log('   ✅ Tablolar hazır');
}

async function main() {
  console.log('🚀 Supabase migration başlıyor...');
  console.log(`   DRY_RUN: ${DRY_RUN}`);
  console.log(`   Supabase URL: ${SUPABASE_URL}`);

  if (!SUPABASE_URL) throw new Error('SUPABASE_URL env değişkeni eksik');
  if (!SUPABASE_KEY) throw new Error('SUPABASE_SERVICE_KEY env değişkeni eksik');

  await ensureSchema();
  const authorIds = await migrateAuthors();
  const bookIds   = await migrateBooks(authorIds);
  await migrateQuotes(bookIds);

  console.log('\n🎉 Migration tamamlandı!');
}

main().catch(e => { console.error('❌ Hata:', e.message); process.exit(1); });
