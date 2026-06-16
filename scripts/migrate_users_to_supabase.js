// scripts/migrate_users_to_supabase.js
// Firestore users koleksiyonundaki mevcut kullanıcıları Supabase users tablosuna taşır.
// Upsert kullandığı için defalarca çalıştırılabilir — duplicate olmaz.
// GitHub Actions → supabase-migrate.yml ile tetiklenir (workflow_dispatch).

const { initializeApp, cert } = require('firebase-admin/app');
const { getFirestore }        = require('firebase-admin/firestore');
const { createClient }        = require('@supabase/supabase-js');

const DRY_RUN      = process.env.DRY_RUN === 'true';
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_KEY;
const FB_SA        = JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT);
const BATCH_SIZE   = 100;

initializeApp({ credential: cert(FB_SA) });
const db       = getFirestore();
const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

function chunk(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

async function run() {
  console.log(`[migrate_users] başlıyor — DRY_RUN=${DRY_RUN}`);

  const snap = await db.collection('users').get();
  console.log(`[migrate_users] Firestore'da ${snap.size} kullanıcı bulundu`);

  const rows = [];
  snap.forEach((doc) => {
    const d = doc.data();
    const displayName = (d.displayName || d.name || '').trim();
    if (!displayName) return; // boş isimli kayıtları atla

    rows.push({
      uid          : doc.id,
      display_name : displayName,
      photo_url    : d.photoURL || '',
      bio          : d.bio || '',
      banned       : d.banned === true,
      created_at   : d.createdAt?.toDate?.()?.toISOString?.() || new Date().toISOString(),
    });
  });

  console.log(`[migrate_users] ${rows.length} kayıt işlenecek`);

  if (DRY_RUN) {
    console.log('[migrate_users] DRY RUN — Supabase\'e yazılmadı');
    console.log('Örnek kayıt:', rows[0]);
    return;
  }

  let success = 0, failed = 0;
  for (const batch of chunk(rows, BATCH_SIZE)) {
    const { error } = await supabase
      .from('users')
      .upsert(batch, { onConflict: 'uid' });

    if (error) {
      console.error(`[migrate_users] Batch hata: ${error.message}`);
      failed += batch.length;
    } else {
      success += batch.length;
      process.stdout.write(`\r[migrate_users] ${success}/${rows.length} tamamlandı`);
    }
  }

  console.log(`\n[migrate_users] ✅ ${success} başarılı, ${failed} hatalı`);
}

run().catch((e) => { console.error(e); process.exit(1); });
