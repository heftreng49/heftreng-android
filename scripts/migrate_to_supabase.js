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
async function main() {
  console.log('🚀 Supabase migration başlıyor...');
  console.log(`   DRY_RUN: ${DRY_RUN}`);
  console.log(`   Supabase URL: ${SUPABASE_URL}`);

  if (!SUPABASE_URL) throw new Error('SUPABASE_URL env değişkeni eksik');
  if (!SUPABASE_KEY) throw new Error('SUPABASE_SERVICE_KEY env değişkeni eksik');

  const authorIds = await migrateAuthors();
  const bookIds   = await migrateBooks(authorIds);
  await migrateQuotes(bookIds);
  await migrateFollows();
  await migrateFeedLikes();
  await migrateFeedSaves();
  await migrateSerialLikes();

  console.log('\n🎉 Migration tamamlandı!');
}

main().catch(e => { console.error('❌ Hata:', e.message); process.exit(1); });

// ── 4. Follows ────────────────────────────────────────────────────────────────
async function migrateFollows() {
  console.log('\n👥 Follows koleksiyonu okunuyor...');
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const snap = await db.collection('follows').get();
  console.log(`   ${snap.size} takip ilişkisi bulundu`);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:           doc.id,
      from_uid:     safeStr(d.fromUid),
      from_name:    safeStr(d.fromName),
      from_photo:   safeStr(d.fromPhoto),
      target_uid:   safeStr(d.targetUid),
      target_name:  safeStr(d.targetName),
      target_photo: safeStr(d.targetPhoto),
      created_at:   d.ts?.toDate?.()?.toISOString() || new Date().toISOString(),
    };
  }).filter(r => r.from_uid && r.target_uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('follows', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} takip ilişkisi taşındı`);
}

// ── 5. Feed Likes ─────────────────────────────────────────────────────────────
async function migrateFeedLikes() {
  console.log('\n❤️  Feed likes koleksiyonu okunuyor...');
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const snap = await db.collection('feedLikes').get();
  console.log(`   ${snap.size} beğeni bulundu`);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:         doc.id,
      post_id:    safeStr(d.feedId || d.postId),
      uid:        safeStr(d.uid),
      name:       safeStr(d.displayName || d.name),
      photo_url:  safeStr(d.photoURL || d.photoUrl),
      created_at: d.ts?.toDate?.()?.toISOString() || new Date().toISOString(),
    };
  }).filter(r => r.post_id && r.uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('feed_likes', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} feed beğenisi taşındı`);
}

// ── 6. Feed Saves ─────────────────────────────────────────────────────────────
async function migrateFeedSaves() {
  console.log('\n🔖 Feed saves koleksiyonu okunuyor...');
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const snap = await db.collection('feedSaves').get();
  console.log(`   ${snap.size} kayıt bulundu`);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:         doc.id,
      post_id:    safeStr(d.feedId || d.postId),
      uid:        safeStr(d.uid),
      created_at: d.ts?.toDate?.()?.toISOString() || new Date().toISOString(),
    };
  }).filter(r => r.post_id && r.uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('feed_saves', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} kayıt taşındı`);
}

// ── 7. Serial Likes ───────────────────────────────────────────────────────────
async function migrateSerialLikes() {
  console.log('\n📚 Serial likes koleksiyonu okunuyor...');
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const snap = await db.collection('serialLikes').get();
  console.log(`   ${snap.size} serial beğenisi bulundu`);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:         doc.id,
      serial_id:  safeStr(d.serialId),
      uid:        safeStr(d.uid),
      name:       safeStr(d.displayName || d.name),
      photo_url:  safeStr(d.photoURL || d.photoUrl),
      created_at: d.ts?.toDate?.()?.toISOString() || new Date().toISOString(),
    };
  }).filter(r => r.serial_id && r.uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('serial_likes', batch);
    inserted += batch.length;
    process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${inserted} serial beğenisi taşındı`);
}
