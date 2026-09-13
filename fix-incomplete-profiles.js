/**
 * fix-incomplete-profiles.js
 *
 * Firebase Auth'ta var olan ama Firestore'da profili eksik/yarım kalan
 * kullanicilari tespit eder ve tam bir profil olusturur.
 *
 * "Eksik profil" tanimı:
 *   1. Firestore users/{uid} belgesi HIC YOK
 *   2. Belge var ama zorunlu alanlar eksik (displayName, username, email)
 *   3. Supabase users tablosunda kayit YOK (Auth trigger calismamis)
 *   4. usernames/{username} rezervasyon belgesi YOK (createUserDoc yanda kesilmis)
 *
 * Her eksiklik bagimsiz duzeltilir — ust adim basarisiz olsa bile
 * diger adimlar atlanmaz.
 *
 * Kullanim:
 *   node fix-incomplete-profiles.js            -> dry-run (hicbir sey yazilmaz)
 *   node fix-incomplete-profiles.js --write    -> eksikleri duzelt
 *   node fix-incomplete-profiles.js --write --uid=abc123  -> tek kullanici
 *
 * Gerekli env:
 *   FIREBASE_SERVICE_ACCOUNT  — JSON string (GitHub Actions secret)
 *   SUPABASE_URL              — https://xxx.supabase.co
 *   SUPABASE_SERVICE_KEY      — service_role key
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
const SINGLE_UID = (process.argv.find(a => a.startsWith('--uid=')) || '').replace('--uid=', '');

if (DRY_RUN)   console.log('DRY-RUN modu — hicbir sey yazilmayacak.\n');
if (SINGLE_UID) console.log(`Sadece 1 kullanici isleniyor: ${SINGLE_UID}\n`);

// ── Yardimcilar ──────────────────────────────────────────────────────────────

function normalizeTurkish(s) {
  return (s || '')
    .replace(/[ğĞ]/g, 'g').replace(/[şŞ]/g, 's').replace(/ı/g, 'i')
    .replace(/İ/g, 'i').replace(/[öÖ]/g, 'o').replace(/[üÜ]/g, 'u')
    .replace(/[çÇ]/g, 'c');
}

function toHandle(raw) {
  return normalizeTurkish((raw || '').trim())
    .toLowerCase().replace(/[^a-z0-9_]/g, '').slice(0, 20) || 'user';
}

/** Kullanıcı adını atomik transaction ile rezerve eder. Alındıysa false döner. */
async function claimUsername(handle, uid) {
  try {
    await db.runTransaction(async tx => {
      const ref  = db.collection('usernames').document(handle);
      const snap = await tx.get(ref);
      if (snap.exists && snap.data().uid !== uid) {
        throw new Error('taken');
      }
      tx.set(ref, { uid });
    });
    return true;
  } catch { return false; }
}

/** Benzersiz bir username uretir. */
async function generateUsername(displayName, uid) {
  // Once mevcut Firestore kaydinda username var mi?
  const existing = await db.collection('users').doc(uid).get()
    .then(s => s.data()?.username).catch(() => null);
  if (existing && existing.trim()) return existing;

  const base = toHandle(displayName);
  let handle = base;
  for (let i = 0; i < 8; i++) {
    if (await claimUsername(handle, uid)) return handle;
    handle = base.slice(0, 16) + Math.floor(1000 + Math.random() * 9000);
  }
  // Son care: uid sonucu
  const fallback = base.slice(0, 12) + '_' + uid.slice(-6);
  await claimUsername(fallback, uid);
  return fallback;
}

/**
 * Firestore'da eksik alanlari tek seferlik set(merge) ile doldurur.
 * Var olan alanlara dokunmaz (merge: true).
 */
