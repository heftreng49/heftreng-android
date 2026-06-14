// scripts/migrate_to_supabase.js
// Firestore'daki authors + library_books koleksiyonlarını Supabase'e taşır.
// Upsert kullandığı için defalarca çalıştırılabilir — duplicate olmaz.

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');
const { createClient }        = require('@supabase/supabase-js');
const ws                      = require('ws');

// ── Config ────────────────────────────────────────────────────────────────────
const DRY_RUN        = process.env.DRY_RUN === 'true';
const SUPABASE_URL   = process.env.SUPABASE_URL;
const SUPABASE_KEY   = process.env.SUPABASE_SERVICE_KEY; // service_role key — write yetkisi var
const FB_SA          = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);

const BATCH_SIZE     = 50; // Supabase'e kaç kayıt aynı anda gönderilsin

// ── İstemcileri başlat ────────────────────────────────────────────────────────
initializeApp({ credential: cert(FB_SA) });
const db       = getFirestore();
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY, { realtime: { transport: ws } });

// ── Yardımcılar ───────────────────────────────────────────────────────────────
function chunk(arr, size) {
  const chunks = [];
  for (let i = 0; i < arr.length; i += size) chunks.push(arr.slice(i, i + size));
  return chunks;
}

function safeStr(val) {
  return (typeof val === 'string' ? val : '') || '';
}

function safeInt(val) {
  const n = parseInt(val, 10);
  return isNaN(n) ? 0 : n;
}

function safeFloat(val) {
  const n = parseFloat(val);
  return isNaN(n) ? 0 : n;
}

// ── 1. Authors ────────────────────────────────────────────────────────────────
async function migrateAuthors() {
  console.log('\n📚 Authors koleksiyonu okunuyor...');
  const snap = await db.collection('authors').get();
  console.log(`   ${snap.size} yazar bulundu`);

  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return []; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id:             doc.id,
      name:           safeStr(d.name),
      bio:            safeStr(d.bio),
      photo_url:      safeStr(d.photoURL),
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
    const { error } = await supabase
      .from('authors')
      .upsert(batch, { onConflict: 'id' });

    if (error) {
      console.error('   ❌ Yazar batch hatası:', error.message);
    } else {
      inserted += batch.length;
      process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
    }
  }
  console.log(`\n   ✅ ${inserted} yazar taşındı`);
  return rows.map(r => r.id); // sonraki adım için id listesi
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
      author_id:    safeStr(d.authorId)  || null, // FK — boşsa null yaz
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
    const { error } = await supabase
      .from('library_books')
      .upsert(batch, { onConflict: 'id' });

    if (error) {
      console.error('   ❌ Kitap batch hatası:', error.message);
    } else {
      inserted += batch.length;
      process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`);
    }
  }
  console.log(`\n   ✅ ${inserted} kitap taşındı`);
}

// ── 3. Follows ────────────────────────────────────────────────────────────────
async function migrateFollows() {
  console.log('\n👥 Follows koleksiyonu okunuyor...');
  const snap = await db.collection('follows').get();
  console.log(`   ${snap.size} takip ilişkisi bulundu`);
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    const parts = doc.id.split('_');
    return {
      id:           doc.id,
      from_uid:     safeStr(d.fromUid)    || parts[0] || '',
      from_name:    safeStr(d.fromName),
      from_photo:   safeStr(d.fromPhoto)  || safeStr(d.fromPhotoURL),
      target_uid:   safeStr(d.targetUid)  || parts[1] || '',
      target_name:  safeStr(d.targetName),
      target_photo: safeStr(d.targetPhoto) || safeStr(d.targetPhotoURL),
    };
  }).filter(r => r.from_uid && r.target_uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    const { error } = await supabase.from('follows').upsert(batch, { onConflict: 'id' });
    if (error) console.error('   ❌ Follows batch hatası:', error.message);
    else { inserted += batch.length; process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`); }
  }
  console.log(`\n   ✅ ${inserted} takip ilişkisi taşındı`);
}

