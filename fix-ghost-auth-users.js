/**
 * fix-ghost-auth-users.js
 *
 * Firebase Auth'ta kaydi olan ama Firestore'da users/{uid} belgesi
 * BULUNMAYAN kullanicilari tespit eder ve tum izlerini siler.
 *
 * "Hayalet Auth" tanimlari:
 *   A) Firestore users belgesi yok + email var (silinen gercek hesap)
 *   B) Firestore users belgesi yok + email yok (anonim / yari kayitli)
 *   C) Firestore'da "banned: true" + Auth'ta hala aktif (cezali hesap temizligi)
 *
 * Her silinen uid icin:
 *   1. Firebase Auth kaydini sil
 *   2. Firestore users belgesi varsa sil (C durumu icin)
 *   3. usernames/{username} rezervasyonunu sil
 *   4. Supabase users satirini sil
 *   5. follows (from + target) satirlarini sil
 *
 * Guvenlik kilitleri:
 *   - Auth'tan 0 kullanici donerse hic islem yapma
 *   - Silme orani toplam Auth kullanici sayisinin %20'sinden fazlaysa dur
 *   - dry-run modunda hicbir sey yazilmaz
 *
 * Kullanim:
 *   node fix-ghost-auth-users.js              -> dry-run
 *   node fix-ghost-auth-users.js --write      -> sil
 *   node fix-ghost-auth-users.js --write --uid=abc123  -> tek kullanici
 *   node fix-ghost-auth-users.js --write --banned      -> sadece banlilari sil
 */

'use strict';

const admin = require('firebase-admin');
const { createClient } = require('@supabase/supabase-js');

// ── Baglanti ──────────────────────────────────────────────────────────────────

const FB_SA = process.env.FIREBASE_SERVICE_ACCOUNT
  ? JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)
  : (() => { try { return require('./serviceAccount.json'); } catch { return null; } })();

if (!FB_SA) {
  console.error('HATA: FIREBASE_SERVICE_ACCOUNT env degiskeni veya serviceAccount.json gerekli.');
  process.exit(1);
}

const SUPABASE_URL = process.env.SUPABASE_URL || '';
const SUPABASE_KEY = process.env.SUPABASE_SERVICE_KEY || '';

admin.initializeApp({ credential: admin.credential.cert(FB_SA) });
const db   = admin.firestore();
const auth = admin.auth();

let supabase = null;
if (SUPABASE_URL && SUPABASE_KEY) {
  supabase = createClient(SUPABASE_URL, SUPABASE_KEY);
} else {
  console.warn('UYARI: SUPABASE_URL/KEY eksik — Supabase adimlari atlanacak.\n');
}

const DRY_RUN    = !process.argv.includes('--write');
const ONLY_UID   = (process.argv.find(a => a.startsWith('--uid=')) || '').replace('--uid=', '');
const ONLY_BANNED = process.argv.includes('--banned');

if (DRY_RUN)     console.log('DRY-RUN modu — hicbir sey silinmeyecek.\n');
if (ONLY_UID)    console.log(`Sadece 1 kullanici: ${ONLY_UID}\n`);
if (ONLY_BANNED) console.log('Sadece banlı kullanicilar isleniyor.\n');

// ── Tek kullanici silme ───────────────────────────────────────────────────────

async function deleteGhostUser(uid, username, reason, stats) {
  console.log(`  Sebep: ${reason}`);

  // 1. Firebase Auth
  if (!DRY_RUN) {
    try {
      await auth.deleteUser(uid);
      console.log(`  ✓ Auth silindi`);
    } catch (e) {
      if (e.code === 'auth/user-not-found') {
        console.log(`  ~ Auth zaten yok`);
      } else {
        console.warn(`  ✗ Auth silme HATA: ${e.message}`);
      }
    }
  }

  // 2. Firestore users belgesi (C durumu — banned)
  if (!DRY_RUN) {
    try {
      await db.collection('users').doc(uid).delete();
      console.log(`  ✓ Firestore users silindi`);
    } catch (e) {
      console.warn(`  ✗ Firestore silme HATA: ${e.message}`);
    }
  }

  // 3. usernames rezervasyonu
  if (username && !DRY_RUN) {
    try {
      await db.collection('usernames').doc(username).delete();
      console.log(`  ✓ @${username} rezervasyonu silindi`);
    } catch (e) {
      console.warn(`  ✗ usernames silme HATA: ${e.message}`);
    }
  }

  // 4. Supabase users
  if (supabase && !DRY_RUN) {
    const { error } = await supabase.from('users').delete().eq('uid', uid);
    if (error) console.warn(`  ✗ Supabase silme HATA: ${error.message}`);
    else       console.log(`  ✓ Supabase users silindi`);
  }

  // 5. Follows (her iki yon)
  if (!DRY_RUN) {
    try {
      const [fromSnap, toSnap] = await Promise.all([
        db.collection('follows').where('fromUid',   '==', uid).get(),
        db.collection('follows').where('targetUid', '==', uid).get(),
      ]);
      const batch = db.batch();
      fromSnap.docs.forEach(d => batch.delete(d.ref));
      toSnap.docs.forEach(d => batch.delete(d.ref));
      const total = fromSnap.size + toSnap.size;
      if (total > 0) {
        await batch.commit();
        console.log(`  ✓ ${total} follows kaydı silindi`);
      }
    } catch (e) {
      console.warn(`  ✗ follows silme HATA: ${e.message}`);
    }
  }

  stats.deleted++;
}

