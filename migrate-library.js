/**
 * migrate-library.js
 * ─────────────────────────────────────────────────────────────────
 * Heftreng — Kütüphane Migration Scripti
 * GitHub Actions ile çalışır (FIREBASE_SERVICE_ACCOUNT secret gerekir)
 *
 * Yapılan işlemler:
 *   1. authors → nameLower eksik olanlara ekle
 *   2. library_books → titleLower eksik olanlara ekle
 *   3. feed → library_books/quotes'a düşmemiş alıntıları sync et
 * ─────────────────────────────────────────────────────────────────
 */

const admin = require("firebase-admin");
const sa = require("./serviceAccount.json");

admin.initializeApp({
  credential: admin.credential.cert(sa),
  projectId: "bloggerheftreng",
});

const db = admin.firestore();

// ────────────────────────────────────────────────────────────────
//  ADIM 1 — authors: nameLower eksik olanlara ekle
// ────────────────────────────────────────────────────────────────
async function fixAuthors() {
  console.log("\n── ADIM 1: authors → nameLower ──");
  const snap = await db.collection("authors").get();
  let fixed = 0;
  const batch = db.batch();

  snap.docs.forEach((doc) => {
    const d = doc.data();
    if (!d.nameLower && d.name) {
      const nameLower = d.name.toLowerCase().trim();
      batch.update(doc.ref, { nameLower });
      console.log(`  [FIX] "${d.name}" → nameLower: "${nameLower}"`);
      fixed++;
    }
  });

  if (fixed > 0) {
    await batch.commit();
    console.log(`  ✓ ${fixed} yazar güncellendi`);
  } else {
    console.log("  ✓ Tüm yazarlarda nameLower zaten var");
  }
}

// ────────────────────────────────────────────────────────────────
//  ADIM 2 — library_books: titleLower eksik olanlara ekle
// ────────────────────────────────────────────────────────────────
async function fixBooks() {
  console.log("\n── ADIM 2: library_books → titleLower ──");
  const snap = await db.collection("library_books").get();
  let fixed = 0;
  const batch = db.batch();

  snap.docs.forEach((doc) => {
    const d = doc.data();
    if (!d.titleLower && d.title) {
      const titleLower = d.title.toLowerCase().trim();
      batch.update(doc.ref, { titleLower });
      console.log(`  [FIX] "${d.title}" → titleLower: "${titleLower}"`);
      fixed++;
    }
  });

  if (fixed > 0) {
    await batch.commit();
    console.log(`  ✓ ${fixed} kitap güncellendi`);
  } else {
    console.log("  ✓ Tüm kitaplarda titleLower zaten var");
  }
}