// ── 4. FeedLikes ──────────────────────────────────────────────────────────────
async function migrateFeedLikes() {
  console.log('\n❤️  FeedLikes koleksiyonu okunuyor...');
  const snap = await db.collection('feedLikes').get();
  console.log(`   ${snap.size} beğeni bulundu`);
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    const parts = doc.id.split('_');
    return {
      id:        doc.id,
      post_id:   safeStr(d.feedId) || parts[0] || '',
      uid:       safeStr(d.uid)    || parts[1] || '',
      name:      safeStr(d.name)   || safeStr(d.displayName),
      photo_url: safeStr(d.photoURL),
    };
  }).filter(r => r.post_id && r.uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    const { error } = await supabase.from('feed_likes').upsert(batch, { onConflict: 'id' });
    if (error) console.error('   ❌ FeedLikes batch hatası:', error.message);
    else { inserted += batch.length; process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`); }
  }
  console.log(`\n   ✅ ${inserted} beğeni taşındı`);
}

// ── 5. FeedSaves ──────────────────────────────────────────────────────────────
async function migrateFeedSaves() {
  console.log('\n🔖 FeedSaves koleksiyonu okunuyor...');
  const snap = await db.collection('feedSaves').get();
  console.log(`   ${snap.size} kayıt bulundu`);
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    const parts = doc.id.split('_');
    return {
      id:      doc.id,
      post_id: safeStr(d.feedId) || parts[0] || '',
      uid:     safeStr(d.uid)    || parts[1] || '',
    };
  }).filter(r => r.post_id && r.uid);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    const { error } = await supabase.from('feed_saves').upsert(batch, { onConflict: 'id' });
    if (error) console.error('   ❌ FeedSaves batch hatası:', error.message);
    else { inserted += batch.length; process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`); }
  }
  console.log(`\n   ✅ ${inserted} kayıt taşındı`);
}

// ── 6. Firestore likes sayaçlarını Supabase gerçek sayısıyla güncelle ─────────
async function syncLikeCounts() {
  console.log('\n🔢 Like sayaçları senkronize ediliyor...');

  // Supabase'den post başına like sayısını çek
  const { data: rows, error } = await supabase
    .from('feed_likes')
    .select('post_id');

  if (error) { console.error('   ❌ Supabase hatası:', error.message); return; }

  // post_id bazında say
  const counts = {};
  for (const row of rows) {
    counts[row.post_id] = (counts[row.post_id] || 0) + 1;
  }

  console.log(`   ${Object.keys(counts).length} post için like sayısı bulundu`);
  if (DRY_RUN) { console.log('   [DRY RUN] Firestore yazma atlandı'); return; }

  // Firestore'da güncelle — batch ile
  const { getFirestore } = require('firebase-admin/firestore');
  const db = getFirestore();
  const entries = Object.entries(counts);
  let updated = 0;

  for (let i = 0; i < entries.length; i += 490) {
    const batchChunk = entries.slice(i, i + 490);
    const batch = db.batch();
    for (const [postId, count] of batchChunk) {
      batch.update(db.collection('feed').doc(postId), { likes: count });
    }
    try {
      await batch.commit();
      updated += batchChunk.length;
      process.stdout.write(`   ✅ ${updated}/${entries.length}\r`);
    } catch (e) {
      console.error('   ❌ Batch hatası:', e.message);
    }
  }
  console.log(`\n   ✅ ${updated} postun like sayacı güncellendi`);
}

// ── 7. ReadingLists → reading_status ──────────────────────────────────────────
// Firestore: readingLists/{uid}/books/{sid} → Supabase: reading_status (uid, book_id)
async function migrateReadingStatus() {
  console.log('\n📑 ReadingLists koleksiyonu okunuyor (collectionGroup)...');
  const snap = await db.collectionGroup('books').get();
  console.log(`   ${snap.size} okuma listesi kaydı bulundu`);
  if (DRY_RUN) { console.log('   [DRY RUN] yazma atlandı'); return; }

  const rows = snap.docs.map(doc => {
    const d   = doc.data();
    const uid = doc.ref.parent.parent ? doc.ref.parent.parent.id : '';
    return {
      uid:          uid,
      book_id:      safeStr(d.sid) || doc.id,
      status:       safeStr(d.status),
      title:        safeStr(d.title),
      cover_img:    safeStr(d.coverImg),
      bg:           safeStr(d.bg),
      author_name:  safeStr(d.authorName),
      source:       safeStr(d.source) || 'serial',
      current_page: safeInt(d.currentPage),
    };
  }).filter(r => r.uid && r.book_id && r.status);

  let inserted = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    const { error } = await supabase.from('reading_status').upsert(batch, { onConflict: 'uid,book_id' });
    if (error) console.error('   ❌ ReadingStatus batch hatası:', error.message);
    else { inserted += batch.length; process.stdout.write(`   ✅ ${inserted}/${rows.length}\r`); }
  }
  console.log(`\n   ✅ ${inserted} okuma listesi kaydı taşındı`);
}

// main'e ekle — Öncelik 2: reading_status migrasyonu
async function main() {
  console.log('═══════════════════════════════════════════');
  console.log('  Heftreng — Firestore → Supabase Migration');
  console.log(`  Mod: ${DRY_RUN ? '🔍 DRY RUN (yazma yok)' : '🚀 CANLI'}`);
  console.log('═══════════════════════════════════════════');

  await migrateAuthors();
  await migrateBooks();
  await migrateFollows();
  await migrateFeedLikes();
  await migrateFeedSaves();
  await migrateReadingStatus();
  await syncLikeCounts();   // Firestore sayaçlarını Supabase ile senkronize et

  console.log('\n🎉 Migration tamamlandı!');
}

main().catch(err => { console.error('FATAL:', err); process.exit(1); });