// ── Ana fonksiyon ─────────────────────────────────────────────────────────────

async function main() {
  console.log('Kullanicilar yukleniyor...\n');

  // Firebase Auth — tum kullanicilar
  let allAuthUsers = [];
  let pageToken;
  do {
    const result = await auth.listUsers(1000, pageToken);
    allAuthUsers = allAuthUsers.concat(result.users);
    pageToken = result.pageToken;
  } while (pageToken);

  // Guvenlik kilidi #1
  if (allAuthUsers.length === 0) {
    console.error('HATA: Firebase Auth 0 kullanici dondu — islem iptal edildi.');
    process.exit(1);
  }

  if (ONLY_UID) {
    allAuthUsers = allAuthUsers.filter(u => u.uid === ONLY_UID);
    if (!allAuthUsers.length) { console.error('UID bulunamadi.'); process.exit(1); }
  }

  console.log(`Firebase Auth kullanicisi: ${allAuthUsers.length}`);

  // Firestore users — tum belgeler
  const fsSnap = await db.collection('users').get();
  const fsMap  = new Map(fsSnap.docs.map(d => [d.id, d.data()]));
  console.log(`Firestore users kaydi: ${fsMap.size}`);

  // Tespit: hayalet Auth kullanicilari
  const ghosts = [];

  for (const u of allAuthUsers) {
    const fs = fsMap.get(u.uid);

    if (ONLY_BANNED) {
      // Sadece banlı mod: Firestore'da banned:true olanlar
      if (fs?.banned === true) {
        ghosts.push({ user: u, fs, reason: 'Firestore banned:true' });
      }
      continue;
    }

    if (!fs) {
      // Firestore belgesi yok — hayalet Auth kaydı
      const hasEmail = !!(u.email?.trim());
      ghosts.push({
        user: u,
        fs: null,
        reason: hasEmail
          ? `Firestore belgesi yok (email: ${u.email})`
          : 'Firestore belgesi yok + email yok (anonim)',
      });
    }
  }

  console.log(`\nSilinecek hayalet/banned kullanici sayisi: ${ghosts.length}`);

  if (!ghosts.length) {
    console.log('Temizlenecek bir sey yok.');
    return;
  }

  // Guvenlik kilidi #2 — %20 esigi
  const ratio = ghosts.length / allAuthUsers.length;
  if (!ONLY_UID && ratio > 0.20) {
    console.error(`\nUYARI: Silme orani %${Math.round(ratio * 100)} — cok yuksek!`);
    console.error('Muhtemelen bir eslesme sorunu var. Guvenlik icin islem IPTAL edildi.');
    console.error('Tek tek test etmek icin: --uid=<uid>');
    process.exit(1);
  }

  console.log('\n' + '─'.repeat(60));

  const stats = { deleted: 0, errors: 0 };

  for (const { user: u, fs, reason } of ghosts) {
    const username = fs?.username || '';
    console.log(`\n[${ghosts.indexOf({ user: u, fs, reason }) + 1}/${ghosts.length}] ${u.uid}`);
    console.log(`  Ad: "${u.displayName || fs?.displayName || '?'}" | @${username || '?'}`);
    try {
      await deleteGhostUser(u.uid, username, reason, stats);
    } catch (e) {
      stats.errors++;
      console.error(`  HATA: ${e.message}`);
    }
  }

  console.log('\n' + '─'.repeat(60));
  console.log('SONUC:');
  console.log(`  Silinen  : ${stats.deleted}`);
  console.log(`  Hatalar  : ${stats.errors}`);
  if (DRY_RUN) console.log('\n(DRY-RUN — hicbir sey silinmedi. --write ekle.)');
}

main().catch(err => { console.error(err); process.exit(1); });