// ────────────────────────────────────────────────────────────────
//  ADIM 3 — feed → library_books/quotes sync
// ────────────────────────────────────────────────────────────────
async function syncFeedQuotesToLibrary() {
  console.log("\n── ADIM 3: feed alıntıları → library_books/quotes sync ──");

  // Tüm kitapları yükle (titleLower → doc map)
  const booksSnap = await db.collection("library_books").get();
  const bookByTitleLower = {};
  booksSnap.docs.forEach((doc) => {
    const d = doc.data();
    const tl = (d.titleLower || (d.title || "").toLowerCase()).trim();
    if (tl) bookByTitleLower[tl] = { ...d, id: doc.id };
  });

  // Tüm yazarları yükle (nameLower → doc map)
  const authorsSnap = await db.collection("authors").get();
  const authorByNameLower = {};
  authorsSnap.docs.forEach((doc) => {
    const d = doc.data();
    const nl = (d.nameLower || (d.name || "").toLowerCase()).trim();
    if (nl) authorByNameLower[nl] = { ...d, id: doc.id };
  });

  // Feed'den alıntı postlarını çek
  const feedSnap = await db.collection("feed")
    .orderBy("ts", "desc")
    .limit(500)
    .get();

  let synced = 0;
  let skipped = 0;
  let noBook = 0;

  for (const doc of feedSnap.docs) {
    const d = doc.data();

    const quoteObj   = d.quote;
    const quoteText  = (quoteObj?.text  || d.quoteText  || "").trim();
    const bookName   = (quoteObj?.book  || d.bookName   || "").trim();
    const authorName = (quoteObj?.author || d.authorName || "").trim();

    if (!quoteText || !bookName) continue;

    // libraryBookId zaten varsa kullan, yoksa isimden bul
    let bookId   = (d.libraryBookId   || "").trim();
    let authorId = (d.libraryAuthorId || "").trim();

    if (!bookId) {
      const found = bookByTitleLower[bookName.toLowerCase().trim()];
      if (found) bookId = found.id;
    }
    if (!authorId && authorName) {
      const found = authorByNameLower[authorName.toLowerCase().trim()];
      if (found) authorId = found.id;
    }

    if (!bookId) {
      console.log(`  [SKIP] Kitap bulunamadı: "${bookName}" (feedId: ${doc.id})`);
      noBook++;
      continue;
    }

    // Bu feed postu daha önce quotes'a yazıldı mı?
    const existingSnap = await db
      .collection("library_books").doc(bookId)
      .collection("quotes")
      .where("feedPostId", "==", doc.id)
      .limit(1).get();

    if (!existingSnap.empty) {
      skipped++;
      continue;
    }

    // quotes sub-koleksiyonuna yaz
    await db.collection("library_books").doc(bookId)
      .collection("quotes").add({
        bookId,
        authorId,
        bookTitle:        bookName,
        authorName,
        text:             quoteText,
        uid:              d.uid            || "",
        userDisplayName:  d.name || d.displayName || "",
        userPhotoURL:     d.photoURL       || "",
        feedPostId:       doc.id,
        likesCount:       0,
        ts:               d.ts || admin.firestore.Timestamp.now(),
      });

    // quoteCount artır
    await db.collection("library_books").doc(bookId)
      .update({ quoteCount: admin.firestore.FieldValue.increment(1) });

    if (authorId) {
      await db.collection("authors").doc(authorId)
        .update({ quoteCount: admin.firestore.FieldValue.increment(1) })
        .catch(() => {});
    }

    // feed post'ta libraryBookId/libraryAuthorId eksikse güncelle
    if (!d.libraryBookId) {
      await db.collection("feed").doc(doc.id).update({
        libraryBookId:   bookId,
        libraryAuthorId: authorId,
        type:            "library_quote",
      }).catch(() => {});
    }

    console.log(`  [SYNC] "${bookName}" — "${quoteText.slice(0, 50)}..."`);
    synced++;
  }

  console.log(`\n  Synced: ${synced} | Zaten vardı: ${skipped} | Kitap bulunamadı: ${noBook}`);
}

// ────────────────────────────────────────────────────────────────
//  ADIM 4 — feed: eski alıntı postlarına type="library_quote" yaz
//
//  Hedef kayıtlar:
//    a) nested quote map'i olan  (quote.text dolu)
//    b) flat quoteText dolu olan
//  Her ikisinde de bookName veya authorName bulunmalı.
//  type zaten "library_quote" olanlar atlanır.
// ────────────────────────────────────────────────────────────────
async function fixQuoteTypes() {
  console.log("\n── ADIM 4: feed → type=\"library_quote\" eksik alıntıları düzelt ──");

  // Firestore whereEqualTo("type","") güvenilmez, tüm feed çekiyoruz
  // (mevcut migration zaten bunu yapıyor, tutarlı)
  const feedSnap = await db.collection("feed").get();

  let fixed = 0;
  let skipped = 0;

  // Firestore batch limiti 500; büyük koleksiyonlar için parçalı commit
  let batch = db.batch();
  let batchCount = 0;

  for (const doc of feedSnap.docs) {
    const d = doc.data();

    // Zaten doğru type varsa geç
    if (d.type === "library_quote") {
      skipped++;
      continue;
    }

    // Alıntı metni: nested map veya flat
    const quoteObj   = d.quote;
    const quoteText  = (
      (quoteObj?.text   || "").trim() ||
      (d.quoteText      || "").trim()
    );
    const bookName   = (
      (quoteObj?.book   || "").trim() ||
      (d.bookName       || "").trim()
    );
    const authorName = (
      (quoteObj?.author || "").trim() ||
      (d.authorName     || "").trim()
    );

    // Alıntı metni yoksa veya kitap+yazar ikisi de yoksa bu bir alıntı postu değil
    if (!quoteText || (!bookName && !authorName)) {
      skipped++;
      continue;
    }

    batch.update(doc.ref, { type: "library_quote" });
    fixed++;
    batchCount++;
    console.log(`  [FIX] ${doc.id} — "${(quoteText).slice(0, 60)}..."`);

    // 500 limitine gelince commit et, yeni batch başlat
    if (batchCount === 500) {
      await batch.commit();
      batch = db.batch();
      batchCount = 0;
    }
  }

  if (batchCount > 0) await batch.commit();

  console.log(`\n  Güncellendi: ${fixed} | Atlandı (zaten doğru veya alıntı değil): ${skipped}`);
}

