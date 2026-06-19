const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccount.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();

async function fixUserNames() {
  console.log('Kullanıcılar taranıyor...');
  const snap = await db.collection('users').get();

  let fixed = 0, skipped = 0;
  const batch = db.batch();

  snap.forEach(doc => {
    const d = doc.data();
    const displayName = (d.displayName || '').trim();
    const name        = (d.name        || '').trim();
    const email       = (d.email       || '').trim();
    const username    = (d.username    || '').trim();

    // Zaten iyi bir adı varsa atla
    const hasGoodName = displayName
      && displayName !== 'Kullanıcı'
      && displayName !== 'Bikarhêner'
      && displayName !== 'Misafir';

    if (hasGoodName) { skipped++; return; }

    // Öncelik: name → email prefix → username → 'Kullanıcı'
    const finalName = (name && name !== 'Kullanıcı' && name !== 'Bikarhêner')
      ? name
      : email
      ? email.split('@')[0]
      : username || 'Kullanıcı';

    console.log(`[FIX] ${doc.id.substring(0,8)}... → "${finalName}"`);
    batch.update(doc.ref, { displayName: finalName, name: finalName });
    fixed++;
  });

  if (fixed === 0) {
    console.log('Düzeltilecek kullanıcı bulunamadı.');
    return;
  }

  await batch.commit();
  console.log(`\nTamamlandı → ${fixed} düzeltildi, ${skipped} atlandı.`);
}

fixUserNames().catch(e => { console.error('HATA:', e.message); process.exit(1); });