async function ensureFirestoreDoc(user, stats) {
  const ref  = db.collection('users').doc(user.uid);
  const snap = await ref.get();
  const data = snap.exists ? snap.data() : null;

  // Hangi alanlar eksik?
  const missingDoc      = !snap.exists;
  const missingName     = !data?.displayName?.trim();
  const missingUsername = !data?.username?.trim();
  const missingEmail    = !data?.email?.trim() && user.email;

  if (!missingDoc && !missingName && !missingUsername && !missingEmail) {
    return; // Firestore tarafı tam
  }

  stats.firestoreFixed++;

  const name = (data?.displayName?.trim())
    || user.displayName?.trim()
    || user.email?.split('@')[0]
    || 'Kullanıcı';

  const email = data?.email?.trim() || user.email || '';

  // username — var olup olmadığını claimUsername kontrol eder
  let username = data?.username?.trim();
  if (!username) {
    username = await generateUsername(name, user.uid);
  }

  const isGoogle = user.providerData?.some(p => p.providerId === 'google.com');

  const patch = {
    uid:            user.uid,
    displayName:    name,
    name:           name,
    username,
    usernameLower:  username.toLowerCase(),
    email,
    photoURL:       data?.photoURL ?? user.photoUrl ?? '',
    coverPhoto:     data?.coverPhoto ?? '',
    bio:            data?.bio ?? '',
    website:        data?.website ?? '',
    xp:             data?.xp ?? 0,
    kf_xp:          data?.kf_xp ?? 0,
    level:          data?.level ?? 1,
    streak:         data?.streak ?? 0,
    kf_streak:      data?.kf_streak ?? 0,
    banned:         data?.banned ?? false,
    followersCount: data?.followersCount ?? 0,
    followingCount: data?.followingCount ?? 0,
    postsCount:     data?.postsCount ?? 0,
    emailVerified:  data?.emailVerified ?? isGoogle,
    signInMethod:   data?.signInMethod ?? (isGoogle ? 'google' : 'email'),
    platform:       data?.platform ?? 'android',
    appVersion:     data?.appVersion ?? '',
    createdAt:      data?.createdAt ?? admin.firestore.Timestamp.now(),
    lastSeen:       data?.lastSeen  ?? admin.firestore.Timestamp.now(),
  };

  const label = missingDoc ? 'YENİ BELGE' : 'GUNCELLEME';
  const missing = [
    missingDoc      && 'belge yok',
    missingName     && 'displayName bos',
    missingUsername && 'username bos',
    missingEmail    && 'email bos',
  ].filter(Boolean).join(', ');

  console.log(`  [Firestore ${label}] ${user.uid.slice(0,10)}... | ${name} | @${username} | eksik: ${missing}`);

  if (!DRY_RUN) {
    await ref.set(patch, { merge: true });
  }
}

/**
 * Supabase users tablosunda satir yoksa ekler.
 * Var ama display_name bos ise gunceller.
 */
async function ensureSupabaseRow(user, fsData, username, stats) {
  if (!supabase) return;

  const { data: existing, error: fetchErr } = await supabase
    .from('users')
    .select('uid, display_name, username')
    .eq('uid', user.uid)
    .maybeSingle();

  if (fetchErr) {
    console.warn(`    Supabase fetch hatasi (${user.uid.slice(0,8)}...): ${fetchErr.message}`);
    return;
  }

  const name    = fsData?.displayName?.trim() || user.displayName?.trim() || 'Kullanıcı';
  const photo   = fsData?.photoURL ?? user.photoUrl ?? '';
  const bio     = fsData?.bio ?? '';

  if (!existing) {
    // Satir hic yok — ekle
    stats.supabaseFixed++;
    console.log(`  [Supabase INSERT] ${user.uid.slice(0,10)}... | @${username}`);
    if (!DRY_RUN) {
      const { error } = await supabase.from('users').insert({
        uid:           user.uid,
        display_name:  name,
        photo_url:     photo,
        bio,
        banned:        false,
        username,
        username_lower: username.toLowerCase(),
        created_at:    new Date().toISOString(),
      });
      if (error) console.warn(`    Supabase insert hatasi: ${error.message}`);
    }
  } else if (!existing.display_name?.trim() || !existing.username?.trim()) {
    // Satir var ama bos alanlari doldur
    stats.supabaseFixed++;
    console.log(`  [Supabase UPDATE] ${user.uid.slice(0,10)}... | display_name veya username bos`);
    if (!DRY_RUN) {
      const patch = {};
      if (!existing.display_name?.trim()) patch.display_name = name;
      if (!existing.username?.trim())     { patch.username = username; patch.username_lower = username.toLowerCase(); }
      const { error } = await supabase.from('users').update(patch).eq('uid', user.uid);
      if (error) console.warn(`    Supabase update hatasi: ${error.message}`);
    }
  }
}

/** username rezervasyon belgesi (usernames/{handle}) eksikse olusturur. */
async function ensureUsernameReservation(uid, username, stats) {
  const ref  = db.collection('usernames').doc(username);
  const snap = await ref.get();

  if (!snap.exists) {
    stats.usernameReserved++;
    console.log(`  [Usernames] @${username} rezervasyonu eksik — olusturuluyor`);
    if (!DRY_RUN) await ref.set({ uid });
  } else if (snap.data().uid !== uid) {
    // Baska birine ait — yeni username uret (dry-run'da sadece logla)
    console.warn(`  [Usernames] @${username} baskasina ait! (${snap.data().uid.slice(0,8)}...) — uid degismeli`);
  }
}