// ────────────────────────────────────────────────────────────────
//  ADIM 5 — feed: visibility alanı eksik alıntılara "public" ekle
// ────────────────────────────────────────────────────────────────
async function fixVisibility() {
  console.log('\n── ADIM 5: visibility eksik alıntılara "public" ekle ──');
  const snap = await db.collection("feed")
    .where("type", "==", "library_quote")
    .get();

  let fixed = 0, skipped = 0;
  let batch = db.batch(), count = 0;
  const batches = [];

  snap.docs.forEach((doc) => {
    if (!doc.data().visibility) {
      batch.update(doc.ref, { visibility: "public" });
      fixed++; count++;
      if (count >= 400) { batches.push(batch); batch = db.batch(); count = 0; }
    } else { skipped++; }
  });
  if (count > 0) batches.push(batch);
  for (const b of batches) await b.commit();

  console.log(`\n  Güncellendi: ${fixed} | Zaten var: ${skipped}`);
}

// ────────────────────────────────────────────────────────────────
//  Ana akış
// ────────────────────────────────────────────────────────────────

// ────────────────────────────────────────────────────────────────
//  ADIM 7 — Mevcut kullanıcılara termsAcceptedAt backfill
//
//  Henüz termsAcceptedAt alanı olmayan kullanıcılara
//  "implicit_backfill" methoduyla geçmişe dönük kabul kaydı ekler.
//  Yeni kayıtlar AuthViewModel.acceptTerms() ile otomatik eklenir.
// ────────────────────────────────────────────────────────────────
async function backfillTermsAcceptance() {
  console.log("\n── ADIM 7: terms_acceptances backfill ──");

  const usersSnap = await db.collection("users").get();
  let filled = 0, skipped = 0;
  let batch = db.batch();
  let batchCount = 0;

  for (const doc of usersSnap.docs) {
    const d = doc.data();
    if (d.termsAcceptedAt) { skipped++; continue; }

    const uid = doc.id;
    batch.update(doc.ref, {
      termsAcceptedAt: admin.firestore.Timestamp.now(),
      termsVersion:    "1.0",
      privacyVersion:  "1.0",
    });

    const auditRef = db.collection("terms_acceptances").doc();
    batch.set(auditRef, {
      uid,
      email:          doc.data().email || "",
      ts:             admin.firestore.Timestamp.now(),
      termsVersion:   "1.0",
      privacyVersion: "1.0",
      termsUrl:       "https://heft-reng.blogspot.com/p/kullanim-kosullari.html",
      privacyUrl:     "https://heft-reng.blogspot.com/p/gizlilik-politikasi.html",
      platform:       d.platform || "unknown",
      appVersion:     d.appVersion || "",
      method:         "implicit_backfill",
    });

    filled++;
    batchCount += 2;
    console.log(`  [BACKFILL] ${uid} — ${d.email || ""}`);

    if (batchCount >= 498) {
      await batch.commit();
      batch = db.batch();
      batchCount = 0;
    }
  }

  if (batchCount > 0) await batch.commit();
  console.log(`\n  Backfill edildi: ${filled} | Zaten vardı: ${skipped}`);
}

(async () => {
  try {
    await fixAuthors();
    await fixBooks();
    await syncFeedQuotesToLibrary();
    await fixQuoteTypes();
    await fixVisibility();
    await backfillTermsAcceptance();
    console.log("\n✅ Migration tamamlandı.");
  } catch (e) {
    console.error("❌ Hata:", e);
    process.exit(1);
  } finally {
    process.exit(0);
  }
})();
