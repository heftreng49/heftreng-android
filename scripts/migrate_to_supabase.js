// scripts/migrate_to_supabase.js
// Firestore → Supabase migration (fetch tabanlı — WebSocket gerektirmez)
// Upsert kullandığı için defalarca çalıştırılabilir.

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');

const DRY_RUN      = process.env.DRY_RUN === 'true';
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_KEY;
const FB_SA        = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const BATCH_SIZE   = 50;

initializeApp({ credential: cert(FB_SA) });
const db = getFirestore();

// ── Supabase REST upsert ──────────────────────────────────────────────────────
async function upsert(table, rows) {
  if (!rows.length) return;
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
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}
const safeStr   = v => (typeof v === 'string' ? v : '') || '';
const safeInt   = v => { const n = parseInt(v); return isNaN(n) ? 0 : n; };
const safeFloat = v => { const n = parseFloat(v); return isNaN(n) ? 0 : n; };
const safeTs    = v => v?.toDate?.()?.toISOString() || new Date().toISOString();

// ── 1. Authors ────────────────────────────────────────────────────────────────
async function migrateAuthors() {
  console.log('\n✍️  Authors...');
  const snap = await db.collection('authors').get();
  console.log(`   ${snap.size} yazar`);
  if (DRY_RUN) return snap.docs.map(d => d.id);

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, name: safeStr(d.name) || 'İsimsiz',
      bio: safeStr(d.bio), photo_url: safeStr(d.photoURL || d.photo_url),
      birth_year: safeInt(d.birthYear), nationality: safeStr(d.nationality),
      book_count: safeInt(d.bookCount), quote_count: safeInt(d.quoteCount),
      review_count: safeInt(d.reviewCount), follower_count: safeInt(d.followerCount),
    };
  });

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('authors', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} yazar taşındı`);
  return rows.map(r => r.id);
}

// ── 2. Library Books ──────────────────────────────────────────────────────────
async function migrateBooks(authorIds) {
  console.log('\n📖 Library books...');
  const snap = await db.collection('library_books').get();
  console.log(`   ${snap.size} kitap`);
  if (DRY_RUN) return snap.docs.map(d => d.id);

  const authorSet = new Set(authorIds);
  const rows = snap.docs.map(doc => {
    const d = doc.data();
    const authorId = safeStr(d.authorId) || null;
    return {
      id: doc.id, title: safeStr(d.title),
      author_id: (authorId && authorSet.has(authorId)) ? authorId : null,
      author_name: safeStr(d.authorName), cover_img: safeStr(d.coverImg),
      genre: safeStr(d.genre), publish_year: safeInt(d.publishYear),
      synopsis: safeStr(d.synopsis), page_count: safeInt(d.pageCount),
      quote_count: safeInt(d.quoteCount), review_count: safeInt(d.reviewCount),
      avg_rating: safeFloat(d.avgRating),
    };
  });

  const nullCount = rows.filter(r => !r.author_id && snap.docs.find(d => d.id === r.id)?.data().authorId).length;
  if (nullCount) console.log(`   ⚠️  ${nullCount} kitabın author_id'si yok → null`);

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('library_books', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} kitap taşındı`);
  return rows.map(r => r.id);
}

// ── 3. Book Quotes ────────────────────────────────────────────────────────────
async function migrateQuotes(bookIds) {
  console.log('\n💬 Book quotes...');
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  let total = 0, done = 0;
  for (const bookId of bookIds) {
    const snap = await db.collection('library_books').doc(bookId).collection('quotes').get();
    if (snap.empty) continue;
    total += snap.size;
    const rows = snap.docs.map(doc => {
      const d = doc.data();
      return {
        id: doc.id, book_id: bookId, author_id: safeStr(d.authorId) || null,
        book_title: safeStr(d.bookTitle), author_name: safeStr(d.authorName),
        text: safeStr(d.text), uid: safeStr(d.uid),
        user_display_name: safeStr(d.userDisplayName), user_photo_url: safeStr(d.userPhotoURL),
        feed_post_id: safeStr(d.feedPostId), likes_count: safeInt(d.likesCount),
        created_at: safeTs(d.ts),
      };
    });
    for (const batch of chunk(rows, BATCH_SIZE)) {
      await upsert('book_quotes', batch);
      done += batch.length;
    }
  }
  console.log(`   ✅ ${done}/${total} alıntı taşındı`);
}

// ── 4. Follows ────────────────────────────────────────────────────────────────
async function migrateFollows() {
  console.log('\n👥 Follows...');
  const snap = await db.collection('follows').get();
  console.log(`   ${snap.size} takip`);
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id,
      from_uid: safeStr(d.fromUid), from_name: safeStr(d.fromName), from_photo: safeStr(d.fromPhoto),
      target_uid: safeStr(d.targetUid), target_name: safeStr(d.targetName), target_photo: safeStr(d.targetPhoto),
      created_at: safeTs(d.ts),
    };
  }).filter(r => r.from_uid && r.target_uid);

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('follows', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} takip taşındı`);
}

