// scripts/migrate_to_supabase.js
// Firestore'daki authors + library_books koleksiyonlarını Supabase'e taşır.
// Upsert kullandığı için defalarca çalıştırılabilir — duplicate olmaz.

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
async function migrateBooks() {
  console.log('\n📖 Library books koleksiyonu okunuyor...');
  const snap = await db.collection('library_books').get();
  console.log(`   ${snap.size} kitap bulundu`);

  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:           doc.id,
      title:        safeStr(d.title),
      author_id:    safeStr(d.authorId) || null,
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

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('library_books', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} kitap taşındı`);
}

// ── Ana akış ──────────────────────────────────────────────────────────────────
async function main() {
  console.log('🚀 Supabase migration başlıyor...');
  console.log(`   DRY_RUN: ${DRY_RUN}`);
  console.log(`   Supabase URL: ${SUPABASE_URL}`);

  if (!SUPABASE_URL) throw new Error('SUPABASE_URL env değişkeni eksik');
  if (!SUPABASE_KEY) throw new Error('SUPABASE_SERVICE_KEY env değişkeni eksik');

  await migrateAuthors();
  await migrateBooks();

  console.log('\n🎉 Migration tamamlandı!');
}

main().catch(e => { console.error('❌ Hata:', e.message); process.exit(1); });