// ── Ana fonksiyon ─────────────────────────────────────────────────────────────

async function main() {
  console.log('Kullanicilar yukleniyor...\n');

  // Firebase Auth'taki TUM kullanicilari cek
  let allUsers = [];
  let pageToken;
  do {
    const result = await auth.listUsers(1000, pageToken);
    allUsers = allUsers.concat(result.users);
    pageToken = result.pageToken;
  } while (pageToken);

  if (SINGLE_UID) {
    allUsers = allUsers.filter(u => u.uid === SINGLE_UID);
    if (!allUsers.length) { console.error('UID bulunamadi.'); process.exit(1); }
  }

  console.log(`Toplam Firebase Auth kullanicisi: ${allUsers.length}`);

  // Firestore users koleksiyonunu toplu cek (batch)
  const fsSnap    = await db.collection('users').get();
  const fsMap     = new Map(fsSnap.docs.map(d => [d.id, d.data()]));
  console.log(`Firestore users kaydi: ${fsMap.size}`);

  // Supabase users'i toplu cek
  let sbMap = new Map();
  if (supabase) {
    const { data: sbRows } = await supabase.from('users').select('uid, display_name, username');
    if (sbRows) sbMap = new Map(sbRows.map(r => [r.uid, r]));
    console.log(`Supabase users kaydi: ${sbMap.size}`);
  }

  // Eksik profilleri bul
  const broken = allUsers.filter(u => {
    const fs = fsMap.get(u.uid);
    if (!fs) return true;                        // Belge hic yok
    if (!fs.displayName?.trim()) return true;    // displayName bos
    if (!fs.username?.trim())    return true;    // username bos
    return false;
  });

  // Auth'ta photoUrl var ama Firestore'da photoURL bos olanlar
  const photoMissing = allUsers.filter(u => {
    if (broken.find(b => b.uid === u.uid)) return false; // zaten broken'da
    const fs = fsMap.get(u.uid);
    if (!fs) return false;
    return (u.photoUrl) && !(fs.photoURL?.trim());
  });

  console.log(`\nEksik/yarim profil sayisi: ${broken.length}`);
  console.log(`Fotograf eksik (Auth'ta var, Firestore'da yok): ${photoMissing.length}`);

  if (photoMissing.length) {
    console.log('\n── Fotograf guncelleniyor ──────────────────────────────────');
    for (const user of photoMissing) {
      console.log(`  [photoURL FIX] ${user.uid.slice(0,10)}... → ${user.photoUrl}`);
      if (!DRY_RUN) {
        await db.collection('users').doc(user.uid).update({ photoURL: user.photoUrl });
      }
    }
  }

  if (!broken.length) { console.log('Profil duzeltme gerekmiyor.'); return; }

  console.log('\n' + '─'.repeat(60));

  const stats = { firestoreFixed: 0, supabaseFixed: 0, usernameReserved: 0, errors: 0 };

  for (const user of broken) {
    console.log(`\n[${broken.indexOf(user) + 1}/${broken.length}] ${user.uid}`);
    try {
      // 1. Firestore belgesi
      await ensureFirestoreDoc(user, stats);

      // Guncel Firestore verisini al (yeni yazdıysa oradan, yoksa Auth'tan)
      const fsData   = fsMap.get(user.uid) || {};
      const name     = fsData.displayName?.trim() || user.displayName?.trim() || 'Kullanıcı';
      const username = fsData.username?.trim() || await generateUsername(name, user.uid);

      // 2. usernames/ rezervasyonu
      await ensureUsernameReservation(user.uid, username, stats);

      // 3. Supabase satiri
      await ensureSupabaseRow(user, fsData, username, stats);

    } catch (err) {
      stats.errors++;
      console.error(`  HATA: ${err.message}`);
    }
  }

  console.log('\n' + '─'.repeat(60));
  console.log('SONUC:');
  console.log(`  Firestore duzeltilen : ${stats.firestoreFixed}`);
  console.log(`  Supabase duzeltilen  : ${stats.supabaseFixed}`);
  console.log(`  Username rezerve     : ${stats.usernameReserved}`);
  console.log(`  Hatalar              : ${stats.errors}`);
  if (DRY_RUN) console.log('\n(DRY-RUN — hicbir degisiklik yazilmadi. --write ekle.)');
}

main().catch(err => { console.error(err); process.exit(1); });
