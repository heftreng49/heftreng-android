/**
 * heftreng fix-usernames-v2.js
 *
 * [A] DISPLAY NAME  — placeholder/bos olanlari duzeltir
 *     Firestore users → gunceller
 *     Supabase users (display_name) → senkronize eder
 *
 * [B] USERNAME — Turkce karakter normalize + race condition duzeltmesi
 *     Firestore users + usernames → atomik transaction ile gunceller
 *     Supabase users tablosunda username kolonu YOK — etkilenmez
 *
 * [C] FOLLOWS SYNC — eski takip kayitlarindaki bozuk from_name/target_name
 *     Supabase follows → display_name degisen kullanicilarin kayitlarini gunceller
 *
 * Env variables (GitHub Actions secrets):
 *   FIREBASE_SERVICE_ACCOUNT  — JSON string
 *   SUPABASE_URL              — https://xxx.supabase.co
 *   SUPABASE_SERVICE_KEY      — service_role key (yazma icin)
 *
 * Kullanim:
 *   node fix-usernames-v2.js              -> dry-run
 *   node fix-usernames-v2.js --write      -> Firestore + Supabase yaz
 *   node fix-usernames-v2.js --write --sync-feed   -> feed de dahil
 */

const admin    = require('firebase-admin');
const { createClient } = require('@supabase/supabase-js');

// ── Baglanti ──────────────────────────────────────────────────────────────
const FB_SA         = process.env.FIREBASE_SERVICE_ACCOUNT
                        ? JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT)
                        : require('./serviceAccount.json');
const SUPABASE_URL  = process.env.SUPABASE_URL  || '';
const SUPABASE_KEY  = process.env.SUPABASE_SERVICE_KEY || '';

admin.initializeApp({ credential: admin.credential.cert(FB_SA) });
const db = admin.firestore();

const supabase = (SUPABASE_URL && SUPABASE_KEY)
  ? createClient(SUPABASE_URL, SUPABASE_KEY)
  : null;

const DRY_RUN   = !process.argv.includes('--write');
const SYNC_FEED = process.argv.includes('--sync-feed');

if (DRY_RUN)   console.log('DRY-RUN — degisiklik yazilmayacak.\n');
if (!supabase) console.log('UYARI: SUPABASE_URL/KEY eksik — Supabase adimlari atlanacak.\n');

// ── Yardimcilar ───────────────────────────────────────────────────────────
function normalizeTurkish(s) {
  return (s || '')
    .replace(/\u011f|\u011e/g, 'g')   // ğ Ğ
    .replace(/\u015f|\u015e/g, 's')   // ş Ş
    .replace(/\u0131/g, 'i')          // ı
    .replace(/\u0130/g, 'i')          // İ
    .replace(/\u00f6|\u00d6/g, 'o')   // ö Ö
    .replace(/\u00fc|\u00dc/g, 'u')   // ü Ü
    .replace(/\u00e7|\u00c7/g, 'c');  // ç Ç
}

function toHandle(raw) {
  return normalizeTurkish((raw || '').trim())
    .toLowerCase().replace(/[^a-z0-9_]/g, '').slice(0, 20);
}

const PLACEHOLDERS = new Set([
  'kullanici','kullan\u0131c\u0131','bikarhener','bikarh\u00eaner','misafir','guest','user',''
]);
const isPlaceholder = name => PLACEHOLDERS.has((name||'').trim().toLowerCase());

function chunk(arr, n) {
  const out = [];
  for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i+n));
  return out;
}

