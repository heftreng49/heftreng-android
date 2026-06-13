// scripts/sync_counts.js
// Supabase feed_likes/feed_saves sayılarını Firestore feed doc'larına yazar
// Kullanım: DRY_RUN=true node sync_counts.js

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');
const { createClient }        = require('@supabase/supabase-js');

const DRY_RUN  = process.env.DRY_RUN === 'true';
const supabase = createClient(
  process.env.SUPABASE_URL,
  process.env.SUPABASE_SERVICE_KEY
);
initializeApp({ credential: cert(JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)) });
const db = getFirestore();

async function run() {
  console.log('═══════════════════════════════════');
  console.log('  Like/Save Sayaç Senkronizasyonu');
  console.log(`  Mod: ${DRY_RUN ? '🔍 DRY RUN' : '🚀 CANLI'}`);
  console.log('═══════════════════════════════════');

  // feed_likes say
  const { data: likes, error: likeErr } = await supabase
    .from('feed_likes').select('post_id');
  if (likeErr) { console.error('Supabase like hata:', likeErr.message); process.exit(1); }

  const counts = {};
  for (const r of likes) counts[r.post_id] = (counts[r.post_id] || 0) + 1;
  console.log(`\n❤️  ${Object.keys(counts).length} post için like sayısı`);

  // feed_saves say
  const { data: saves, error: saveErr } = await supabase
    .from('feed_saves').select('post_id');
  if (saveErr) { console.error('Supabase save hata:', saveErr.message); process.exit(1); }

  const saveCounts = {};
  for (const r of saves) saveCounts[r.post_id] = (saveCounts[r.post_id] || 0) + 1;
  console.log(`🔖 ${Object.keys(saveCounts).length} post için save sayısı`);

  if (DRY_RUN) {
    console.log('\n[DRY RUN] Firestore yazma atlandı');
    return;
  }

  // Firestore'u güncelle
  const allPostIds = [...new Set([...Object.keys(counts), ...Object.keys(saveCounts)])];
  console.log(`\n🔄 ${allPostIds.length} post güncelleniyor...`);

  let updated = 0;
  for (let i = 0; i < allPostIds.length; i += 490) {
    const chunk = allPostIds.slice(i, i + 490);
    const batch = db.batch();
    for (const postId of chunk) {
      const updates = {};
      if (counts[postId]     !== undefined) updates.likes = counts[postId];
      if (saveCounts[postId] !== undefined) updates.saves = saveCounts[postId];
      batch.update(db.collection('feed').doc(postId), updates);
    }
    await batch.commit();
    updated += chunk.length;
    process.stdout.write(`   ✅ ${updated}/${allPostIds.length}\r`);
  }
  console.log(`\n✅ Tamamlandı: ${updated} post güncellendi`);
}

run().catch(e => { console.error('FATAL:', e); process.exit(1); });
