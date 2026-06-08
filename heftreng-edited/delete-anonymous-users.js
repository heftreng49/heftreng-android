const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccount.json');

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db = admin.firestore();
const auth = admin.auth();

async function deleteAnonymousUsers() {
  console.log('Email\'siz (anonim) kullanıcılar taranıyor...');

  const snap = await db.collection('users').get();
  let deleted = 0;
  let skipped = 0;

  for (const doc of snap.docs) {
    const d = doc.data();
    const email = (d.email || '').trim();

    if (email) { skipped++; continue; }

    const uid      = doc.id;
    const name     = d.displayName || d.name || '?';
    const username = d.username    || '';

    console.log(`Siliniyor: ${uid.substring(0, 10)}... | ad: "${name}"`);

    try {
      // 1. Firestore users belgesi
      await db.collection('users').doc(uid).delete();

      // 2. usernames kaydı
      if (username) {
        await db.collection('usernames').doc(username).delete().catch(() => {});
      }

      // 3. follows ilişkileri
      const [fromSnap, toSnap] = await Promise.all([
        db.collection('follows').where('fromUid',    '==', uid).get(),
        db.collection('follows').where('targetUid',  '==', uid).get(),
      ]);
      const batch = db.batch();
      fromSnap.docs.forEach(d => batch.delete(d.ref));
      toSnap.docs.forEach(d => batch.delete(d.ref));
      if (fromSnap.size + toSnap.size > 0) await batch.commit();

      // 4. Firebase Auth'tan sil
      await auth.deleteUser(uid).catch(e => {
        console.log(`  Auth atlandı (${uid.substring(0,8)}): ${e.message}`);
      });

      deleted++;
    } catch (e) {
      console.error(`  HATA (${uid.substring(0,8)}):`, e.message);
    }
  }

  console.log(`\n✅ Tamamlandı: ${deleted} anonim kullanıcı silindi, ${skipped} gerçek kullanıcı korundu.`);
}

deleteAnonymousUsers().catch(e => {
  console.error('HATA:', e.message);
  process.exit(1);
});