// ── 5. Feed Likes ─────────────────────────────────────────────────────────────
async function migrateFeedLikes() {
  console.log('\n❤️  Feed likes...');
  const snap = await db.collection('feedLikes').get();
  console.log(`   ${snap.size} beğeni`);
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, post_id: safeStr(d.feedId || d.postId), uid: safeStr(d.uid),
      name: safeStr(d.displayName || d.name), photo_url: safeStr(d.photoURL || d.photoUrl),
      created_at: safeTs(d.ts),
    };
  }).filter(r => r.post_id && r.uid);

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('feed_likes', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} beğeni taşındı`);
}

// ── 6. Feed Saves ─────────────────────────────────────────────────────────────
async function migrateFeedSaves() {
  console.log('\n🔖 Feed saves...');
  const snap = await db.collection('feedSaves').get();
  console.log(`   ${snap.size} kayıt`);
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, post_id: safeStr(d.feedId || d.postId),
      uid: safeStr(d.uid), created_at: safeTs(d.ts),
    };
  }).filter(r => r.post_id && r.uid);

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('feed_saves', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} kayıt taşındı`);
}

// ── 7. Serial Likes ───────────────────────────────────────────────────────────
async function migrateSerialLikes() {
  console.log('\n📚 Serial likes...');
  const snap = await db.collection('serialLikes').get();
  console.log(`   ${snap.size} serial beğenisi`);
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  const rows = snap.docs.map(doc => {
    const d = doc.data();
    return {
      id: doc.id, serial_id: safeStr(d.serialId), uid: safeStr(d.uid),
      name: safeStr(d.displayName || d.name), photo_url: safeStr(d.photoURL || d.photoUrl),
      created_at: safeTs(d.ts),
    };
  }).filter(r => r.serial_id && r.uid);

  let done = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    await upsert('serial_likes', batch);
    done += batch.length;
    process.stdout.write(`   ✅ ${done}/${rows.length}\r`);
  }
  console.log(`\n   ✅ ${done} serial beğenisi taşındı`);
}

// ── 8. Comment Likes ──────────────────────────────────────────────────────────
async function migrateCommentLikes() {
  console.log('\n💬 Comment likes...');
  const feedSnap = await db.collection('feed').limit(500).get();
  let total = 0, done = 0;
  if (DRY_RUN) { console.log('   [DRY RUN]'); return; }

  for (const feedDoc of feedSnap.docs) {
    const cmtSnap = await feedDoc.ref.collection('comments').get();
    for (const cmtDoc of cmtSnap.docs) {
      const likeSnap = await cmtDoc.ref.collection('likes').get();
      if (likeSnap.empty) continue;
      total += likeSnap.size;
      const rows = likeSnap.docs.map(doc => ({
        id: `${cmtDoc.id}_${doc.id}`,
        comment_id: cmtDoc.id, uid: safeStr(doc.data().uid),
        name: '', photo_url: '', created_at: safeTs(doc.data().ts),
      })).filter(r => r.uid);
      for (const batch of chunk(rows, BATCH_SIZE)) {
        await upsert('comment_likes', batch);
        done += batch.length;
      }
    }
  }
  console.log(`   ✅ ${done}/${total} yorum beğenisi taşındı`);
}

// ── Ana akış ──────────────────────────────────────────────────────────────────
async function main() {
  console.log('🚀 Supabase migration başlıyor...');
  console.log(`   DRY_RUN: ${DRY_RUN}`);
  console.log(`   Supabase URL: ${SUPABASE_URL}`);

  if (!SUPABASE_URL) throw new Error('SUPABASE_URL eksik');
  if (!SUPABASE_KEY) throw new Error('SUPABASE_SERVICE_KEY eksik');

  const authorIds = await migrateAuthors();
  const bookIds   = await migrateBooks(authorIds);
  await migrateQuotes(bookIds);
  await migrateFollows();
  await migrateFeedLikes();
  await migrateFeedSaves();
  await migrateSerialLikes();
  await migrateCommentLikes();

  console.log('\n🎉 Migration tamamlandı!');
}

main().catch(e => { console.error('❌ Hata:', e.message); process.exit(1); });
