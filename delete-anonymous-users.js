const admin = require('firebase-admin');
const serviceAccount = require('./serviceAccount.json');

// ── Supabase (opsiyonel ama şiddetle önerilir) ──────────────────────────────
// SUPABASE_URL / SUPABASE_SERVICE_KEY env değişkenleri set edilmemişse betik
// yine de Firebase tarafını temizler, ama Supabase reconciliation adımını atlar
// ve bunu açıkça loglar — sessizce yarım iş yapmaz.
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_KEY;
let supabase = null;
if (SUPABASE_URL && SUPABASE_KEY) {
  const { createClient } = require('@supabase/supabase-js');
  supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
} else {
  console.warn('⚠️  SUPABASE_URL / SUPABASE_SERVICE_KEY tanımlı değil — Supabase temizliği ATLANACAK.');
  console.warn('   (Sadece Firebase tarafı silinecek, Supabase\'de hayalet kayıtlar kalabilir.)');
}

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount),
});

const db   = admin.firestore();
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

      // 5. Supabase users tablosundan DOĞRUDAN sil — onUserDeleted Cloud Function'ına
      //    güvenmiyoruz; o fonksiyon deploy edilmemiş/hatalı olsa bile Supabase
      //    kaydı burada garanti silinir. Bu yüzden bot hesapları "Kesên Pêşniyarkirî"
      //    önerilerinde hayalet olarak görünmeye devam ediyordu.
      if (supabase) {
        const { error } = await supabase.from('users').delete().eq('uid', uid);
        if (error) console.log(`  Supabase silme HATA (${uid.substring(0,8)}): ${error.message}`);
      }

      deleted++;
    } catch (e) {
      console.error(`  HATA (${uid.substring(0,8)}):`, e.message);
    }
  }

  console.log(`\n✅ Tamamlandı: ${deleted} anonim kullanıcı silindi, ${skipped} gerçek kullanıcı korundu.`);

  // ── Reconciliation: geçmişte oluşmuş TÜM hayalet Supabase kayıtlarını temizle ──
  // (Bu betik daha önce çalıştırılmış olabilir, ya da hesaplar Firebase Console'dan
  // elle silinmiş olabilir — onUserDeleted tetiklenmediyse Supabase'de uid'leri
  // artık Firebase Auth'ta hiç olmayan satırlar birikir. Bunları da temizliyoruz.)
  if (supabase) {
    console.log('\nSupabase reconciliation başlıyor (hayalet kayıtlar taranıyor)...');
    try {
      const existingUids = new Set();
      let pageToken;
      do {
        const result = await auth.listUsers(1000, pageToken);
        result.users.forEach(u => existingUids.add(u.uid));
        pageToken = result.pageToken;
      } while (pageToken);
      console.log(`  Firebase Auth'ta ${existingUids.size} aktif kullanıcı bulundu.`);

      let from = 0;
      const PAGE = 1000;
      let orphansDeleted = 0;
      while (true) {
        const { data: rows, error } = await supabase
          .from('users')
          .select('uid')
          .range(from, from + PAGE - 1);
        if (error) { console.error('  Supabase select HATA:', error.message); break; }
        if (!rows || rows.length === 0) break;

        const orphanUids = rows.map(r => r.uid).filter(uid => !existingUids.has(uid));
        if (orphanUids.length > 0) {
          const { error: delErr } = await supabase.from('users').delete().in('uid', orphanUids);
          if (delErr) {
            console.error(`  Supabase orphan silme HATA: ${delErr.message}`);
          } else {
            orphansDeleted += orphanUids.length;
            console.log(`  ${orphanUids.length} hayalet kayıt silindi (sayfa offset ${from})`);
          }
        }
        if (rows.length < PAGE) break;
        from += PAGE;
      }
      console.log(`✅ Reconciliation tamamlandı: ${orphansDeleted} hayalet Supabase kaydı silindi.`);
    } catch (e) {
      console.error('  Reconciliation genel HATA:', e.message);
    }
  }
}

deleteAnonymousUsers().catch(e => {
  console.error('HATA:', e.message);
  process.exit(1);
});