// ── Ana fonksiyon ─────────────────────────────────────────────────────────
async function main() {
  const usersSnap = await db.collection('users').get();
  console.log('Toplam ' + usersSnap.size + ' kullanici.\n');

  // Analiz
  const displayNameFixes = [];  // { uid, oldName, newName }
  const usernameFixes    = [];  // { uid, oldHandle, newHandle }
  const uidToHandle      = {};
  const handleToUids     = {};

  usersSnap.forEach(doc => {
    const uid = doc.id;
    const d   = doc.data();

    // [A] Display name
    const dn  = (d.displayName || '').trim();
    const nm  = (d.name        || '').trim();
    const em  = (d.email       || '').trim();
    const un  = (d.username    || '').trim();
    if (isPlaceholder(dn)) {
      const newName =
        (nm && !isPlaceholder(nm)) ? nm :
        em ? em.split('@')[0]      :
        (un && !isPlaceholder(un)) ? un : 'Kullanici';
      if (newName !== dn) displayNameFixes.push({ uid, oldName: dn, newName });
    }

    // [B] Username
    const current = un;
    const fixed   = toHandle(current);
    uidToHandle[uid] = { current, fixed };
    if (!handleToUids[fixed]) handleToUids[fixed] = [];
    handleToUids[fixed].push(uid);
    if (current !== fixed && fixed.length >= 3)
      usernameFixes.push({ uid, oldHandle: current, newHandle: fixed });
  });

  // ── Rapor ─────────────────────────────────────────────────────────────
  console.log('=== [A] DISPLAY NAME ===');
  if (!displayNameFixes.length) {
    console.log('Duzeltilecek yok.\n');
  } else {
    displayNameFixes.forEach(({ uid, oldName, newName }) =>
      console.log('  ' + uid.slice(0,8) + '  "' + oldName + '" -> "' + newName + '"'));
    console.log('');
  }

  console.log('=== [B] USERNAME ===');
  let collisions = 0;
  for (const [h, uids] of Object.entries(handleToUids)) {
    if (h && uids.length > 1) { collisions++;
      console.log('  CAKISMA: "' + h + '" -> ' + uids.join(', ')); }
  }
  if (!collisions)      console.log('  Cakisma yok.');
  if (!usernameFixes.length) {
    console.log('  Normalize gereken yok.\n');
  } else {
    usernameFixes.forEach(({ uid, oldHandle, newHandle }) =>
      console.log('  ' + uid.slice(0,8) + '  "' + oldHandle + '" -> "' + newHandle + '"'));
    console.log('');
  }

  console.log('=== [C] SUPABASE ETKI ANALIZI ===');
  console.log('  users.display_name  : ' + displayNameFixes.length + ' kayit guncellenmeli');
  console.log('  users.username      : KOLON YOK — etkilenmez');
  console.log('  follows.from_name   : display_name degisen kullanicilarin eski kayitlari guncellenmeli');
  console.log('  follows.target_name : ayni');
  console.log('  feed_likes.name     : ayni');
  console.log('  comment_likes.name  : ayni\n');

  if (DRY_RUN) {
    console.log('--- DRY-RUN bitti. --write ile gercek yazma yapin. ---');
    return;
  }

  // ── [A] Firestore display name ────────────────────────────────────────
  if (displayNameFixes.length) {
    console.log('\n[A] Firestore display name duzeltiliyor...');
    for (const ch of chunk(displayNameFixes, 400)) {
      const batch = db.batch();
      ch.forEach(({ uid, newName }) =>
        batch.update(db.collection('users').doc(uid), { displayName: newName, name: newName }));
      await batch.commit();
    }
    console.log('[A] ' + displayNameFixes.length + ' Firestore kaydi guncellendi.');
  }

  // ── [A] Supabase users.display_name ──────────────────────────────────
  if (supabase && displayNameFixes.length) {
    console.log('\n[A] Supabase users.display_name senkronize ediliyor...');
    // username'i de upsert'e dahil et (mevcut degerini koru, sadece display_name guncelle)
    const userSnaps = await db.collection('users').get();
    const uidToUsername = {};
    userSnaps.forEach(d => { uidToUsername[d.id] = toHandle(d.data().username || ''); });

    const rows = displayNameFixes.map(({ uid, newName }) => ({
      uid,
      display_name: newName,
      username:       uidToUsername[uid] || '',
      username_lower: uidToUsername[uid] || '',
    }));
    for (const ch of chunk(rows, 100)) {
      const { error } = await supabase.from('users').upsert(ch, { onConflict: 'uid' });
      if (error) console.error('  Supabase upsert hata: ' + error.message);
    }
    console.log('[A] ' + displayNameFixes.length + ' Supabase users kaydi guncellendi.');
  }

  // ── [C] Supabase follows / feed_likes / comment_likes ────────────────
  if (supabase && displayNameFixes.length) {
    console.log('\n[C] Supabase denormalize name alanlari guncelleniyor...');
    for (const { uid, newName } of displayNameFixes) {
      // follows: from_name
      await supabase.from('follows').update({ from_name: newName })
        .eq('from_uid', uid);
      // follows: target_name
      await supabase.from('follows').update({ target_name: newName })
        .eq('target_uid', uid);
      // feed_likes: name
      await supabase.from('feed_likes').update({ name: newName })
        .eq('uid', uid);
      // comment_likes: name
      await supabase.from('comment_likes').update({ name: newName })
        .eq('uid', uid);
      // serial_likes: name
      await supabase.from('serial_likes').update({ name: newName })
        .eq('uid', uid);
    }
    console.log('[C] ' + displayNameFixes.length + ' kullanicinin tum sosyal kayitlari guncellendi.');
  }

  // ── [B] Firestore username (transaction) ──────────────────────────────
  if (usernameFixes.length) {
    console.log('\n[B] Username duzeltiliyor...');
    let ok = 0, skip = 0, fail = 0;
    for (const { uid, oldHandle, newHandle } of usernameFixes) {
      if (handleToUids[newHandle]?.length > 1 && handleToUids[newHandle][0] !== uid) {
        console.log('  Atlandi (cakisma): ' + uid.slice(0,8) + ' "' + newHandle + '"');
        skip++; continue;
      }
      try {
        await db.runTransaction(async tx => {
          const ref  = db.collection('usernames').doc(newHandle);
          const snap = await tx.get(ref);
          if (snap.exists && snap.data().uid !== uid)
            throw new Error('"' + newHandle + '" baskasina ait');
          if (oldHandle) tx.delete(db.collection('usernames').doc(oldHandle));
          tx.set(ref, { uid, createdAt: admin.firestore.FieldValue.serverTimestamp() });
          tx.update(db.collection('users').doc(uid),
            { username: newHandle, usernameLower: newHandle });
        });
        console.log('  OK: ' + uid.slice(0,8) + ' "' + oldHandle + '" -> "' + newHandle + '"');
        ok++;
      } catch (e) { console.error('  HATA: ' + uid.slice(0,8) + ' ' + e.message); fail++; }
    }
    console.log('[B] ' + ok + ' duzeltildi | ' + skip + ' atlandi | ' + fail + ' hatali.');

    // Supabase username sync
    if (supabase && ok > 0) {
      console.log('[B] Supabase username senkronize ediliyor...');
      const fixedUids = new Set(
        usernameFixes
          .filter(({ uid, newHandle }) =>
            !(handleToUids[newHandle]?.length > 1 && handleToUids[newHandle][0] !== uid))
          .map(({ uid }) => uid)
      );
      const uRows = usernameFixes
        .filter(({ uid }) => fixedUids.has(uid))
        .map(({ uid, newHandle }) => ({ uid, username: newHandle, username_lower: newHandle }));
      for (const ch of chunk(uRows, 100)) {
        const { error } = await supabase.from('users').upsert(ch, { onConflict: 'uid' });
        if (error) console.error('  Supabase username upsert hata: ' + error.message);
      }
      console.log('[B] ' + uRows.length + ' Supabase username kaydi guncellendi.');
    }
  }

  // ── [B] Firestore feed sync (opsiyonel) ───────────────────────────────
  if (SYNC_FEED && usernameFixes.length) {
    console.log('\n[B] Feed gonderileri (username) guncelleniyor...');
    let total = 0;
    for (const { uid, newHandle } of usernameFixes) {
      const snap = await db.collection('feed').where('uid', '==', uid).get();
      if (snap.empty) continue;
      for (const ch of chunk(snap.docs, 400)) {
        const batch = db.batch();
        ch.forEach(d => batch.update(d.ref, { username: newHandle }));
        await batch.commit();
        total += ch.length;
      }
    }
    console.log('[B] ' + total + ' feed gonderisi guncellendi.');
  }

  // ── Orphan temizligi ──────────────────────────────────────────────────
  console.log('\nOrphan usernames temizleniyor...');
  const unSnap = await db.collection('usernames').get();
  const batch  = db.batch();
  let orphans  = 0;
  unSnap.forEach(doc => {
    const ownerUid = doc.data().uid;
    const ud = uidToHandle[ownerUid];
    if (!ud || toHandle(ud.fixed || ud.current) !== doc.id) {
      batch.delete(doc.ref); orphans++;
      console.log('  Silindi: "' + doc.id + '" (' + (ownerUid||'?').slice(0,8) + ')');
    }
  });
  if (orphans) await batch.commit();
  else console.log('  Orphan yok.');

  console.log('\n=== SONUC ===');
  console.log('  Display name (Firestore) : ' + displayNameFixes.length);
  console.log('  Display name (Supabase)  : ' + (supabase ? displayNameFixes.length : 'ATLANDI (key eksik)'));
  console.log('  Sosyal kayitlar          : ' + (supabase ? displayNameFixes.length + ' kullanici' : 'ATLANDI'));
  console.log('  Username normalize       : ' + usernameFixes.length);
  console.log('  Orphan silindi           : ' + orphans);
}

main().catch(e => { console.error('FATAL:', e.message); process.exit(1); });
